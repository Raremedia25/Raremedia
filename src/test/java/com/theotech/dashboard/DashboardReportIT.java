package com.theotech.dashboard;

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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The four dashboard figures, low/out-of-stock lists, the sales report and the settings that drive them. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardReportIT {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JsonMapper json;

    MockHttpSession admin;
    long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        admin = TestAuth.loginAsAdmin(mvc, users);
        categoryId = data(mvc.perform(get("/api/categories").session(admin)).andReturn()).get(0).path("id").asLong();
    }

    @Test
    void dashboardFiguresAreLiveAndListsAreCorrect() throws Exception {
        // empty shop first
        mvc.perform(get("/api/dashboard").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalProducts").value(0))
                .andExpect(jsonPath("$.data.itemsInStock").value(0))
                .andExpect(jsonPath("$.data.itemsSold").value(0))
                .andExpect(jsonPath("$.data.lowStockThreshold").value(5))
                .andExpect(jsonPath("$.data.recentSales").isEmpty());

        long charger = create("Charger", "10000", 30);      // sells 12 → 18 available
        long speaker = create("Bluetooth Speaker", "30000", 10);   // sells 7 → 3 low
        long cable = create("USB Cable", "5000", 4);              // sells 4 → 0 out
        create("Power Bank", "25000", 2);                          // 2 low, never sold
        sell(charger, 12);
        sell(speaker, 7);
        sell(cable, 4);

        MvcResult r = mvc.perform(get("/api/dashboard").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalProducts").value(4))
                .andExpect(jsonPath("$.data.itemsInStock").value(18 + 3 + 2))
                .andExpect(jsonPath("$.data.itemsSold").value(23))
                .andExpect(jsonPath("$.data.lowStock[*].name", Matchers.contains("Power Bank", "Bluetooth Speaker")))
                .andExpect(jsonPath("$.data.lowStock[0].availableStock").value(2))
                .andExpect(jsonPath("$.data.outOfStock[*].name", Matchers.contains("USB Cable")))
                .andExpect(jsonPath("$.data.recentSales.length()").value(3))
                .andExpect(jsonPath("$.data.recentSales[0].productName").value("USB Cable"))
                .andReturn();
        // 12×10000 + 7×30000 + 4×5000
        assertThat(data(r).path("totalSales").decimalValue()).isEqualByComparingTo("350000");
    }

    @Test
    void salesReportGroupsByProductWithRemainingStock() throws Exception {
        long charger = create("Charger", "10000", 30);
        long earphone = create("Earphone", "15000", 50);
        long radio = create("Radio", "18000", 15);
        create("Never Sold", "1000", 9);
        sell(charger, 10);
        sell(charger, 5);
        sell(earphone, 20);
        sell(radio, 5);

        MvcResult r = mvc.perform(get("/api/reports/sales").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows.length()").value(3))
                .andExpect(jsonPath("$.data.rows[0].productName").value("Earphone"))    // biggest sales first
                .andExpect(jsonPath("$.data.rows[0].quantitySold").value(20))
                .andExpect(jsonPath("$.data.rows[0].remainingStock").value(30))
                .andExpect(jsonPath("$.data.rows[1].productName").value("Charger"))
                .andExpect(jsonPath("$.data.rows[1].quantitySold").value(15))
                .andExpect(jsonPath("$.data.rows[1].remainingStock").value(15))
                .andExpect(jsonPath("$.data.rows[2].productName").value("Radio"))
                .andExpect(jsonPath("$.data.rows[2].remainingStock").value(10))
                .andExpect(jsonPath("$.data.totalQuantity").value(40))
                .andReturn();
        JsonNode d = data(r);
        assertThat(d.path("rows").get(0).path("totalSales").decimalValue()).isEqualByComparingTo("300000");
        assertThat(d.path("rows").get(1).path("totalSales").decimalValue()).isEqualByComparingTo("150000");
        assertThat(d.path("totalSales").decimalValue()).isEqualByComparingTo("540000");

        // a period with no sales
        String inAnHour = Instant.now().plus(1, ChronoUnit.HOURS).toString();
        String tomorrow = Instant.now().plus(1, ChronoUnit.DAYS).toString();
        mvc.perform(get("/api/reports/sales").session(admin).param("from", inAnHour).param("to", tomorrow))
                .andExpect(jsonPath("$.data.rows").isEmpty())
                .andExpect(jsonPath("$.data.totalQuantity").value(0));
        // a period around now has everything
        String hourAgo = Instant.now().minus(1, ChronoUnit.HOURS).toString();
        mvc.perform(get("/api/reports/sales").session(admin).param("from", hourAgo).param("to", inAnHour))
                .andExpect(jsonPath("$.data.rows.length()").value(3));
    }

    @Test
    void settingsHaveDefaultsAndCanBeChanged() throws Exception {
        mvc.perform(get("/api/settings").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyName").value("THEO TECH LTD"))
                .andExpect(jsonPath("$.data.lowStockThreshold").value(5));

        mvc.perform(TestAuth.json(mvc, put("/api/settings"), admin,
                        "{\"companyName\":\"THEO TECH LTD - Nyabugogo\",\"companyAddress\":\"Nyabugogo, Kigali\",\"companyPhone\":\"078 000 0000\",\"lowStockThreshold\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyName").value("THEO TECH LTD - Nyabugogo"))
                .andExpect(jsonPath("$.data.companyPhone").value("078 000 0000"))
                .andExpect(jsonPath("$.data.lowStockThreshold").value(10));
        mvc.perform(get("/api/dashboard").session(admin))
                .andExpect(jsonPath("$.data.lowStockThreshold").value(10));

        mvc.perform(TestAuth.json(mvc, put("/api/settings"), admin, "{\"companyName\":\"\",\"lowStockThreshold\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.companyName").value("required"))
                .andExpect(jsonPath("$.errors.lowStockThreshold").value("min"));
    }

    // ---- helpers ----------------------------------------------------------------------------

    private long create(String name, String price, int initialStock) throws Exception {
        MvcResult r = mvc.perform(TestAuth.json(mvc, post("/api/products"), admin,
                        "{\"name\":\"%s\",\"categoryId\":%d,\"price\":%s,\"initialStock\":%d}".formatted(name, categoryId, price, initialStock)))
                .andExpect(status().isCreated()).andReturn();
        return data(r).path("id").asLong();
    }

    private void sell(long productId, int quantity) throws Exception {
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, "{\"productId\":%d,\"quantity\":%d}".formatted(productId, quantity)))
                .andExpect(status().isCreated());
    }

    private JsonNode data(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString()).path("data");
    }
}
