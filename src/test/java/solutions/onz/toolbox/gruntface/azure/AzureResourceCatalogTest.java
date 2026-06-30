package solutions.onz.toolbox.gruntface.azure;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AzureResourceCatalogTest {

    @Test
    void catalogIsNonEmpty() {
        assertFalse(AzureResourceCatalog.all().isEmpty());
    }

    @Test
    void everyEntryHasNonEmptyFields() {
        for (AzureResource r : AzureResourceCatalog.all()) {
            assertNotNull(r.id());
            assertFalse(r.id().isBlank(), "id blank");
            assertFalse(r.displayName().isBlank(), "displayName blank for " + r.id());
            assertFalse(r.iconResourcePath().isBlank(), "iconResourcePath blank for " + r.id());
            assertNotNull(r.keywords());
        }
    }

    @Test
    void allKeywordsAreLowercaseAndWhitespaceFree() {
        for (AzureResource r : AzureResourceCatalog.all()) {
            for (String kw : r.keywords()) {
                assertEquals(kw.toLowerCase(), kw, "keyword not lowercase: " + kw + " in " + r.id());
                assertFalse(kw.matches(".*\\s.*"), "keyword has whitespace: '" + kw + "' in " + r.id());
            }
        }
    }

    @Test
    void allIdsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (AzureResource r : AzureResourceCatalog.all()) {
            assertTrue(seen.add(r.id()), "duplicate id: " + r.id());
        }
    }

    @Test
    void everyIconResolvesOnClasspath() {
        for (AzureResource r : AzureResourceCatalog.all()) {
            assertNotNull(
                AzureResourceCatalog.class
                    .getResource("/solutions/onz/toolbox/gruntface/ui/graph/" + r.iconResourcePath()),
                "missing icon for " + r.id() + " at " + r.iconResourcePath()
            );
        }
    }

    @Test
    void genericIsResourceEntry() {
        AzureResource g = AzureResourceCatalog.generic();
        assertEquals("resource", g.id());
        assertSame(g, AzureResourceCatalog.generic(), "generic() should be stable");
        assertTrue(g.keywords().isEmpty(), "generic has no keywords");
    }

    @Test
    void catalogContainsExpectedCoreEntries() {
        List<String> required = List.of(
            "storage_account", "virtual_network", "aks", "key_vault",
            "sql_database", "cosmos_db", "resource_group", "resource"
        );
        Set<String> ids = new HashSet<>();
        for (AzureResource r : AzureResourceCatalog.all()) ids.add(r.id());
        for (String id : required) assertTrue(ids.contains(id), "missing required entry: " + id);
    }

    @Test
    @Disabled("awaiting AzureCategory feature — see WIP commit eadd29f")
    void everyEntryHasNonNullCategory() {
        // Intentionally left blank — re-enable when AzureResource.category() exists.
    }

    @Test
    @Disabled("awaiting AzureCategory feature — see WIP commit eadd29f")
    void everyCategoryHasAtLeastOneEntry() {
        // Intentionally left blank — re-enable when AzureCategory enum exists.
    }
}
