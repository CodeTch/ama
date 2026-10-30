package ama.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.core.env.Environment;
import ama.identity.SessionPort;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean @Order(1)
    SecurityFilterChain casSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher("/auth/cas/**")
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable())
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .build();
    }

    @Bean @Order(2)
    SecurityFilterChain businessSecurity(HttpSecurity http, SessionPort sessions, Environment environment) throws Exception {
        return http.securityMatcher("/api/**")
                // CSRF is session-bound and stateless: exact Origin + independent stored token in the filter.
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable())
                .addFilterBefore(new SessionSecurityFilter(sessions, environment), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, failure) -> BusinessErrors.write(response, 401, "UNAUTHENTICATED", "Authentication required"))
                        .accessDeniedHandler((request, response, failure) -> BusinessErrors.write(response, 403, "FORBIDDEN", "Access denied")))
                .build();
    }

    @Bean @Order(2)
    SecurityFilterChain casSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher("/auth/cas/**")
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable())
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .build();
    }
}
