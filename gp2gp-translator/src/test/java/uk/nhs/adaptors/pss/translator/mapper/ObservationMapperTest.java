package uk.nhs.adaptors.pss.translator.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.util.ResourceUtils.getFile;

import static uk.nhs.adaptors.pss.translator.util.MetaUtil.MetaType.META_WITH_SECURITY;
import static uk.nhs.adaptors.pss.translator.util.MetaUtil.assertMetaSecurityIsPresent;
import static uk.nhs.adaptors.pss.translator.util.CompoundStatementResourceExtractors.extractAllCompoundStatements;
import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Observation.ObservationStatus;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.Quantity;
import org.hl7.fhir.dstu3.model.StringType;
import org.hl7.v3.CV;
import org.hl7.v3.RCMRMT030101UKComponent02;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKEhrExtract;

import org.hl7.v3.RCMRMT030101UKObservationStatement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.SneakyThrows;
import uk.nhs.adaptors.pss.translator.util.MetaUtil;
import uk.nhs.adaptors.pss.translator.TestUtility;
import uk.nhs.adaptors.pss.translator.service.ConfidentialityService;
import uk.nhs.adaptors.pss.translator.util.DatabaseImmunizationChecker;
import uk.nhs.adaptors.pss.translator.util.DegradedCodeableConcepts;

import static uk.nhs.adaptors.common.util.CodeableConceptUtils.createCodeableConcept;

@ExtendWith(MockitoExtension.class)
public class ObservationMapperTest {

    private static final String XML_RESOURCES_BASE = "xml/Observation/";
    private static final String EXAMPLE_ID = "263B2A9F-0B1D-4697-943A-328F70E068DE";
    private static final String PRACTISE_CODE = "TESTPRACTISECODE";
    private static final String IDENTIFIER_SYSTEM = "https://PSSAdaptor/TESTPRACTISECODE";
    private static final String INTERPRETATION_SYSTEM = "http://hl7.org/fhir/v2/0078";
    private static final String CODING_DISPLAY_MOCK = "Test Display";
    private static final String ISSUED_EHR_COMPOSITION_EXAMPLE = "2020-01-01T01:01:01.000+00:00";
    private static final String PPRF_PARTICIPANT_ID = "Practitioner/1230F602-6BB1-47E0-B2EC-39912A59787D";
    private static final String NEGATIVE_VALUE = "Negative";
    private static final String TEST_DISPLAY_VALUE = "Test display name";
    private static final BigDecimal QUANTITY_VALUE_BASE = new BigDecimal(27);
    private static final BigDecimal QUANTITY_VALUE = QUANTITY_VALUE_BASE.setScale(3);
    private static final BigDecimal REFERENCE_RANGE_LOW_VALUE = new BigDecimal(20);
    private static final BigDecimal REFERENCE_RANGE_HIGH_VALUE = new BigDecimal(22);
    private static final String SNOMED_SYSTEM = "http://snomed.info/sct";
    private static final List<Encounter> ENCOUNTER_LIST = List.of(
        (Encounter) new Encounter().setId("TEST_ID_MATCHING_ENCOUNTER")
    );
    private static final String ORIGINAL_TEXT = "Original Text";
    private static final String MINUS_ONE_ANNOTATION_TEXT = "minus 1 sequence comment";
    private static final String ZERO_ANNOTATION_TEXT = "zero sequence comment";
    private static final String PLUS_ONE_ANNOTATION_TEXT = "plus 1 sequence comment";
    private static final String NULL_FLAVOR_ANNOTATION_TEXT = "nullFlavor sequence comment";
    private static final String EPISODICITY_WITH_ORIGINAL_TEXT_NOTE_TEXT =
            "{Episodicity : code=255217005, displayName=First}";
    private static final String EPISODICITY_WITH_ORIGINAL_TEXT_NOTE_TEXT_WITH_ORIGINAL_TEXT =
            "{Episodicity : code=303350001, displayName=Ongoing, originalText=Review}";
    private static final String META_PROFILE = "Observation-1";
    private static final Meta META_WITH_SECURITY_ADDED = MetaUtil.getMetaFor(META_WITH_SECURITY, META_PROFILE);
    private static final CV NOPAT_CV = TestUtility.createCv(
        "NOPAT",
        "http://hl7.org/fhir/v3/ActCode",
        "no disclosure to patient, family or caregivers without attending provider's authorization");

    @Mock
    private CodeableConceptMapper codeableConceptMapper;

    @Mock
    private ConfidentialityService confidentialityService;

    @Mock
    private DatabaseImmunizationChecker immunizationChecker;

    @Mock
    private Patient patient;

    @InjectMocks
    private ObservationMapper observationMapper;

    @Test
    public void mapTwoObservationsWrappedInCompoundStatementMetaSecurityWithNoPapExpectObservationsWithNoPat() {
        var ehrExtract = unmarshallEhrExtractElement("nopat_compound_statement_with_observation.xml");

        var ehrComposition = getEhrComposition(ehrExtract);
        var observationStatements = getObservationStatementIncludedIntoCompoundStatement(ehrExtract);
        var compoundStatement = ehrComposition
            .getComponent()
            .stream()
            .flatMap(component4 -> extractAllCompoundStatements(component4))
            .findFirst().get();

        observationStatements.getFirst().setConfidentialityCode(NOPAT_CV);
        ehrComposition.setConfidentialityCode(NOPAT_CV);

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            observationStatements.getFirst().getConfidentialityCode(),
            compoundStatement.getConfidentialityCode()
        )).thenReturn(META_WITH_SECURITY_ADDED);

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            observationStatements.get(1).getConfidentialityCode(),
            compoundStatement.getConfidentialityCode()
        )).thenReturn(META_WITH_SECURITY_ADDED);

        var observations = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE);

        assertThat(observations).hasSize(2);
        assertMetaSecurityIsPresent(META_WITH_SECURITY_ADDED, observations.getFirst().getMeta());
        assertMetaSecurityIsPresent(META_WITH_SECURITY_ADDED, observations.get(1).getMeta());
    }

    @Test
    public void mapObservationMetaSecurityWithNoPatWhenConfidentialityCodeIsPresentForObservationStatementAndEhrComposition() {
        var ehrExtract = unmarshallEhrExtractElement("full_valid_data_observation_example.xml");

        var ehrComposition = getEhrComposition(ehrExtract);
        ehrComposition.setConfidentialityCode(NOPAT_CV);

        var observationStatement = getObservationStatement(ehrExtract);
        observationStatement.setConfidentialityCode(NOPAT_CV);

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            observationStatement.getConfidentialityCode(),
            Optional.empty()
        )).thenReturn(META_WITH_SECURITY_ADDED);

        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        assertMetaSecurityIsPresent(META_WITH_SECURITY_ADDED, observation.getMeta());
    }

    @Test
    public void mapObservationMetaSecurityWithNoPatWhenConfidentialityCodeIsPresentOnlyForEhrComposition() {
        var ehrExtract = unmarshallEhrExtractElement("full_valid_data_observation_example.xml");

        var ehrComposition = getEhrComposition(ehrExtract);
        ehrComposition.setConfidentialityCode(NOPAT_CV);

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            Optional.empty(),
            Optional.empty()
        )).thenReturn(META_WITH_SECURITY_ADDED);

        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        assertMetaSecurityIsPresent(META_WITH_SECURITY_ADDED, observation.getMeta());
    }

    @Test
    public void mapObservationWithValidData() {
        var ehrExtract = unmarshallEhrExtractElement("full_valid_data_observation_example.xml");
        var ehrComposition = getEhrComposition(ehrExtract);
        var observationStatement = getObservationStatement(ehrExtract);

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            observationStatement.getConfidentialityCode(),
            Optional.empty()
        )).thenReturn(META_WITH_SECURITY_ADDED);

        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        assertFixedValues(observation);
        assertThat(observation.getId()).isEqualTo(EXAMPLE_ID);
        assertThat(observation.getEffective()).isInstanceOf(DateTimeType.class);
        assertThat(observation.getEffectiveDateTimeType().getValue()).isEqualTo("2019-07-08T13:35:00+00:00");
        assertThat(observation.getIssuedElement().asStringValue()).isEqualTo(ISSUED_EHR_COMPOSITION_EXAMPLE);
        assertThat(observation.getPerformer().getFirst().getReference()).isEqualTo(PPRF_PARTICIPANT_ID);
        assertThat(observation.getValue()).isInstanceOf(Quantity.class);
        assertQuantity(observation.getValueQuantity(), QUANTITY_VALUE, "kilogram per square meter", "kg/m2");
        assertInterpretation(observation.getInterpretation(), "High", "H", "High");
        assertThat(observation.getComment()).isEqualTo("Subject: Uncle Test text 1");
        assertThat(observation.getReferenceRange().getFirst().getText()).isEqualTo("Age and sex based");
        assertQuantity(observation.getReferenceRange().getFirst().getLow(), REFERENCE_RANGE_LOW_VALUE, "liter", "L");
        assertQuantity(observation.getReferenceRange().getFirst().getHigh(), REFERENCE_RANGE_HIGH_VALUE, "liter", "L");
        assertTrue(observation.hasSubject());
    }

    /**
     * Testing episodicity comment renders without any existing comments being present.
     * Formatting should be similar to Allergy Intolerance.
     */
    @Test
    public void mapObservationWithEpisodicity() {
        var ehrExtract = unmarshallEhrExtractElement("data_observation_with_episodicity.xml");
        var ehrComposition = getEhrComposition(ehrExtract);
        var observationStatement = getObservationStatement(ehrExtract);

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            observationStatement.getConfidentialityCode(),
            Optional.empty()
        )).thenReturn(META_WITH_SECURITY_ADDED);

        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        assertFixedValues(observation);
        assertThat(observation.getComment()).isEqualTo(EPISODICITY_WITH_ORIGINAL_TEXT_NOTE_TEXT);
    }

    /**
     * Testing when episodicity contains an originalText element.
     */
    @Test
    public void mapObservationWithEpisodicityWithOriginalText() {
        var ehrExtract = unmarshallEhrExtractElement("data_observation_with_episodicity_with_original_text.xml");
        var ehrComposition = getEhrComposition(ehrExtract);
        var observationStatement = getObservationStatement(ehrExtract);

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            observationStatement.getConfidentialityCode(),
            Optional.empty()
        )).thenReturn(META_WITH_SECURITY_ADDED);

        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        assertFixedValues(observation);
        assertThat(observation.getComment()).isEqualTo(EPISODICITY_WITH_ORIGINAL_TEXT_NOTE_TEXT_WITH_ORIGINAL_TEXT);
    }
    @Test
    public void mapObservationWithEpisodicityWithOriginalTextAndExistingComment() {
        var ehrExtract = unmarshallEhrExtractElement("data_observation_with_episodicity_with_existing_comment.xml");
        var ehrComposition = getEhrComposition(ehrExtract);
        var observationStatement = getObservationStatement(ehrExtract);

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            observationStatement.getConfidentialityCode(),
            Optional.empty()
        )).thenReturn(META_WITH_SECURITY_ADDED);

        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        assertFixedValues(observation);
        assertThat(observation.getComment()).contains(EPISODICITY_WITH_ORIGINAL_TEXT_NOTE_TEXT_WITH_ORIGINAL_TEXT);
    }
    @Test
    public void mapObservationWhichIsBloodPressureWithoutBatteryOrBloodPressureTripleExpectObservationMapped() {
        var ehrExtract = unmarshallEhrExtractElement(
                "observation_is_blood_pressure_without_battery_or_triple.xml");
        var observations = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE);

        assertThat(observations).isNotEmpty();
    }

    @Test
    public void mappingObservationWhichIsBloodPressureExpectObservationNotMapped() {
        var ehrExtract = unmarshallEhrExtractElement(
                "observation_is_blood_pressure.xml");
        var observations = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE);

        assertThat(observations).isEmpty();
    }

    @Test
    public void mapObservationWithNoOptionalData() {

        var ehrExtract = unmarshallEhrExtractElement("no_optional_data_observation_example.xml");
        var ehrComposition = getEhrComposition(ehrExtract);
        var observationStatement = getObservationStatement(ehrExtract);

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            observationStatement.getConfidentialityCode(),
            Optional.empty()
        )).thenReturn(META_WITH_SECURITY_ADDED);

        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        assertFixedValues(observation);
        assertThat(observation.getId()).isEqualTo(EXAMPLE_ID);
        assertThat(observation.getIssuedElement().asStringValue()).isEqualTo(ISSUED_EHR_COMPOSITION_EXAMPLE);
        assertThat(observation.getPerformer().getFirst().getReference()).isEqualTo(PPRF_PARTICIPANT_ID);
        assertNull(observation.getEffective());
        assertNull(observation.getValue());
        assertThat(observation.getInterpretation().getCoding()).isEmpty();
        assertNull(observation.getComment());
        assertThat(observation.getReferenceRange()).isEmpty();
    }

    @Test
    public void mapObservationWithValueStringUsingValueTypeST() {
        var ehrExtract = unmarshallEhrExtractElement("value_st_observation_example.xml");
        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        assertThat(observation.getValue()).isInstanceOf(StringType.class);
        assertThat(observation.getValueStringType().getValue()).isEqualToIgnoringWhitespace(NEGATIVE_VALUE);
    }

    @Test
    public void mapObservationWithValueStringUsingValueTypeCVOriginalText() {
        var ehrExtract = unmarshallEhrExtractElement("value_cv_display_name_observation_example.xml");
        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        assertThat(observation.getValue()).isInstanceOf(StringType.class);
        assertThat(observation.getValueStringType().getValue()).isEqualTo(TEST_DISPLAY_VALUE);
    }

    @Test
    public void mapObservationWithMultiplePertinentInformation() {
        var ehrExtract = unmarshallEhrExtractElement("multiple_pertinent_information_observation_example.xml");
        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        assertThat(observation.getComment()).isEqualTo("Test text 1 Test text 2");
    }

    @Test
    public void mapObservationWithCompositionIdMatchingEncounter() {
        var ehrExtract = unmarshallEhrExtractElement("ehr_composition_id_matching_encounter_observation_example.xml");
        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        assertTrue(observation.hasContext());
    }

    @Test
    public void mapObservationWithEffectiveDateTime() {
        var ehrExtract = unmarshallEhrExtractElement(
            "effective_date_time_type_using_effective_time_center.xml");
        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        assertTrue(observation.getEffective() instanceof DateTimeType);
        assertThat(observation.getEffectiveDateTimeType().getValueAsString()).isEqualTo("2010-05-21");
    }

    @Test
    public void mapObservationWithEffectivePeriod() {
        var ehrExtract = unmarshallEhrExtractElement(
            "effective_period_start_end_using_effective_time_observation_example.xml");
        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        assertTrue(observation.getEffective() instanceof Period);
        assertThat(observation.getEffectivePeriod().getStartElement().getValueAsString()).isEqualTo("2010-05-21");
        assertThat(observation.getEffectivePeriod().getEndElement().getValueAsString()).isEqualTo("2010-05-22");
    }

    @Test
    public void handleEmptyComponentWithNoObservationStatement() {
        var ehrExtract = unmarshallEhrExtractElement("ehr_composition_with_no_observation_statements_example.xml");
        var observations = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE);

        assertThat(observations).isEmpty();
    }

    @Test
    public void When_MapObservation_With_Plus1SequenceComment_Expect_CommentMapped() {
        var ehrExtract = unmarshallEhrExtractElement(
            "sequence_comment_plus1__originalText_observation.xml");
        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        assertThat(observation.getComment()).isEqualTo(PLUS_ONE_ANNOTATION_TEXT);
    }

    @Test
    public void When_MapObservation_With_Minus1SequenceComment_Expect_CommentAndOriginalTextMapped() {
        var ehrExtract = unmarshallEhrExtractElement(
            "sequence_comment_minus1_originalText_observation.xml");
        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        var expectedComment = MINUS_ONE_ANNOTATION_TEXT + StringUtils.SPACE + ORIGINAL_TEXT;

        assertThat(observation.getComment()).isEqualTo(expectedComment);
    }

    @Test
    public void When_MapObservation_With_Minus1AndPlus1SequenceComment_Expect_CommentsAndOriginalTextMapped() {
        var ehrExtract = unmarshallEhrExtractElement(
            "sequence_comment_plus1_minus1_originalText_observation.xml");
        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        var expectedComment = MINUS_ONE_ANNOTATION_TEXT + StringUtils.SPACE + ORIGINAL_TEXT + StringUtils.SPACE + PLUS_ONE_ANNOTATION_TEXT;

        assertThat(observation.getComment()).isEqualTo(expectedComment);
    }

    @Test
    public void When_MapObservation_With_ZeroSequenceAndPlus1SequenceComment_Expect_CommentsMapped() {
        var ehrExtract = unmarshallEhrExtractElement(
            "sequence_comment_plus1_zero_originalText_observation.xml");
        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        var expectedComment = ZERO_ANNOTATION_TEXT + StringUtils.SPACE + PLUS_ONE_ANNOTATION_TEXT;

        assertThat(observation.getComment()).isEqualTo(expectedComment);
    }

    @Test
    public void When_MapObservation_With_ZeroSequenceComment_Expect_CommentMapped() {
        var ehrExtract = unmarshallEhrExtractElement(
            "sequence_comment_zero_originalText_observation.xml");
        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        assertThat(observation.getComment()).isEqualTo(ZERO_ANNOTATION_TEXT);
    }

    @Test
    public void When_MapObservation_With_Minus1SequenceCommentAndNoOriginalText_Expect_CommentMapped() {
        var ehrExtract = unmarshallEhrExtractElement(
            "sequence_comment_minus1_observation.xml");
        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        assertThat(observation.getComment()).isEqualTo(MINUS_ONE_ANNOTATION_TEXT);
    }

    @Test
    public void When_MapObservation_With_AllSequenceCommentsAndOriginalText_Expect_MappedInCorrectOrder() {
        var ehrExtract = unmarshallEhrExtractElement(
            "sequence_comment_all_observation.xml");
        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        var expectedComment = MINUS_ONE_ANNOTATION_TEXT + StringUtils.SPACE + ORIGINAL_TEXT + StringUtils.SPACE + ZERO_ANNOTATION_TEXT
            + StringUtils.SPACE + PLUS_ONE_ANNOTATION_TEXT;

        assertThat(observation.getComment()).isEqualTo(expectedComment);
    }

    @Test
    public void When_MapObservation_WithNullFlavorSequenceComments_Expect_CommentsPostFixed() {
        var ehrExtract = unmarshallEhrExtractElement(
            "sequence_comment_minus1_nullFlavor_originalText.xml");
        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        var expectedComment = MINUS_ONE_ANNOTATION_TEXT + StringUtils.SPACE + ORIGINAL_TEXT + StringUtils.SPACE
            + NULL_FLAVOR_ANNOTATION_TEXT;

        assertThat(observation.getComment()).isEqualTo(expectedComment);
    }

    @Test
    public void When_MapObservation_WithoutSnomedCodeInCode_Expect_DegradedCodeableConcept() {

        var codeableConcept = createCodeableConcept(null, "1.2.3.4.5", CODING_DISPLAY_MOCK);
        when(codeableConceptMapper.mapToCodeableConcept(any())).thenReturn(codeableConcept);

        var ehrExtract = unmarshallEhrExtractElement("full_valid_data_observation_example.xml");
        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        assertThat(observation.getCode().getCodingFirstRep())
            .isEqualTo(DegradedCodeableConcepts.DEGRADED_OTHER);
    }

    @Test
    public void When_MapObservation_WithSnomedCodeInCode_Expect_MappedWithoutDegrading() {

        var codeableConcept = createCodeableConcept(null, SNOMED_SYSTEM, CODING_DISPLAY_MOCK);
        lenient().when(codeableConceptMapper.mapToCodeableConcept(any())).thenReturn(codeableConcept);

        var ehrExtract = unmarshallEhrExtractElement("full_valid_data_observation_example.xml");
        var observation = observationMapper.mapResources(ehrExtract, patient, ENCOUNTER_LIST, PRACTISE_CODE).getFirst();

        assertThat(observation.getCode().getCodingFirstRep().getDisplay())
            .isEqualTo(CODING_DISPLAY_MOCK);
        assertThat(observation.getCode().getCodingFirstRep().getSystem())
            .isEqualTo(SNOMED_SYSTEM);
    }

    private void assertFixedValues(Observation observation) {
        assertThat(observation.getMeta().getProfile().getFirst().getValue()).isEqualTo(META_PROFILE);
        assertThat(observation.getIdentifier().getFirst().getSystem()).isEqualTo(IDENTIFIER_SYSTEM);
        assertThat(observation.getIdentifier().getFirst().getValue()).isEqualTo(EXAMPLE_ID);
        assertThat(observation.getStatus()).isEqualTo(ObservationStatus.FINAL);
    }

    private void assertInterpretation(CodeableConcept interpretation, String text, String code, String display) {
        assertThat(interpretation.getText()).isEqualTo(text);
        assertThat(interpretation.getCoding().getFirst().getCode()).isEqualTo(code);
        assertThat(interpretation.getCoding().getFirst().getDisplay()).isEqualTo(display);
        assertThat(interpretation.getCoding().getFirst().getSystem()).isEqualTo(INTERPRETATION_SYSTEM);
    }

    private void assertQuantity(Quantity quantity, BigDecimal value, String unit, String code) {
        assertThat(quantity.getValue()).isEqualTo(value);
        assertThat(quantity.getUnit()).isEqualTo(unit);
        assertThat(quantity.getCode()).isEqualTo(code);
    }

    private RCMRMT030101UKEhrComposition getEhrComposition(RCMRMT030101UKEhrExtract ehrExtract) {

        return ehrExtract.getComponent().getFirst()
            .getEhrFolder().getComponent().getFirst()
            .getEhrComposition();
    }

    private RCMRMT030101UKObservationStatement getObservationStatement(RCMRMT030101UKEhrExtract ehrExtract) {
        return ehrExtract.getComponent().getFirst()
            .getEhrFolder().getComponent().getFirst()
            .getEhrComposition().getComponent().getFirst()
            .getObservationStatement();
    }

    private List<RCMRMT030101UKObservationStatement> getObservationStatementIncludedIntoCompoundStatement(
        RCMRMT030101UKEhrExtract ehrExtract) {

        return ehrExtract.getComponent().getFirst()
            .getEhrFolder().getComponent().getFirst()
            .getEhrComposition().getComponent().getFirst().getCompoundStatement().getComponent().stream()
            .filter(RCMRMT030101UKComponent02::hasObservationStatement)
            .map(RCMRMT030101UKComponent02::getObservationStatement)
            .toList();
    }

    @SneakyThrows
    private RCMRMT030101UKEhrExtract unmarshallEhrExtractElement(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES_BASE + fileName), RCMRMT030101UKEhrExtract.class);
    }
}
