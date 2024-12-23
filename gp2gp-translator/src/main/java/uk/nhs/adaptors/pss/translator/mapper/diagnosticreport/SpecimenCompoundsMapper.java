package uk.nhs.adaptors.pss.translator.mapper.diagnosticreport;

import static org.hl7.fhir.dstu3.model.ResourceType.Specimen;

import static uk.nhs.adaptors.pss.translator.util.TextUtil.addLine;
import static uk.nhs.adaptors.pss.translator.util.TextUtil.extractPmipComment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.DiagnosticReport;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Observation.ObservationRelatedComponent;
import org.hl7.fhir.dstu3.model.Observation.ObservationRelationshipType;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.v3.RCMRMT030101UKComponent02;
import org.hl7.v3.RCMRMT030101UKComponent3;
import org.hl7.v3.RCMRMT030101UKCompoundStatement;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKNarrativeStatement;
import org.hl7.v3.RCMRMT030101UKObservationStatement;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import uk.nhs.adaptors.pss.translator.mapper.diagnosticreport.SpecimenBatteryMapper.SpecimenBatteryParameters;
import uk.nhs.adaptors.pss.translator.service.ConfidentialityService;
import uk.nhs.adaptors.pss.translator.util.CompoundStatementResourceExtractors;
import uk.nhs.adaptors.pss.translator.util.DateFormatUtil;

import static uk.nhs.adaptors.common.util.CodeableConceptUtils.createCodeableConcept;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class SpecimenCompoundsMapper {

    private static final String BATTERY_CLASSCODE = "BATTERY";
    private static final String CLUSTER_CLASSCODE = "CLUSTER";
    private static final String USER_COMMENT_HEADER = "USER COMMENT";
    public static final String CODING_CODE = "laboratory";
    public static final String CODING_SYSTEM = "http://hl7.org/fhir/observation-category";
    public static final String CODING_DISPLAY = "Laboratory";
    private static final String META_PROFILE = "Observation-1";

    private final SpecimenBatteryMapper batteryMapper;
    private final ConfidentialityService confidentialityService;

    public List<Observation> handleSpecimenChildComponents(
        RCMRMT030101UKEhrExtract ehrExtract,
        List<Observation> observations,
        List<Observation> observationComments,
        List<DiagnosticReport> diagnosticReports,
        Patient patient,
        List<Encounter> encounters,
        String practiceCode) {

        final List<Observation> batteryObservations = new ArrayList<>();

        for (var diagnosticReport : diagnosticReports) {
            var diagnosticReportCompoundStatement = getCompoundStatementByDRId(ehrExtract, diagnosticReport.getId());
            if (diagnosticReportCompoundStatement.isEmpty()) {
                continue;
            }

            var ehrComposition = getCurrentEhrComposition(ehrExtract, diagnosticReportCompoundStatement.orElseThrow());

            for (var specimenCompoundStatement : getSpecimenCompoundStatements(diagnosticReportCompoundStatement.get())) {
                handleSpecimenObservationStatement(ehrComposition, observations, diagnosticReport, specimenCompoundStatement);

                var nestedSpecimenCompoundStatements = getNestedSpecimenCompoundStatements(specimenCompoundStatement);

                for (var nestedSpecimenCompoundStatement : nestedSpecimenCompoundStatements) {
                    if (CLUSTER_CLASSCODE.equals(nestedSpecimenCompoundStatement.getClassCode().getFirst())) {
                        handleClusterCompoundStatement(
                            ehrComposition,
                            specimenCompoundStatement,
                            nestedSpecimenCompoundStatement,
                            observations,
                            observationComments,
                            diagnosticReport,
                            false
                        );
                    }

                    if (BATTERY_CLASSCODE.equals(nestedSpecimenCompoundStatement.getClassCode().getFirst())) {
                        handleBatteryCompoundStatement(
                            ehrComposition,
                            specimenCompoundStatement,
                            nestedSpecimenCompoundStatement,
                            observations,
                            observationComments,
                            diagnosticReport
                        );

                        final SpecimenBatteryParameters batteryParameters = SpecimenBatteryParameters.builder()
                            .ehrExtract(ehrExtract)
                            .batteryCompoundStatement(nestedSpecimenCompoundStatement)
                            .specimenCompoundStatement(specimenCompoundStatement)
                            .ehrComposition(ehrComposition)
                            .diagnosticReport(diagnosticReport)
                            .patient(patient)
                            .encounters(encounters)
                            .observations(observations)
                            .observationComments(observationComments)
                            .practiceCode(practiceCode)
                            .build();

                        batteryObservations.add(batteryMapper.mapBatteryObservation(batteryParameters));
                    }
                }
            }
        }

        return batteryObservations;
    }

    private static @NotNull List<RCMRMT030101UKCompoundStatement> getNestedSpecimenCompoundStatements(
        RCMRMT030101UKCompoundStatement specimenCompoundStatement) {

        return specimenCompoundStatement.getComponent()
            .stream()
            .filter(RCMRMT030101UKComponent02::hasCompoundStatement)
            .map(RCMRMT030101UKComponent02::getCompoundStatement)
            .filter(SpecimenCompoundsMapper::hasBatteryOrClusterClassCode)
            .toList();
    }

    private void handleSpecimenObservationStatement(
        RCMRMT030101UKEhrComposition ehrComposition,
        List<Observation> observations,
        DiagnosticReport diagnosticReport,
        RCMRMT030101UKCompoundStatement specimenCompoundStatement) {

        getObservationStatementsInCompound(specimenCompoundStatement).forEach(specimenObservationStatement ->
            getObservationById(observations, specimenObservationStatement.getId().getRoot())
            .ifPresent(observation -> {
                handleObservationStatement(ehrComposition, specimenCompoundStatement, specimenObservationStatement, observation);
                DiagnosticReportMapper.addResultToDiagnosticReport(observation, diagnosticReport);
            }));
    }

    private void handleObservationStatement(RCMRMT030101UKEhrComposition ehrComposition,
                                            RCMRMT030101UKCompoundStatement specimenCompoundStatement,
                                            RCMRMT030101UKObservationStatement observationStatement,
                                            Observation observation) {

        var meta = confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            specimenCompoundStatement.getConfidentialityCode());

        final Reference specimenReference = new Reference(new IdType(
            Specimen.name(),
            specimenCompoundStatement.getId().getFirst().getRoot()
        ));

        if (observationStatement.getAvailabilityTime().hasValue()) {
            observation.setIssuedElement(
                DateFormatUtil.parseToInstantType(
                    observationStatement.getAvailabilityTime().getValue()
                )
            );
        }

        observation.setSpecimen(specimenReference)
                   .addCategory(createCategory())
                   .setMeta(meta);
    }

    private void handleNarrativeStatements(RCMRMT030101UKCompoundStatement compoundStatement,
        List<Observation> observationComments, Observation observation) {

        List<Observation> surplusObservationComments = new ArrayList<>();

        getNarrativeStatementsInCompound(compoundStatement).forEach(childNarrativeStatement -> {

            var meta = confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
                META_PROFILE,
                compoundStatement.getConfidentialityCode(),
                childNarrativeStatement.getConfidentialityCode());

            if (childNarrativeStatement.getText().contains(USER_COMMENT_HEADER)) {
                getObservationById(observationComments, childNarrativeStatement.getId().getRoot())
                    .ifPresent(observationComment -> {
                        observationComment.setEffective(null);
                        observationComment.setComment(extractPmipComment(observationComment.getComment()));
                        observationComment.setMeta(meta);
                        createRelationship(observation, observationComment);
                    });
            } else if (observation != null) {
                observation.setComment(addLine(observation.getComment(), extractPmipComment(childNarrativeStatement.getText())));
                observation.setMeta(meta);

                getObservationById(observationComments, childNarrativeStatement.getId().getRoot())
                    .ifPresent(surplusObservationComments::add);
            }
        });
        observationComments.removeAll(surplusObservationComments);
    }

    private void createRelationship(Observation observation, Observation observationComment) {
        if (observation != null && !containsRelatedComponent(observationComment, observation.getId())) {
            observationComment.addRelated(
                new ObservationRelatedComponent(new Reference(observation)).setType(ObservationRelationshipType.DERIVEDFROM)
            );
        }
    }

    private void handleClusterCompoundStatement(RCMRMT030101UKEhrComposition ehrComposition,
                                                RCMRMT030101UKCompoundStatement specimenCompoundStatement,
                                                RCMRMT030101UKCompoundStatement clusterCompoundStatement,
                                                List<Observation> observations, List<Observation> observationComments,
                                                DiagnosticReport diagnosticReport, boolean isNestedCluster) {

        var nestedObservationStatements = clusterCompoundStatement.getComponent().stream()
            .filter(RCMRMT030101UKComponent02::hasObservationStatement)
            .map(RCMRMT030101UKComponent02::getObservationStatement)
            .toList();

        for (var observationStatement : nestedObservationStatements) {
            var observationOpt = getObservationById(observations, observationStatement.getId().getRoot());

            observationOpt.ifPresent(observation -> {
                if (!isNestedCluster) {
                    DiagnosticReportMapper.addResultToDiagnosticReport(observation, diagnosticReport);
                }
                handleObservationStatement(ehrComposition, specimenCompoundStatement, observationStatement, observation);
            });

            handleNarrativeStatements(clusterCompoundStatement, observationComments, observationOpt.orElse(null));
        }
    }

    private void handleBatteryCompoundStatement(RCMRMT030101UKEhrComposition ehrComposition,
                                                RCMRMT030101UKCompoundStatement specimenCompoundStatement,
                                                RCMRMT030101UKCompoundStatement batteryCompoundStatement,
                                                List<Observation> observations,
                                                List<Observation> observationComments,
                                                DiagnosticReport diagnosticReport) {

        batteryCompoundStatement.getComponent().stream()
            .filter(RCMRMT030101UKComponent02::hasCompoundStatement)
            .map(RCMRMT030101UKComponent02::getCompoundStatement)
            .filter(compoundStatement -> CLUSTER_CLASSCODE.equals(compoundStatement.getClassCode().getFirst()))
            .forEach(compoundStatement ->
                         handleClusterCompoundStatement(
                             ehrComposition,
                             specimenCompoundStatement,
                             compoundStatement,
                             observations,
                             observationComments,
                             diagnosticReport,
                             true));

        batteryCompoundStatement.getComponent().stream()
            .filter(RCMRMT030101UKComponent02::hasObservationStatement)
            .map(RCMRMT030101UKComponent02::getObservationStatement)
            .forEach(
                observationStatement -> getObservationById(observations, observationStatement.getId().getRoot()).ifPresent(
                    observation -> handleObservationStatement(ehrComposition, specimenCompoundStatement,
                                                              observationStatement, observation)));
    }

    private Optional<RCMRMT030101UKCompoundStatement> getCompoundStatementByDRId(RCMRMT030101UKEhrExtract ehrExtract, String id) {

        return ehrExtract.getComponent().getFirst().getEhrFolder().getComponent()
            .stream()
            .flatMap(component3 -> component3.getEhrComposition().getComponent().stream())
            .flatMap(CompoundStatementResourceExtractors::extractAllCompoundStatements)
            .filter(Objects::nonNull)
            .filter(compoundStatement -> id.equals(compoundStatement.getId().getFirst().getRoot()))
            .findFirst();
    }

    private Optional<Observation> getObservationById(List<Observation> observations, String id) {
        return observations.stream()
            .filter(e -> id.equals(e.getId()))
            .findFirst();
    }

    private boolean containsRelatedComponent(Observation observation, String id) {
        if (!observation.getRelated().isEmpty()) {
            return observation.getRelated().stream()
                .map(relatedComponent -> relatedComponent.getTarget().getResource())
                .filter(Objects::nonNull)
                .anyMatch(resource -> id.equals(resource.getIdElement().getValue()));
        }
        return false;
    }

    private CodeableConcept createCategory() {
        return createCodeableConcept(CODING_CODE, CODING_SYSTEM, CODING_DISPLAY, null);
    }

    private List<RCMRMT030101UKCompoundStatement> getSpecimenCompoundStatements(
        RCMRMT030101UKCompoundStatement parentCompoundStatement) {

        return parentCompoundStatement.getComponent()
            .stream()
            .filter(RCMRMT030101UKComponent02::hasCompoundStatement)
            .map(RCMRMT030101UKComponent02::getCompoundStatement)
            .toList();
    }

    private List<RCMRMT030101UKObservationStatement> getObservationStatementsInCompound(
        RCMRMT030101UKCompoundStatement compoundStatement) {

        return compoundStatement.getComponent()
            .stream()
            .filter(RCMRMT030101UKComponent02::hasObservationStatement)
            .map(RCMRMT030101UKComponent02::getObservationStatement)
            .toList();
    }

    protected List<RCMRMT030101UKNarrativeStatement> getNarrativeStatementsInCompound(
        RCMRMT030101UKCompoundStatement compoundStatement) {

        return compoundStatement.getComponent()
            .stream()
            .filter(RCMRMT030101UKComponent02::hasNarrativeStatement)
            .map(RCMRMT030101UKComponent02::getNarrativeStatement)
            .toList();
    }

    private RCMRMT030101UKEhrComposition getCurrentEhrComposition(RCMRMT030101UKEhrExtract ehrExtract,
                                                                  RCMRMT030101UKCompoundStatement parentCompoundStatement) {

        return ehrExtract.getComponent().getFirst().getEhrFolder().getComponent()
            .stream()
            .filter(RCMRMT030101UKComponent3::hasEhrComposition)
            .map(RCMRMT030101UKComponent3::getEhrComposition)
            .filter(e -> e.getComponent()
                .stream()
                .flatMap(CompoundStatementResourceExtractors::extractAllCompoundStatements)
                .anyMatch(parentCompoundStatement::equals)
            ).findFirst().get();
    }

    private static boolean hasBatteryOrClusterClassCode(RCMRMT030101UKCompoundStatement compoundStatement) {
        return CLUSTER_CLASSCODE.equals(compoundStatement.getClassCode().getFirst())
            || BATTERY_CLASSCODE.equals(compoundStatement.getClassCode().getFirst());
    }
}
