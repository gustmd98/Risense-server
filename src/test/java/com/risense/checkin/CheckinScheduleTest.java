package com.risense.checkin;

import static org.assertj.core.api.Assertions.*;
import com.risense.domain.project.CheckinDay;
import java.time.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CheckinScheduleTest {
    @Test void mondayThursdayWindowUsesKoreanMidnight() {
        var window = CheckinSchedule.windowFor(LocalDate.parse("2026-10-05"), Set.of(CheckinDay.MON, CheckinDay.THU));
        assertThat(window.opensAt()).isEqualTo(Instant.parse("2026-10-04T15:00:00Z"));
        assertThat(window.deadlineAt()).isEqualTo(Instant.parse("2026-10-05T15:00:00Z"));
        assertThat(window.lateUntilAt()).isEqualTo(Instant.parse("2026-10-07T15:00:00Z"));
    }
    @Test void consecutiveDaysHaveNoLatePeriod() {
        var window = CheckinSchedule.windowFor(LocalDate.parse("2026-10-05"), Set.of(CheckinDay.MON, CheckinDay.TUE));
        assertThat(window.lateUntilAt()).isEqualTo(window.deadlineAt());
    }
    @Test void singleWeekdayMovesToNextWeekAcrossYear() {
        var window = CheckinSchedule.windowFor(LocalDate.parse("2026-12-31"), Set.of(CheckinDay.THU));
        assertThat(window.lateUntilAt()).isEqualTo(Instant.parse("2027-01-06T15:00:00Z"));
    }
    @Test void changedScheduleStartsAtOrAfterExistingCutoff() {
        assertThat(CheckinSchedule.firstDateOnOrAfter(Instant.parse("2026-10-07T15:00:00Z"), Set.of(CheckinDay.THU)))
            .isEqualTo(LocalDate.parse("2026-10-08"));
        assertThat(CheckinSchedule.firstDateOnOrAfter(Instant.parse("2026-10-07T15:00:01Z"), Set.of(CheckinDay.THU)))
            .isEqualTo(LocalDate.parse("2026-10-15"));
    }
    @Test void rejectsEmptyScheduleAndUnscheduledRound() {
        assertThatIllegalArgumentException().isThrownBy(() -> CheckinSchedule.windowFor(LocalDate.parse("2026-10-05"), Set.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> CheckinSchedule.windowFor(LocalDate.parse("2026-10-06"), Set.of(CheckinDay.MON)));
    }
    @Test void openedWindowDoesNotChangeWithNewSchedule() {
        var days = java.util.EnumSet.of(CheckinDay.MON, CheckinDay.THU);
        var window = CheckinSchedule.windowFor(LocalDate.parse("2026-10-05"), days);
        days.clear(); days.add(CheckinDay.TUE);
        assertThat(window.lateUntilAt()).isEqualTo(Instant.parse("2026-10-07T15:00:00Z"));
    }
}
