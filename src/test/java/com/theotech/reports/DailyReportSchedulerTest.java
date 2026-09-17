package com.theotech.reports;

import com.theotech.reports.service.DailyReportSchedulerTestAccess;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** When the daily report is due: at or after the set time, once per day, catching up after downtime. */
class DailyReportSchedulerTest {

    private static final ZoneId KIGALI = ZoneId.of("Africa/Kigali");
    private static final LocalTime AT_20 = LocalTime.of(20, 0);

    private static ZonedDateTime at(int hour, int minute) {
        return ZonedDateTime.of(2026, 9, 16, hour, minute, 0, 0, KIGALI);
    }

    @Test
    void notDueBeforeTheConfiguredTime() {
        assertThat(DailyReportSchedulerTestAccess.isDue(at(19, 59), AT_20, null)).isFalse();
        assertThat(DailyReportSchedulerTestAccess.isDue(at(9, 0), AT_20, LocalDate.of(2026, 9, 15))).isFalse();
    }

    @Test
    void dueAtTheMinuteAndAnyLaterMinuteThatDay() {
        assertThat(DailyReportSchedulerTestAccess.isDue(at(20, 0), AT_20, null)).isTrue();
        assertThat(DailyReportSchedulerTestAccess.isDue(at(23, 30), AT_20, LocalDate.of(2026, 9, 15))).as("server was off at 20:00").isTrue();
    }

    @Test
    void neverTwiceOnTheSameDay() {
        assertThat(DailyReportSchedulerTestAccess.isDue(at(20, 1), AT_20, LocalDate.of(2026, 9, 16))).isFalse();
        assertThat(DailyReportSchedulerTestAccess.isDue(at(23, 59), AT_20, LocalDate.of(2026, 9, 16))).isFalse();
    }
}
