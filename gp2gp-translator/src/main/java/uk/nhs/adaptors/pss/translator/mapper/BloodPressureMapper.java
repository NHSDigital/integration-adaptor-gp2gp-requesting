package uk.nhs.adaptors.pss.translator.mapper;

import static uk.nhs.adaptors.pss.translator.util.BloodPressureValidatorUtil.isDiastolicBloodPressure;
import static uk.nhs.adaptors.pss.translator.util.BloodPressureValidatorUtil.isSystolicBloodPressure;
import static uk.nhs.adaptors.pss.translator.util.CDUtil.extractSnomedCode;
import static uk.nhs.adaptors.pss.translator.util.ObservationUtil.getEffective;
import static uk.nhs.adaptors.pss.translator.util.ObservationUtil.getInterpretation;
import static uk.nhs.adaptors.pss.translator.util.ObservationUtil.getIssued;
import static uk.nhs.adaptors.pss.translator.util.ObservationUtil.getReferenceRange;
import static uk.nhs.adaptors.pss.translator.util.ObservationUtil.getValueQuantity;
import static uk.nhs.adaptors.pss.translator.util.ParticipantReferenceUtil.getParticipantReference;
import static uk.nhs.adaptors.pss.translator.util.ResourceFilterUtil.hasDiagnosticReportParent;
import static uk.nhs.adaptors.pss.translator.util.ResourceFilterUtil.isDiagnosticReport;
import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.addContextToObservation;
import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.buildIdentifier;
import static uk.nhs.adaptors.pss.translator.util.CompoundStatementResourceExtractors.extractAllCompoundStatements;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Observation.ObservationComponentComponent;
import org.hl7.fhir.dstu3.model.Observation.ObservationStatus;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.v3.CD;
import org.hl7.v3.CV;
import org.hl7.v3.RCMRMT030101UKAnnotation;
import org.hl7.v3.RCMRMT030101UKComponent02;
import org.hl7.v3.RCMRMT030101UKCompoundStatement;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKNarrativeStatement;
import org.hl7.v3.RCMRMT030101UKObservationStatement;
import org.hl7.v3.RCMRMT030101UKPertinentInformation02;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import uk.nhs.adaptors.pss.translator.service.ConfidentialityService;
import uk.nhs.adaptors.pss.translator.util.BloodPressureValidatorUtil;
import uk.nhs.adaptors.pss.translator.util.DegradedCodeableConcepts;

@Service
@AllArgsConstructor
public class BloodPressureMapper extends AbstractMapper<Observation> {
    private static final String META_PROFILE = "Observation-1";
    private static final String SYSTOLIC_NOTE = "Systolic Note: ";
    private static final String DIASTOLIC_NOTE = "Diastolic Note: ";
    private static final String BP_NOTE = "BP Note: ";

    private CodeableConceptMapper codeableConceptMapper;

    private ConfidentialityService confidentialityService;

    public List<Observation> mapResources(RCMRMT030101UKEhrExtract ehrExtract, Patient patient, List<Encounter> encounters,
                                          String practiceCode) {
        return mapEhrExtractToFhirResource(ehrExtract, (extract, composition, component) ->
            extractAllCompoundStatements(component)
                .filter(Objects::nonNull)
                .filter(BloodPressureValidatorUtil::isBloodPressureWithBatteryAndBloodPressureTriple)
                .filter(compoundStatement -> !isDiagnosticReport(compoundStatement)
                    && !hasDiagnosticReportParent(ehrExtract, compoundStatement))
                .map(compoundStatement -> mapObservation(composition, compoundStatement, patient, encounters, practiceCode)))
            .toList();
    }

    private Observation mapObservation(RCMRMT030101UKEhrComposition ehrComposition,
                                       RCMRMT030101UKCompoundStatement compoundStatement, Patient patient, List<Encounter> encounters,
                                       String practiceCode) {
        var observationStatements = getObservationStatementsFromCompoundStatement(compoundStatement);
        var id = compoundStatement.getId().getFirst();

        Observation observation = new Observation()
            .addIdentifier(buildIdentifier(id.getRoot(), practiceCode))
            .setStatus(ObservationStatus.FINAL)
            .setCode(getCode(compoundStatement.getCode()))
            .setComponent(getComponent(observationStatements))
            .setComment(
                getComment(observationStatements, getNarrativeStatementsFromCompoundStatement(compoundStatement)))
            .setSubject(new Reference(patient))
            .setIssuedElement(getIssued(
                ehrComposition))
            .addPerformer(getParticipantReference(
                compoundStatement.getParticipant(),
                ehrComposition));

        observation.setId(id.getRoot());
        observation.setMeta(generateMeta(ehrComposition, compoundStatement, observationStatements));

        addEffective(observation, getEffective(compoundStatement.getEffectiveTime(), compoundStatement.getAvailabilityTime()));
        addContextToObservation(observation, encounters, ehrComposition);

        return observation;
    }

    private Meta generateMeta(RCMRMT030101UKEhrComposition ehrComposition,
                              RCMRMT030101UKCompoundStatement compoundStatement,
                              List<RCMRMT030101UKObservationStatement> observationStatements) {
        @SuppressWarnings("unchecked")
        final Optional<CV>[] confidentialityCodes = Stream.concat(
            Stream.of(ehrComposition.getConfidentialityCode(), compoundStatement.getConfidentialityCode()),
            observationStatements.stream().map(RCMRMT030101UKObservationStatement::getConfidentialityCode)
        ).toArray(Optional[]::new);

        return confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            confidentialityCodes
        );
    }

    private CodeableConcept getCode(CD code) {
        if (code != null) {
            var codeableConcept = codeableConceptMapper.mapToCodeableConcept(code);
            DegradedCodeableConcepts.addDegradedEntryIfRequired(
                codeableConcept, DegradedCodeableConcepts.DEGRADED_OTHER);

            return codeableConcept;
        }

        return null;
    }

    private List<ObservationComponentComponent> getComponent(List<RCMRMT030101UKObservationStatement> observationStatements) {
        var components = new ArrayList<ObservationComponentComponent>();

        for (RCMRMT030101UKObservationStatement observationStatement
            : observationStatements) {
            components.add(new ObservationComponentComponent()
                .setCode(getCode(observationStatement.getCode()))
                .setValue(getValueQuantity(observationStatement.getValue(),
                    observationStatement.getUncertaintyCode()))
                .setInterpretation(getInterpretation(observationStatement.getInterpretationCode()))
                .setReferenceRange(getReferenceRange(observationStatement.getReferenceRange())));
        }

        return components;
    }

    private String getComment(List<RCMRMT030101UKObservationStatement> observationStatements,
        List<RCMRMT030101UKNarrativeStatement> narrativeStatements) {
        var stringBuilder = new StringBuilder();

        for (RCMRMT030101UKObservationStatement observationStatement
            : observationStatements) {
            var bloodPressureText = observationStatement.getPertinentInformation().stream()
                .filter(this::pertinentInformationHasText)
                .map(RCMRMT030101UKPertinentInformation02.class::cast)
                .map(RCMRMT030101UKPertinentInformation02::getPertinentAnnotation)
                .map(RCMRMT030101UKAnnotation::getText)
                .map(text -> {

                    var code = extractSnomedCode(observationStatement.getCode());

                    if (code.isPresent()) {
                        if (isSystolicBloodPressure(code.orElseThrow())) {
                            return SYSTOLIC_NOTE + text + StringUtils.SPACE;
                        }
                        if (isDiastolicBloodPressure(code.orElseThrow())) {
                            return DIASTOLIC_NOTE + text + StringUtils.SPACE;
                        }
                    }
                    return StringUtils.EMPTY;
                })
                .collect(Collectors.joining());

            if (StringUtils.isNotEmpty(bloodPressureText)) {
                stringBuilder.append(bloodPressureText);
            }
        }

        if (!narrativeStatements.isEmpty()) {
            stringBuilder.append(BP_NOTE);
            for (RCMRMT030101UKNarrativeStatement narrativeStatement
                : narrativeStatements) {
                stringBuilder.append(narrativeStatement.getText()).append(StringUtils.SPACE);
            }
        }

        return stringBuilder.toString().trim();
    }

    private boolean pertinentInformationHasText(org.hl7.v3.RCMRMT030101UKPertinentInformation02 pertinentInformation) {
        return pertinentInformation != null && pertinentInformation.getPertinentAnnotation() != null
            && StringUtils.isNotEmpty(pertinentInformation.getPertinentAnnotation().getText());
    }

    private List<RCMRMT030101UKObservationStatement> getObservationStatementsFromCompoundStatement(
        RCMRMT030101UKCompoundStatement compoundStatement) {

        return compoundStatement.getComponent().stream()
            .map(RCMRMT030101UKComponent02::getObservationStatement)
            .filter(Objects::nonNull)
            .toList();
    }

    private List<RCMRMT030101UKNarrativeStatement> getNarrativeStatementsFromCompoundStatement(
        RCMRMT030101UKCompoundStatement compoundStatement) {
        return compoundStatement.getComponent().stream()
            .map(RCMRMT030101UKComponent02::getNarrativeStatement)
            .filter(Objects::nonNull)
            .toList();
    }

    private void addEffective(Observation observation, Object effective) {
        if (effective instanceof DateTimeType dateTimeType) {
            observation.setEffective(dateTimeType);
        } else if (effective instanceof Period period) {
            observation.setEffective(period);
        }
    }
}
