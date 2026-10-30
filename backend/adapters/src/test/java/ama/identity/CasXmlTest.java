package ama.identity;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CasXmlTest {
    @Test
    void parsesCas2SuccessAndAttributesWithoutTrustingUnqualifiedElements() {
        String xml = """
                <cas:serviceResponse xmlns:cas="http://www.yale.edu/tp/cas">
                  <cas:authenticationSuccess>
                    <cas:user>demo-owner</cas:user>
                    <cas:attributes>
                      <cas:displayName>示例负责人</cas:displayName>
                      <cas:email>owner@example.invalid</cas:email>
                      <cas:department>示例投资管理部</cas:department>
                      <cas:role>owner</cas:role>
                    </cas:attributes>
                  </cas:authenticationSuccess>
                </cas:serviceResponse>
                """;

        CasXml.User user = CasXml.parse(xml);

        assertEquals("demo-owner", user.username());
        assertEquals(Map.of(
                "displayName", "示例负责人",
                "email", "owner@example.invalid",
                "department", "示例投资管理部",
                "role", "owner"), user.attributes());
    }

    @Test
    void rejectsCasAuthenticationFailureEvenWhenHttpResponseWasSuccessful() {
        String xml = """
                <cas:serviceResponse xmlns:cas="http://www.yale.edu/tp/cas">
                  <cas:authenticationFailure code="INVALID_TICKET">ticket is invalid</cas:authenticationFailure>
                </cas:serviceResponse>
                """;

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> CasXml.parse(xml));

        assertEquals("CAS ticket rejected: INVALID_TICKET", error.getMessage());
    }

    @Test
    void rejectsExternalEntitiesAndWrongNamespace() {
        String xxe = """
                <!DOCTYPE serviceResponse [<!ENTITY leaked SYSTEM "file:///etc/passwd">]>
                <cas:serviceResponse xmlns:cas="http://www.yale.edu/tp/cas">
                  <cas:authenticationSuccess><cas:user>&leaked;</cas:user></cas:authenticationSuccess>
                </cas:serviceResponse>
                """;
        String wrongNamespace = "<serviceResponse xmlns=\"http://attacker.invalid/cas\"><authenticationSuccess><user>attacker</user></authenticationSuccess></serviceResponse>";

        assertThrows(IllegalArgumentException.class, () -> CasXml.parse(xxe));
        assertThrows(IllegalArgumentException.class, () -> CasXml.parse(wrongNamespace));
    }

    @Test
    void rejectsUnqualifiedUserAndAttributesInsideCasSuccess() {
        String xml = "<cas:serviceResponse xmlns:cas=\"http://www.yale.edu/tp/cas\"><cas:authenticationSuccess>"
                + "<cas:user>owner</cas:user><attributes><role>user</role></attributes>"
                + "</cas:authenticationSuccess></cas:serviceResponse>";

        assertThrows(IllegalArgumentException.class, () -> CasXml.parse(xml));
    }

    @Test
    void rejectsNestedElementsInsideUserAndAttributes() {
        String xml = "<cas:serviceResponse xmlns:cas=\"http://www.yale.edu/tp/cas\"><cas:authenticationSuccess>"
                + "<cas:user><cas:name>owner</cas:name></cas:user><cas:attributes><cas:role><cas:name>user</cas:name></cas:role></cas:attributes>"
                + "</cas:authenticationSuccess></cas:serviceResponse>";

        assertThrows(IllegalArgumentException.class, () -> CasXml.parse(xml));
    }
}
