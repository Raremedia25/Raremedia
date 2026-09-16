package com.theotech.catalog;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Products: add, edit, search, delete, add stock, categories — and the stock status that drives the badges. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductStockIT {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JsonMapper json;

    MockHttpSession admin;
    long chargers;
    long phones;

    @BeforeEach
    void setUp() throws Exception {
        admin = TestAuth.loginAsAdmin(mvc, users);
        chargers = categoryId("Chargers");
        phones = categoryId("Phones");
    }

    @Test
    void addEditSearchAndDeleteProducts() throws Exception {
        MvcResult created = mvc.perform(TestAuth.json(mvc, post("/api/products"), admin,
                        product("Samsung Charger", chargers, "10000", 20)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Samsung Charger"))
                .andExpect(jsonPath("$.data.categoryName").value("Chargers"))
                .andExpect(jsonPath("$.data.initialStock").value(20))
                .andExpect(jsonPath("$.data.soldQuantity").value(0))
                .andExpect(jsonPath("$.data.availableStock").value(20))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andReturn();
        long id = data(created).path("id").asLong();
        assertThat(data(created).path("price").decimalValue()).isEqualByComparingTo("10000");

        // same name (any case) is refused
        mvc.perform(TestAuth.json(mvc, post("/api/products"), admin, product("samsung charger", phones, "1", 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PRODUCT"));

        create("Type-C Cable", chargers, "5000", 50);
        create("Tecno Spark", phones, "120000", 0);

        // search by name and by category, filter by category
        mvc.perform(get("/api/products").session(admin))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].name").value("Samsung Charger"));   // sorted by name
        mvc.perform(get("/api/products").session(admin).param("q", "charger"))
                .andExpect(jsonPath("$.data[*].name", Matchers.contains("Samsung Charger", "Type-C Cable")));
        mvc.perform(get("/api/products").session(admin).param("q", "tecno"))
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/products").session(admin).param("categoryId", String.valueOf(phones)))
                .andExpect(jsonPath("$.data[*].name", Matchers.contains("Tecno Spark")));
        mvc.perform(get("/api/products/" + id).session(admin))
                .andExpect(jsonPath("$.data.name").value("Samsung Charger"));

        // edit: rename, re-price, move category, correct the stocked total
        mvc.perform(TestAuth.json(mvc, put("/api/products/" + id), admin, product("Samsung 25W Charger", phones, "12000", 25)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Samsung 25W Charger"))
                .andExpect(jsonPath("$.data.categoryName").value("Phones"))
                .andExpect(jsonPath("$.data.availableStock").value(25));
        // renaming onto another product's name is refused too
        mvc.perform(TestAuth.json(mvc, put("/api/products/" + id), admin, product("Type-C Cable", phones, "12000", 25)))
                .andExpect(status().isConflict());

        // delete hides the product and frees the name
        mvc.perform(TestAuth.withCsrf(mvc, delete("/api/products/" + id), admin)).andExpect(status().isOk());
        mvc.perform(get("/api/products/" + id).session(admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products").session(admin)).andExpect(jsonPath("$.data.length()").value(2));
        create("Samsung 25W Charger", chargers, "12000", 1);
        mvc.perform(TestAuth.withCsrf(mvc, delete("/api/products/999999"), admin)).andExpect(status().isNotFound());
    }

    @Test
    void validationReturnsFieldKeys() throws Exception {
        mvc.perform(TestAuth.json(mvc, post("/api/products"), admin, "{\"name\":\"\",\"price\":-5,\"initialStock\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.name").value("required"))
                .andExpect(jsonPath("$.errors.categoryId").value("required"))
                .andExpect(jsonPath("$.errors.price").value("min"))
                .andExpect(jsonPath("$.errors.initialStock").value("min"));
        mvc.perform(TestAuth.json(mvc, post("/api/products"), admin, product("Ghost", 999999L, "100", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.categoryId").value("invalid"));
    }

    @Test
    void addStockIncreasesAvailableAndStatusFollowsTheThreshold() throws Exception {
        long id = create("Power Bank", chargers, "25000", 3);
        mvc.perform(get("/api/products/" + id).session(admin))
                .andExpect(jsonPath("$.data.status").value("LOW"));           // 3 ≤ default threshold 5

        mvc.perform(TestAuth.json(mvc, post("/api/products/" + id + "/stock"), admin, "{\"quantity\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.initialStock").value(23))
                .andExpect(jsonPath("$.data.availableStock").value(23))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));

        mvc.perform(TestAuth.json(mvc, post("/api/products/" + id + "/stock"), admin, "{\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").value("positive"));
        mvc.perform(TestAuth.json(mvc, post("/api/products/" + id + "/stock"), admin, "{\"quantity\":-4}"))
                .andExpect(status().isBadRequest());

        long empty = create("USB Cable", chargers, "3000", 0);
        mvc.perform(get("/api/products/" + empty).session(admin))
                .andExpect(jsonPath("$.data.status").value("OUT_OF_STOCK"));

        // raise the low-stock level in settings → 23 counts as low now
        mvc.perform(TestAuth.json(mvc, put("/api/settings"), admin,
                        "{\"companyName\":\"THEO TECH LTD\",\"companyAddress\":\"Kigali\",\"companyPhone\":\"\",\"lowStockThreshold\":25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lowStockThreshold").value(25));
        mvc.perform(get("/api/products/" + id).session(admin))
                .andExpect(jsonPath("$.data.status").value("LOW"));
    }

    @Test
    void stockedTotalCannotDropBelowWhatWasSold() throws Exception {
        long id = create("Radio", chargers, "18000", 10);
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, "{\"productId\":" + id + ",\"quantity\":4}"))
                .andExpect(status().isCreated());
        mvc.perform(TestAuth.json(mvc, put("/api/products/" + id), admin, product("Radio", chargers, "18000", 3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.initialStock").value("belowSold"));
        mvc.perform(TestAuth.json(mvc, put("/api/products/" + id), admin, product("Radio", chargers, "18000", 4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableStock").value(0))
                .andExpect(jsonPath("$.data.status").value("OUT_OF_STOCK"));
    }

    @Test
    void categoriesAreSeededUniqueAndProtectedWhileInUse() throws Exception {
        mvc.perform(get("/api/categories").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name", Matchers.hasItems("Phones", "Chargers", "Cables", "Batteries", "Bluetooth",
                        "Earphones", "Headphones", "Radios", "Speakers", "Power Banks", "Memory Cards", "Computer Accessories", "Other")))
                .andExpect(jsonPath("$.data[*].name", Matchers.not(Matchers.hasItem("Audio"))));

        MvcResult created = mvc.perform(TestAuth.json(mvc, post("/api/categories"), admin, "{\"name\":\"Smart Watches\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productCount").value(0))
                .andReturn();
        long catId = data(created).path("id").asLong();
        mvc.perform(TestAuth.json(mvc, post("/api/categories"), admin, "{\"name\":\"smart watches\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CATEGORY"));
        mvc.perform(TestAuth.json(mvc, post("/api/categories"), admin, "{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("required"));

        long productId = create("Watch", catId, "45000", 2);
        mvc.perform(get("/api/categories").session(admin))
                .andExpect(jsonPath("$.data[?(@.id == %d)].productCount".formatted(catId), Matchers.contains(1)));
        mvc.perform(TestAuth.withCsrf(mvc, delete("/api/categories/" + catId), admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));

        mvc.perform(TestAuth.withCsrf(mvc, delete("/api/products/" + productId), admin)).andExpect(status().isOk());
        mvc.perform(TestAuth.withCsrf(mvc, delete("/api/categories/" + catId), admin)).andExpect(status().isOk());
        mvc.perform(get("/api/categories").session(admin))
                .andExpect(jsonPath("$.data[*].name", Matchers.not(Matchers.hasItem("Smart Watches"))));
    }

    // ---- helpers ----------------------------------------------------------------------------

    private long categoryId(String name) throws Exception {
        JsonNode list = data(mvc.perform(get("/api/categories").session(admin)).andReturn());
        for (JsonNode c : list) {
            if (c.path("name").asText().equals(name)) return c.path("id").asLong();
        }
        throw new AssertionError("category not seeded: " + name);
    }

    private static String product(String name, long categoryId, String price, int initialStock) {
        return "{\"name\":\"%s\",\"categoryId\":%d,\"price\":%s,\"initialStock\":%d}".formatted(name, categoryId, price, initialStock);
    }

    private long create(String name, long categoryId, String price, int initialStock) throws Exception {
        MvcResult r = mvc.perform(TestAuth.json(mvc, post("/api/products"), admin, product(name, categoryId, price, initialStock)))
                .andExpect(status().isCreated()).andReturn();
        return data(r).path("id").asLong();
    }

    private JsonNode data(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString()).path("data");
    }
}
