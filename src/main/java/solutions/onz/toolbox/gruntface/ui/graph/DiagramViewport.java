package solutions.onz.toolbox.gruntface.ui.graph;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Group;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Region;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

/**
 * Pan/zoom wrapper around a content {@link Group}. Mouse drag pans; Ctrl+scroll zooms toward
 * the cursor; plain scroll pans vertically (Shift = horizontal). Ctrl+0 fits; Ctrl+= / Ctrl+-
 * zoom in/out by 1.2×. Clamps the zoom level to [0.25, 4.0].
 */
public class DiagramViewport extends Region {

    private final Group content = new Group();
    private final Scale scale = new Scale(1, 1, 0, 0);
    private final Translate translate = new Translate(0, 0);
    private final DoubleProperty zoom = new SimpleDoubleProperty(1.0);

    public DiagramViewport() {
        content.getTransforms().addAll(translate, scale);
        getChildren().add(content);
        setFocusTraversable(true);

        addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, this::onPress);
        addEventFilter(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, this::onDrag);
        addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, this::onScroll);
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, this::onKey);
    }

    public Group content() { return content; }
    public DoubleProperty zoomProperty() { return zoom; }

    /** Observable X translation of the content group (in viewport pixels). */
    public DoubleProperty contentTranslateXProperty() { return translate.xProperty(); }
    /** Observable Y translation of the content group (in viewport pixels). */
    public DoubleProperty contentTranslateYProperty() { return translate.yProperty(); }
    /** Programmatically pan the viewport (used by the minimap). */
    public void setTranslate(double x, double y) { translate.setX(x); translate.setY(y); }
    /** Current scale factor (same as zoom). */
    public double getScale() { return scale.getX(); }

    public void fit() {
        var b = content.getBoundsInLocal();
        if (b.getWidth() <= 0 || b.getHeight() <= 0) return;
        double sx = (getWidth()  - 40) / b.getWidth();
        double sy = (getHeight() - 40) / b.getHeight();
        double s = Math.min(Math.min(sx, sy), 1.0);
        setZoom(Math.max(s, 0.25));
        translate.setX(20 - b.getMinX() * s);
        translate.setY(20 - b.getMinY() * s);
    }

    public void setZoom(double z) {
        z = Math.max(0.25, Math.min(4.0, z));
        scale.setX(z); scale.setY(z);
        zoom.set(z);
    }

    private double dragX, dragY;
    private void onPress(javafx.scene.input.MouseEvent e) {
        dragX = e.getX() - translate.getX();
        dragY = e.getY() - translate.getY();
        requestFocus();
    }
    private void onDrag(javafx.scene.input.MouseEvent e) {
        if (!e.isPrimaryButtonDown()) return;
        translate.setX(e.getX() - dragX);
        translate.setY(e.getY() - dragY);
    }
    private void onScroll(javafx.scene.input.ScrollEvent e) {
        // Default: wheel zooms toward cursor. Hold Ctrl (vertical) or Shift (horizontal) to pan.
        if (e.isControlDown() || e.isShiftDown()) {
            double step = e.getDeltaY();
            if (e.isShiftDown()) translate.setX(translate.getX() + step);
            else                 translate.setY(translate.getY() + step);
        } else {
            double factor = e.getDeltaY() > 0 ? 1.1 : 1 / 1.1;
            double cx = e.getX(), cy = e.getY();
            double cur = scale.getX();
            double next = Math.max(0.25, Math.min(4.0, cur * factor));
            translate.setX(cx - (cx - translate.getX()) * (next / cur));
            translate.setY(cy - (cy - translate.getY()) * (next / cur));
            scale.setX(next); scale.setY(next);
            zoom.set(next);
            e.consume();
        }
    }
    private static final KeyCombination FIT_KEY  = KeyCombination.keyCombination("Ctrl+0");
    private static final KeyCombination ZIN_KEY  = KeyCombination.keyCombination("Ctrl+Equals");
    private static final KeyCombination ZIN_PLUS = KeyCombination.keyCombination("Ctrl+Plus");
    private static final KeyCombination ZOUT_KEY = KeyCombination.keyCombination("Ctrl+Minus");
    private void onKey(javafx.scene.input.KeyEvent e) {
        if (FIT_KEY.match(e)) { fit(); e.consume(); return; }
        if (ZIN_KEY.match(e) || ZIN_PLUS.match(e)) { setZoom(scale.getX() * 1.2); e.consume(); return; }
        if (ZOUT_KEY.match(e)) { setZoom(scale.getX() / 1.2); e.consume(); }
        if (e.getCode() == KeyCode.ESCAPE) requestFocus();
    }
}
