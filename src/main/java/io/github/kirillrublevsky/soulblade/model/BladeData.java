package io.github.kirillrublevsky.soulblade.model;

import java.time.Instant;
import java.util.UUID;

public record BladeData(UUID id, long kills, long revision, UUID creator, String creatorName,
                        UUID lastOwner, String lastOwnerName, Instant createdAt, String status) {
    public BladeData withKills(long value, long newRevision) {
        return new BladeData(id, value, newRevision, creator, creatorName, lastOwner, lastOwnerName, createdAt, status);
    }
}
