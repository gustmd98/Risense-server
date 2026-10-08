package com.risense.checkin;

import java.time.Instant;
import java.util.Objects;

/** Immutable time boundaries captured when a round opens. End boundaries are exclusive. */
public record CheckinWindow(Instant opensAt, Instant deadlineAt, Instant lateUntilAt) {
    public CheckinWindow {
        Objects.requireNonNull(opensAt, "opensAt");
        Objects.requireNonNull(deadlineAt, "deadlineAt");
        Objects.requireNonNull(lateUntilAt, "lateUntilAt");
        if (!opensAt.isBefore(deadlineAt) || lateUntilAt.isBefore(deadlineAt)) {
            throw new IllegalArgumentException("Invalid check-in window boundaries");
        }
    }
    public enum Phase { NOT_OPEN, ON_TIME, LATE, CLOSED }

    public Phase phaseAt(Instant now) {
        Objects.requireNonNull(now, "now");
        if (now.isBefore(opensAt)) return Phase.NOT_OPEN;
        if (now.isBefore(deadlineAt)) return Phase.ON_TIME;
        if (now.isBefore(lateUntilAt)) return Phase.LATE;
        return Phase.CLOSED;
    }

    /** Time permission only. Callers must also validate project and membership permissions. */
    public boolean allowsSubmission(Instant now, boolean alreadySubmitted) {
        return switch (phaseAt(now)) {
            case ON_TIME -> true;
            case LATE -> !alreadySubmitted;
            case NOT_OPEN, CLOSED -> false;
        };
    }
}
