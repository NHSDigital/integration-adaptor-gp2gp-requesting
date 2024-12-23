package uk.nhs.adaptors.pss.translator.mapper;

import static org.hl7.fhir.dstu3.model.Observation.ObservationStatus.FINAL;

import static uk.nhs.adaptors.pss.translator.util.CompoundStatementResourceExtractors.extractAllNonBloodPressureNarrativeStatements;
import static uk.nhs.adaptors.pss.translator.util.ResourceFilterUtil.isDocumentReference;
import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.addContextToObservation;
import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.buildIdentifier;
import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.generateMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.InstantType;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKNarrativeStatement;
import org.hl7.v3.TS;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import uk.nhs.adaptors.common.util.CodeableConceptUtils;
import uk.nhs.adaptors.pss.translator.util.DateFormatUtil;
import uk.nhs.adaptors.pss.translator.util.ParticipantReferenceUtil;

@Service
@AllArgsConstructor
public class ObservationCommentMapper extends AbstractMapper<Observation> {

    private static final String META_URL = "Observation-1";
    private static final String CODING_SYSTEM = "http://snomed.info/sct";
    private static final String CODING_CODE = "37331000000100";
    private static final String CODING_DISPLAY = "Comment note";

    public ArrayList<Observation> mapResources(RCMRMT030101UKEhrExtract ehrExtract, Patient patient, List<Encounter> encounters,
                                               String practiceCode) {

        return mapEhrExtractToFhirResource(ehrExtract, (extract, composition, component) ->
            extractAllNonBloodPressureNarrativeStatements(component)
                .filter(Objects::nonNull)
                .filter(narrativeStatement -> !isDocumentReference(narrativeStatement))
                .map(narrativeStatement -> mapObservation(composition, narrativeStatement, patient, encounters, practiceCode)))
                .collect((Collectors.toCollection(ArrayList::new)));
    }

    private Observation mapObservation(RCMRMT030101UKEhrComposition ehrComposition,
                                       RCMRMT030101UKNarrativeStatement narrativeStatement, Patient patient, List<Encounter> encounters,
                                       String practiceCode) {

        var narrativeStatementId = narrativeStatement.getId();
        var observation = new Observation();
        observation.setId(narrativeStatement.getId().getRoot());
        observation.setMeta(generateMeta(META_URL));
        observation.setStatus(FINAL);
        observation.setSubject(new Reference(patient));
        observation.setIssuedElement(createIssued(narrativeStatement));
        observation.setCode(createCodeableConcept());
        observation.addPerformer(createPerformer(ehrComposition, narrativeStatement));
        observation.addIdentifier(buildIdentifier(narrativeStatementId.getRoot(), practiceCode));

        setObservationEffective(observation, narrativeStatement.getAvailabilityTime());
        setObservationComment(observation, narrativeStatement.getText());

        addContextToObservation(observation, encounters, ehrComposition);
        return observation;
    }

    private void setObservationEffective(Observation observation, TS availabilityTime) {
        if (availabilityTime.hasValue()) {
            observation.setEffective(DateFormatUtil.parseToDateTimeType(availabilityTime.getValue()));
        }
    }

    private void setObservationComment(Observation observation, String text) {
        if (!text.isBlank()) {
            observation.setComment(text.trim());
        }
    }

    private InstantType createIssued(RCMRMT030101UKNarrativeStatement narrativeStatement) {
        if (narrativeStatement.hasAvailabilityTime() && narrativeStatement.getAvailabilityTime().hasValue()) {
            return DateFormatUtil.parseToInstantType(
                narrativeStatement.getAvailabilityTime().getValue()
            );
        }
        return null;
    }

    private Reference createPerformer(RCMRMT030101UKEhrComposition composition, RCMRMT030101UKNarrativeStatement narrativeStatement) {
        return ParticipantReferenceUtil.getParticipantReference(narrativeStatement.getParticipant(), composition);
    }

    private CodeableConcept createCodeableConcept() {
        return CodeableConceptUtils.createCodeableConcept(CODING_CODE, CODING_SYSTEM, CODING_DISPLAY);
    }
}
