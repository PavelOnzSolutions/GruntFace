package solutions.onz.toolbox.gruntface.ui.graph;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * A small minimap overlay that shows a scaled-down snapshot of the diagram and a
 * draggable rectangle representing the currently-visible viewport area.
 *
 * <p>Typical lifecycle:
 * <ol>
 *   <li>Construct with the {@link DiagramViewport} and the content {@link Group}.</li>
 *   <li>Add to a {@link javafx.scene.layout.StackPane} overlaying the viewport
 *       (bottom-right corner).</li>
 *   <li>Call {@link #refresh()} after every diagram render.</li>
 * </ol>
 */
public class MinimapPane extends Pane {

    private static final double MINIMAP_W = 200;
    private static final double MINIMAP_H = 150;

    private final DiagramViewport viewport;
    private final Group diagramContent;

    private final ImageView imageView = new ImageView();
    private final Rectangle viewportRect = new Rectangle();

    // minimapScale is recalculated each time the snapshot changes
    private double minimapScale = 1.0;
    // offset of the content bounds min-x/y within minimap coords
    private double contentMinX = 0;
    private double contentMinY = 0;

    // Guard against recursive updates triggered by our own setTranslate calls
    private boolean updatingViewport = false;

    public MinimapPane(DiagramViewport viewport, Group diagramContent) {
        this.viewport = viewport;
        this.diagramContent = diagramContent;

        setPrefSize(MINIMAP_W, MINIMAP_H);
        setMaxSize(MINIMAP_W, MINIMAP_H);
        getStyleClass().add("diagram-minimap");

        imageView.setPreserveRatio(true);
        imageView.setFitWidth(MINIMAP_W);
        imageView.setFitHeight(MINIMAP_H);
        imageView.setMouseTransparent(true);

        viewportRect.getStyleClass().add("diagram-minimap-viewport");
        viewportRect.setMouseTransparent(true);

        getChildren().addAll(imageView, viewportRect);

        // Listen for pan/zoom changes to keep the viewport rectangle in sync
        ChangeListener<Number> transformListener = (obs, oldVal, newVal) -> {
            if (!updatingViewport) updateViewportRect();
        };
        viewport.contentTranslateXProperty().addListener(transformListener);
        viewport.contentTranslateYProperty().addListener(transformListener);
        viewport.zoomProperty().addListener(transformListener);
        viewport.widthProperty().addListener(transformListener);
        viewport.heightProperty().addListener(transformListener);

        // Click/drag in the minimap → pan the main viewport
        setOnMousePressed(e -> handleMinimapClick(e.getX(), e.getY()));
        setOnMouseDragged(e -> handleMinimapClick(e.getX(), e.getY()));
    }

    /**
     * Refresh the minimap snapshot. Should be called from the FX thread after each diagram
     * render (i.e. inside a {@link javafx.application.Platform#runLater} block after
     * {@code skin.render()} completes).
     */
    public void refresh() {
        Bounds bounds = diagramContent.getBoundsInLocal();
        if (bounds.getWidth() <= 0 || bounds.getHeight() <= 0) {
            imageView.setImage(null);
            viewportRect.setVisible(false);
            return;
        }

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);

        WritableImage snapshot = diagramContent.snapshot(params, null);

        // Compute the scale used to fit the snapshot inside the minimap (preserve ratio)
        double scaleX = MINIMAP_W / bounds.getWidth();
        double scaleY = MINIMAP_H / bounds.getHeight();
        minimapScale = Math.min(scaleX, scaleY);
        contentMinX = bounds.getMinX();
        contentMinY = bounds.getMinY();

        imageView.setImage(snapshot);
        viewportRect.setVisible(true);
        updateViewportRect();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Recompute the position + size of the viewport indicator rectangle. */
    private void updateViewportRect() {
        if (minimapScale <= 0) return;

        double scale   = viewport.getScale();
        double tx      = viewport.contentTranslateXProperty().get();
        double ty      = viewport.contentTranslateYProperty().get();
        double vpW     = viewport.getWidth();
        double vpH     = viewport.getHeight();

        if (scale <= 0 || vpW <= 0 || vpH <= 0) {
            viewportRect.setVisible(false);
            return;
        }

        // Visible region in content (unscaled) coordinates
        double visibleX = -tx / scale;
        double visibleY = -ty / scale;
        double visibleW = vpW / scale;
        double visibleH = vpH / scale;

        // Map to minimap coordinates
        double rectX = (visibleX - contentMinX) * minimapScale;
        double rectY = (visibleY - contentMinY) * minimapScale;
        double rectW = visibleW * minimapScale;
        double rectH = visibleH * minimapScale;

        viewportRect.setX(rectX);
        viewportRect.setY(rectY);
        viewportRect.setWidth(Math.max(1, rectW));
        viewportRect.setHeight(Math.max(1, rectH));
        viewportRect.setVisible(true);
    }

    /**
     * Translate the main viewport so that the point at {@code (mx, my)} in minimap coordinates
     * is centred in the viewport.
     */
    private void handleMinimapClick(double mx, double my) {
        if (minimapScale <= 0) return;

        // Convert minimap coords → content coords
        double contentX = mx / minimapScale + contentMinX;
        double contentY = my / minimapScale + contentMinY;

        double scale = viewport.getScale();
        double vpW   = viewport.getWidth();
        double vpH   = viewport.getHeight();

        // New translate so that contentX/contentY appears at the centre of the viewport
        double newTx = vpW / 2.0 - contentX * scale;
        double newTy = vpH / 2.0 - contentY * scale;

        updatingViewport = true;
        try {
            viewport.setTranslate(newTx, newTy);
        } finally {
            updatingViewport = false;
        }
        // The listener is suppressed during the call above, so update manually
        updateViewportRect();
    }
}
