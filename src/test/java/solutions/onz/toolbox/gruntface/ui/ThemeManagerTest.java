package solutions.onz.toolbox.gruntface.ui;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThemeManagerTest {

    private static final Preferences NODE =
        Preferences.userRoot().node("onz/solutions/toolbox/gruntface");

    private static String originalThemeMode;

    @BeforeAll
    static void snapshot() {
        originalThemeMode = NODE.get("theme_mode", null);
    }

    @AfterAll
    static void restore() {
        if (originalThemeMode == null) {
            NODE.remove("theme_mode");
        } else {
            NODE.put("theme_mode", originalThemeMode);
        }
    }

    @BeforeEach @AfterEach
    void cleanPrefs() {
        NODE.remove("theme_mode");
    }

    private static ThemeManager newManager(Supplier<ThemeManager.Effective> osScheme) {
        return new ThemeManager(osScheme, /* applySink */ ignored -> {});
    }

    @Test
    void default_mode_is_AUTO_when_prefs_empty() {
        ThemeManager m = newManager(() -> ThemeManager.Effective.LIGHT);
        assertEquals(ThemeManager.Mode.AUTO, m.getMode());
    }

    @Test
    void mode_LIGHT_ignores_OS_scheme() {
        ThemeManager m = newManager(() -> ThemeManager.Effective.DARK);
        m.setMode(ThemeManager.Mode.LIGHT);
        assertEquals(ThemeManager.Effective.LIGHT, m.getEffective());
    }

    @Test
    void mode_DARK_ignores_OS_scheme() {
        ThemeManager m = newManager(() -> ThemeManager.Effective.LIGHT);
        m.setMode(ThemeManager.Mode.DARK);
        assertEquals(ThemeManager.Effective.DARK, m.getEffective());
    }

    @Test
    void mode_AUTO_derives_from_OS_scheme_dark() {
        ThemeManager m = newManager(() -> ThemeManager.Effective.DARK);
        m.setMode(ThemeManager.Mode.AUTO);
        assertEquals(ThemeManager.Effective.DARK, m.getEffective());
    }

    @Test
    void mode_AUTO_derives_from_OS_scheme_light() {
        ThemeManager m = newManager(() -> ThemeManager.Effective.LIGHT);
        m.setMode(ThemeManager.Mode.AUTO);
        assertEquals(ThemeManager.Effective.LIGHT, m.getEffective());
    }

    @Test
    void changing_mode_persists_to_Prefs() {
        ThemeManager m = newManager(() -> ThemeManager.Effective.LIGHT);
        m.setMode(ThemeManager.Mode.DARK);
        assertEquals(ThemeManager.Mode.DARK, Prefs.themeMode());
    }

    @Test
    void apply_sink_is_invoked_with_effective_on_change() {
        AtomicReference<ThemeManager.Effective> captured = new AtomicReference<>();
        ThemeManager m = new ThemeManager(
            () -> ThemeManager.Effective.LIGHT,
            captured::set);
        m.setMode(ThemeManager.Mode.DARK);
        assertEquals(ThemeManager.Effective.DARK, captured.get());
    }

    @Test
    void onOsSchemeChanged_updates_effective_only_in_AUTO() {
        AtomicReference<ThemeManager.Effective> osScheme =
            new AtomicReference<>(ThemeManager.Effective.LIGHT);
        ThemeManager m = newManager(osScheme::get);

        // In AUTO: OS change is reflected
        m.setMode(ThemeManager.Mode.AUTO);
        assertEquals(ThemeManager.Effective.LIGHT, m.getEffective());
        osScheme.set(ThemeManager.Effective.DARK);
        m.onOsSchemeChanged();
        assertEquals(ThemeManager.Effective.DARK, m.getEffective());

        // In LIGHT: OS change is ignored
        m.setMode(ThemeManager.Mode.LIGHT);
        osScheme.set(ThemeManager.Effective.DARK);
        m.onOsSchemeChanged();
        assertEquals(ThemeManager.Effective.LIGHT, m.getEffective());
    }

    @Test
    void effectiveProperty_is_observable() {
        ThemeManager m = newManager(() -> ThemeManager.Effective.LIGHT);
        AtomicReference<ThemeManager.Effective> seen = new AtomicReference<>();
        m.effectiveProperty().addListener((obs, oldV, newV) -> seen.set(newV));
        m.setMode(ThemeManager.Mode.DARK);
        assertEquals(ThemeManager.Effective.DARK, seen.get());
    }

    @Test
    void mode_loaded_from_Prefs_on_construction() {
        Prefs.themeMode(ThemeManager.Mode.DARK);
        ThemeManager m = newManager(() -> ThemeManager.Effective.LIGHT);
        assertEquals(ThemeManager.Mode.DARK, m.getMode());
        assertEquals(ThemeManager.Effective.DARK, m.getEffective());
    }
}
