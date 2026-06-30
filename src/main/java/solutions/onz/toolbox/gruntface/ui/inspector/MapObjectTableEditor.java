package solutions.onz.toolbox.gruntface.ui.inspector;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import solutions.onz.toolbox.gruntface.model.InputValue;
import solutions.onz.toolbox.gruntface.model.Variable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Editor for `map(object({...}))` typed inputs. Renders the raw HCL value as a TableView
 * with one row per map entry and one column per object field, plus a "(key)" column.
 *
 * <p>Cells hold raw HCL expression text — this keeps unresolved expressions
 * ({@code local.x}, {@code dependency.y.outputs.z}) round-trippable without re-quoting.</p>
 *
 * <p>If the raw value cannot be parsed into the expected shape, {@link #tryBuild} returns
 * empty so the caller can fall back to a plain TextArea editor.</p>
 */
public class MapObjectTableEditor implements ValueEditor {

    private final List<TypeBadges.FieldDecl> fields;
    private final ObservableList<RowData> rows;
    private final VBox container;

    public static class RowData {
        final StringProperty key;
        final LinkedHashMap<String, StringProperty> values = new LinkedHashMap<>();

        RowData(String key, List<TypeBadges.FieldDecl> fields, Map<String, String> initial) {
            this.key = new SimpleStringProperty(key == null ? "" : key);
            for (TypeBadges.FieldDecl f : fields) {
                this.values.put(f.name(),
                    new SimpleStringProperty(initial.getOrDefault(f.name(), "")));
            }
        }

        StringProperty value(String field) { return values.get(field); }
    }

    private MapObjectTableEditor(List<TypeBadges.FieldDecl> fields, List<RowData> initialRows) {
        this.fields = fields;
        this.rows = FXCollections.observableArrayList(initialRows);
        this.container = buildNode();
    }

    public static Optional<MapObjectTableEditor> tryBuild(Variable v, InputValue value) {
        List<TypeBadges.FieldDecl> fields = TypeBadges.parseMapObjectFields(v.typeExpr());
        if (fields.isEmpty()) return Optional.empty();

        String raw = value instanceof InputValue.RawHcl(String hcl) ? hcl : null;
        List<RowData> initial;
        if (raw == null || raw.isBlank()) {
            initial = new ArrayList<>();
        } else {
            initial = parseRows(raw, fields);
            if (initial == null) return Optional.empty();
        }
        return Optional.of(new MapObjectTableEditor(fields, initial));
    }

    @Override
    public Node node() {
        return container;
    }

    @Override
    public InputValue read(Variable v, InputValue previous) {
        return new InputValue.RawHcl(serialize());
    }

    /* --------------------------------------------------------------------- */
    /*  UI build                                                             */
    /* --------------------------------------------------------------------- */

    private VBox buildNode() {
        VBox entriesHost = new VBox(8);
        entriesHost.getStyleClass().add("map-object-entries");
        rebuildEntries(entriesHost);
        rows.addListener((ListChangeListener<RowData>) ch -> rebuildEntries(entriesHost));

        Button add = new Button("+ Add row");
        add.setOnAction(e -> rows.add(new RowData("", fields, Map.of())));
        HBox actions = new HBox(add);
        actions.getStyleClass().add("map-object-actions");

        VBox box = new VBox(8);
        box.getChildren().addAll(entriesHost, actions);
        return box;
    }

    private void rebuildEntries(VBox host) {
        host.getChildren().clear();
        for (int i = 0; i < rows.size(); i++) {
            host.getChildren().add(buildEntryCard(rows.get(i), i));
        }
    }

    private Node buildEntryCard(RowData row, int index) {
        Label title = new Label("Entry " + (index + 1));
        title.setStyle("-fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button delBtn = new Button("×");
        delBtn.getStyleClass().add("row-delete-btn");
        delBtn.setTooltip(new Tooltip("Remove row"));
        delBtn.setOnAction(e -> rows.remove(row));
        HBox header = new HBox(8, title, spacer, delBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(4);
        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setMinWidth(120);
        ColumnConstraints valueCol = new ColumnConstraints();
        valueCol.setHgrow(Priority.ALWAYS);
        valueCol.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelCol, valueCol);

        int r = 0;
        Label keyLbl = new Label("(key)");
        keyLbl.setStyle("-fx-font-weight: bold;");
        grid.add(keyLbl, 0, r);
        grid.add(bind(new TextField(), row.key), 1, r);
        r++;

        for (TypeBadges.FieldDecl f : fields) {
            String labelText = f.name() + (f.optional() ? "?" : "");
            Label nameLabel = new Label(labelText);
            nameLabel.setStyle("-fx-font-weight: bold;");
            Tooltip tip = new Tooltip(f.name() + " : " + f.type()
                + (f.optional() && !f.defaultLiteral().isEmpty()
                    ? "\n(default: " + f.defaultLiteral() + ")" : ""));
            tip.setShowDelay(Duration.millis(150));
            tip.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
            nameLabel.setTooltip(tip);
            HBox labelBox = new HBox(6, nameLabel, TypeBadges.makeBadge(f.type()));
            labelBox.setAlignment(Pos.CENTER_LEFT);
            grid.add(labelBox, 0, r);
            grid.add(bind(new TextField(), row.values.get(f.name())), 1, r);
            r++;
        }

        VBox card = new VBox(6, header, grid);
        card.getStyleClass().add("map-object-card");
        card.setStyle("-fx-border-color: #ccc; -fx-border-radius: 4; -fx-padding: 8; -fx-background-radius: 4;");
        return card;
    }

    private static TextField bind(TextField field, StringProperty prop) {
        field.setText(prop.get() == null ? "" : prop.get());
        field.textProperty().bindBidirectional(prop);
        return field;
    }

    /* --------------------------------------------------------------------- */
    /*  Serialize rows -> HCL                                                */
    /* --------------------------------------------------------------------- */

    String serialize() {
        return serializeRows(rows, fields);
    }

    static String serializeRows(List<RowData> rows, List<TypeBadges.FieldDecl> fields) {
        if (rows.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{\n");
        for (RowData row : rows) {
            String key = row.key.get() == null ? "" : row.key.get().trim();
            if (key.isEmpty()) continue;
            sb.append("  ").append(quoteKeyIfNeeded(key)).append(" = {\n");
            for (TypeBadges.FieldDecl f : fields) {
                String v = row.values.get(f.name()) == null ? "" : row.values.get(f.name()).get();
                if (v == null) v = "";
                v = v.trim();
                if (v.isEmpty()) {
                    if (f.optional()) continue;
                    // Required field empty — emit it as empty string so the file round-trips
                    v = "\"\"";
                }
                sb.append("    ").append(f.name()).append(" = ").append(v).append('\n');
            }
            sb.append("  }\n");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String quoteKeyIfNeeded(String key) {
        if (key.matches("[A-Za-z_][A-Za-z0-9_-]*")) return key;
        // Quote keys that aren't bare identifiers
        return "\"" + key.replace("\"", "\\\"") + "\"";
    }

    /* --------------------------------------------------------------------- */
    /*  Parse raw HCL value -> rows                                          */
    /*                                                                       */
    /*  Expected shape (forgiving on whitespace, commas, comments):          */
    /*      { key1 = { f1 = v1, f2 = v2 } , key2 = { ... } }                 */
    /*  Cell values are captured as raw HCL expression text so unresolved    */
    /*  references survive the round trip untouched.                         */
    /* --------------------------------------------------------------------- */

    static List<RowData> parseRows(String raw, List<TypeBadges.FieldDecl> fields) {
        Cursor c = new Cursor(raw);
        c.skipWsAndComments();
        if (!c.consume('{')) return null;
        List<RowData> result = new ArrayList<>();
        while (true) {
            c.skipWsAndComments();
            if (c.peek() == '}') { c.next(); break; }
            if (c.eof()) return null;
            // Optional comma between entries
            if (c.peek() == ',') { c.next(); continue; }

            String key = c.readKey();
            if (key == null) return null;
            c.skipWsAndComments();
            if (!c.consume('=')) return null;
            c.skipWsAndComments();
            if (!c.consume('{')) return null;

            Map<String, String> fieldValues = new LinkedHashMap<>();
            while (true) {
                c.skipWsAndComments();
                if (c.peek() == '}') { c.next(); break; }
                if (c.peek() == ',') { c.next(); continue; }
                if (c.eof()) return null;

                String field = c.readIdent();
                if (field == null) return null;
                c.skipWsAndComments();
                if (!c.consume('=')) return null;
                c.skipWsAndComments();
                String expr = c.readExpression();
                if (expr == null) return null;
                fieldValues.put(field, expr.trim());
            }
            result.add(new RowData(key, fields, fieldValues));
        }
        return result;
    }

    /** Minimal HCL-ish cursor — depth-aware expression capture, comment skipping. */
    private static class Cursor {
        final String src;
        int pos;

        Cursor(String src) { this.src = src; }

        boolean eof() { return pos >= src.length(); }
        char peek() { return eof() ? '\0' : src.charAt(pos); }
        char next() { return src.charAt(pos++); }

        boolean consume(char c) {
            if (peek() == c) { pos++; return true; }
            return false;
        }

        void skipWsAndComments() {
            while (!eof()) {
                char c = peek();
                if (Character.isWhitespace(c)) { pos++; continue; }
                if (c == '#') { while (!eof() && next() != '\n') {} continue; }
                if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '/') {
                    while (!eof() && next() != '\n') {}
                    continue;
                }
                if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '*') {
                    pos += 2;
                    while (pos + 1 < src.length() && !(src.charAt(pos) == '*' && src.charAt(pos + 1) == '/')) pos++;
                    if (pos + 1 < src.length()) pos += 2;
                    continue;
                }
                return;
            }
        }

        String readIdent() {
            int start = pos;
            while (!eof()) {
                char c = peek();
                if (Character.isLetterOrDigit(c) || c == '_' || c == '-') pos++;
                else break;
            }
            return pos == start ? null : src.substring(start, pos);
        }

        String readKey() {
            if (peek() == '"') {
                int start = ++pos;
                StringBuilder sb = new StringBuilder();
                boolean escape = false;
                while (!eof()) {
                    char c = next();
                    if (escape) { sb.append(c); escape = false; continue; }
                    if (c == '\\') { escape = true; continue; }
                    if (c == '"') return sb.toString();
                    sb.append(c);
                }
                return null;
            }
            return readIdent();
        }

        /**
         * Read an HCL expression value. Depth-aware over {}, [], (), and quoted strings.
         * Stops at top-level comma, newline (if no opener was seen), or top-level }.
         */
        String readExpression() {
            int start = pos;
            int curly = 0, square = 0, paren = 0;
            boolean inString = false;
            boolean escape = false;
            while (!eof()) {
                char c = peek();
                if (escape) { escape = false; pos++; continue; }
                if (inString) {
                    if (c == '\\') { escape = true; pos++; continue; }
                    if (c == '"') { inString = false; pos++; continue; }
                    pos++;
                    continue;
                }
                if (c == '"') { inString = true; pos++; continue; }
                if (c == '{') { curly++; pos++; continue; }
                if (c == '}') {
                    if (curly == 0) return src.substring(start, pos);
                    curly--; pos++; continue;
                }
                if (c == '[') { square++; pos++; continue; }
                if (c == ']' && square > 0) { square--; pos++; continue; }
                if (c == '(') { paren++; pos++; continue; }
                if (c == ')' && paren > 0) { paren--; pos++; continue; }
                if (c == ',' && curly == 0 && square == 0 && paren == 0) {
                    return src.substring(start, pos);
                }
                if (c == '\n' && curly == 0 && square == 0 && paren == 0) {
                    return src.substring(start, pos);
                }
                pos++;
            }
            return src.substring(start);
        }
    }
}
