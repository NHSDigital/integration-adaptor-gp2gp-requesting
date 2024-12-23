package uk.nhs.adaptors.pss.translator.mapper.diagnosticreport;

import static java.util.stream.Collectors.toCollection;

import static org.hl7.fhir.dstu3.model.Observation.ObservationStatus.UNKNOWN;
import static uk.nhs.adaptors.pss.translator.util.CompoundStatementResourceExtractors.extractAllCompoundStatements;
import static uk.nhs.adaptors.pss.translator.util.DateFormatUtil.parseToDateTimeType;
import static uk.nhs.adaptors.pss.translator.util.DateFormatUtil.parseToInstantType;
import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.buildIdentifier;
import static uk.nhs.adaptors.pss.translator.util.TextUtil.extractPmipComment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.DiagnosticReport;
import org.hl7.fhir.dstu3.model.DiagnosticReport.DiagnosticReportStatus;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.InstantType;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.hl7.v3.II;
import org.hl7.v3.RCMRMT030101UKComponent02;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKCompoundStatement;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKNarrativeStatement;
import org.hl7.v3.RCMRMT030101UKAuthor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import uk.nhs.adaptors.pss.translator.mapper.AbstractMapper;
import uk.nhs.adaptors.pss.translator.service.ConfidentialityService;
import uk.nhs.adaptors.pss.translator.service.IdGeneratorService;
import uk.nhs.adaptors.pss.translator.util.CompoundStatementResourceExtractors;
import uk.nhs.adaptors.pss.translator.util.ResourceFilterUtil;
import uk.nhs.adaptors.pss.translator.util.TextUtil;
import static uk.nhs.adaptors.common.util.CodeableConceptUtils.createCodeableConcept;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class DiagnosticReportMapper extends AbstractMapper<DiagnosticReport> {
    private static final String PMIP_EXTENSION_IDENTIFIER_ROOT = "2.16.840.1.113883.2.1.4.5.5";
    private static final String META_PROFILE = "DiagnosticReport-1";
    public static final String CODING_CODE = "721981007";
    public static final String CODING_SYSTEM = "http://snomed.info/sct";
    public static final String CODING_DISPLAY = "Diagnostic studies report";
    private static final String BATTERY_CLASS_CODE = "BATTERY";
    private static final String CLUSTER_CLASS_CODE = "CLUSTER";
    private static final String LAB_REPORT_COMMENT_TYPE = "CommentType:LABORATORY RESULT COMMENT(E141)";
    public static final String USER_COMMENT_COMMENT_TYPE = "CommentType:USER COMMENT";

    private final IdGeneratorService idGeneratorService;
    private final ConfidentialityService confidentialityService;

    public static void addResultToDiagnosticReport(Observation observation, DiagnosticReport diagnosticReport) {
        if (!containsReference(diagnosticReport.getResult(), observation.getId())) {
            diagnosticReport.addResult(new Reference(observation));
        }
    }

    @Override
    public List<DiagnosticReport> mapResources(RCMRMT030101UKEhrExtract ehrExtract, Patient patient, List<Encounter> encounters,
                                               String practiceCode) {
        return mapResources(ehrExtract, patient, encounters, practiceCode, new ArrayList<>());
    }

    public List<DiagnosticReport> mapResources(RCMRMT030101UKEhrExtract ehrExtract, Patient patient, List<Encounter> encounters,
                                               String practiceCode, List<Observation> observationComments) {
        return mapEhrExtractToFhirResource(ehrExtract, (extract, composition, component) ->
                extractAllCompoundStatements(component)
                        .filter(Objects::nonNull)
                        .filter(ResourceFilterUtil::isDiagnosticReport)
                        .map(compoundStatement -> {
                                    DiagnosticReport diagnosticReport = createDiagnosticReport(
                                            compoundStatement, patient, composition, encounters, observationComments, practiceCode
                                    );
                                    getIssued(compoundStatement, composition).ifPresent(diagnosticReport::setIssuedElement);
                                    return diagnosticReport;
                                }
                        )).toList();
    }

    public void handleChildObservationComments(RCMRMT030101UKEhrExtract ehrExtract, List<Observation> observationComments) {

        List<Observation> conclusionComments = new ArrayList<>();

        ehrExtract.getComponent().getFirst().getEhrFolder().getComponent()
                .stream()
                .flatMap(e -> e.getEhrComposition().getComponent().stream())
                .flatMap(CompoundStatementResourceExtractors::extractAllCompoundStatements)
                .filter(Objects::nonNull)
                .filter(ResourceFilterUtil::isDiagnosticReport)
                .flatMap(e -> e.getComponent().stream())
                .filter(RCMRMT030101UKComponent02::hasNarrativeStatement)
                .map(RCMRMT030101UKComponent02::getNarrativeStatement)
                .map(narrativeStatement -> getObservationCommentById(observationComments, narrativeStatement.getId().getRoot()))
                .flatMap(Optional::stream)
                .forEach(observationComment -> {
                    if (observationComment.getComment().contains(LAB_REPORT_COMMENT_TYPE)) {
                        conclusionComments.add(observationComment);
                    }

                    observationComment.setEffective(null);
                    observationComment.setComment(extractPmipComment(observationComment.getComment()));
                });

        observationComments.removeAll(conclusionComments);
    }

    private DiagnosticReport createDiagnosticReport(RCMRMT030101UKCompoundStatement compoundStatement,
                                                    Patient patient,
                                                    RCMRMT030101UKEhrComposition ehrComposition,
                                                    List<Encounter> encounters,
                                                    List<Observation> observationComments,
                                                    String practiceCode
    ) {
        final DiagnosticReport diagnosticReport = new DiagnosticReport();
        final String id = compoundStatement.getId().getFirst().getRoot();

        var meta = confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            compoundStatement.getConfidentialityCode());

        diagnosticReport.setMeta(meta)
                        .setId(id);
        diagnosticReport.addIdentifier(buildIdentifier(id, practiceCode))
                        .setCode(createCode())
                        .setStatus(DiagnosticReportStatus.UNKNOWN)
                        .setSubject(new Reference(patient))
                        .setSpecimen(getSpecimenReferences(compoundStatement));
        createIdentifierExtension(compoundStatement.getId()).ifPresent(diagnosticReport::addIdentifier);
        buildContext(ehrComposition, encounters).ifPresent(diagnosticReport::setContext);
        setResultReferences(ehrComposition, compoundStatement, diagnosticReport, observationComments);

        var conclusion = getConclusion(compoundStatement);

        if (!conclusion.isEmpty()) {
            diagnosticReport.setConclusion(conclusion);
        }

        return diagnosticReport;
    }

    private String getConclusion(RCMRMT030101UKCompoundStatement compoundStatement) {

        return compoundStatement.getComponent()
                .stream()
                .filter(RCMRMT030101UKComponent02::hasNarrativeStatement)
                .map(RCMRMT030101UKComponent02::getNarrativeStatement)
                .map(RCMRMT030101UKNarrativeStatement::getText)
                .filter(comment -> comment.contains(LAB_REPORT_COMMENT_TYPE))
                .map(TextUtil::extractPmipComment)
                .collect(Collectors.joining(StringUtils.LF));
    }

    private List<Reference> getSpecimenReferences(RCMRMT030101UKCompoundStatement compoundStatement) {

        return compoundStatement.getComponent()
                .stream()
                .filter(RCMRMT030101UKComponent02::hasCompoundStatement)
                .map(RCMRMT030101UKComponent02::getCompoundStatement)
                .filter(ResourceFilterUtil::isSpecimen)
                .map(compoundStatement1 -> new Reference(
                        new IdType(ResourceType.Specimen.name(), compoundStatement1.getId().getFirst().getRoot())))
                .toList();
    }

    private void setResultReferences(
        RCMRMT030101UKEhrComposition ehrComposition,
        RCMRMT030101UKCompoundStatement compoundStatement,
        DiagnosticReport diagnosticReport,
        List<Observation> observationComments
    ) {
        var resultReferences = getDirectResultReferences(compoundStatement);
        diagnosticReport.setResult(resultReferences);

        var batteryLevelFilingCommentId = getBatteryLevelFilingCommentId(compoundStatement);

        if (batteryLevelFilingCommentId.isEmpty()) {
            return;
        }

        var existingObservationComment =
                getObservationCommentById(observationComments, batteryLevelFilingCommentId.get());

        if (existingObservationComment.isPresent()) {
            var filingCommentObservation = buildFilingComment(ehrComposition, existingObservationComment.get());

            diagnosticReport.getResult().add(buildFilingCommentReference(filingCommentObservation));
            observationComments.add(filingCommentObservation);
        }
    }

    private Observation buildFilingComment(
        RCMRMT030101UKEhrComposition ehrComposition,
        Observation existingObservationComment) {
        var filingCommentObservation = existingObservationComment.copy();
        var filingCommentObservationId = idGeneratorService.generateUuid().toUpperCase();
        filingCommentObservation.setId(filingCommentObservationId);
        filingCommentObservation.getIdentifierFirstRep().setValue(filingCommentObservationId);
        filingCommentObservation.setComment(null);
        filingCommentObservation.setStatus(UNKNOWN);
        filingCommentObservation.setEffective(
            parseToDateTimeType(ehrComposition.getAuthor().getTime().getValue())
        );
        filingCommentObservation.setPerformer(
            Collections.singletonList(
                new Reference("Practitioner/" + ehrComposition.getAuthor().getAgentRef().getId().getRoot())
            )
        );
        return filingCommentObservation;
    }

    @NotNull
    private Optional<String> getBatteryLevelFilingCommentId(RCMRMT030101UKCompoundStatement compoundStatement) {
        return compoundStatement.getComponent()
                .stream()
                .flatMap(component -> getComponentStreamByClassCode(component, CLUSTER_CLASS_CODE))
                .flatMap(component -> getComponentStreamByClassCode(component, BATTERY_CLASS_CODE))
                .filter(RCMRMT030101UKComponent02::hasNarrativeStatement)
                .map(RCMRMT030101UKComponent02::getNarrativeStatement)
                .filter(this::isUserCommentType)
                .map(narrativeStatement -> narrativeStatement.getId().getRoot())
                .findFirst();
    }

    @NotNull
    private ArrayList<Reference> getDirectResultReferences(RCMRMT030101UKCompoundStatement compoundStatement) {
        return compoundStatement.getComponent()
                .stream()
                .filter(RCMRMT030101UKComponent02::hasNarrativeStatement)
                .map(RCMRMT030101UKComponent02::getNarrativeStatement)
                .filter(this::isNotLabReportCommentType)
                .map(this::buildReference)
                .collect(toCollection(ArrayList::new));
    }

    private Reference buildReference(RCMRMT030101UKNarrativeStatement narrativeStatement) {
        return new Reference(new IdType(
                ResourceType.Observation.name(),
                narrativeStatement.getId().getRoot()));
    }

    private Reference buildFilingCommentReference(Observation filingComment) {
        return new Reference(new IdType(ResourceType.Observation.name(), filingComment.getId()));
    }

    private Stream<RCMRMT030101UKComponent02> getComponentStreamByClassCode(
            RCMRMT030101UKComponent02 component,
            String classCode) {
        return Optional.ofNullable(component.getCompoundStatement())
                .filter(compoundStatement -> classCode.equals(compoundStatement.getClassCode().getFirst()))
                .map(compoundStatement -> compoundStatement.getComponent().stream())
                .orElse(Stream.empty());
    }

    private boolean isUserCommentType(RCMRMT030101UKNarrativeStatement narrativeStatement) {
        return narrativeStatement.getText().contains(USER_COMMENT_COMMENT_TYPE);
    }

    private boolean isNotLabReportCommentType(RCMRMT030101UKNarrativeStatement narrativeStatement) {
        return !narrativeStatement.getText().contains(LAB_REPORT_COMMENT_TYPE);
    }

    private Optional<Observation> getObservationCommentById(List<Observation> observationComments, String id) {
        return observationComments.stream()
            .filter(observationComment -> id.equals(observationComment.getId()))
            .findFirst();
    }

    private Optional<Identifier> createIdentifierExtension(List<II> id) {
        if (id.size() > 1) {
            final II idExtension = id.get(1);
            if (idExtension != null && PMIP_EXTENSION_IDENTIFIER_ROOT.equals(idExtension.getRoot())) {
                return Optional.of(new Identifier()
                    .setSystem("urn:oid:" + PMIP_EXTENSION_IDENTIFIER_ROOT)
                    .setValue(idExtension.getExtension()));
            }
        }
        return Optional.empty();
    }

    private Optional<Reference> buildContext(RCMRMT030101UKEhrComposition ehrComposition, List<Encounter> encounters) {

        return encounters.stream()
            .filter(encounter -> encounter.getId().equals(ehrComposition.getId().getRoot()))
            .findFirst()
            .map(Reference::new);
    }

    private Optional<InstantType> getIssued(RCMRMT030101UKCompoundStatement compoundStatement,
                                            RCMRMT030101UKEhrComposition ehrComposition) {

        if (compoundStatementHasValidAvailabilityTime(compoundStatement)) {
            return Optional.of(parseToInstantType(compoundStatement.getAvailabilityTime().getValue()));
        }

        if (authorHasValidTimeValue(ehrComposition.getAuthor())) {
            return Optional.of(parseToInstantType(ehrComposition.getAuthor().getTime().getValue()));
        }

        return Optional.empty();
    }

    private boolean compoundStatementHasValidAvailabilityTime(RCMRMT030101UKCompoundStatement compoundStatement) {

        return compoundStatement != null && compoundStatement.getAvailabilityTime() != null
            && compoundStatement.getAvailabilityTime().hasValue()
            && !compoundStatement.getAvailabilityTime().hasNullFlavor();
    }

    private boolean authorHasValidTimeValue(RCMRMT030101UKAuthor author) {
        return author != null && author.hasTime()
            && author.getTime().hasValue()
            && !author.getTime().hasNullFlavor();
    }

    private CodeableConcept createCode() {
        return createCodeableConcept(CODING_CODE, CODING_SYSTEM, CODING_DISPLAY, null);
    }

    private static boolean containsReference(List<Reference> references, String id) {
        if (!references.isEmpty()) {
            return references.stream()
                .map(reference -> {
                    if (reference.hasReference()) {
                        return reference.getReference();
                    }
                    if (reference.getResource() != null) {
                        return reference.getResource().getIdElement().getValue();
                    }
                    return StringUtils.EMPTY;
                }).anyMatch(referenceId -> referenceId.contains(id));
        }
        return false;
    }
}
