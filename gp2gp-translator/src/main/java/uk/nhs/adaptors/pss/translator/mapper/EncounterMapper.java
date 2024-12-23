package uk.nhs.adaptors.pss.translator.mapper;

import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.buildIdentifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.DomainResource;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Encounter.EncounterLocationComponent;
import org.hl7.fhir.dstu3.model.Encounter.EncounterParticipantComponent;
import org.hl7.fhir.dstu3.model.Encounter.EncounterStatus;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.ListResource;
import org.hl7.fhir.dstu3.model.ListResource.ListEntryComponent;
import org.hl7.fhir.dstu3.model.Location;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.hl7.v3.CD;
import org.hl7.v3.CsNullFlavor;
import org.hl7.v3.II;
import org.hl7.v3.LinkableComponent;
import org.hl7.v3.RCMRMT030101UKComponent;
import org.hl7.v3.RCMRMT030101UKComponent02;
import org.hl7.v3.RCMRMT030101UKComponent3;
import org.hl7.v3.RCMRMT030101UKComponent4;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKEhrFolder;
import org.hl7.v3.RCMRMT030101UKLinkSet;
import org.hl7.v3.RCMRMT030101UKAuthor;
import org.hl7.v3.RCMRMT030101UKCompoundStatement;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKParticipant2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import lombok.RequiredArgsConstructor;
import uk.nhs.adaptors.pss.translator.service.ConfidentialityService;
import uk.nhs.adaptors.pss.translator.util.DateFormatUtil;
import uk.nhs.adaptors.pss.translator.util.DegradedCodeableConcepts;
import uk.nhs.adaptors.pss.translator.util.ResourceReferenceUtil;

import static uk.nhs.adaptors.common.util.CodeableConceptUtils.createCodeableConcept;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class EncounterMapper {

    private static final List<String> INVALID_CODES = List.of("196401000000100", "196391000000103");
    private static final String ENCOUNTER_META_PROFILE = "Encounter-1";
    private static final String PRACTITIONER_REFERENCE_PREFIX = "Practitioner/";
    private static final String LOCATION_REFERENCE = "Location/%s";
    private static final String PERFORMER_SYSTEM = "http://hl7.org/fhir/v3/ParticipationType";
    private static final String PERFORMER_CODE = "PPRF";
    private static final String PERFORMER_DISPLAY = "primary performer";
    private static final String RECORDER_SYSTEM = "https://fhir.nhs.uk/STU3/CodeSystem/GPConnect-ParticipantType-1";
    private static final String RECORDER_CODE = "REC";
    private static final String RECORDER_DISPLAY = "recorder";
    private static final String TOPIC_CLASS_CODE = "TOPIC";
    private static final String CATEGORY_CLASS_CODE = "CATEGORY";
    private static final String ENCOUNTER_KEY = "encounters";
    private static final String CONSULTATION_KEY = "consultations";
    private static final String TOPIC_KEY = "topics";
    private static final String CATEGORY_KEY = "categories";
    private static final String IDENTIFIER_EXTERNAL = "2.16.840.1.113883.2.1.4.5.3";
    private static final String RELATED_PROBLEM_URL = "https://fhir.hl7.org.uk/STU3/StructureDefinition/Extension-CareConnect-RelatedProblemHeader-1";
    private static final String RELATED_PROBLEM_TARGET_URL = "target";

    private final CodeableConceptMapper codeableConceptMapper;
    private final ConsultationListMapper consultationListMapper;
    private final ResourceReferenceUtil resourceReferenceUtil;
    private final ConfidentialityService confidentialityService;

    public Map<String, List<? extends DomainResource>> mapEncounters(
            RCMRMT030101UKEhrExtract ehrExtract,
            Patient patient,
            String practiceCode,
            List<Location> entryLocations
    ) {
        List<Encounter> encounters = new ArrayList<>();
        List<ListResource> consultations = new ArrayList<>();
        List<ListResource> topics = new ArrayList<>();
        List<ListResource> categories = new ArrayList<>();

        Map<String, List<? extends DomainResource>> map = new HashMap<>();

        List<RCMRMT030101UKEhrComposition> ehrCompositionList = getEncounterEhrCompositions(ehrExtract);

        ehrCompositionList.forEach(ehrComposition -> {
            var encounter = mapToEncounter(ehrComposition, patient, practiceCode, entryLocations);
            var consultation = consultationListMapper.mapToConsultation(ehrComposition, encounter);

            var topicCompoundStatementList = getTopicCompoundStatements(ehrComposition);
            if (CollectionUtils.isEmpty(topicCompoundStatementList)) {
                generateFlatConsultation(consultation, topics, ehrComposition);
            } else {
                generateStructuredConsultation(topicCompoundStatementList, ehrComposition, consultation, topics, categories);
            }

            encounters.add(encounter);
            consultations.add(consultation);
        });

        map.put(ENCOUNTER_KEY, encounters);
        map.put(CONSULTATION_KEY, consultations);
        map.put(TOPIC_KEY, topics);
        map.put(CATEGORY_KEY, categories);

        return map;
    }

    private void generateFlatConsultation(ListResource consultation, List<ListResource> topics,
        RCMRMT030101UKEhrComposition ehrComposition) {

        var topic = consultationListMapper.mapToTopic(consultation, null);

        List<Reference> entryReferences = new ArrayList<>();
        resourceReferenceUtil.extractChildReferencesFromEhrComposition(ehrComposition, entryReferences);
        entryReferences.forEach(reference -> addEntry(topic, reference));

        consultation.addEntry(new ListEntryComponent(new Reference(topic)));
        topics.add(topic);
    }

    private void generateStructuredConsultation(List<RCMRMT030101UKCompoundStatement> topicCompoundStatementList,
        RCMRMT030101UKEhrComposition ehrComposition, ListResource consultation, List<ListResource> topics,
        List<ListResource> categories) {

        topicCompoundStatementList.forEach(topicCompoundStatement -> {
            var topic = consultationListMapper.mapToTopic(consultation, topicCompoundStatement);
            consultation.addEntry(new ListEntryComponent(new Reference(topic)));

            generateCategoryLists(topicCompoundStatement, topic, categories);

            List<Extension> relatedProblems = getRelatedProblemsForStructuredConsultation(topicCompoundStatement, ehrComposition);
            relatedProblems.forEach(topic::addExtension);

            topics.add(topic);
        });
    }

    private List<Extension> getRelatedProblemsForStructuredConsultation(RCMRMT030101UKCompoundStatement topicCompoundStatement,
        RCMRMT030101UKEhrComposition ehrComposition) {

        var components = topicCompoundStatement.getComponent().stream()
            .map(RCMRMT030101UKComponent02::getCompoundStatement)
            .filter(Objects::nonNull)
            .flatMap(categoryCompoundStatement -> categoryCompoundStatement.getComponent().stream())
            .toList();

        return buildRelatedProblemExtensions(getLinkSetNamedStatementIds(components), getLinkSets(ehrComposition));

    }

    private Set<String> getLinkSetNamedStatementIds(List<? extends LinkableComponent> components) {

        List<String> observationStatementIds = components.stream()
            .map(LinkableComponent::getObservationStatement)
            .filter(Objects::nonNull)
            .map(observationStatement -> observationStatement.getId().getRoot())
            .toList();

        List<String> requestStatementIds = components.stream()
            .map(LinkableComponent::getRequestStatement)
            .filter(Objects::nonNull)
            .flatMap(requestStatement -> requestStatement.getId().stream())
            .map(II::getRoot)
            .filter(root -> !root.equals(IDENTIFIER_EXTERNAL))
            .toList();

        HashSet<String> statementIds = new HashSet<>();
        statementIds.addAll(observationStatementIds);
        statementIds.addAll(requestStatementIds);

        return statementIds;
    }

    private List<Extension> buildRelatedProblemExtensions(Set<String> statementIds, List<RCMRMT030101UKLinkSet> linkSets) {

        List<Extension> extensions = new ArrayList<>();

        for (var linkSet : linkSets) {
            var conditionNamed = linkSet.getConditionNamed();

            if (conditionNamed != null
                && statementIds.contains(conditionNamed.getNamedStatementRef().getId().getRoot())) {

                var extension = new Extension(RELATED_PROBLEM_URL);

                extension.addExtension(new Extension(RELATED_PROBLEM_TARGET_URL,
                    new Reference(new IdType(ResourceType.Condition.name(), linkSet.getId().getRoot()))));

                extensions.add(extension);
            }
        }

        return extensions;
    }

    private List<RCMRMT030101UKLinkSet> getLinkSets(RCMRMT030101UKEhrComposition ehrComposition) {

        return ehrComposition.getComponent()
            .stream()
            .map(RCMRMT030101UKComponent4::getLinkSet)
            .filter(Objects::nonNull)
            .toList();
    }

    private void generateCategoryLists(RCMRMT030101UKCompoundStatement topicCompoundStatement, ListResource topic,
        List<ListResource> categories) {
        var categoryCompoundStatements = getCategoryCompoundStatements(topicCompoundStatement);
        categoryCompoundStatements.forEach(categoryCompoundStatement -> {
            var category = consultationListMapper.mapToCategory(topic, categoryCompoundStatement);

            List<Reference> entryReferences = new ArrayList<>();
            resourceReferenceUtil.extractChildReferencesFromCompoundStatement(categoryCompoundStatement, entryReferences);
            entryReferences.forEach(reference -> addEntry(category, reference));

            topic.addEntry(new ListEntryComponent(new Reference(category)));
            categories.add(category);
        });
    }

    private List<RCMRMT030101UKCompoundStatement> getCategoryCompoundStatements(RCMRMT030101UKCompoundStatement
        topicCompoundStatement) {
        return topicCompoundStatement.getComponent()
            .stream()
            .map(RCMRMT030101UKComponent02::getCompoundStatement)
            .filter(this::hasValidCategoryCompoundStatement)
            .toList();
    }

    private boolean hasValidCategoryCompoundStatement(RCMRMT030101UKCompoundStatement compoundStatement) {
        return compoundStatement != null && CATEGORY_CLASS_CODE.equals(compoundStatement.getClassCode().getFirst());
    }

    private List<RCMRMT030101UKEhrComposition> getEncounterEhrCompositions(RCMRMT030101UKEhrExtract ehrExtract) {
        return ehrExtract
            .getComponent()
            .stream()
            .filter(RCMRMT030101UKComponent::hasEhrFolder)
            .map(RCMRMT030101UKComponent::getEhrFolder)
            .map(RCMRMT030101UKEhrFolder::getComponent)
            .flatMap(List::stream)
            .filter(RCMRMT030101UKComponent3::hasEhrComposition)
            .map(RCMRMT030101UKComponent3::getEhrComposition)
            .filter(this::isEncounterEhrComposition)
            .toList();
    }

    private boolean isEncounterEhrComposition(RCMRMT030101UKEhrComposition ehrComposition) {
        return !INVALID_CODES.contains(ehrComposition.getCode().getCode())
            && ehrComposition.getComponent().stream().noneMatch(this::hasSuppressedContent);
    }

    private boolean hasSuppressedContent(RCMRMT030101UKComponent4 component) {
        return component.getEhrEmpty() != null || component.getRegistrationStatement() != null;
    }

    private List<RCMRMT030101UKCompoundStatement> getTopicCompoundStatements(RCMRMT030101UKEhrComposition ehrComposition) {
        return ehrComposition
            .getComponent()
            .stream()
            .map(RCMRMT030101UKComponent4::getCompoundStatement)
            .filter(this::hasValidTopicCompoundStatement)
            .toList();
    }

    private boolean hasValidTopicCompoundStatement(RCMRMT030101UKCompoundStatement compoundStatement) {
        return compoundStatement != null && TOPIC_CLASS_CODE.equals(compoundStatement.getClassCode().getFirst());
    }

    private Encounter mapToEncounter(
        RCMRMT030101UKEhrComposition ehrComposition,
        Patient patient,
        String practiceCode,
        List<Location> entryLocations) {

        var id = ehrComposition.getId().getRoot();

        final Meta meta = confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            ENCOUNTER_META_PROFILE,
            ehrComposition.getConfidentialityCode()
        );

        var encounter = initializeEncounter(ehrComposition, patient, practiceCode, id, meta);
        setEncounterLocation(encounter, ehrComposition, entryLocations);

        return encounter;
    }

    private Encounter initializeEncounter(RCMRMT030101UKEhrComposition ehrComposition, Patient patient,
                                                   String practiceCode, String id, Meta meta) {
        var encounter = new Encounter();
        encounter
            .setParticipant(getParticipants(ehrComposition.getAuthor(), ehrComposition.getParticipant2()))
            .setStatus(EncounterStatus.FINISHED)
            .setSubject(new Reference(patient))
            .setType(getType(ehrComposition.getCode()))
            .setPeriod(getPeriod(ehrComposition))
            .addIdentifier(buildIdentifier(id, practiceCode))
            .setMeta(meta)
            .setId(id);
        return encounter;
    }

    private List<CodeableConcept> getType(CD code) {
        var codeableConcept = codeableConceptMapper.mapToCodeableConcept(code);

        DegradedCodeableConcepts.addDegradedEntryIfRequired(codeableConcept, DegradedCodeableConcepts.DEGRADED_OTHER);
        return List.of(codeableConcept);
    }

    private Period getPeriod(RCMRMT030101UKEhrComposition ehrComposition) {
        Period period = new Period();
        var effectiveTime = ehrComposition.getEffectiveTime();
        var availabilityTime = ehrComposition.getAvailabilityTime();

        if (effectiveTime.hasCenter()) {
            return period.setStartElement(DateFormatUtil.parseToDateTimeType(effectiveTime.getCenter().getValue()));
        } else if (effectiveTime.hasLow() && effectiveTime.hasHigh()) {
            return period.setStartElement(DateFormatUtil.parseToDateTimeType(effectiveTime.getLow().getValue()))
                .setEndElement(DateFormatUtil.parseToDateTimeType(effectiveTime.getHigh().getValue()));
        } else if (effectiveTime.hasLow() && !effectiveTime.hasHigh()) {
            return period.setStartElement(DateFormatUtil.parseToDateTimeType(effectiveTime.getLow().getValue()));
        } else if (!effectiveTime.hasLow() && effectiveTime.hasHigh()) {
            return period.setEndElement(DateFormatUtil.parseToDateTimeType(effectiveTime.getHigh().getValue()));
        } else if (effectiveTime.getCenter() != null
            && effectiveTime.getCenter().hasNullFlavor()
            && CsNullFlavor.UNK.value().equals(effectiveTime.getCenter().getNullFlavor().value())) {
            return null;
        } else if (availabilityTime.hasValue()) {
            return period.setStartElement(DateFormatUtil.parseToDateTimeType(availabilityTime.getValue()));
        }

        return null;
    }

    private EncounterParticipantComponent getRecorder(RCMRMT030101UKAuthor author) {
        var recorder = new EncounterParticipantComponent();

        return recorder
            .addType(createCodeableConcept(RECORDER_CODE, RECORDER_SYSTEM, RECORDER_DISPLAY))
            .setIndividual(new Reference(PRACTITIONER_REFERENCE_PREFIX + author.getAgentRef().getId().getRoot()));
    }

    private boolean isNonNullParticipant2(RCMRMT030101UKParticipant2 participant2) {
        return participant2.getNullFlavor() == null;
    }

    private EncounterParticipantComponent getPerformer(RCMRMT030101UKParticipant2 participant2) {
        var performer = new EncounterParticipantComponent();

        return performer
            .addType(createCodeableConcept(PERFORMER_CODE, PERFORMER_SYSTEM, PERFORMER_DISPLAY))
            .setIndividual(new Reference(PRACTITIONER_REFERENCE_PREFIX + participant2.getAgentRef().getId().getRoot()));
    }

    private List<EncounterParticipantComponent> getParticipants(RCMRMT030101UKAuthor author,
                                                                List<RCMRMT030101UKParticipant2> participant2List) {
        List<EncounterParticipantComponent> participants = new ArrayList<>();

        if (author.getNullFlavor() == null) {
            participants.add(getRecorder(author));
        }

        participant2List
            .stream()
            .filter(this::isNonNullParticipant2)
            .findFirst()
            .ifPresent(participant2 -> participants.add(getPerformer(participant2)));

        return participants;
    }

    private void setEncounterLocation(Encounter encounter, RCMRMT030101UKEhrComposition ehrComposition, List<Location> entryLocations) {
        if (ehrComposition.getLocation() != null) {

            var locationName = ehrComposition.getLocation().getLocatedEntity().getLocatedPlace().getName().toLowerCase();

            for (var entryLocation : entryLocations) {
                if (entryLocation.getName().toLowerCase().equals(locationName)) {
                    var id = entryLocation.getId();
                    var location = new EncounterLocationComponent();
                    location.setLocation(new Reference(LOCATION_REFERENCE.formatted(id)));
                    encounter.setLocation(List.of(location));
                }
            }
        }
    }

    private void addEntry(ListResource list, Reference reference) {
        list.addEntry(new ListEntryComponent(reference));
    }
}
