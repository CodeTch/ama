package ama.api;
import ama.access.MeetingAccess;
import ama.db.TransactionRunner;
import ama.identity.SessionPort;
import ama.recording.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
@Configuration(proxyBeanMethods=false)
@EnableWebSocket
class RecordingWebSocketConfiguration implements WebSocketConfigurer {
 private final RecordingWebSocketHandler handler;private final Environment environment;
 RecordingWebSocketConfiguration(RecordingWebSocketHandler handler,Environment environment){this.handler=handler;this.environment=environment;}
 @Bean(destroyMethod="close") static RecordingWebSocketHandler recordingSocketHandler(SessionPort identities,RecordingService recordings,JdbcRecordingWriterLeases leases,RecordingMutationGuard mutations,TransactionRunner tx,MeetingAccess access,Environment environment,RecordingIngressBudget ingress){return new RecordingWebSocketHandler(identities,recordings,leases,mutations,tx,access,environment,ingress);}
 @Bean static ServletServerContainerFactoryBean recordingSocketContainer(){var container=new ServletServerContainerFactoryBean();container.setMaxBinaryMessageBufferSize(ama.realtime.RecordingBinaryCodec.MAX_FRAME_BYTES);container.setMaxTextMessageBufferSize(1024);container.setMaxSessionIdleTimeout(45000L);container.setAsyncSendTimeout(5000L);return container;}
 public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){String origin=environment.getProperty("identity.allowed-origin","");registry.addHandler(handler,"/api/v1/recordings/*/stream").addInterceptors(handler.handshake()).setAllowedOrigins(origin.isBlank()?"https://disabled.invalid":origin);}
}
