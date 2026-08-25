
package org.hl7.v3;

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlSeeAlso;
import jakarta.xml.bind.annotation.XmlType;

/**
 * 
 * A name for a person, organization, place or thing. A sequence of name
 * parts, such as given name or family name, prefix, suffix, etc.
 * Examples for entity name values are "Jim Bob Walton, Jr.", "Health
 * Level Seven, Inc.", "Lake Tahoe", etc. An entity name may be as simple
 * as a character string or may consist of several entity name parts,
 * such as, "Jim", "Bob", "Walton", and "Jr.", "Health Level Seven" and
 * "Inc.", "Lake" and "Tahoe".
 * 
 * 
 * &lt;p&gt;Java class for EN complex type.
 * 
 * &lt;p&gt;The following schema fragment specifies the expected content contained within this class.
 * 
 * &lt;pre&gt;
 * &amp;lt;complexType name="EN"&amp;gt;
 *   &amp;lt;complexContent&amp;gt;
 *     &amp;lt;extension base="{urn:hl7-org:v3}ANY"&amp;gt;
 *       &amp;lt;sequence&amp;gt;
 *         &amp;lt;choice maxOccurs="unbounded" minOccurs="0"&amp;gt;
 *           &amp;lt;element name="delimiter" type="{urn:hl7-org:v3}en.delimiter"/&amp;gt;
 *           &amp;lt;element name="family" type="{urn:hl7-org:v3}en.family"/&amp;gt;
 *           &amp;lt;element name="given" type="{urn:hl7-org:v3}en.given"/&amp;gt;
 *           &amp;lt;element name="prefix" type="{urn:hl7-org:v3}en.prefix"/&amp;gt;
 *           &amp;lt;element name="suffix" type="{urn:hl7-org:v3}en.suffix"/&amp;gt;
 *         &amp;lt;/choice&amp;gt;
 *         &amp;lt;element name="validTime" type="{urn:hl7-org:v3}IVL_TS" minOccurs="0"/&amp;gt;
 *         &amp;lt;element name="id" type="{urn:hl7-org:v3}II" minOccurs="0"/&amp;gt;
 *       &amp;lt;/sequence&amp;gt;
 *       &amp;lt;attribute name="use" type="{urn:hl7-org:v3}set_cs_EntityNameUse" /&amp;gt;
 *     &amp;lt;/extension&amp;gt;
 *   &amp;lt;/complexContent&amp;gt;
 * &amp;lt;/complexType&amp;gt;
 * &lt;/pre&gt;
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "EN", propOrder = {
    "delimiter",
    "family",
    "given",
    "prefix",
    "suffix",
    "validTime",
    "id"
})
@XmlSeeAlso({
    PN.class,
    ON.class,
    TN.class
})
public class EN {

    @XmlAttribute(name = "use")
    protected List<CsEntityNameUse> use;
    protected String delimiter;
    protected String family;
    protected List<String> given;
    protected String prefix;
    protected String suffix;
    protected IVLTS validTime;
    protected String id;

    /**
     * Gets the value of the use property.
     * 
     * &lt;p&gt;
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a &lt;CODE&gt;set&lt;/CODE&gt; method for the use property.
     * 
     * &lt;p&gt;
     * For example, to add a new item, do as follows:
     * &lt;pre&gt;
     *    getUse().add(newItem);
     * &lt;/pre&gt;
     * 
     * 
     * &lt;p&gt;
     * Objects of the following type(s) are allowed in the list
     * {@link CsEntityNameUse }
     * 
     * 
     */
    public List<CsEntityNameUse> getUse() {
        if (use == null) {
            use = new ArrayList<>();
        }
        return this.use;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    /**
     * Gets the value of the given property.
     *
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the given property.
     *
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getGiven().add(newItem);
     * </pre>
     *
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     */
    public List<String> getGiven() {
        if (given == null) {
            given = new ArrayList<>();
        }
        return this.given;
    }

    /**
     * Convenience method for backward compatibility - returns the first given name as a string.
     */
    public String getFirstGiven() {
        if (given != null && !given.isEmpty()) {
            return given.get(0);
        }
        return null;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public IVLTS getValidTime() {
        return validTime;
    }

    public void setValidTime(IVLTS validTime) {
        this.validTime = validTime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
