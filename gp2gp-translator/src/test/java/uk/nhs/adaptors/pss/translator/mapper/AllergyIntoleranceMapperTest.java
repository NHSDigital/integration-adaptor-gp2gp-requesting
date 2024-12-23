package uk.nhs.adaptors.pss.translator.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hl7.fhir.dstu3.model.AllergyIntolerance.AllergyIntoleranceCategory.ENVIRONMENT;
import static org.hl7.fhir.dstu3.model.AllergyIntolerance.AllergyIntoleranceCategory.MEDICATION;
import static org.hl7.fhir.dstu3.model.AllergyIntolerance.AllergyIntoleranceClinicalStatus.ACTIVE;
import static org.hl7.fhir.dstu3.model.AllergyIntolerance.AllergyIntoleranceVerificationStatus.UNCONFIRMED;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.util.ResourceUtils.getFile;

import static uk.nhs.adaptors.common.util.CodeableConceptUtils.createCodeableConcept;

import static uk.nhs.adaptors.pss.translator.util.MetaUtil.MetaType.META_WITHOUT_SECURITY;
import static uk.nhs.adaptors.pss.translator.util.MetaUtil.MetaType.META_WITH_SECURITY;
import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.hl7.fhir.dstu3.model.AllergyIntolerance;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.v3.CD;
import org.hl7.v3.CV;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKObservationStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.SneakyThrows;

import uk.nhs.adaptors.pss.translator.util.MetaUtil;
import uk.nhs.adaptors.pss.translator.TestUtility;
import uk.nhs.adaptors.pss.translator.service.ConfidentialityService;
import uk.nhs.adaptors.pss.translator.util.DateFormatUtil;
import uk.nhs.adaptors.pss.translator.util.DegradedCodeableConcepts;

@ExtendWith(MockitoExtension.class)
class AllergyIntoleranceMapperTest {

    private static final String XML_RESOURCES_BASE = "xml/AllergyIntolerance/";
    private static final String COMPOUND_STATEMENT_ROOT_ID = "394559384658936";
    private static final String PRACTISE_CODE = "TESTPRACTISECODE";
    private static final String ENCOUNTER_ID = "62A39454-299F-432E-993E-5A6232B4E099";
    private static final String CODING_DISPLAY_1 = "Ischaemic heart disease";
    private static final String CODING_DISPLAY_2 = "H/O: aspirin allergy";
    private static final String CODING_DISPLAY_3 = "H/O: drug allergy";
    private static final String CODING_DISPLAY_4 = "Coconut oil";
    private static final String PATIENT_ID = "9A5D5A78-1F63-434C-9637-1D7E7843341B";
    private static final String META_PROFILE = "AllergyIntolerance-1";
    private static final String IDENTIFIER_SYSTEM = "https://PSSAdaptor/TESTPRACTISECODE";
    private static final String EPISODICITY_WITH_ORIGINAL_TEXT_NOTE_TEXT =
            "Episodicity : code=303350001, displayName=Ongoing, originalText=Review";
    private static final String EPISODICITY_WITHOUT_ORIGINAL_TEXT_NOTE_TEXT =
            "Episodicity : code=303350001, displayName=Ongoing";
    private static final String PERTINENT_NOTE_TEXT =
            "Reason Ended: Patient reports no subsequent recurrence on same medication Status:"
                    + " Resolved Type: Allergy Criticality: Low Risk Last Occurred: 1978-12-31 Example note text";
    private static final String ALLERGY_NOTE_TEXT = "Allergy Code: " + CODING_DISPLAY_1;
    private static final int THREE = 3;
    private static final String ORIGINAL_TEXT_IN_CODE = "OriginalText from Code";
    private static final String DISPLAY_NAME_IN_VALUE = "Value displayName";
    private static final String MULTILEX_CODE_SYSTEM = "2.16.840.1.113883.2.1.6.4";
    private static final String MULTILEX_COCONUT_OIL = "01142009";
    private static final String SNOMED_CODE_SYSTEM = "2.16.840.1.113883.2.1.3.2.4.15";
    private static final String SNOMED_COCONUT_OIL = "14613911000001107";

    public static final Function<RCMRMT030101UKEhrExtract, RCMRMT030101UKObservationStatement> GET_OBSERVATION_STATEMENT =
        extract -> extract
            .getComponent().getFirst()
            .getEhrFolder()
            .getComponent().getFirst()
            .getEhrComposition()
            .getComponent().getFirst()
            .getCompoundStatement()
            .getComponent().getFirst()
            .getObservationStatement();

    @Mock
    private CodeableConceptMapper codeableConceptMapper;

    @Mock
    private ConfidentialityService confidentialityService;

    @Captor
    private ArgumentCaptor<CD> cdCaptor;

    @InjectMocks
    private AllergyIntoleranceMapper allergyIntoleranceMapper;

    @BeforeEach
    void beforeEach() {
        Mockito.lenient()
            .when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
                any(String.class), any(Optional.class), any(Optional.class)
            )).thenReturn(MetaUtil.getMetaFor(META_WITHOUT_SECURITY, META_PROFILE));
    }

    @Test
    void testGivenDrugAllergyWithAllDataThenAllDataPopulated() {
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("drug-allergy-structure.xml");

        when(codeableConceptMapper.mapToCodeableConcept(any(CD.class)))
            .thenReturn(defaultCodeableConcept())
            .thenReturn(secondaryCodeableConcept());

        final List<AllergyIntolerance> allergyIntolerances = allergyIntoleranceMapper.mapResources(ehrExtract, getPatient(),
            getEncounterList(), PRACTISE_CODE);

        assertThat(allergyIntolerances).hasSize(1);
        final AllergyIntolerance allergyIntolerance = allergyIntolerances.getFirst();

        assertFixedValues(allergyIntolerance);
        assertExtension(allergyIntolerance);

        assertThat(allergyIntolerance.getCategory().getFirst().getValue()).isEqualTo(MEDICATION);
        assertThat(allergyIntolerance.getAssertedDateElement().asStringValue()).isEqualTo("1978-12-31");
        assertThat(allergyIntolerance.getRecorder().getReference()).isEqualTo("Practitioner/2D70F602-6BB1-47E0-B2EC-39912A59787D");
        assertThat(allergyIntolerance.getOnsetDateTimeType().asStringValue())
            .isEqualTo(DateFormatUtil.parseToDateTimeType("19781231").asStringValue());
        assertThat(allergyIntolerance.getAsserter().getReference()).isEqualTo("Practitioner/2D70F602-6BB1-47E0-B2EC-39912A59787D");
        assertThat(allergyIntolerance.getNote().getFirst().getText()).isEqualTo(EPISODICITY_WITH_ORIGINAL_TEXT_NOTE_TEXT);
        assertThat(allergyIntolerance.getNote().get(1).getText()).isEqualTo(PERTINENT_NOTE_TEXT);
        assertThat(allergyIntolerance.getNote().get(2).getText()).isEqualTo(ALLERGY_NOTE_TEXT);
        assertThat(allergyIntolerance.getCode().getCodingFirstRep()).isEqualTo(DegradedCodeableConcepts.DEGRADED_DRUG_ALLERGY);
        assertThat(allergyIntolerance.getCode().getCoding().get(1).getDisplay()).isEqualTo(CODING_DISPLAY_2);
        verifyConfidentialityServiceCalled(1, Optional.empty(), Optional.empty());
    }

    @Test
    void testGivenAuthorAndParticipantThenMapsToRecorderAndAsserterAllergyIntolerance() {
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("allergy-structure-with-valid-author-and-participant2.xml");

        when(codeableConceptMapper.mapToCodeableConcept(any(CD.class)))
            .thenReturn(defaultCodeableConcept())
            .thenReturn(secondaryCodeableConcept());

        final List<AllergyIntolerance> allergyIntolerances = allergyIntoleranceMapper.mapResources(ehrExtract, getPatient(),
            getEncounterList(), PRACTISE_CODE);

        assertEquals(1, allergyIntolerances.size());
        final AllergyIntolerance allergyIntolerance = allergyIntolerances.getFirst();

        assertAll(
            () -> assertExtension(allergyIntolerance),
            () -> assertEquals("Practitioner/9F2ABD26-1682-FDFE-1E88-19673307C67A", allergyIntolerance.getAsserter().getReference()),
            () -> assertEquals("Practitioner/E7E7B550-09EF-BE85-C20F-34598014166C", allergyIntolerance.getRecorder().getReference())
        );
        verifyConfidentialityServiceCalled(1, Optional.empty(), Optional.empty());
    }

    /**
     * At this moment it is not very clear if this is the correct behavior.
     * We haven't seen a supplier send over a HL7 in this form, but we want to specify some behaviour.
     */
    @Test
    void testGivenAuthorAndAutParticipant2AuthorAndRecorderThenPopulatedWithParticipant2() {
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("allergy-structure-with-participant-of-aut-typecode.xml");

        when(codeableConceptMapper.mapToCodeableConcept(any(CD.class)))
            .thenReturn(defaultCodeableConcept())
            .thenReturn(secondaryCodeableConcept());

        final List<AllergyIntolerance> allergyIntolerances = allergyIntoleranceMapper.mapResources(ehrExtract, getPatient(),
            getEncounterList(), PRACTISE_CODE);

        assertEquals(1, allergyIntolerances.size());
        final AllergyIntolerance allergyIntolerance = allergyIntolerances.getFirst();

        assertAll(
            () -> assertExtension(allergyIntolerance),
            () -> assertEquals("Practitioner/9F2ABD26-1682-FDFE-1E88-19673307C67A", allergyIntolerance.getRecorder().getReference()),
            () -> assertEquals("Practitioner/9F2ABD26-1682-FDFE-1E88-19673307C67A", allergyIntolerance.getAsserter().getReference())
        );
        verifyConfidentialityServiceCalled(1, Optional.empty(), Optional.empty());
    }

    /**
     * At this moment it is not very clear if this is the correct behavior with such number of participants.
     * We haven't seen a supplier send over a HL7 in this form, but we want to specify some behaviour.
     */
    @Test
    void testGivenAuthorAndMultipleParticipant2sAndOneAutParticipant2AuthorAndRecorderThenPopulatedWithAuthorAndParticipant2() {
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("allergy-structure-with-author-and-multiple-participants.xml");

        when(codeableConceptMapper.mapToCodeableConcept(any(CD.class)))
            .thenReturn(defaultCodeableConcept())
            .thenReturn(secondaryCodeableConcept());

        final List<AllergyIntolerance> allergyIntolerances = allergyIntoleranceMapper.mapResources(ehrExtract, getPatient(),
            getEncounterList(), PRACTISE_CODE);

        assertEquals(1, allergyIntolerances.size());
        final AllergyIntolerance allergyIntolerance = allergyIntolerances.getFirst();

        assertAll(
            () -> assertExtension(allergyIntolerance),
            () -> assertEquals("Practitioner/9F2ABD26-1682-FDFE-1E88-19673307C67A", allergyIntolerance.getAsserter().getReference()),
            () -> assertEquals("Practitioner/E7E7B550-09EF-BE85-C20F-34598014166C", allergyIntolerance.getRecorder().getReference())
        );
        verifyConfidentialityServiceCalled(1, Optional.empty(), Optional.empty());
    }

    @Test
    void testGivenNonDrugAllergyWithAllDataThenMapsSuccessfully() {
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("non-drug-allergy-structure.xml");

        when(codeableConceptMapper.mapToCodeableConcept(any(CD.class)))
            .thenReturn(defaultCodeableConcept())
            .thenReturn(secondaryCodeableConcept());

        final List<AllergyIntolerance> allergyIntolerances
            = allergyIntoleranceMapper.mapResources(ehrExtract, getPatient(), getEncounterList(), PRACTISE_CODE);

        assertEquals(1, allergyIntolerances.size());
        final AllergyIntolerance allergyIntolerance = allergyIntolerances.getFirst();

        assertFixedValues(allergyIntolerance);

        assertExtension(allergyIntolerance);
        assertEquals(ENVIRONMENT, allergyIntolerance.getCategory().getFirst().getValue());
        assertEquals("1978-12-31", allergyIntolerance.getAssertedDateElement().asStringValue());
        assertEquals("Practitioner/2D70F602-6BB1-47E0-B2EC-39912A59787D", allergyIntolerance.getRecorder().getReference());
        assertEquals(DateFormatUtil.parseToDateTimeType("19781231").asStringValue(),
                                                    allergyIntolerance.getOnsetDateTimeType().asStringValue());
        assertEquals("Practitioner/2D70F602-6BB1-47E0-B2EC-39912A59787D", allergyIntolerance.getAsserter().getReference());
        assertEquals(PERTINENT_NOTE_TEXT, allergyIntolerance.getNote().getFirst().getText());
        assertEquals(DegradedCodeableConcepts.DEGRADED_NON_DRUG_ALLERGY, allergyIntolerance.getCode().getCodingFirstRep());
        assertEquals(CODING_DISPLAY_1, allergyIntolerance.getCode().getCoding().get(1).getDisplay());
        verifyConfidentialityServiceCalled(1, Optional.empty(), Optional.empty());
    }

    @Test
    void testGivenCompoundStatementCodeOfNonDrugAllergyCodeThenSetsCodeToTransferDegradedNonDrugAllergy() {
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("degraded-non-drug-allergy-structure.xml");

        when(codeableConceptMapper.mapToCodeableConcept(any(CD.class)))
            .thenReturn(new CodeableConcept());

        final List<AllergyIntolerance> allergyIntolerances = allergyIntoleranceMapper.mapResources(ehrExtract, getPatient(),
            getEncounterList(), PRACTISE_CODE);

        assertThat(allergyIntolerances).hasSize(1);
        final AllergyIntolerance allergyIntolerance = allergyIntolerances.getFirst();

        assertThat(allergyIntolerance.getCode().getCodingFirstRep())
            .isEqualTo(DegradedCodeableConcepts.DEGRADED_NON_DRUG_ALLERGY);
        verifyConfidentialityServiceCalled(1, Optional.empty(), Optional.empty());
    }

    @Test
    void testGivenCompoundStatementCodeOfDrugAllergyCodeThenSetsCodeToTransferDegradedDrugAllergy() {
        when(codeableConceptMapper.mapToCodeableConcept(any(CD.class)))
                .thenReturn(nonSnomedCodeableConcept());

        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("degraded-drug-allergy-structure.xml");
        final List<AllergyIntolerance> allergyIntolerances = allergyIntoleranceMapper.mapResources(ehrExtract, getPatient(),
                getEncounterList(), PRACTISE_CODE);

        assertThat(allergyIntolerances).hasSize(1);
        final AllergyIntolerance allergyIntolerance = allergyIntolerances.getFirst();

        assertThat(allergyIntolerance.getCode().getCodingFirstRep())
            .isEqualTo(DegradedCodeableConcepts.DEGRADED_DRUG_ALLERGY);

        verifyConfidentialityServiceCalled(1, Optional.empty(), Optional.empty());
    }

    @Test
    void testGivenAllergyWithOriginalTextAndNoValueThenMapsCodingTextFromCodeOriginalText() {
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("allergy-structure-with-original-text-in-code.xml");

        when(codeableConceptMapper.mapToCodeableConcept(any(CD.class)))
            .thenReturn(defaultCodeableConcept())
            .thenReturn(secondaryCodeableConcept());

        final List<AllergyIntolerance> allergyIntolerances = allergyIntoleranceMapper.mapResources(ehrExtract, getPatient(),
            getEncounterList(), PRACTISE_CODE);

        assertThat(allergyIntolerances).hasSize(1);
        final AllergyIntolerance allergyIntolerance = allergyIntolerances.getFirst();

        assertFixedValues(allergyIntolerance);

        assertThat(allergyIntolerance.getCode().getText()).isEqualTo(ORIGINAL_TEXT_IN_CODE);
        verifyConfidentialityServiceCalled(1, Optional.empty(), Optional.empty());
    }

    @Test
    void testGivenAllergyWithNoOptionalData() {
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("allergy-structure-with-optional-data.xml");

        when(codeableConceptMapper.mapToCodeableConcept(any(CD.class)))
            .thenReturn(defaultCodeableConcept());

        final List<AllergyIntolerance> allergyIntolerances = allergyIntoleranceMapper.mapResources(ehrExtract, getPatient(),
            getEncounterList(), PRACTISE_CODE);

        assertThat(allergyIntolerances).hasSize(1);
        final AllergyIntolerance allergyIntolerance = allergyIntolerances.getFirst();
        assertFixedValues(allergyIntolerance);
        assertThat(allergyIntolerance.getCode().getCodingFirstRep()).isEqualTo(DegradedCodeableConcepts.DEGRADED_DRUG_ALLERGY);
        assertThat(allergyIntolerance.getCode().getCoding().get(1).getDisplay()).isEqualTo(CODING_DISPLAY_1);
        assertThat(allergyIntolerance.getAssertedDateElement().asStringValue()).isEqualTo("2019-07-08T13:35:00+00:00");
        assertNull(allergyIntolerance.getRecorder().getReference()); // this is added later in the UnknownPractitionerHandler
        assertNull(allergyIntolerance.getAsserter().getReference());
        assertNull(allergyIntolerance.getOnset());
        assertThat(allergyIntolerance.getNote()).isEmpty();
        verifyConfidentialityServiceCalled(1, Optional.empty(), Optional.empty());
    }

    @Test
    void testGivenUnkAvailabilityTimeThenAssertedDateIsAuthorTime() {
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("allergy-structure-with-asserted-date-fallback.xml");

        when(codeableConceptMapper.mapToCodeableConcept(any(CD.class)))
            .thenReturn(defaultCodeableConcept());

        final List<AllergyIntolerance> allergyIntolerances = allergyIntoleranceMapper.mapResources(ehrExtract, getPatient(),
            getEncounterList(), PRACTISE_CODE);

        assertThat(allergyIntolerances).hasSize(1);
        final AllergyIntolerance allergyIntolerance = allergyIntolerances.getFirst();

        assertFixedValues(allergyIntolerance);

        assertThat(allergyIntolerance.getAssertedDateElement().asStringValue()).isEqualTo("2010-02-09T12:31:51+00:00");
        verifyConfidentialityServiceCalled(1, Optional.empty(), Optional.empty());
    }

    @Test
    void testGivenMultipleAllergiesThenExpectAllToBePresent() {
        final RCMRMT030101UKEhrExtract ehrExtract =
            unmarshallEhrExtract("allergy-structure-with-multiple-allergy.xml");

        when(codeableConceptMapper.mapToCodeableConcept(any(CD.class)))
            .thenReturn(defaultCodeableConcept());

        final List<AllergyIntolerance> allergyIntolerances
            = allergyIntoleranceMapper.mapResources(ehrExtract, getPatient(), getEncounterList(), PRACTISE_CODE);

        assertEquals(THREE, allergyIntolerances.size());
        verifyConfidentialityServiceCalled(THREE, Optional.empty(), Optional.empty());
    }

    @Test
    void testGivenAllergyIntoleranceWithNopatConfidentialityCodePresentWithinEhrCompositionExpectMetaSecurityAdded() {
        final Meta stubbedMeta = MetaUtil.getMetaFor(META_WITH_SECURITY, META_PROFILE);
        final RCMRMT030101UKEhrExtract ehrExtract =
            unmarshallEhrExtract("allergy-structure-with-ehr-composition-nopat-confidentiality-code.xml");

        Mockito
            .lenient()
            .when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
                any(String.class), any(Optional.class), any(Optional.class)
            )).thenReturn(stubbedMeta);

        final RCMRMT030101UKEhrComposition ehrComposition = TestUtility.getEhrComposition(ehrExtract);
        final List<AllergyIntolerance> allergyIntolerance = allergyIntoleranceMapper
            .mapResources(ehrExtract, getPatient(), getEncounterList(), PRACTISE_CODE);

        final Meta meta = allergyIntolerance.getFirst().getMeta();

        assertMetaSecurityPresent(meta);
        verifyConfidentialityServiceCalled(1, ehrComposition.getConfidentialityCode(), Optional.empty());
    }

    @Test
    void testGivenAllergyIntoleranceWithNopatConfidentialityCodePresentWithinObservationStatementExpectMetaSecurityAdded() {
        final Meta stubbedMeta = MetaUtil.getMetaFor(META_WITH_SECURITY, META_PROFILE);
        final RCMRMT030101UKEhrExtract ehrExtract =
            unmarshallEhrExtract("allergy-structure-with-observation-statement-nopat-confidentiality-code.xml");

        Mockito
            .lenient()
            .when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
                any(String.class), any(Optional.class), any(Optional.class)
            )).thenReturn(stubbedMeta);

        final RCMRMT030101UKObservationStatement observationStatement = GET_OBSERVATION_STATEMENT.apply(ehrExtract);
        final List<AllergyIntolerance> allergyIntolerance = allergyIntoleranceMapper
            .mapResources(ehrExtract, getPatient(), getEncounterList(), PRACTISE_CODE);

        final Meta meta = allergyIntolerance.getFirst().getMeta();

        assertMetaSecurityPresent(meta);
        verifyConfidentialityServiceCalled(1, Optional.empty(), observationStatement.getConfidentialityCode());
    }

    @Test
    void testGivenAllergyIntoleranceWithNoscrubConfidentialityCodePresentWithinObservationStatementExpectMetaSecurityNotAdded() {
        final RCMRMT030101UKEhrExtract ehrExtract =
            unmarshallEhrExtract("allergy-structure-with-observation-statement-noscrub-confidentiality-code.xml");

        final RCMRMT030101UKObservationStatement observationStatement = GET_OBSERVATION_STATEMENT.apply(ehrExtract);
        final List<AllergyIntolerance> allergyIntolerance = allergyIntoleranceMapper
            .mapResources(ehrExtract, getPatient(), getEncounterList(), PRACTISE_CODE);

        assertThat(allergyIntolerance.getFirst().getMeta().getSecurity()).isEmpty();
        verifyConfidentialityServiceCalled(1, Optional.empty(), observationStatement.getConfidentialityCode());
    }

    @Test
    void testGivenStandaloneAllergyThenNoExtensionPresent() {
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("allergy-structure-invalid-encounter-reference.xml");

        when(codeableConceptMapper.mapToCodeableConcept(any(CD.class)))
            .thenReturn(defaultCodeableConcept());

        final List<AllergyIntolerance> allergyIntolerances = allergyIntoleranceMapper.mapResources(ehrExtract, getPatient(),
            getEncounterList(), PRACTISE_CODE);

        final AllergyIntolerance allergyIntolerance = allergyIntolerances.getFirst();
        assertThat(allergyIntolerance.getExtension()).isEmpty();
        verifyConfidentialityServiceCalled(1, Optional.empty(), Optional.empty());
    }

    @Test
    void testGivenAllergyWithSameTermTexts() {
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("drug-allergy-structure-with-term-text.xml");

        when(codeableConceptMapper.mapToCodeableConcept(any(CD.class)))
            .thenReturn(defaultCodeableConcept());

        final List<AllergyIntolerance> allergyIntolerances = allergyIntoleranceMapper.mapResources(ehrExtract, getPatient(),
            getEncounterList(), PRACTISE_CODE);

        assertThat(allergyIntolerances).hasSize(1);
        final AllergyIntolerance allergyIntolerance = allergyIntolerances.getFirst();

        assertFixedValues(allergyIntolerance);

        assertExtension(allergyIntolerance);
        assertThat(allergyIntolerance.getCategory().getFirst().getValue()).isEqualTo(MEDICATION);
        assertThat(allergyIntolerance.getAssertedDateElement().asStringValue()).isEqualTo("1978-12-31");
        assertThat(allergyIntolerance.getRecorder().getReference()).isEqualTo("Practitioner/2D70F602-6BB1-47E0-B2EC-39912A59787D");
        assertThat(allergyIntolerance.getOnsetDateTimeType().asStringValue())
            .isEqualTo(DateFormatUtil.parseToDateTimeType("19781231").asStringValue());
        assertThat(allergyIntolerance.getAsserter().getReference()).isEqualTo("Practitioner/2D70F602-6BB1-47E0-B2EC-39912A59787D");
        assertThat(allergyIntolerance.getNote().getFirst().getText()).isEqualTo(PERTINENT_NOTE_TEXT);
        assertThat(allergyIntolerance.getCode().getCodingFirstRep()).isEqualTo(DegradedCodeableConcepts.DEGRADED_DRUG_ALLERGY);
        assertThat(allergyIntolerance.getCode().getCoding().get(1).getDisplay()).isEqualTo(CODING_DISPLAY_1);
        assertThat(allergyIntolerance.getNote().size()).isOne();
        verifyConfidentialityServiceCalled(1, Optional.empty(), Optional.empty());
    }

    @Test
    void testMapAllergyWithDrugTermText() {
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("drug-allergy-structure-with-term-text.xml");

        when(codeableConceptMapper.mapToCodeableConcept(any(CD.class)))
            .thenReturn(tertiaryCodeableConcept())
            .thenReturn(tertiaryCodeableConcept());

        final List<AllergyIntolerance> allergyIntolerances = allergyIntoleranceMapper.mapResources(ehrExtract, getPatient(),
            getEncounterList(), PRACTISE_CODE);

        assertThat(allergyIntolerances).hasSize(1);
        final AllergyIntolerance allergyIntolerance = allergyIntolerances.getFirst();

        assertFixedValues(allergyIntolerance);

        assertExtension(allergyIntolerance);
        assertThat(allergyIntolerance.getCategory().getFirst().getValue()).isEqualTo(MEDICATION);
        assertThat(allergyIntolerance.getAssertedDateElement().asStringValue()).isEqualTo("1978-12-31");
        assertThat(allergyIntolerance.getRecorder().getReference()).isEqualTo("Practitioner/2D70F602-6BB1-47E0-B2EC-39912A59787D");
        assertThat(allergyIntolerance.getOnsetDateTimeType().asStringValue())
            .isEqualTo(DateFormatUtil.parseToDateTimeType("19781231").asStringValue());
        assertThat(allergyIntolerance.getAsserter().getReference()).isEqualTo("Practitioner/2D70F602-6BB1-47E0-B2EC-39912A59787D");
        assertThat(allergyIntolerance.getNote().getFirst().getText()).isEqualTo(PERTINENT_NOTE_TEXT);
        assertThat(allergyIntolerance.getCode().getCodingFirstRep()).isEqualTo(DegradedCodeableConcepts.DEGRADED_DRUG_ALLERGY);
        assertThat(allergyIntolerance.getCode().getCoding().get(1).getDisplay()).isEqualTo(CODING_DISPLAY_3);
        assertThat(allergyIntolerance.getNote().size()).isOne();
        verifyConfidentialityServiceCalled(1, Optional.empty(), Optional.empty());
    }

    @Test
    void testGivenDrugAllergyWithValueElementThenMapsCodingTextFromValueDescription() {
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("drug-allergy-with-value.xml");

        when(codeableConceptMapper.mapToCodeableConcept(any(CD.class)))
            .thenReturn(defaultCodeableConcept())
            .thenReturn(secondaryCodeableConcept());

        final List<AllergyIntolerance> allergyIntolerances = allergyIntoleranceMapper.mapResources(ehrExtract, getPatient(),
            getEncounterList(), PRACTISE_CODE);

        assertThat(allergyIntolerances).hasSize(1);
        final AllergyIntolerance allergyIntolerance = allergyIntolerances.getFirst();

        assertFixedValues(allergyIntolerance);

        assertExtension(allergyIntolerance);

        assertThat(allergyIntolerance.getCode().getText()).isEqualTo(DISPLAY_NAME_IN_VALUE);
        verifyConfidentialityServiceCalled(1, Optional.empty(), Optional.empty());
    }

    @Test
    void testGivenAllergyIntoleranceWithQualifierAndOriginalTextThenNotesContainsEpisodicity() {
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("drug_allergy_with_qualifier_and_original_text.xml");

        when(codeableConceptMapper.mapToCodeableConcept(any(CD.class)))
            .thenReturn(defaultCodeableConcept());

        final List<AllergyIntolerance> allergyIntolerances = allergyIntoleranceMapper.mapResources(ehrExtract, getPatient(),
            getEncounterList(), PRACTISE_CODE);

        final AllergyIntolerance allergyIntolerance = allergyIntolerances.getFirst();

        assertThat(allergyIntolerance.getNote().getFirst().getText()).isEqualTo(EPISODICITY_WITH_ORIGINAL_TEXT_NOTE_TEXT);
        verifyConfidentialityServiceCalled(1, Optional.empty(), Optional.empty());
    }

    @Test
    void testGivenAllergyIntoleranceWithQualifierAndWithoutOriginalTextThenNotesContainsEpisodicity() {
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("drug_allergy_with_qualifier_without_original_text.xml");

        when(codeableConceptMapper.mapToCodeableConcept(any(CD.class)))
            .thenReturn(defaultCodeableConcept());

        final List<AllergyIntolerance> allergyIntolerances = allergyIntoleranceMapper.mapResources(ehrExtract, getPatient(),
            getEncounterList(), PRACTISE_CODE);

        final AllergyIntolerance allergyIntolerance = allergyIntolerances.getFirst();

        assertThat(allergyIntolerance.getNote().getFirst().getText()).isEqualTo(EPISODICITY_WITHOUT_ORIGINAL_TEXT_NOTE_TEXT);
        verifyConfidentialityServiceCalled(1, Optional.empty(), Optional.empty());
    }

    @ParameterizedTest
    @MethodSource("allergyStructuresWithTranslations")
    void testTppNamedSchemaInValue(String filename) {
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract(filename);

        when(codeableConceptMapper.mapToCodeableConcept(any(CD.class)))
            .thenReturn(tertiaryCodeableConcept());

        allergyIntoleranceMapper.mapResources(ehrExtract, getPatient(), getEncounterList(), PRACTISE_CODE);

        verify(codeableConceptMapper, times(2)).mapToCodeableConcept(cdCaptor.capture());

        final CD cd = cdCaptor.getAllValues().get(1);

        assertThat(cd.getCode()).isEqualTo(MULTILEX_COCONUT_OIL);
        assertThat(cd.getCodeSystem()).isEqualTo(MULTILEX_CODE_SYSTEM);
        assertThat(cd.getDisplayName()).isEqualTo(CODING_DISPLAY_4);
        assertThat(cd.getTranslation().size()).isOne();

        final CD translation = cd.getTranslation().getFirst();

        assertThat(translation.getCodeSystem()).isEqualTo(SNOMED_CODE_SYSTEM);
        assertThat(translation.getCode()).isEqualTo(SNOMED_COCONUT_OIL);
        assertThat(translation.getDisplayName()).isEqualTo(CODING_DISPLAY_4);
        verifyConfidentialityServiceCalled(1, Optional.empty(), Optional.empty());
    }

    private static Stream<Arguments> allergyStructuresWithTranslations() {
        return Stream.of(
            Arguments.of("allergy-structure-tpp-named-schema.xml"),
            Arguments.of("allergy-structure-with-translations.xml")
        );
    }

    private void assertFixedValues(AllergyIntolerance allergyIntolerance) {
        assertThat(allergyIntolerance.getId()).isEqualTo(COMPOUND_STATEMENT_ROOT_ID);
        assertThat(allergyIntolerance.getMeta().getProfile().getFirst().getValue()).isEqualTo(META_PROFILE);
        assertIdentifier(allergyIntolerance.getIdentifierFirstRep(), allergyIntolerance.getId());
        assertThat(allergyIntolerance.getClinicalStatus()).isEqualTo(ACTIVE);
        assertThat(allergyIntolerance.getVerificationStatus()).isEqualTo(UNCONFIRMED);
        assertThat(allergyIntolerance.getPatient().getResource().getIdElement().getValue()).isEqualTo(PATIENT_ID);
    }

    private void assertExtension(AllergyIntolerance allergyIntolerance) {
        var encounterID = allergyIntolerance.getExtension().stream()
            .map(Extension::getValue)
            .map(Reference.class::cast)
            .map(Reference::getResource)
            .map(encounter -> encounter.getIdElement().getValue())
            .findFirst()
            .orElseThrow();

        assertThat(encounterID).isEqualTo(ENCOUNTER_ID);
    }

    private void assertMetaSecurityPresent(Meta meta) {
        assertAll(
            () -> assertThat(meta.getSecurity()).hasSize(1),
            () -> assertThat(meta.getSecurity().getFirst().getCode())
                .isEqualTo("NOPAT"),
            () -> assertThat(meta.getSecurity().getFirst().getSystem())
                .isEqualTo("http://hl7.org/fhir/v3/ActCode"),
            () -> assertThat(meta.getSecurity().getFirst().getDisplay())
                .isEqualTo("no disclosure to patient, family or caregivers without attending provider's authorization")
        );
    }

    private void assertIdentifier(Identifier identifier, String id) {
        assertThat(identifier.getSystem()).isEqualTo(IDENTIFIER_SYSTEM);
        assertThat(identifier.getValue()).isEqualTo(id);
    }

    private List<Encounter> getEncounterList() {
        var encounter = new Encounter();
        encounter.setId(ENCOUNTER_ID);
        return List.of(encounter);
    }

    private Patient getPatient() {
        var patient = new Patient();
        patient.setId(PATIENT_ID);
        return patient;
    }

    private CodeableConcept defaultCodeableConcept() {
        return createCodeableConcept(null, SNOMED_CODE_SYSTEM, CODING_DISPLAY_1);
    }

    private CodeableConcept secondaryCodeableConcept() {
        return createCodeableConcept(null, SNOMED_CODE_SYSTEM, CODING_DISPLAY_2);
    }

    private CodeableConcept tertiaryCodeableConcept() {
        return createCodeableConcept(null, null, CODING_DISPLAY_3);
    }

    private CodeableConcept nonSnomedCodeableConcept() {
        return createCodeableConcept(MULTILEX_COCONUT_OIL, MULTILEX_CODE_SYSTEM, CODING_DISPLAY_4);
    }

    @SneakyThrows
    private RCMRMT030101UKEhrExtract unmarshallEhrExtract(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES_BASE + fileName), RCMRMT030101UKEhrExtract.class);
    }

    @SafeVarargs
    private void verifyConfidentialityServiceCalled(int expectedCalls, Optional<CV>... cvs) {
        verify(confidentialityService, times(expectedCalls))
            .createMetaAndAddSecurityIfConfidentialityCodesPresent(META_PROFILE, cvs);
    }
}