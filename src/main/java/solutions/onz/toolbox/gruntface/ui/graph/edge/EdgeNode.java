package solutions.onz.toolbox.gruntface.ui.graph.edge;

import javafx.scene.Group;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.transform.Rotate;
import solutions.onz.toolbox.gruntface.ui.graph.layout.DiagramLayout;

public class EdgeNode extends Group {

    private final Path line = new Path();
    private final Path arrow = new Path();
    private final DiagramLayout.EdgeShape edge;

    public EdgeNode(DiagramLayout.EdgeShape edge) {
        this.edge = edge;
        line.getElements().setAll(EdgeRouter.toPathElements(edge));
        line.getStyleClass().add("diagram-edge");
        line.getStyleClass().add("diagram-edge-" + edge.kind().name().toLowerCase().replace('_', '-'));
        getChildren().add(line);

        if (edge.kind() == DiagramLayout.EdgeKind.DEPENDS_ON && edge.bendPoints().size() >= 2) {
            double[] tip = edge.bendPoints().get(edge.bendPoints().size() - 1);
            double angle = Math.toDegrees(EdgeRouter.endAngle(edge));
            arrow.getElements().setAll(
                new MoveTo(tip[0] - 8, tip[1] - 4),
                new LineTo(tip[0], tip[1]),
                new LineTo(tip[0] - 8, tip[1] + 4)
            );
            arrow.getStyleClass().add("diagram-edge-arrow");
            arrow.getTransforms().add(new Rotate(angle, tip[0], tip[1]));
            getChildren().add(arrow);
        }
    }

    public DiagramLayout.EdgeShape edge() { return edge; }
}
