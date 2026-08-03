package uk.nhs.adaptors.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.hl7.fhir.dstu3.model.Extension;
import org.junit.jupiter.api.Test;

public class CodeableConceptUtilsTest {

    @Test
    public void shouldCreateCodeableConceptWithTextAndUrnFormattedSystem() {
        var codeableConcept = CodeableConceptUtils.createCodeableConcept("123", "1.2.3", "Display value", "Some text");

        assertEquals("Some text", codeableConcept.getText());
        assertEquals("123", codeableConcept.getCodingFirstRep().getCode());
        assertEquals("urn:oid:1.2.3", codeableConcept.getCodingFirstRep().getSystem());
        assertEquals("Display value", codeableConcept.getCodingFirstRep().getDisplay());
    }

    @Test
    public void shouldCreateCodeableConceptWithGp2gpSpecificCoding() {
        var codeableConcept = CodeableConceptUtils.createCodeableConceptWithEhrRequestAckOidCode(
            "INTERNAL_SERVER_ERROR",
            "1.2.3",
            "Error display",
            "Any text",
            "25"
        );

        assertEquals(2, codeableConcept.getCoding().size());
        assertEquals("urn:oid:1.2.3", codeableConcept.getCoding().get(0).getSystem());
        assertEquals(CodeableConceptUtils.EHR_REQUEST_ACK_CODE_URN, codeableConcept.getCoding().get(1).getSystem());
        assertEquals("25", codeableConcept.getCoding().get(1).getCode());
    }b

    @Test
    public void shouldCreateCodeableConceptWithExtension() {
        var extension = new Extension("https://example.test/extension");

        var codeableConcept = CodeableConceptUtils.createCodeableConcept("CODE", "http://system", "Display", "Text", extension);

        assertEquals("http://system", codeableConcept.getCodingFirstRep().getSystem());
        assertEquals("CODE", codeableConcept.getCodingFirstRep().getCode());
        assertEquals(1, codeableConcept.getCodingFirstRep().getExtension().size());
        assertEquals("https://example.test/extension", codeableConcept.getCodingFirstRep().getExtensionFirstRep().getUrl());
    }
}

