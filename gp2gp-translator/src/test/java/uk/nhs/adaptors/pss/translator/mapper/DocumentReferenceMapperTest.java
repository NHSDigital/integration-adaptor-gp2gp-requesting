package uk.nhs.adaptors.pss.translator.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hl7.fhir.dstu3.model.Enumerations.DocumentReferenceStatus;
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
import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.DocumentReference;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.v3.CD;
import org.hl7.v3.CV;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.SneakyThrows;
import uk.nhs.adaptors.connector.model.PatientAttachmentLog;
import uk.nhs.adaptors.pss.translator.FileFactory;
import uk.nhs.adaptors.pss.translator.util.MetaUtil;
import uk.nhs.adaptors.pss.translator.TestUtility;
import uk.nhs.adaptors.pss.translator.service.ConfidentialityService;
import uk.nhs.adaptors.pss.translator.util.DegradedCodeableConcepts;
import static uk.nhs.adaptors.common.util.CodeableConceptUtils.createCodeableConcept;

@ExtendWith(MockitoExtension.class)
class DocumentReferenceMapperTest {

    private static final String NARRATIVE_STATEMENT_ROOT_ID = "5E496953-065B-41F2-9577-BE8F2FBD0757";
    private static final String META_PROFILE = "DocumentReference-1";
    private static final String CODING_DISPLAY = "Original Text document";
    private static final String IDENTIFIER_SYSTEM = "https://PSSAdaptor/TESTPRACTISECODE";
    private static final String NARRATIVE_STATEMENT_TYPE = "Record Attachment";
    private static final String FILENAME = "31B75ED0-6E88-11EA-9384-E83935108FD5_patient-attachment.txt";
    private static final String URL = "file://localhost/31B75ED0-6E88-11EA-9384-E83935108FD5_patient-attachment.txt";
    private static final Integer ATTACHMENT_SIZE = 128000;
    private static final String CONTENT_TYPE = "text/plain";
    private static final String ENCOUNTER_ID = "62A39454-299F-432E-993E-5A6232B4E099";
    private static final String PATIENT_ID = "45329454-299F-432E-993E-5A6232B4E099";
    private static final Organization AUTHOR_ORG = new Organization().addIdentifier(new Identifier().setValue("TESTPRACTISECODE"));
    private static final String PLACEHOLDER = "GP2GP generated placeholder. Original document not available. See notes for details";
    private static final Integer EXPECTED_DOCUMENT_REFERENCE_COUNT = 3;
    private static final String TEST_FILES_DIRECTORY = "DocumentReference";
    private static final Meta META_WITH_SECURITY_ADDED = MetaUtil.getMetaFor(META_WITH_SECURITY, META_PROFILE);
    private static final Meta META_WITHOUT_SECURITY_ADDED = MetaUtil.getMetaFor(META_WITHOUT_SECURITY, META_PROFILE);
    private static final String NOPAT = "NOPAT";

    private static final String SNOMED_SYSTEM = "http://snomed.info/sct";

    @Mock
    private CodeableConceptMapper codeableConceptMapper;
    @Mock
    private ConfidentialityService confidentialityService;
    @InjectMocks
    private DocumentReferenceMapper documentReferenceMapper;
    @Captor
    private ArgumentCaptor<Optional<CV>> confidentialityCodeCaptor;

    @BeforeEach
    void setup() {
        configureCommonStubs();
    }

    @Test
    void mapNarrativeStatementToDocumentReferenceWithValidData() {
        var ehrExtract = unmarshallEhrExtract("narrative_statement_has_referred_to_external_document.xml");
        List<DocumentReference> documentReferences = documentReferenceMapper.mapResources(ehrExtract, createPatient(),
            getEncounterList(), AUTHOR_ORG, createAttachmentList());
        var documentReference = documentReferences.getFirst();

        assertFullValidData(documentReference);
    }

    @Test
    void mapNarrativeStatementToDocumentReferenceWithOptionalData() {
        var ehrExtract = unmarshallEhrExtract("narrative_statement_has_referred_to_external_document_with_optional_data.xml");
        List<DocumentReference> documentReferences = documentReferenceMapper.mapResources(ehrExtract, createPatient(),
            getEncounterList(), AUTHOR_ORG, createAttachmentList());
        var documentReference = documentReferences.getFirst();

        assertOptionalValidData(documentReference);
    }

    @Test
    void mapMultipleNarrativeStatementToDocumentReference() {
        var ehrExtract = unmarshallEhrExtract("multiple_narrative_statements_has_referred_to_external_document.xml");
        List<DocumentReference> documentReferences = documentReferenceMapper.mapResources(ehrExtract, createPatient(),
            getEncounterList(), AUTHOR_ORG, createAttachmentList());

        assertThat(documentReferences).hasSize(EXPECTED_DOCUMENT_REFERENCE_COUNT);
    }

    @Test
    void mapNarrativeStatementToDocumentReferenceWithAttachments() {
        var ehrExtract = unmarshallEhrExtract("narrative_statement_has_referred_to_external_document.xml");
        List<DocumentReference> documentReferences = documentReferenceMapper.mapResources(ehrExtract, createPatient(),
            getEncounterList(), AUTHOR_ORG, createAttachmentList());
        var documentReference = documentReferences.getFirst();

        assertAttachmentData(documentReference);
    }

    @Test
    void mapNarrativeStatementToDocumentReferenceWithAbsentAttachment() {
        var ehrExtract = unmarshallEhrExtract("narrative_statement_has_referred_to_external_document_with_absent_attachment.xml");
        List<DocumentReference> documentReferences = documentReferenceMapper.mapResources(ehrExtract, createPatient(),
            getEncounterList(), AUTHOR_ORG, new ArrayList<>());
        var documentReference = documentReferences.getFirst();

        assertDocumentReferenceWithAbsentAttachment(documentReference);
    }

    @Test
    void mapNarrativeStatementToDocumentReferenceWithInvalidEncounterReference() {
        var ehrExtract = unmarshallEhrExtract("narrative_statement_with_invalid_encounter.xml");
        List<DocumentReference> documentReferences = documentReferenceMapper.mapResources(ehrExtract, createPatient(),
            getEncounterList(), AUTHOR_ORG, createAttachmentList());
        var documentReference = documentReferences.getFirst();

        assertDocumentReferenceWithInvalidEncounter(documentReference);
    }

    @Test
    void mapNestedNarrativeStatement() {
        var ehrExtract = unmarshallEhrExtract("nested_narrative_statements.xml");

        List<DocumentReference> documentReferences = documentReferenceMapper.mapResources(ehrExtract, createPatient(),
            getEncounterList(), AUTHOR_ORG, createAttachmentList());
        var documentReference = documentReferences.getFirst();

        assertDocumentReferenceMappedFromNestedNarrativeStatement(documentReference);
    }

    @Test
    void mapNarrativeStatementToDocumentReferenceWithNullFlavors() {
        var ehrExtract = unmarshallEhrExtract("narrative_statement_null_flavors.xml");
        List<DocumentReference> documentReferences = documentReferenceMapper.mapResources(ehrExtract, createPatient(),
            getEncounterList(), AUTHOR_ORG, createAttachmentList());
        var documentReference = documentReferences.getFirst();

        assertThat(documentReference.getCreatedElement().asStringValue()).isNull();
    }

    @Test
    void mapNarrativeStatementWithSnomedCode() {
        var codeableConcept = createCodeableConcept(null, SNOMED_SYSTEM, CODING_DISPLAY);
        when(codeableConceptMapper.mapToCodeableConcept(any())).thenReturn(codeableConcept);

        var ehrExtract = unmarshallEhrExtract("nested_narrative_statements.xml");

        List<DocumentReference> documentReferences = documentReferenceMapper.mapResources(ehrExtract, createPatient(),
            getEncounterList(), AUTHOR_ORG, createAttachmentList());
        var documentReference = documentReferences.getFirst();

        assertEquals(codeableConcept, documentReference.getType());
    }

    @Test
    void mapNarrativeStatementWithoutSnomedCode() {
        var codeableConcept = createCodeableConcept(null, "not-a-snomed-system", CODING_DISPLAY);
        when(codeableConceptMapper.mapToCodeableConcept(any())).thenReturn(codeableConcept);

        var ehrExtract = unmarshallEhrExtract("nested_narrative_statements.xml");

        List<DocumentReference> documentReferences = documentReferenceMapper.mapResources(ehrExtract, createPatient(),
            getEncounterList(), AUTHOR_ORG, createAttachmentList());
        var documentReference = documentReferences.getFirst();

        assertThat(documentReference.getType().getCodingFirstRep())
            .isEqualTo(DegradedCodeableConcepts.DEGRADED_OTHER);
    }

    @Test
    void When_NarrativeStatement_With_ExternalDocumentAndNopatConfidentialityCode_Expect_MetaFromConfidentialityServiceWithSecurity() {
        final RCMRMT030101UKEhrExtract ehrExtract =
            unmarshallEhrExtract("narrative_statement_has_referred_to_external_document_with_nopat.xml");

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            eq(META_PROFILE),
            confidentialityCodeCaptor.capture()
        )).thenReturn(META_WITH_SECURITY_ADDED);

        final List<DocumentReference> documentReferences = documentReferenceMapper.mapResources(ehrExtract, createPatient(),
            getEncounterList(), AUTHOR_ORG, createAttachmentList());

        final CV externalDocumentConfidentialityCode = confidentialityCodeCaptor
            .getValue()
            .orElseThrow(TestUtility.NoConfidentialityCodePresentException::new);

        assertAll(
            () -> documentReferences.forEach(this::assertMetaHasSecurity),
            () -> assertThat(externalDocumentConfidentialityCode.getCode()).isEqualTo(NOPAT)
        );
    }

    @Test
    void When_NarrativeStatement_With_ExternalDocumentAndNoConfidentialityCode_Expect_MetaFromConfidentialityWithoutSecurity() {
        final RCMRMT030101UKEhrExtract ehrExtract =
            unmarshallEhrExtract("narrative_statement_has_referred_to_external_document.xml");

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            eq(META_PROFILE),
            confidentialityCodeCaptor.capture()
        )).thenReturn(META_WITHOUT_SECURITY_ADDED);

        final List<DocumentReference> documentReferences = documentReferenceMapper.mapResources(ehrExtract, createPatient(),
            getEncounterList(), AUTHOR_ORG, createAttachmentList());

        final Optional<CV> externalDocumentConfidentialityCode = confidentialityCodeCaptor
            .getValue();

        assertAll(
            () -> documentReferences.forEach(this::assertMetaHasNoSecurity),
            () -> assertThat(externalDocumentConfidentialityCode).isEmpty()
        );
    }

    private void assertMetaHasSecurity(DocumentReference documentReference) {
        final Meta meta = documentReference.getMeta();
        assertThat(meta).usingRecursiveComparison().isEqualTo(META_WITH_SECURITY_ADDED);
    }

    private void assertMetaHasNoSecurity(DocumentReference documentReference) {
        final Meta meta = documentReference.getMeta();
        assertThat(meta).usingRecursiveComparison().isEqualTo(META_WITHOUT_SECURITY_ADDED);
    }

    private void assertDocumentReferenceMappedFromNestedNarrativeStatement(DocumentReference documentReference) {
        assertThat(documentReference.getId()).isEqualTo(NARRATIVE_STATEMENT_ROOT_ID);
        assertThat(documentReference.getMeta().getProfile().getFirst().getValue()).isEqualTo(META_PROFILE);
        assertThat(documentReference.getStatus()).isEqualTo(DocumentReferenceStatus.CURRENT);
        assertThatIdentifierIsValid(documentReference.getIdentifierFirstRep(), documentReference.getId());
        assertThat(documentReference.getType().getText()).isEqualTo(NARRATIVE_STATEMENT_TYPE);
        assertThat(documentReference.getAuthor().getFirst().getReference()).isEqualTo("Practitioner/2D70F602-6BB1-47E0-B2EC-39912A59787D");
        assertThat(documentReference.getDescription()).isEqualTo("Some example text");
        assertThat(documentReference.getIndexedElement().getValue()).isEqualTo("2010-01-14");
        assertThat(documentReference.getCreatedElement().asStringValue()).isEqualTo("2019-07-08T13:35:00+00:00");
        assertNotNull(documentReference.getSubject().getResource());
        assertThat(documentReference.getSubject().getResource().getIdElement().getValue()).isEqualTo(PATIENT_ID);
        assertThat(documentReference.getContext().getEncounter().getResource().getIdElement().getValue()).isEqualTo(ENCOUNTER_ID);
        assertAttachmentData(documentReference);
    }

    private void assertFullValidData(DocumentReference documentReference) {
        assertThat(documentReference.getId()).isEqualTo(NARRATIVE_STATEMENT_ROOT_ID);
        assertThat(documentReference.getMeta().getProfile().getFirst().getValue()).isEqualTo(META_PROFILE);
        assertThat(documentReference.getStatus()).isEqualTo(DocumentReferenceStatus.CURRENT);
        assertThatIdentifierIsValid(documentReference.getIdentifierFirstRep(), documentReference.getId());
        assertThat(documentReference.getType().getText()).isEqualTo(NARRATIVE_STATEMENT_TYPE);
        assertThat(documentReference.getAuthor().getFirst().getReference()).isEqualTo("Practitioner/2D70F602-6BB1-47E0-B2EC-39912A59787D");
        assertThat(documentReference.getDescription()).isEqualTo("Some example text");
        assertThat(documentReference.getIndexedElement().getValue()).isEqualTo("2010-01-14");
        assertThat(documentReference.getCreatedElement().asStringValue()).isEqualTo("2019-07-08T13:35:00+00:00");
        assertNotNull(documentReference.getSubject().getResource());
        assertThat(documentReference.getSubject().getResource().getIdElement().getValue()).isEqualTo(PATIENT_ID);
        assertThat(documentReference.getContext().getEncounter().getResource().getIdElement().getValue()).isEqualTo(ENCOUNTER_ID);
        assertAttachmentData(documentReference);
    }

    private void assertOptionalValidData(DocumentReference documentReference) {
        assertThat(documentReference.getId()).isEqualTo(NARRATIVE_STATEMENT_ROOT_ID);
        assertThat(documentReference.getMeta().getProfile().getFirst().getValue()).isEqualTo(META_PROFILE);
        assertThat(documentReference.getStatus()).isEqualTo(DocumentReferenceStatus.CURRENT);
        assertThatIdentifierIsValid(documentReference.getIdentifierFirstRep(), documentReference.getId());
        assertThat(documentReference.getType().getText()).isEqualTo(NARRATIVE_STATEMENT_TYPE);
        assertThat(documentReference.getAuthor().getFirst().getReference()).isEqualTo("Practitioner/2D70F602-6BB1-47E0-B2EC-39912A59787D");
        assertThat(documentReference.getDescription()).isEqualTo("31B75ED0-6E88-11EA-9384-E83935108FD5_patient-attachment.txt");
        assertThat(documentReference.getCreatedElement().asStringValue()).isEqualTo("2019-07-08T13:35:00+00:00");
        assertAttachmentData(documentReference);
    }

    private void assertAttachmentData(DocumentReference documentReference) {
        assertThat(documentReference.getContent().getFirst().getAttachment().getTitle()).isNull();
        assertThat(documentReference.getContent().getFirst().getAttachment().getUrl()).isEqualTo(URL);
        assertThat(documentReference.getContent().getFirst().getAttachment().getContentType()).isEqualTo(CONTENT_TYPE);
        assertThat(documentReference.getContent().getFirst().getAttachment().getSize()).isEqualTo(ATTACHMENT_SIZE);
    }

    private void assertDocumentReferenceWithAbsentAttachment(DocumentReference documentReference) {
        assertThat(documentReference.getId()).isEqualTo(NARRATIVE_STATEMENT_ROOT_ID);
        assertThat(documentReference.getContent().getFirst().getAttachment().getTitle()).isEqualTo(PLACEHOLDER);
        assertNotNull(documentReference.getContent().getFirst().getAttachment().getUrl());
        assertFalse(documentReference.getContent().getFirst().getAttachment().hasSize());
        assertThat(documentReference.getContent().getFirst().getAttachment().getContentType()).isEqualTo(CONTENT_TYPE);
    }

    private void assertDocumentReferenceWithInvalidEncounter(DocumentReference documentReference) {
        assertThat(documentReference.getId()).isEqualTo(NARRATIVE_STATEMENT_ROOT_ID);
        assertThat(documentReference.getMeta().getProfile().getFirst().getValue()).isEqualTo(META_PROFILE);
        assertThat(documentReference.getStatus()).isEqualTo(DocumentReferenceStatus.CURRENT);
        assertThatIdentifierIsValid(documentReference.getIdentifierFirstRep(), documentReference.getId());
        assertThat(documentReference.getType().getText()).isEqualTo(NARRATIVE_STATEMENT_TYPE);
        assertNull(documentReference.getContext().getEncounter().getResource());
    }

    private void assertThatIdentifierIsValid(Identifier identifier, String id) {
        assertThat(identifier.getSystem()).isEqualTo(IDENTIFIER_SYSTEM);
        assertThat(identifier.getValue()).isEqualTo(id);
    }

    private static Patient createPatient() {
        Patient patient = new Patient();
        patient.setId(PATIENT_ID);
        return patient;
    }

    private static List<PatientAttachmentLog> createAttachmentList() {
        return Collections.singletonList(
                PatientAttachmentLog.builder()
                    .filename(FILENAME)
                    .postProcessedLengthNum(ATTACHMENT_SIZE)
                    .mid("1")
                    .build());
    }

    private List<Encounter> getEncounterList() {
        var encounter = new Encounter();
        encounter.setId(ENCOUNTER_ID);
        return List.of(encounter);
    }

    @SneakyThrows
    private RCMRMT030101UKEhrExtract unmarshallEhrExtract(String fileName) {
        final File file = FileFactory.getXmlFileFor(TEST_FILES_DIRECTORY, fileName);
        return unmarshallFile(file, RCMRMT030101UKEhrExtract.class);
    }

    private void configureCommonStubs() {
        final CodeableConcept concept = createCodeableConcept(null, SNOMED_SYSTEM, CODING_DISPLAY);

        Mockito.lenient().when(codeableConceptMapper.mapToCodeableConcept(
            any(CD.class)
        )).thenReturn(concept);

        Mockito.lenient().when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            eq(META_PROFILE),
            confidentialityCodeCaptor.capture()
        )).thenReturn(MetaUtil.getMetaFor(META_WITHOUT_SECURITY, META_PROFILE));
    }
}