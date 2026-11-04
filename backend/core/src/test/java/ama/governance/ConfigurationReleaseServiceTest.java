package ama.governance;

import ama.contract.GovernanceContracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationReleaseServiceTest {
    @Test void otherKindsCannotHideTwoIncorrectVoicePairs() {
        var results = new ArrayList<SampleResult>();
        results.add(new SampleResult("same-wrong", true, 0, null));
        results.add(new SampleResult("different-wrong", true, 0, null));
        for (int i = 0; i < 8; i++) results.add(new SampleResult("minutes-" + i, true, 1, null));
        var configuration = new ModelConfig("harness-embed-v1", "summary-v1", 0.8);
        var aggregate = EvaluationService.metrics(results);
        assertEquals(0.8, aggregate.score());
        assertThrows(IllegalArgumentException.class, () -> ConfigurationReleaseService.validate(
            configuration, aggregate, true, Set.of(Kind.VOICE_SAME, Kind.VOICE_DIFFERENT, Kind.MINUTES), configuration));
    }

    @Test void nonVoiceConfigurationDoesNotRequireVoiceCalibration() {
        var configuration = new ModelConfig("harness-embed-v1", "summary-v1", null);
        assertDoesNotThrow(() -> ConfigurationReleaseService.validate(
            configuration, new Metrics(1, 1, 0, 1), true, Set.of(Kind.MINUTES), configuration));
    }
}
