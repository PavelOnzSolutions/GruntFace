package solutions.onz.toolbox.gruntface.ui.graph.edge;

import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.PathElement;
import solutions.onz.toolbox.gruntface.ui.graph.layout.DiagramLayout;

import java.util.ArrayList;
import java.util.List;

public final class EdgeRouter {

    private EdgeRouter() {}

    public static List<PathElement> toPathElements(DiagramLayout.EdgeShape edge) {
        List<PathElement> out = new ArrayList<>(edge.bendPoints().size());
        boolean first = true;
        for (double[] p : edge.bendPoints()) {
            if (first) { out.add(new MoveTo(p[0], p[1])); first = false; }
            else        { out.add(new LineTo(p[0], p[1])); }
        }
        return out;
    }

    /** Returns the angle (radians) of the final segment, used to rotate the arrowhead. */
    public static double endAngle(DiagramLayout.EdgeShape edge) {
        List<double[]> bps = edge.bendPoints();
        if (bps.size() < 2) return 0.0;
        double[] a = bps.get(bps.size() - 2);
        double[] b = bps.getLast();
        return Math.atan2(b[1] - a[1], b[0] - a[0]);
    }
}
