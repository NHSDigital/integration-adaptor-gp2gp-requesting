
package org.hl7.v3;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;


/**
 * &lt;p&gt;Java class for RoleClassOntological_X.
 * 
 * &lt;p&gt;The following schema fragment specifies the expected content contained within this class.
 * &lt;pre&gt;
 * &amp;lt;simpleType name="RoleClassOntological_X"&amp;gt;
 *   &amp;lt;restriction base="{urn:hl7-org:v3}cs"&amp;gt;
 *     &amp;lt;enumeration value="SUBY"/&amp;gt;
 *     &amp;lt;enumeration value="SUBS"/&amp;gt;
 *     &amp;lt;enumeration value="INST"/&amp;gt;
 *   &amp;lt;/restriction&amp;gt;
 * &amp;lt;/simpleType&amp;gt;
 * &lt;/pre&gt;
 * 
 */
@XmlType(name = "RoleClassOntological_X")
@XmlEnum
public enum RoleClassOntologicalX {

    SUBY,
    SUBS,
    INST;

    public String value() {
        return name();
    }

    public static RoleClassOntologicalX fromValue(String v) {
        return valueOf(v);
    }

}
