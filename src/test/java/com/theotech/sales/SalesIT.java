package com.theotech.sales;

import com.theotech.TestAuth;
import com.theotech.iam.repository.UserRepository;
import com.theotech.sales.repository.SaleRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Selling: stock goes down, sold goes up, overselling is refused, history and totals are right. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SalesIT {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired SaleRepository sales;
    @Autowired JsonMapper json;

    MockHttpSession admin;
    long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        admin = TestAuth.loginAsAdmin(mvc, users);
        categoryId = data(mvc.perform(get("/api/categories").session(admin)).andReturn()).get(0).path("id").asLong();
    }

    @Test
    void sellingReducesStockAndRecordsTheSaleAtTheProductPrice() throws Exception {
        long earphone = create("Bluetooth Earphone", "15000", 10);

        MvcResult sold = mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(earphone, 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productId").value(earphone))
                .andExpect(jsonPath("$.data.productName").value("Bluetooth Earphone"))
                .andExpect(jsonPath("$.data.quantity").value(3))
                .andExpect(jsonPath("$.data.remainingStock").value(7))
                .andExpect(jsonPath("$.data.soldAt").isString())
                .andReturn();
        JsonNode s = data(sold);
        assertThat(s.path("unitPrice").decimalValue()).isEqualByComparingTo("15000");
        assertThat(s.path("total").decimalValue()).isEqualByComparingTo("45000");

        mvc.perform(get("/api/products/" + earphone).session(admin))
                .andExpect(jsonPath("$.data.initialStock").value(10))
                .andExpect(jsonPath("$.data.soldQuantity").value(3))
                .andExpect(jsonPath("$.data.availableStock").value(7));

        mvc.perform(get("/api/sales/" + s.path("id").asLong()).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(3));
        mvc.perform(get("/api/sales/999999").session(admin)).andExpect(status().isNotFound());
    }

    @Test
    void overSellingIsRefusedAndNothingIsRecorded() throws Exception {
        long speaker = create("Bluetooth Speaker", "30000", 5);
        long before = sales.count();

        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(speaker, 8)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.errors.available").value("5"))
                .andExpect(jsonPath("$.errors.requested").value("8"));

        assertThat(sales.count()).isEqualTo(before);
        mvc.perform(get("/api/products/" + speaker).session(admin))
                .andExpect(jsonPath("$.data.soldQuantity").value(0))
                .andExpect(jsonPath("$.data.availableStock").value(5));

        // exactly the last units can be sold, then nothing more
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(speaker, 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.remainingStock").value(0));
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(speaker, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.available").value("0"));
        mvc.perform(get("/api/products/" + speaker).session(admin))
                .andExpect(jsonPath("$.data.status").value("OUT_OF_STOCK"));

        // bad requests
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(speaker, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").value("positive"));
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, "{\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.productId").value("required"));
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(999999L, 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void historyFiltersSearchesSortsAndTotals() throws Exception {
        long charger = create("Charger", "10000", 30);
        long radio = create("Radio", "18000", 15);
        long earphone = create("Earphone", "15000", 50);
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(charger, 2))).andExpect(status().isCreated());
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(earphone, 3))).andExpect(status().isCreated());
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(radio, 1))).andExpect(status().isCreated());

        // newest first by default; totals cover the whole filtered set
        MvcResult all = mvc.perform(get("/api/sales").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].productName").value("Radio"))
                .andExpect(jsonPath("$.data.content[2].productName").value("Charger"))
                .andExpect(jsonPath("$.data.totalQuantity").value(6))
                .andReturn();
        assertThat(data(all).path("totalAmount").decimalValue()).isEqualByComparingTo("83000");   // 20000 + 45000 + 18000

        // paging keeps the totals of the whole set
        mvc.perform(get("/api/sales").session(admin).param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.totalQuantity").value(6));

        // search by product name
        MvcResult q = mvc.perform(get("/api/sales").session(admin).param("q", "ear"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalQuantity").value(3))
                .andReturn();
        assertThat(data(q).path("totalAmount").decimalValue()).isEqualByComparingTo("45000");

        // sort by total, largest first
        mvc.perform(get("/api/sales").session(admin).param("sort", "total,desc"))
                .andExpect(jsonPath("$.data.content[*].productName", Matchers.contains("Earphone", "Charger", "Radio")));
        mvc.perform(get("/api/sales").session(admin).param("sort", "soldBy"))
                .andExpect(status().isBadRequest());

        // date range: everything is "now"; a window in the future is empty, a window around now has all three
        String hourAgo = Instant.now().minus(1, ChronoUnit.HOURS).toString();
        String inAnHour = Instant.now().plus(1, ChronoUnit.HOURS).toString();
        String tomorrow = Instant.now().plus(1, ChronoUnit.DAYS).toString();
        mvc.perform(get("/api/sales").session(admin).param("from", hourAgo).param("to", inAnHour))
                .andExpect(jsonPath("$.data.totalElements").value(3));
        mvc.perform(get("/api/sales").session(admin).param("from", inAnHour).param("to", tomorrow))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.totalQuantity").value(0));
    }

    @Test
    void deletingAProductKeepsItsSalesHistory() throws Exception {
        long id = create("Old Phone", "50000", 2);
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(id, 1))).andExpect(status().isCreated());
        mvc.perform(TestAuth.withCsrf(mvc, delete("/api/products/" + id), admin)).andExpect(status().isOk());

        mvc.perform(get("/api/sales").session(admin))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].productName").value("Old Phone"));
        // but the deleted product can no longer be sold
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(id, 1)))
                .andExpect(status().isNotFound());
    }

    // ---- helpers ----------------------------------------------------------------------------

    private long create(String name, String price, int initialStock) throws Exception {
        MvcResult r = mvc.perform(TestAuth.json(mvc, post("/api/products"), admin,
                        "{\"name\":\"%s\",\"categoryId\":%d,\"price\":%s,\"initialStock\":%d}".formatted(name, categoryId, price, initialStock)))
                .andExpect(status().isCreated()).andReturn();
        return data(r).path("id").asLong();
    }

    private static String sale(long productId, int quantity) {
        return "{\"productId\":%d,\"quantity\":%d}".formatted(productId, quantity);
    }

    private JsonNode data(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString()).path("data");
    }
}
