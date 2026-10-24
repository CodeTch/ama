package ama.api;

import ama.identity.*;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

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
    @Bean SessionIssuer companyIdentity() { return new CompanyIdentityAdapter(); }
    @Bean DirectoryPort companyDirectory() { return new CompanyDirectoryAdapter(); }
}
