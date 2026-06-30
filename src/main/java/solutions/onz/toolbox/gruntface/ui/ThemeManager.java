package solutions.onz.toolbox.gruntface.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ThemeManager {

    public enum Mode { LIGHT, DARK, AUTO }
    public enum Effective { LIGHT, DARK }

    private final Supplier<Effective> osSchemeSupplier;
    private final Consumer<Effective> applySink;
    private final ObjectProperty<Mode> mode = new SimpleObjectProperty<>(Mode.AUTO);
    private final ReadOnlyObjectWrapper<Effective> effective =
        new ReadOnlyObjectWrapper<>(Effective.LIGHT);

    public ThemeManager(Supplier<Effective> osSchemeSupplier, Consumer<Effective> applySink) {
        this.osSchemeSupplier = osSchemeSupplier;
        this.applySink = applySink;
        // Load persisted mode (default AUTO).
        Mode loaded = Prefs.themeMode();
        this.mode.set(loaded);
        recomputeEffective();
        this.mode.addListener((obs, oldV, newV) -> {
            Prefs.themeMode(newV);
            recomputeEffective();
        });
    }

    public Mode getMode() { return mode.get(); }
    public void setMode(Mode m) { mode.set(m); }
    public ObjectProperty<Mode> modeProperty() { return mode; }

    public Effective getEffective() { return effective.get(); }
    public ReadOnlyObjectProperty<Effective> effectiveProperty() {
        return effective.getReadOnlyProperty();
    }

    /**
     * Called by the OS preference listener (production) or directly by tests.
     * In production this must be invoked on the JavaFX Application Thread.
     */
    public void onOsSchemeChanged() {
        if (mode.get() == Mode.AUTO) recomputeEffective();
    }

    private void recomputeEffective() {
        Effective next = switch (mode.get()) {
            case LIGHT -> Effective.LIGHT;
            case DARK  -> Effective.DARK;
            case AUTO  -> {
                Effective os = osSchemeSupplier.get();
                yield os != null ? os : Effective.LIGHT;
            }
        };
        effective.set(next);
        // Always invoke applySink so first-construction also paints, even if value unchanged.
        applySink.accept(next);
    }
}
