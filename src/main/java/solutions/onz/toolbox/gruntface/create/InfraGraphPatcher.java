package solutions.onz.toolbox.gruntface.create;

import solutions.onz.toolbox.gruntface.discovery.CycleDetector;
import solutions.onz.toolbox.gruntface.model.*;
import solutions.onz.toolbox.gruntface.model.Module;

import java.nio.file.Path;
import java.util.*;

public final class InfraGraphPatcher {
    private InfraGraphPatcher() {}

    public static InfraGraph addUnit(InfraGraph base, Unit linked) {
        List<Unit> units = new ArrayList<>(base.units());
        units.add(linked);

        List<InfraGraph.UsesEdge> uses = new ArrayList<>(base.usesEdges());
        if (linked.sourceLocalPath().isPresent()) {
            Path resolvedModuleDir = linked.sourceLocalPath().get().toAbsolutePath().normalize();
            for (Module m : base.modules()) {
                if (m.dir().toAbsolutePath().normalize().equals(resolvedModuleDir)) {
                    uses.add(new InfraGraph.UsesEdge(linked, m));
                    break;
                }
            }
        } else {
            Optional<String> ref = linked.sourceRef();
            if (ref.isPresent()) {
                for (ExternalModule x : base.externals()) {
                    if (x.sourceRef().equals(ref.get())) {
                        uses.add(new InfraGraph.UsesEdge(linked, x));
                        break;
                    }
                }
            }
        }

        List<InfraGraph.IncludesEdge> includes = new ArrayList<>(base.includesEdges());
        for (IncludeBlock ib : linked.includes()) {
            if (ib.resolvedPath().isEmpty()) continue;
            Path target = ib.resolvedPath().get().toAbsolutePath().normalize();
            for (CommonHcl c : base.commons()) {
                if (c.file().toAbsolutePath().normalize().equals(target)) {
                    includes.add(new InfraGraph.IncludesEdge(linked, c));
                    break;
                }
            }
        }

        List<InfraGraph.DependsOnEdge> deps = new ArrayList<>(base.dependsEdges());
        Map<Path, Unit> unitsByCanonicalDir = new HashMap<>();
        for (Unit u : units) {
            unitsByCanonicalDir.put(u.file().getParent().toAbsolutePath().normalize(), u);
        }
        for (Dependency d : linked.dependencies()) {
            d.resolvedUnitPath().ifPresent(p -> {
                Unit to = unitsByCanonicalDir.get(p.getParent().toAbsolutePath().normalize());
                if (to != null) deps.add(new InfraGraph.DependsOnEdge(linked, to));
            });
        }

        List<List<Unit>> cycles = recomputeCycles(units, deps);

        return new InfraGraph(
            units, base.modules(), base.externals(), base.commons(),
            uses, deps, includes, cycles);
    }

    public static InfraGraph addCommon(InfraGraph base, CommonHcl c) {
        List<CommonHcl> commons = new ArrayList<>(base.commons());
        commons.add(c);
        return new InfraGraph(
            base.units(), base.modules(), base.externals(), commons,
            base.usesEdges(), base.dependsEdges(), base.includesEdges(), base.cycles());
    }

    /**
     * Removes a Unit and every edge incident to it (outgoing uses/includes/dependsOn and
     * incoming dependsOn). Recomputes cycles. The unit is matched by {@link Unit#file()}.
     */
    public static InfraGraph removeUnit(InfraGraph base, Unit unit) {
        Path target = unit.file();

        List<Unit> units = new ArrayList<>();
        for (Unit u : base.units()) if (!u.file().equals(target)) units.add(u);

        List<InfraGraph.UsesEdge> uses = new ArrayList<>();
        for (InfraGraph.UsesEdge e : base.usesEdges()) {
            if (!e.from().file().equals(target)) uses.add(e);
        }

        List<InfraGraph.IncludesEdge> includes = new ArrayList<>();
        for (InfraGraph.IncludesEdge e : base.includesEdges()) {
            if (!e.from().file().equals(target)) includes.add(e);
        }

        List<InfraGraph.DependsOnEdge> deps = new ArrayList<>();
        for (InfraGraph.DependsOnEdge e : base.dependsEdges()) {
            if (e.from().file().equals(target)) continue;
            if (e.to().file().equals(target)) continue;
            deps.add(e);
        }

        List<List<Unit>> cycles = recomputeCycles(units, deps);

        return new InfraGraph(
            units, base.modules(), base.externals(), base.commons(),
            uses, deps, includes, cycles);
    }

    /**
     * Removes a CommonHcl and any IncludesEdge pointing at it. Callers should pre-check
     * that no Unit still includes this Common before invoking; any stray edges that
     * survive are dropped defensively.
     */
    public static InfraGraph removeCommon(InfraGraph base, CommonHcl common) {
        Path target = common.file();

        List<CommonHcl> commons = new ArrayList<>();
        for (CommonHcl c : base.commons()) if (!c.file().equals(target)) commons.add(c);

        List<InfraGraph.IncludesEdge> includes = new ArrayList<>();
        for (InfraGraph.IncludesEdge e : base.includesEdges()) {
            if (!e.to().file().equals(target)) includes.add(e);
        }

        return new InfraGraph(
            base.units(), base.modules(), base.externals(), commons,
            base.usesEdges(), base.dependsEdges(), includes, base.cycles());
    }

    /**
     * Replaces a Unit in place when its underlying file has been rewritten end-to-end
     * (raw HCL edit). Unlike a value-only inputs swap, the new file may declare
     * different {@code dependency}, {@code include}, or {@code terraform {source}}
     * blocks, so all of {@code oldU}'s outgoing edges are dropped and freshly resolved
     * for {@code newU}; incoming {@code dependsOn} edges are rebound to the new instance.
     */
    public static InfraGraph replaceUnit(InfraGraph base, Unit oldU, Unit newU) {
        Path target = oldU.file();

        // 1) Units list: swap by file equality.
        List<Unit> units = new ArrayList<>();
        for (Unit u : base.units()) units.add(u.file().equals(target) ? newU : u);

        // 2) UsesEdge: drop outgoing from oldU; re-resolve outgoing for newU.
        List<InfraGraph.UsesEdge> uses = new ArrayList<>();
        for (InfraGraph.UsesEdge e : base.usesEdges()) {
            if (!e.from().file().equals(target)) uses.add(e);
        }
        if (newU.sourceLocalPath().isPresent()) {
            Path resolved = newU.sourceLocalPath().get().toAbsolutePath().normalize();
            for (Module m : base.modules()) {
                if (m.dir().toAbsolutePath().normalize().equals(resolved)) {
                    uses.add(new InfraGraph.UsesEdge(newU, m));
                    break;
                }
            }
        } else if (newU.sourceRef().isPresent()) {
            String ref = newU.sourceRef().get();
            for (ExternalModule x : base.externals()) {
                if (x.sourceRef().equals(ref)) {
                    uses.add(new InfraGraph.UsesEdge(newU, x));
                    break;
                }
            }
        }

        // 3) IncludesEdge: drop outgoing; re-resolve for newU.
        List<InfraGraph.IncludesEdge> includes = new ArrayList<>();
        for (InfraGraph.IncludesEdge e : base.includesEdges()) {
            if (!e.from().file().equals(target)) includes.add(e);
        }
        for (IncludeBlock ib : newU.includes()) {
            if (ib.resolvedPath().isEmpty()) continue;
            Path resolved = ib.resolvedPath().get().toAbsolutePath().normalize();
            for (CommonHcl c : base.commons()) {
                if (c.file().toAbsolutePath().normalize().equals(resolved)) {
                    includes.add(new InfraGraph.IncludesEdge(newU, c));
                    break;
                }
            }
        }

        // 4) DependsOnEdge: drop outgoing from oldU; rebind incoming to newU;
        //    re-resolve outgoing for newU.
        List<InfraGraph.DependsOnEdge> deps = new ArrayList<>();
        for (InfraGraph.DependsOnEdge e : base.dependsEdges()) {
            if (e.from().file().equals(target)) continue;
            if (e.to().file().equals(target)) {
                deps.add(new InfraGraph.DependsOnEdge(e.from(), newU));
            } else {
                deps.add(e);
            }
        }
        Map<Path, Unit> unitsByCanonicalDir = new HashMap<>();
        for (Unit u : units) {
            unitsByCanonicalDir.put(u.file().getParent().toAbsolutePath().normalize(), u);
        }
        for (Dependency d : newU.dependencies()) {
            d.resolvedUnitPath().ifPresent(p -> {
                Unit to = unitsByCanonicalDir.get(p.getParent().toAbsolutePath().normalize());
                if (to != null) deps.add(new InfraGraph.DependsOnEdge(newU, to));
            });
        }

        // 5) Recompute cycles using existing helper.
        List<List<Unit>> cycles = recomputeCycles(units, deps);

        return new InfraGraph(
            units, base.modules(), base.externals(), base.commons(),
            uses, deps, includes, cycles);
    }

    private static List<List<Unit>> recomputeCycles(
            List<Unit> units, List<InfraGraph.DependsOnEdge> deps) {
        Map<Unit, List<Unit>> edgeMap = new HashMap<>();
        for (Unit u : units) edgeMap.put(u, new ArrayList<>());
        for (InfraGraph.DependsOnEdge e : deps) edgeMap.get(e.from()).add(e.to());
        return CycleDetector.findCycles(edgeMap);
    }
}
