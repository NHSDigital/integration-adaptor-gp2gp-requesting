package uk.nhs.adaptors.pss.gpc.controller.handler;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;

import static org.hl7.fhir.dstu3.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.dstu3.model.OperationOutcome.IssueSeverity.INFORMATION;
import static org.hl7.fhir.dstu3.model.OperationOutcome.IssueType.EXCEPTION;
import static org.hl7.fhir.dstu3.model.OperationOutcome.IssueType.INVALID;
import static org.hl7.fhir.dstu3.model.OperationOutcome.IssueType.NOTFOUND;
import static org.hl7.fhir.dstu3.model.OperationOutcome.IssueType.NOTSUPPORTED;
import static org.springframework.http.HttpHeaders.ALLOW;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

import static uk.nhs.adaptors.pss.gpc.controller.handler.FhirMediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static uk.nhs.adaptors.pss.gpc.util.fhir.OperationOutcomeUtils.createOperationOutcome;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.OperationOutcome;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.adaptors.common.exception.FhirValidationException;
import uk.nhs.adaptors.common.util.CodeableConceptUtils;
import uk.nhs.adaptors.common.util.fhir.FhirParser;

@ControllerAdvice
@Slf4j
public class OperationOutcomeExceptionHandler extends ResponseEntityExceptionHandler {
    private static final String ISSUE_SYSTEM = "https://fhir.nhs.uk/STU3/ValueSet/Spine-ErrorOrWarningCode-1";
    private static final Map<String, List<HttpMethod>> ALLOWED_METHODS = Map.of(
        "/Patient/$gpc.migratestructuredrecord", List.of(POST),
            "/$gpc.ack", List.of(POST)
    );

    @Autowired
    private FhirParser fhirParser;

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
        NoHandlerFoundException ex, HttpHeaders requestHeaders, HttpStatusCode status, WebRequest request) {
        HttpServletRequest servletReq = ((ServletWebRequest) request).getRequest();
        String errorMessage = servletReq.getRequestURI() + " not found";
        CodeableConcept details = CodeableConceptUtils.createCodeableConcept("RESOURCE_NOT_FOUND", ISSUE_SYSTEM, "Resource not found",
            errorMessage);
        OperationOutcome operationOutcome = createOperationOutcome(NOTFOUND, INFORMATION, details, null);
        return errorResponse(requestHeaders, status, operationOutcome);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
        HttpRequestMethodNotSupportedException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        headers.put(ALLOW, List.of(getAllowedMethods(request)));
        CodeableConcept details = CodeableConceptUtils.createCodeableConcept("METHOD_NOT_SUPPORTED", ISSUE_SYSTEM, "Method not supported",
            null);
        OperationOutcome operationOutcome = createOperationOutcome(NOTSUPPORTED, ERROR, details, ex.getMessage());
        return errorResponse(headers, status, operationOutcome);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
        HttpMediaTypeNotSupportedException ex, HttpHeaders requestHeaders, HttpStatusCode status, WebRequest request) {
        CodeableConcept details = CodeableConceptUtils.createCodeableConcept("UNSUPPORTED_MEDIA_TYPE", ISSUE_SYSTEM, "Unsupported media "
            + "type", null);
        OperationOutcome operationOutcome = createOperationOutcome(NOTSUPPORTED, ERROR, details, ex.getMessage());
        return errorResponse(requestHeaders, status, operationOutcome);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
        Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleAllExceptions(ex);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
        HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return unprocessableEntityResponse(ex);
    }

    @SneakyThrows
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolationException(ConstraintViolationException ex) {
        return unprocessableEntityResponse(ex);
    }

    @SneakyThrows
    @ExceptionHandler(FhirValidationException.class)
    public ResponseEntity<Object> handleFhirValidationException(FhirValidationException ex) {
        return unprocessableEntityResponse(ex);
    }

    @SneakyThrows
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Object> handleMissingRequestHeaderException(MissingRequestHeaderException ex) {
        CodeableConcept details = CodeableConceptUtils.createCodeableConcept("BAD_REQUEST", ISSUE_SYSTEM, "Bad request", null);
        OperationOutcome operationOutcome = createOperationOutcome(INVALID, ERROR, details, ex.getMessage());
        return errorResponse(new HttpHeaders(), BAD_REQUEST, operationOutcome);
    }

    @ExceptionHandler(Exception.class)
    private ResponseEntity<Object> handleAllExceptions(Exception ex) {
        LOGGER.error("Error occurred: {}", ex.getMessage());
        CodeableConcept details = CodeableConceptUtils.createCodeableConcept("INTERNAL_SERVER_ERROR", ISSUE_SYSTEM, "Internal server "
            + "error", null);
        OperationOutcome operationOutcome = createOperationOutcome(
            EXCEPTION, ERROR, details, "Internal server error. Please contact the administrator.");
        return errorResponse(new HttpHeaders(), INTERNAL_SERVER_ERROR, operationOutcome);
    }

    private ResponseEntity<Object> unprocessableEntityResponse(Exception ex) {
        LOGGER.error("Error occurred: {}", ex.getMessage());
        CodeableConcept details = CodeableConceptUtils.createCodeableConcept("INVALID_RESOURCE", ISSUE_SYSTEM, "Submitted resource is not"
            + " valid", null);
        OperationOutcome operationOutcome = createOperationOutcome(INVALID, ERROR, details, "Failed to parse request payload");
        return errorResponse(new HttpHeaders(), UNPROCESSABLE_ENTITY, operationOutcome);
    }

    private ResponseEntity<Object> errorResponse(HttpHeaders headers, HttpStatusCode status, OperationOutcome operationOutcome) {
        headers = new HttpHeaders(headers);
        headers.put(CONTENT_TYPE, singletonList(APPLICATION_FHIR_JSON_VALUE));
        String content = fhirParser.encodeToJson(operationOutcome);
        return new ResponseEntity<>(content, headers, status);
    }

    private String getAllowedMethods(WebRequest request) {
        HttpServletRequest servletReq = ((ServletWebRequest) request).getRequest();
        String requestURI = servletReq.getRequestURI();
        return ALLOWED_METHODS.get(requestURI)
            .stream()
            .map(HttpMethod::name)
            .collect(joining(","));
    }
}
