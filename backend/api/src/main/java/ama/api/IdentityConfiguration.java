package ama.api;

import ama.identity.*;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import java.net.URI;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
class IdentityConfiguration {
    IdentityConfiguration(Environment environment) {
        if (!"company".equals(environment.getProperty("identity.mode", "company")))
            throw new IllegalArgumentException("Production identity assembly only supports company mode");
        String origin = environment.getProperty("identity.allowed-origin", "");
        if (!origin.isEmpty()) {
            try {
                var uri = java.net.URI.create(origin);
                if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null
                        || uri.getRawUserInfo() != null || !uri.getRawPath().isEmpty()
                        || uri.getRawQuery() != null || uri.getRawFragment() != null)
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid allowed Origin"); }
        }
    }
    @Bean JdbcIdentitySessions sharedIdentitySessions(DataSource source) { return new JdbcIdentitySessions(source); }
    @Bean IdentityProvisioner identityProvisioner(JdbcIdentitySessions sessions) { return sessions::ensureCasIdentity; }
    @Bean @ConditionalOnProperty(name="cas.enabled", havingValue="true")
    SessionIssuer companyIdentity(JdbcIdentitySessions sessions) { return sessions::issueForVerifiedIdentity; }
    @Bean DirectoryPort companyDirectory() { return new CompanyDirectoryAdapter(); }
    @Bean CasAuthenticationPort casAuthentication(DataSource source, Environment environment) {
        return new CasAuthenticationAdapter(source,
                URI.create(environment.getRequiredProperty("cas.origin")),
                URI.create(environment.getRequiredProperty("cas.service-url")),
                URI.create(environment.getRequiredProperty("cas.logout-service-url")),
                URI.create(environment.getRequiredProperty("identity.allowed-origin")),
                Duration.ofMillis(environment.getProperty("cas.timeout-ms", Long.class, 3000L)),
                Duration.ofSeconds(environment.getProperty("cas.session-lifetime-seconds", Long.class, 28800L)));
    }
}
