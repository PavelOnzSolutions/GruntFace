package solutions.onz.toolbox.gruntface.ui.graph;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import solutions.onz.toolbox.gruntface.model.InfraGraph;
import solutions.onz.toolbox.gruntface.ui.graph.layout.DiagramLayout;
import solutions.onz.toolbox.gruntface.ui.graph.layout.ElkLayoutService;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Pure-JavaFX replacement for {@code GraphView}. Public API mirrors the old class so
 * MainController only needs constructor + method-call swaps.
 *
 * Threading: callers invoke {@link #render} from the FX thread. Layout runs on an internal
 * single-thread executor so it never blocks the FX thread, then the scene update is posted
 * back via {@link Platform#runLater}.
 */
public class DiagramView extends BorderPane {

    public record RenderOptions(boolean showModules, boolean showIncludes,
                                boolean showUnlinkedModules, boolean showUnlinkedIncludes) {}

    private final DiagramViewport viewport = new DiagramViewport();
    private final DiagramSkin skin = new DiagramSkin();
    private final ElkLayoutService layoutService = new ElkLayoutService();
    private final ExecutorService layoutExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "gruntface-elk-layout");
        t.setDaemon(true);
        return t;
    });
    private Consumer<String> onSelect = id -> {};
    private Runnable onDeselect = () -> {};
    private ContextMenuHandler contextMenuHandler = (x, y, id) -> {};

    @FunctionalInterface
    public interface ContextMenuHandler {
        void on(double screenX, double screenY, String nodeIdOrNull);
    }

    private final MinimapPane minimap;

    public DiagramView() {
        viewport.content().getChildren().add(skin.content());
        minimap = new MinimapPane(viewport, skin.content());

        StackPane stackPane = new StackPane(viewport, minimap);
        StackPane.setAlignment(minimap, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(minimap, new Insets(0, 10, 10, 0));
        setCenter(stackPane);
        Objects.requireNonNull(
            getClass().getResource("diagram.css"),
            "diagram.css missing"
        );
        getStylesheets().add(getClass().getResource("diagram.css").toExternalForm());
        skin.setOnSelect(id -> onSelect.accept(id));
        skin.setOnDeselect(() -> onDeselect.run());
        skin.setOnSecondaryClick((screenX, screenY, nodeId) -> contextMenuHandler.on(screenX, screenY, nodeId));
    }

    public void setOnSelect(Consumer<String> handler) { this.onSelect = handler; }
    public void setOnDeselect(Runnable handler) { this.onDeselect = handler; }
    public void setOnContextMenuRequest(ContextMenuHandler h) {
        this.contextMenuHandler = h == null ? (x, y, id) -> {} : h;
    }

    /** Caller invokes from the FX thread. Layout runs in the background; scene is updated on FX. */
    public void render(InfraGraph graph, Path terragruntRoot, RenderOptions options) {
        layoutExec.submit(() -> {
            try {
                DiagramLayout layout = layoutService.layout(
                    graph, terragruntRoot,
                    options.showModules(), options.showIncludes(),
                    options.showUnlinkedModules(), options.showUnlinkedIncludes());
                Platform.runLater(() -> {
                    skin.render(graph, layout);
                    viewport.fit();
                    minimap.refresh();
                });
            } catch (Throwable t) {
                System.err.println("[DiagramView] layout failed: " + t);
                t.printStackTrace();
            }
        });
    }

    public void fit() { viewport.fit(); }

    public void select(String nodeId) { skin.highlightSelection(nodeId); }

    public void clearSelection() { skin.highlightSelection(null); }

    /** Shut down the layout executor — call from {@code Application.stop()}. */
    public void dispose() { layoutExec.shutdownNow(); }
}
