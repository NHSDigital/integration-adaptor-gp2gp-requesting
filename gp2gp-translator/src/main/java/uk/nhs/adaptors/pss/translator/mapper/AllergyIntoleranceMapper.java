package uk.nhs.adaptors.pss.translator.mapper;

import static org.hl7.fhir.dstu3.model.AllergyIntolerance.AllergyIntoleranceCategory.ENVIRONMENT;
import static org.hl7.fhir.dstu3.model.AllergyIntolerance.AllergyIntoleranceCategory.MEDICATION;
import static org.hl7.fhir.dstu3.model.AllergyIntolerance.AllergyIntoleranceClinicalStatus.ACTIVE;
import static org.hl7.fhir.dstu3.model.AllergyIntolerance.AllergyIntoleranceVerificationStatus.UNCONFIRMED;

import static uk.nhs.adaptors.pss.translator.util.CompoundStatementResourceExtractors.extractAllCompoundStatements;
import static uk.nhs.adaptors.pss.translator.util.DateFormatUtil.parseToDateTimeType;
import static uk.nhs.adaptors.pss.translator.util.ParticipantReferenceUtil.fetchRecorderAndAsserter;
import static uk.nhs.adaptors.pss.translator.util.ParticipantReferenceUtil.getParticipantReference;
import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.buildIdentifier;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.AllergyIntolerance;
import org.hl7.fhir.dstu3.model.Annotation;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.v3.CD;
import org.hl7.v3.CR;
import org.hl7.v3.RCMRMT030101UKComponent02;
import org.hl7.v3.RCMRMT030101UKCompoundStatement;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKObservationStatement;
import org.hl7.v3.TS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import uk.nhs.adaptors.pss.translator.service.ConfidentialityService;
import uk.nhs.adaptors.pss.translator.util.DegradedCodeableConcepts;
import uk.nhs.adaptors.pss.translator.util.ResourceFilterUtil;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AllergyIntoleranceMapper extends AbstractMapper<AllergyIntolerance> {
    private static final String DRUG_ALLERGY_CODE = "14L..00";
    private static final String NON_DRUG_ALLERGY_CODE = "SN53.00";
    // the meta profile is pre-processed with the url so only the final section is required here
    private static final String META_PROFILE = "AllergyIntolerance-1";
    private static final String ENCOUNTER_URL = "http://hl7.org/fhir/StructureDefinition/encounter-associatedEncounter";
    private static final String EGTON_CODE_SYSTEM = "2.16.840.1.113883.2.1.6.3";
    private static final String ALLERGY_TERM_TEXT = "H/O: drug allergy";
    private static final String ALLERGY_NOTE = "Allergy Code: %s";
    public static final String EPISODICITY_NOTE = "Episodicity : %s";
    public static final String ASSERTER = "asserter";
    public static final String RECORDER = "recorder";

    private final CodeableConceptMapper codeableConceptMapper;
    private final ConfidentialityService confidentialityService;

    @Override
    public List<AllergyIntolerance> mapResources(RCMRMT030101UKEhrExtract ehrExtract, Patient patient, List<Encounter> encounters,
                                                 String practiceCode) {
        return mapEhrExtractToFhirResource(
                ehrExtract,
                (extract, composition, component) -> extractAllCompoundStatements(component)
                        .filter(Objects::nonNull)
                        .filter(ResourceFilterUtil::isAllergyIntolerance)
                        .map(compoundStatement -> mapAllergyIntolerance(
                            composition,
                                compoundStatement,
                            practiceCode,
                                encounters,
                                patient))
        ).toList();
    }

    private AllergyIntolerance mapAllergyIntolerance(RCMRMT030101UKEhrComposition ehrComposition,
                                                     RCMRMT030101UKCompoundStatement compoundStatement,
                                                     String practiceCode,
                                                     List<Encounter> encounters,
                                                     Patient patient) {

        var allergyIntolerance = new AllergyIntolerance();
        var observationStatement = extractObservationStatement(compoundStatement);
        var id = observationStatement.getId().getRoot();

        final Meta meta = confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            observationStatement.getConfidentialityCode());

        initializeAllergyIntolerance(allergyIntolerance, compoundStatement, ehrComposition, patient, practiceCode, id, meta);
        buildAdditionalFields(allergyIntolerance, compoundStatement, ehrComposition, encounters, observationStatement);

        return allergyIntolerance;
    }

    private void initializeAllergyIntolerance(
        AllergyIntolerance allergyIntolerance,
        RCMRMT030101UKCompoundStatement compoundStatement,
        RCMRMT030101UKEhrComposition ehrComposition,
        Patient patient,
        String practiceCode,
        String id,
        Meta meta) {

        allergyIntolerance
            .addCategory(getCategory(compoundStatement))
            .setAssertedDateElement(getAssertedDateElement(compoundStatement.getAvailabilityTime(), ehrComposition))
            .setPatient(new Reference(patient))
            .setClinicalStatus(ACTIVE)
            .setVerificationStatus(UNCONFIRMED)
            .addIdentifier(buildIdentifier(id, practiceCode))
            .setMeta(meta)
            .setId(id);
    }

    private void buildAdditionalFields(
        AllergyIntolerance allergyIntolerance,
        RCMRMT030101UKCompoundStatement compoundStatement,
        RCMRMT030101UKEhrComposition ehrComposition,
        List<Encounter> encounters,
        RCMRMT030101UKObservationStatement observationStatement) {

        buildOnset(compoundStatement, allergyIntolerance);
        buildParticipantReferences(ehrComposition, compoundStatement, allergyIntolerance);
        buildExtension(ehrComposition, encounters, allergyIntolerance);

        var codeableConceptFromCode = codeableConceptMapper.mapToCodeableConcept(observationStatement.getCode());
        var codeableConceptFromValue = getCodeableConceptFromNonEgtonCodeValue(observationStatement);
        var compoundCode = compoundStatement.getCode().getCode();

        buildCode(allergyIntolerance, observationStatement, compoundCode, codeableConceptFromCode, codeableConceptFromValue);
        buildNote(allergyIntolerance, observationStatement, compoundCode, codeableConceptFromCode, codeableConceptFromValue);
    }

    private void buildParticipantReferences(RCMRMT030101UKEhrComposition ehrComposition,
                                            RCMRMT030101UKCompoundStatement compoundStatement,
                                            AllergyIntolerance allergyIntolerance) {

        var recorderAndAsserter = fetchRecorderAndAsserter(ehrComposition);

        if (recorderAndAsserter.get(RECORDER).isPresent() && recorderAndAsserter.get(ASSERTER).isPresent()) {
            allergyIntolerance
                    .setRecorder(recorderAndAsserter.get(RECORDER).get())
                    .setAsserter(recorderAndAsserter.get(ASSERTER).get());
        } else {
            var practitioner = Optional.ofNullable(getParticipantReference(
                    compoundStatement.getParticipant(),
                    ehrComposition));

            practitioner.ifPresent(reference -> allergyIntolerance
                        .setRecorder(reference)
                        .setAsserter(reference));
        }
    }

    private void buildExtension(RCMRMT030101UKEhrComposition ehrComposition, List<Encounter> encounters,
        AllergyIntolerance allergyIntolerance) {

        encounters
            .stream()
            .filter(encounter -> encounter.getId().equals(ehrComposition.getId().getRoot()))
            .map(Reference::new)
            .findFirst()
            .ifPresent(reference -> allergyIntolerance.addExtension(new Extension(ENCOUNTER_URL, reference)));
    }

    private AllergyIntolerance.AllergyIntoleranceCategory getCategory(RCMRMT030101UKCompoundStatement compoundStatement) {

        return compoundStatement.getCode().getCode().equals(DRUG_ALLERGY_CODE)
                ? MEDICATION
                : ENVIRONMENT;
    }

    private CodeableConcept getCodeableConceptFromNonEgtonCodeValue(RCMRMT030101UKObservationStatement observationStatement) {
        if (observationStatement.hasValue()
            && observationStatement.getValue() instanceof CD value
            && !EGTON_CODE_SYSTEM.equals(value.getCodeSystem())) {

            return codeableConceptMapper.mapToCodeableConcept(value);
        }
        return null;
    }

    private void buildCode(AllergyIntolerance allergyIntolerance,
                           RCMRMT030101UKObservationStatement observationStatement,
                           String compoundCode,
                           CodeableConcept codeableConceptFromCode,
                           CodeableConcept codeableConceptFromValue
                           ) {

        if (NON_DRUG_ALLERGY_CODE.equals(compoundCode)) {
            buildNonDrugAllergyCode(allergyIntolerance, codeableConceptFromCode);
        }

        if (DRUG_ALLERGY_CODE.equals(compoundCode)) {
            buildDrugAllergyCode(allergyIntolerance, codeableConceptFromCode, codeableConceptFromValue);
        }

        buildAllergyIntoleranceText(observationStatement, allergyIntolerance);
    }

    private void buildNonDrugAllergyCode(AllergyIntolerance allergyIntolerance,
                                         CodeableConcept codeableConceptFromCode) {

        if (codeableConceptFromCode == null) {
            return;
        }

        allergyIntolerance.setCode(codeableConceptFromCode);

        DegradedCodeableConcepts.addDegradedEntryIfRequired(
                allergyIntolerance.getCode(),
                DegradedCodeableConcepts.DEGRADED_NON_DRUG_ALLERGY);
    }

    private void buildDrugAllergyCode(AllergyIntolerance allergyIntolerance,
                                      CodeableConcept codeableConceptFromCode,
                                      CodeableConcept codeableConceptFromValue) {

        if (codeableConceptFromValue == null && codeableConceptFromCode == null) {
            return;
        }

        allergyIntolerance.setCode(Objects.requireNonNullElse(
                codeableConceptFromValue,
                codeableConceptFromCode));

        if (allergyIntolerance.getCode() == null) {
            return;
        }

        DegradedCodeableConcepts.addDegradedEntryIfRequired(
                allergyIntolerance.getCode(),
                DegradedCodeableConcepts.DEGRADED_DRUG_ALLERGY);
    }

    private void buildOnset(RCMRMT030101UKCompoundStatement compoundStatement, AllergyIntolerance allergyIntolerance) {

        var effectiveTime = compoundStatement.getEffectiveTime();
        var availabilityTime = compoundStatement.getAvailabilityTime();

        if (effectiveTime.hasLow()) {
            allergyIntolerance.setOnset(parseToDateTimeType(effectiveTime.getLow().getValue()));
        } else if (effectiveTime.hasCenter()) {
            allergyIntolerance.setOnset(parseToDateTimeType(effectiveTime.getCenter().getValue()));
        } else if (availabilityTime.hasValue()) {
            allergyIntolerance.setOnset(parseToDateTimeType(availabilityTime.getValue()));
        }
    }

    private DateTimeType getAssertedDateElement(TS availabilityTime,
        RCMRMT030101UKEhrComposition ehrComposition) {

        if (availabilityTime != null && availabilityTime.hasValue()) {
            return parseToDateTimeType(availabilityTime.getValue());
        } else {
            if (ehrComposition.getAvailabilityTime() != null && ehrComposition.getAvailabilityTime().hasValue()) {
                return parseToDateTimeType(ehrComposition.getAvailabilityTime().getValue());
            } else if (ehrComposition.getAuthor() != null
                    && ehrComposition.getAuthor().getTime() != null
                    && ehrComposition.getAuthor().getTime().hasValue()) {
                return parseToDateTimeType(ehrComposition.getAuthor().getTime().getValue());
            }
        }

        return null;
    }

    private void buildAllergyIntoleranceText(
            RCMRMT030101UKObservationStatement observationStatement,
            AllergyIntolerance allergyIntolerance) {

        if (allergyIntolerance.getCode() != null) {
            if (observationStatement.hasValue() && observationStatement.getValue() instanceof CD value) {
                var valueDisplayName = value.getDisplayName();
                if (!StringUtils.isEmpty(valueDisplayName)) {
                    allergyIntolerance.getCode().setText(valueDisplayName);
                    return;
                }
            }
            var originalTextFromCode = observationStatement.getCode().getOriginalText();
            if (!StringUtils.isEmpty(originalTextFromCode)) {
                allergyIntolerance.getCode().setText(originalTextFromCode);
            }
        }
    }

    private void buildNote(AllergyIntolerance allergyIntolerance,
                           RCMRMT030101UKObservationStatement observationStatement,
                           String compoundCode,
                           CodeableConcept codeableConceptFromCode,
                           CodeableConcept codeableConceptFromValue) {

        var episodicityAnnotations = getEpisodicityAnnotations(observationStatement);
        var pertinentInformationAnnotations = getPertinentInformationAnnotations(observationStatement);
        var allergyAnnotation = getAllergyAnnotation(compoundCode, codeableConceptFromCode, codeableConceptFromValue);

        var allergyIntoleranceNotes =
            Stream.of(episodicityAnnotations, pertinentInformationAnnotations, allergyAnnotation)
                    .flatMap(ain -> ain);

        allergyIntolerance.setNote(allergyIntoleranceNotes.toList());
    }

    private Stream<Annotation> getEpisodicityAnnotations(RCMRMT030101UKObservationStatement observationStatement) {

        return observationStatement
                .getCode()
                .getQualifier()
                .stream()
                .map(this::buildEpisodicityText)
                .filter(Objects::nonNull)
                .map(et -> new Annotation().setText(EPISODICITY_NOTE.formatted(et)));
    }

    private String buildEpisodicityText(CR qualifier) {
        var qualifierName = qualifier.getName();

        if (qualifierName == null) {
            return null;
        }

        var text = "code=" + qualifierName.getCode()
                + ", displayName=" + qualifierName.getDisplayName();

        if (qualifierName.hasOriginalText()) {
            return text + ", originalText=" + qualifierName.getOriginalText();
        }

        return text;
    }

    private Stream<Annotation> getPertinentInformationAnnotations(
            RCMRMT030101UKObservationStatement observationStatement) {

        return observationStatement
                .getPertinentInformation()
                .stream()
                .map(pertinentInformation -> pertinentInformation.getPertinentAnnotation().getText())
                .map(ep -> new Annotation().setText(ep));
    }

    private Stream<Annotation> getAllergyAnnotation(
            String compoundCode,
            CodeableConcept codeableConceptFromCode,
            CodeableConcept codeableConceptFromValue) {

        if (!DRUG_ALLERGY_CODE.equals(compoundCode)
            || codeableConceptFromCode == null
            || codeableConceptFromValue == null) {
            return Stream.of();
        }

        var codeDisplayName = codeableConceptFromCode.getCodingFirstRep().getDisplay();
        if (codeDisplayName != null
            && !ALLERGY_TERM_TEXT.equals(codeDisplayName)
            && !codeDisplayName.equals(codeableConceptFromValue.getCodingFirstRep().getDisplay())) {

            return Stream.of(new Annotation().setText(ALLERGY_NOTE.formatted(codeDisplayName)));
        }
        return null;
    }

    private RCMRMT030101UKObservationStatement extractObservationStatement(RCMRMT030101UKCompoundStatement compoundStatement) {

        return compoundStatement.getComponent()
                .stream()
                .map(RCMRMT030101UKComponent02::getObservationStatement)
                .findFirst()
                .orElse(null);
    }
}
