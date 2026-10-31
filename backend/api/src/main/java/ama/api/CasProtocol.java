package ama.api;

import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Strict, bounded CAS 2.0 XML response reader. It never resolves external entities. */
final class CasProtocol {
    private static final String NS = "http://www.yale.edu/tp/cas";
    private static final int MAX_BYTES = 64 * 1024;
    private CasProtocol() {}

    record Assertion(String username, Map<String, String> attributes) {}
    static final class Failure extends RuntimeException {
        private final String code;
        Failure(String code) { super(code); this.code = code; }
        String code() { return code; }
    }

    static Assertion parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new Failure("INVALID_RESPONSE");
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var document = factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(bytes)));
            var root = document.getDocumentElement();
            if (root == null || !NS.equals(root.getNamespaceURI()) || !"serviceResponse".equals(root.getLocalName())) throw new Failure("INVALID_RESPONSE");
            var successes = children(root, "authenticationSuccess");
            var failures = children(root, "authenticationFailure");
            if (successes.size() == 1 && failures.isEmpty()) {
                var success = successes.getFirst();
                var users = children(success, "user");
                if (users.size() != 1) throw new Failure("INVALID_RESPONSE");
                String username = bounded(text(users.getFirst()), 255);
                var attributes = new LinkedHashMap<String, String>();
                var groups = children(success, "attributes");
                if (groups.size() > 1) throw new Failure("INVALID_RESPONSE");
                if (!groups.isEmpty()) {
                    var group = groups.getFirst();
                    for (var child = group.getFirstChild(); child != null; child = child.getNextSibling()) {
                        if (!(child instanceof Element element) || !NS.equals(element.getNamespaceURI()) || element.getLocalName() == null) continue;
                        String key = bounded(element.getLocalName(), 128), value = attributeText(element, 2048);
                        if (attributes.putIfAbsent(key, value) != null) throw new Failure("INVALID_RESPONSE");
                    }
                }
                return new Assertion(username, Map.copyOf(attributes));
            }
            if (successes.isEmpty() && failures.size() == 1) {
                var code = failures.getFirst().getAttribute("code");
                throw new Failure(code == null || code.isBlank() ? "INVALID_TICKET" : bounded(code, 128));
            }
            throw new Failure("INVALID_RESPONSE");
        } catch (Failure failure) { throw failure; }
        catch (Exception invalid) { throw new Failure("INVALID_RESPONSE"); }
    }

    private static List<Element> children(Element parent, String name) {
        var result = new ArrayList<Element>();
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling())
            if (child instanceof Element element && NS.equals(element.getNamespaceURI()) && name.equals(element.getLocalName())) result.add(element);
        return result;
    }
    private static String text(Element element) {
        String value = element.getTextContent();
        if (value == null || value.isBlank() || value.codePoints().anyMatch(Character::isISOControl)) throw new Failure("INVALID_RESPONSE");
        return value.trim();
    }
    private static String attributeText(Element element, int max) {
        String value = element.getTextContent();
        if (value == null || value.codePoints().anyMatch(Character::isISOControl)) throw new Failure("INVALID_RESPONSE");
        value = value.trim();
        if (value.length() > max) throw new Failure("INVALID_RESPONSE");
        return value;
    }
    private static String bounded(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw new Failure("INVALID_RESPONSE");
        return value;
    }
}
