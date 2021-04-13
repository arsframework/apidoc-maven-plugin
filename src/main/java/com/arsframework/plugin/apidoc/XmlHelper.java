package com.arsframework.plugin.apidoc;

import java.util.List;

import lombok.Getter;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Xml helper
 *
 * @author Woody
 */
public final class XmlHelper {
    private XmlHelper() {
    }

    /**
     * Constructs the element
     *
     * @param name  The element name
     * @param value The element text value
     * @return The element object
     */
    public static Element element(String name, String value) {
        return new Element(name, value);
    }

    /**
     * Constructs the element
     *
     * @param name     The element name
     * @param children The child elements
     * @return The Element object
     */
    public static Element element(String name, Element... children) {
        return new Element(name, children);
    }

    /**
     * Constructs the element
     *
     * @param name       The element name
     * @param attributes The element attributes
     * @return The element object
     */
    public static Element element(String name, Attribute... attributes) {
        return new Element(name, attributes);
    }

    /**
     * Constructs the element
     *
     * @param name       The element name
     * @param children   The child elements
     * @param attributes The element attributes
     * @return The element object
     */
    public static Element element(String name, List<Element> children, List<Attribute> attributes) {
        return new Element(name, children, attributes);
    }

    /**
     * Constructs the element
     *
     * @param name     The element name
     * @param value    The element text value
     * @param children The child elements
     * @return The element object
     */
    public static Element element(String name, String value, Element... children) {
        return new Element(name, value, children);
    }

    /**
     * Constructs the element with a textual body and only attribute
     *
     * @param name       The element name
     * @param value      The element text value
     * @param attributes The element attributes
     * @return The element object
     */
    public static Element element(String name, String value, Attribute... attributes) {
        return new Element(name, value, attributes);
    }

    /**
     * Constructs the element containing child elements and attributes
     *
     * @param name       The element name
     * @param value      The element text value
     * @param children   The child elements
     * @param attributes The element attributes
     * @return The Element object
     */
    public static Element element(String name, String value, List<Element> children,
                                  List<Attribute> attributes) {
        return new Element(name, value, children, attributes);
    }

    /**
     * Converts PlexusConfiguration to a Xpp3Dom.
     *
     * @param config the PlexusConfiguration. Must not be {@code null}.
     * @return the Xpp3Dom representation of the PlexusConfiguration
     */
    public static Xpp3Dom toXpp3Dom(PlexusConfiguration config) {
        Xpp3Dom result = new Xpp3Dom(config.getName());
        result.setValue(config.getValue(null));
        for (String name : config.getAttributeNames()) {
            result.setAttribute(name, config.getAttribute(name));
        }
        for (PlexusConfiguration child : config.getChildren()) {
            result.addChild(toXpp3Dom(child));
        }
        return result;
    }

    /**
     * Builds the configuration for the goal using Elements
     *
     * @param elements A list of elements for the configuration section
     * @return The elements transformed into the Maven-native XML format
     */
    public static Xpp3Dom configuration(Element... elements) {
        Xpp3Dom dom = new Xpp3Dom("configuration");
        for (Element e : elements) {
            dom.addChild(e.toXpp3Dom());
        }
        return dom;
    }

    /**
     * Attribute wrapper class
     */
    @Getter
    public static class Attribute {
        private final String name;
        private final String value;

        public Attribute(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    /**
     * Element wrapper class for configuration elements
     */
    @Getter
    public static class Element {
        private final String name;
        private final String text;
        private final Element[] children;
        private final Attribute[] attributes;

        public Element(String name, String value) {
            this(name, value, null, null);
        }

        public Element(String name, Element... children) {
            this(name, null, children);
        }

        public Element(String name, Attribute... attributes) {
            this(name, null, attributes);
        }

        public Element(String name, List<Element> children, List<Attribute> attributes) {
            this(name, null, children, attributes);
        }

        public Element(String name, String text, Element... children) {
            this.name = name;
            this.text = text;
            this.children = children;
            this.attributes = null;
        }

        public Element(String name, String text, Attribute... attributes) {
            this.name = name;
            this.text = text;
            this.children = null;
            this.attributes = attributes;
        }

        public Element(String name, String text, List<Element> children, List<Attribute> attributes) {
            this.name = name;
            this.text = text;
            this.children = children == null || children.isEmpty() ? null : children.toArray(new Element[0]);
            this.attributes = attributes == null || attributes.isEmpty() ? null : attributes.toArray(new Attribute[0]);
        }

        /**
         * Convert Element to Xpp3Dom
         *
         * @return The Xpp3Dom object
         */
        public Xpp3Dom toXpp3Dom() {
            Xpp3Dom dom = new Xpp3Dom(this.name);
            if (this.text != null) {
                dom.setValue(this.text);
            }
            if (this.children != null) {
                for (Element e : this.children) {
                    dom.addChild(e.toXpp3Dom());
                }
            }
            if (this.attributes != null) {
                for (Attribute attribute : this.attributes) {
                    dom.setAttribute(attribute.name, attribute.value);
                }
            }
            return dom;
        }
    }
}
