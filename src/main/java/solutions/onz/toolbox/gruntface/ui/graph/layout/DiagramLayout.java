package solutions.onz.toolbox.gruntface.ui.graph.layout;

import java.util.List;

/**
 * Plain-data result of {@code ElkLayoutService}. Holds absolute scene-space coordinates and
 * sizes for every node and container, plus orthogonal bend points for every edge. The FX
 * renderer consumes only this record — no ELK types are exposed beyond the layout package.
 */
public record DiagramLayout(
    List<ContainerBox> containers,
    List<NodeBox> nodes,
    List<EdgeShape> edges
) {
    public DiagramLayout {
        containers = List.copyOf(containers);
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    public enum ContainerKind { REGION, ENV, UNCATEGORISED }
    public enum NodeKind { UNIT, MODULE, EXTERNAL, COMMON }
    public enum EdgeKind { USES, DEPENDS_ON, INCLUDES }
    public enum PortSide { N, E, S, W }

    public record ContainerBox(
        String id,
        ContainerKind kind,
        String label,
        double x, double y, double width, double height,
        String parentId  // null for top-level
    ) {}

    public record PortAnchor(String id, PortSide side, double x, double y) {}

    public record NodeBox(
        String id,
        NodeKind kind,
        String parentId,  // container id, or null
        double x, double y, double width, double height,
        List<PortAnchor> ports
    ) {
        public NodeBox {
            ports = List.copyOf(ports);
        }
    }

    public record EdgeShape(
        String id,
        EdgeKind kind,
        String sourceNodeId,
        String targetNodeId,
        String sourcePortId,  // nullable
        String targetPortId,  // nullable
        List<double[]> bendPoints  // each entry is {x, y}; first is source endpoint, last is target endpoint
    ) {
        public EdgeShape {
            bendPoints = List.copyOf(bendPoints);
        }
    }
}
