package solutions.onz.toolbox.gruntface.ui.graph;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import solutions.onz.toolbox.gruntface.azure.AzureResourceInferrer;
import solutions.onz.toolbox.gruntface.model.*;
import solutions.onz.toolbox.gruntface.ui.graph.card.*;
import solutions.onz.toolbox.gruntface.ui.graph.container.EnvContainer;
import solutions.onz.toolbox.gruntface.ui.graph.container.RegionContainer;
import solutions.onz.toolbox.gruntface.ui.graph.edge.EdgeNode;
import solutions.onz.toolbox.gruntface.ui.graph.layout.DiagramLayout;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

import static solutions.onz.toolbox.gruntface.ui.graph.layout.DiagramLayout.*;

public class DiagramSkin {

    private final Group content = new Group();
    private final Map<String, Node> nodeById = new LinkedHashMap<>();
    private final Map<String, EdgeNode> edgeById = new LinkedHashMap<>();
    private final Map<String, Unit> unitById = new HashMap<>();
    private Consumer<String> onSelect = id -> {};
    private Runnable onDeselect = () -> {};
    private SecondaryClickHandler onSecondary = (x, y, id) -> {};

    @FunctionalInterface
    public interface SecondaryClickHandler {
        void on(double screenX, double screenY, String nodeIdOrNull);
    }

    public Group content() { return content; }

    public void setOnSelect(Consumer<String> handler) { this.onSelect = handler; }
    public void setOnDeselect(Runnable handler) { this.onDeselect = handler; }
    public void setOnSecondaryClick(SecondaryClickHandler h) {
        this.onSecondary = h == null ? (x, y, id) -> {} : h;
    }

    public void render(InfraGraph graph, DiagramLayout layout) {
        content.getChildren().clear();
        nodeById.clear();
        edgeById.clear();
        unitById.clear();

        Map<Path, Unit> unitsByFile = indexUnits(graph);

        // Containers, parents first.
        List<ContainerBox> sorted = sortContainersByDepth(layout.containers());
        for (ContainerBox cb : sorted) {
            Node node = switch (cb.kind()) {
                case REGION -> new RegionContainer(cb.label(),
                    countUnitsInContainer(layout, cb.id()));
                case ENV -> new EnvContainer(cb.label(),
                    countUnitsInContainer(layout, cb.id()),
                    colorForEnv(cb.label()));
                case UNCATEGORISED -> new EnvContainer("uncategorised",
                    countUnitsInContainer(layout, cb.id()), null);
            };
            node.relocate(cb.x(), cb.y());
            ((javafx.scene.layout.Region) node).setPrefSize(cb.width(), cb.height());
            content.getChildren().add(node);
            nodeById.put(cb.id(), node);
        }

        // Nodes.
        for (NodeBox nb : layout.nodes()) {
            Node card = switch (nb.kind()) {
                case UNIT -> {
                    Unit u = unitsByFile.get(unitFileOfId(nb.id()));
                    unitById.put(nb.id(), u);
                    AzureResourceInferrer.Match m = AzureResourceInferrer.infer(u);
                    UnitCard uc = new UnitCard(u, m);
                    if (u.issues().stream().anyMatch(i -> i.severity() == ParseIssue.Severity.ERROR)) {
                        uc.getStyleClass().add("error");
                    }
                    for (List<Unit> cycle : graph.cycles()) {
                        if (cycle.contains(u)) { uc.getStyleClass().add("cycle"); break; }
                    }
                    final String unitId = nb.id();
                    uc.setOnMouseClicked(ev -> {
                        if (ev.getButton() == MouseButton.SECONDARY) {
                            onSecondary.on(ev.getScreenX(), ev.getScreenY(), unitId);
                        } else {
                            onSelect.accept(unitId);
                        }
                        ev.consume();
                    });
                    List<UnitCard.OutPortDescriptor> outs = new ArrayList<>();
                    for (InfraGraph.DependsOnEdge de : graph.dependsEdges()) {
                        if (de.from().file().equals(u.file())) {
                            outs.add(new UnitCard.OutPortDescriptor(de.to().name(), de.to()));
                        }
                    }
                    uc.setOutPorts(outs);
                    uc.setOnPortNavigate(target -> onSelect.accept("u::" + target.file()));
                    yield uc;
                }
                case MODULE -> {
                    final String base = baseOf(nb.id());
                    solutions.onz.toolbox.gruntface.model.Module m = graph.modules().stream()
                        .filter(mm -> ("m::" + mm.dir()).equals(base)).findFirst().orElseThrow();
                    ModuleCard mc = new ModuleCard(m);
                    mc.setOnMouseClicked(ev -> {
                        if (ev.getButton() == MouseButton.SECONDARY) {
                            onSecondary.on(ev.getScreenX(), ev.getScreenY(), base);
                        } else {
                            onSelect.accept(base);
                        }
                        ev.consume();
                    });
                    yield mc;
                }
                case EXTERNAL -> {
                    final String base = baseOf(nb.id());
                    ExternalModule x = graph.externals().stream()
                        .filter(xx -> ("x::" + xx.sourceRef()).equals(base)).findFirst().orElseThrow();
                    ExternalCard xc = new ExternalCard(x);
                    xc.setOnMouseClicked(ev -> {
                        if (ev.getButton() == MouseButton.SECONDARY) {
                            onSecondary.on(ev.getScreenX(), ev.getScreenY(), base);
                        } else {
                            onSelect.accept(base);
                        }
                        ev.consume();
                    });
                    yield xc;
                }
                case COMMON -> {
                    final String base = baseOf(nb.id());
                    CommonHcl c = graph.commons().stream()
                        .filter(cc -> ("c::" + cc.file()).equals(base)).findFirst().orElseThrow();
                    CommonCard cc = new CommonCard(c);
                    cc.setOnMouseClicked(ev -> {
                        if (ev.getButton() == MouseButton.SECONDARY) {
                            onSecondary.on(ev.getScreenX(), ev.getScreenY(), base);
                        } else {
                            onSelect.accept(base);
                        }
                        ev.consume();
                    });
                    yield cc;
                }
            };
            javafx.scene.layout.Region r = (javafx.scene.layout.Region) card;
            if (nb.kind() == NodeKind.UNIT) {
                // Pin width only; let height grow when expanded.
                r.setPrefWidth(nb.width());
                r.setMinHeight(nb.height());
            } else {
                r.setPrefSize(nb.width(), nb.height());
            }
            card.relocate(nb.x(), nb.y());
            content.getChildren().add(card);
            nodeById.put(nb.id(), card);
        }

        // Edges.
        for (EdgeShape e : layout.edges()) {
            EdgeNode en = new EdgeNode(e);
            en.setMouseTransparent(true);  // never intercept clicks meant for cards
            content.getChildren().add(en);
            edgeById.put(e.id(), en);
        }

        // Any click that wasn't consumed by a card → deselect (primary) or fire a
        // canvas-level context-menu request (secondary). Container labels and
        // empty container backgrounds don't consume, so clicking them clears the highlight.
        content.setOnMouseClicked(ev -> {
            if (ev.isConsumed()) return;
            if (ev.getButton() == MouseButton.SECONDARY) {
                onSecondary.on(ev.getScreenX(), ev.getScreenY(), null);
            } else {
                onDeselect.run();
            }
        });
    }

    public void highlightSelection(String selectedNodeId) {
        for (Map.Entry<String, Node> entry : nodeById.entrySet()) {
            entry.getValue().getStyleClass().remove("dimmed");
            entry.getValue().pseudoClassStateChanged(
                javafx.css.PseudoClass.getPseudoClass("selected"),
                selectedNodeId != null && baseOf(entry.getKey()).equals(selectedNodeId));
        }
        Set<String> connectedInstanceIds = new HashSet<>();
        for (EdgeNode en : edgeById.values()) {
            en.getStyleClass().remove("highlighted");
            en.getStyleClass().remove("dimmed");
            if (selectedNodeId == null) continue;
            DiagramLayout.EdgeShape e = en.edge();
            String sBase = baseOf(e.sourceNodeId());
            String tBase = baseOf(e.targetNodeId());
            if (selectedNodeId.equals(sBase) || selectedNodeId.equals(tBase)) {
                en.getStyleClass().add("highlighted");
                connectedInstanceIds.add(e.sourceNodeId());
                connectedInstanceIds.add(e.targetNodeId());
            } else {
                en.getStyleClass().add("dimmed");
            }
        }
        if (selectedNodeId != null) {
            for (Map.Entry<String, Node> entry : nodeById.entrySet()) {
                String k = entry.getKey();
                boolean isSelected = baseOf(k).equals(selectedNodeId);
                if (!isSelected && !connectedInstanceIds.contains(k)
                        && !k.startsWith("region:") && !k.startsWith("env:")
                        && !"uncategorised".equals(k)) {
                    entry.getValue().getStyleClass().add("dimmed");
                }
            }
        }
    }

    private static String baseOf(String id) {
        if (id == null) return null;
        int idx = id.indexOf("##");
        return idx < 0 ? id : id.substring(0, idx);
    }

    private static Map<Path, Unit> indexUnits(InfraGraph graph) {
        Map<Path, Unit> m = new HashMap<>();
        for (Unit u : graph.units()) m.put(u.file(), u);
        return m;
    }

    private static List<ContainerBox> sortContainersByDepth(List<ContainerBox> in) {
        List<ContainerBox> out = new ArrayList<>(in);
        out.sort((a, b) -> Boolean.compare(a.parentId() != null, b.parentId() != null));
        return out;
    }

    private static int countUnitsInContainer(DiagramLayout layout, String containerId) {
        if (containerId == null) return 0;
        // Region containers don't directly hold units — they hold env containers. Sum up.
        if (containerId.startsWith("region:")) {
            Set<String> childEnvIds = new HashSet<>();
            for (ContainerBox cb : layout.containers()) {
                if (cb.kind() == ContainerKind.ENV && containerId.equals(cb.parentId())) {
                    childEnvIds.add(cb.id());
                }
            }
            int n = 0;
            for (NodeBox nb : layout.nodes()) {
                if (nb.kind() == NodeKind.UNIT && childEnvIds.contains(nb.parentId())) n++;
            }
            return n;
        }
        int n = 0;
        for (NodeBox nb : layout.nodes()) {
            if (nb.kind() == NodeKind.UNIT && containerId.equals(nb.parentId())) n++;
        }
        return n;
    }

    /**
     * Deterministic colour per environment name. Uses rgba with low alpha so the tint
     * blends with whatever AtlantaFX theme background is underneath — works in both
     * light and dark mode without needing two palettes.
     */
    private static final String[] ENV_PALETTE = {
        "rgba(59, 130, 246, 0.20)",  // blue
        "rgba(168, 85, 247, 0.20)",  // violet
        "rgba(34, 197, 94, 0.20)",   // green
        "rgba(234, 179, 8, 0.20)",   // amber
        "rgba(236, 72, 153, 0.20)",  // pink
        "rgba(99, 102, 241, 0.20)",  // indigo
        "rgba(20, 184, 166, 0.20)",  // teal
        "rgba(249, 115, 22, 0.20)"   // orange
    };
    private static String colorForEnv(String envName) {
        if (envName == null || envName.isEmpty()) return null;
        int idx = Math.floorMod(envName.hashCode(), ENV_PALETTE.length);
        return ENV_PALETTE[idx];
    }

    /** Recover the original unit file Path from the "u::<file>" id. */
    private static Path unitFileOfId(String id) {
        return Path.of(id.substring("u::".length()));
    }
}
