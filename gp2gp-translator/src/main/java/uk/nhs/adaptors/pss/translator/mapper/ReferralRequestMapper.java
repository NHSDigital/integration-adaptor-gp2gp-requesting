package uk.nhs.adaptors.pss.translator.mapper;

import static org.hl7.fhir.dstu3.model.ReferralRequest.ReferralPriority;
import static org.hl7.fhir.dstu3.model.ReferralRequest.ReferralPriorityEnumFactory;

import static uk.nhs.adaptors.pss.translator.util.CompoundStatementResourceExtractors.extractAllRequestStatements;
import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.buildIdentifier;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.Annotation;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.ReferralRequest;
import org.hl7.fhir.dstu3.model.ReferralRequest.ReferralCategory;
import org.hl7.fhir.dstu3.model.ReferralRequest.ReferralRequestStatus;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.hl7.v3.CD;
import org.hl7.v3.CR;
import org.hl7.v3.CV;
import org.hl7.v3.IVLTS;
import org.hl7.v3.RCMRMT030101UKComponent;
import org.hl7.v3.RCMRMT030101UKComponent3;
import org.hl7.v3.RCMRMT030101UKComponent4;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKLinkSet;
import org.hl7.v3.RCMRMT030101UKPart;
import org.hl7.v3.RCMRMT030101UKRequestStatement;
import org.hl7.v3.RCMRMT030101UKResponsibleParty3;
import org.hl7.v3.TS;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import uk.nhs.adaptors.pss.translator.service.ConfidentialityService;
import uk.nhs.adaptors.pss.translator.util.DateFormatUtil;
import uk.nhs.adaptors.pss.translator.util.DegradedCodeableConcepts;
import uk.nhs.adaptors.pss.translator.util.ParticipantReferenceUtil;
import uk.nhs.adaptors.pss.translator.util.ResourceFilterUtil;

@Service
@AllArgsConstructor
public class ReferralRequestMapper extends AbstractMapper<ReferralRequest> {

    private static final String META_PROFILE = "ReferralRequest-1";
    private static final String PRIORITY_PREFIX = "Priority: ";
    private static final String ACTION_DATE_PREFIX = "Action Date: ";
    private static final String PRACTITIONER_REFERENCE = "Practitioner/%s";
    private static final String ORGANIZATION_REFERENCE = "Organization/%s";
    private static final String RESP_PARTY_TYPE_CODE = "RESP";
    private static final String SELF_REFERRAL = "SelfReferral";
    private static final Map<String, String> PRIORITY_CODES = Map.of(
            "394848005", "routine",
            "394849002", "urgent",
            "88694003", "asap"
    );
    private static final String SNOMED_CODE_SYSTEM = "2.16.840.1.113883.2.1.3.2.4.15";

    private static final BinaryOperator<List<Reference>> DOCUMENT_REFERENCE_MERGE_FUNCTION = (existing, added) -> {
        added.forEach(addedReference -> {
            if (existing.stream().noneMatch(
                existingReference -> addedReference.getReference().equals(existingReference.getReference())
            )) {
                existing.add(addedReference);
            }
        });

        return existing;
    };

    private CodeableConceptMapper codeableConceptMapper;
    private ConfidentialityService confidentialityService;

    public List<ReferralRequest> mapResources(RCMRMT030101UKEhrExtract ehrExtract,
                                              Patient patient,
                                              List<Encounter> encounters,
                                              String practiceCode) {

        var referralRequestToDocumentReferences = extractReferralRequestIdToDocumentReferences(ehrExtract);

        return mapEhrExtractToFhirResource(ehrExtract, (extract, composition, component) ->
            extractAllRequestStatements(component)
                .filter(Objects::nonNull)
                .filter(this::isNotSelfReferral)
                .map(requestStatement -> {
                    var documentReferences = referralRequestToDocumentReferences.getOrDefault(
                        requestStatement.getId().getFirst().getRoot(),
                        List.of()
                    );
                    return mapToReferralRequest(
                        ehrExtract,
                        composition,
                        requestStatement,
                        patient,
                        encounters,
                        documentReferences,
                        practiceCode
                    );
                }))
            .toList();
    }

    public ReferralRequest mapToReferralRequest(RCMRMT030101UKEhrExtract ehrExtract,
                                                RCMRMT030101UKEhrComposition ehrComposition,
                                                RCMRMT030101UKRequestStatement requestStatement,
                                                Patient patient,
                                                List<Encounter> encounters,
                                                List<Reference> documentReferences,
                                                String practiceCode
    ) {

        var referralRequest = initializeReferralRequest(ehrComposition, requestStatement, patient, practiceCode);

        setReferralRequestContext(referralRequest, ehrComposition, encounters);
        setReferralRequestRecipient(ehrExtract, requestStatement, referralRequest);
        setReferralRequestReasonCode(referralRequest, requestStatement.getCode());

        if (!documentReferences.isEmpty()) {
            referralRequest.setSupportingInfo(documentReferences);
        }

        return referralRequest;
    }

    private ReferralRequest initializeReferralRequest(RCMRMT030101UKEhrComposition ehrComposition,
                                                               RCMRMT030101UKRequestStatement requestStatement,
                                                               Patient patient,
                                                               String practiceCode) {
        var referralRequest = new ReferralRequest();
        var id = requestStatement.getId().getFirst().getRoot();
        var identifier = buildIdentifier(id, practiceCode);

        var agent = ParticipantReferenceUtil.getParticipantReference(requestStatement.getParticipant(), ehrComposition);

        var authoredOn = getAuthoredOn(requestStatement.getAvailabilityTime());
        var referralPriority = getReferralPriority(requestStatement);

        final Meta meta = confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            requestStatement.getConfidentialityCode(),
            ehrComposition.getConfidentialityCode()
        );

        referralRequest.setId(id)
                       .setMeta(meta);
        referralRequest.getIdentifier().add(identifier);
        referralRequest.setStatus(ReferralRequestStatus.UNKNOWN)
                        .setIntent(ReferralCategory.ORDER)
                        .setAuthoredOnElement(authoredOn)
                        .setNote(getNotes(requestStatement))
                        .setSubject(new Reference(patient))
                        .setPriority(referralPriority)
                        .getRequester().setAgent(agent);

        return referralRequest;
    }

    private boolean isAgentOrganization(RCMRMT030101UKEhrExtract ehrExtract,
                                        RCMRMT030101UKRequestStatement requestStatement,
                                        String agentRefRoot) {

        if (requestStatement.getResponsibleParty() == null) {
            return false;
        }

        return ehrExtract.getComponent()
            .stream()
            .map(component -> component.getEhrFolder().getResponsibleParty())
            .filter(Objects::nonNull)
            .flatMap(respParty -> respParty.getAgentDirectory().getPart().stream())
            .anyMatch(part -> isMatchingAgent(part, agentRefRoot));
    }

    private boolean isMatchingAgent(RCMRMT030101UKPart part, String requestAgentRoot) {
        return part.getAgent().getId().getFirst().getRoot().equals(requestAgentRoot)
               && part.getAgent().getAgentPerson() == null;
    }

    private void setReferralRequestContext(ReferralRequest referralRequest,
                                           RCMRMT030101UKEhrComposition ehrComposition,
                                           List<Encounter> encounters) {

        encounters
                .stream()
                .filter(encounter -> encounter.getId().equals(ehrComposition.getId().getRoot()))
                .findFirst()
                .map(Reference::new)
                .ifPresent(referralRequest::setContext);
    }
    private void setReferralRequestRecipient(RCMRMT030101UKEhrExtract ehrExtract,
                                             RCMRMT030101UKRequestStatement requestStatement,
                                             ReferralRequest referralRequest) {

        if (!hasIdValue(requestStatement.getResponsibleParty())) {
            return;
        }

        var agentRefRoot = requestStatement.getResponsibleParty().getAgentRef().getId().getRoot();

        var recipient = isAgentOrganization(ehrExtract, requestStatement, agentRefRoot)
                        ? new Reference(ORGANIZATION_REFERENCE.formatted(agentRefRoot))
                        : new Reference(PRACTITIONER_REFERENCE.formatted(agentRefRoot));

        referralRequest.getRecipient().add(recipient);
    }

    private void setReferralRequestReasonCode(ReferralRequest referralRequest, CD code) {
        if (code == null) {
            return;
        }

        var reasonCode = codeableConceptMapper.mapToCodeableConcept(code);
        DegradedCodeableConcepts.addDegradedEntryIfRequired(reasonCode, DegradedCodeableConcepts.DEGRADED_REFERRAL);
        referralRequest.getReasonCode().add(reasonCode);
    }

    private DateTimeType getAuthoredOn(TS availabilityTime) {
        if (availabilityTime != null && availabilityTime.hasValue()) {
            return DateFormatUtil.parseToDateTimeType(availabilityTime.getValue());
        }
        return null;
    }

    private List<Annotation> getNotes(RCMRMT030101UKRequestStatement requestStatement) {
        var priority = getPriorityText(requestStatement.getPriorityCode());
        var actionDate = getActionDateText(requestStatement.getEffectiveTime());
        var text = requestStatement.getText();

        var notes = new ArrayList<Annotation>();

        if (StringUtils.isNotEmpty(priority)) {
            notes.add(new Annotation().setText(priority));
        }

        if (StringUtils.isNotEmpty(actionDate)) {
            notes.add(new Annotation().setText(actionDate));
        }

        if (StringUtils.isNotEmpty(text)) {
            notes.add(new Annotation().setText(text));
        }

        return notes;
    }

    private String getPriorityText(CV priorityCode) {
        if (priorityCode != null) {
            if (StringUtils.isNotEmpty(priorityCode.getOriginalText())) {
                return PRIORITY_PREFIX + priorityCode.getOriginalText();
            } else if (StringUtils.isNotEmpty(priorityCode.getDisplayName())) {
                return PRIORITY_PREFIX + priorityCode.getDisplayName();
            }
        }

        return StringUtils.EMPTY;
    }

    private String getActionDateText(IVLTS effectiveTime) {
        if (!hasEffectiveTimeValue(effectiveTime)) {
            return StringUtils.EMPTY;
        }

        var effectiveTimeValue = DateFormatUtil.parseToDateTimeType(effectiveTime.getCenter().getValue());
        return ACTION_DATE_PREFIX + effectiveTimeValue.asStringValue();
    }

    private ReferralPriority getReferralPriority(RCMRMT030101UKRequestStatement requestStatement) {

        var priorityCode = requestStatement.getPriorityCode();
        if (snomedCodeNotPresent(priorityCode)) {
            return null;
        }

        var hl7Code = priorityCode.getCode();
        if (PRIORITY_CODES.containsKey(hl7Code)) {
            return new ReferralPriorityEnumFactory().fromCode(PRIORITY_CODES.get(hl7Code));
        }

        return null;
    }

    private boolean snomedCodeNotPresent(CV codeElement) {
        return codeElement == null || !codeElement.hasCode() || !codeElement.hasCodeSystem()
            || !SNOMED_CODE_SYSTEM.equals(codeElement.getCodeSystem());
    }

    private boolean hasIdValue(RCMRMT030101UKResponsibleParty3 responsibleParty) {
        return responsibleParty != null
                && responsibleParty.getTypeCode().stream().anyMatch(RESP_PARTY_TYPE_CODE::equals)
                && responsibleParty.getAgentRef() != null
                && responsibleParty.getAgentRef().getId() != null;
    }

    private boolean hasEffectiveTimeValue(IVLTS effectiveTime) {
        return effectiveTime != null
                && effectiveTime.getCenter() != null
                && effectiveTime.getCenter().getValue() != null;
    }

    private boolean isNotSelfReferral(RCMRMT030101UKRequestStatement requestStatement) {

        for (CR qualifier : requestStatement.getCode().getQualifier()) {
            if (qualifier.getValue().getCode().equals(SELF_REFERRAL)) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, List<Reference>> extractReferralRequestIdToDocumentReferences(
        RCMRMT030101UKEhrExtract ehrExtract) {

        return ehrExtract.getComponent()
            .stream()
            .map(RCMRMT030101UKComponent::getEhrFolder)
            .flatMap(ehrFolder -> ehrFolder.getComponent().stream())
            .map(RCMRMT030101UKComponent3::getEhrComposition)
            .flatMap(ehrComposition -> ehrComposition.getComponent().stream())
            .map(RCMRMT030101UKComponent4::getLinkSet)
            .filter(Objects::nonNull)
            .filter(linkSet -> ResourceFilterUtil.isReferralRequestToExternalDocumentLinkSet(ehrExtract, linkSet))
            .map(ReferralRequestMapper::buildReferralRequestToDocumentReferenceSimpleEntry)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, DOCUMENT_REFERENCE_MERGE_FUNCTION));
    }

    private static AbstractMap.SimpleEntry<String, List<Reference>> buildReferralRequestToDocumentReferenceSimpleEntry(
        RCMRMT030101UKLinkSet linkSet
    ) {
        var referralRequestId = linkSet.getConditionNamed().getNamedStatementRef().getId().getRoot();
        var documentReferences = linkSet.getComponent().stream()
            .map(component -> component.getStatementRef().getId().getRoot())
            .map(id -> new Reference(new IdType(ResourceType.DocumentReference.name(), id)))
            .collect(Collectors.toCollection(ArrayList::new));

        return new AbstractMap.SimpleEntry<>(referralRequestId, documentReferences);
    }
}