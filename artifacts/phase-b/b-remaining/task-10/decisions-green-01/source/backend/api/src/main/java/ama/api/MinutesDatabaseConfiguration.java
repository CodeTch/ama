package ama.api;
import ama.minutes.ControlledMinutesDataSource;import javax.sql.DataSource;import org.springframework.beans.factory.config.BeanPostProcessor;import org.springframework.context.annotation.*;import org.springframework.core.env.Environment;
/** The controlled API and Worker initialize every connection with the same bounded session contract. */
@Configuration(proxyBeanMethods=false) class MinutesDatabaseConfiguration {
 @Bean static BeanPostProcessor minutesConnections(Environment env){boolean controlled=env.getProperty("minutes.enabled",Boolean.class,false)&&"CONTROLLED_INSTALLATION".equals(env.getProperty("minutes.mode"));return new BeanPostProcessor(){public Object postProcessBeforeInitialization(Object bean,String name){if(controlled&&bean instanceof com.zaxxer.hikari.HikariDataSource pool)ControlledMinutesDataSource.connectionProperties().forEach((key,value)->pool.addDataSourceProperty(key.toString(),value));return bean;}public Object postProcessAfterInitialization(Object bean,String name){return controlled&&bean instanceof DataSource source&&!(source instanceof ControlledMinutesDataSource)?new ControlledMinutesDataSource(source):bean;}};}
}
