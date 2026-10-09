package com.risense.checkin;

import com.risense.domain.project.CheckinDay;
import java.time.*;
import java.util.Objects;
import java.util.Set;

/** Pure schedule calculations; independent of the host timezone and database. */
public final class CheckinSchedule {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private CheckinSchedule() {}

    public static CheckinWindow windowFor(LocalDate date, Set<CheckinDay> days) {
        Objects.requireNonNull(date, "date");
        var schedule = validated(days);
        if (!includes(schedule, date)) {
            throw new IllegalArgumentException("Round date must be a scheduled weekday");
        }
        var next = date.plusDays(1);
        while (!includes(schedule, next)) next = next.plusDays(1);
        return new CheckinWindow(midnight(date), midnight(date.plusDays(1)), midnight(next));
    }

    /** Finds a scheduled midnight at or after the previous round's immutable cutoff. */
    public static LocalDate firstDateOnOrAfter(Instant cutoff, Set<CheckinDay> days) {
        Objects.requireNonNull(cutoff, "cutoff");
        var schedule = validated(days);
        var date = cutoff.atZone(ZONE).toLocalDate();
        if (midnight(date).isBefore(cutoff)) date = date.plusDays(1);
        while (!includes(schedule, date)) date = date.plusDays(1);
        return date;
    }

    private static Set<CheckinDay> validated(Set<CheckinDay> days) {
        Objects.requireNonNull(days, "days");
        if (days.isEmpty()) throw new IllegalArgumentException("At least one weekday is required");
        return Set.copyOf(days);
    }

    private static boolean includes(Set<CheckinDay> days, LocalDate date) {
        return days.stream().anyMatch(day -> weekday(day) == date.getDayOfWeek());
    }

    private static DayOfWeek weekday(CheckinDay day) {
        return switch (day) {
            case MON -> DayOfWeek.MONDAY;
            case TUE -> DayOfWeek.TUESDAY;
            case WED -> DayOfWeek.WEDNESDAY;
            case THU -> DayOfWeek.THURSDAY;
            case FRI -> DayOfWeek.FRIDAY;
            case SAT -> DayOfWeek.SATURDAY;
            case SUN -> DayOfWeek.SUNDAY;
        };
    }

    private static Instant midnight(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }
}
