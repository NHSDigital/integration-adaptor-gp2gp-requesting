package uk.nhs.adaptors.pss.translator.mapper.diagnosticreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.util.ResourceUtils.getFile;

import static uk.nhs.adaptors.pss.translator.util.MetaUtil.MetaType.META_WITH_SECURITY;
import static uk.nhs.adaptors.pss.translator.util.MetaUtil.assertMetaSecurityIsPresent;
import static uk.nhs.adaptors.pss.translator.util.DateFormatUtil.parseToDateTimeType;
import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.DiagnosticReport;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.Specimen;
import org.hl7.v3.RCMRMT030101UKCompoundStatement;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.SneakyThrows;
import uk.nhs.adaptors.pss.translator.util.MetaUtil;
import uk.nhs.adaptors.pss.translator.mapper.DateTimeMapper;
import uk.nhs.adaptors.pss.translator.service.ConfidentialityService;

@ExtendWith(MockitoExtension.class)
public class SpecimenMapperTest {

    private static final String XML_RESOURCES_BASE = "xml/Specimen/";
    private static final String SPECIMEN_PREFIX = "Specimen/";
    private static final String TEST_SPECIMEN_ID = "COMPOUND_STATEMENT_CHILD_ID_1";
    private static final String PRACTICE_CODE = "TEST_PRACTICE_CODE";
    private static final String ACCESSION_IDENTIFIER_VALUE = "SPECIMEN_ROLE_ID_EXTENSION";
    private static final String SPECIMEN_TYPE_TEXT = "EINE KLEINE";
    private static final Patient PATIENT = (Patient) new Patient().setId("PATIENT_TEST_ID");
    private static final DateTimeType SPECIMEN_COLLECTED_DATETIME = parseToDateTimeType("20100223000000");
    private static final String NOTE_TEXT = "Received Date: 2002-03-30 09:21";
    private static final String IDENTIFIER_SYSTEM = "https://PSSAdaptor/TEST_PRACTICE_CODE";
    private static final String DR_ID = "DIAGNOSTIC_REPORT_ID";
    private static final DiagnosticReport DIAGNOSTIC_REPORT_WITH_SPECIMEN = generateDiagnosticReportWithSpecimenReference();
    private static final DiagnosticReport DIAGNOSTIC_REPORT_WITHOUT_SPECIMEN = generateDiagnosticReportWithNoSpecimenReference();
    private static final String SPECIMEN_META_PROFILE = "Specimen-1";
    private static final Meta META_WITH_SECURITY_ADDED = MetaUtil.getMetaFor(META_WITH_SECURITY, SPECIMEN_META_PROFILE);
    private static final String NARRATIVE_STATEMENT_ID = "9326C01E-488B-4EDF-B9C9-529E69EE0361";

    @Mock
    private DateTimeMapper dateTimeMapper;

    @Mock
    private ConfidentialityService confidentialityService;

    @InjectMocks
    private SpecimenMapper specimenMapper;

    @Test
    public void testThatSpecimenIsPopulatedWithMetaSecurityWhenNoPatCompoundStatement() {
        when(dateTimeMapper.mapDateTime(any())).thenCallRealMethod();
        RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("specimen_valid_with_nopat_compound_statement.xml");
        final var compoundStatement = getCompoundStatement(ehrExtract);

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            SPECIMEN_META_PROFILE,
            Optional.empty(),
            compoundStatement.getConfidentialityCode()
        )).thenReturn(META_WITH_SECURITY_ADDED);

        List<Specimen> specimenList = specimenMapper.mapSpecimens(
            ehrExtract, List.of(DIAGNOSTIC_REPORT_WITH_SPECIMEN), PATIENT, PRACTICE_CODE);

        assertThat(specimenList).isNotEmpty();
        final Specimen specimen = specimenList.getFirst();
        assertMetaSecurityIsPresent(META_WITH_SECURITY_ADDED, specimen.getMeta());
    }

    @Test
    public void testHandlingSpecimenNoPatEhrComposition() {
        when(dateTimeMapper.mapDateTime(any())).thenCallRealMethod();
        RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("specimen_valid_with_nopat_ehr_composition.xml");
        final var ehrComposition = getEhrComposition(ehrExtract);

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            SPECIMEN_META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            Optional.empty()
        )).thenReturn(META_WITH_SECURITY_ADDED);

        List<Specimen> specimenList = specimenMapper.mapSpecimens(
            ehrExtract, List.of(DIAGNOSTIC_REPORT_WITH_SPECIMEN), PATIENT, PRACTICE_CODE);

        assertThat(specimenList).isNotEmpty();
        final Specimen specimen = specimenList.getFirst();
        assertMetaSecurityIsPresent(META_WITH_SECURITY_ADDED, specimen.getMeta());
    }

    @Test
    void testSpecimenIsMapped() {
        when(dateTimeMapper.mapDateTime(any())).thenCallRealMethod();
        RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("specimen_valid.xml");
        final var compoundStatement = getCompoundStatement(ehrExtract);

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            SPECIMEN_META_PROFILE,
            Optional.empty(),
            compoundStatement.getConfidentialityCode()
        )).thenReturn(META_WITH_SECURITY_ADDED);

        List<Specimen> specimenList = specimenMapper.mapSpecimens(
            ehrExtract, List.of(DIAGNOSTIC_REPORT_WITH_SPECIMEN), PATIENT, PRACTICE_CODE
        );

        assertThat(specimenList).isNotEmpty();

        final Specimen specimen = specimenList.getFirst();
        checkFixedValues(specimen);
        assertThat(specimen.getNote().getFirst().getText()).isEqualTo(NOTE_TEXT);
        assertThat(specimen.getAccessionIdentifier().getValue()).isEqualTo(ACCESSION_IDENTIFIER_VALUE);
        assertThat(specimen.getType().getText()).isEqualTo(SPECIMEN_TYPE_TEXT);
        assertThat(specimen.getCollection().getCollected().toString()).isEqualTo(SPECIMEN_COLLECTED_DATETIME.toString());
    }

    @Test
    void testSpecimenIsMappedWithNoOptionalFields() {
        RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("specimen_no_optional_fields.xml");
        final var compoundStatement = getCompoundStatement(ehrExtract);

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            SPECIMEN_META_PROFILE,
            Optional.empty(),
            compoundStatement.getConfidentialityCode()
        )).thenReturn(META_WITH_SECURITY_ADDED);

        List<Specimen> specimenList = specimenMapper.mapSpecimens(
            ehrExtract, List.of(DIAGNOSTIC_REPORT_WITH_SPECIMEN), PATIENT, PRACTICE_CODE
        );

        Specimen specimen = specimenList.getFirst();
        assertThat(specimen.getCollection().getCollected()).isNull();
        assertThat(specimen.hasAccessionIdentifier()).isFalse();
        assertThat(specimen.getType().getText()).isNullOrEmpty();
        checkFixedValues(specimen);
    }

    void checkFixedValues(Specimen specimen) {
        assertThat(specimen.getId()).isEqualTo(TEST_SPECIMEN_ID);
        assertThat(specimen.getMeta().getProfile().getFirst().getValue()).isEqualTo(SPECIMEN_META_PROFILE);
        assertThat(specimen.getIdentifierFirstRep().getSystem()).isEqualTo(IDENTIFIER_SYSTEM);
        assertThat(specimen.getIdentifierFirstRep().getValue()).isEqualTo(TEST_SPECIMEN_ID);
        assertThat(specimen.getSubject().getResource().getIdElement().getValue()).isEqualTo(PATIENT.getId());
    }

    @Test
    void testInvalidSpecimenIsNotMapped() {
        RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("specimen_invalid.xml");
        List<Specimen> specimenList = specimenMapper.mapSpecimens(
            ehrExtract, List.of(DIAGNOSTIC_REPORT_WITHOUT_SPECIMEN), PATIENT, PRACTICE_CODE
        );

        assertThat(specimenList).isEmpty();
    }

    @Test
    void When_RemoveSurplusObservationComments_With_ChildNarrativeStatements_Expect_CommentsRemoved() {
        RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("specimen_valid.xml");

        var outputList = specimenMapper
            .removeSurplusObservationComments(ehrExtract, getObservationComments());

        assertThat(outputList).hasSize(2);

        List<String> ids = outputList.stream()
            .map(Observation::getId)
            .toList();

        assertThat(ids).doesNotContain(NARRATIVE_STATEMENT_ID);
    }

    @Test
    void When_RemoveSurplusObservationComments_Without_NoChildNarrativeStatements_Expect_CommentsUnchanged() {
        RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("specimen_valid_without_comment.xml");

        var observationComments = getObservationComments();

        var outputList = specimenMapper
            .removeSurplusObservationComments(ehrExtract, observationComments);

        assertThat(outputList).isEqualTo(observationComments);
    }

    private ArrayList<Observation> getObservationComments() {
        var observationComments = new ArrayList<Observation>();

        var comment = new Observation();
        comment.setId(NARRATIVE_STATEMENT_ID);
        observationComments.add(comment);

        var comment2 = new Observation();
        comment2.setId(UUID.randomUUID().toString());
        observationComments.add(comment2);

        var comment3 = new Observation();
        comment3.setId(UUID.randomUUID().toString());
        observationComments.add(comment3);

        return observationComments;
    }

    private static DiagnosticReport generateDiagnosticReportWithSpecimenReference() {
        DiagnosticReport diagnosticReport = (DiagnosticReport) new DiagnosticReport().setId(DR_ID);
        List<Reference> specimen = List.of(new Reference(SPECIMEN_PREFIX + TEST_SPECIMEN_ID));
        diagnosticReport.setSpecimen(specimen);

        return diagnosticReport;
    }

    private static DiagnosticReport generateDiagnosticReportWithNoSpecimenReference() {
        return (DiagnosticReport) new DiagnosticReport().setId(DR_ID);
    }



    private RCMRMT030101UKCompoundStatement getCompoundStatement(RCMRMT030101UKEhrExtract ehrExtract) {
        return ehrExtract.getComponent().getFirst()
            .getEhrFolder().getComponent().getFirst()
            .getEhrComposition().getComponent().getFirst()
            .getCompoundStatement().getComponent().getFirst()
            .getCompoundStatement();
    }

    private RCMRMT030101UKEhrComposition getEhrComposition(RCMRMT030101UKEhrExtract ehrExtract) {

        return ehrExtract.getComponent().getFirst()
            .getEhrFolder().getComponent().getFirst()
            .getEhrComposition();
    }

    @SneakyThrows
    private RCMRMT030101UKEhrExtract unmarshallEhrExtract(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES_BASE + fileName), RCMRMT030101UKEhrExtract.class);
    }
}
