package com.theotech.reports.service;

import com.theotech.config.AppProperties;
import com.theotech.settings.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * Sends the day's report once a day, at or after the time set in Settings (shop time zone). Checking every
 * minute rather than at the exact minute means a server that was off or asleep at 20:00 still sends the
 * report when it comes back later that evening; {@code report.last_sent_date} stops it sending twice.
 */
@Component
public class DailyReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyReportScheduler.class);

    private final SettingsService settings;
    private final ReportMailer mailer;
    private final AppProperties props;

    public DailyReportScheduler(SettingsService settings, ReportMailer mailer, AppProperties props) {
        this.settings = settings;
        this.mailer = mailer;
        this.props = props;
    }

    @Scheduled(cron = "0 * * * * *")
    public void tick() {
        try {
            runIfDue(ZonedDateTime.now(props.zoneId()));
        } catch (Exception e) {
            // the next tick retries; the reason is logged once a minute at most while it lasts
            log.warn("Daily report not sent: {}", e.getMessage());
        }
    }

    /** @return true when a report was sent by this call */
    public boolean runIfDue(ZonedDateTime now) {
        if (!settings.dailyReportEnabled()) return false;
        if (!isDue(now, settings.dailyReportTime(), settings.lastReportSentDate())) return false;
        String to = mailer.send(now.toLocalDate().atStartOfDay(now.getZone()).toInstant(), now.toInstant(),
                "daily report for " + now.toLocalDate());
        settings.recordDailyReportSent(now.toLocalDate());
        log.info("Daily sales report for {} sent to {}", now.toLocalDate(), to);
        return true;
    }

    /** Due when today's report has not gone out yet and the configured time has been reached. */
    static boolean isDue(ZonedDateTime now, LocalTime sendAt, LocalDate lastSent) {
        return !now.toLocalDate().equals(lastSent) && !now.toLocalTime().isBefore(sendAt);
    }
}
