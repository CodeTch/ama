package ama.api;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CasProtocolTest {
    @Test void parsesNamespacedSuccessAndAttributes() {
        var xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <cas:serviceResponse xmlns:cas="http://www.yale.edu/tp/cas">
                  <cas:authenticationSuccess>
                    <cas:user>dev-owner</cas:user>
                    <cas:attributes><cas:displayName>开发负责人</cas:displayName><cas:department>投资管理部</cas:department></cas:attributes>
                  </cas:authenticationSuccess>
                </cas:serviceResponse>
                """;
        var assertion = CasProtocol.parse(xml.getBytes(StandardCharsets.UTF_8));
        assertEquals("dev-owner", assertion.username());
        assertEquals("开发负责人", assertion.attributes().get("displayName"));
        assertEquals("投资管理部", assertion.attributes().get("department"));
    }

    @Test void reportsProviderFailureAndRejectsXmlExternalEntities() {
        var failure = """
                <cas:serviceResponse xmlns:cas="http://www.yale.edu/tp/cas"><cas:authenticationFailure code="INVALID_TICKET">bad</cas:authenticationFailure></cas:serviceResponse>
                """;
        var error = assertThrows(CasProtocol.Failure.class, () -> CasProtocol.parse(failure.getBytes(StandardCharsets.UTF_8)));
        assertEquals("INVALID_TICKET", error.code());
        var xxe = """
                <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <cas:serviceResponse xmlns:cas="http://www.yale.edu/tp/cas"><cas:authenticationSuccess><cas:user>&xxe;</cas:user></cas:authenticationSuccess></cas:serviceResponse>
                """;
        assertThrows(CasProtocol.Failure.class, () -> CasProtocol.parse(xxe.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void rejectsMissingUserAndAmbiguousSuccess() {
        var missing = "<serviceResponse xmlns=\"http://www.yale.edu/tp/cas\"><authenticationSuccess/></serviceResponse>";
        assertThrows(CasProtocol.Failure.class, () -> CasProtocol.parse(missing.getBytes(StandardCharsets.UTF_8)));
        var duplicate = "<serviceResponse xmlns=\"http://www.yale.edu/tp/cas\"><authenticationSuccess><user>a</user></authenticationSuccess><authenticationSuccess><user>b</user></authenticationSuccess></serviceResponse>";
        assertThrows(CasProtocol.Failure.class, () -> CasProtocol.parse(duplicate.getBytes(StandardCharsets.UTF_8)));
    }
}
