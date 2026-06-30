package solutions.onz.toolbox.gruntface.discovery;

import solutions.onz.toolbox.gruntface.azure.AzureResourceInferrer;
import solutions.onz.toolbox.gruntface.hcl.HclService;
import solutions.onz.toolbox.gruntface.hcl.IncludePathResolver;
import solutions.onz.toolbox.gruntface.model.CommonHcl;
import solutions.onz.toolbox.gruntface.model.Dependency;
import solutions.onz.toolbox.gruntface.model.ExternalModule;
import solutions.onz.toolbox.gruntface.model.IncludeBlock;
import solutions.onz.toolbox.gruntface.model.InfraGraph;
import solutions.onz.toolbox.gruntface.model.Module;
import solutions.onz.toolbox.gruntface.model.ParseIssue;
import solutions.onz.toolbox.gruntface.model.Unit;
import solutions.onz.toolbox.gruntface.name.EvaluationContext;
import solutions.onz.toolbox.gruntface.name.ResolvedName;
import solutions.onz.toolbox.gruntface.name.ResourceNameSynth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DiscoveryService {

    private final HclService hcl;

    public DiscoveryService(HclService hcl) {
        this.hcl = hcl;
    }

    /**
     * Single-root entry point. Walks the Terragrunt root to auto-detect a `_common/` directory
     * (the first one found that contains at least one `*.hcl`) and a `modules/` directory (the
     * first one whose direct subdirectories contain `*.tf` files with `variable` blocks).
     */
    public InfraGraph load(Path terragruntRoot) throws IOException {
        Optional<Path> commonsDir = autodetectCommonsDir(terragruntRoot);
        Optional<Path> modulesRoot = autodetectModulesRoot(terragruntRoot);
        return load(terragruntRoot, commonsDir, modulesRoot);
    }

    /**
     * Legacy two-root API used by existing callers/tests.
     */
    public InfraGraph load(Path terragruntRoot, Path modulesRoot) throws IOException {
        return load(terragruntRoot, Optional.empty(), Optional.of(modulesRoot));
    }

    public InfraGraph load(Path terragruntRoot,
                           Optional<Path> commonsDir,
                           Optional<Path> modulesRoot) throws IOException {
        // ---- Modules ----
        List<Module> modules = new ArrayList<>();
        if (modulesRoot.isPresent()) {
            try (Stream<Path> s = Files.walk(modulesRoot.get())) {
                List<Path> moduleDirs = s
                        .filter(Files::isDirectory)
                        .filter(DiscoveryService::hasTfWithVariableBlock)
                        .sorted()
                        .toList();
                for (Path d : moduleDirs) modules.add(hcl.parseModule(d));
            }
        }

        // ---- Commons ----
        List<CommonHcl> commons = new ArrayList<>();
        if (commonsDir.isPresent()) {
            try (Stream<Path> s = Files.walk(commonsDir.get())) {
                List<Path> commonFiles = s
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".hcl"))
                        .sorted()
                        .toList();
                for (Path c : commonFiles) commons.add(hcl.parseCommon(c));
            }
        }

        // ---- Units ----
        List<Unit> units = new ArrayList<>();
        try (Stream<Path> s = Files.walk(terragruntRoot)) {
            List<Path> unitFiles = s
                    .filter(p -> p.getFileName().toString().equals("terragrunt.hcl"))
                    .sorted()
                    .toList();
            for (Path u : unitFiles) units.add(hcl.parseUnit(u));
        }

        // ---- Per-unit linking (includes / source / resolved-name) ----
        Map<String, ExternalModule> externalsByRef = new LinkedHashMap<>();
        List<Unit> linkedUnits = new ArrayList<>();
        List<InfraGraph.IncludesEdge> includesEdges = new ArrayList<>();
        List<InfraGraph.UsesEdge> usesEdges = new ArrayList<>();
        for (Unit raw : units) {
            LinkResult lr = linkOne(raw, modules, commons, externalsByRef);
            linkedUnits.add(lr.unit());
            includesEdges.addAll(lr.includes());
            usesEdges.addAll(lr.uses());
        }

        // ---- Dependency resolution (needs the full unit set) ----
        Map<Path, Unit> unitsByCanonicalDir = new HashMap<>();
        for (Unit u : linkedUnits) {
            unitsByCanonicalDir.put(u.file().getParent().toAbsolutePath().normalize(), u);
        }

        List<InfraGraph.DependsOnEdge> dependsEdges = new ArrayList<>();
        List<Unit> finalUnits = new ArrayList<>();
        for (Unit u : linkedUnits) {
            List<Dependency> resolvedDeps = new ArrayList<>();
            for (Dependency d : u.dependencies()) {
                Path target = u.file().getParent().resolve(d.configPath()).toAbsolutePath().normalize();
                Unit toUnit = unitsByCanonicalDir.get(target);
                resolvedDeps.add(new Dependency(d.name(), d.configPath(),
                        toUnit == null ? Optional.empty() : Optional.of(toUnit.file())));
                if (toUnit != null) {
                    dependsEdges.add(new InfraGraph.DependsOnEdge(u, toUnit));
                }
            }
            // Preserve the resolved-name produced by linkOne when re-wrapping with resolved dependencies.
            finalUnits.add(new Unit(
                    u.file(), u.name(), u.sourceRef(), u.sourceLocalPath(),
                    u.inputs(), u.inputsRange(), resolvedDeps,
                    u.includes(), u.issues(), u.originalText(),
                    u.resolvedName()
            ));
        }

        Map<Unit, List<Unit>> edgeMap = new HashMap<>();
        for (Unit u : finalUnits) edgeMap.put(u, new ArrayList<>());
        for (InfraGraph.DependsOnEdge e : dependsEdges) {
            Unit from = findUnitByFile(finalUnits, e.from().file());
            Unit to = findUnitByFile(finalUnits, e.to().file());
            edgeMap.get(from).add(to);
        }
        List<InfraGraph.DependsOnEdge> remappedDepends = new ArrayList<>();
        for (var entry : edgeMap.entrySet()) {
            for (Unit to : entry.getValue()) {
                remappedDepends.add(new InfraGraph.DependsOnEdge(entry.getKey(), to));
            }
        }
        List<InfraGraph.UsesEdge> remappedUses = new ArrayList<>();
        for (InfraGraph.UsesEdge e : usesEdges) {
            Unit from = findUnitByFile(finalUnits, ((Unit) e.from()).file());
            remappedUses.add(new InfraGraph.UsesEdge(from, e.to()));
        }
        List<InfraGraph.IncludesEdge> remappedIncludes = new ArrayList<>();
        for (InfraGraph.IncludesEdge e : includesEdges) {
            Unit from = findUnitByFile(finalUnits, e.from().file());
            remappedIncludes.add(new InfraGraph.IncludesEdge(from, e.to()));
        }

        List<List<Unit>> cycles = CycleDetector.findCycles(edgeMap);

        return new InfraGraph(
                finalUnits, modules, List.copyOf(externalsByRef.values()),
                commons,
                remappedUses, remappedDepends,
                remappedIncludes,
                cycles
        );
    }

    /**
     * Parse a single terragrunt.hcl and resolve its includes / source / resolved name
     * against the modules + commons already in {@code existing}. Used after the create flow to
     * splice a new unit into the in-memory graph without re-walking the project. Dependencies on
     * the returned unit are preserved as parsed (unresolved); the patcher resolves them against
     * the live graph.
     */
    public Unit linkSingleUnit(Path unitFile, InfraGraph existing) throws IOException {
        Unit raw = hcl.parseUnit(unitFile);
        // We don't need the edges or external accumulator outside the patcher; a local one is fine.
        Map<String, ExternalModule> sink = new LinkedHashMap<>();
        return linkOne(raw, existing.modules(), existing.commons(), sink).unit();
    }

    /**
     * Parse a single _common/*.hcl file.
     */
    public CommonHcl linkSingleCommon(Path commonFile) throws IOException {
        return hcl.parseCommon(commonFile);
    }

    /**
     * Internal: bundle the linked Unit with the edges discovered while linking it.
     */
    private record LinkResult(
            Unit unit,
            List<InfraGraph.UsesEdge> uses,
            List<InfraGraph.IncludesEdge> includes
    ) {
    }

    /**
     * Per-unit linking. Resolves include paths, resolves {@code source} (including a single
     * {@code ${include.<name>.locals.<key>}} interpolation), looks up local modules by name when
     * the source is external but a matching local module exists, then synthesises a resolved
     * resource name via {@link ResourceNameSynth}. Dependency resolution is left to the caller
     * because it needs the full set of units.
     *
     * <p>{@code externalsByRef} is shared across calls so the same external URL collapses to
     * one {@link ExternalModule} instance per source-ref string.
     */
    private LinkResult linkOne(Unit raw, List<Module> modules, List<CommonHcl> commons,
                               Map<String, ExternalModule> externalsByRef) {
        List<Path> moduleDirsList = modules.stream().map(Module::dir).toList();

        // 1) Resolve include paths.
        List<IncludeBlock> rebuilt = new ArrayList<>();
        for (IncludeBlock ib : raw.includes()) {
            Optional<Path> resolved = IncludePathResolver.resolve(ib.pathExpr(), raw.file());
            Optional<Path> normalized = resolved.map(p -> p.toAbsolutePath().normalize());
            rebuilt.add(new IncludeBlock(ib.name(), ib.pathExpr(), normalized));
        }

        // Collect include edges where the resolved path matches a discovered common.
        Set<Path> commonCanonicalFiles = new HashSet<>();
        for (CommonHcl c : commons) {
            commonCanonicalFiles.add(c.file().toAbsolutePath().normalize());
        }
        List<InfraGraph.IncludesEdge> includes = new ArrayList<>();

        // 2) Resolve source (with single ${include.X.locals.Y} interpolation against commons).
        Optional<String> effectiveSourceRef = raw.sourceRef();
        if (effectiveSourceRef.isPresent()) {
            Optional<String> resolvedInterp = resolveSourceInterpolation(
                    effectiveSourceRef.get(), rebuilt, commons);
            if (resolvedInterp.isPresent()) {
                effectiveSourceRef = resolvedInterp;
            }
        }

        SourceResolver.Result r = SourceResolver.resolve(effectiveSourceRef, raw.file(), moduleDirsList);
        List<ParseIssue> mergedIssues = new ArrayList<>(raw.issues());

        // If SourceResolver classified the source as external, see if a local module with a
        // matching name exists; prefer the local module so the user can edit schema-aware inputs.
        Optional<Path> localByName = Optional.empty();
        if (r.localModule().isEmpty() && r.external().isPresent()) {
            localByName = matchExternalUrlToLocalModule(r.external().get().sourceRef(), moduleDirsList);
        }

        Optional<Path> resolvedLocal = r.localModule().isPresent() ? r.localModule() : localByName;
        if (resolvedLocal.isEmpty() && r.external().isEmpty()) {
            r.issue().ifPresent(mergedIssues::add);
        }

        Unit linked = new Unit(
                raw.file(), raw.name(), raw.sourceRef(),
                resolvedLocal,
                raw.inputs(), raw.inputsRange(), raw.dependencies(),
                rebuilt,
                mergedIssues, raw.originalText()
        );

        // Include edges (done after we have the linked unit so the edge points to `linked`).
        for (IncludeBlock ib : rebuilt) {
            if (ib.resolvedPath().isEmpty()) continue;
            Path target = ib.resolvedPath().get();
            if (!commonCanonicalFiles.contains(target)) continue;
            CommonHcl matched = null;
            for (CommonHcl c : commons) {
                if (c.file().toAbsolutePath().normalize().equals(target)) {
                    matched = c;
                    break;
                }
            }
            if (matched != null) {
                includes.add(new InfraGraph.IncludesEdge(linked, matched));
            }
        }

        // Uses edge.
        List<InfraGraph.UsesEdge> uses = new ArrayList<>();
        if (resolvedLocal.isPresent()) {
            Module m = findModule(modules, resolvedLocal.get());
            uses.add(new InfraGraph.UsesEdge(linked, m));
        } else if (r.external().isPresent()) {
            ExternalModule ext = externalsByRef.computeIfAbsent(
                    r.external().get().sourceRef(), ExternalModule::new);
            uses.add(new InfraGraph.UsesEdge(linked, ext));
        }

        // 3) Resolved name synthesis.
        Map<Path, CommonHcl> commonsByCanonicalFile = new HashMap<>();
        for (CommonHcl c : commons) {
            commonsByCanonicalFile.put(c.file().toAbsolutePath().normalize(), c);
        }
        EvaluationContext ctx = buildEvaluationContext(linked, commonsByCanonicalFile);
        ResolvedName resolved = new ResourceNameSynth().synthesise(linked, ctx, AzureResourceInferrer.infer(linked));
        Unit named = new Unit(
                linked.file(),
                linked.name(),
                linked.sourceRef(),
                linked.sourceLocalPath(),
                linked.inputs(),
                linked.inputsRange(),
                linked.dependencies(),
                linked.includes(),
                linked.issues(),
                linked.originalText(),
                Optional.of(resolved)
        );

        // Re-wrap any edges built against `linked` to point at the named version, so callers
        // observe consistent unit identities.
        List<InfraGraph.UsesEdge> usesRemapped = new ArrayList<>();
        for (InfraGraph.UsesEdge e : uses) {
            usesRemapped.add(new InfraGraph.UsesEdge(named, e.to()));
        }
        List<InfraGraph.IncludesEdge> includesRemapped = new ArrayList<>();
        for (InfraGraph.IncludesEdge e : includes) {
            includesRemapped.add(new InfraGraph.IncludesEdge(named, e.to()));
        }
        return new LinkResult(named, usesRemapped, includesRemapped);
    }

    /**
     * Walks {@code root} looking for the first directory named {@code _common} that contains at least one *.hcl file.
     */
    private static Optional<Path> autodetectCommonsDir(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            return s
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName() != null && "_common".equals(p.getFileName().toString()))
                    .filter(DiscoveryService::hasHclFile)
                    .sorted()
                    .findFirst();
        }
    }

    /**
     * Walks {@code root} looking for the first directory named {@code modules} whose direct subdirectories
     * contain *.tf files declaring `variable` blocks.
     */
    private static Optional<Path> autodetectModulesRoot(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            return s
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName() != null && "modules".equals(p.getFileName().toString()))
                    .filter(DiscoveryService::hasChildModuleDir)
                    .sorted()
                    .findFirst();
        }
    }

    private static boolean hasHclFile(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.anyMatch(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".hcl"));
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean hasChildModuleDir(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(Files::isDirectory).anyMatch(DiscoveryService::hasTfWithVariableBlock);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Try to match an external source URL to a discovered local module by name.
     * The "module name" is the last non-empty path segment of the URL, stripped of any query string
     * and trailing slashes. Underscores and hyphens are normalised so {@code key-vault} matches
     * a directory named {@code key_vault} too. Matches a local module dir whose name equals the
     * extracted name after normalisation. Returns empty if no match.
     */
    private static Optional<Path> matchExternalUrlToLocalModule(String url, List<Path> moduleDirs) {
        String name = extractModuleNameFromUrl(url);
        if (name.isEmpty()) return Optional.empty();
        String target = normaliseModuleName(name);
        for (Path dir : moduleDirs) {
            String dirName = dir.getFileName() == null ? "" : dir.getFileName().toString();
            if (normaliseModuleName(dirName).equals(target)) return Optional.of(dir);
        }
        return Optional.empty();
    }

    private static String extractModuleNameFromUrl(String url) {
        if (url == null || url.isBlank()) return "";
        // Strip query string (e.g. ?ref=v1.0)
        int q = url.indexOf('?');
        String s = q >= 0 ? url.substring(0, q) : url;
        // Strip trailing slashes
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        int slash = s.lastIndexOf('/');
        return slash >= 0 ? s.substring(slash + 1) : s;
    }

    private static String normaliseModuleName(String s) {
        return s.toLowerCase().replace('_', '-');
    }

    /**
     * If {@code sourceRef} starts with an interpolation of the form {@code ${include.<name>.locals.<key>}}
     * (optionally surrounded by quotes/whitespace) followed by an optional literal suffix
     * (e.g. {@code ?ref=<commit>}), look up the {@code key} in the {@link CommonHcl} linked
     * through the unit's include named {@code name} and return {@code <value><suffix>}.
     * Returns empty if anything doesn't match.
     */
    private static final Pattern SOURCE_INTERPOLATION = Pattern.compile(
            // Two shapes occur depending on how the HCL parser handles interpolation:
            //   1. Raw HCL with wrapper:   "${include.<name>.locals.<key>}<suffix>"  (optionally with quotes)
            //   2. Parser-stripped inner:  include.<name>.locals.<key><suffix>
            // <suffix> is a literal trailing segment, typically `?ref=<commit>` or `//<subpath>?ref=<commit>`.
            "^\\s*\"?(?:\\$\\{\\s*)?" +
                    "include\\.([A-Za-z_][A-Za-z0-9_]*)\\.locals\\.([A-Za-z_][A-Za-z0-9_]*)" +
                    "(?:\\s*\\})?([^\"\\s]*)\\s*\"?\\s*$"
    );

    private static Optional<String> resolveSourceInterpolation(String sourceRef,
                                                               List<IncludeBlock> unitIncludes,
                                                               List<CommonHcl> commons) {
        if (sourceRef == null) return Optional.empty();
        Matcher m = SOURCE_INTERPOLATION.matcher(sourceRef);
        if (!m.matches()) {
            System.err.println("[DEBUG] resolveSourceInterpolation: regex no-match for sourceRef='" + sourceRef + "'");
            return Optional.empty();
        }
        String includeName = m.group(1);
        String localKey = m.group(2);
        String suffix = m.group(3);
        Optional<IncludeBlock> ib = unitIncludes.stream()
                .filter(i -> i.name().equals(includeName))
                .findFirst();
        if (ib.isEmpty()) {
            System.err.println("[DEBUG] resolveSourceInterpolation: no include named '" + includeName
                    + "'; have: " + unitIncludes.stream().map(IncludeBlock::name).toList());
            return Optional.empty();
        }
        if (ib.get().resolvedPath().isEmpty()) {
            System.err.println("[DEBUG] resolveSourceInterpolation: include '" + includeName
                    + "' pathExpr did not resolve; pathExpr='" + ib.get().pathExpr() + "'");
            return Optional.empty();
        }
        Path resolvedCommonFile = ib.get().resolvedPath().get().toAbsolutePath().normalize();
        Optional<CommonHcl> common = commons.stream()
                .filter(c -> c.file().toAbsolutePath().normalize().equals(resolvedCommonFile))
                .findFirst();
        if (common.isEmpty()) {
            System.err.println("[DEBUG] resolveSourceInterpolation: no CommonHcl matches resolved include path "
                    + resolvedCommonFile + "; discovered commons: "
                    + commons.stream().map(c -> c.file().toAbsolutePath().normalize().toString()).toList());
            return Optional.empty();
        }
        String value = common.get().locals().get(localKey);
        if (value == null) {
            System.err.println("[DEBUG] resolveSourceInterpolation: local '" + localKey
                    + "' not found in " + common.get().file() + "; available locals: " + common.get().locals().keySet());
            return Optional.empty();
        }
        return Optional.of(suffix == null || suffix.isEmpty() ? value : value + suffix);
    }

    private static boolean hasTfWithVariableBlock(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.anyMatch(p -> {
                if (!p.getFileName().toString().endsWith(".tf")) return false;
                try {
                    return Files.readString(p).contains("variable ");
                } catch (IOException e) {
                    return false;
                }
            });
        } catch (IOException e) {
            return false;
        }
    }

    private static Module findModule(List<Module> modules, Path dir) {
        for (Module m : modules) {
            if (m.dir().toAbsolutePath().normalize().equals(dir.toAbsolutePath().normalize())) return m;
        }
        throw new IllegalStateException("module not in list: " + dir);
    }

    private static Unit findUnitByFile(List<Unit> units, Path file) {
        for (Unit u : units) {
            if (u.file().toAbsolutePath().normalize().equals(file.toAbsolutePath().normalize())) return u;
        }
        throw new IllegalStateException("unit not in list: " + file);
    }

    private EvaluationContext buildEvaluationContext(
            Unit u, Map<Path, CommonHcl> commonsByCanonicalFile) {
        Map<String, String> flatLocals = new LinkedHashMap<>();
        Map<String, Map<String, String>> includeLocals = new LinkedHashMap<>();
        for (IncludeBlock ib : u.includes()) {
            if (ib.resolvedPath().isEmpty()) continue;
            CommonHcl c = commonsByCanonicalFile.get(ib.resolvedPath().get().toAbsolutePath().normalize());
            if (c == null) continue;
            includeLocals.put(ib.name(), c.locals());
            // First include's locals win for the flat lookup namespace.
            for (Map.Entry<String, String> e : c.locals().entrySet()) {
                flatLocals.putIfAbsent(e.getKey(), e.getValue());
            }
        }
        // Fill remaining keys from the Terragrunt hierarchy (location.hcl / env.hcl / project.hcl)
        // and the unit's own locals block. Additive: _common keys keep precedence.
        for (Map.Entry<String, String> e : new HierarchyLocalsResolver(hcl).resolve(u.file()).entrySet()) {
            flatLocals.putIfAbsent(e.getKey(), e.getValue());
        }
        return new EvaluationContext(flatLocals, includeLocals, u.inputs());
    }
}
