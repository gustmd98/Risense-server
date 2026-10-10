package com.risense.checkin;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Pure validation. The caller supplies authorized, current round targets inside its transaction. */
public final class CheckinSubmissionPolicy {
    private CheckinSubmissionPolicy() {}

    public enum Result {
        ALLOWED, NOT_OPEN, CLOSED, LATE_EDIT_NOT_ALLOWED,
        NO_TARGETS, INVALID_TASK_IDS, TARGETS_CHANGED
    }

    public static Result evaluate(CheckinWindow window, Instant now, boolean alreadySubmitted,
            Set<Long> effectiveTargetIds, List<Long> submittedTaskIds) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(effectiveTargetIds, "effectiveTargetIds");
        var phase = window.phaseAt(now);
        if (phase == CheckinWindow.Phase.NOT_OPEN) return Result.NOT_OPEN;
        if (phase == CheckinWindow.Phase.CLOSED) return Result.CLOSED;
        if (phase == CheckinWindow.Phase.LATE && alreadySubmitted) return Result.LATE_EDIT_NOT_ALLOWED;
        if (effectiveTargetIds.isEmpty()) return Result.NO_TARGETS;
        if (submittedTaskIds == null || submittedTaskIds.stream().anyMatch(id -> id == null || id <= 0)) {
            return Result.INVALID_TASK_IDS;
        }
        var submittedIds = new HashSet<>(submittedTaskIds);
        if (submittedIds.size() != submittedTaskIds.size()) return Result.INVALID_TASK_IDS;
        if (!submittedIds.equals(effectiveTargetIds)) return Result.TARGETS_CHANGED;
        return Result.ALLOWED;
    }
}
