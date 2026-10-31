package ama.api;

import ama.contract.IdentityContracts.IssuedSession;
import ama.identity.CasAuthenticationPort;
import ama.identity.IdentityProvisioner;
import ama.identity.SessionPort;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CasControllerTest {
    @Test
    void loginRedirectsToConfiguredCasAndCallbackIssuesHostOnlySessionCookie() throws Exception {
        CasAuthenticationPort cas = mock(CasAuthenticationPort.class);
        when(cas.loginUri()).thenReturn(URI.create("http://127.0.0.1:8787/cas/login?service=http%3A%2F%2F127.0.0.1%3A5173%2Fauth%2Fcas%2Fcallback"));
        when(cas.authenticate("ST-12345678")).thenReturn(new IssuedSession("A".repeat(43), "B".repeat(43), Instant.now().plusSeconds(300)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CasController(cas, "http://127.0.0.1:5173")).build();

        mvc.perform(get("/auth/cas/login"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "http://127.0.0.1:8787/cas/login?service=http%3A%2F%2F127.0.0.1%3A5173%2Fauth%2Fcas%2Fcallback"));
        mvc.perform(get("/auth/cas/callback").param("ticket", "ST-12345678"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "http://127.0.0.1:5173/meetings"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("__Host-AMA_SESSION=")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")));
        verify(cas).authenticate("ST-12345678");
    }

    @Test
    void callbackWithoutTicketIsRejectedBeforeCallingCas() throws Exception {
        CasAuthenticationPort cas = mock(CasAuthenticationPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CasController(cas, "http://127.0.0.1:5173")).build();

        mvc.perform(get("/auth/cas/callback"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cas);
    }

    @Test
    void logoutRevokesLocalSessionBeforeRedirectingToCas() throws Exception {
        CasAuthenticationPort cas = mock(CasAuthenticationPort.class);
        SessionPort sessions = mock(SessionPort.class);
        when(cas.logoutUri()).thenReturn(URI.create("http://127.0.0.1:8787/cas/logout?service=http%3A%2F%2F127.0.0.1%3A5173%2Fmeetings"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CasController(cas, sessions, "http://127.0.0.1:5173")).build();

        mvc.perform(get("/auth/cas/logout").cookie(new Cookie(SessionCookies.NAME, "A".repeat(43))))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "http://127.0.0.1:8787/cas/logout?service=http%3A%2F%2F127.0.0.1%3A5173%2Fmeetings"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        verify(sessions).revoke(org.mockito.ArgumentMatchers.eq("A".repeat(43)), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void credentialLoginValidatesServiceTicketOnServerAndOnlyReturnsSessionCookie() throws Exception {
        CasClient cas = mock(CasClient.class);
        ama.identity.SessionIssuer sessions = mock(ama.identity.SessionIssuer.class);
        SessionPort sessionPort = mock(SessionPort.class);
        IdentityProvisioner identities = mock(IdentityProvisioner.class);
        var environment = new MockEnvironment()
                .withProperty("identity.allowed-origin", "http://127.0.0.1:5173")
                .withProperty("cas.identity-attribute", "user")
                .withProperty("cas.session-lifetime-seconds", "300");
        String service = "http://127.0.0.1:5173/auth/cas/callback";
        when(cas.service()).thenReturn(URI.create(service));
        when(cas.login("alice", "secret")).thenReturn(URI.create(service + "?ticket=ST-server-only-ticket-1234"));
        when(cas.validate("ST-server-only-ticket-1234"))
                .thenReturn(new CasProtocol.Assertion("alice", Map.of("displayName", "Alice", "department", "Finance")));
        when(sessions.issueForVerifiedIdentity(eq("alice"), any())).thenReturn(
                new IssuedSession("A".repeat(43), "B".repeat(43), Instant.now().plusSeconds(300)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new CasAuthenticationController(cas, sessions, sessionPort, identities, environment)).build();

        mvc.perform(post("/auth/cas/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("Origin", "http://127.0.0.1:5173")
                        .param("username", "alice")
                        .param("password", "secret"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/meetings"))
                .andExpect(header().string("Location", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ST-"))))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("__Host-AMA_SESSION=")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")));
        verify(cas).validate("ST-server-only-ticket-1234");
        verify(identities).ensureCasIdentity("alice", "Alice", "Finance");
    }

    @Test
    void credentialLoginRejectsUnexpectedOriginWithSafeRedirect() throws Exception {
        CasClient cas = mock(CasClient.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CasAuthenticationController(
                cas, mock(ama.identity.SessionIssuer.class), mock(SessionPort.class),
                mock(IdentityProvisioner.class), new MockEnvironment()
                        .withProperty("identity.allowed-origin", "http://127.0.0.1:5173"))).build();

        mvc.perform(post("/auth/cas/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("Origin", "https://attacker.invalid")
                        .param("username", "alice")
                        .param("password", "secret"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/login?error=FORBIDDEN"));
        verifyNoInteractions(cas);
    }

    @Test
    void credentialLoginMapsInvalidIdentityProjectionToSafeCasError() throws Exception {
        CasClient cas = mock(CasClient.class);
        ama.identity.SessionIssuer sessions = mock(ama.identity.SessionIssuer.class);
        IdentityProvisioner identities = mock(IdentityProvisioner.class);
        var environment = new MockEnvironment().withProperty("identity.allowed-origin", "http://127.0.0.1:5173");
        String service = "http://127.0.0.1:5173/auth/cas/callback";
        when(cas.service()).thenReturn(URI.create(service));
        when(cas.login("alice", "secret")).thenReturn(URI.create(service + "?ticket=ST-invalid-identity-1234"));
        when(cas.validate("ST-invalid-identity-1234"))
                .thenReturn(new CasProtocol.Assertion("alice", Map.of("displayName", "bad")));
        doThrow(new IllegalArgumentException("Invalid CAS identity attributes"))
                .when(identities).ensureCasIdentity("alice", "bad", null);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CasAuthenticationController(
                cas, sessions, mock(SessionPort.class), identities, environment)).build();

        mvc.perform(post("/auth/cas/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("Origin", "http://127.0.0.1:5173")
                        .param("username", "alice")
                        .param("password", "secret"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/login?error=INVALID_CAS_ASSERTION"));
    }

    @Test
    void credentialLoginPreservesCredentialAndProviderFailureCodes() throws Exception {
        for (String code : new String[]{"INVALID_CREDENTIALS", "PROVIDER_UNAVAILABLE"}) {
            CasClient cas = mock(CasClient.class);
            doThrow(new CasProtocol.Failure(code)).when(cas).login("alice", "secret");
            MockMvc mvc = MockMvcBuilders.standaloneSetup(new CasAuthenticationController(
                    cas, mock(ama.identity.SessionIssuer.class), mock(SessionPort.class),
                    mock(IdentityProvisioner.class), new MockEnvironment()
                            .withProperty("identity.allowed-origin", "http://127.0.0.1:5173"))).build();

            mvc.perform(post("/auth/cas/login")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .header("Origin", "http://127.0.0.1:5173")
                            .param("username", "alice")
                            .param("password", "secret"))
                    .andExpect(status().isSeeOther())
                    .andExpect(header().string("Location", "/login?error=" + code));
        }
    }
}
