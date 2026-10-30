package ama.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="cas.enabled", havingValue="true")
class CasConfiguration {
    @Bean CasClient casClient(Environment env) { return new CasClient(env); }
}
