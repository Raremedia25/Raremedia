package com.theotech.iam;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The admin adds workers; a worker can sell and look, and nothing else. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WorkersIT {

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
    void adminAddsAWorkerWhoCanSellButNotManage() throws Exception {
        mvc.perform(get("/api/workers").session(admin)).andExpect(jsonPath("$.data").isEmpty());

        MvcResult created = mvc.perform(TestAuth.json(mvc, post("/api/workers"), admin,
                        "{\"fullName\":\"Alice Worker\",\"username\":\"Alice\",\"password\":\"Alice2026x\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andReturn();
        long id = data(created).path("id").asLong();
        mvc.perform(TestAuth.json(mvc, post("/api/workers"), admin, "{\"fullName\":\"Dup\",\"username\":\"ALICE\",\"password\":\"Alice2026x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USERNAME"));
        mvc.perform(TestAuth.json(mvc, post("/api/workers"), admin, "{\"fullName\":\"\",\"username\":\"a b\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullName").value("required"))
                .andExpect(jsonPath("$.errors.username").value("username"))
                .andExpect(jsonPath("$.errors.password").value("passwordLength"));
        // the admin account itself is not listed or editable here
        mvc.perform(get("/api/workers").session(admin))
                .andExpect(jsonPath("$.data[*].username", Matchers.contains("alice")));

        // first sign-in forces a password change, then the worker can sell
        MockHttpSession alice = TestAuth.login(mvc, "alice", "Alice2026x");
        mvc.perform(get("/api/products").session(alice))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(TestAuth.json(mvc, post("/api/auth/change-password"), alice,
                        "{\"currentPassword\":\"Alice2026x\",\"newPassword\":\"MyOwnPass9\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(alice))
                .andExpect(jsonPath("$.data.roles", Matchers.contains("SALES_STAFF")))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));

        long productId = data(mvc.perform(TestAuth.json(mvc, post("/api/products"), admin,
                        "{\"name\":\"Charger\",\"categoryId\":" + categoryId + ",\"price\":10000,\"initialStock\":5}"))
                .andExpect(status().isCreated()).andReturn()).path("id").asLong();

        // allowed: look and sell
        mvc.perform(get("/api/products").session(alice)).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard").session(alice)).andExpect(status().isOk());
        mvc.perform(get("/api/settings").session(alice)).andExpect(status().isOk());
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), alice, "{\"productId\":" + productId + ",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.soldByName").value("Alice Worker"))
                .andExpect(jsonPath("$.data.remainingStock").value(3));
        mvc.perform(get("/api/sales").session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].soldByName").value("Alice Worker"));

        // refused: anything that changes products, settings or accounts, and the report
        mvc.perform(TestAuth.json(mvc, post("/api/products"), alice, "{\"name\":\"X\",\"categoryId\":" + categoryId + ",\"price\":1,\"initialStock\":1}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(TestAuth.json(mvc, put("/api/products/" + productId), alice, "{\"name\":\"Y\",\"categoryId\":" + categoryId + ",\"price\":1,\"initialStock\":9}"))
                .andExpect(status().isForbidden());
        mvc.perform(TestAuth.json(mvc, post("/api/products/" + productId + "/stock"), alice, "{\"quantity\":5}"))
                .andExpect(status().isForbidden());
        mvc.perform(TestAuth.withCsrf(mvc, delete("/api/products/" + productId), alice)).andExpect(status().isForbidden());
        mvc.perform(TestAuth.json(mvc, post("/api/categories"), alice, "{\"name\":\"Nope\"}")).andExpect(status().isForbidden());
        mvc.perform(TestAuth.json(mvc, put("/api/settings"), alice, "{\"companyName\":\"Hijack\",\"lowStockThreshold\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/workers").session(alice)).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/sales").session(alice)).andExpect(status().isForbidden());

        // admin: disable → cannot sign in; reset password → forced change again; remove → gone
        mvc.perform(TestAuth.json(mvc, put("/api/workers/" + id), admin, "{\"fullName\":\"Alice W.\",\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
        mvc.perform(TestAuth.withCsrf(mvc, post("/api/auth/login"), admin).param("username", "alice").param("password", "MyOwnPass9"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        mvc.perform(TestAuth.json(mvc, put("/api/workers/" + id), admin, "{\"fullName\":\"Alice W.\",\"enabled\":true}")).andExpect(status().isOk());
        mvc.perform(TestAuth.json(mvc, post("/api/workers/" + id + "/reset-password"), admin, "{\"newPassword\":\"Temp2026pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
        MockHttpSession again = TestAuth.login(mvc, "alice", "Temp2026pw");
        mvc.perform(get("/api/products").session(again)).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        mvc.perform(TestAuth.withCsrf(mvc, delete("/api/workers/" + id), admin)).andExpect(status().isOk());
        mvc.perform(get("/api/workers").session(admin)).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(TestAuth.withCsrf(mvc, post("/api/auth/login"), admin).param("username", "alice").param("password", "Temp2026pw"))
                .andExpect(status().isUnauthorized());
        // the sale she recorded is still in the history
        mvc.perform(get("/api/sales").session(admin)).andExpect(jsonPath("$.data.totalElements").value(1));
        // the admin cannot be managed through this API
        long adminId = users.findActiveByUsername(TestAuth.ADMIN).orElseThrow().getId();
        mvc.perform(TestAuth.withCsrf(mvc, delete("/api/workers/" + adminId), admin)).andExpect(status().isNotFound());
    }

    private JsonNode data(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString()).path("data");
    }
}
