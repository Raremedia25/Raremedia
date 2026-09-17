package com.theotech.security;

import com.theotech.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.LinkedHashMap;

/**
 * One administrator login, session cookie, nothing more.
 * <ul>
 *   <li>Form login posts to {@code /api/auth/login}; every outcome is JSON.</li>
 *   <li>CSRF via {@code csrf.spa()}: readable {@code XSRF-TOKEN} cookie echoed back as {@code X-XSRF-TOKEN}.</li>
 *   <li>Remember-me is a signed cookie (hash of username + expiry + key), not a rotating database token:
 *       a page fires several API calls at once, and single-use tokens fail under that with
 *       {@code CookieTheftException} after every server restart.</li>
 *   <li>Two levels: the administrator may do everything; workers may sell and look at products, stock and
 *       sales. Admin-only service methods carry {@code @PreAuthorize("hasRole('ADMIN')")}.</li>
 *   <li>Unauthenticated {@code /api/**} → 401 JSON, pages → login page.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final int REMEMBER_ME_SECONDS = 14 * 24 * 60 * 60;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JsonAuthHandlers handlers,
                                                   AppUserDetailsService userDetailsService,
                                                   AppProperties props) throws Exception {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entryPoints = new LinkedHashMap<>();
        entryPoints.put(PathPatternRequestMatcher.pathPattern("/api/**"), handlers);
        DelegatingAuthenticationEntryPoint entryPoint = new DelegatingAuthenticationEntryPoint(entryPoints);
        entryPoint.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login.html"));

        http
            .csrf(csrf -> csrf.spa())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login.html", "/css/**", "/js/**", "/vendor/**", "/favicon.ico", "/error").permitAll()
                .requestMatchers("/api/auth/login", "/api/auth/csrf", "/actuator/health").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/settings/logo").permitAll()   // login page shows it
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/api/auth/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(handlers)
                .failureHandler(handlers)
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(handlers)
                .invalidateHttpSession(true)
                .deleteCookies("THEOSESSION", "remember-me"))
            .rememberMe(remember -> remember
                .key(props.security().rememberMeKey())
                .userDetailsService(userDetailsService)
                .rememberMeParameter("remember-me")
                .rememberMeCookieName("remember-me")
                .tokenValiditySeconds(REMEMBER_ME_SECONDS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(handlers))
            .sessionManagement(session -> session
                .sessionFixation(fixation -> fixation.migrateSession()))
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin()))
            .addFilterBefore(new PasswordChangeRequiredFilter(handlers), AuthorizationFilter.class);

        return http.build();
    }
}
