package com.risense.checkin;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CheckinSubmissionPolicyTest {
    private final Instant start = Instant.parse("2026-10-08T15:00:00Z");
    private final Instant deadline = Instant.parse("2026-10-09T15:00:00Z");
    private final Instant cutoff = Instant.parse("2026-10-11T15:00:00Z");
    private final CheckinWindow window = new CheckinWindow(start, deadline, cutoff);

    @Test void unchangedFullSubmissionAndOnTimeEditAreAllowed() {
        assertThat(CheckinSubmissionPolicy.evaluate(window, start, false, Set.of(1L, 2L), List.of(2L, 1L)))
            .isEqualTo(CheckinSubmissionPolicy.Result.ALLOWED);
        assertThat(CheckinSubmissionPolicy.evaluate(window, deadline.minusNanos(1), true, Set.of(1L), List.of(1L)))
            .isEqualTo(CheckinSubmissionPolicy.Result.ALLOWED);
    }
    @Test void missingAndExtraTasksRequireReload() {
        assertThat(CheckinSubmissionPolicy.evaluate(window, start, false, Set.of(1L, 2L), List.of(1L)))
            .isEqualTo(CheckinSubmissionPolicy.Result.TARGETS_CHANGED);
        assertThat(CheckinSubmissionPolicy.evaluate(window, start, false, Set.of(1L), List.of(1L, 2L)))
            .isEqualTo(CheckinSubmissionPolicy.Result.TARGETS_CHANGED);
    }
    @Test void repeatedIdsCannotStandInForAllTargets() {
        assertThat(CheckinSubmissionPolicy.evaluate(window, start, false, Set.of(1L, 2L), List.of(1L, 1L)))
            .isEqualTo(CheckinSubmissionPolicy.Result.INVALID_TASK_IDS);
    }
    @Test void rejectsNullAndNonPositiveRequestIds() {
        for (var ids : List.of(java.util.Arrays.asList(1L, null), List.of(0L), List.of(-1L))) {
            assertThat(CheckinSubmissionPolicy.evaluate(window, start, false, Set.of(1L), ids))
                .isEqualTo(CheckinSubmissionPolicy.Result.INVALID_TASK_IDS);
        }
        assertThat(CheckinSubmissionPolicy.evaluate(window, start, false, Set.of(1L), null))
            .isEqualTo(CheckinSubmissionPolicy.Result.INVALID_TASK_IDS);
    }
    @Test void noTargetsNeverCreatesSubmission() {
        assertThat(CheckinSubmissionPolicy.evaluate(window, start, false, Set.of(), List.of()))
            .isEqualTo(CheckinSubmissionPolicy.Result.NO_TARGETS);
    }
    @Test void latePeriodAllowsOnlyFirstSubmission() {
        assertThat(CheckinSubmissionPolicy.evaluate(window, deadline, false, Set.of(1L), List.of(1L)))
            .isEqualTo(CheckinSubmissionPolicy.Result.ALLOWED);
        assertThat(CheckinSubmissionPolicy.evaluate(window, deadline, true, Set.of(1L), List.of(1L)))
            .isEqualTo(CheckinSubmissionPolicy.Result.LATE_EDIT_NOT_ALLOWED);
    }
    @Test void rejectsNotYetOpenAndClosedRounds() {
        assertThat(CheckinSubmissionPolicy.evaluate(window, start.minusNanos(1), false, Set.of(1L), List.of(1L)))
            .isEqualTo(CheckinSubmissionPolicy.Result.NOT_OPEN);
        assertThat(CheckinSubmissionPolicy.evaluate(window, cutoff, false, Set.of(1L), List.of(1L)))
            .isEqualTo(CheckinSubmissionPolicy.Result.CLOSED);
    }
}
