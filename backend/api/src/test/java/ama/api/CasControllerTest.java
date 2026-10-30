package ama.api;

import ama.contract.IdentityContracts.IssuedSession;
import ama.identity.CasAuthenticationPort;
import ama.identity.SessionPort;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
