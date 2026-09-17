package com.theotech.reports;

import com.theotech.TestAuth;
import com.theotech.common.mail.EmailSender;
import com.theotech.iam.repository.UserRepository;
import com.theotech.reports.service.DailyReportScheduler;
import com.theotech.settings.service.SettingsService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The e-mailed report: settings, the "send now" button, the daily schedule. SMTP itself is replaced by a mock. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportEmailIT {

    private static final String MAIL_SETTINGS = """
            {"reportEmail":"owner@example.com","dailyReportEnabled":%s,"dailyReportTime":"20:00",
             "mailHost":"smtp.example.com","mailPort":587,"mailUsername":"shop@example.com","mailPassword":"%s","mailFrom":""}
            """;

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired SettingsService settings;
    @Autowired DailyReportScheduler scheduler;
    @Autowired JsonMapper json;
    @MockitoBean EmailSender emailSender;

    MockHttpSession admin;
    long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        admin = TestAuth.loginAsAdmin(mvc, users);
        categoryId = data(mvc.perform(get("/api/categories").session(admin)).andReturn()).get(0).path("id").asLong();
    }

    @Test
    void mailSettingsAreSavedAndThePasswordNeverComesBack() throws Exception {
        mvc.perform(get("/api/settings/mail").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportEmail").value(""))
                .andExpect(jsonPath("$.data.dailyReportEnabled").value(false))
                .andExpect(jsonPath("$.data.dailyReportTime").value("20:00"))
                .andExpect(jsonPath("$.data.mailPort").value(587))
                .andExpect(jsonPath("$.data.mailPasswordSet").value(false))
                .andExpect(jsonPath("$.data.mailConfigured").value(false));

        MvcResult saved = mvc.perform(TestAuth.json(mvc, put("/api/settings/mail"), admin, MAIL_SETTINGS.formatted("true", "app-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportEmail").value("owner@example.com"))
                .andExpect(jsonPath("$.data.dailyReportEnabled").value(true))
                .andExpect(jsonPath("$.data.mailHost").value("smtp.example.com"))
                .andExpect(jsonPath("$.data.mailPasswordSet").value(true))
                .andExpect(jsonPath("$.data.mailConfigured").value(true))
                .andReturn();
        assertThat(saved.getResponse().getContentAsString()).doesNotContain("app-secret").doesNotContain("mailPassword\"");
        assertThat(settings.mail().password()).isEqualTo("app-secret");

        // an empty password keeps the stored one; a bad address or time is refused
        mvc.perform(TestAuth.json(mvc, put("/api/settings/mail"), admin, MAIL_SETTINGS.formatted("false", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mailPasswordSet").value(true));
        assertThat(settings.mail().password()).isEqualTo("app-secret");
        mvc.perform(TestAuth.json(mvc, put("/api/settings/mail"), admin,
                        "{\"reportEmail\":\"not-an-address\",\"dailyReportEnabled\":true,\"dailyReportTime\":\"25:99\",\"mailPort\":587}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reportEmail").value("email"))
                .andExpect(jsonPath("$.errors.dailyReportTime").value("time"));
    }

    @Test
    void sendNowEmailsTheReportWithPaidUnpaidAndCustomer() throws Exception {
        long charger = create("Charger", "10000", 30);
        long radio = create("Radio", "18000", 15);
        sell(charger, 2, true, null);
        sell(radio, 1, false, "Mukamana Alice");

        // no address yet → clear error, nothing sent
        mvc.perform(TestAuth.withCsrf(mvc, post("/api/reports/sales/email"), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPORT_EMAIL_MISSING"));
        verify(emailSender, never()).send(anyString(), anyString(), anyString());

        mvc.perform(TestAuth.json(mvc, put("/api/settings/mail"), admin, MAIL_SETTINGS.formatted("false", "pw"))).andExpect(status().isOk());
        mvc.perform(TestAuth.withCsrf(mvc, post("/api/reports/sales/email"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sentTo").value("owner@example.com"));

        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailSender, times(1)).send(to.capture(), subject.capture(), html.capture());
        assertThat(to.getValue()).isEqualTo("owner@example.com");
        assertThat(subject.getValue()).contains("sales report").contains("RWF 38,000").contains("RWF 18,000 not paid");
        assertThat(html.getValue())
                .contains("Charger").contains("Radio")
                .contains("Mukamana Alice")
                .contains("RWF 20,000")      // paid
                .contains("RWF 18,000")      // not paid
                .contains("Sales per product").contains("Not paid").contains("Low stock");

        // the test e-mail uses the same sender and address
        mvc.perform(TestAuth.withCsrf(mvc, post("/api/settings/mail/test"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sentTo").value("owner@example.com"));
        verify(emailSender, times(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    void dailyScheduleSendsOncePerDayAtOrAfterTheSetTime() throws Exception {
        long charger = create("Charger", "10000", 30);
        sell(charger, 3, true, null);
        ZoneId zone = ZoneId.of("Africa/Kigali");
        // a simulated clock after 20:00 today that is also after the sale just recorded, so the day's report contains it
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime evening = now.getHour() >= 20 ? now.plusMinutes(1) : now.withHour(20).withMinute(30);

        // disabled → nothing, even after the time
        assertThat(scheduler.runIfDue(evening)).isFalse();

        mvc.perform(TestAuth.json(mvc, put("/api/settings/mail"), admin, MAIL_SETTINGS.formatted("true", "pw"))).andExpect(status().isOk());
        assertThat(scheduler.runIfDue(evening.withHour(19).withMinute(0))).as("before 20:00").isFalse();
        assertThat(scheduler.runIfDue(evening)).as("first time after 20:00").isTrue();
        assertThat(scheduler.runIfDue(evening.plusMinutes(1))).as("same day again").isFalse();
        assertThat(settings.lastReportSentDate()).isEqualTo(evening.toLocalDate());
        assertThat(scheduler.runIfDue(evening.plusDays(1))).as("next day").isTrue();

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(emailSender, times(2)).send(anyString(), subject.capture(), anyString());
        assertThat(subject.getAllValues().getFirst()).contains("RWF 30,000");
        mvc.perform(get("/api/settings/mail").session(admin))
                .andExpect(jsonPath("$.data.lastReportSentDate", Matchers.notNullValue()));
    }

    // ---- helpers ----------------------------------------------------------------------------

    private long create(String name, String price, int initialStock) throws Exception {
        MvcResult r = mvc.perform(TestAuth.json(mvc, post("/api/products"), admin,
                        "{\"name\":\"%s\",\"categoryId\":%d,\"price\":%s,\"initialStock\":%d}".formatted(name, categoryId, price, initialStock)))
                .andExpect(status().isCreated()).andReturn();
        return data(r).path("id").asLong();
    }

    private void sell(long productId, int quantity, boolean paid, String customer) throws Exception {
        String body = "{\"productId\":%d,\"quantity\":%d,\"paid\":%s%s}".formatted(productId, quantity, paid,
                customer == null ? "" : ",\"customerName\":\"" + customer + "\"");
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, body)).andExpect(status().isCreated());
    }

    private JsonNode data(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString()).path("data");
    }
}
