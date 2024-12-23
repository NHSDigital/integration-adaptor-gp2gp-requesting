package uk.nhs.adaptors.pss.translator.mapper.medication;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.util.ResourceUtils.getFile;

import static uk.nhs.adaptors.pss.translator.util.MetaUtil.MetaType.META_WITH_SECURITY;
import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.hl7.fhir.dstu3.model.MedicationRequest.MedicationRequestIntent.ORDER;
import static org.hl7.fhir.dstu3.model.MedicationRequest.MedicationRequestStatus.COMPLETED;

import java.util.Optional;

import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.MedicationRequest;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.hl7.v3.CV;
import org.hl7.v3.RCMRMT030101UKComponent2;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
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
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.SneakyThrows;
import uk.nhs.adaptors.pss.translator.util.MetaUtil;
import uk.nhs.adaptors.pss.translator.TestUtility;
import uk.nhs.adaptors.pss.translator.service.ConfidentialityService;
import uk.nhs.adaptors.pss.translator.util.DateFormatUtil;

@ExtendWith(MockitoExtension.class)
class MedicationRequestOrderMapperTest {

    private static final String META_PROFILE = "MedicationRequest-1";
    private static final String XML_RESOURCES_MEDICATION_STATEMENT = "xml/MedicationStatement/";
    private static final String PRACTISE_CODE = "TESTPRACTISECODE";
    private static final String TEST_ID = "TEST_ID";
    private static final String MEDICATION_ID = "MEDICATION_ID";
    private static final String TAKE_ONE_DAILY = "TAKE ONE DAILY";
    private static final String AVAILABILITY_TIME = "20060426";

    private static final int THREE = 3;
    private static final int SEVEN = 7;

    private static final Meta META = MetaUtil.getMetaFor(META_WITH_SECURITY, META_PROFILE);

    @Mock
    private MedicationMapper medicationMapper;
    @Mock
    private ConfidentialityService confidentialityService;
    @InjectMocks
    private MedicationRequestOrderMapper medicationRequestOrderMapper;
    @Captor
    private ArgumentCaptor<Optional<CV>> confidentialityCodeArgumentCaptor;

    @BeforeEach
    void beforeEach() {
        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            eq(META_PROFILE),
            confidentialityCodeArgumentCaptor.capture(),
            confidentialityCodeArgumentCaptor.capture()
        )).thenReturn(META);

        when(medicationMapper.extractMedicationReference(
            any(RCMRMT030101UKMedicationStatement.class)
        )).thenReturn(Optional.of(new Reference(new IdType(ResourceType.Medication.name(), MEDICATION_ID))));
    }

    @Test
    void When_MappingPrescribeResourceWithAllOptionals_Expect_AllFieldsToBeMappedCorrectly() {
        var medicationStatement = unmarshallMedicationStatement("medicationStatementPrescribeAllOptionals.xml");
        var prescribe = getPrescribeFromMedicationStatement(medicationStatement);

        assertThat(prescribe).isPresent();
        var medicationRequest = medicationRequestOrderMapper.mapToOrderMedicationRequest(
            new RCMRMT030101UKEhrExtract(),
            new RCMRMT030101UKEhrComposition(),
            medicationStatement,
            prescribe.get(),
            PRACTISE_CODE
        );

        assertCommonValues(medicationRequest);
        medicationRequest
            .getExtensionsByUrl("https://fhir.nhs.uk/STU3/StructureDefinition/Extension-CareConnect-GPC-PrescriptionType-1")
            .forEach(this::assertPrescriptionIsRepeat);
        assertThat(medicationRequest.getBasedOnFirstRep().getReferenceElement().getIdPart()).isEqualTo(TEST_ID);
        assertThat(medicationRequest.getNote()).hasSize(THREE);
        assertThat(medicationRequest.getDosageInstructionFirstRep().getText()).isEqualTo(TAKE_ONE_DAILY);
        assertThat(medicationRequest.getDispenseRequest().getQuantity().getValue().intValue()).isEqualTo(SEVEN);
        assertThat(medicationRequest.getDispenseRequest().getValidityPeriod().getStartElement().getValue())
            .isEqualTo(DateFormatUtil.parseToDateTimeType(AVAILABILITY_TIME).getValue());
    }

    @Test
    void When_MappingPrescribeResourceWithNoOptionals_Expect_AllFieldsToBeMappedCorrectly() {
        var medicationStatement = unmarshallMedicationStatement("medicationStatementPrescribeNoOptionals.xml");
        var prescribe = getPrescribeFromMedicationStatement(medicationStatement);

        assertThat(prescribe).isPresent();
        var medicationRequest = medicationRequestOrderMapper.mapToOrderMedicationRequest(
            new RCMRMT030101UKEhrExtract(),
            new RCMRMT030101UKEhrComposition(),
            medicationStatement,
            prescribe.get(),
            PRACTISE_CODE
        );

        assertCommonValues(medicationRequest);

        medicationRequest
            .getExtensionsByUrl("https://fhir.nhs.uk/STU3/StructureDefinition/Extension-CareConnect-GPC-PrescriptionType-1")
            .forEach(this::assertPrescriptionIsRepeat);
        assertThat(medicationRequest.getBasedOnFirstRep().getReferenceElement().getIdPart()).isEqualTo(TEST_ID);
        assertThat(medicationRequest.getNote()).hasSize(1);
        assertThat(medicationRequest.getDosageInstructionFirstRep().getText()).isEqualTo(TAKE_ONE_DAILY);
        assertThat(medicationRequest.getDispenseRequest().getQuantity().getValue()).isNull();
        assertThat(medicationRequest.getDispenseRequest().getValidityPeriod().getStartElement().getValue())
            .isEqualTo(DateFormatUtil.parseToDateTimeType(AVAILABILITY_TIME).getValue());
    }

    @Test
    void When_MappingPrescribeResource_Expect_MetaPopulatedFromConfidentialityService() {
        final RCMRMT030101UKMedicationStatement medicationStatement = unmarshallMedicationStatement(
            "medicationStatementPrescribeNoOptionalsWithNopatConfidentialityCode.xml"
        );

        final Optional<RCMRMT030101UKPrescribe> prescribe = getPrescribeFromMedicationStatement(medicationStatement);
        final CV compositionConfidentialityCode = TestUtility.createCv("MEDIUM");

        RCMRMT030101UKEhrComposition rcmrmt030101UKEhrComposition = new RCMRMT030101UKEhrComposition();
        rcmrmt030101UKEhrComposition.setConfidentialityCode(compositionConfidentialityCode);
        final MedicationRequest medicationRequest = medicationRequestOrderMapper.mapToOrderMedicationRequest(
            new RCMRMT030101UKEhrExtract(),
            rcmrmt030101UKEhrComposition,
            medicationStatement,
            prescribe.orElseThrow(),
            PRACTISE_CODE
        );

        assertAll(
            () -> assertThat(medicationRequest.getMeta()).usingRecursiveComparison().isEqualTo(META),
            () -> assertThat(confidentialityCodeArgumentCaptor
                                 .getAllValues().get(1)
                                 .orElseThrow()
                                 .getCode()).isEqualTo("NOPAT"),
            () -> assertThat(confidentialityCodeArgumentCaptor.getAllValues().getFirst().orElseThrow()).usingRecursiveComparison()
                .isEqualTo(compositionConfidentialityCode)
        );
    }

    @Test
    void When_MappingPrescribeResource_Expect_MetaPopulatedFromConfidentialityServiceWithNoSecurity() {
        final RCMRMT030101UKMedicationStatement medicationStatement = unmarshallMedicationStatement(
            "medicationStatementPrescribeNoOptionalsWithNoscrubConfidentialityCode.xml"
        );

        final Optional<RCMRMT030101UKPrescribe> prescribe = getPrescribeFromMedicationStatement(medicationStatement);

        final MedicationRequest medicationRequest = medicationRequestOrderMapper.mapToOrderMedicationRequest(
            new RCMRMT030101UKEhrExtract(),
            new RCMRMT030101UKEhrComposition(),
            medicationStatement,
            prescribe.orElseThrow(),
            PRACTISE_CODE
        );

        assertAll(
            () -> assertThat(medicationRequest.getMeta()).usingRecursiveComparison().isEqualTo(META),
            () -> assertThat(confidentialityCodeArgumentCaptor.getAllValues().get(1).orElseThrow().getCode()).isEqualTo("NOSCRUB"),
            () -> assertThat(confidentialityCodeArgumentCaptor.getAllValues().getFirst()).isEmpty()
        );
    }

    private void assertCommonValues(MedicationRequest medicationRequest) {
        assertThat(medicationRequest.getIdentifier()).hasSize(1);
        assertThat(medicationRequest.getIntent()).isEqualTo(ORDER);
        assertThat(medicationRequest.getStatus()).isEqualTo(COMPLETED);
        assertThat(medicationRequest.getMedicationReference().getReferenceElement().getIdPart()).isEqualTo(MEDICATION_ID);
    }

    private void assertPrescriptionIsRepeat(Extension extension) {
        var codeableConcept = (CodeableConcept) extension.getValue();
        assertThat(codeableConcept.getCodingFirstRep().getDisplay()).isEqualTo("Repeat");
    }

    @SneakyThrows
    private RCMRMT030101UKMedicationStatement unmarshallMedicationStatement(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES_MEDICATION_STATEMENT + fileName),
            RCMRMT030101UKMedicationStatement.class);
    }

    private Optional<RCMRMT030101UKPrescribe> getPrescribeFromMedicationStatement(RCMRMT030101UKMedicationStatement medicationStatement) {
        return medicationStatement.getComponent()
            .stream()
            .filter(RCMRMT030101UKComponent2::hasEhrSupplyPrescribe)
            .map(RCMRMT030101UKComponent2::getEhrSupplyPrescribe)
            .findFirst();
    }
}