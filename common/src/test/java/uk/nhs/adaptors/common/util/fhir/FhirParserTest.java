package uk.nhs.adaptors.common.util.fhir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hl7.fhir.dstu3.model.Parameters;
import org.junit.jupiter.api.Test;

import ca.uhn.fhir.context.FhirContext;
import uk.nhs.adaptors.common.exception.FhirValidationException;

public class FhirParserTest {

    private final FhirParser fhirParser = new FhirParser(FhirContext.forDstu3());

    @Test
    public void shouldParseParametersResource() {
        var json = "{"
            + "\"resourceType\":\"Parameters\","
            + "\"parameter\":[{\"name\":\"test-name\",\"valueString\":\"value\"}]"
            + "}";

        var parsed = fhirParser.parseResource(json, Parameters.class);

        assertEquals("test-name", parsed.getParameterFirstRep().getName());
        assertEquals("value", parsed.getParameterFirstRep().getValue().primitiveValue());
    }

    @Test
    public void shouldThrowValidationExceptionForInvalidJson() {
        assertThrows(FhirValidationException.class, () -> fhirParser.parseResource("{not-json}", Parameters.class));
    }

    @Test
    public void shouldEncodeResourceToJson() {
        var parameters = new Parameters();
        parameters.addParameter().setName("test-name").setValue(new org.hl7.fhir.dstu3.model.StringType("value"));

        var encoded = fhirParser.encodeToJson(parameters);

        assertTrue(encoded.contains("\"resourceType\": \"Parameters\""));
        assertTrue(encoded.contains("\"name\": \"test-name\""));
    }
}

