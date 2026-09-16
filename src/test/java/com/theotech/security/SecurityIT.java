package com.theotech.security;

import com.theotech.TestAuth;
import com.theotech.iam.domain.User;
import com.theotech.iam.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The one administrator login, against the real {@code theo_tech_test} database. CSRF is exercised the way
 * the browser does it: read the {@code XSRF-TOKEN} cookie, send it back as {@code X-XSRF-TOKEN}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIT {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    @Test
    void unauthenticatedApiCallReturnsJson401() throws Exception {
        for (String url : new String[] {"/api/auth/me", "/api/products", "/api/sales", "/api/dashboard", "/api/reports/sales", "/api/settings"}) {
            mvc.perform(get(url).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
    }

    @Test
    void unauthenticatedPageRedirectsToLogin() throws Exception {
        mvc.perform(get("/index.html").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", Matchers.endsWith("/login.html")));
        mvc.perform(get("/sell.html")).andExpect(status().is3xxRedirection());
    }

    @Test
    void loginPageAndAssetsArePublic() throws Exception {
        mvc.perform(get("/login.html")).andExpect(status().isOk());
        mvc.perform(get("/vendor/bootstrap/bootstrap.min.css")).andExpect(status().isOk());
        mvc.perform(get("/js/api.js")).andExpect(status().isOk());
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
    }

    @Test
    void csrfEndpointIssuesReadableCookie() throws Exception {
        MvcResult r = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headerName").value(TestAuth.XSRF_HEADER))
                .andReturn();
        Cookie c = r.getResponse().getCookie(TestAuth.XSRF_COOKIE);
        assertThat(c).isNotNull();
        assertThat(c.isHttpOnly()).as("frontend JS must be able to read it").isFalse();
        assertThat(c.getValue()).isNotBlank();
    }

    @Test
    void loginWithCorrectPasswordSucceeds() throws Exception {
        MvcResult result = login(TestAuth.ADMIN, TestAuth.ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value(TestAuth.ADMIN))
                .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("password");

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(TestAuth.ADMIN));
        assertThat(users.findActiveByUsername(TestAuth.ADMIN).orElseThrow().getLastLoginAt()).isNotNull();
    }

    @Test
    void loginWithWrongPasswordIs401() throws Exception {
        login(TestAuth.ADMIN, "definitely-wrong")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
        assertThat(users.findActiveByUsername(TestAuth.ADMIN).orElseThrow().getFailedLoginAttempts()).isEqualTo(1);

        login("nobody", "whatever")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void loginWithoutCsrfTokenIs403() throws Exception {
        mvc.perform(post("/api/auth/login").param("username", TestAuth.ADMIN).param("password", TestAuth.ADMIN_PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void repeatedWrongPasswordsLockTheAccount() throws Exception {
        for (int i = 1; i <= 4; i++) {
            login(TestAuth.ADMIN, "wrong-" + i).andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
        }
        login(TestAuth.ADMIN, "wrong-5")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        // even the right password is refused while locked
        login(TestAuth.ADMIN, TestAuth.ADMIN_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        User admin = users.findActiveByUsername(TestAuth.ADMIN).orElseThrow();
        assertThat(admin.getLockedUntil()).isNotNull();
        assertThat(admin.isAccountNonLocked()).isFalse();
    }

    @Test
    void mustChangePasswordBlocksOtherApisUntilChanged() throws Exception {
        MockHttpSession session = TestAuth.login(mvc, TestAuth.ADMIN, TestAuth.ADMIN_PASSWORD);

        mvc.perform(get("/api/products").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        mvc.perform(TestAuth.json(mvc, post("/api/auth/change-password"), session,
                        "{\"currentPassword\":\"nope\",\"newPassword\":\"NewPass2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.currentPassword").value("passwordIncorrect"));

        mvc.perform(TestAuth.json(mvc, post("/api/auth/change-password"), session,
                        "{\"currentPassword\":\"" + TestAuth.ADMIN_PASSWORD + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").value("passwordLength"));

        mvc.perform(TestAuth.json(mvc, post("/api/auth/change-password"), session,
                        "{\"currentPassword\":\"" + TestAuth.ADMIN_PASSWORD + "\",\"newPassword\":\"NewPass2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // the live session is unblocked
        mvc.perform(get("/api/products").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));

        // and the new password works for a fresh login
        login(TestAuth.ADMIN, "NewPass2026").andExpect(status().isOk());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session = TestAuth.loginAsAdmin(mvc, users);
        mvc.perform(get("/api/dashboard").session(session)).andExpect(status().isOk());
        mvc.perform(TestAuth.withCsrf(mvc, post("/api/auth/logout"), session)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    private ResultActions login(String username, String password) throws Exception {
        Cookie xsrf = TestAuth.csrf(mvc, null);
        return mvc.perform(post("/api/auth/login")
                .cookie(xsrf).header(TestAuth.XSRF_HEADER, xsrf.getValue())
                .param("username", username).param("password", password));
    }
}
