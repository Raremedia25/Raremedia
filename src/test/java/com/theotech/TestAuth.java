package com.theotech;

import com.theotech.iam.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Logs in through the real filter chain the way the browser does (CSRF cookie → header) so integration
 * tests exercise the same path as production.
 */
public final class TestAuth {

    public static final String ADMIN = "admin";
    public static final String ADMIN_PASSWORD = "Admin@123";
    public static final String XSRF_COOKIE = "XSRF-TOKEN";
    public static final String XSRF_HEADER = "X-XSRF-TOKEN";

    private TestAuth() {
    }

    /** Any GET issues a fresh XSRF-TOKEN cookie thanks to {@code csrf.spa()}. */
    public static Cookie csrf(MockMvc mvc, MockHttpSession session) throws Exception {
        MockHttpServletRequestBuilder req = get("/api/auth/csrf");
        if (session != null) req = req.session(session);
        MvcResult r = mvc.perform(req).andExpect(status().isOk()).andReturn();
        Cookie c = r.getResponse().getCookie(XSRF_COOKIE);
        assertThat(c).as("XSRF-TOKEN cookie issued").isNotNull();
        return c;
    }

    public static MockHttpSession login(MockMvc mvc, String username, String password) throws Exception {
        Cookie xsrf = csrf(mvc, null);
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .cookie(xsrf).header(XSRF_HEADER, xsrf.getValue())
                        .param("username", username).param("password", password))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    /**
     * The seeded admin must change its password before using any other API; tests clear that flag
     * (inside the rolled-back test transaction) so they can use the admin session directly.
     */
    public static MockHttpSession loginAsAdmin(MockMvc mvc, UserRepository users) throws Exception {
        users.findActiveByUsername(ADMIN).ifPresent(u -> u.setMustChangePassword(false));
        return login(mvc, ADMIN, ADMIN_PASSWORD);
    }

    /** Adds session + CSRF cookie/header to a mutating request. */
    public static <B extends AbstractMockHttpServletRequestBuilder<B>> B withCsrf(MockMvc mvc, B builder,
                                                                                  MockHttpSession session) throws Exception {
        Cookie xsrf = csrf(mvc, session);
        return builder.session(session).cookie(xsrf).header(XSRF_HEADER, xsrf.getValue());
    }

    public static <B extends AbstractMockHttpServletRequestBuilder<B>> B json(MockMvc mvc, B builder,
                                                                              MockHttpSession session, String body) throws Exception {
        return withCsrf(mvc, builder, session).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
