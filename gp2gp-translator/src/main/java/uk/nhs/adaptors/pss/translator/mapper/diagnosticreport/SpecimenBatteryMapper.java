package uk.nhs.adaptors.pss.translator.mapper.diagnosticreport;

import static org.hl7.fhir.dstu3.model.ResourceType.Specimen;

import static uk.nhs.adaptors.pss.translator.util.CompoundStatementUtil.extractResourcesFromCompound;
import static uk.nhs.adaptors.pss.translator.util.DateFormatUtil.parseToInstantType;
import static uk.nhs.adaptors.pss.translator.util.ObservationUtil.getEffective;
import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.buildIdentifier;
import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.generateMeta;
import static uk.nhs.adaptors.pss.translator.util.TextUtil.extractPmipComment;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.DiagnosticReport;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.InstantType;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Observation.ObservationRelatedComponent;
import org.hl7.fhir.dstu3.model.Observation.ObservationRelationshipType;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.hl7.v3.RCMRMT030101UKComponent02;
import org.hl7.v3.RCMRMT030101UKObservationStatement;
import org.hl7.v3.RCMRMT030101UKCompoundStatement;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKNarrativeStatement;
import org.hl7.v3.RCMRMT030101UKParticipant;
import org.hl7.v3.RCMRMT030101UKParticipant2;
import org.hl7.v3.RCMRMT030101UKAuthor;
import org.hl7.v3.TS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import uk.nhs.adaptors.pss.translator.mapper.CodeableConceptMapper;
import uk.nhs.adaptors.pss.translator.util.CompoundStatementResourceExtractors;
import uk.nhs.adaptors.pss.translator.util.DegradedCodeableConcepts;
import uk.nhs.adaptors.pss.translator.util.TextUtil;

import static uk.nhs.adaptors.common.util.CodeableConceptUtils.createCodeableConcept;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class SpecimenBatteryMapper {

    private static final String META_PROFILE_URL_SUFFIX = "Observation-1";
    private static final String USER_COMMENT_HEADER = "USER COMMENT";
    private static final String TYPECODE_PRF = "PRF";
    private static final String TYPECODE_PPRF = "PPRF";
    public static final String CODING_CODE = "laboratory";
    public static final String CODING_SYSTEM = "http://hl7.org/fhir/observation-category";
    public static final String CODING_DISPLAY = "Laboratory";

    private final CodeableConceptMapper codeableConceptMapper;

    public Observation mapBatteryObservation(SpecimenBatteryParameters batteryParameters) {
        final var batteryCompoundStatement = batteryParameters.getBatteryCompoundStatement();
        final var diagnosticReport = batteryParameters.getDiagnosticReport();
        final var ehrComposition = batteryParameters.getEhrComposition();

        final Observation observation = new Observation();
        final String id = batteryParameters.getBatteryCompoundStatement().getId().getFirst().getRoot();
        observation.setId(id);
        observation.setMeta(generateMeta(META_PROFILE_URL_SUFFIX));
        observation.addIdentifier(buildIdentifier(id, batteryParameters.getPracticeCode()));
        observation.setSubject(new Reference(batteryParameters.getPatient()));
        observation.setSpecimen(createSpecimenReference(batteryParameters.getSpecimenCompoundStatement()));
        observation.setStatus(Observation.ObservationStatus.FINAL);
        observation.addCategory(createCategory());
        observation.setCode(createCode(batteryCompoundStatement));
        observation.setComment(getDirectChildNarrativeStatementComments(
            batteryParameters.getBatteryCompoundStatement(), batteryParameters.getObservationComments()));
        getContext(batteryParameters.getEncounters(), ehrComposition).ifPresent(observation::setContext);
        addEffective(batteryCompoundStatement, observation);
        getIssued(batteryCompoundStatement, diagnosticReport, ehrComposition).ifPresent(observation::setIssuedElement);
        getPerformer(batteryCompoundStatement, ehrComposition).ifPresent(observation::addPerformer);
        getRelated(batteryCompoundStatement).forEach(observation::addRelated);
        handleDirectChildNarrativeStatementUserComments(batteryCompoundStatement, observation, batteryParameters.getObservationComments());
        referenceObservationInDiagnosticReport(observation, batteryParameters.getDiagnosticReport());
        referenceBatteryInChildObservations(batteryCompoundStatement, observation, batteryParameters.getObservations());

        return observation;
    }

    private void referenceBatteryInChildObservations(RCMRMT030101UKCompoundStatement batteryCompoundStatement,
                                                     Observation batteryObservation, List<Observation> observations) {

        batteryCompoundStatement.getComponent()
            .stream()
            .flatMap(CompoundStatementResourceExtractors::extractInnerObservationStatements)
            .filter(Objects::nonNull)
            .map(observationStatement -> getObservationById(observations, observationStatement.getId().getRoot()))
            .flatMap(Optional::stream)
            .forEach(observation ->
                observation.addRelated(new ObservationRelatedComponent(new Reference(batteryObservation))
                    .setType(ObservationRelationshipType.DERIVEDFROM)));
    }

    private Optional<Observation> getObservationById(List<Observation> observations, String id) {
        return observations.stream()
            .filter(observation -> id.equals(observation.getId()))
            .findFirst();
    }

    private void referenceObservationInDiagnosticReport(Observation observation, DiagnosticReport diagnosticReport) {
        if (diagnosticReport != null) {
            diagnosticReport.addResult(new Reference(new IdType(ResourceType.Observation.name(), observation.getId())));
        }
    }

    private void handleDirectChildNarrativeStatementUserComments(RCMRMT030101UKCompoundStatement batteryCompoundStatement,
        Observation batteryObservation, List<Observation> observationComments) {

        getDirectNarrativeStatements(batteryCompoundStatement)
            .filter(narrativeStatement -> narrativeStatement.getText().contains(USER_COMMENT_HEADER))
            .forEach(narrativeStatement -> getObservationById(observationComments, narrativeStatement.getId().getRoot())
                .ifPresent(observationComment -> {
                    observationComment.setComment(extractPmipComment(observationComment.getComment()));
                    observationComment.addRelated(new ObservationRelatedComponent(new Reference(batteryObservation))
                        .setType(ObservationRelationshipType.DERIVEDFROM));
                }));
    }

    private String getDirectChildNarrativeStatementComments(RCMRMT030101UKCompoundStatement batteryCompoundStatement,
        List<Observation> observationComments) {

        var narrativeStatements = getDirectNarrativeStatements(batteryCompoundStatement)
            .filter(statement -> !statement.getText().contains(USER_COMMENT_HEADER))
            .toList();

        var surplusObservationComments = observationComments.stream()
            .filter(
                observation -> narrativeStatements.stream()
                    .map(narrativeStatement -> narrativeStatement.getId().getRoot())
                    .anyMatch(id -> id.equals(observation.getId()))
            ).toList();

        observationComments.removeAll(surplusObservationComments);

        return narrativeStatements.stream()
            .map(RCMRMT030101UKNarrativeStatement::getText)
            .filter(text -> !text.contains(USER_COMMENT_HEADER))
            .map(TextUtil::extractPmipComment)
            .collect(Collectors.joining(StringUtils.LF));
    }

    private Optional<Reference> getPerformer(RCMRMT030101UKCompoundStatement batteryCompoundStatement,
        RCMRMT030101UKEhrComposition ehrComposition) {

        Optional<Reference> referenceOpt = Optional.empty();
        if (!batteryCompoundStatement.getParticipant().isEmpty()) {
            referenceOpt = batteryCompoundStatement.getParticipant()
                .stream()
                .filter(participant -> !participant.hasNullFlavour())
                .filter(this::hasTypeCode)
                .map(RCMRMT030101UKParticipant::getAgentRef)
                .filter(Objects::nonNull)
                .findFirst()
                .map(agentRef -> new Reference(new IdType(ResourceType.Practitioner.name(), agentRef.getId().getRoot())));
        }
        if (referenceOpt.isEmpty() && !ehrComposition.getParticipant2().isEmpty()) {
            referenceOpt = ehrComposition.getParticipant2()
                .stream()
                .filter(participant2 -> !participant2.hasNullFlavor())
                .map(RCMRMT030101UKParticipant2::getAgentRef)
                .filter(Objects::nonNull)
                .findFirst()
                .map(agentRef -> new Reference(new IdType(ResourceType.Practitioner.name(), agentRef.getId().getRoot())));
        }
        return referenceOpt;
    }

    private boolean hasTypeCode(RCMRMT030101UKParticipant participant) {
        return participant.getTypeCode()
            .stream()
            .anyMatch(typeCode -> TYPECODE_PRF.equals(typeCode) || TYPECODE_PPRF.equals(typeCode));
    }

    private Optional<Reference> getContext(List<Encounter> encounters, RCMRMT030101UKEhrComposition ehrComposition) {
        return encounters.stream()
            .filter(encounter -> ehrComposition.getId().getRoot().equals(encounter.getId()))
            .findFirst()
            .map(encounter -> new IdType(ResourceType.Encounter.name(), encounter.getId()))
            .map(Reference::new);
    }

    private CodeableConcept createCode(RCMRMT030101UKCompoundStatement compoundStatement) {
        var codeableConcept = codeableConceptMapper.mapToCodeableConcept(compoundStatement.getCode());
        DegradedCodeableConcepts.addDegradedEntryIfRequired(codeableConcept, DegradedCodeableConcepts.DEGRADED_OTHER);
        return codeableConcept;
    }

    private CodeableConcept createCategory() {
        return createCodeableConcept(CODING_CODE, CODING_SYSTEM, CODING_DISPLAY, null);
    }

    private Reference createSpecimenReference(RCMRMT030101UKCompoundStatement specimenCompoundStatement) {
        return new Reference(new IdType(Specimen.name(), specimenCompoundStatement.getId().getFirst().getRoot()));
    }

    private Optional<InstantType> getIssued(
        RCMRMT030101UKCompoundStatement batteryCompoundStatement,
        DiagnosticReport diagnosticReport,
        RCMRMT030101UKEhrComposition ehrComposition) {

        if (batteryCompoundStatement != null
            && availabilityTimeHasValue(batteryCompoundStatement.getAvailabilityTime())) {
            return Optional.of(parseToInstantType(batteryCompoundStatement.getAvailabilityTime().getValue()));
        }

        if (diagnosticReport != null && diagnosticReport.hasIssued()) {
            return Optional.of(diagnosticReport.getIssuedElement());
        }

        if (hasValidTimeValue(ehrComposition.getAuthor())) {
            return Optional.of(parseToInstantType(ehrComposition.getAuthor().getTime().getValue()));
        }

        return Optional.empty();
    }

    private boolean hasValidTimeValue(RCMRMT030101UKAuthor author) {
        return author != null && author.hasTime()
            && author.getTime().hasValue()
            && !author.getTime().hasNullFlavor();
    }

    private boolean availabilityTimeHasValue(TS availabilityTime) {
        return availabilityTime != null && availabilityTime.hasValue() && !availabilityTime.hasNullFlavor();
    }

    private void addEffective(RCMRMT030101UKCompoundStatement compoundStatement, Observation observation) {
        final Object effective = getEffective(compoundStatement.getEffectiveTime(), compoundStatement.getAvailabilityTime());
        if (effective instanceof DateTimeType dateTimeType) {
            observation.setEffective(dateTimeType);
        } else if (effective instanceof Period period) {
            observation.setEffective(period);
        }
    }

    private List<ObservationRelatedComponent> getRelated(RCMRMT030101UKCompoundStatement batteryCompoundStatement) {
        return Stream.concat(
            getDirectNarrativeStatements(batteryCompoundStatement)
                .filter(narrativeStatement -> narrativeStatement.getText().contains(USER_COMMENT_HEADER))
                .map(narrativeStatement -> new Reference(new IdType(ResourceType.Observation.name(), narrativeStatement.getId().getRoot())))
                .map(reference -> new ObservationRelatedComponent().setTarget(reference)
                    .setType(ObservationRelationshipType.HASMEMBER)),
            getObservationReferences(batteryCompoundStatement)
        ).toList();
    }

    private Stream<RCMRMT030101UKNarrativeStatement> getDirectNarrativeStatements(
        RCMRMT030101UKCompoundStatement batteryCompoundStatement) {

        return batteryCompoundStatement.getComponent()
            .stream()
            .filter(RCMRMT030101UKComponent02::hasNarrativeStatement)
            .map(RCMRMT030101UKComponent02::getNarrativeStatement);
    }

    private Stream<ObservationRelatedComponent> getObservationReferences(RCMRMT030101UKCompoundStatement batteryCompoundStatement) {
        return extractResourcesFromCompound(batteryCompoundStatement, RCMRMT030101UKComponent02::hasObservationStatement,
            RCMRMT030101UKComponent02::getObservationStatement)
            .stream()
            .map(RCMRMT030101UKObservationStatement.class::cast)
            .map(observationStatement -> new Reference(new IdType(ResourceType.Observation.name(), observationStatement.getId().getRoot())))
            .map(reference -> new ObservationRelatedComponent().setTarget(reference)
                .setType(ObservationRelationshipType.HASMEMBER));
    }

    @Getter
    @Builder
    public static class SpecimenBatteryParameters {
        private RCMRMT030101UKEhrExtract ehrExtract;
        private RCMRMT030101UKCompoundStatement batteryCompoundStatement;
        private RCMRMT030101UKCompoundStatement specimenCompoundStatement;
        private RCMRMT030101UKEhrComposition ehrComposition;
        private DiagnosticReport diagnosticReport;
        private Patient patient;
        private List<Encounter> encounters;
        private List<Observation> observations;
        private List<Observation> observationComments;
        private String practiceCode;
    }
}
