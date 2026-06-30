package solutions.onz.toolbox.gruntface.ui.inspector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MapObjectTableEditorParseTest {

    private static final String TYPE_EXPR =
        "map(object({resource_group_name=optional(string), location=string, "
            + "scopes=list(string), enabled=optional(bool, true)}))";

    @Test
    void parsesSimpleMapOfObject() {
        String raw = """
            {
              alert1 = {
                resource_group_name = "rg-foo"
                location            = "westeurope"
                scopes              = ["scope1"]
                enabled             = true
              }
              alert2 = {
                location = "westus"
                scopes   = []
              }
            }
            """;

        List<TypeBadges.FieldDecl> fields = TypeBadges.parseMapObjectFields(TYPE_EXPR);
        assertEquals(4, fields.size());

        List<MapObjectTableEditor.RowData> rows = MapObjectTableEditor.parseRows(raw, fields);
        assertNotNull(rows);
        assertEquals(2, rows.size());

        assertEquals("alert1", rows.get(0).key.get());
        assertEquals("\"rg-foo\"", rows.get(0).value("resource_group_name").get());
        assertEquals("\"westeurope\"", rows.get(0).value("location").get());
        assertEquals("[\"scope1\"]", rows.get(0).value("scopes").get());
        assertEquals("true", rows.get(0).value("enabled").get());

        assertEquals("alert2", rows.get(1).key.get());
        assertEquals("", rows.get(1).value("resource_group_name").get(),
            "missing optional field should default to empty");
        assertEquals("\"westus\"", rows.get(1).value("location").get());
    }

    @Test
    void parsesQuotedKeys() {
        String raw = """
            {
              "with space" = {
                location = "weu"
              }
            }
            """;
        List<TypeBadges.FieldDecl> fields = TypeBadges.parseMapObjectFields(TYPE_EXPR);
        List<MapObjectTableEditor.RowData> rows = MapObjectTableEditor.parseRows(raw, fields);
        assertNotNull(rows);
        assertEquals(1, rows.size());
        assertEquals("with space", rows.get(0).key.get());
    }

    @Test
    void returnsNullForGarbage() {
        List<TypeBadges.FieldDecl> fields = TypeBadges.parseMapObjectFields(TYPE_EXPR);
        assertNull(MapObjectTableEditor.parseRows("merge(local.tags, {x = 1})", fields),
            "expression starting with a function call shouldn't be table-parseable");
    }

    @Test
    void preservesUnresolvedExpressions() {
        String raw = """
            {
              dep1 = {
                location = local.region
                scopes   = dependency.foo.outputs.scopes
              }
            }
            """;
        List<TypeBadges.FieldDecl> fields = TypeBadges.parseMapObjectFields(TYPE_EXPR);
        List<MapObjectTableEditor.RowData> rows = MapObjectTableEditor.parseRows(raw, fields);
        assertNotNull(rows);
        assertEquals(1, rows.size());
        assertEquals("local.region", rows.get(0).value("location").get(),
            "unresolved expression should pass through verbatim");
        assertEquals("dependency.foo.outputs.scopes", rows.get(0).value("scopes").get());
    }

    @Test
    void classifiesMapOfObject() {
        TypeBadges.TypeInfo info = TypeBadges.classify(TYPE_EXPR);
        assertEquals("map", info.category());
        assertEquals("map<Object>", info.display());
        assertTrue(info.fullType().contains("object("));
    }

    @Test
    void classifiesPlainObject() {
        TypeBadges.TypeInfo info = TypeBadges.classify("object({a=string, b=number})");
        assertEquals("object", info.category());
        assertEquals("Object", info.display());
    }

    @Test
    void classifiesPrimitiveTypes() {
        assertEquals("string", TypeBadges.classify("string").category());
        assertEquals("number", TypeBadges.classify("number").category());
        assertEquals("bool",   TypeBadges.classify("bool").category());
        assertEquals("list",   TypeBadges.classify("list(string)").category());
        assertEquals("map",    TypeBadges.classify("map(string)").category());
        assertEquals("any",    TypeBadges.classify("any").category());
        assertEquals("any",    TypeBadges.classify("").category());
    }

    @Test
    void optionalUnwrapsDisplay() {
        TypeBadges.TypeInfo info = TypeBadges.classify("optional(string)");
        assertEquals("string", info.category());
        assertEquals("string?", info.display());
    }

    @Test
    void parsesNewlineSeparatedObjectFields() {
        String type = """
            map(object({
                name                           = optional(string, null)
                log_categories                 = optional(set(string), [])
                log_groups                     = optional(set(string), ["allLogs"])
                metric_categories              = optional(set(string), ["AllMetrics"])
                log_analytics_destination_type = optional(string, "Dedicated")
                workspace_resource_id          = optional(string, null)
                event_hub_name                 = optional(string, null)
              }))""";

        List<TypeBadges.FieldDecl> fields = TypeBadges.parseMapObjectFields(type);
        assertEquals(7, fields.size(),
            "newline-separated object fields should all be detected");
        assertEquals("name", fields.get(0).name());
        assertTrue(fields.get(0).optional());
        assertEquals("string", fields.get(0).type());
        assertEquals("null", fields.get(0).defaultLiteral());

        assertEquals("log_categories", fields.get(1).name());
        assertEquals("set(string)", fields.get(1).type());
        assertEquals("[]", fields.get(1).defaultLiteral());

        assertEquals("log_groups", fields.get(2).name());
        assertEquals("[\"allLogs\"]", fields.get(2).defaultLiteral());
    }

    @Test
    void parsesMixedCommaAndNewlineSeparators() {
        String type = "object({a=string, b=number\n  c=optional(bool, true),\n  d=string})";
        List<TypeBadges.FieldDecl> fields = TypeBadges.parseObjectFields(type);
        assertEquals(4, fields.size());
        assertEquals("a", fields.get(0).name());
        assertEquals("b", fields.get(1).name());
        assertEquals("c", fields.get(2).name());
        assertEquals("d", fields.get(3).name());
    }

    @Test
    void parsesObjectFieldsWithOptional() {
        List<TypeBadges.FieldDecl> fields =
            TypeBadges.parseObjectFields("object({a=string, b=optional(number, 0), c=optional(bool)})");
        assertEquals(3, fields.size());
        assertEquals("a", fields.get(0).name());
        assertFalse(fields.get(0).optional());

        assertEquals("b", fields.get(1).name());
        assertTrue(fields.get(1).optional());
        assertEquals("number", fields.get(1).type());
        assertEquals("0", fields.get(1).defaultLiteral());

        assertEquals("c", fields.get(2).name());
        assertTrue(fields.get(2).optional());
        assertEquals("bool", fields.get(2).type());
        assertEquals("", fields.get(2).defaultLiteral());
    }

    @Test
    void roundTripsThroughSerializer() {
        String raw = """
            {
              alert1 = {
                location = "westeurope"
                scopes   = ["s1", "s2"]
                enabled  = true
              }
            }
            """;
        List<TypeBadges.FieldDecl> fields = TypeBadges.parseMapObjectFields(TYPE_EXPR);
        List<MapObjectTableEditor.RowData> rows = MapObjectTableEditor.parseRows(raw, fields);
        assertNotNull(rows);

        String serialized = MapObjectTableEditor.serializeRows(rows, fields);
        List<MapObjectTableEditor.RowData> reparsed = MapObjectTableEditor.parseRows(serialized, fields);
        assertNotNull(reparsed);
        assertEquals(1, reparsed.size());
        assertEquals("alert1", reparsed.get(0).key.get());
        assertEquals("\"westeurope\"", reparsed.get(0).value("location").get());
        assertEquals("[\"s1\", \"s2\"]", reparsed.get(0).value("scopes").get());
        assertEquals("true", reparsed.get(0).value("enabled").get());
    }
}
