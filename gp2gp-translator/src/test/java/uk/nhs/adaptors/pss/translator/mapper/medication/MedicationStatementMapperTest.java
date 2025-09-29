package uk.nhs.adaptors.pss.translator.mapper.medication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hl7.fhir.dstu3.model.MedicationStatement.MedicationStatementStatus.ACTIVE;
import static org.hl7.fhir.dstu3.model.MedicationStatement.MedicationStatementStatus.COMPLETED;
import static org.hl7.fhir.dstu3.model.MedicationStatement.MedicationStatementStatus.STOPPED;
import static org.hl7.fhir.dstu3.model.MedicationStatement.MedicationStatementTaken.UNK;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import static uk.nhs.adaptors.pss.translator.util.MetaUtil.MetaType.META_WITHOUT_SECURITY;
import static uk.nhs.adaptors.pss.translator.util.MetaUtil.MetaType.META_WITH_SECURITY;
import static uk.nhs.adaptors.pss.translator.TestUtility.GET_EHR_COMPOSITION;
import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import jakarta.xml.bind.JAXBException;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.MedicationStatement;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.hl7.v3.CV;
import org.hl7.v3.RCMRMT030101UKAuthorise;
import org.hl7.v3.RCMRMT030101UKComponent;
import org.hl7.v3.RCMRMT030101UKComponent2;
import org.hl7.v3.RCMRMT030101UKComponent3;
import org.hl7.v3.RCMRMT030101UKComponent4;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKEhrFolder;
import org.hl7.v3.RCMRMT030101UKMedicationStatement;
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
import uk.nhs.adaptors.pss.translator.FileFactory;
import uk.nhs.adaptors.pss.translator.util.MetaUtil;
import uk.nhs.adaptors.pss.translator.service.ConfidentialityService;
import uk.nhs.adaptors.pss.translator.util.DateFormatUtil;

@ExtendWith(MockitoExtension.class)
class MedicationStatementMapperTest {

    private static final String META_PROFILE = "MedicationStatement-1";
    private static final String PRACTISE_CODE = "TESTPRACTISECODE";
    private static final String TEST_ID = "TEST_ID";
    private static final String MEDICATION_ID = "MEDICATION_ID";
    private static final String TAKE_ONE_DAILY = "TAKE ONE DAILY";
    private static final String NOPAT = "NOPAT";
    private static final String TEST_FILE_DIRECTORY = "MedicationStatement";

    private static final String PRESCRIBED_BY_ANOTHER_ORGANISATION_CODE = "prescribed-by-another-organisation";
    private static final String PRESCRIBED_BY_ANOTHER_ORGANISATION_DISPLAY = "Prescribed by another organisation";

    @Mock
    private MedicationMapper medicationMapper;
    @Mock
    private ConfidentialityService confidentialityService;
    @InjectMocks
    private MedicationStatementMapper medicationStatementMapper;
    @Captor
    private ArgumentCaptor<Optional<CV>> confidentialityCodeCaptor;

    @BeforeEach
    void beforeEach() {
        Mockito.lenient().when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            eq(META_PROFILE),
            confidentialityCodeCaptor.capture(),
            confidentialityCodeCaptor.capture()
        )).thenReturn(MetaUtil.getMetaFor(META_WITHOUT_SECURITY, META_PROFILE));
    }

    @Test
    void When_MappingPrescribeResourceWithAnotherOrg_Expect_AllFieldsToBeMappedCorrectly() throws JAXBException {
        final File file = FileFactory.getXmlFileFor("MedicationStatement", "ehrExtract3.xml");
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallFile(file, RCMRMT030101UKEhrExtract.class);
        final RCMRMT030101UKEhrComposition ehrComposition = GET_EHR_COMPOSITION.apply(ehrExtract);
        final RCMRMT030101UKMedicationStatement medicationStatement =
            unmarshallMedicationStatement("medicationStatementAuthoriseAllOptionals_MedicationStatement.xml");
        final Optional<RCMRMT030101UKAuthorise> authorise = medicationStatement.getComponent()
            .stream()
            .filter(RCMRMT030101UKComponent2::hasEhrSupplyAuthorise)
            .map(RCMRMT030101UKComponent2::getEhrSupplyAuthorise)
            .findFirst();
        authorise.get().getCode().setDisplayName(PRESCRIBED_BY_ANOTHER_ORGANISATION_DISPLAY);

        final MedicationStatement result = medicationStatementMapper.mapToMedicationStatement(
            ehrExtract,
            ehrComposition,
            medicationStatement,
            authorise.get(),
            PRACTISE_CODE,
            new DateTimeType());

        assertAll(
            () -> assertEquals(PRESCRIBED_BY_ANOTHER_ORGANISATION_CODE,
                               ((CodeableConcept) result.getExtension().get(0).getValue()).getCoding().get(0).getCode()),
            () -> assertEquals(PRESCRIBED_BY_ANOTHER_ORGANISATION_DISPLAY,
                               ((CodeableConcept) result.getExtension().get(0).getValue()).getCoding().get(0).getDisplay()));
    }

    @Test
    void When_MappingPrescribeResourceWithNoOptionals_Expect_AllFieldsToBeMappedCorrectly() throws JAXBException {
        final File file = FileFactory.getXmlFileFor("MedicationStatement", "ehrExtract3.xml");
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallFile(file, RCMRMT030101UKEhrExtract.class);
        final RCMRMT030101UKEhrComposition ehrComposition = GET_EHR_COMPOSITION.apply(ehrExtract);
        final RCMRMT030101UKMedicationStatement medicationStatement =
            unmarshallMedicationStatement("medicationStatementAuthoriseAllOptionals_MedicationStatement.xml");
        final Optional<RCMRMT030101UKAuthorise> authorise = medicationStatement.getComponent()
            .stream()
            .filter(RCMRMT030101UKComponent2::hasEhrSupplyAuthorise)
            .map(RCMRMT030101UKComponent2::getEhrSupplyAuthorise)
            .findFirst();

        when(medicationMapper.extractMedicationReference(any(RCMRMT030101UKMedicationStatement.class)))
            .thenReturn(getMedicationReference());

        assertThat(authorise).isPresent();

        final MedicationStatement result = medicationStatementMapper.mapToMedicationStatement(
            ehrExtract,
            ehrComposition,
            medicationStatement,
            authorise.get(),
            PRACTISE_CODE,
            new DateTimeType()
        );

        var lastIssuedDate = result.getExtensionsByUrl(
            "https://fhir.nhs.uk/STU3/StructureDefinition/Extension-CareConnect-GPC-MedicationStatementLastIssueDate-1");
        assertThat(lastIssuedDate).hasSize(1);
        var dateTime = (DateTimeType) lastIssuedDate.getFirst().getValue();
        assertThat(dateTime.getValue()).isEqualTo(DateFormatUtil.parseToDateTimeType("20060428").getValue());

        var prescribingAgency = result
            .getExtensionsByUrl("https://fhir.nhs.uk/STU3/StructureDefinition/Extension-CareConnect-GPC-PrescribingAgency-1");

        assertAll(
            () -> assertThat(prescribingAgency).hasSize(1),
            () -> assertThat(result.getTaken()).isEqualTo(UNK),
            () -> assertThat(result.getDosageFirstRep().getText()).isEqualTo(TAKE_ONE_DAILY),
            () -> assertThat(result.getBasedOnFirstRep().getReferenceElement().getIdPart()).isEqualTo(TEST_ID),
            () -> assertThat(result.getMedicationReference().getReferenceElement().getIdPart()).isEqualTo(MEDICATION_ID)
        );
    }

    @Test
    void When_MappingPrescribeResourceWithNoLastIssueDate_Expect_AllFieldsToBeMappedCorrectly() {
        var medicationStatement = unmarshallMedicationStatement("medicationStatementAuthoriseNoOptionals_MedicationStatement.xml");
        var authorise = medicationStatement.getComponent()
            .stream()
            .filter(RCMRMT030101UKComponent2::hasEhrSupplyAuthorise)
            .map(RCMRMT030101UKComponent2::getEhrSupplyAuthorise)
            .findFirst();

        when(medicationMapper.extractMedicationReference(any(RCMRMT030101UKMedicationStatement.class)))
            .thenReturn(getMedicationReference());

        assertThat(authorise).isPresent();

        final MedicationStatement result = medicationStatementMapper.mapToMedicationStatement(
            new RCMRMT030101UKEhrExtract(),
            new RCMRMT030101UKEhrComposition(),
            medicationStatement,
            authorise.get(),
            PRACTISE_CODE,
            new DateTimeType()
        );

        var lastIssuedDate = result.getExtensionsByUrl(
            "https://fhir.nhs.uk/STU3/StructureDefinition/Extension-CareConnect-GPC-MedicationStatementLastIssueDate-1");
        assertThat(lastIssuedDate).isEmpty();

        var prescribingAgency = result
            .getExtensionsByUrl("https://fhir.nhs.uk/STU3/StructureDefinition/Extension-CareConnect-GPC-PrescribingAgency-1");

        assertAll(
            () -> assertThat(prescribingAgency).hasSize(1),
            () -> assertThat(result.getTaken()).isEqualTo(UNK),
            () -> assertThat(result.getStatus()).isEqualTo(ACTIVE),
            () -> assertThat(result.getDosageFirstRep().getText()).isEqualTo(TAKE_ONE_DAILY),
            () -> assertThat(result.getBasedOnFirstRep().getReferenceElement().getIdPart()).isEqualTo(TEST_ID),
            () -> assertThat(result.getMedicationReference().getReferenceElement().getIdPart()).isEqualTo(MEDICATION_ID)
        );
    }

    @Test
    void When_MapToMedicationStatement_WithDiscontinue_WithAvailabilityTime_Expect_PeriodEndMappedAndStatusStopped() {
        var expectedStartDate = "2010-01-14";
        var expectedEndDate = "2010-04-26";

        var result =
            mapMedicationStatementFromEhrFile("ehrExtract_discontinue.xml", new DateTimeType());
        var effectivePeriod = result.getEffectivePeriod();

        assertThat(effectivePeriod.getStartElement().toHumanDisplay()).isEqualTo(expectedStartDate);
        assertThat(effectivePeriod.getEndElement().toHumanDisplay()).isEqualTo(expectedEndDate);
        assertThat(result.getStatus()).isEqualTo(STOPPED);
    }

    @Test
    void When_MapToMedicationStatement_WithDiscontinue_WithMissingAvailabilityTime_Expect_PeriodEndMappedAndStatusCompleted() {
        var expectedStartDate = "2010-01-14";

        var result =
            mapMedicationStatementFromEhrFile("ehrExtract_discontinueMissingAvailabilityTime.xml", new DateTimeType());
        var effectivePeriod = result.getEffectivePeriod();

        assertThat(effectivePeriod.getStartElement().toHumanDisplay()).isEqualTo(expectedStartDate);
        assertThat(effectivePeriod.getEndElement().toHumanDisplay()).isEqualTo(expectedStartDate);
        assertThat(result.getStatus()).isEqualTo(COMPLETED);
    }

    @Test
    void When_MapToMedicationStatement_WithCompletedStatus_WithAuthoriseEffectiveTimeHigh_Expect_PeriodEndMapped() {
        var expectedStartDate = "2010-04-27";
        var expectedEndDate = "2010-06-27";

        var result =
            mapMedicationStatementFromEhrFile("ehrExtract_authorise_effectiveTimeHigh.xml", new DateTimeType());
        var effectivePeriod = result.getEffectivePeriod();

        assertThat(effectivePeriod.getStartElement().toHumanDisplay()).isEqualTo(expectedStartDate);
        assertThat(effectivePeriod.getEndElement().toHumanDisplay()).isEqualTo(expectedEndDate);
        assertThat(result.getStatus()).isEqualTo(COMPLETED);
    }

    @Test
    void When_MapToMedicationStatement_WithCompletedStatus_WithStatementEffectiveTimeHigh_Expect_PeriodEndMapped() {
        var expectedStartDate = "2010-01-14";
        var expectedEndDate = "2010-06-26";

        var result = mapMedicationStatementFromEhrFile("ehrExtract_effectiveTimeHigh.xml", new DateTimeType());
        var effectivePeriod = result.getEffectivePeriod();

        assertThat(effectivePeriod.getStartElement().toHumanDisplay()).isEqualTo(expectedStartDate);
        assertThat(effectivePeriod.getEndElement().toHumanDisplay()).isEqualTo(expectedEndDate);
        assertThat(result.getStatus()).isEqualTo(COMPLETED);
    }

    @Test
    void When_MapToMedicationStatement_WithCompletedStatus_WithNoValidTimes_Expect_StartAndEndTimesEqualAuthoredOn() {
        var authoredOn = new DateTimeType("2023-01-27");

        var result = mapMedicationStatementFromEhrFile("ehrExtract_noValidTimes.xml", authoredOn);
        var effectivePeriod = result.getEffectivePeriod();

        assertThat(effectivePeriod.getStartElement()).isEqualTo(authoredOn);
        assertThat(effectivePeriod.getEndElement()).isEqualTo(authoredOn);
        assertThat(result.getStatus()).isEqualTo(COMPLETED);
    }

    @Test
    void When_MapToMedicationStatement_WithDiscontinue_WithNoValidTimes_Expect_StartAndEndTimesEqualAuthoredOn() {
        var authoredOn = new DateTimeType("2023-01-27");

        var result =
            mapMedicationStatementFromEhrFile("ehrExtract_discontinue_noValidTimes.xml", authoredOn);
        var effectivePeriod = result.getEffectivePeriod();

        assertThat(effectivePeriod.getStartElement()).isEqualTo(authoredOn);
        assertThat(effectivePeriod.getEndElement()).isEqualTo(authoredOn);
        assertThat(result.getStatus()).isEqualTo(COMPLETED);
    }

    @Test
    void When_MapToMedicationStatement_WithActiveStatement_Expect_StartDateIsNotMappedToEndDate() {
        var authoredOn = new DateTimeType("2023-01-27");
        var expectedStartDate = "2010-01-14";

        var result = mapMedicationStatementFromEhrFile("ehrExtract4.xml", authoredOn);
        var effectivePeriod = result.getEffectivePeriod();

        assertThat(result.getStatus()).isEqualTo(ACTIVE);
        assertThat(effectivePeriod.getStartElement().toHumanDisplay()).isEqualTo(expectedStartDate);
        assertThat(effectivePeriod.hasEndElement()).isFalse();
    }

    @Test
    void When_MapToMedicationStatement_Expect_MetaPresentFromConfidentialityServiceWithSecurity() {
        final String fileName = "ehrExtract_nopatConfidentialityCodePresentWithinMedicationStatement.xml";
        final Meta expectedMeta = MetaUtil.getMetaFor(META_WITH_SECURITY, META_PROFILE);

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            eq(META_PROFILE),
            confidentialityCodeCaptor.capture(),
            confidentialityCodeCaptor.capture()
        )).thenReturn(MetaUtil.getMetaFor(META_WITH_SECURITY, META_PROFILE));

        final MedicationStatement result = mapMedicationStatementFromEhrFile(fileName, new DateTimeType());
        final Optional<CV> medicationStatementConfidentialityCode = getMedicationStatementConfidentialityCode();
        final Optional<CV> ehrCompositionConfidentialityCode = getEhrCompositionConfidentialityCode();

        assertAll(
            () -> assertThat(ehrCompositionConfidentialityCode).isNotPresent(),
            () -> assertThat(result.getMeta()).usingRecursiveComparison().isEqualTo(expectedMeta),
            () -> {
                assert Objects.requireNonNull(medicationStatementConfidentialityCode).isPresent();
                assertThat(medicationStatementConfidentialityCode.orElseThrow().getCode()).isEqualTo(NOPAT);
            }
        );
    }

    @Test
    void When_MapToMedicationStatement_With_ConfidentialityCodeInEhrComposition_Expect_MetaPresentFromConfidentialityServiceWithSecurity() {
        final String fileName = "ehrExtract_nopatConfidentialityCodePresentWithinEhrComposition.xml";
        final Meta expectedMeta = MetaUtil.getMetaFor(META_WITH_SECURITY, META_PROFILE);

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            eq(META_PROFILE),
            confidentialityCodeCaptor.capture(),
            confidentialityCodeCaptor.capture()
        )).thenReturn(MetaUtil.getMetaFor(META_WITH_SECURITY, META_PROFILE));

        final MedicationStatement result = mapMedicationStatementFromEhrFile(fileName, new DateTimeType());
        final Optional<CV> medicationStatementConfidentialityCode = getMedicationStatementConfidentialityCode();
        final Optional<CV> ehrCompositionConfidentialityCode = getEhrCompositionConfidentialityCode();

        assertAll(
            () -> assertThat(medicationStatementConfidentialityCode).isNotPresent(),
            () -> assertThat(result.getMeta()).usingRecursiveComparison().isEqualTo(expectedMeta),
            () -> {
                assert Objects.requireNonNull(ehrCompositionConfidentialityCode).isPresent();
                assertThat(ehrCompositionConfidentialityCode.orElseThrow().getCode()).isEqualTo(NOPAT);
            }
        );
    }

    @SneakyThrows
    private RCMRMT030101UKMedicationStatement unmarshallMedicationStatement(String fileName) {
        final File file = FileFactory.getXmlFileFor(TEST_FILE_DIRECTORY, fileName);
        return unmarshallFile(file, RCMRMT030101UKMedicationStatement.class);
    }

    @SneakyThrows
    private RCMRMT030101UKEhrExtract unmarshallEhrExtract(String fileName) {
        final File file = FileFactory.getXmlFileFor("MedicationStatement", fileName);
        return unmarshallFile(file, RCMRMT030101UKEhrExtract.class);
    }

    private MedicationStatement mapMedicationStatementFromEhrFile(String fileName, DateTimeType authoredOn) {
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract(fileName);
        final RCMRMT030101UKEhrComposition ehrComposition = GET_EHR_COMPOSITION.apply(ehrExtract);
        final Optional<RCMRMT030101UKMedicationStatement> medicationStatement = extractMedicationStatement(ehrExtract);
        assertThat(medicationStatement).isPresent();

        var authorise = extractAuthorise(medicationStatement.orElseThrow());
        assertThat(authorise).isPresent();

        when(medicationMapper.extractMedicationReference(any(RCMRMT030101UKMedicationStatement.class)))
            .thenReturn(getMedicationReference());

        return medicationStatementMapper.mapToMedicationStatement(
            ehrExtract, ehrComposition, medicationStatement.orElseThrow(), authorise.orElseThrow(), PRACTISE_CODE, authoredOn);
    }

    private Optional<RCMRMT030101UKMedicationStatement> extractMedicationStatement(RCMRMT030101UKEhrExtract ehrExtract) {
        return ehrExtract
            .getComponent()
            .stream()
            .map(RCMRMT030101UKComponent::getEhrFolder)
            .map(RCMRMT030101UKEhrFolder::getComponent)
            .flatMap(List::stream)
            .map(RCMRMT030101UKComponent3::getEhrComposition)
            .map(RCMRMT030101UKEhrComposition::getComponent)
            .flatMap(List::stream)
            .map(RCMRMT030101UKComponent4::getMedicationStatement)
            .findFirst();
    }

    private Optional<RCMRMT030101UKAuthorise> extractAuthorise(RCMRMT030101UKMedicationStatement medicationStatement) {

        return medicationStatement.getComponent()
            .stream()
            .filter(RCMRMT030101UKComponent2::hasEhrSupplyAuthorise)
            .map(RCMRMT030101UKComponent2::getEhrSupplyAuthorise)
            .findFirst();
    }

    private Optional<Reference> getMedicationReference() {
        final IdType idType = new IdType(ResourceType.Medication.name(), MEDICATION_ID);
        final Reference reference = new Reference(idType);
        return Optional.of(reference);
    }

    private Optional<CV> getMedicationStatementConfidentialityCode() {
        return confidentialityCodeCaptor.getAllValues().getFirst();
    }

    private Optional<CV> getEhrCompositionConfidentialityCode() {
        return confidentialityCodeCaptor.getAllValues().get(1);
    }
}