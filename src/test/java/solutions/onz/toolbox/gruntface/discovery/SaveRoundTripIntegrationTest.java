package solutions.onz.toolbox.gruntface.discovery;

import solutions.onz.toolbox.gruntface.hcl.Hcl4jHclService;
import solutions.onz.toolbox.gruntface.hcl.InputsRenderer;
import solutions.onz.toolbox.gruntface.model.ByteRange;
import solutions.onz.toolbox.gruntface.model.InputValue;
import solutions.onz.toolbox.gruntface.model.Unit;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SaveRoundTripIntegrationTest {

    private final Hcl4jHclService hcl = new Hcl4jHclService();

    @Test
    void surgicalWritePreservesEverythingOutsideInputsBlock(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        Path src = Path.of("src/test/resources/fixtures/sample-project/terragrunt/dev/vpc/terragrunt.hcl");
        Path work = tmp.resolve("terragrunt.hcl");
        Files.writeString(work, Files.readString(src));

        Unit u = hcl.parseUnit(work);
        ByteRange range = u.inputsRange().orElseThrow();
        String before = u.originalText();
        String preBlock = before.substring(0, range.start());
        String postBlock = before.substring(range.end());

        Map<String, InputValue> edited = new LinkedHashMap<>(u.inputs());
        edited.put("cidr_block", new InputValue.StringValue("10.99.0.0/16"));
        String newBlock = InputsRenderer.render(edited, List.of("name", "cidr_block", "enable_dns"));
        String newText = preBlock + newBlock + postBlock;
        Files.writeString(work, newText);

        Unit reparsed = hcl.parseUnit(work);
        assertEquals("10.99.0.0/16",
            ((InputValue.StringValue) reparsed.inputs().get("cidr_block")).value());

        String reparseText = reparsed.originalText();
        ByteRange newRange = reparsed.inputsRange().orElseThrow();
        assertEquals(preBlock, reparseText.substring(0, newRange.start()),
            "content before inputs block must be unchanged");
        assertEquals(postBlock, reparseText.substring(newRange.end()),
            "content after inputs block must be unchanged");
    }
}
