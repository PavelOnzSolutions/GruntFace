package solutions.onz.toolbox.gruntface.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import solutions.onz.toolbox.gruntface.hcl.Hcl4jHclService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HierarchyLocalsResolverTest {

    @Test
    void resolve_mergesHierarchyNearestWins_andStopsAtRoot(@TempDir Path tmp) throws IOException {
        // A sentinel ABOVE the root.hcl directory must never be read.
        write(tmp.resolve("outside.hcl"), "locals {\n  location_short = \"OUTSIDE\"\n}\n");

        Path anchor = tmp.resolve("anchor");
        write(anchor.resolve("root.hcl"), "# root\n");
        write(anchor.resolve("project.hcl"),
            "locals {\n  project_name_short = \"wpa\"\n  project_name = basename(get_terragrunt_dir())\n}\n");
        write(anchor.resolve("gwc/location.hcl"),
            "locals {\n  location_short = basename(get_terragrunt_dir())\n}\n");
        write(anchor.resolve("gwc/prod/env.hcl"),
            "locals {\n  environment = basename(get_terragrunt_dir())\n  environment_short = \"p\"\n}\n");
        Path unit = anchor.resolve("gwc/prod/key-vault/terragrunt.hcl");
        write(unit, "locals {\n  purpose = \"secrets\"\n}\n");

        Map<String, String> locals =
            new HierarchyLocalsResolver(new Hcl4jHclService()).resolve(unit);

        assertEquals("secrets", locals.get("purpose"));        // unit's own locals (gap 2)
        assertEquals("prod", locals.get("environment"));       // env.hcl basename
        assertEquals("p", locals.get("environment_short"));    // env.hcl literal
        assertEquals("gwc", locals.get("location_short"));     // location.hcl basename, NOT "OUTSIDE"
        assertEquals("wpa", locals.get("project_name_short")); // project.hcl literal
    }

    private static void write(Path p, String content) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }
}
