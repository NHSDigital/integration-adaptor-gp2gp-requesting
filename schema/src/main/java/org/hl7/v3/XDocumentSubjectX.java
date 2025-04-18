
package org.hl7.v3;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;


/**
 * &lt;p&gt;Java class for x_DocumentSubject_X.
 * 
 * &lt;p&gt;The following schema fragment specifies the expected content contained within this class.
 * &lt;pre&gt;
 * &amp;lt;simpleType name="x_DocumentSubject_X"&amp;gt;
 *   &amp;lt;restriction base="{urn:hl7-org:v3}cs"&amp;gt;
 *     &amp;lt;enumeration value="PRS"/&amp;gt;
 *     &amp;lt;enumeration value="PAT"/&amp;gt;
 *   &amp;lt;/restriction&amp;gt;
 * &amp;lt;/simpleType&amp;gt;
 * &lt;/pre&gt;
 * 
 */
@XmlType(name = "x_DocumentSubject_X")
@XmlEnum
public enum XDocumentSubjectX {

    PRS,
    PAT;

    public String value() {
        return name();
    }

    public static XDocumentSubjectX fromValue(String v) {
        return valueOf(v);
    }

}
