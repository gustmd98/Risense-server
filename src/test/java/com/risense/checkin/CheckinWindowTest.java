package com.risense.checkin;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CheckinWindowTest {
    private final Instant open = Instant.parse("2026-10-04T15:00:00Z");
    private final Instant deadline = Instant.parse("2026-10-05T15:00:00Z");
    private final Instant cutoff = Instant.parse("2026-10-07T15:00:00Z");
    private final CheckinWindow window = new CheckinWindow(open, deadline, cutoff);

    @Test void includesStartButExcludesDeadlineFromOnTime() {
        assertThat(window.phaseAt(open.minusNanos(1))).isEqualTo(CheckinWindow.Phase.NOT_OPEN);
        assertThat(window.phaseAt(open)).isEqualTo(CheckinWindow.Phase.ON_TIME);
        assertThat(window.phaseAt(deadline.minusNanos(1))).isEqualTo(CheckinWindow.Phase.ON_TIME);
        assertThat(window.phaseAt(deadline)).isEqualTo(CheckinWindow.Phase.LATE);
        assertThat(window.phaseAt(cutoff.minusNanos(1))).isEqualTo(CheckinWindow.Phase.LATE);
        assertThat(window.phaseAt(cutoff)).isEqualTo(CheckinWindow.Phase.CLOSED);
    }
    @Test void onTimeAllowsFirstSubmissionAndEdits() {
        assertThat(window.allowsSubmission(open, false)).isTrue();
        assertThat(window.allowsSubmission(open, true)).isTrue();
    }
    @Test void lateAllowsOnlyFirstSubmission() {
        assertThat(window.allowsSubmission(deadline, false)).isTrue();
        assertThat(window.allowsSubmission(deadline, true)).isFalse();
    }
    @Test void closedAndNotYetOpenedRejectSubmission() {
        assertThat(window.allowsSubmission(open.minusNanos(1), false)).isFalse();
        assertThat(window.allowsSubmission(cutoff, false)).isFalse();
        assertThat(window.allowsSubmission(cutoff, true)).isFalse();
    }
    @Test void consecutiveScheduleClosesWithoutLatePhase() {
        var consecutive = new CheckinWindow(open, deadline, deadline);
        assertThat(consecutive.phaseAt(deadline)).isEqualTo(CheckinWindow.Phase.CLOSED);
        assertThat(consecutive.allowsSubmission(deadline, false)).isFalse();
    }
    @Test void rejectsReversedAndEmptyOnTimeWindows() {
        assertThatIllegalArgumentException().isThrownBy(() -> new CheckinWindow(open, open, cutoff));
        assertThatIllegalArgumentException().isThrownBy(() -> new CheckinWindow(deadline, open, cutoff));
        assertThatIllegalArgumentException().isThrownBy(() -> new CheckinWindow(open, deadline, open));
    }
}
