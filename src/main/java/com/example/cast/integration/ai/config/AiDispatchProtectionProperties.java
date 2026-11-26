package com.example.cast.integration.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** 운영 전환 이후의 신규 작업만 전송하기 위한 보호 게이트. */
@ConfigurationProperties(prefix = "cast.ai.dispatch-protection")
public record AiDispatchProtectionProperties(String cutoverAt, String protectedJobIds) {

    /** 전환 시각과 보호 목록이 모두 유효한 경우에만 게이트를 반환한다. */
    public Gate requiredGate() {
        if (cutoverAt == null || cutoverAt.isBlank()
                || protectedJobIds == null || protectedJobIds.isBlank()) {
            throw new IllegalStateException("AI dispatch protection requires cutoverAt and protectedJobIds");
        }
        Instant cutoff;
        try {
            cutoff = Instant.parse(cutoverAt.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalStateException("AI dispatch cutoverAt must be an ISO-8601 UTC instant", exception);
        }
        var ids = new ArrayList<UUID>();
        var seen = new HashSet<UUID>();
        for (String raw : protectedJobIds.split(",", -1)) {
            if (raw.isBlank()) {
                throw new IllegalStateException("AI dispatch protectedJobIds contains a blank entry");
            }
            UUID id;
            try {
                id = UUID.fromString(raw.trim());
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("AI dispatch protectedJobIds contains an invalid UUID", exception);
            }
            if (!seen.add(id)) {
                throw new IllegalStateException("AI dispatch protectedJobIds contains a duplicate UUID");
            }
            ids.add(id);
        }
        if (ids.isEmpty()) {
            throw new IllegalStateException("AI dispatch protectedJobIds cannot be empty");
        }
        return new Gate(cutoff, List.copyOf(ids));
    }

    public record Gate(Instant cutoverAt, List<UUID> protectedJobIds) {}
}
