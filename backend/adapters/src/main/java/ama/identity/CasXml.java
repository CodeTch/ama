package ama.identity;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/** Strict parser for the CAS 2.0 XML response returned by the identity provider. */
final class CasXml {
    static final String CAS_NAMESPACE = "http://www.yale.edu/tp/cas";

    private CasXml() {}

    record User(String username, Map<String, String> attributes) {}
    static final class TicketRejected extends IllegalArgumentException {
        TicketRejected(String message) { super(message); }
    }
    static final class Malformed extends IllegalArgumentException {
        Malformed(String message, Throwable cause) { super(message, cause); }
    }

    static User parse(String xml) {
        if (xml == null || xml.isBlank() || xml.length() > 256 * 1024)
            throw new IllegalArgumentException("Invalid CAS response");
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            var root = builder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
            requireName(root, CAS_NAMESPACE, "serviceResponse");
            Element response = onlyChild(root, CAS_NAMESPACE, "authenticationSuccess", "authenticationFailure");
            if ("authenticationFailure".equals(response.getLocalName())) {
                String code = response.getAttribute("code");
                if (code == null || code.isBlank() || code.length() > 128) code = "UNKNOWN";
                throw new TicketRejected("CAS ticket rejected: " + code);
            }

            Element user = requiredChild(response, CAS_NAMESPACE, "user");
            String username = text(user, 255);
            if (username.isBlank()) throw new IllegalArgumentException("Invalid CAS user");
            Element attributes = optionalChild(response, CAS_NAMESPACE, "attributes");
            Map<String, String> values = new LinkedHashMap<>();
            if (attributes != null) {
                for (Element attribute : elementChildren(attributes)) {
                    requireName(attribute, CAS_NAMESPACE, attribute.getLocalName());
                    String key = attribute.getLocalName();
                    if (key == null || key.isBlank() || values.put(key, text(attribute, 4096)) != null)
                        throw new IllegalArgumentException("Duplicate CAS attribute");
                }
            }
            return new User(username, Map.copyOf(values));
        } catch (TicketRejected | Malformed invalid) {
            throw invalid;
        } catch (IllegalArgumentException invalid) {
            throw new Malformed("Invalid CAS response", invalid);
        } catch (Exception invalid) {
            throw new Malformed("Invalid CAS response", invalid);
        }
    }

    private static Element onlyChild(Element parent, String namespace, String... names) {
        Element found = null;
        for (Element child : elementChildren(parent)) {
            boolean expected = false;
            for (String name : names) expected |= name.equals(child.getLocalName()) && namespace.equals(child.getNamespaceURI());
            if (!expected || found != null) throw new IllegalArgumentException("Invalid CAS response shape");
            found = child;
        }
        if (found == null) throw new IllegalArgumentException("Invalid CAS response shape");
        return found;
    }

    private static Element optionalChild(Element parent, String namespace, String name) {
        Element found = null;
        for (Element child : elementChildren(parent)) {
            if (name.equals(child.getLocalName()) && namespace.equals(child.getNamespaceURI())) {
                if (found != null) throw new IllegalArgumentException("Duplicate CAS element");
                found = child;
            } else if (!(CAS_NAMESPACE.equals(child.getNamespaceURI()) && "user".equals(child.getLocalName()))) {
                throw new IllegalArgumentException("Invalid CAS response shape");
            }
        }
        return found;
    }

    private static Element requiredChild(Element parent, String namespace, String name) {
        Element found = null;
        for (Element child : elementChildren(parent)) {
            if (name.equals(child.getLocalName()) && namespace.equals(child.getNamespaceURI())) {
                if (found != null) throw new IllegalArgumentException("Duplicate CAS element");
                found = child;
            } else if (!(CAS_NAMESPACE.equals(child.getNamespaceURI()) && "attributes".equals(child.getLocalName()))) {
                throw new IllegalArgumentException("Invalid CAS response shape");
            }
        }
        if (found == null) throw new IllegalArgumentException("Invalid CAS response shape");
        return found;
    }

    private static void requireName(Element element, String namespace, String localName) {
        if (!namespace.equals(element.getNamespaceURI()) || !localName.equals(element.getLocalName()))
            throw new IllegalArgumentException("Invalid CAS response namespace");
    }

    private static String text(Element element, int maxChars) {
        if (!elementChildren(element).isEmpty()) throw new IllegalArgumentException("Nested CAS response field");
        String value = element.getTextContent();
        if (value == null || value.codePointCount(0, value.length()) > maxChars)
            throw new IllegalArgumentException("CAS response field exceeds limit");
        return value;
    }

    private static java.util.List<Element> elementChildren(Element parent) {
        var result = new java.util.ArrayList<Element>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) result.add((Element) node);
            else if (node.getNodeType() != Node.TEXT_NODE && node.getNodeType() != Node.CDATA_SECTION_NODE)
                throw new IllegalArgumentException("Invalid CAS response content");
        }
        return result;
    }
}
