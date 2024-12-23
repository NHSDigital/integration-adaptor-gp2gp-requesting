package config.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import uk.nhs.adaptors.common.service.MDCService;
import uk.nhs.adaptors.common.util.fhir.FhirParser;
import uk.nhs.adaptors.pss.gpc.config.filter.ConversationIdFilter;

import java.io.IOException;
import java.util.UUID;

import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static uk.nhs.adaptors.pss.gpc.controller.handler.FhirMediaTypes.APPLICATION_FHIR_JSON_VALUE;

@ExtendWith(MockitoExtension.class)
public class ConversationIdFilterTest {

    @Mock
    private MDCService mdcService;

    @Mock
    private FhirParser fhirParser;

    @Mock
    private FilterChain filterChain;

    private ConversationIdFilter conversationIdFilter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private static final String CONVERSATION_ID = "ConversationId";
    private static final UUID GENERATED_CONVERSATION_ID = UUID.randomUUID();

    @BeforeEach
    public void setUp() {
        conversationIdFilter = new ConversationIdFilter(mdcService, fhirParser);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "f099635b-5d8b-4dd1-af2e-16b2e492bb03",
        "FEDE0DCA-9D6A-42B6-B34F-DB4ADF6FDE2B",
        "this-is-quite-clearly-not-a-conversation-id"
    })
    @EmptySource
    public void When_ConversationIdFilter_Expect_MdcServiceIsReset(
        String conversationId
    ) throws ServletException, IOException {
        request.addHeader(CONVERSATION_ID, conversationId);

        doConversationIdFilter();

        Mockito.verify(mdcService, Mockito.times(1))
            .resetAllMdcKeys();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "f099635b-5d8b-4dd1-af2e-16b2e492bb03",
        "FEDE0DCA-9D6A-42B6-B34F-DB4ADF6FDE2B"
    })
    public void When_ConversationIdFilterWithValidConversationId_Expect_UppercaseIdIsAppliedToMdcService(
        String conversationId
    ) throws ServletException, IOException {
        request.addHeader(CONVERSATION_ID, conversationId);

        doConversationIdFilter();

        Mockito.verify(mdcService, Mockito.times(1))
            .applyConversationId(conversationId.toUpperCase());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "f099635b-5d8b-4dd1-af2e-16b2e492bb03",
        "FEDE0DCA-9D6A-42B6-B34F-DB4ADF6FDE2B"
    })
    public void When_ConversationIdFilterWithValidConversationId_Expect_UppercaseIdIsAddedToResponseHeaders(
        String conversationId
    ) throws ServletException, IOException {
        request.addHeader(CONVERSATION_ID, conversationId);

        doConversationIdFilter();

        assertThat(response.getHeader(CONVERSATION_ID))
            .isEqualTo(conversationId.toUpperCase());
    }

    @Test
    public void When_ConversationIdFilterWithNoConversationIdHeader_Expect_UppercaseIdIsAppliedToMdcService()
        throws ServletException, IOException {

        doConversationIdFilter();

        Mockito.verify(mdcService, Mockito.times(1))
            .applyConversationId(GENERATED_CONVERSATION_ID.toString().toUpperCase());
    }

    @Test
    public void When_ConversationIdFilterWithNoConversationIdHeader_Expect_UppercaseIdIsAddedToResponseHeaders()
        throws ServletException, IOException {

        doConversationIdFilter();

        assertThat(response.getHeader(CONVERSATION_ID))
            .isEqualTo(GENERATED_CONVERSATION_ID.toString().toUpperCase());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "f099635b-5d8b-4dd1-af2e-16b2e492bb03",
        "FEDE0DCA-9D6A-42B6-B34F-DB4ADF6FDE2B"
    })
    public void When_ConversationIdFilterWithValidConversationId_Expect_NextFilterInChainIsCalled(
        String conversationId
    ) throws ServletException, IOException {
        request.addHeader(CONVERSATION_ID, conversationId);

        doConversationIdFilter();

        Mockito.verify(filterChain, Mockito.times(1))
            .doFilter(request, response);
    }

    @Test
    public void When_ConversationIdFilterWithInvalidConversationId_Expect_BadRequestOperationOutcomeResponse()
        throws ServletException, IOException {

        request.addHeader(CONVERSATION_ID, "this-is-quite-clearly-not-a-conversation-id");
        var expectedContent = """
            {
                "resourceType": "OperationOutcome",
                "meta": {
                    "profile": [
                        "https://fhir.nhs.uk/STU3/StructureDefinition/GPConnect-OperationOutcome-1"
                    ]
                },
                "issue": [
                    {
                        "severity": "error",
                        "code": "invalid",
                        "details": {
                            "coding": [
                                {
                                    "system": "https://fhir.nhs.uk/STU3/ValueSet/Spine-ErrorOrWarningCode-1",
                                    "code": "BAD_REQUEST",
                                    "display": "ConversationId header must be either be absent, empty or a valid UUID"
                                }
                            ]
                        },
                        "diagnostics": "ConversationId header must be either be absent, empty or a valid UUID"
                    }
                ]
            }""";
        when(fhirParser.encodeToJson(any())).thenReturn(expectedContent);

        doConversationIdFilter();

        assertAll(
            () -> assertThat(response.getStatus())
                .isEqualTo(HttpServletResponse.SC_BAD_REQUEST),
            () -> assertThat(response.getHeader(CONTENT_TYPE))
                .isEqualTo(APPLICATION_FHIR_JSON_VALUE),
            () -> assertThat(response.getContentAsString())
                .isEqualTo(expectedContent)
        );
    }

    private void doConversationIdFilter() throws ServletException, IOException {
        try (var mockedUuid = mockStatic(UUID.class)) {
            mockedUuid.when(UUID::randomUUID).thenReturn(GENERATED_CONVERSATION_ID);

            conversationIdFilter.doFilter(request, response, filterChain);
        }
    }
}
