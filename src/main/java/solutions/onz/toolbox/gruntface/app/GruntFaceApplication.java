package solutions.onz.toolbox.gruntface.app;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import solutions.onz.toolbox.gruntface.ui.Prefs;
import solutions.onz.toolbox.gruntface.ui.ThemeManager;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Objects;

public class GruntFaceApplication extends Application {

    private static final Logger LOG = System.getLogger(GruntFaceApplication.class.getName());
    private static ThemeManager themeManager;

    public static ThemeManager themeManager() { return themeManager; }

    private static HostServices hostServices;

    public static HostServices hostServices() { return hostServices; }

    @Override
    public void start(Stage stage) throws Exception {
        hostServices = getHostServices();
        themeManager = new ThemeManager(
            GruntFaceApplication::readOsScheme,
            GruntFaceApplication::applyShellStylesheet);

        // Attach OS-preference listener so AUTO can track live changes.
        Platform.getPreferences().colorSchemeProperty().addListener(
            (obs, oldV, newV) -> themeManager.onOsSchemeChanged());

        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/solutions/onz/toolbox/gruntface/ui/main-view.fxml"));
        BorderPane root = loader.load();
        double w = Prefs.windowWidth(1200);
        double h = Prefs.windowHeight(800);
        Scene scene = new Scene(root, w, h);
        stage.setTitle("GruntFace");
        stage.setScene(scene);
        stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/solutions/onz/toolbox/gruntface/ui/icon.png"))));
        stage.setOnCloseRequest(e -> Prefs.windowSize(scene.getWidth(), scene.getHeight()));
        stage.show();
    }

    private static ThemeManager.Effective readOsScheme() {
        try {
            ColorScheme cs = Platform.getPreferences().getColorScheme();
            if (cs == ColorScheme.DARK) return ThemeManager.Effective.DARK;
            if (cs == ColorScheme.LIGHT) return ThemeManager.Effective.LIGHT;
            LOG.log(Level.WARNING, "OS color scheme not reported; defaulting to LIGHT.");
            return ThemeManager.Effective.LIGHT;
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "Failed to read OS color scheme; defaulting to LIGHT.", t);
            return ThemeManager.Effective.LIGHT;
        }
    }

    private static void applyShellStylesheet(ThemeManager.Effective effective) {
        String css = (effective == ThemeManager.Effective.DARK)
            ? new PrimerDark().getUserAgentStylesheet()
            : new PrimerLight().getUserAgentStylesheet();
        Application.setUserAgentStylesheet(css);
    }
}
