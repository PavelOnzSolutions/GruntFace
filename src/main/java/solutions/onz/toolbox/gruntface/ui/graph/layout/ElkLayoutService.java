package solutions.onz.toolbox.gruntface.ui.graph.layout;

import org.eclipse.elk.alg.layered.options.LayeredOptions;
import org.eclipse.elk.core.RecursiveGraphLayoutEngine;
import org.eclipse.elk.core.math.ElkPadding;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.core.options.Direction;
import org.eclipse.elk.core.options.EdgeRouting;
import org.eclipse.elk.core.options.HierarchyHandling;
import org.eclipse.elk.core.options.PortConstraints;
import org.eclipse.elk.core.options.PortSide;
import org.eclipse.elk.core.util.BasicProgressMonitor;
import org.eclipse.elk.graph.*;
import org.eclipse.elk.graph.util.ElkGraphUtil;
import solutions.onz.toolbox.gruntface.model.*;
import solutions.onz.toolbox.gruntface.model.Module;

import java.nio.file.Path;
import java.util.*;

import static solutions.onz.toolbox.gruntface.ui.graph.layout.DiagramLayout.*;

/**
 * ElkLayoutService is responsible for computing and organizing the visual layout
 * of infrastructure graphs into hierarchical diagrams. It utilizes the Eclipse Layout Kernel (ELK)
 * to arrange nodes, edges, and containers for clear visualization of relationships and dependencies
 * within the infrastructure.
 *
 * The class transforms graph models into diagram layouts that hold absolute scene-space
 * coordinates, container hierarchies, and connectivity details, while abstracting the
 * underlying ELK functionality.</br>
 * </br>
 * Fields:
 * <li> UNIT_W: The standard width of a unit node in the layout.</li>
 * <li> UNIT_H: The standard height of a unit node in the layout.</li>
 * <li> REF_W: Reference width used for layout adjustments and scaling.</li>
 * <li> REF_H: Reference height used for layout adjustments and scaling.</li>
 * <li> PORT_SIZE: The size of connection points (ports) on diagram nodes.</li>
 *
 */
public class ElkLayoutService {

    static final double UNIT_W = 240;
    static final double UNIT_H = 96;
    static final double REF_W = 200;
    static final double REF_H = 56;
    static final double PORT_SIZE = 8;

    /**
     * Arranges and lays out a diagram representing the structure, relationships, and references
     * of various infrastructure components described in an {@link InfraGraph}.
     *
     * The method creates a hierarchical representation of infrastructure units, modules, and dependencies,
     * uses the Eclipse Layout Kernel (ELK) library for graph layout optimization, and finally returns
     * a {@link DiagramLayout} containing the graph's visual metadata.
     *
     * @param graph The {@link InfraGraph} representing the units, modules, dependencies, and their relationships.
     * @param terragruntRoot The root path of the Terraform or Terragrunt configuration used for constructing the hierarchy.
     * @param showModules Indicates whether module nodes (internal and external) should be included in the layout.
     * @param showIncludes Indicates whether common "includes" nodes should be included in the layout.
     * @return A {@link DiagramLayout} object that encapsulates the laid out graph including nodes, edges, and other metadata.
     */
    public DiagramLayout layout(InfraGraph graph, Path terragruntRoot,
                                 boolean showModules, boolean showIncludes,
                                 boolean showUnlinkedModules, boolean showUnlinkedIncludes) {
        Set<String> linkedModuleIds = new HashSet<>();
        for (InfraGraph.UsesEdge e : graph.usesEdges()) {
            linkedModuleIds.add((e.to() instanceof Module m) ? moduleId(m)
                : externalId((ExternalModule) e.to()));
        }
        Set<Path> linkedCommonFiles = new HashSet<>();
        for (InfraGraph.IncludesEdge e : graph.includesEdges()) {
            linkedCommonFiles.add(e.to().file());
        }
        HierarchyBuilder.Tree tree = HierarchyBuilder.build(graph.units(), terragruntRoot);

        ElkNode root = ElkGraphUtil.createGraph();
        configureRoot(root);

        Map<String, ElkNode> elkByContainerId = createContainers(root, tree);

        Map<Path, ElkNode> unitElk = new LinkedHashMap<>();
        Map<String, ElkPort> outPortByUnitId = new LinkedHashMap<>();
        Map<String, ElkPort> depPortByKey = new LinkedHashMap<>();   // key = "u::<file>|<depName>"
        for (Unit u : graph.units()) {
            ElkNode parent = elkByContainerId.get(tree.containerOfUnit().get(u.file()));
            ElkNode n = ElkGraphUtil.createNode(parent);
            n.setWidth(UNIT_W);
            n.setHeight(UNIT_H);
            n.setIdentifier(unitId(u));
            n.setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_SIDE);
            unitElk.put(u.file(), n);

            for (Dependency d : u.dependencies()) {
                ElkPort p = ElkGraphUtil.createPort(n);
                p.setProperty(CoreOptions.PORT_SIDE, PortSide.WEST);
                p.setWidth(PORT_SIZE); p.setHeight(PORT_SIZE);
                p.setIdentifier(unitId(u) + "|in:" + d.name());
                depPortByKey.put(p.getIdentifier(), p);
            }
            ElkPort out = ElkGraphUtil.createPort(n);
            out.setProperty(CoreOptions.PORT_SIDE, PortSide.EAST);
            out.setWidth(PORT_SIZE); out.setHeight(PORT_SIZE);
            out.setIdentifier(unitId(u) + "|out");
            outPortByUnitId.put(unitId(u), out);
        }

        // Reference nodes (modules, externals, commons) live at root.
        Map<String, ElkNode> moduleElk = new LinkedHashMap<>();
        Map<String, ElkNode> externalElk = new LinkedHashMap<>();
        Map<Path, ElkNode> commonElk = new LinkedHashMap<>();
        if (showModules) {
            for (Module m : graph.modules()) {
                if (!showUnlinkedModules && !linkedModuleIds.contains(moduleId(m))) continue;
                ElkNode n = ElkGraphUtil.createNode(root);
                n.setWidth(REF_W); n.setHeight(REF_H);
                n.setIdentifier(moduleId(m));
                moduleElk.put(moduleId(m), n);
            }
            for (ExternalModule x : graph.externals()) {
                if (!showUnlinkedModules && !linkedModuleIds.contains(externalId(x))) continue;
                ElkNode n = ElkGraphUtil.createNode(root);
                n.setWidth(REF_W); n.setHeight(REF_H);
                n.setIdentifier(externalId(x));
                externalElk.put(externalId(x), n);
            }
        }
        if (showIncludes) {
            for (CommonHcl c : graph.commons()) {
                if (!showUnlinkedIncludes && !linkedCommonFiles.contains(c.file())) continue;
                ElkNode n = ElkGraphUtil.createNode(root);
                n.setWidth(REF_W); n.setHeight(REF_H);
                n.setIdentifier(commonId(c));
                commonElk.put(c.file(), n);
            }
        }

        // Edges.
        List<EdgeRef> edgeRefs = new ArrayList<>();
        for (InfraGraph.DependsOnEdge e : graph.dependsEdges()) {
            ElkNode src = unitElk.get(e.from().file());
            ElkNode tgt = unitElk.get(e.to().file());
            if (src == null || tgt == null) continue;
            // Attach node-to-node so ELK routes from the node boundary (within node box).
            // Ports are preserved as PortAnchor metadata but not used as edge connectors.
            ElkEdge ee = ElkGraphUtil.createSimpleEdge(src, tgt);
            ee.setIdentifier("e:dep:" + System.identityHashCode(ee));
            edgeRefs.add(new EdgeRef(ee, EdgeKind.DEPENDS_ON,
                unitId(e.from()), unitId(e.to()),
                null, null));
        }
        if (showModules) {
            for (InfraGraph.UsesEdge e : graph.usesEdges()) {
                ElkNode src = unitElk.get(((Unit) e.from()).file());
                ElkNode tgt = (e.to() instanceof Module m) ? moduleElk.get(moduleId(m))
                    : externalElk.get(externalId((ExternalModule) e.to()));
                if (src == null || tgt == null) continue;
                ElkEdge ee = ElkGraphUtil.createSimpleEdge(src, tgt);
                ee.setIdentifier("e:uses:" + System.identityHashCode(ee));
                String targetId = (e.to() instanceof Module m) ? moduleId(m)
                    : externalId((ExternalModule) e.to());
                edgeRefs.add(new EdgeRef(ee, EdgeKind.USES,
                    unitId((Unit) e.from()), targetId, null, null));
            }
        }
        if (showIncludes) {
            for (InfraGraph.IncludesEdge e : graph.includesEdges()) {
                ElkNode src = unitElk.get(e.from().file());
                ElkNode tgt = commonElk.get(e.to().file());
                if (src == null || tgt == null) continue;
                ElkEdge ee = ElkGraphUtil.createSimpleEdge(src, tgt);
                ee.setIdentifier("e:inc:" + System.identityHashCode(ee));
                edgeRefs.add(new EdgeRef(ee, EdgeKind.INCLUDES,
                    unitId(e.from()), commonId(e.to()), null, null));
            }
        }

        new RecursiveGraphLayoutEngine().layout(root, new BasicProgressMonitor());

        return collect(graph, tree, root, elkByContainerId, unitElk,
            moduleElk, externalElk, commonElk, edgeRefs);
    }

    private record EdgeRef(ElkEdge ee, EdgeKind kind,
                           String sourceNodeId, String targetNodeId,
                           String sourcePortId, String targetPortId) {}

    private static ElkPort depPortByEdge(Map<String, ElkPort> depPortByKey, InfraGraph.DependsOnEdge e) {
        // Match by dependency name when available; otherwise first WEST port on target.
        for (Dependency d : e.from().dependencies()) {
            // We don't know which dependency points to `e.to()` here without re-resolving;
            // for MVP we just attach to the first port on the target. ELK will route correctly.
        }
        // Use any WEST port on the target unit if present, else null.
        String prefix = unitId(e.to()) + "|in:";
        for (Map.Entry<String, ElkPort> entry : depPortByKey.entrySet()) {
            if (entry.getKey().startsWith(prefix)) return entry.getValue();
        }
        return null;
    }

    private static Map<String, ElkNode> createContainers(ElkNode root, HierarchyBuilder.Tree tree) {
        Map<String, ElkNode> byId = new LinkedHashMap<>();
        for (String regionId : tree.regionIds()) {
            ElkNode rn = ElkGraphUtil.createNode(root);
            rn.setIdentifier(regionId);
            configureContainer(rn);
            byId.put(regionId, rn);
        }
        for (String envId : tree.envIds()) {
            ElkNode parent = byId.get(tree.envParentRegionId().get(envId));
            ElkNode en = ElkGraphUtil.createNode(parent);
            en.setIdentifier(envId);
            configureContainer(en);
            byId.put(envId, en);
        }
        if (tree.labels().containsKey("uncategorised")) {
            ElkNode un = ElkGraphUtil.createNode(root);
            un.setIdentifier("uncategorised");
            configureContainer(un);
            byId.put("uncategorised", un);
        }
        return byId;
    }

    private static DiagramLayout collect(InfraGraph graph, HierarchyBuilder.Tree tree,
                                          ElkNode root, Map<String, ElkNode> elkByContainerId,
                                          Map<Path, ElkNode> unitElk,
                                          Map<String, ElkNode> moduleElk,
                                          Map<String, ElkNode> externalElk,
                                          Map<Path, ElkNode> commonElk,
                                          List<EdgeRef> edgeRefs) {
        List<ContainerBox> containers = new ArrayList<>();
        for (Map.Entry<String, ElkNode> entry : elkByContainerId.entrySet()) {
            String cid = entry.getKey();
            ElkNode en = entry.getValue();
            double[] abs = absolute(en);
            ContainerKind kind = "uncategorised".equals(cid) ? ContainerKind.UNCATEGORISED
                : cid.startsWith("region:") ? ContainerKind.REGION : ContainerKind.ENV;
            String parentId = en.getParent() == root ? null : en.getParent().getIdentifier();
            containers.add(new ContainerBox(
                cid, kind, tree.labels().get(cid),
                abs[0], abs[1], en.getWidth(), en.getHeight(),
                parentId
            ));
        }

        List<NodeBox> nodes = new ArrayList<>();
        for (Unit u : graph.units()) {
            ElkNode en = unitElk.get(u.file());
            nodes.add(toNodeBox(unitId(u), NodeKind.UNIT, tree.containerOfUnit().get(u.file()), en));
        }
        for (Module m : graph.modules()) {
            ElkNode en = moduleElk.get(moduleId(m));
            if (en != null) nodes.add(toNodeBox(moduleId(m), NodeKind.MODULE, null, en));
        }
        for (ExternalModule x : graph.externals()) {
            ElkNode en = externalElk.get(externalId(x));
            if (en != null) nodes.add(toNodeBox(externalId(x), NodeKind.EXTERNAL, null, en));
        }
        for (CommonHcl c : graph.commons()) {
            ElkNode en = commonElk.get(c.file());
            if (en != null) nodes.add(toNodeBox(commonId(c), NodeKind.COMMON, null, en));
        }

        List<EdgeShape> edges = new ArrayList<>();
        for (EdgeRef er : edgeRefs) edges.add(toEdgeShape(er));
        return new DiagramLayout(containers, nodes, edges);
    }

    private static NodeBox toNodeBox(String id, NodeKind kind, String parentId, ElkNode en) {
        double[] abs = absolute(en);
        List<PortAnchor> ports = new ArrayList<>();
        for (ElkPort p : en.getPorts()) {
            double[] pAbs = { abs[0] + p.getX() + p.getWidth() / 2.0,
                              abs[1] + p.getY() + p.getHeight() / 2.0 };
            org.eclipse.elk.core.options.PortSide side = p.getProperty(CoreOptions.PORT_SIDE);
            DiagramLayout.PortSide ds = switch (side) {
                case NORTH -> DiagramLayout.PortSide.N;
                case SOUTH -> DiagramLayout.PortSide.S;
                case EAST  -> DiagramLayout.PortSide.E;
                case WEST  -> DiagramLayout.PortSide.W;
                default -> DiagramLayout.PortSide.W;
            };
            ports.add(new PortAnchor(p.getIdentifier(), ds, pAbs[0], pAbs[1]));
        }
        return new NodeBox(id, kind, parentId, abs[0], abs[1], en.getWidth(), en.getHeight(), ports);
    }

    private static EdgeShape toEdgeShape(EdgeRef er) {
        // ELK stores edge section coords relative to the edge's containing ElkNode (the LCA).
        // We must add the LCA's absolute offset to get scene-space coordinates.
        ElkNode container = er.ee().getContainingNode();
        double[] containerAbs = (container != null) ? absolute(container) : new double[]{0, 0};
        double ox = containerAbs[0], oy = containerAbs[1];

        List<double[]> bends = new ArrayList<>();
        for (ElkEdgeSection s : er.ee().getSections()) {
            bends.add(new double[]{ s.getStartX() + ox, s.getStartY() + oy });
            for (ElkBendPoint bp : s.getBendPoints()) {
                bends.add(new double[]{ bp.getX() + ox, bp.getY() + oy });
            }
            bends.add(new double[]{ s.getEndX() + ox, s.getEndY() + oy });
        }
        if (bends.isEmpty()) bends = List.of(new double[]{0,0}, new double[]{0,0});
        return new EdgeShape(er.ee().getIdentifier(), er.kind(),
            er.sourceNodeId(), er.targetNodeId(),
            er.sourcePortId(), er.targetPortId(), bends);
    }

    private static void configureRoot(ElkNode root) {
        root.setProperty(CoreOptions.ALGORITHM, "layered");
        root.setProperty(CoreOptions.DIRECTION, Direction.DOWN);
        root.setProperty(CoreOptions.EDGE_ROUTING, EdgeRouting.ORTHOGONAL);
        root.setProperty(CoreOptions.HIERARCHY_HANDLING, HierarchyHandling.INCLUDE_CHILDREN);
        root.setProperty(LayeredOptions.SPACING_NODE_NODE, 32.0);
        root.setProperty(LayeredOptions.SPACING_EDGE_NODE, 16.0);
    }

    private static void configureContainer(ElkNode c) {
        c.setProperty(CoreOptions.ALGORITHM, "layered");
        c.setProperty(CoreOptions.DIRECTION, Direction.DOWN);
        c.setProperty(CoreOptions.PADDING, new ElkPadding(28, 12, 12, 12));
    }

    private static double[] absolute(ElkNode n) {
        double x = n.getX(), y = n.getY();
        ElkNode p = n.getParent();
        while (p != null && p.getParent() != null) {
            x += p.getX(); y += p.getY();
            p = p.getParent();
        }
        return new double[]{ x, y };
    }

    static String unitId(Unit u) { return "u::" + u.file(); }
    static String moduleId(Module m) { return "m::" + m.dir(); }
    static String externalId(ExternalModule x) { return "x::" + x.sourceRef(); }
    static String commonId(CommonHcl c) { return "c::" + c.file(); }
}
