package org.hl7.v3;

import java.util.List;
import javax.xml.bind.annotation.XmlSeeAlso;

public interface RCMRMT030101UKPatient {
    II getId();

    void setId(II value);

    String getType();

    void setType(String value);

    List<String> getClassCode();

    List<String> getTypeID();

    List<String> getRealmCode();

    String getNullFlavor();

    void setNullFlavor(String value);
}