package com.theotech.reports.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/** Exposes the package-private due rule to the unit test in {@code com.theotech.reports}. */
public final class DailyReportSchedulerTestAccess {

    private DailyReportSchedulerTestAccess() {
    }

    public static boolean isDue(ZonedDateTime now, LocalTime sendAt, LocalDate lastSent) {
        return DailyReportScheduler.isDue(now, sendAt, lastSent);
    }
}
