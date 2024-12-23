package uk.nhs.adaptors.pss.gpc.util.fhir;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hl7.fhir.dstu3.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.dstu3.model.OperationOutcome.IssueType.NOTSUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.junit.jupiter.api.Test;

import uk.nhs.adaptors.common.util.CodeableConceptUtils;

public class OperationOutcomeUtilsTest {
    private static final String URI_TYPE = "https://fhir.nhs.uk/STU3/StructureDefinition/GPConnect-OperationOutcome-1";

    @Test
    public void testCreateOperationOutcome() {

        var details = createCodeableConcept();
        var diagnostics = "Dancing not supported";

        var result = OperationOutcomeUtils.createOperationOutcome(NOTSUPPORTED, ERROR, details, diagnostics);

        assertThat(result.getMeta().getProfile().getFirst().equals(URI_TYPE)).isTrue();
        assertEquals(NOTSUPPORTED, result.getIssueFirstRep().getCode());
        assertEquals(ERROR, result.getIssueFirstRep().getSeverity());
        assertEquals(details, result.getIssueFirstRep().getDetails());
        assertEquals(diagnostics, result.getIssueFirstRep().getDiagnostics());
    }

    private CodeableConcept createCodeableConcept() {
        return CodeableConceptUtils.createCodeableConcept("code", "system", "display", "text");
    }
}
