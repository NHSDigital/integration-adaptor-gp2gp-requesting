package uk.nhs.adaptors.pss.translator.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import static uk.nhs.adaptors.pss.translator.util.MetaUtil.MetaType.META_WITHOUT_SECURITY;
import static uk.nhs.adaptors.pss.translator.util.MetaUtil.MetaType.META_WITH_SECURITY;
import static uk.nhs.adaptors.pss.translator.util.DateFormatUtil.parseToDateTimeType;
import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Condition;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.MedicationRequest;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.v3.CV;
import org.hl7.v3.II;
import org.hl7.v3.RCMRMT030101UKAuthorise;
import org.hl7.v3.RCMRMT030101UKComponent2;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKMedicationStatement;
import org.hl7.v3.RCMRMT030101UKPrescribe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.SneakyThrows;
import uk.nhs.adaptors.pss.translator.FileFactory;
import uk.nhs.adaptors.pss.translator.util.MetaUtil;
import uk.nhs.adaptors.pss.translator.mapper.medication.MedicationMapperUtils;
import uk.nhs.adaptors.pss.translator.service.ConfidentialityService;
import uk.nhs.adaptors.pss.translator.util.DegradedCodeableConcepts;
import static uk.nhs.adaptors.common.util.CodeableConceptUtils.createCodeableConcept;

@ExtendWith(MockitoExtension.class)
class ConditionMapperTest {

    private static final String META_PROFILE = "ProblemHeader-Condition-1";
    private static final String TEST_FILES_DIRECTORY = "Condition";
    private static final String PATIENT_ID = "PATIENT_ID";
    private static final String PRACTISE_CODE = "TESTPRACTISECODE";
    private static final String ENCOUNTER_ID = "EHR_COMPOSITION_ENCOUNTER_ID";
    private static final String ASSERTER_ID_REFERENCE = "Practitioner/ASSERTER_ID";
    private static final String LINKSET_ID = "LINKSET_ID";
    private static final String CODING_DISPLAY = "THIS IS A TEST";
    private static final DateTimeType EHR_EXTRACT_AVAILABILITY_DATETIME = parseToDateTimeType("20101209114846.00");
    private static final String NOPAT = "NOPAT";

    private static final String ACTUAL_PROBLEM_URL = "https://fhir.hl7.org.uk/STU3/StructureDefinition/Extension-CareConnect"
        + "-ActualProblem-1";
    private static final String PROBLEM_SIGNIFICANCE_URL = "https://fhir.hl7.org.uk/STU3/StructureDefinition/Extension-CareConnect"
        + "-ProblemSignificance-1";
    private static final String RELATED_CLINICAL_CONTENT_URL = "https://fhir.hl7.org.uk/STU3/StructureDefinition/Extension-CareConnect"
        + "-RelatedClinicalContent-1";
    private static final String MEDICATION_STATEMENT_PLAN_ID = "PLAN_REF_ID";
    private static final String AUTHORISE_ID = "AUTHORISE_ID";
    private static final String MEDICATION_STATEMENT_ORDER_ID = "ORDER_REF_ID";
    private static final String PRESCRIBE_ID = "PRESCRIBE_ID";
    private static final String NAMED_STATEMENT_REF_ID = "NAMED_STATEMENT_REF_ID";
    private static final String STATEMENT_REF_ID = "STATEMENT_REF_ID";
    private static final String STATEMENT_REF_ID_1 = "STATEMENT_REF_ID_1";
    private static final int EXPECTED_NUMBER_OF_EXTENSIONS = 4;

    @Mock
    private CodeableConceptMapper codeableConceptMapper;
    @Mock
    private DateTimeMapper dateTimeMapper;
    @Mock
    private ConfidentialityService confidentialityService;
    @InjectMocks
    private ConditionMapper conditionMapper;
    @Captor
    private ArgumentCaptor<Optional<CV>> confidentialityCodeCaptor;

    private Patient patient;

    @BeforeEach
    void beforeEach() {
        configureCommonStubs();
        patient = (Patient) new Patient().setId(PATIENT_ID);
    }

    @Test
    void testConditionIsMappedCorrectlyNoReferences() {
        when(dateTimeMapper.mapDateTime(any())).thenReturn(EHR_EXTRACT_AVAILABILITY_DATETIME);

        final List<Encounter> emptyEncounterList = List.of();
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("linkset_valid.xml");
        final List<Condition> conditions = conditionMapper.mapResources(ehrExtract, patient, emptyEncounterList, PRACTISE_CODE);

        assertThat(conditions).isNotEmpty();

        final Condition condition = conditions.getFirst();

        assertGeneratedComponentsAreCorrect(condition);
        assertThat(condition.getId()).isEqualTo(LINKSET_ID);

        assertThat(condition.getExtensionsByUrl(ACTUAL_PROBLEM_URL)).isEmpty();
        assertThat(condition.getExtensionsByUrl(PROBLEM_SIGNIFICANCE_URL)).hasSize(1);
        assertThat(condition.getExtensionsByUrl(RELATED_CLINICAL_CONTENT_URL)).isEmpty();

        assertThat(condition.getClinicalStatus().getDisplay()).isEqualTo("Active");
        assertFalse(condition.getCode().getCodingFirstRep().hasDisplay());

        assertThat(condition.getSubject().getResource().getIdElement().getIdPart()).isEqualTo(PATIENT_ID);
        assertThat(condition.getAsserter().getReference()).isEqualTo(ASSERTER_ID_REFERENCE);
        assertFalse(condition.getContext().hasReference());

        assertThat(condition.getOnsetDateTimeType()).isEqualTo(EHR_EXTRACT_AVAILABILITY_DATETIME);
        assertThat(condition.getAbatementDateTimeType()).isEqualTo(EHR_EXTRACT_AVAILABILITY_DATETIME);
        assertNull(condition.getAssertedDateElement().getValue());

        assertThat(condition.getNote()).isEmpty();
    }

    @Test
    void testConditionIsMappedCorrectlyWithNamedStatementRef() {
        when(dateTimeMapper.mapDateTime(any(String.class))).thenCallRealMethod();
        var codeableConcept = new CodeableConcept().addCoding(new Coding().setDisplay(CODING_DISPLAY));
        when(codeableConceptMapper.mapToCodeableConcept(any())).thenReturn(codeableConcept);

        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("linkset_valid_with_reference.xml");
        final List<Condition> conditions = conditionMapper.mapResources(ehrExtract, patient, List.of(), PRACTISE_CODE);
        conditionMapper.addReferences(buildBundleWithNamedStatementObservation(), conditions, ehrExtract);

        assertThat(conditions.getFirst().getCode().getCodingFirstRep()).isEqualTo(DegradedCodeableConcepts.DEGRADED_OTHER);
        assertThat(conditions.getFirst().getCode().getCoding().get(1).getDisplay()).isEqualTo(CODING_DISPLAY);
    }

    @Test
    void testConditionIsMappedCorrectlyWithActualProblemReference() {
        when(dateTimeMapper.mapDateTime(any())).thenReturn(EHR_EXTRACT_AVAILABILITY_DATETIME);

        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("linkset_valid.xml");
        final List<Condition> conditions = conditionMapper.mapResources(ehrExtract, patient, List.of(), PRACTISE_CODE);

        conditionMapper.addReferences(buildBundleWithNamedStatementObservation(), conditions, ehrExtract);

        assertThat(conditions).isNotEmpty();
        assertThat(conditions.getFirst().getExtensionsByUrl(ACTUAL_PROBLEM_URL)).isNotEmpty();
        assertActualProblemExtension(conditions.getFirst());
    }

    @Test
    void testConditionIsMappedCorrectlyWithRelatedClinicalContentReference() {
        when(dateTimeMapper.mapDateTime(any())).thenReturn(EHR_EXTRACT_AVAILABILITY_DATETIME);

        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("linkset_valid.xml");
        final List<Condition> conditions = conditionMapper.mapResources(ehrExtract, patient, List.of(), PRACTISE_CODE);

        conditionMapper.addReferences(buildBundleWithStatementRefObservations(), conditions, ehrExtract);

        assertThat(conditions).isNotEmpty();
        assertThat(conditions.getFirst().getExtensionsByUrl(RELATED_CLINICAL_CONTENT_URL)).isNotEmpty();
        assertRelatedClinicalContentExtension(conditions.getFirst());
    }

    @Test
    void testConditionIsMappedCorrectlyWithContext() {
        when(dateTimeMapper.mapDateTime(any())).thenReturn(EHR_EXTRACT_AVAILABILITY_DATETIME);

        final List<Encounter> encounters = List.of((Encounter) new Encounter().setId(ENCOUNTER_ID));

        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("linkset_valid.xml");
        final List<Condition> conditions = conditionMapper.mapResources(ehrExtract, patient, encounters, PRACTISE_CODE);

        assertThat(conditions).isNotEmpty();
        assertThat(conditions.getFirst().getContext().getResource().getIdElement().getValue()).isEqualTo(ENCOUNTER_ID);
    }

    @Test
    void testLinkSetWithNoDatesIsMappedWithNullOnsetDateTime() {
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("linkset_no_dates.xml");
        final List<Condition> conditions = conditionMapper.mapResources(ehrExtract, patient, List.of(), PRACTISE_CODE);

        assertGeneratedComponentsAreCorrect(conditions.getFirst());
        assertThat(conditions.getFirst().getId()).isEqualTo(LINKSET_ID);

        assertThat(conditions.getFirst().getClinicalStatus().getDisplay()).isEqualTo("Inactive");

        assertNull(conditions.getFirst().getAbatementDateTimeType());
        assertNull(conditions.getFirst().getAssertedDateElement().getValue());
    }

    @Test
    void testLinkSetWithEffectiveTimeLowNullFlavorUnkIsMappedWithNullOnsetDateTime() {
        when(dateTimeMapper.mapDateTime(any())).thenReturn(EHR_EXTRACT_AVAILABILITY_DATETIME);
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("linkset_with_null_flavor_unk.xml");
        final List<Condition> conditions = conditionMapper.mapResources(ehrExtract, patient, List.of(), PRACTISE_CODE);

        assertGeneratedComponentsAreCorrect(conditions.getFirst());
        assertThat(conditions.getFirst().getId()).isEqualTo(LINKSET_ID);

        assertNull(conditions.getFirst().getOnsetDateTimeType());
    }

    @Test
    void testLinkSetWithEffectiveTimeCenterNullFlavorUnkIsMappedCorrectly() {

        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("linkset_with_center_null_flavor_unk.xml");
        final List<Condition> conditions = conditionMapper.mapResources(ehrExtract, patient, List.of(), PRACTISE_CODE);

        assertGeneratedComponentsAreCorrect(conditions.getFirst());
        assertThat(conditions.getFirst().getId()).isEqualTo(LINKSET_ID);

        assertNull(conditions.getFirst().getOnsetDateTimeType());
    }

    @Test
    void testConditionWithMedicationRequestsIsMappedCorrectly() {
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("linkset_medication_refs.xml");

        MockedStatic<MedicationMapperUtils> mockedMedicationMapperUtils = Mockito.mockStatic(MedicationMapperUtils.class);

        // spotbugs doesn't allow try with resources due to de-referenced null check
        try {
            mockedMedicationMapperUtils.when(() -> MedicationMapperUtils.getMedicationStatements(ehrExtract))
                .thenReturn(getMedicationStatements());

            final List<Condition> conditions = conditionMapper.mapResources(ehrExtract, patient, List.of(), PRACTISE_CODE);

            assertThat(conditions.size()).isOne();

            var bundle = new Bundle();
            bundle.addEntry(new BundleEntryComponent().setResource(conditions.getFirst()));
            addMedicationRequestsToBundle(bundle);

            conditionMapper.addReferences(bundle, conditions, ehrExtract);

            var extensions = conditions.getFirst().getExtension();

            assertThat(extensions).hasSize(EXPECTED_NUMBER_OF_EXTENSIONS);
            var relatedClinicalContentExtensions = extensions.stream()
                .filter(extension -> extension.getUrl().equals(RELATED_CLINICAL_CONTENT_URL))
                .toList();

            assertThat(relatedClinicalContentExtensions).hasSize(2);

            List<String> clinicalContextReferences = relatedClinicalContentExtensions.stream()
                .map(Extension::getValue)
                .map(Reference.class::cast)
                .map(reference -> reference.getReferenceElement().getValue())
                .toList();

            assertThat(clinicalContextReferences).contains(AUTHORISE_ID);
            assertThat(clinicalContextReferences).contains(PRESCRIBE_ID);
        } finally {
            mockedMedicationMapperUtils.close();
        }

    }

    @Test
    void mapConditionWithoutSnomedCodeInCoding() {
        var codeableConcept = new CodeableConcept().addCoding(new Coding().setDisplay(CODING_DISPLAY));
        when(codeableConceptMapper.mapToCodeableConcept(any())).thenReturn(codeableConcept);
        when(dateTimeMapper.mapDateTime(any(String.class))).thenCallRealMethod();

        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("linkset_valid_with_reference.xml");
        final List<Condition> conditions = conditionMapper.mapResources(ehrExtract, patient, List.of(), PRACTISE_CODE);
        conditionMapper.addReferences(buildBundleWithNamedStatementObservation(), conditions, ehrExtract);

        assertThat(conditions).isNotEmpty();
        assertThat(conditions.getFirst().getCode().getCodingFirstRep())
            .isEqualTo(DegradedCodeableConcepts.DEGRADED_OTHER);
        assertThat(conditions.getFirst().getCode().getCoding().get(1).getDisplay())
            .isEqualTo(CODING_DISPLAY);
    }

    @Test
    void mapConditionWithSnomedCodeInCoding() {

        var codeableConcept = createCodeableConcept("123456", "http://snomed.info/sct", "Display");
        when(codeableConceptMapper.mapToCodeableConcept(any())).thenReturn(codeableConcept);
        when(dateTimeMapper.mapDateTime(any(String.class))).thenCallRealMethod();

        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("linkset_valid_with_reference.xml");
        final List<Condition> conditions = conditionMapper.mapResources(ehrExtract, patient, List.of(), PRACTISE_CODE);
        conditionMapper.addReferences(buildBundleWithNamedStatementObservation(), conditions, ehrExtract);

        assertThat(conditions).isNotEmpty();
        assertEquals(codeableConcept, conditions.getFirst().getCode());
    }

    @Test
    void When_Condition_With_NopatConfidentialityCode_Expect_MetaFromConfidentialityServiceWithSecurity() {
        final Meta metaWithSecurity = MetaUtil.getMetaFor(META_WITH_SECURITY, META_PROFILE);
        final RCMRMT030101UKEhrExtract ehrExtract =
            unmarshallEhrExtract("linkset_valid_nopat_confidentiality_code.xml");

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            eq(META_PROFILE),
            confidentialityCodeCaptor.capture(),
            confidentialityCodeCaptor.capture()
        )).thenReturn(MetaUtil.getMetaFor(META_WITH_SECURITY, META_PROFILE));

        final List<Condition> conditions = conditionMapper
            .mapResources(ehrExtract, patient, Collections.emptyList(), PRACTISE_CODE);

        final CV linksetConfidentialityCode = confidentialityCodeCaptor
            .getAllValues()
            .getFirst() // linkSet.getConfidentialityCode()
            .orElseThrow();

        assertAllConditionsHaveMeta(conditions, metaWithSecurity);
        assertAll(
            () -> assertThat(linksetConfidentialityCode.getCode()).isEqualTo(NOPAT),
            () -> assertThat(confidentialityCodeCaptor.getAllValues().get(1)).isNotPresent()
        );
    }

    @Test
    void When_Condition_With_NopatConfidentialityCodeInEhrComposition_Expect_MetaFromConfidentialityServiceWithSecurity() {
        final Meta metaWithSecurity = MetaUtil.getMetaFor(META_WITH_SECURITY, META_PROFILE);
        final RCMRMT030101UKEhrExtract ehrExtract =
            unmarshallEhrExtract("linkset_valid_ehr_composition_nopat_confidentiality_code.xml");

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            eq(META_PROFILE),
            confidentialityCodeCaptor.capture(),
            confidentialityCodeCaptor.capture()
        )).thenReturn(MetaUtil.getMetaFor(META_WITH_SECURITY, META_PROFILE));

        final List<Condition> conditions = conditionMapper
            .mapResources(ehrExtract, patient, Collections.emptyList(), PRACTISE_CODE);

        final CV ehrCompositionConfidentialityCode = confidentialityCodeCaptor
            .getAllValues()
            .get(1) // ehrComposition.getConfidentialityCode()
            .orElseThrow();

        assertAllConditionsHaveMeta(conditions, metaWithSecurity);
        assertAll(
            () -> assertThat(ehrCompositionConfidentialityCode.getCode()).isEqualTo(NOPAT),
            () -> assertThat(confidentialityCodeCaptor.getAllValues().getFirst()).isNotPresent()
        );
    }

    @Test
    void When_MappingLinksetWhichIsAReferralRequestToExternalDocumentLinkSet_Expect_ConditionNotToBeMapped() {
        final var ehrExtract = unmarshallEhrExtract(
            "ResourceFilter",
            "ehr_extract_with_referral_request_to_external_document_linkset.xml"
        );

        final List<Condition> conditions = conditionMapper.mapResources(ehrExtract, patient, Collections.emptyList(), PRACTISE_CODE);

        assertThat(conditions).hasSize(0);
    }

    private void addMedicationRequestsToBundle(Bundle bundle) {
        var planMedicationRequest = new MedicationRequest().setId(AUTHORISE_ID);
        var orderMedicationRequest = new MedicationRequest().setId(PRESCRIBE_ID);

        bundle.addEntry(new BundleEntryComponent().setResource(planMedicationRequest));
        bundle.addEntry(new BundleEntryComponent().setResource(orderMedicationRequest));
    }

    private List<RCMRMT030101UKMedicationStatement> getMedicationStatements() {

        var planMedicationStatement = new RCMRMT030101UKMedicationStatement();
        planMedicationStatement.setId(createIdWithRoot(MEDICATION_STATEMENT_PLAN_ID));
        planMedicationStatement.getMoodCode().add("INT");

        var authorise = new RCMRMT030101UKAuthorise();
        authorise.setId(createIdWithRoot(AUTHORISE_ID));

        var planComponent = new RCMRMT030101UKComponent2();
        planComponent.setEhrSupplyAuthorise(authorise);

        planMedicationStatement.getComponent().add(planComponent);

        var orderMedicationStatement = new RCMRMT030101UKMedicationStatement();
        orderMedicationStatement.setId(createIdWithRoot(MEDICATION_STATEMENT_ORDER_ID));
        orderMedicationStatement.getMoodCode().add("ORD");

        var prescribe = new RCMRMT030101UKPrescribe();
        prescribe.setId(createIdWithRoot(PRESCRIBE_ID));

        var orderComponent = new RCMRMT030101UKComponent2();
        orderComponent.setEhrSupplyPrescribe(prescribe);

        orderMedicationStatement.getComponent().add(orderComponent);

        return List.of(planMedicationStatement, orderMedicationStatement);
    }

    private II createIdWithRoot(String rootValue) {
        var id = new II();
        id.setRoot(rootValue);

        return id;
    }

    private void assertActualProblemExtension(Condition condition) {
        var extension = condition.getExtensionsByUrl(ACTUAL_PROBLEM_URL).getFirst();
        assertThat(extension.getValue()).isInstanceOf(Reference.class);
        assertThat(((Reference) extension.getValue()).getResource()).isInstanceOf(Observation.class);
        assertThat(((Observation) ((Reference) extension.getValue()).getResource()).getId()).isEqualTo(NAMED_STATEMENT_REF_ID);
    }

    private void assertRelatedClinicalContentExtension(Condition condition) {
        var extensions = condition.getExtensionsByUrl(RELATED_CLINICAL_CONTENT_URL);
        assertThat(extensions).hasSize(2);
        assertThat(((Reference) extensions.getFirst().getValue()).getResource().getIdElement().getValue()).isEqualTo(STATEMENT_REF_ID);
        assertThat(((Reference) extensions.get(1).getValue()).getResource().getIdElement().getValue()).isEqualTo(STATEMENT_REF_ID_1);
    }

    private void assertGeneratedComponentsAreCorrect(Condition condition) {
        assertNotNull(condition.getMeta().getProfile().getFirst());
        assertThat(condition.getIdentifierFirstRep().getValue()).isEqualTo(LINKSET_ID);
        assertThat(condition.getCategoryFirstRep().getCodingFirstRep().getDisplay()).isEqualTo("Problem List Item");
    }

    private Bundle buildBundleWithNamedStatementObservation() {
        return new Bundle()
            .addEntry(new BundleEntryComponent()
                .setResource(new Observation().setId(NAMED_STATEMENT_REF_ID)));
    }

    private Bundle buildBundleWithStatementRefObservations() {
        return new Bundle()
            .addEntry(new BundleEntryComponent()
                .setResource(new Observation().setId(STATEMENT_REF_ID)))
            .addEntry(new BundleEntryComponent()
                .setResource(new Observation().setId(STATEMENT_REF_ID_1)));
    }

    private void assertAllConditionsHaveMeta(List<Condition> conditions, Meta expectedMeta) {
        assertAll(conditions.stream().map(condition ->
            () -> assertThat(condition.getMeta()).usingRecursiveComparison().isEqualTo(expectedMeta)
        ));
    }

    @SneakyThrows
    private RCMRMT030101UKEhrExtract unmarshallEhrExtract(String testFilesDirectory, String filename) {
        final File file = FileFactory.getXmlFileFor(testFilesDirectory, filename);
        return unmarshallFile(file, RCMRMT030101UKEhrExtract.class);
    }

    @SneakyThrows
    private RCMRMT030101UKEhrExtract unmarshallEhrExtract(String filename) {
        return unmarshallEhrExtract(TEST_FILES_DIRECTORY, filename);
    }

    private void configureCommonStubs() {
        Mockito.lenient().when(dateTimeMapper.mapDateTime(
            any(String.class)
        )).thenReturn(EHR_EXTRACT_AVAILABILITY_DATETIME);

        Mockito.lenient().when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            eq(META_PROFILE),
            confidentialityCodeCaptor.capture(),
            confidentialityCodeCaptor.capture()
        )).thenReturn(MetaUtil.getMetaFor(META_WITHOUT_SECURITY, META_PROFILE));
    }
}