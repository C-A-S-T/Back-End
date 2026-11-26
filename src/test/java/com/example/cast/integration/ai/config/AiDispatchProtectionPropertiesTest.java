package com.example.cast.integration.ai.config;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AiDispatchProtectionPropertiesTest {
    private static final String ID_A = "00000000-0000-0000-0000-000000000001";
    private static final String ID_B = "00000000-0000-0000-0000-000000000002";

    @Test
    void validGateHasCutoverAndProtectedIds() {
        var gate = new AiDispatchProtectionProperties("2026-12-01T00:00:00Z", ID_A + ", " + ID_B)
                .requiredGate();
        assertEquals(Instant.parse("2026-12-01T00:00:00Z"), gate.cutoverAt());
        assertEquals(java.util.List.of(UUID.fromString(ID_A), UUID.fromString(ID_B)), gate.protectedJobIds());
    }

    @Test
    void missingSettingsFailClosed() {
        assertThrows(IllegalStateException.class,
                () -> new AiDispatchProtectionProperties(null, null).requiredGate());
        assertThrows(IllegalStateException.class,
                () -> new AiDispatchProtectionProperties("2026-12-01T00:00:00Z", "").requiredGate());
        assertThrows(IllegalStateException.class,
                () -> new AiDispatchProtectionProperties("", ID_A).requiredGate());
    }

    @Test
    void malformedOrDuplicateSettingsFailClosed() {
        assertThrows(IllegalStateException.class,
                () -> new AiDispatchProtectionProperties("not-an-instant", ID_A).requiredGate());
        assertThrows(IllegalStateException.class,
                () -> new AiDispatchProtectionProperties("2026-12-01T00:00:00Z", "bad-uuid").requiredGate());
        assertThrows(IllegalStateException.class,
                () -> new AiDispatchProtectionProperties("2026-12-01T00:00:00Z", ID_A + "," + ID_A)
                        .requiredGate());
        assertThrows(IllegalStateException.class,
                () -> new AiDispatchProtectionProperties("2026-12-01T00:00:00Z", ID_A + ",")
                        .requiredGate());
    }
}
