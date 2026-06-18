package uk.nhs.adaptors.pss.gpc.controller;

import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.nhs.adaptors.common.util.FileUtil.readResourceAsString;

import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import uk.nhs.adaptors.pss.gpc.GpcFacadeApplication;

@SpringBootTest(classes = GpcFacadeApplication.class)
@AutoConfigureMockMvc
@Transactional
public class PatientTransferContractIT {

    private static final String ENDPOINT = "/Patient/$gpc.migratestructuredrecord";
    private static final String CONTENT_TYPE = "application/fhir+json";
    public static final String CONVERSATION_ID = "ConversationId";
    public static final int NHS_NUMBER = 10;

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void shouldReturnOperationOutcomeForInvalidRequestPayload() throws Exception {
        var requestBody = buildRequestBody("/requests/migrate-patient-record/invalidRequestBody.json");

        mockMvc.perform(post(ENDPOINT)
                .contentType(CONTENT_TYPE)
                .headers(requiredHeaders())
                .header(CONVERSATION_ID, UUID.randomUUID().toString().toUpperCase(Locale.ROOT))
                .content(requestBody))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentType(CONTENT_TYPE))
            .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
            .andExpect(jsonPath("$.issue[0].code").value("invalid"))
            .andExpect(jsonPath("$.issue[0].details.coding[0].code").value("INVALID_RESOURCE"));
    }

    private String buildRequestBody(String templatePath) {
        return readResourceAsString(templatePath).replace("{{nhsNumber}}", randomNumeric(NHS_NUMBER));
    }

    private static HttpHeaders requiredHeaders() {
        var headers = new HttpHeaders();
        headers.set("from-asid", "123456");
        headers.set("to-asid", "32145");
        headers.set("from-ods", "B943");
        headers.set("to-ods", "F765");
        return headers;
    }
}

