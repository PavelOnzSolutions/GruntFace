package solutions.onz.toolbox.gruntface.ui;

import java.nio.file.Path;
import java.util.Optional;
import java.util.prefs.Preferences;

public final class Prefs {

    private static final Preferences NODE =
        Preferences.userRoot().node("solutions/onz/toolbox/gruntface");

    private static final String K_TG_ROOT = "terragrunt_root";
    private static final String K_MOD_ROOT = "modules_root";
    private static final String K_COMMONS_ROOT = "commons_root";
    private static final String K_WIN_W = "window_w";
    private static final String K_WIN_H = "window_h";
    private static final String K_SHOW_MODULES = "show_modules";
    private static final String K_SHOW_INCLUDES = "show_includes";
    private static final String K_SHOW_UNLINKED_MODULES = "show_unlinked_modules";
    private static final String K_SHOW_UNLINKED_INCLUDES = "show_unlinked_includes";
    private static final String K_THEME_MODE = "theme_mode";

    private Prefs() {}

    public static Optional<Path> terragruntRoot() {
        String v = NODE.get(K_TG_ROOT, "");
        return v.isEmpty() ? Optional.empty() : Optional.of(Path.of(v));
    }
    public static void terragruntRoot(Path p) { NODE.put(K_TG_ROOT, p.toString()); }

    public static Optional<Path> modulesRoot() {
        String v = NODE.get(K_MOD_ROOT, "");
        return v.isEmpty() ? Optional.empty() : Optional.of(Path.of(v));
    }
    public static void modulesRoot(Path p) { NODE.put(K_MOD_ROOT, p.toString()); }
    public static void clearModulesRoot() { NODE.remove(K_MOD_ROOT); }

    public static Optional<Path> commonsRoot() {
        String v = NODE.get(K_COMMONS_ROOT, "");
        return v.isEmpty() ? Optional.empty() : Optional.of(Path.of(v));
    }
    public static void commonsRoot(Path p) { NODE.put(K_COMMONS_ROOT, p.toString()); }
    public static void clearCommonsRoot() { NODE.remove(K_COMMONS_ROOT); }

    public static ThemeManager.Mode themeMode() {
        String v = NODE.get(K_THEME_MODE, "AUTO");
        try { return ThemeManager.Mode.valueOf(v); }
        catch (IllegalArgumentException e) { return ThemeManager.Mode.AUTO; }
    }

    public static void themeMode(ThemeManager.Mode m) {
        NODE.put(K_THEME_MODE, m.name());
    }

    public static double windowWidth(double dflt) { return NODE.getDouble(K_WIN_W, dflt); }
    public static double windowHeight(double dflt) { return NODE.getDouble(K_WIN_H, dflt); }
    public static void windowSize(double w, double h) {
        NODE.putDouble(K_WIN_W, w);
        NODE.putDouble(K_WIN_H, h);
    }

    public static boolean showModules() { return NODE.getBoolean(K_SHOW_MODULES, false); }
    public static void showModules(boolean v) { NODE.putBoolean(K_SHOW_MODULES, v); }

    public static boolean showIncludes() { return NODE.getBoolean(K_SHOW_INCLUDES, false); }
    public static void showIncludes(boolean v) { NODE.putBoolean(K_SHOW_INCLUDES, v); }

    public static boolean showUnlinkedModules() { return NODE.getBoolean(K_SHOW_UNLINKED_MODULES, true); }
    public static void showUnlinkedModules(boolean v) { NODE.putBoolean(K_SHOW_UNLINKED_MODULES, v); }

    public static boolean showUnlinkedIncludes() { return NODE.getBoolean(K_SHOW_UNLINKED_INCLUDES, true); }
    public static void showUnlinkedIncludes(boolean v) { NODE.putBoolean(K_SHOW_UNLINKED_INCLUDES, v); }

    private static final String K_HELP_W = "help_window_w";
    private static final String K_HELP_H = "help_window_h";
    private static final String K_HELP_X = "help_window_x";
    private static final String K_HELP_Y = "help_window_y";

    public static double helpWindowWidth(double dflt)  { return NODE.getDouble(K_HELP_W, dflt); }
    public static double helpWindowHeight(double dflt) { return NODE.getDouble(K_HELP_H, dflt); }
    public static void helpWindowSize(double w, double h) {
        NODE.putDouble(K_HELP_W, w);
        NODE.putDouble(K_HELP_H, h);
    }

    /** Returns {@link Double#NaN} if no position is stored. */
    public static double helpWindowX() { return NODE.getDouble(K_HELP_X, Double.NaN); }
    public static double helpWindowY() { return NODE.getDouble(K_HELP_Y, Double.NaN); }
    public static void helpWindowPosition(double x, double y) {
        NODE.putDouble(K_HELP_X, x);
        NODE.putDouble(K_HELP_Y, y);
    }
}
