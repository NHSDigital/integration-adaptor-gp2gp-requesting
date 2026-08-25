package uk.nhs.adaptors.pss.gpc.util.fhir;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.hl7.fhir.dstu3.model.Extension;
import org.junit.jupiter.api.Test;
import uk.nhs.adaptors.common.util.CodeableConceptUtils;

public class CodeableConceptUtilsTest {

    private static final String CODE = "RESOURCE_NOT_FOUND";
    private static final String ISSUE_SYSTEM = "Spine-ErrorOrWarningCode-1";
    private static final String DISPLAY = "Resource not found";
    private static final String TEXT = "Resource got lost";
    private static final String EHR_REQUEST_ACK_OID_URN = "urn:oid:2.16.840.1.113883.2.1.3.2.4.17.101";
    private static final String GP2GP_SPECIFIC_CODE = "99";

    @Test
    public void When_CreateCodeableConcept_Expect_CodeableConceptIsCreatedCorrectly() {
        final var result = CodeableConceptUtils.createCodeableConcept(CODE, ISSUE_SYSTEM, DISPLAY, TEXT);

        assertAll(
                () -> assertEquals(CODE, result.getCodingFirstRep().getCode()),
                () -> assertEquals(ISSUE_SYSTEM, result.getCodingFirstRep().getSystem()),
                () -> assertEquals(DISPLAY, result.getCodingFirstRep().getDisplay()),
                () -> assertEquals(TEXT, result.getText())
        );
    }

    @Test
    public void When_CreateCodeableConceptWithOidAsSystem_Expect_CreatedCodeableConceptContainsOidAsUrn() {
        final var system = "1.2.3.4.5";
        final var expectedSystem = "urn:oid:1.2.3.4.5";

        final var result = CodeableConceptUtils.createCodeableConcept(CODE, system, DISPLAY, TEXT);

        assertEquals(expectedSystem, result.getCodingFirstRep().getSystem());
    }

    @Test
    public void When_CreateCodeableConceptWithNullText_Expect_CreatedCodeableConceptDoesNotContainText() {
        final var result = CodeableConceptUtils.createCodeableConcept(CODE, ISSUE_SYSTEM, DISPLAY, null);

        assertNull(result.getText());
    }

    @Test
    public void When_CreateCodeableConceptWithExtension_Expect_CreatedCodeableContainsThisExtension() {
        final var EXTENSION_URL = "https://fhir.nhs.uk/STU3/StructureDefinition/Extension-coding-sctdescid";
        final var extension = new Extension().setUrl(EXTENSION_URL);

        final var result = CodeableConceptUtils.createCodeableConcept(
                CODE,
                ISSUE_SYSTEM,
                DISPLAY,
                null,
                extension);

        assertEquals(EXTENSION_URL, result.getCoding().getFirst().getExtension().getFirst().getUrlElement().getValue());
    }

    @Test
    public void When_CreateCodeableConceptWithEhrRequestAckOidCode_Expect_CodeableConceptIsCreatedCorrectly() {
        final var result = CodeableConceptUtils.createCodeableConceptWithEhrRequestAckOidCode(
                CODE,
                ISSUE_SYSTEM,
                DISPLAY,
                null,
                GP2GP_SPECIFIC_CODE);
        final var actualBaseCoding = result.getCoding().getFirst();
        final var actualEhrRequestAckCoding = result.getCoding().get(1);

        assertAll(
                () -> assertEquals(ISSUE_SYSTEM, actualBaseCoding.getSystem()),
                () -> assertEquals(CODE, actualBaseCoding.getCode()),
                () -> assertEquals(DISPLAY, actualBaseCoding.getDisplay()),
                () -> assertEquals(EHR_REQUEST_ACK_OID_URN, actualEhrRequestAckCoding.getSystem()),
                () -> assertEquals(GP2GP_SPECIFIC_CODE, actualEhrRequestAckCoding.getCode()),
                () -> assertEquals(DISPLAY, actualEhrRequestAckCoding.getDisplay())
        );
    }

    @Test
    public void When_CreateCodeableConceptWithEhrRequestAckOidCodeWithSystemOid_Expect_SystemIsMappedToUrn() {
        final var system = "1.2.3.4.5";
        final var expectedSystem = "urn:oid:1.2.3.4.5";

        final var result = CodeableConceptUtils.createCodeableConceptWithEhrRequestAckOidCode(
                CODE,
                system,
                DISPLAY,
                null,
                GP2GP_SPECIFIC_CODE);

        assertEquals(expectedSystem, result.getCoding().getFirst().getSystem());
    }
}
