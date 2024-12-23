package uk.nhs.adaptors.pss.translator.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Property;
import org.hl7.fhir.dstu3.model.Resource;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.adaptors.common.exception.FhirValidationException;
import uk.nhs.adaptors.common.util.fhir.FhirParser;
import uk.nhs.adaptors.pss.translator.exception.SdsRetrievalException;
import uk.nhs.adaptors.pss.translator.sds.SdsRequestBuilder;

@Slf4j
@Service
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class SDSService {

    private static final String EXTENSION_KEY_VALUE = "extension";
    private static final String IDENTIFIER_KEY_VALUE = "identifier";
    private static final String PERSIST_DURATION_URL = "nhsMHSPersistDuration";
    private static final String NHS_MHS_PARTY_KEY_URL =  "https://fhir.nhs.uk/Id/nhsMhsPartyKey";

    private final SdsRequestBuilder requestBuilder;
    private final SdsClientService sdsClientService;
    private final FhirParser fhirParser;

    public Duration getPersistDurationFor(String messageType, String odsCode, String conversationId) throws SdsRetrievalException {
        String partyKeyResponseFromSds = getPartyKeyResponseFromSds(messageType, odsCode, conversationId);
        String partyKey = parseNhsMhsPartyKey(partyKeyResponseFromSds);
        String persistDurationResponseFromSds = getPersistDurationResponseFromSds(messageType, partyKey, conversationId);
        Duration duration = parsePersistDuration(persistDurationResponseFromSds);

        LOGGER.debug("Retrieved persist duration of [{}] for odscode [{}] and  messageType [{}]", duration, odsCode, messageType);
        return duration;
    }

    public String parseNhsMhsPartyKey(String sdsResponse) throws SdsRetrievalException {

        Property identifierChildren = getResourceSubelement(sdsResponse, IDENTIFIER_KEY_VALUE);
        return identifierChildren.getValues()
                                 .stream()
                                 .map(Identifier.class::cast)
                                 .filter(identifierChild -> NHS_MHS_PARTY_KEY_URL.equals(identifierChild.getSystem()))
                                 .findFirst()
                                 .map(Identifier::getValue).get();
    }

    private String getPartyKeyResponseFromSds(String messageType, String odsCode, String conversationId) {

        var request = requestBuilder.buildDeviceGetRequest(messageType, odsCode, conversationId);

        try {
            LOGGER.debug("Sending party key request to SDS");
            return sdsClientService.send(request);
        } catch (WebClientResponseException e) {
            LOGGER.error("Received an ERROR response from SDS: [{}]", e.getMessage());
            throw new SdsRetrievalException(String.format("Error getting messageType [%s] info from SDS", messageType));
        }

    }

    private String getPersistDurationResponseFromSds(
            String messageType,
            String nhsMhsPartyKey,
            String conversationId) throws SdsRetrievalException {

        var request = requestBuilder.buildEndpointGetRequest(messageType, nhsMhsPartyKey, conversationId);

        try {
            LOGGER.debug("Sending persist duration request to SDS");
            return sdsClientService.send(request);
        } catch (WebClientResponseException e) {
            LOGGER.error("Received an ERROR response from SDS: [{}]", e.getMessage());
            throw new SdsRetrievalException(String.format("Error getting messageType [%s] info from SDS", messageType));
        }
    }

    private Duration parsePersistDuration(String sdsResponse) throws SdsRetrievalException {

        Optional<Extension> extensions = getExtensions(sdsResponse);

        Optional<Extension> persistDuration = extensions
            .orElseThrow(() -> new SdsRetrievalException("Error parsing persist duration extension"))
            .getExtensionsByUrl(PERSIST_DURATION_URL)
            .stream().findFirst();

        String isoDuration = persistDuration
            .orElseThrow(() -> new SdsRetrievalException("Error parsing persist duration value"))
            .getValue()
            .toString();

        return Duration.parse(isoDuration);
    }

    @NotNull
    private Optional<Extension> getExtensions(String sdsResponse) {
        Property matchingChildren = getResourceSubelement(sdsResponse, EXTENSION_KEY_VALUE);
        return matchingChildren.getValues().stream().map(Extension.class::cast).findFirst();
    }

    private Property getResourceSubelement(String sdsResponse, String resourceSubelement) {
        Bundle bundle;

        try {
            bundle = fhirParser.parseResource(sdsResponse, Bundle.class);
        } catch (FhirValidationException e) {
            throw new SdsRetrievalException(e.getMessage());
        }

        List<Bundle.BundleEntryComponent> entries = bundle.getEntry();

        if (entries.isEmpty()) {
            throw new SdsRetrievalException("sds response doesn't contain any results");
        }

        Resource resource = entries.getFirst().getResource();
        return resource.getChildByName(resourceSubelement);
    }
}
