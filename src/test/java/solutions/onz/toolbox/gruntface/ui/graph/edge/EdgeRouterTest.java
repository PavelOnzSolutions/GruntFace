package solutions.onz.toolbox.gruntface.ui.graph.edge;

import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.PathElement;
import org.junit.jupiter.api.Test;
import solutions.onz.toolbox.gruntface.ui.graph.layout.DiagramLayout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EdgeRouterTest {

    @Test
    void translates_bend_points_to_move_then_lines() {
        DiagramLayout.EdgeShape edge = new DiagramLayout.EdgeShape(
            "e1", DiagramLayout.EdgeKind.DEPENDS_ON,
            "s", "t", null, null,
            List.of(new double[]{10, 20}, new double[]{40, 20}, new double[]{40, 80})
        );
        List<PathElement> els = EdgeRouter.toPathElements(edge);

        assertEquals(3, els.size());
        assertInstanceOf(MoveTo.class, els.get(0));
        assertEquals(10.0, ((MoveTo) els.get(0)).getX());
        assertEquals(20.0, ((MoveTo) els.get(0)).getY());
        assertInstanceOf(LineTo.class, els.get(1));
        assertEquals(40.0, ((LineTo) els.get(1)).getX());
        assertEquals(20.0, ((LineTo) els.get(1)).getY());
        assertInstanceOf(LineTo.class, els.get(2));
        assertEquals(40.0, ((LineTo) els.get(2)).getX());
        assertEquals(80.0, ((LineTo) els.get(2)).getY());
    }

    @Test
    void single_point_collapses_to_move_only() {
        DiagramLayout.EdgeShape edge = new DiagramLayout.EdgeShape(
            "e", DiagramLayout.EdgeKind.USES, "s", "t", null, null,
            List.of(new double[]{5, 5})
        );
        List<PathElement> els = EdgeRouter.toPathElements(edge);
        assertEquals(1, els.size());
        assertInstanceOf(MoveTo.class, els.get(0));
    }
}
