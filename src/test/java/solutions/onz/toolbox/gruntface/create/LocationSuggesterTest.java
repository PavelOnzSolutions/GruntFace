package solutions.onz.toolbox.gruntface.create;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LocationSuggesterTest {

    @Test
    void parsesRegionAndEnvFromNamingLayout() {
        var s = LocationSuggester.suggest(
            Path.of("/repo/applications/payments/gwc/prod/key-vault-secrets"),
            Path.of("/repo"));
        assertEquals(Optional.of("gwc"), s.locationShort());
        assertEquals(Optional.of("prod"), s.environment());
        assertEquals(Optional.of("key-vault-secrets"), s.purpose());
    }

    @Test
    void recognisesAllKnownEnvironments() {
        for (String env : new String[]{"prod", "preprod", "dev", "test", "staging", "pre", "prd", "uat"}) {
            var s = LocationSuggester.suggest(
                Path.of("/repo/x/weu/" + env + "/foo"),
                Path.of("/repo"));
            assertEquals(Optional.of(env), s.environment(), "env=" + env);
        }
    }

    @Test
    void recognisesKnownRegions() {
        for (String r : new String[]{"gwc", "weu", "neu", "eus", "wus"}) {
            var s = LocationSuggester.suggest(
                Path.of("/repo/x/" + r + "/prod/foo"),
                Path.of("/repo"));
            assertEquals(Optional.of(r), s.locationShort(), "region=" + r);
        }
    }

    @Test
    void returnsEmptyFieldsWhenPathDoesntMatch() {
        var s = LocationSuggester.suggest(
            Path.of("/repo/_common"),
            Path.of("/repo"));
        assertTrue(s.locationShort().isEmpty());
        assertTrue(s.environment().isEmpty());
    }

    @Test
    void purposeDefaultsToLastSegmentEvenWithoutRegionEnv() {
        var s = LocationSuggester.suggest(
            Path.of("/repo/special/some-thing"),
            Path.of("/repo"));
        assertEquals(Optional.of("some-thing"), s.purpose());
    }
}
