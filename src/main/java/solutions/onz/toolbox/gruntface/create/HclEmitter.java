package solutions.onz.toolbox.gruntface.create;

import solutions.onz.toolbox.gruntface.hcl.InputsRenderer;
import solutions.onz.toolbox.gruntface.model.CommonHcl;
import solutions.onz.toolbox.gruntface.model.ExternalModule;

import java.nio.file.Path;
import java.util.List;

public final class HclEmitter {

    private HclEmitter() {}

    /** Returns the full file content (UTF-8, LF, exactly one trailing newline) for the {@link CreateRequest}. */
    public static String emit(CreateRequest req, Path terragruntRoot) {
        if (req.mode() instanceof WizardMode.IncludeFromModule && !req.dependencies().isEmpty()) {
            throw new IllegalArgumentException(
                "IncludeFromModule does not support dependency blocks; got " + req.dependencies().keySet());
        }
        return switch (req.mode()) {
            case WizardMode.ResourceFromInclude m -> emitResourceFromInclude(req, terragruntRoot);
            case WizardMode.ResourceFromModule  m -> emitResourceFromModule(req);
            case WizardMode.IncludeFromModule   m -> emitIncludeFromModule(req);
        };
    }

    private static String emitIncludeFromModule(CreateRequest req) {
        String sourceUrl = switch (req.template()) {
            case ResourceTemplate.LocalModuleTemplate(solutions.onz.toolbox.gruntface.model.Module module) -> {
                // New include file is at parentDir/folderName.hcl; its parent IS parentDir.
                Path newFile = req.parentDir().resolve(req.folderName() + ".hcl");
                Path rel = newFile.getParent().toAbsolutePath().normalize()
                    .relativize(module.dir().toAbsolutePath().normalize());
                yield toForwardSlash(rel);
            }
            case ResourceTemplate.ExternalModuleTemplate(ExternalModule external) -> external.sourceRef();
            case ResourceTemplate.IncludeTemplate t ->
                throw new IllegalStateException("Include-from-Module requires a Module template, got " + t);
        };

        StringBuilder sb = new StringBuilder();
        sb.append("locals {\n");
        sb.append("  base_source_url = \"").append(escapeForDoubleQuotedHcl(sourceUrl)).append("\"\n");
        sb.append("}\n\n");
        sb.append(InputsRenderer.render(req.inputs(), List.copyOf(req.inputs().keySet())));
        sb.append('\n');
        return sb.toString();
    }

    private static String emitResourceFromModule(CreateRequest req) {
        StringBuilder sb = new StringBuilder();
        record Dispatch(String source, boolean addRootInclude) {}
        Dispatch d = switch (req.template()) {
            case ResourceTemplate.LocalModuleTemplate(solutions.onz.toolbox.gruntface.model.Module module) -> {
                // The new file lives at <parentDir>/<folderName>/terragrunt.hcl,
                // so relativise the module dir from that directory.
                Path newFileDir = req.parentDir().resolve(req.folderName());
                Path rel = newFileDir.toAbsolutePath().normalize()
                    .relativize(module.dir().toAbsolutePath().normalize());
                yield new Dispatch(toForwardSlash(rel), true);
            }
            case ResourceTemplate.ExternalModuleTemplate(ExternalModule external) ->
                new Dispatch(external.sourceRef(), false);
            case ResourceTemplate.IncludeTemplate t ->
                throw new IllegalStateException("Resource-from-Module requires a Module template, got " + t);
        };
        String source = d.source();
        boolean addRootInclude = d.addRootInclude();

        if (addRootInclude) {
            sb.append("include \"root\" {\n");
            sb.append("  path = find_in_parent_folders(\"root.hcl\")\n");
            sb.append("}\n\n");
        }

        sb.append("terraform {\n");
        sb.append("  source = \"").append(escapeForDoubleQuotedHcl(source)).append("\"\n");
        sb.append("}\n\n");

        appendDependencies(sb, req.dependencies());

        sb.append(InputsRenderer.render(req.inputs(), List.copyOf(req.inputs().keySet())));
        sb.append('\n');
        return sb.toString();
    }

    private static String emitResourceFromInclude(CreateRequest req, Path terragruntRoot) {
        ResourceTemplate.IncludeTemplate t = (ResourceTemplate.IncludeTemplate) req.template();
        CommonHcl include = t.include();
        // The include's `path` is built around `dirname(find_in_parent_folders("root.hcl"))`,
        // which resolves at runtime to wherever `root.hcl` actually lives — not necessarily
        // the Terragrunt root the app was opened with. Walk up from the new unit's directory
        // looking for `root.hcl`; if found, relativise the include path against THAT directory
        // (so the emitted expression includes the right `../../` walk if root.hcl is nested).
        Path newUnitDir = req.parentDir().resolve(req.folderName()).toAbsolutePath().normalize();
        Path rootHclDir = findRootHclDir(newUnitDir).orElse(terragruntRoot.toAbsolutePath().normalize());
        String includeRel = toForwardSlash(
            rootHclDir.relativize(include.file().toAbsolutePath().normalize()));

        StringBuilder sb = new StringBuilder();
        sb.append("include \"root\" {\n");
        sb.append("  path = find_in_parent_folders(\"root.hcl\")\n");
        sb.append("}\n\n");

        sb.append("include \"common\" {\n");
        sb.append("  path   = \"${dirname(find_in_parent_folders(\"root.hcl\"))}/")
          .append(escapeForDoubleQuotedHcl(includeRel))
          .append("\"\n");
        sb.append("  expose = true\n");
        sb.append("}\n\n");

        sb.append("terraform {\n");
        sb.append("  source = include.common.locals.base_source_url\n");
        sb.append("}\n\n");

        appendDependencies(sb, req.dependencies());

        List<String> ordered = ConventionalInputs.ALL.stream()
            .filter(req.inputs()::containsKey)
            .toList();
        sb.append(InputsRenderer.render(req.inputs(), ordered));
        sb.append('\n');

        return sb.toString();
    }

    private static String toForwardSlash(Path p) {
        return p.toString().replace('\\', '/');
    }

    /**
     * Walk upward from {@code startDir} looking for a {@code root.hcl} file. The start directory
     * is treated as a candidate even if it doesn't yet exist on disk (the new unit's directory
     * is created later, at commit time). Returns the directory containing {@code root.hcl}, or
     * empty if no ancestor has one.
     */
    private static java.util.Optional<Path> findRootHclDir(Path startDir) {
        Path dir = startDir;
        while (dir != null) {
            if (java.nio.file.Files.exists(dir.resolve("root.hcl"))) {
                return java.util.Optional.of(dir);
            }
            dir = dir.getParent();
        }
        return java.util.Optional.empty();
    }

    /** Renders one {@code dependency "<name>" { config_path = "..." mock_outputs = {} }} block per entry. */
    private static void appendDependencies(StringBuilder sb,
                                            java.util.LinkedHashMap<String, DependencyDecl> deps) {
        for (DependencyDecl d : deps.values()) {
            sb.append("dependency \"").append(d.name()).append("\" {\n");
            sb.append("  config_path  = \"")
              .append(escapeForDoubleQuotedHcl(d.configPath()))
              .append("\"\n");
            sb.append("  mock_outputs = {}\n");
            sb.append("}\n\n");
        }
    }

    private static String escapeForDoubleQuotedHcl(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"'  -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
