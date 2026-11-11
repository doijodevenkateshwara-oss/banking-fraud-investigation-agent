package com.bfs.fraudcase.config;

import com.bfs.fraudcase.domain.Actor;
import com.bfs.fraudcase.domain.Role;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Two filter chains: stateless HTTP Basic for {@code /api/**} and form login with CSRF protection for the UI.
 * Demo users are synthetic training identities; their password comes from {@code CAPSTONE_DEMO_PASSWORD}
 * (or is generated at start-up and printed once to the console).
 *
 * @author Venkateshwara Doijode
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    public static final Map<String, Set<Role>> DEMO_USERS = Map.of(
            "agent01", Set.of(Role.SERVICE_AGENT),
            "investigator01", Set.of(Role.SERVICE_AGENT, Role.INVESTIGATOR),
            "reviewer08", Set.of(Role.REVIEWER),
            "lead01", Set.of(Role.REVIEWER, Role.TEAM_LEAD),
            "auditor01", Set.of(Role.AUDITOR));

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    InMemoryUserDetailsManager users(CapstoneProperties props, PasswordEncoder encoder) {
        String password = props.security().demoPassword();
        if (password == null || password.isBlank()) {
            byte[] b = new byte[12];
            new SecureRandom().nextBytes(b);
            password = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
            log.warn("CAPSTONE_DEMO_PASSWORD not set; generated demo password for this run: {}", password);
        }
        String encoded = encoder.encode(password);
        List<UserDetails> users = DEMO_USERS.entrySet().stream()
                .map(e -> User.withUsername(e.getKey()).password(encoded)
                        .roles(e.getValue().stream().map(Enum::name).toArray(String[]::new)).build())
                .toList();
        return new InMemoryUserDetailsManager(users);
    }

    @Bean
    @Order(1)
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(c -> c.disable());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain web(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a
                        .requestMatchers("/login", "/css/**", "/js/**", "/actuator/health", "/actuator/health/**", "/error").permitAll()
                        .requestMatchers("/actuator/**").hasAnyRole(Role.AUDITOR.name(), Role.TEAM_LEAD.name())
                        .anyRequest().authenticated())
                .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/cases", true).permitAll())
                .logout(l -> l.logoutSuccessUrl("/login?logout"))
                .headers(h -> h
                        .contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; frame-ancestors 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)));
        return http.build();
    }

    /** Builds the application actor from the authenticated principal (never from request input). */
    public static Actor actor(Authentication auth) {
        Set<Role> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5))
                .filter(r -> java.util.Arrays.stream(Role.values()).anyMatch(x -> x.name().equals(r)))
                .map(Role::valueOf).collect(Collectors.toSet());
        return new Actor(auth.getName(), roles);
    }
}
