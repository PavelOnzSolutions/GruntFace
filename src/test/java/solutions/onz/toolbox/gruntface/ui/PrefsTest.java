package solutions.onz.toolbox.gruntface.ui;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrefsTest {

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

    @AfterEach
    void cleanup() {
        NODE.remove("theme_mode");
    }

    @Test
    void themeMode_defaults_to_AUTO_when_unset() {
        NODE.remove("theme_mode");
        assertEquals(ThemeManager.Mode.AUTO, Prefs.themeMode());
    }

    @Test
    void themeMode_round_trips_LIGHT() {
        Prefs.themeMode(ThemeManager.Mode.LIGHT);
        assertEquals(ThemeManager.Mode.LIGHT, Prefs.themeMode());
    }

    @Test
    void themeMode_round_trips_DARK() {
        Prefs.themeMode(ThemeManager.Mode.DARK);
        assertEquals(ThemeManager.Mode.DARK, Prefs.themeMode());
    }

    @Test
    void themeMode_round_trips_AUTO() {
        Prefs.themeMode(ThemeManager.Mode.AUTO);
        assertEquals(ThemeManager.Mode.AUTO, Prefs.themeMode());
    }

    @Test
    void themeMode_falls_back_to_AUTO_on_unrecognized_value() {
        NODE.put("theme_mode", "ORANGE");
        assertEquals(ThemeManager.Mode.AUTO, Prefs.themeMode());
    }
}
