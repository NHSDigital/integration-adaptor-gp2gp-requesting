package uk.nhs.adaptors.pss.translator.mapper;

import static uk.nhs.adaptors.pss.translator.util.CompoundStatementResourceExtractors.extractAllObservationStatements;
import static uk.nhs.adaptors.pss.translator.util.ParticipantReferenceUtil.getParticipantReference;
import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.buildIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.Annotation;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.Immunization;
import org.hl7.fhir.dstu3.model.Immunization.ImmunizationPractitionerComponent;
import org.hl7.fhir.dstu3.model.Immunization.ImmunizationStatus;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.StringType;
import org.hl7.v3.II;
import org.hl7.v3.RCMRMT030101UKAnnotation;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKObservationStatement;
import org.hl7.v3.RCMRMT030101UKPertinentInformation02;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import uk.nhs.adaptors.common.util.CodeableConceptUtils;
import uk.nhs.adaptors.pss.translator.service.ConfidentialityService;
import uk.nhs.adaptors.pss.translator.util.DatabaseImmunizationChecker;
import uk.nhs.adaptors.pss.translator.util.DateFormatUtil;
import uk.nhs.adaptors.pss.translator.util.ParticipantReferenceUtil;

@Service
@AllArgsConstructor
public class ImmunizationMapper extends AbstractMapper<Immunization> {

    private static final String META_PROFILE = "Immunization-1";
    private static final String VACCINE_PROCEDURE_URL = "https://fhir.hl7.org.uk/STU3/StructureDefinition/Extension-CareConnect-VaccinationProcedure-1";
    private static final String END_DATE_PREFIX = "End Date: ";
    private static final String RECORDED_DATE_EXTENSION_URL = "https://fhir.hl7.org.uk/STU3/StructureDefinition/Extension-CareConnect"
        + "-DateRecorded-1";
    private static final String IMMUNIZATION_ROLE_URL = "http://hl7.org/fhir/stu3/valueset-immunization-role.html";
    public static final String RECORDER = "recorder";

    private CodeableConceptMapper codeableConceptMapper;
    private DatabaseImmunizationChecker immunizationChecker;
    private final ConfidentialityService confidentialityService;

    public List<Immunization> mapResources(RCMRMT030101UKEhrExtract ehrExtract, Patient patientResource,
                                           List<Encounter> encounterList, String practiceCode) {
        return mapEhrExtractToFhirResource(ehrExtract, (extract, composition, component) ->
            extractAllObservationStatements(component)
                .filter(Objects::nonNull)
                .filter(this::isImmunization)
                .map(observationStatement ->
                    mapImmunization(composition, observationStatement, patientResource, encounterList, practiceCode)))
            .toList();
    }

    private boolean isImmunization(RCMRMT030101UKObservationStatement observationStatement) {

        if (observationStatement.hasCode() && observationStatement.getCode().hasCode()) {
            return immunizationChecker.isImmunization(observationStatement);
        }
        return false;
    }

    private Immunization mapImmunization(RCMRMT030101UKEhrComposition ehrComposition,
                                         RCMRMT030101UKObservationStatement observationStatement, Patient patientResource,
                                         List<Encounter> encounterList, String practiceCode) {

        var immunization = initializeImmunization(observationStatement,
                                                  ehrComposition,
                                                  encounterList,
                                                  practiceCode,
                                                  patientResource);

        setPractiotionerAndAsserter(ehrComposition, observationStatement, immunization);
        buildNote(observationStatement).forEach(immunization::addNote);
        setDateFields(immunization, observationStatement);
        setVaccineCode(immunization);

        return immunization;
    }

    private void setVaccineCode(Immunization immunization) {
        // we never receive a vaccine code but we have to include a unk code to make it FHIR compliant
        var unkCoding = new Coding().setCode("UNK").setSystem("http://hl7.org/fhir/v3/NullFlavor");
        var codingList = new ArrayList<Coding>();
        codingList.add(unkCoding);
        immunization.setVaccineCode(new CodeableConcept().setCoding(codingList));
    }

    private static void setPractiotionerAndAsserter(RCMRMT030101UKEhrComposition ehrComposition,
                                                    RCMRMT030101UKObservationStatement observationStatement,
                                                    Immunization immunization) {

        var recorderAndAsserter = ParticipantReferenceUtil.fetchRecorderAndAsserter(ehrComposition);
        ImmunizationPractitionerComponent recorder = null;
        ImmunizationPractitionerComponent asserter = null;

        var practitioner = Optional.ofNullable(getParticipantReference(
            observationStatement.getParticipant(),
            ehrComposition));
        if (practitioner.isPresent()) {
            asserter = getImmunizationPractitioner(practitioner.get(), "");
            if (recorderAndAsserter.get(RECORDER).isPresent()
                && !recorderAndAsserter.get(RECORDER).get().getReference().equals(practitioner.get().getReference())) {
                recorder = getImmunizationPractitioner(recorderAndAsserter.get(RECORDER).get(), "EP");
            }
        }

        immunization
            .addPractitioner(recorder)
            .addPractitioner(asserter);
    }

    private Immunization initializeImmunization(RCMRMT030101UKObservationStatement observationStatement,
                                                RCMRMT030101UKEhrComposition ehrComposition,
                                                List<Encounter> encounterList,
                                                String practiceCode,
                                                Patient patientResource) {

        Immunization immunization = new Immunization();
        var id = observationStatement.getId().getRoot();

        var encounter = getEncounterReference(encounterList, ehrComposition.getId());

        var meta = confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            observationStatement.getConfidentialityCode());

        immunization
            .setEncounter(encounter)
            .addIdentifier(buildIdentifier(id, practiceCode))
            .setStatus(ImmunizationStatus.COMPLETED)
            .setPatient(new Reference(patientResource))
            .setNotGiven(false)
            .setPrimarySource(true)
            .addExtension(createVaccineProcedureExtension(observationStatement))
            .addExtension(createRecordedTimeExtension(ehrComposition))
            .setId(id)
            .setMeta(meta);

        return immunization;
    }

    @NotNull
    private static ImmunizationPractitionerComponent getImmunizationPractitioner(Reference practitionerReference, String role) {

        ImmunizationPractitionerComponent recorder = new ImmunizationPractitionerComponent(practitionerReference);
        if (StringUtils.isNotEmpty(role)) {
            var epCodeableConceptRole = CodeableConceptUtils.createCodeableConcept(role, IMMUNIZATION_ROLE_URL, null);
            recorder.setRole(epCodeableConceptRole);
        }

        return recorder;
    }

    private Reference getEncounterReference(List<Encounter> encounterList, II ehrCompositionId) {
        if (ehrCompositionId != null) {
            var matchingEncounter = encounterList.stream()
                .filter(encounter -> hasMatchingId(encounter.getId(), ehrCompositionId))
                .findFirst();

            if (matchingEncounter.isPresent()) {
                return new Reference(matchingEncounter.get());
            }
        }

        return null;
    }

    private boolean hasMatchingId(String encounterId, II ehrCompositionId) {
        return encounterId.equals(ehrCompositionId.getRoot());
    }

    private Extension createRecordedTimeExtension(RCMRMT030101UKEhrComposition ehrComposition) {

        var extension = new Extension();
        extension.setUrl(RECORDED_DATE_EXTENSION_URL);

        if (ehrComposition.getAuthor() != null) {
            return extension
                .setValue(new DateTimeType(
                    DateFormatUtil.parseToDateTimeType(ehrComposition.getAuthor().getTime().getValue()).asStringValue()));
        } else if (ehrComposition.getEffectiveTime() != null && ehrComposition.getAvailabilityTime().getNullFlavor() == null) {
            return extension
                .setValue(new DateTimeType(
                    DateFormatUtil.parseToDateTimeType(ehrComposition.getAvailabilityTime().getValue()).asStringValue()));
        }

        return null;
    }

    private Extension createVaccineProcedureExtension(RCMRMT030101UKObservationStatement observationStatement) {

        return new Extension()
                    .setUrl(VACCINE_PROCEDURE_URL)
                    .setValue(codeableConceptMapper.mapToCodeableConcept(observationStatement.getCode()));
    }

    private Annotation buildAnnotation(String annotation) {
        return new Annotation(new StringType(annotation));
    }

    private void setDateFields(Immunization immunization, RCMRMT030101UKObservationStatement observationStatement) {

        if (observationStatement.hasEffectiveTime()) {
            var effectiveTime = observationStatement.getEffectiveTime();

            if (effectiveTime.hasHigh() && !effectiveTime.hasCenter()) {
                immunization.addNote(buildAnnotation(END_DATE_PREFIX
                    + DateFormatUtil.parseToDateTimeType(effectiveTime.getHigh().getValue()).asStringValue()));
            }

            if (effectiveTime.hasCenter()) {
                immunization.setDateElement(DateFormatUtil.parseToDateTimeType(effectiveTime.getCenter().getValue()));
            } else if (effectiveTime.hasLow()) {
                immunization.setDateElement(DateFormatUtil.parseToDateTimeType(effectiveTime.getLow().getValue()));
            } else if (observationStatement.hasAvailabilityTime() && observationStatement.getAvailabilityTime().hasValue()) {
                immunization.setDateElement(DateFormatUtil.parseToDateTimeType(observationStatement.getAvailabilityTime().getValue()));
            }
        }
    }

    private List<Annotation> buildNote(RCMRMT030101UKObservationStatement observationStatement) {

        return observationStatement
            .getPertinentInformation()
            .stream()
            .map(RCMRMT030101UKPertinentInformation02::getPertinentAnnotation)
            .map(RCMRMT030101UKAnnotation::getText)
            .map(this::buildAnnotation)
            .collect(Collectors.toList());
    }
}
