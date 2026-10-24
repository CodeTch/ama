package ama.api;

import ama.config.DeploymentConfig;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration(proxyBeanMethods = false)
class RuntimeConfiguration {
    @Bean DeploymentConfig deploymentConfig(Environment environment) {
        var values = new HashMap<String, String>();
        for (String key : List.of("database.mode", "storage.type", "storage.default-backend-id", "storage.s3.endpoint", "storage.s3.bucket", "storage.s3.region", "storage.s3.access-key", "storage.s3.secret-key")) {
            String value = environment.getProperty(key);
            if (value != null) values.put(key, value);
        }
        return DeploymentConfig.parse(values);
    }

    @Bean SecurityFilterChain internalNetworkSecurity(HttpSecurity http, Environment environment) throws Exception {
        var networks = Arrays.stream(environment.getProperty("internal.health.allowed-networks", "127.0.0.1/32,::1/128").split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).map(IpAddressMatcher::new).toList();
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/internal/health/live", "/internal/health/ready").access((authentication, context) -> {
                            var request = context.getRequest();
                            int port = environment.getProperty("local.management.port", Integer.class, -1);
                            boolean internal = request.getLocalPort() == port && networks.stream().anyMatch(network -> network.matches(request.getRemoteAddr()));
                            return new AuthorizationDecision(internal);
                        })
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> forbidden(response))
                        .accessDeniedHandler((request, response, error) -> forbidden(response)))
                .build();
    }
    private static void forbidden(HttpServletResponse response) throws IOException {
        response.setStatus(403);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":{\"code\":\"FORBIDDEN\",\"message\":\"Access denied\"}}");
    }
}
