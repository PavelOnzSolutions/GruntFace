package solutions.onz.toolbox.gruntface.hcl;

import org.junit.jupiter.api.Test;
import solutions.onz.toolbox.gruntface.model.Module;
import solutions.onz.toolbox.gruntface.model.Variable;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HeredocDescriptionTest {

    private final HclService service = new Hcl4jHclService();

    @Test
    void capturesHeredocDescriptionBody() throws Exception {
        Module mod = service.parseModule(Path.of("src/test/resources/fixtures/heredoc-description"));

        Variable diag = byName(mod, "diagnostic_settings");
        assertNotNull(diag);
        String desc = diag.description();
        assertFalse(desc.startsWith("<<"),
            "description should be the body of the heredoc, not the opening marker. Got: " + desc);
        assertTrue(desc.contains("A map of diagnostic settings"),
            "missing first body line. Got: " + desc);
        assertTrue(desc.contains("`name`"),
            "missing middle body content. Got: " + desc);
        assertTrue(desc.contains("Defaults to `[\"allLogs\"]`"),
            "missing later body line. Got: " + desc);
        assertFalse(desc.contains("DESCRIPTION"),
            "closing marker should not be included. Got: " + desc);
    }

    @Test
    void capturesIndentedHeredoc() throws Exception {
        Module mod = service.parseModule(Path.of("src/test/resources/fixtures/heredoc-description"));
        Variable v = byName(mod, "indented_heredoc_var");
        assertNotNull(v);
        String desc = v.description();
        assertTrue(desc.contains("Indented heredoc form"), "got: " + desc);
        assertTrue(desc.contains("stripped"), "got: " + desc);
        assertFalse(desc.contains("DOC"), "closing marker leaked through: " + desc);
    }

    @Test
    void quotedDescriptionStillWorks() throws Exception {
        Module mod = service.parseModule(Path.of("src/test/resources/fixtures/heredoc-description"));
        Variable v = byName(mod, "regular_string_desc");
        assertNotNull(v);
        assertEquals("Simple quoted description.", v.description());
        assertEquals("\"x\"", v.defaultLiteral().orElseThrow());
    }

    @Test
    void typeMapObjectStillParsesAlongsideHeredoc() throws Exception {
        Module mod = service.parseModule(Path.of("src/test/resources/fixtures/heredoc-description"));
        Variable diag = byName(mod, "diagnostic_settings");
        assertNotNull(diag);
        assertTrue(diag.typeExpr().startsWith("map(object("),
            "type should start with map(object(. Got: " + diag.typeExpr());
        assertTrue(diag.typeExpr().contains("event_hub_name"),
            "type should include later fields. Got: " + diag.typeExpr());
    }

    private static Variable byName(Module m, String name) {
        return m.variables().stream()
            .filter(v -> v.name().equals(name))
            .findFirst().orElse(null);
    }
}
