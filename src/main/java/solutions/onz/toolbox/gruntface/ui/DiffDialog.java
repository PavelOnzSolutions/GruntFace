package solutions.onz.toolbox.gruntface.ui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;

public class DiffDialog {

    public static boolean show(Window owner, String title, String before, String after) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(title);

        TextFlow flow = new TextFlow();
        flow.setStyle("-fx-font-family: 'Consolas', 'Menlo', monospace; -fx-font-size: 12px; -fx-padding: 8;");

        List<String> a = before.lines().toList();
        List<String> b = after.lines().toList();
        for (DiffLine d : MyersDiff.diff(a, b)) {
            Text t = new Text(d.prefix() + d.text() + "\n");
            switch (d.kind()) {
                case CONTEXT -> t.setFill(Color.web("#555"));
                case ADD     -> t.setFill(Color.web("#2e7d32"));
                case REMOVE  -> t.setFill(Color.web("#c62828"));
            }
            flow.getChildren().add(t);
        }
        ScrollPane sp = new ScrollPane(flow);
        sp.setFitToWidth(true);
        VBox.setVgrow(sp, Priority.ALWAYS);

        Button confirm = new Button("Confirm & Save");
        Button cancel = new Button("Cancel");
        boolean[] ok = { false };
        confirm.setOnAction(e -> { ok[0] = true; stage.close(); });
        cancel.setOnAction(e -> stage.close());

        HBox bar = new HBox(8, cancel, confirm);
        bar.setPadding(new Insets(8));
        VBox root = new VBox(new Label("Review the change before writing the file:"), sp, bar);
        root.setPadding(new Insets(8));
        VBox.setVgrow(sp, Priority.ALWAYS);

        stage.setScene(new Scene(root, 700, 520));
        stage.showAndWait();
        return ok[0];
    }

    public enum Kind { CONTEXT, ADD, REMOVE }
    public record DiffLine(Kind kind, String text) {
        String prefix() {
            return switch (kind) { case CONTEXT -> "  "; case ADD -> "+ "; case REMOVE -> "- "; };
        }
    }

    static class MyersDiff {
        static List<DiffLine> diff(List<String> a, List<String> b) {
            int n = a.size(), m = b.size();
            int[][] lcs = new int[n + 1][m + 1];
            for (int i = n - 1; i >= 0; i--) {
                for (int j = m - 1; j >= 0; j--) {
                    if (a.get(i).equals(b.get(j))) lcs[i][j] = lcs[i + 1][j + 1] + 1;
                    else lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
            List<DiffLine> out = new ArrayList<>();
            int i = 0, j = 0;
            while (i < n && j < m) {
                if (a.get(i).equals(b.get(j))) { out.add(new DiffLine(Kind.CONTEXT, a.get(i))); i++; j++; }
                else if (lcs[i + 1][j] >= lcs[i][j + 1]) { out.add(new DiffLine(Kind.REMOVE, a.get(i))); i++; }
                else { out.add(new DiffLine(Kind.ADD, b.get(j))); j++; }
            }
            while (i < n) { out.add(new DiffLine(Kind.REMOVE, a.get(i++))); }
            while (j < m) { out.add(new DiffLine(Kind.ADD, b.get(j++))); }
            return out;
        }
    }
}
