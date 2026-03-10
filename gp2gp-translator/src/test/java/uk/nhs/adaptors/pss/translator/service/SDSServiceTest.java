package uk.nhs.adaptors.pss.translator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.parser.StrictErrorHandler;
import org.hl7.fhir.dstu3.model.Bundle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import uk.nhs.adaptors.common.exception.FhirValidationException;
import uk.nhs.adaptors.common.util.fhir.FhirParser;
import uk.nhs.adaptors.pss.translator.exception.SdsRetrievalException;
import uk.nhs.adaptors.pss.translator.sds.SdsRequestBuilder;

@ExtendWith(MockitoExtension.class)
public class SDSServiceTest {

    private static final String EHR_EXTRACT_MESSAGE_TYPE = "RCMR_IN030000UK07";
    private static final String COPC_MESSAGE_TYPE = "COPC_IN000001UK01";
    private static final String TEST_ODS_CODE = "P83007";
    private static final String TEST_CONVERSATION_ID = "test-conversation-id";
    private static final Duration EHR_DURATION = Duration.parse("PT4H10M");
    private static final Duration COPC_DURATION = Duration.parse("PT7H");

    private static String sdsResponseEHRExtract;
    private static Bundle ehrResponseBundle;
    private static String sdsResponseCopcMessage;
    private static Bundle copcResponseBundle;
    private static String sdsResponseNoResults;
    private static Bundle noResultsBundle;


    @Mock
    private FhirParser fhirParser;

    @Mock
    private SdsRequestBuilder sdsRequestBuilder;

    @Mock
    private SdsClientService sdsClientService;

    @InjectMocks
    private SDSService sdsService;

    @BeforeAll
    public static void parseResponses() throws IOException {

        FhirContext context = FhirContext.forDstu3();
        context.newJsonParser();
        context.setParserErrorHandler(new StrictErrorHandler());
        IParser jsonParser = context.newJsonParser();

        readResponsesFromFile();

        ehrResponseBundle = jsonParser.parseResource(Bundle.class, sdsResponseEHRExtract);
        copcResponseBundle = jsonParser.parseResource(Bundle.class, sdsResponseCopcMessage);
        noResultsBundle = jsonParser.parseResource(Bundle.class, sdsResponseNoResults);
    }

    private static void readResponsesFromFile() throws IOException {
        sdsResponseEHRExtract = readFileAsString("sds/sdsResponseEHRExtract.json");
        sdsResponseCopcMessage = readFileAsString("sds/sdsResponseCopcMessage.json");
        sdsResponseNoResults = readFileAsString("sds/sdsResponseNoResults.json");
    }

    private static String readFileAsString(String path) throws IOException {
        Resource resource = new ClassPathResource(path);
        return Files.readString(Paths.get(resource.getURI()));
    }

    @Test
    public void When_GetPersistDurationForEHRExtract_WithWebClientResponseException_Expect_ThrowsSdsRetrievalException() {
        when(sdsClientService.send(any())).thenThrow(new WebClientResponseException(
                HttpStatus.BAD_REQUEST.value(),
                "BAD REQUEST",
                new HttpHeaders(),
                new byte[]{},
                Charset.defaultCharset())
        );

        assertThrows(SdsRetrievalException.class, () -> sdsService.getPersistDurationFor(EHR_EXTRACT_MESSAGE_TYPE, TEST_ODS_CODE,
                TEST_CONVERSATION_ID));
    }

    @Test
    public void When_GetPersistDurationFor_WhenEHRExtractValidInput_Expect_CorrectDuration() {
        when(sdsClientService.send(any())).thenReturn(sdsResponseEHRExtract);
        when(fhirParser.parseResource(any(), eq(Bundle.class))).thenReturn(ehrResponseBundle);

        Duration parsedDuration = sdsService.getPersistDurationFor(EHR_EXTRACT_MESSAGE_TYPE, TEST_ODS_CODE, TEST_CONVERSATION_ID);

        assertEquals(EHR_DURATION, parsedDuration);
    }

    @Test
    public void When_GetNhsMhsPartyKey_WhenEHRExtractValidInput_Expect_CorrectNhsMhsPartyKey() {

        when(fhirParser.parseResource(any(), eq(Bundle.class))).thenReturn(ehrResponseBundle);

        String nhsMhsPartyKey = sdsService.parseNhsMhsPartyKey(sdsResponseEHRExtract);

        assertEquals("P83007-822482", nhsMhsPartyKey);
    }

    @Test
    public void When_GetPersistDurationFor_WhenCOPCValidInput_Expect_CorrectDuration() {
        when(sdsClientService.send(any())).thenReturn(sdsResponseCopcMessage);
        when(fhirParser.parseResource(any(), eq(Bundle.class))).thenReturn(copcResponseBundle);

        Duration parsedDuration = sdsService.getPersistDurationFor(COPC_MESSAGE_TYPE, TEST_ODS_CODE, TEST_CONVERSATION_ID);

        assertEquals(COPC_DURATION, parsedDuration);
    }

    @Test
    public void When_GetPersistDurationFor_WhenNoResults_Expect_SdsRetrievalException() {
        when(sdsClientService.send(any())).thenReturn(sdsResponseNoResults);
        when(fhirParser.parseResource(any(), eq(Bundle.class))).thenReturn(noResultsBundle);

        assertThrows(SdsRetrievalException.class,
                () -> sdsService.getPersistDurationFor(COPC_MESSAGE_TYPE, TEST_ODS_CODE, TEST_CONVERSATION_ID));
    }

    @Test
    public void When_GetPersistDurationFor_FHIRValidationException_Expect_SdsRetrievalException() {
        when(sdsClientService.send(any())).thenReturn(sdsResponseEHRExtract);
        when(fhirParser.parseResource(any(), eq(Bundle.class))).thenThrow(FhirValidationException.class);

        assertThrows(SdsRetrievalException.class,
                () -> sdsService.getPersistDurationFor(EHR_EXTRACT_MESSAGE_TYPE, TEST_ODS_CODE, TEST_CONVERSATION_ID));
    }

}
