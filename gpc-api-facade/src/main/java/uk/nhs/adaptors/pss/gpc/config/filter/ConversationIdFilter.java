package uk.nhs.adaptors.pss.gpc.config.filter;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;

import static org.hl7.fhir.dstu3.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.dstu3.model.OperationOutcome.IssueType.INVALID;
import static org.springframework.util.ObjectUtils.isEmpty;
import static uk.nhs.adaptors.pss.gpc.controller.handler.FhirMediaTypes.APPLICATION_FHIR_JSON_VALUE;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import lombok.RequiredArgsConstructor;
import uk.nhs.adaptors.common.service.MDCService;
import uk.nhs.adaptors.common.util.CodeableConceptUtils;
import uk.nhs.adaptors.common.util.fhir.FhirParser;
import uk.nhs.adaptors.pss.gpc.util.fhir.OperationOutcomeUtils;

import java.io.IOException;
import java.util.UUID;


@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Component
public class ConversationIdFilter extends OncePerRequestFilter {
    private final MDCService mdcService;
    private final FhirParser fhirParser;

    private static final String UUID_REGEX =
        "^[0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12}$";
    private static final String CONVERSATION_ID = "ConversationId";

    @Override
    protected void doFilterInternal(
        final HttpServletRequest request,
        final HttpServletResponse response,
        final FilterChain chain)
        throws java.io.IOException, ServletException {
        try {
            var token = request.getHeader(CONVERSATION_ID);
            if (isEmpty(token)) {
                token = UUID.randomUUID().toString();
            }
            token = token.toUpperCase();

            if (!token.matches(UUID_REGEX)) {
                setInvalidConversationIdResponse(response);
                return;
            }

            mdcService.applyConversationId(token);
            token = encode(token, UTF_8);
            response.addHeader(CONVERSATION_ID, token);
            chain.doFilter(request, response);
        } finally {
            mdcService.resetAllMdcKeys();
        }
    }

    private void setInvalidConversationIdResponse(
        final HttpServletResponse response
    ) throws IOException {
        var content = fhirParser.encodeToJson(
            OperationOutcomeUtils.createOperationOutcome(
                INVALID,
                ERROR,
                CodeableConceptUtils.createCodeableConcept(
                    "BAD_REQUEST",
                    "https://fhir.nhs.uk/STU3/ValueSet/Spine-ErrorOrWarningCode-1",
                    "Bad Request"
                ),
                "ConversationId header must be either be absent, empty or a valid UUID"
            )
        );
        response.resetBuffer();
        response.setStatus(SC_BAD_REQUEST);
        response.setHeader(CONTENT_TYPE, APPLICATION_FHIR_JSON_VALUE);
        response.getOutputStream().print(content);
        response.flushBuffer();
    }
}
