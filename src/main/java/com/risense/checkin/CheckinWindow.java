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
}
