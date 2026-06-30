package solutions.onz.toolbox.gruntface.azure;

import solutions.onz.toolbox.gruntface.azure.AzureResourceInferrer.Confidence;
import solutions.onz.toolbox.gruntface.azure.AzureResourceInferrer.Match;
import solutions.onz.toolbox.gruntface.model.Unit;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AzureResourceInferrerTest {

    private static Unit unit(String dirName, String sourceRef) {
        return new Unit(
            Path.of("/tmp", dirName, "terragrunt.hcl"),
            dirName,
            sourceRef == null ? Optional.empty() : Optional.of(sourceRef),
            Optional.empty(),
            Map.of(),
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(),
            ""
        );
    }

    @Test
    void localSourceMatchesStorageAccount() {
        Match m = AzureResourceInferrer.infer(unit("prod-data", "../../modules/storage-account"));
        assertEquals("storage_account", m.resource().id());
        assertEquals(Confidence.MATCHED, m.confidence());
    }

    @Test
    void gitSourceMatchesVirtualNetwork() {
        Match m = AzureResourceInferrer.infer(unit("vnet",
            "git::https://example.com/terraform-azurerm-virtual-network.git?ref=v3.0.0"));
        assertEquals("virtual_network", m.resource().id());
        assertEquals(Confidence.MATCHED, m.confidence());
    }

    @Test
    void tfrSourceMatchesCosmosDb() {
        Match m = AzureResourceInferrer.infer(unit("state", "tfr:///Azure/cosmosdb/azurerm"));
        assertEquals("cosmos_db", m.resource().id());
        assertEquals(Confidence.MATCHED, m.confidence());
    }

    @Test
    void noSourceFallsBackToDirNameMatch() {
        Match m = AzureResourceInferrer.infer(unit("aks", null));
        assertEquals("aks", m.resource().id());
        assertEquals(Confidence.GUESSED, m.confidence());
    }

    @Test
    void noSourceUngenericDirIsUnknown() {
        Match m = AzureResourceInferrer.infer(unit("main", null));
        assertEquals("resource", m.resource().id());
        assertEquals(Confidence.UNKNOWN, m.confidence());
    }

    @Test
    void longestSuffixWinsAgainstShorterKeyword() {
        Match m = AzureResourceInferrer.infer(unit("private-endpoint-blob", null));
        assertEquals("private_endpoint", m.resource().id());
    }

    @Test
    void caseInsensitive() {
        Match m = AzureResourceInferrer.infer(unit("KeyVault", null));
        assertEquals("key_vault", m.resource().id());
        assertEquals(Confidence.GUESSED, m.confidence());
    }

    @Test
    void sourceBeatsContradictoryDir() {
        Match m = AzureResourceInferrer.infer(unit("vnet", "../../modules/storage-account"));
        assertEquals("storage_account", m.resource().id());
        assertEquals(Confidence.MATCHED, m.confidence());
    }

    @Test
    void unmatchedSourceFallsThroughToDir() {
        Match m = AzureResourceInferrer.infer(unit("aks", "../../modules/bespoke-thing"));
        assertEquals("aks", m.resource().id());
        assertEquals(Confidence.GUESSED, m.confidence());
    }

    @Test
    void unmatchedSourceAndUngenericDirIsUnknown() {
        Match m = AzureResourceInferrer.infer(unit("main", "../../modules/bespoke-thing"));
        assertEquals("resource", m.resource().id());
        assertEquals(Confidence.UNKNOWN, m.confidence());
    }

    @Test
    void terraformAzurermPrefixIsStripped() {
        Match m = AzureResourceInferrer.infer(unit("ignored",
            "git::https://example.com/terraform-azurerm-storage-account.git"));
        assertEquals("storage_account", m.resource().id());
        assertEquals(Confidence.MATCHED, m.confidence());
    }
}
