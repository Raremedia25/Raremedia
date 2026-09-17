package com.theotech.expenses;

import com.theotech.TestAuth;
import com.theotech.iam.repository.UserRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Expenses: add / edit / delete, filters and totals, admin-only, and their place in the report and dashboard. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExpensesIT {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JsonMapper json;

    MockHttpSession admin;
    LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        admin = TestAuth.loginAsAdmin(mvc, users);
        today = LocalDate.now(ZoneId.of("Africa/Kigali"));
    }

    @Test
    void crudFiltersAndTotals() throws Exception {
        long rent = create(today.minusDays(3), " transport ", "Moto to Nyabugogo market", "3000", null);
        create(today, "Rent", "Shop rent for the month", "150000", "paid to landlord");
        create(today, "Airtime", "MTN airtime", "2000", null);

        // category spelling is tidied
        mvc.perform(get("/api/expenses/" + rent).session(admin))
                .andExpect(jsonPath("$.data.category").value("Transport"))
                .andExpect(jsonPath("$.data.recordedByName").value("System Administrator"));

        // list: newest first, total of the whole filtered set
        MvcResult all = mvc.perform(get("/api/expenses").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[*].spentOn", Matchers.hasItem(today.toString())))
                .andReturn();
        assertThat(data(all).path("totalAmount").decimalValue()).isEqualByComparingTo("155000");

        MvcResult todayOnly = mvc.perform(get("/api/expenses").session(admin).param("from", today.toString()).param("to", today.toString()))
                .andExpect(jsonPath("$.data.totalElements").value(2)).andReturn();
        assertThat(data(todayOnly).path("totalAmount").decimalValue()).isEqualByComparingTo("152000");
        mvc.perform(get("/api/expenses").session(admin).param("category", "rent"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/expenses").session(admin).param("q", "landlord"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].category").value("Rent"));
        mvc.perform(get("/api/expenses").session(admin).param("sort", "amount,desc"))
                .andExpect(jsonPath("$.data.content[0].category").value("Rent"))
                .andExpect(jsonPath("$.data.content[2].category").value("Airtime"));
        mvc.perform(get("/api/expenses/categories").session(admin))
                .andExpect(jsonPath("$.data", Matchers.contains("Airtime", "Rent", "Transport")));

        // edit, delete
        mvc.perform(TestAuth.json(mvc, put("/api/expenses/" + rent), admin,
                        body(today.minusDays(2), "Transport", "Moto to market and back", "4500", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("Moto to market and back"));
        mvc.perform(TestAuth.withCsrf(mvc, delete("/api/expenses/" + rent), admin)).andExpect(status().isOk());
        mvc.perform(get("/api/expenses/" + rent).session(admin)).andExpect(status().isNotFound());

        // validation
        mvc.perform(TestAuth.json(mvc, post("/api/expenses"), admin, "{\"spentOn\":\"" + today + "\",\"category\":\"\",\"description\":\"x\",\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.category").value("required"))
                .andExpect(jsonPath("$.errors.amount").value("positive"));
    }

    @Test
    void reportAndDashboardIncludeExpenses() throws Exception {
        long categoryId = data(mvc.perform(get("/api/categories").session(admin)).andReturn()).get(0).path("id").asLong();
        long charger = data(mvc.perform(TestAuth.json(mvc, post("/api/products"), admin,
                        "{\"name\":\"Charger\",\"categoryId\":%d,\"price\":10000,\"initialStock\":30}".formatted(categoryId)))
                .andExpect(status().isCreated()).andReturn()).path("id").asLong();
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, "{\"productId\":%d,\"quantity\":5}".formatted(charger)))
                .andExpect(status().isCreated());
        create(today, "Rent", "Shop rent", "20000", null);
        create(today, "Transport", "Moto", "1500", null);
        create(today.minusDays(40), "Rent", "Last month", "20000", null);   // outside "today"

        MvcResult r = mvc.perform(get("/api/reports/sales").session(admin)
                        .param("from", today.atStartOfDay(ZoneId.of("Africa/Kigali")).toInstant().toString())
                        .param("to", today.plusDays(1).atStartOfDay(ZoneId.of("Africa/Kigali")).toInstant().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expensesByCategory.length()").value(2))
                .andExpect(jsonPath("$.data.expensesByCategory[0].category").value("Rent"))
                .andReturn();
        JsonNode d = data(r);
        assertThat(d.path("totalSales").decimalValue()).isEqualByComparingTo("50000");
        assertThat(d.path("totalExpenses").decimalValue()).isEqualByComparingTo("21500");
        assertThat(d.path("netAfterExpenses").decimalValue()).isEqualByComparingTo("28500");

        MvcResult dash = mvc.perform(get("/api/dashboard").session(admin)).andExpect(status().isOk()).andReturn();
        assertThat(data(dash).path("totalExpenses").decimalValue()).isEqualByComparingTo("41500");
    }

    @Test
    void workersCannotSeeOrRecordExpenses() throws Exception {
        mvc.perform(TestAuth.json(mvc, post("/api/workers"), admin,
                        "{\"fullName\":\"Alice Worker\",\"username\":\"alice\",\"password\":\"Worker2026\"}"))
                .andExpect(status().isCreated());
        users.findActiveByUsername("alice").ifPresent(u -> u.setMustChangePassword(false));
        MockHttpSession alice = TestAuth.login(mvc, "alice", "Worker2026");

        mvc.perform(get("/api/expenses").session(alice)).andExpect(status().isForbidden());
        mvc.perform(TestAuth.json(mvc, post("/api/expenses"), alice, body(today, "Rent", "x", "100", null)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard").session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalExpenses").value(Matchers.nullValue()));
    }

    // ---- helpers ----------------------------------------------------------------------------

    private long create(LocalDate on, String category, String description, String amount, String note) throws Exception {
        MvcResult r = mvc.perform(TestAuth.json(mvc, post("/api/expenses"), admin, body(on, category, description, amount, note)))
                .andExpect(status().isCreated()).andReturn();
        return data(r).path("id").asLong();
    }

    private static String body(LocalDate on, String category, String description, String amount, String note) {
        return "{\"spentOn\":\"%s\",\"category\":\"%s\",\"description\":\"%s\",\"amount\":%s%s}"
                .formatted(on, category, description, amount, note == null ? "" : ",\"note\":\"" + note + "\"");
    }

    private JsonNode data(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString()).path("data");
    }
}
