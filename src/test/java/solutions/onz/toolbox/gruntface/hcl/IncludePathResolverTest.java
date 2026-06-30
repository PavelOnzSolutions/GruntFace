package solutions.onz.toolbox.gruntface.hcl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class IncludePathResolverTest {

    @Test
    void findInParentFoldersFindsRoot(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("root.hcl");
        Files.writeString(root, "# marker\n");
        Path deep = tmp.resolve("a/b/c/terragrunt.hcl");
        Files.createDirectories(deep.getParent());
        Files.writeString(deep, "# unit\n");

        Optional<Path> result = IncludePathResolver.resolve("find_in_parent_folders(\"root.hcl\")", deep);
        assertTrue(result.isPresent());
        assertEquals(root.toAbsolutePath().normalize(), result.get().toAbsolutePath().normalize());
    }

    @Test
    void dirnameOfFindInParentFolders(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("root.hcl"), "# marker\n");
        Path unit = tmp.resolve("a/b/terragrunt.hcl");
        Files.createDirectories(unit.getParent());

        Optional<Path> result = IncludePathResolver.resolve(
            "dirname(find_in_parent_folders(\"root.hcl\"))", unit);
        assertTrue(result.isPresent());
        assertEquals(tmp.toAbsolutePath().normalize(), result.get().toAbsolutePath().normalize());
    }

    @Test
    void interpolatedCommonPath(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("root.hcl"), "");
        Files.createDirectories(tmp.resolve("_common"));
        Files.writeString(tmp.resolve("_common/key-vault.hcl"), "");
        Path unit = tmp.resolve("apps/x/y/terragrunt.hcl");
        Files.createDirectories(unit.getParent());

        Optional<Path> result = IncludePathResolver.resolve(
            "\"${dirname(find_in_parent_folders(\"root.hcl\"))}/_common/key-vault.hcl\"", unit);

        assertTrue(result.isPresent(), "should resolve the include path");
        assertEquals(tmp.resolve("_common/key-vault.hcl").toAbsolutePath().normalize(),
                     result.get().toAbsolutePath().normalize());
    }

    @Test
    void unresolvableExpressionReturnsEmpty() {
        Optional<Path> result = IncludePathResolver.resolve(
            "some_function(\"foo\", bar)", Path.of("/tmp/x/terragrunt.hcl"));
        assertTrue(result.isEmpty());
    }
}
