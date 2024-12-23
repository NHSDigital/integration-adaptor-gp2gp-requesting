package uk.nhs.adaptors.pss.translator.mapper;

import static org.hl7.fhir.dstu3.model.Observation.ObservationRelationshipType.DERIVEDFROM;
import static org.hl7.fhir.dstu3.model.Observation.ObservationRelationshipType.HASMEMBER;
import static org.hl7.fhir.dstu3.model.Observation.ObservationStatus.FINAL;

import static uk.nhs.adaptors.pss.translator.util.CompoundStatementResourceExtractors.extractAllCompoundStatements;
import static uk.nhs.adaptors.pss.translator.util.DateFormatUtil.parseToInstantType;
import static uk.nhs.adaptors.pss.translator.util.ObservationUtil.getEffective;
import static uk.nhs.adaptors.pss.translator.util.ParticipantReferenceUtil.getParticipantReference;
import static uk.nhs.adaptors.pss.translator.util.ResourceFilterUtil.hasDiagnosticReportParent;
import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.buildIdentifier;
import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.generateMeta;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.DomainResource;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.InstantType;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.hl7.v3.RCMRMT030101UKComponent02;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKObservationStatement;
import org.hl7.v3.RCMRMT030101UKCompoundStatement;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import uk.nhs.adaptors.pss.translator.util.CompoundStatementResourceExtractors;
import uk.nhs.adaptors.pss.translator.util.CompoundStatementUtil;
import uk.nhs.adaptors.pss.translator.util.DegradedCodeableConcepts;
import uk.nhs.adaptors.pss.translator.util.ResourceFilterUtil;
import uk.nhs.adaptors.pss.translator.util.ResourceReferenceUtil;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class TemplateMapper extends AbstractMapper<DomainResource> {
    private static final String OBSERVATION_META_PROFILE = "Observation-1";
    private final CodeableConceptMapper codeableConceptMapper;
    private final ResourceReferenceUtil resourceReferenceUtil;

    @Override
    public List<DomainResource> mapResources(RCMRMT030101UKEhrExtract ehrExtract, Patient patient,
                                             List<Encounter> encounters, String practiceCode) {

        return  mapEhrExtractToFhirResource(ehrExtract, (extract, composition, component) ->
            extractAllCompoundStatements(component)
                .filter(Objects::nonNull)
                .filter(ResourceFilterUtil::isTemplate)
                .filter(compoundStatement -> !hasDiagnosticReportParent(ehrExtract, compoundStatement))
                .map(compoundStatement -> mapTemplate(composition, compoundStatement, patient, encounters, practiceCode))
                .flatMap(List::stream)
        ).toList();

    }

    public void addReferences(List<DomainResource> templates, List<Observation> observations, RCMRMT030101UKEhrExtract ehrExtract) {
        List<Observation> parentObservations = templates.stream()
            .filter(Observation.class::isInstance)
            .map(Observation.class::cast)
            .toList();

        List<String> parentObservationIds = parentObservations.stream()
            .map(Observation::getId)
            .toList();

        var parentCompoundStatements = getCompoundStatementsByIds(ehrExtract, parentObservationIds);

        parentCompoundStatements.forEach(parentCompoundStatement -> {

            if (isObservationStatementTemplateParent(parentCompoundStatement)) {
                Observation parentObservation = parentObservations.stream()
                    .filter(observation -> observation.getId().equals(parentCompoundStatement.getId().getFirst().getRoot()))
                    .findFirst()
                    .orElseThrow();

                List<String> childObservationIds = CompoundStatementUtil
                    .extractResourcesFromCompound(parentCompoundStatement,
                        RCMRMT030101UKComponent02::hasObservationStatement, RCMRMT030101UKComponent02::getObservationStatement)
                    .stream()
                    .map(RCMRMT030101UKObservationStatement.class::cast)
                    .map(observationStatement -> observationStatement.getId().getRoot())
                    .toList();

                List<Observation> childObservations = observations.stream()
                    .filter(observation -> childObservationIds.contains(observation.getId()))
                    .toList();

                childObservations.forEach(childObservation -> {
                    parentObservation.addRelated(new Observation.ObservationRelatedComponent()
                        .setType(HASMEMBER)
                        .setTarget(new Reference(new IdType(ResourceType.Observation.name(), childObservation.getId())))
                    );

                    childObservation.addRelated(new Observation.ObservationRelatedComponent()
                        .setType(DERIVEDFROM)
                        .setTarget(new Reference(new IdType(ResourceType.Observation.name(), parentObservation.getId())))
                    );
                });
            }
        });

    }

    private List<DomainResource> mapTemplate(RCMRMT030101UKEhrComposition ehrComposition,
                                             RCMRMT030101UKCompoundStatement compoundStatement,
                                             Patient patient,
                                             List<Encounter> encounters,
                                             String practiceCode) {
        var encounter = getEncounter(encounters, ehrComposition);

        return List.of(
            createParentObservation(compoundStatement, practiceCode, patient, encounter, ehrComposition)
        );
    }

    private Optional<Reference> getEncounter(List<Encounter> encounters, RCMRMT030101UKEhrComposition ehrComposition) {
        return encounters
            .stream()
            .filter(encounter -> encounter.getId().equals(ehrComposition.getId().getRoot()))
            .map(Reference::new)
            .findFirst();
    }

    private Observation createParentObservation(RCMRMT030101UKCompoundStatement compoundStatement, String practiceCode, Patient patient,
        Optional<Reference> encounter, RCMRMT030101UKEhrComposition ehrComposition) {

        var parentObservation = new Observation();
        var id = compoundStatement.getId().getFirst().getRoot();

        var codeableConcept = codeableConceptMapper.mapToCodeableConcept(compoundStatement.getCode());
        DegradedCodeableConcepts.addDegradedEntryIfRequired(codeableConcept, DegradedCodeableConcepts.DEGRADED_OTHER);

        parentObservation
            .setSubject(new Reference(patient))
            .setIssuedElement(getIssued(ehrComposition))
            .addPerformer(getParticipantReference(compoundStatement.getParticipant(), ehrComposition))
            .setCode(codeableConcept)
            .setStatus(FINAL)
            .addIdentifier(buildIdentifier(id, practiceCode))
            .setMeta(generateMeta(OBSERVATION_META_PROFILE))
            .setId(id);

        encounter.ifPresent(parentObservation::setContext);
        addEffective(parentObservation,
            getEffective(compoundStatement.getEffectiveTime(), compoundStatement.getAvailabilityTime()));

        return parentObservation;
    }

    private void addEffective(Observation observation, Object effective) {
        if (effective instanceof DateTimeType dateTimeType) {
            observation.setEffective(dateTimeType);
        } else if (effective instanceof Period period) {
            observation.setEffective(period);
        }
    }

    private InstantType getIssued(RCMRMT030101UKEhrComposition ehrComposition) {
        if (ehrComposition.getAuthor().getTime().hasValue()) {

            return parseToInstantType(ehrComposition.getAuthor().getTime().getValue());
        }
        return null;
    }

    private List<RCMRMT030101UKCompoundStatement> getCompoundStatementsByIds(RCMRMT030101UKEhrExtract ehrExtract, List<String> ids) {

        return ehrExtract.getComponent().getFirst().getEhrFolder().getComponent()
            .stream()
            .flatMap(component3 -> component3.getEhrComposition().getComponent().stream())
            .flatMap(CompoundStatementResourceExtractors::extractAllCompoundStatements)
            .filter(Objects::nonNull)
            .filter(compoundStatement -> ids.contains(compoundStatement.getId().getFirst().getRoot()))
            .toList();
    }

    private boolean isObservationStatementTemplateParent(RCMRMT030101UKCompoundStatement compoundStatement) {
        var hasChildObservationStatement = compoundStatement.getComponent().stream()
            .anyMatch(RCMRMT030101UKComponent02::hasObservationStatement);

        var allChildrenAreEitherAnObservationStatementOrNarrativeStatment = compoundStatement.getComponent().stream()
            .allMatch(component -> component.hasObservationStatement() || component.hasNarrativeStatement());

        return hasChildObservationStatement && allChildrenAreEitherAnObservationStatementOrNarrativeStatment;
    }
}
