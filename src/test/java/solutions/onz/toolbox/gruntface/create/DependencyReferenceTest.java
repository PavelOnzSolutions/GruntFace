package solutions.onz.toolbox.gruntface.create;

import org.junit.jupiter.api.Test;
import solutions.onz.toolbox.gruntface.model.InputValue;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DependencyReferenceTest {

    @Test
    void parse_simpleReference() {
        Optional<DependencyReference.Match> m = DependencyReference.parse(
            new InputValue.RawHcl("dependency.cae.outputs.id"));
        assertTrue(m.isPresent());
        assertEquals("cae", m.get().depName());
        assertEquals("outputs.id", m.get().outputsPath());
        assertFalse(m.get().wrappedInList());
    }

    @Test
    void parse_wrappedInList() {
        Optional<DependencyReference.Match> m = DependencyReference.parse(
            new InputValue.RawHcl("[dependency.uami.outputs.user_assigned_identity.id]"));
        assertTrue(m.isPresent());
        assertEquals("uami", m.get().depName());
        assertEquals("outputs.user_assigned_identity.id", m.get().outputsPath());
        assertTrue(m.get().wrappedInList());
    }

    @Test
    void parse_withWhitespaceInsideBrackets() {
        Optional<DependencyReference.Match> m = DependencyReference.parse(
            new InputValue.RawHcl("[ dependency.kv.outputs.uri ]"));
        assertTrue(m.isPresent());
        assertTrue(m.get().wrappedInList());
        assertEquals("kv", m.get().depName());
        assertEquals("outputs.uri", m.get().outputsPath());
    }

    @Test
    void parse_emptySuffix_returnsEmpty() {
        assertTrue(DependencyReference.parse(new InputValue.RawHcl("dependency.cae.")).isEmpty());
    }

    @Test
    void parse_garbageInSuffix_returnsEmpty() {
        // suffix must be chained identifiers; trailing `]` without leading `[` is malformed
        assertTrue(DependencyReference.parse(new InputValue.RawHcl("dependency.cae.outputs.id]")).isEmpty());
    }

    @Test
    void parse_mismatchedOpeningBracket_returnsEmpty() {
        assertTrue(DependencyReference.parse(new InputValue.RawHcl("[dependency.cae.outputs.id")).isEmpty());
    }

    @Test
    void parse_spaceInsidePath_returnsEmpty() {
        assertTrue(DependencyReference.parse(new InputValue.RawHcl("dependency.cae outputs.id")).isEmpty());
    }

    @Test
    void parse_stringValue_returnsEmpty() {
        // Only RawHcl is matched; a quoted string literal is not a reference.
        assertTrue(DependencyReference.parse(new InputValue.StringValue("dependency.cae.outputs.id")).isEmpty());
    }

    @Test
    void parse_numberValue_returnsEmpty() {
        assertTrue(DependencyReference.parse(new InputValue.NumberValue("42")).isEmpty());
    }

    @Test
    void parse_boolValue_returnsEmpty() {
        assertTrue(DependencyReference.parse(new InputValue.BoolValue(true)).isEmpty());
    }

    @Test
    void build_plain() {
        InputValue v = DependencyReference.build("cae", "outputs.id", false);
        assertInstanceOf(InputValue.RawHcl.class, v);
        assertEquals("dependency.cae.outputs.id", ((InputValue.RawHcl) v).hcl());
    }

    @Test
    void build_wrappedInList() {
        InputValue v = DependencyReference.build("cae", "outputs.id", true);
        assertEquals("[dependency.cae.outputs.id]", ((InputValue.RawHcl) v).hcl());
    }

    @Test
    void roundTrip_matrix() {
        String[][] cases = {
            {"cae", "outputs.id"},
            {"uami", "outputs.user_assigned_identity.id"},
            {"kv", "outputs.uri"},
            {"a_b_c", "outputs.x.y.z"}
        };
        for (String[] c : cases) {
            for (boolean wrap : new boolean[]{false, true}) {
                InputValue v = DependencyReference.build(c[0], c[1], wrap);
                DependencyReference.Match m = DependencyReference.parse(v).orElseThrow();
                assertEquals(c[0], m.depName(), "depName for " + java.util.Arrays.toString(c));
                assertEquals(c[1], m.outputsPath(), "outputsPath for " + java.util.Arrays.toString(c));
                assertEquals(wrap, m.wrappedInList());
            }
        }
    }
}
