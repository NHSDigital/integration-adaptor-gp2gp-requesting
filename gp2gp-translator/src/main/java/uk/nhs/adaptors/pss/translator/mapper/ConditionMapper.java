package uk.nhs.adaptors.pss.translator.mapper;

import static org.hl7.fhir.dstu3.model.Condition.ConditionClinicalStatus.ACTIVE;
import static org.hl7.fhir.dstu3.model.Condition.ConditionClinicalStatus.INACTIVE;

import static uk.nhs.adaptors.pss.translator.mapper.medication.MedicationMapperUtils.getMedicationStatements;
import static uk.nhs.adaptors.pss.translator.util.CompoundStatementResourceExtractors.extractAllLinkSets;
import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.buildIdentifier;
import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.buildReferenceExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.Annotation;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.CodeType;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Condition;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.hl7.fhir.dstu3.model.StringType;
import org.hl7.v3.CD;
import org.hl7.v3.IVLTS;
import org.hl7.v3.RCMRMT030101UKAnnotation;
import org.hl7.v3.RCMRMT030101UKComponent;
import org.hl7.v3.RCMRMT030101UKComponent2;
import org.hl7.v3.RCMRMT030101UKComponent3;
import org.hl7.v3.RCMRMT030101UKComponent4;
import org.hl7.v3.RCMRMT030101UKComponent6;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKEhrFolder;
import org.hl7.v3.RCMRMT030101UKLinkSet;
import org.hl7.v3.RCMRMT030101UKMedicationStatement;
import org.hl7.v3.RCMRMT030101UKObservationStatement;
import org.hl7.v3.RCMRMT030101UKPertinentInformation02;
import org.hl7.v3.RCMRMT030101UKStatementRef;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.adaptors.pss.translator.service.ConfidentialityService;
import uk.nhs.adaptors.pss.translator.util.CompoundStatementResourceExtractors;
import uk.nhs.adaptors.pss.translator.util.DegradedCodeableConcepts;
import uk.nhs.adaptors.pss.translator.util.ResourceFilterUtil;
import static uk.nhs.adaptors.common.util.CodeableConceptUtils.createCodeableConcept;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Slf4j
public class ConditionMapper extends AbstractMapper<Condition> {

    private static final String META_PROFILE = "ProblemHeader-Condition-1";
    public static final String EXTENSION_CARE_CONNECT_URL = "https://fhir.hl7.org.uk/STU3/StructureDefinition/Extension-CareConnect";
    private static final String RELATED_CLINICAL_CONTENT_URL = EXTENSION_CARE_CONNECT_URL + "-RelatedClinicalContent-1";
    private static final String PROBLEM_HEADER_URL = EXTENSION_CARE_CONNECT_URL + "-RelatedProblemHeader-1";
    private static final String ACTUAL_PROBLEM_URL = EXTENSION_CARE_CONNECT_URL + "-ActualProblem-1";
    private static final String PROBLEM_SIGNIFICANCE_URL = EXTENSION_CARE_CONNECT_URL + "-ProblemSignificance-1";
    private static final String MAJOR_CODE = "386134007";
    private static final String CLINICAL_STATUS_ACTIVE_CODE = "394774009";
    private static final String CLINICAL_STATUS_INACTIVE_CODE = "394775005";
    private static final String DEFAULT_CLINICAL_STATUS = "Defaulted status to active : Unknown status at source";
    private static final String DEFAULT_ANNOTATION = "Unspecified Significance: Defaulted to Minor";
    private static final String MAJOR_CODE_NAME = "major";
    private static final String MINOR_CODE_NAME = "minor";
    private static final String HIERARCHY_TYPE_PARENT = "parent";
    private static final String HIERARCHY_TYPE_CHILD = "child";
    private static final String MEDICATION_MOOD_ORDER = "ORD";
    private static final String MEDICATION_MOOD_INTENTION = "INT";
    public static final String CARE_CONNECT_URL = "https://fhir.hl7.org.uk/STU3/CodeSystem/CareConnect-ConditionCategory-1";
    public static final String PROBLEM_LIST_ITEM_CODE = "problem-list-item";
    public static final String PROBLEM_LIST_ITEM_DISPLAY = "Problem List Item";

    private final CodeableConceptMapper codeableConceptMapper;
    private final DateTimeMapper dateTimeMapper;
    private final ConfidentialityService confidentialityService;

    public List<Condition> mapResources(RCMRMT030101UKEhrExtract ehrExtract, Patient patient, List<Encounter> encounters,
                                        String practiceCode) {

        return mapEhrExtractToFhirResource(ehrExtract, (extract, composition, component) ->
                extractAllLinkSets(component)
                    .filter(Objects::nonNull)
                    .filter(linkSet -> !ResourceFilterUtil.isReferralRequestToExternalDocumentLinkSet(ehrExtract, linkSet))
                    .map(linkSet -> getCondition(
                        patient,
                        encounters,
                        composition,
                        linkSet,
                        practiceCode
                    )))
            .toList();
    }

    private Condition getCondition(Patient patient, List<Encounter> encounters, RCMRMT030101UKEhrComposition composition,
                                   RCMRMT030101UKLinkSet linkSet, String practiceCode) {

        String id = linkSet.getId().getRoot();

        final Meta meta = confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            linkSet.getConfidentialityCode(),
            composition.getConfidentialityCode()
        );

        Condition condition = initializeCondition(id, practiceCode, meta);

        buildClinicalStatus(linkSet.getCode()).ifPresentOrElse(
            condition::setClinicalStatus,
            () -> {
                condition.setClinicalStatus(ACTIVE);
                condition.addNote(new Annotation(new StringType(DEFAULT_CLINICAL_STATUS)));
            });

        condition.setSubject(new Reference(patient))
                 .addExtension(buildProblemSignificance(linkSet.getCode()));

        generateAnnotationToMinor(linkSet.getCode()).ifPresent(condition::addNote);

        buildContext(composition, encounters).ifPresent(condition::setContext);

        buildOnsetDateTimeType(linkSet).ifPresent(condition::setOnset);
        buildAbatementDateTimeType(linkSet.getEffectiveTime()).ifPresent(condition::setAbatement);

        buildAssertedDateTimeType(composition).ifPresent(condition::setAssertedDateElement);

        composition.getParticipant2()
            .stream()
            .findFirst()
            .ifPresent(participant2 -> condition.setAsserter(
                new Reference(new IdType(ResourceType.Practitioner.name(), participant2.getAgentRef().getId().getRoot())))
            );

        return condition;
    }

    private Condition initializeCondition(String id, String practiceCode, Meta meta) {
        return (Condition) new Condition()
            .addIdentifier(buildIdentifier(id, practiceCode))
            .addCategory(generateCategory())
            .setId(id)
            .setMeta(meta);
    }

    public void addHierarchyReferencesToConditions(List<Condition> conditions, RCMRMT030101UKEhrExtract ehrExtract) {
        var allLinkSets = getCompositionsContainingLinkSets(ehrExtract).stream()
                .flatMap(ehrComposition -> ehrComposition.getComponent().stream())
                .map(RCMRMT030101UKComponent4::getLinkSet)
                .filter(Objects::nonNull)
                .toList();

        allLinkSets.forEach(linkSet -> {
            var condition = conditions.stream()
                    .filter(condition1 -> linkSet.getId().getRoot().equals(condition1.getId()))
                    .findFirst();

            condition.ifPresent(value -> linkSet.getComponent()
                    .stream()
                    .map(RCMRMT030101UKComponent6::getStatementRef)
                    .forEach(ref -> {
                        var childLinkSet = allLinkSets
                                .stream()
                                .filter(ls -> ls.getId().getRoot().equals(ref.getId().getRoot()))
                                .findFirst();

                        if (childLinkSet.isPresent()) {
                            value.addExtension(
                                    buildConditionReferenceExtension(ref.getId().getRoot(), HIERARCHY_TYPE_CHILD));

                            conditions.stream()
                                    .filter(condition2 -> condition2.getId().equals(ref.getId().getRoot()))
                                    .findFirst()
                                    .ifPresent(parentCondition ->
                                            parentCondition.addExtension(buildConditionReferenceExtension(
                                                    value.getId(), HIERARCHY_TYPE_PARENT)));
                        }
                    }));
        });
    }

    public void addReferences(Bundle bundle, List<Condition> conditions, RCMRMT030101UKEhrExtract ehrExtract) {
        getCompositionsContainingLinkSets(ehrExtract).stream()
            .flatMap(ehrComposition -> ehrComposition.getComponent().stream())
            .map(RCMRMT030101UKComponent4::getLinkSet)
            .filter(Objects::nonNull)
            .forEach(linkSet -> conditions.stream()
                    .filter(condition1 -> linkSet.getId().getRoot().equals(condition1.getId()))
                    .findFirst().ifPresent(condition -> {
                        var namedStatementRef = linkSet.getConditionNamed().getNamedStatementRef();
                        buildActualProblem(bundle, namedStatementRef).ifPresent(condition::addExtension);

                        var referencedObservationStatement = getObservationStatementById(
                                ehrExtract,
                                namedStatementRef.getId().getRoot()
                        );

                        buildNotes(
                                referencedObservationStatement,
                                linkSet
                        ).forEach(condition::addNote);

                        referencedObservationStatement.ifPresent(
                                observationStatement -> {
                                    condition.setCode(codeableConceptMapper.mapToCodeableConcept(observationStatement.getCode()));
                                    DegradedCodeableConcepts.addDegradedEntryIfRequired(
                                        condition.getCode(),
                                        DegradedCodeableConcepts.DEGRADED_OTHER);
                                });

                        var statementRefs = linkSet.getComponent()
                                .stream()
                                .map(RCMRMT030101UKComponent6::getStatementRef)
                                .toList();

                        buildRelatedClinicalContent(bundle, statementRefs, ehrExtract).forEach(condition::addExtension);
                    }));
    }

    private Optional<DateTimeType> buildOnsetDateTimeType(RCMRMT030101UKLinkSet linkSet) {

        if (linkSet.getEffectiveTime() != null) {
            IVLTS effectiveTime = linkSet.getEffectiveTime();

            if (effectiveTime.hasLow()) {
                return Optional.of(dateTimeMapper.mapDateTime(effectiveTime.getLow().getValue()));
            } else if (effectiveTime.getLow() != null && effectiveTime.getLow().hasNullFlavor()) {
                return Optional.empty();
            } else if (effectiveTime.hasCenter()) {
                return Optional.of(dateTimeMapper.mapDateTime(effectiveTime.getCenter().getValue()));
            } else if (effectiveTime.getCenter() != null && effectiveTime.getCenter().hasNullFlavor()) {
                return Optional.empty();
            }
        }

        if (linkSet.getAvailabilityTime() != null && linkSet.getAvailabilityTime().hasValue()) {
            return Optional.of(dateTimeMapper.mapDateTime(linkSet.getAvailabilityTime().getValue()));
        }
        return Optional.empty();
    }

    private Optional<DateTimeType> buildAbatementDateTimeType(IVLTS abatementDateTime) {
        if (abatementDateTime != null && abatementDateTime.hasHigh()) {
            return Optional.of(dateTimeMapper.mapDateTime(abatementDateTime.getHigh().getValue()));
        }
        return Optional.empty();
    }

    private Optional<DateTimeType> buildAssertedDateTimeType(RCMRMT030101UKEhrComposition ehrComposition) {

        if (ehrComposition.hasAuthor() && ehrComposition.getAuthor().hasTime()
            && ehrComposition.getAuthor().getTime().hasValue()
            && !ehrComposition.getAuthor().getTime().hasNullFlavor()) {
            return Optional.of(dateTimeMapper.mapDateTime(ehrComposition.getAuthor().getTime().getValue()));
        }
        return Optional.empty();
    }

    private Optional<Reference> buildContext(RCMRMT030101UKEhrComposition composition, List<Encounter> encounters) {

        return encounters
            .stream()
            .filter(encounter -> encounter.getId().equals(composition.getId().getRoot()))
            .findFirst()
            .map(Reference::new);
    }

    private Optional<Extension> buildActualProblem(Bundle bundle, RCMRMT030101UKStatementRef namedStatementRef) {
        if (namedStatementRef != null) {
            var resourceOpt = bundle.getEntry()
                .stream()
                .map(BundleEntryComponent::getResource)
                .filter(resource -> namedStatementRef.getId().getRoot().equals(resource.getId()))
                .findFirst();

            Reference reference = resourceOpt.map(Reference::new)
                .orElseGet(() -> new Reference(namedStatementRef.getId().getRoot()));

            return Optional.of(buildReferenceExtension(ACTUAL_PROBLEM_URL, reference));
        }
        return Optional.empty();
    }

    private boolean hasMajorCode(CD linkSetCode) {
        return hasCode(linkSetCode) && MAJOR_CODE.equals(linkSetCode.getQualifier().getFirst().getName().getCode());
    }

    private Extension buildConditionReferenceExtension(String id, String heirarchyType) {
        Extension extension = new Extension()
                .setUrl(PROBLEM_HEADER_URL);

        extension.addExtension(new Extension()
                .setUrl("type")
                .setValue(new CodeType(heirarchyType)));
        extension.addExtension(new Extension()
                .setUrl("target")
                .setValue(new Reference("Condition/" + id)));

        return extension;
    }

    private Extension buildProblemSignificance(CD linkSetCode) {
        Extension extension = new Extension()
            .setUrl(PROBLEM_SIGNIFICANCE_URL);

        return hasMajorCode(linkSetCode)
            ? extension.setValue(new CodeType(MAJOR_CODE_NAME)) : extension.setValue(new CodeType(MINOR_CODE_NAME));
    }

    private Optional<Annotation> generateAnnotationToMinor(CD linkSetCode) {
        if (!hasMajorCode(linkSetCode)) {
            return Optional.of(new Annotation().setText(DEFAULT_ANNOTATION));
        }
        return Optional.empty();
    }

    private boolean hasCode(CD linkSetCode) {
        var crOpt = linkSetCode.getQualifier().stream().findFirst();
        return crOpt.isPresent() && crOpt.get().getName() != null && crOpt.get().getName().getCode() != null;
    }

    private Optional<Condition.ConditionClinicalStatus> buildClinicalStatus(CD linksetCode) {
        if (CLINICAL_STATUS_ACTIVE_CODE.equals(linksetCode.getCode())) {
            return Optional.of(ACTIVE);
        } else if (CLINICAL_STATUS_INACTIVE_CODE.equals(linksetCode.getCode())) {
            return Optional.of(INACTIVE);
        }
        return Optional.empty();
    }

    private CodeableConcept generateCategory() {
        return createCodeableConcept(PROBLEM_LIST_ITEM_CODE,
                                     CARE_CONNECT_URL,
                                     PROBLEM_LIST_ITEM_DISPLAY);
    }

    private List<Extension> buildRelatedClinicalContent(Bundle bundle, List<RCMRMT030101UKStatementRef> relatedClinicalStatementReferences,
                                                        RCMRMT030101UKEhrExtract ehrExtract) {

        // Filter for bundle entries where entry ID exists in both streams
        var bundleIds = bundle.getEntry()
            .stream()
            .map(entry -> entry.getResource().getId()).toArray();

        var referenceIds = relatedClinicalStatementReferences
            .stream()
            .filter(entry -> Arrays.asList(bundleIds).contains(entry.getId().getRoot()))
            .map(entry -> entry.getId().getRoot()).collect(Collectors.toList());

        referenceIds.addAll(getMedicationRequestIds(ehrExtract, relatedClinicalStatementReferences));

        var clinicalReferences = bundle.getEntry()
            .stream()
            .filter(entry -> referenceIds.contains(entry.getResource().getId()))
            .toList();

        // Parse bundle entries into condition reference extensions and return
        return clinicalReferences
            .stream()
            .map(BundleEntryComponent::getResource)
            .map(resource -> {
                var reference = new Reference(resource);
                reference.setReferenceElement(new StringType(resource.getId()));
                return buildReferenceExtension(RELATED_CLINICAL_CONTENT_URL, reference);
            }).toList();

    }

    private List<String> getMedicationRequestIds(RCMRMT030101UKEhrExtract ehrExtract, List<RCMRMT030101UKStatementRef> statementRefs) {

        var medicationStatements = getMedicationStatements(ehrExtract);
        Map<String, String> medicationStatementIdMapping = getMedicationStatementIdMapping(medicationStatements);
        List<String> medicationRequestIds = new ArrayList<>();

        statementRefs.forEach(statementRef -> {
            String referenceId = statementRef.getId().getRoot();

            if (medicationStatementIdMapping.containsKey(referenceId)) {
                medicationRequestIds.add(medicationStatementIdMapping.get(referenceId));
            }
        });

        return medicationRequestIds;
    }

    private Map<String, String> getMedicationStatementIdMapping(List<RCMRMT030101UKMedicationStatement> medicationStatements) {

        Map<String, String> statementToRequestMap = new HashMap<>();

        medicationStatements.forEach(medicationStatement -> {
            var medicationStatementId = medicationStatement.getId().getRoot();
            var moodCode = medicationStatement.getMoodCode().getFirst();

            switch (moodCode) {
                case MEDICATION_MOOD_ORDER -> {
                    var medicationRequestOrderId = medicationStatement.getComponent().stream()
                        .filter(RCMRMT030101UKComponent2::hasEhrSupplyPrescribe)
                        .map(RCMRMT030101UKComponent2::getEhrSupplyPrescribe)
                        .map(prescribe -> prescribe.getId().getRoot())
                        .findFirst();

                    medicationRequestOrderId.ifPresent(orderId -> statementToRequestMap.put(medicationStatementId, orderId));
                }
                case MEDICATION_MOOD_INTENTION -> {
                    var medicationRequestPlanId = medicationStatement.getComponent().stream()
                        .filter(RCMRMT030101UKComponent2::hasEhrSupplyAuthorise)
                        .map(RCMRMT030101UKComponent2::getEhrSupplyAuthorise)
                        .map(authorise -> authorise.getId().getRoot())
                        .findFirst();

                    medicationRequestPlanId.ifPresent(planId -> statementToRequestMap.put(medicationStatementId, planId));
                }
                default ->
                    LOGGER.debug("Unexpected mood code [{}] in medication statement [{}] when mapping related content of condition",
                        moodCode, medicationStatement.getId().getRoot());
            }
        });

        return statementToRequestMap;
    }

    private List<Annotation> buildNotes(Optional<RCMRMT030101UKObservationStatement> observationStatement,
                                        RCMRMT030101UKLinkSet linkSet) {

        List<Annotation> annotationList = new ArrayList<>();

        observationStatement.ifPresent(observationStatement1 -> observationStatement1.getPertinentInformation()
            .stream()
            .map(RCMRMT030101UKPertinentInformation02::getPertinentAnnotation)
            .filter(Objects::nonNull)
            .map(RCMRMT030101UKAnnotation::getText)
            .filter(StringUtils::isNotBlank)
            .map(StringType::new)
            .map(Annotation::new)
            .forEach(annotationList::add));

        if (linkSet.hasCode() && linkSet.getCode().getOriginalText() != null) {
            StringType text = new StringType(linkSet.getCode().getOriginalText());
            annotationList.add(new Annotation(text));
        }

        return annotationList;
    }

    private List<RCMRMT030101UKEhrComposition> getCompositionsContainingLinkSets(RCMRMT030101UKEhrExtract ehrExtract) {

        return ehrExtract.getComponent().stream()
            .flatMap(component -> component.getEhrFolder().getComponent().stream())
            .map(RCMRMT030101UKComponent3::getEhrComposition)
            .filter(ehrComposition -> ehrComposition.getComponent()
                .stream()
                .flatMap(CompoundStatementResourceExtractors::extractAllLinkSets)
                .anyMatch(Objects::nonNull))
            .toList();
    }

    private Optional<RCMRMT030101UKObservationStatement> getObservationStatementById(RCMRMT030101UKEhrExtract ehrExtract, String id) {

        List<RCMRMT030101UKObservationStatement> observationStatements = ehrExtract.getComponent().stream()
            .map(RCMRMT030101UKComponent::getEhrFolder)
            .map(RCMRMT030101UKEhrFolder::getComponent)
            .flatMap(Collection::stream)
            .map(RCMRMT030101UKComponent3::getEhrComposition)
            .flatMap(ehrComposition -> ehrComposition.getComponent().stream())
            .flatMap(CompoundStatementResourceExtractors::extractAllObservationStatements)
            .toList();

        return observationStatements.stream()
                .filter(Objects::nonNull)
                .filter(observationStatement -> id.equals(observationStatement.getId().getRoot()))
                .findFirst();
    }
}
