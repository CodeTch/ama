package ama.api;
import ama.model.HttpMeetingCategorySuggestion;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods=false)
class MeetingCategoryConfiguration {
 @Bean(destroyMethod="close") @ConditionalOnProperty(name="minutes.category-suggestion-origin")
 HttpMeetingCategorySuggestion meetingCategoryModel(Environment env){return new HttpMeetingCategorySuggestion(URI.create(env.getRequiredProperty("minutes.category-suggestion-origin")));}
}
