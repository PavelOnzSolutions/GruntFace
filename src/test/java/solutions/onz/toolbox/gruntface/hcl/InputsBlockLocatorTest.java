package solutions.onz.toolbox.gruntface.hcl;

import solutions.onz.toolbox.gruntface.model.ByteRange;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InputsBlockLocatorTest {

    @Test
    void locatesInputsBlockInSimpleUnit() throws Exception {
        String text = Files.readString(Path.of("src/test/resources/fixtures/hcl/simple-unit.hcl"));
        Optional<ByteRange> range = InputsBlockLocator.locate(text);

        assertTrue(range.isPresent(), "expected inputs block to be found");
        String slice = text.substring(range.get().start(), range.get().end());
        assertTrue(slice.startsWith("inputs"), "slice should start with 'inputs'");
        assertTrue(slice.trim().endsWith("}"), "slice should end with closing brace");
        assertTrue(slice.contains("cidr_block"));
    }

    @Test
    void returnsEmptyWhenNoInputsBlock() {
        Optional<ByteRange> range = InputsBlockLocator.locate("terraform { source = \"x\" }\n");
        assertTrue(range.isEmpty());
    }

    @Test
    void handlesNestedBraces() {
        String text = """
            inputs = {
              cfg = {
                a = 1
                b = { c = 2 }
              }
            }
            """;
        Optional<ByteRange> range = InputsBlockLocator.locate(text);
        assertTrue(range.isPresent());
        String slice = text.substring(range.get().start(), range.get().end());
        assertTrue(slice.endsWith("}"));
    }

    @Test
    void ignoresBracesInsideStrings() {
        String text = """
            inputs = {
              note = "this { is not a } block"
            }
            """;
        Optional<ByteRange> range = InputsBlockLocator.locate(text);
        assertTrue(range.isPresent());
        String slice = text.substring(range.get().start(), range.get().end());
        assertTrue(slice.contains("this { is not a } block"));
    }
}
