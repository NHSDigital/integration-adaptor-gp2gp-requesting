package uk.nhs.adaptors.pss.translator.mapper.medication;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.Annotation;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.DomainResource;
import org.hl7.fhir.dstu3.model.Dosage;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Medication;
import org.hl7.fhir.dstu3.model.MedicationRequest;
import org.hl7.fhir.dstu3.model.MedicationStatement;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.hl7.fhir.dstu3.model.StringType;
import org.hl7.v3.RCMRMT030101UKAuthorise;
import org.hl7.v3.RCMRMT030101UKConsumable;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKMedicationStatement;
import org.hl7.v3.RCMRMT030101UKPrescribe;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hl7.fhir.dstu3.model.MedicationRequest.MedicationRequestDispenseRequestComponent;
import static org.hl7.fhir.dstu3.model.MedicationRequest.MedicationRequestIntent;
import static org.hl7.fhir.dstu3.model.MedicationStatement.MedicationStatementStatus;
import static org.hl7.fhir.dstu3.model.MedicationStatement.MedicationStatementTaken;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.util.ResourceUtils.getFile;

import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import java.util.Date;
import java.util.List;

import lombok.SneakyThrows;
import uk.nhs.adaptors.pss.translator.service.IdGeneratorService;
import uk.nhs.adaptors.pss.translator.util.DateFormatUtil;

@ExtendWith(MockitoExtension.class)
public class MedicationRequestMapperTest {

    public static final String INITIAL_PLAN_ID = "000EEA41-289B-4B1C-A5AB-421A666A0D2C";
    public static final String GENERATED_PLAN_ID = "00000000-0000-4000-0000-000000000001";
    public static final String EARLIEST_ORDER_ID = "00000000-0000-4000-0000-100000000001";
    public static final String LATEST_ORDER_ID = "00000000-0000-4000-0000-100000000002";
    private static final String INITIAL_MEDICATION_STATEMENT_ID = INITIAL_PLAN_ID + "-MS";
    private static final String GENERATED_MEDICATION_STATEMENT_ID = GENERATED_PLAN_ID + "-MS";

    private static final String XML_RESOURCES_BASE = "xml/MedicationStatement/";
    private static final String PRACTISE_CODE = "TESTPRACTISECODE";
    private static final int SINGLE_INVOCATION = 1;
    private static final int EXPECTED_RESOURCES_MAPPED = 6;

    private static final DateTimeType EXPECTED_DATE_TIME_TYPE = DateFormatUtil.parseToDateTimeType("20100115");

    private static final String PATIENT_ID = "d7d4ab01-c3a9-4120-9364-4a5b3fd614d0";
    private static final String MEDICATION_STATEMENT_LAST_ISSUE_DATE_URL =
        "https://fhir.nhs.uk/STU3/StructureDefinition/Extension-CareConnect-GPC-MedicationStatementLastIssueDate-1";

    private static final Extension ACUTE_PRESCRIPTION_EXTENSION = new Extension(
        "https://fhir.nhs.uk/STU3/StructureDefinition/Extension-CareConnect-GPC-PrescriptionType-1",
        new CodeableConcept(
            new Coding("https://fhir.nhs.uk/STU3/CodeSystem/CareConnect-PrescriptionType-1",
                "acute",
                "Acute"
            )
        )
    );
    private static final Extension REPEAT_PRESCRIPTION_EXTENSION = new Extension(
        "https://fhir.nhs.uk/STU3/StructureDefinition/Extension-CareConnect-GPC-PrescriptionType-1",
        new CodeableConcept(
            new Coding("https://fhir.nhs.uk/STU3/CodeSystem/CareConnect-PrescriptionType-1",
                "repeat",
                "Repeat"
            )
        )
    );
    private static final Reference REFERENCE_TO_PLAN = new Reference(
        new IdType(
            ResourceType.MedicationRequest.name(),
            INITIAL_PLAN_ID
        )
    );

    @Mock
    private MedicationMapper medicationMapper;
    @Mock
    private MedicationRequestOrderMapper medicationRequestOrderMapper;
    @Mock
    private MedicationRequestPlanMapper medicationRequestPlanMapper;
    @Mock
    private MedicationStatementMapper medicationStatementMapper;
    @Mock
    private MedicationMapperContext medicationMapperContext;
    @Mock
    private IdGeneratorService idGeneratorService;
    @InjectMocks
    private MedicationRequestMapper medicationRequestMapper;

    @BeforeEach
    void beforeEach() {
        setupCommonStubs();
    }

    @Test
    public void When_MappingMedicationStatement_Expect_CorrectMappersToBeCalled() {
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract("ehrExtract1.xml");
        final RCMRMT030101UKEhrComposition ehrComposition =
                ehrExtract.getComponent().getFirst().getEhrFolder().getComponent().getFirst().getEhrComposition();
        final RCMRMT030101UKMedicationStatement medicationStatement =
                ehrComposition.getComponent().getFirst().getMedicationStatement();

        var resources = medicationRequestMapper
                .mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(), PRACTISE_CODE);

        verify(medicationRequestPlanMapper, times(SINGLE_INVOCATION)).mapToPlanMedicationRequest(
            eq(ehrExtract),
            eq(ehrComposition),
            eq(medicationStatement),
            eq(medicationStatement.getComponent().getFirst().getEhrSupplyAuthorise()),
            eq(PRACTISE_CODE)
        );

        verify(medicationRequestOrderMapper, times(SINGLE_INVOCATION)).mapToOrderMedicationRequest(
            eq(ehrExtract),
            eq(ehrComposition),
            eq(medicationStatement),
            eq(medicationStatement.getComponent().get(2).getEhrSupplyPrescribe()),
            eq(PRACTISE_CODE)
        );

        verify(medicationStatementMapper, times(SINGLE_INVOCATION)).mapToMedicationStatement(
            eq(ehrExtract),
            eq(ehrComposition),
            eq(medicationStatement),
            eq(medicationStatement.getComponent().getFirst().getEhrSupplyAuthorise()),
            eq(PRACTISE_CODE),
            any(DateTimeType.class)
        );

        verify(medicationMapper, times(SINGLE_INVOCATION))
                .createMedication(any());

        assertThat(resources).hasSize(EXPECTED_RESOURCES_MAPPED);

        assertMedicationRequests(resources);
        assertMedicationStatements(resources);
    }

    @Test
    public void When_MappingMedicationRequestWithAvailabilityTimeInMedicationStatement_Expect_UseThatAvailabilityTime() {
        var ehrExtract = unmarshallEhrExtract("ehrExtract_AvailabilityTimeSetInMedicationStatement.xml");
        var expectedAuthoredOn = DateFormatUtil.parseToDateTimeType("20100116");

        var resources = medicationRequestMapper
                .mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(), PRACTISE_CODE);

        assertThat(resources).hasSize(EXPECTED_RESOURCES_MAPPED);

        var medicationRequest = resources
            .stream()
            .filter(resource -> ResourceType.MedicationRequest.equals(resource.getResourceType()))
            .map(MedicationRequest.class::cast)
            .findFirst()
            .get();

        assertThat(medicationRequest.getAuthoredOnElement().getValue()).isEqualTo(expectedAuthoredOn.getValue());
    }

    @Test
    public void When_MappingMedicationRequestWithAvailabilityTimeNotInMedicationStatement_Expect_AuthoredOnMapped() {
        var ehrExtract = unmarshallEhrExtract("ehrExtract_AvailabilityTimeNotInMedicationStatement.xml");
        var expectedAuthoredOn = DateFormatUtil.parseToDateTimeType("20100117");


        var resources = medicationRequestMapper.mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(),
                PRACTISE_CODE);

        assertThat(resources).hasSize(EXPECTED_RESOURCES_MAPPED);

        var medicationRequest = resources
                .stream()
                .filter(resource -> ResourceType.MedicationRequest.equals(resource.getResourceType()))
                .map(MedicationRequest.class::cast)
                .findFirst()
                .get();

        assertThat(medicationRequest.getAuthoredOnElement().getValue()).isEqualTo(expectedAuthoredOn.getValue());
    }

    @Test
    public void When_MappingMedicationRequestWithAvailabilityTimeInEhrCompositionAuthor_Expect_UseThatAvailabilityTime() {
        final int expectedResourcesMapped = 6;
        final DateTimeType expectedAvailabilityTime = DateFormatUtil.parseToDateTimeType("20220101010101");

        var ehrExtract = unmarshallEhrExtract("ehrExtract_hasAuthorTime.xml");

        var resources = medicationRequestMapper.mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(),
                PRACTISE_CODE);

        assertThat(resources).hasSize(expectedResourcesMapped);

        resources
                .stream()
                .filter(resource -> ResourceType.MedicationStatement.equals(resource.getResourceType()))
                .map(MedicationStatement.class::cast)
                .forEach(medicationStatement ->
                    assertThat(medicationStatement.getDateAssertedElement().getValue()).isEqualTo(expectedAvailabilityTime.getValue()));
    }

    @Test
    public void When_MappingMedicationRequestWithNoEhrCompositionAuthorTime_Expect_UseEhrExtractAvailabilityTime() {
        final int expectedResourcesMapped = 6;
        final DateTimeType expectedAvailabilityTime = DateFormatUtil.parseToDateTimeType("20100115");

        var ehrExtract = unmarshallEhrExtract("ehrExtract_hasNoAuthorTime.xml");

        var resources = medicationRequestMapper.mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(),
                PRACTISE_CODE);

        assertThat(resources).hasSize(expectedResourcesMapped);

        resources
                .stream()
                .filter(resource -> ResourceType.MedicationStatement.equals(resource.getResourceType()))
                .map(MedicationStatement.class::cast)
                .forEach(medicationStatement ->
                        assertThat(medicationStatement.getDateAssertedElement().getValue()).isEqualTo(expectedAvailabilityTime.getValue()));
    }

    @Test
    public void When_MappingMedicationRequestWithNoAuthoredOn_Expect_NullAuthoredOn() {
        var ehrExtract = unmarshallEhrExtract("ehrExtract_AvailabilityTimeNotInMedicationStatementOrEhrComposition.xml");

        when(medicationStatementMapper.mapToMedicationStatement(
            any(RCMRMT030101UKEhrExtract.class),
            any(RCMRMT030101UKEhrComposition.class),
            any(RCMRMT030101UKMedicationStatement.class),
            any(RCMRMT030101UKAuthorise.class),
            any(String.class),
            eq(null)
        )).thenReturn(new MedicationStatement());

        var resources = medicationRequestMapper.mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(),
                PRACTISE_CODE);

        assertThat(resources).hasSize(EXPECTED_RESOURCES_MAPPED);

        var medicationRequest = resources
                .stream()
                .filter(resource -> ResourceType.MedicationRequest.equals(resource.getResourceType()))
                .map(MedicationRequest.class::cast)
                .findFirst()
                .get();

        assertThat(medicationRequest.getAuthoredOnElement().getValue()).isNull();
    }

    @Test
    public void When_MappingMedicationRequestWithAuthoredOnValidDate_Expect_AuthoredOnToUseMedicationAvailabilityTime() {
        var ehrExtract = unmarshallEhrExtract("ehrExtract_hasAuthorTime.xml");
        var expectedAuthoredOn = DateFormatUtil.parseToDateTimeType("20100115");

        var resources = medicationRequestMapper.mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(),
                PRACTISE_CODE);

        assertThat(resources).hasSize(EXPECTED_RESOURCES_MAPPED);

        var medicationRequest = resources
                .stream()
                .filter(resource -> ResourceType.MedicationRequest.equals(resource.getResourceType()))
                .map(MedicationRequest.class::cast)
                .findFirst()
                .get();

        assertThat(medicationRequest.getAuthoredOnElement().getValue()).isEqualTo(expectedAuthoredOn.getValue());
    }

    @Test
    public void When_MappingMedicationRequestWithAuthoredOnInExtractAndComposition_Expect_AuthoredOnToUseAvailabilityTimeInStatement() {
        var ehrExtract = unmarshallEhrExtract("ehrExtract_hasAuthorTimeInExtract.xml");
        var expectedAuthoredOn = DateFormatUtil.parseToDateTimeType("20100115");

        var resources = medicationRequestMapper.mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(),
                PRACTISE_CODE);

        assertThat(resources).hasSize(EXPECTED_RESOURCES_MAPPED);

        var medicationRequest = resources
                .stream()
                .filter(resource -> ResourceType.MedicationRequest.equals(resource.getResourceType()))
                .map(MedicationRequest.class::cast)
                .findFirst()
                .get();

        assertThat(medicationRequest.getAuthoredOnElement().getValue()).isEqualTo(expectedAuthoredOn.getValue());
    }

    @Test
    public void When_MappingMedicationRequestWithAuthoredOnValidDateInExtractOnly_Expect_AuthoredOnToUseAvailabilityTimeInStatement() {
        var ehrExtract = unmarshallEhrExtract("ehrExtract_hasAuthorTimeInExtractOnly.xml");
        var expectedAuthoredOn = DateFormatUtil.parseToDateTimeType("20100115");

        var resources = medicationRequestMapper.mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(),
                PRACTISE_CODE);

        assertThat(resources).hasSize(EXPECTED_RESOURCES_MAPPED);

        var medicationRequest = resources
                .stream()
                .filter(resource -> ResourceType.MedicationRequest.equals(resource.getResourceType()))
                .map(MedicationRequest.class::cast)
                .findFirst()
                .get();

        assertThat(medicationRequest.getAuthoredOnElement().getValue()).isEqualTo(expectedAuthoredOn.getValue());
    }

    @Test
    public void When_MedicationRequestMapperThrowsException_Expect_MedicationMapperContextToBeReset() {
        var ehrExtract = unmarshallEhrExtract("ehrExtract1.xml");

        when(medicationRequestOrderMapper.mapToOrderMedicationRequest(any(), any(), any(), any(), any()))
                .thenThrow(NullPointerException.class);

        assertThrows(
                NullPointerException.class,
                () -> medicationRequestMapper.mapResources(
                        ehrExtract,
                        (Patient) new Patient().setId(PATIENT_ID),
                        List.of(),
                        PRACTISE_CODE));

        verify(medicationMapperContext).reset();
    }

    @Test
    public void When_MedicationRequestMapperCompletesSuccessfully_Expect_MedicationMapperContextToBeReset() {
        var ehrExtract = unmarshallEhrExtract("ehrExtract1.xml");

        medicationRequestMapper
                .mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(), PRACTISE_CODE);

        verify(medicationMapperContext).reset();
    }

    @Nested()
    @DisplayName("WhenTwoEhrSupplyPrescribeReferenceOneRepeatEhrSupplyAuthorise")
    class TwoEhrSupplyPrescribeReferenceOneRepeatEhrSupplyAuthorise {

        private static RCMRMT030101UKEhrExtract ehrExtract;

        @BeforeAll
        static void beforeAll() {
            ehrExtract = unmarshallEhrExtract(
                "ehrExtract_MultipleSupplyPrescribeInFulfilmentOfSingleNonAcuteSupplyAuthorise.xml"
            );
        }

        @BeforeEach
        void beforeEach() {
            setupMultipleOrdersToOnePlanStubs(REPEAT_PRESCRIPTION_EXTENSION, MedicationStatementStatus.ACTIVE, true);
        }

        @Test
        void expectNoAdditionalPlanCreated() {
            var resources = medicationRequestMapper
                .mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(), PRACTISE_CODE
                );

            var planMedicationRequests = resources.stream()
                .filter(MedicationRequest.class::isInstance)
                .map(MedicationRequest.class::cast)
                .filter(medicationRequest -> MedicationRequestIntent.PLAN.equals(medicationRequest.getIntent()));

            var orderMedicationRequests = resources.stream()
                .filter(MedicationRequest.class::isInstance)
                .map(MedicationRequest.class::cast)
                .filter(medicationRequest -> MedicationRequestIntent.ORDER.equals(medicationRequest.getIntent()))
                .toList();

            assertAll(
                () -> assertThat(planMedicationRequests).as("Plans").hasSize(1),
                () -> assertThat(orderMedicationRequests).as("Orders").hasSize(2)
            );
        }

        @Test
        void expectNoAdditionalMedicationStatementCreated() {
            var resources = medicationRequestMapper
                .mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(), PRACTISE_CODE
                );

            var medicationStatements = resources.stream()
                .filter(MedicationStatement.class::isInstance)
                .map(MedicationStatement.class::cast);

            var orderMedicationRequests = resources.stream()
                .filter(MedicationRequest.class::isInstance)
                .map(MedicationRequest.class::cast)
                .filter(medicationRequest -> MedicationRequestIntent.ORDER.equals(medicationRequest.getIntent()))
                .toList();

            assertAll(
                () -> assertThat(medicationStatements).as("MedicationStatements").hasSize(1),
                () -> assertThat(orderMedicationRequests).as("Orders").hasSize(2)
            );
        }
    }

    @Nested()
    @DisplayName("WhenTwoEhrSupplyPrescribeReferenceOneAcuteEhrSupplyAuthorise")
    class TwoEhrSupplyPrescribeReferenceOneAcuteEhrSupplyAuthorise {

        private static RCMRMT030101UKEhrExtract ehrExtract;

        @BeforeAll
        static void beforeAll() {
            ehrExtract = unmarshallEhrExtract(
                "ehrExtract_MultipleSupplyPrescribeInFulfilmentOfSingleAcuteSupplyAuthorise.xml"
            );
        }
        @BeforeEach
        void beforeEach() {
            setupMultipleOrdersToOnePlanStubs(ACUTE_PRESCRIPTION_EXTENSION, MedicationStatementStatus.ACTIVE, true);
        }

        @Test
        void expectAdditionalPlanCreated() {
            var resources = medicationRequestMapper
                .mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(), PRACTISE_CODE
                );

            var planMedicationRequests = resources.stream()
                .filter(MedicationRequest.class::isInstance)
                .map(MedicationRequest.class::cast)
                .filter(medicationRequest -> MedicationRequestIntent.PLAN.equals(medicationRequest.getIntent()));

            var orderMedicationRequests = resources.stream()
                .filter(MedicationRequest.class::isInstance)
                .map(MedicationRequest.class::cast)
                .filter(medicationRequest -> MedicationRequestIntent.ORDER.equals(medicationRequest.getIntent()))
                .toList();

            assertAll(
                () -> assertThat(planMedicationRequests).as("Plans").hasSize(2),
                () -> assertThat(orderMedicationRequests).as("Orders").hasSize(2)
            );
        }

        @Test
        void expectTheGeneratedPlanHasIdAndIdentityUpdatedToGeneratedId() {
            var resources = medicationRequestMapper
                .mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(), PRACTISE_CODE
                );

            var generatedPlan = getMedicationRequestById(resources, GENERATED_PLAN_ID);

            assertAll(
                () -> assertThat(generatedPlan.getId())
                    .isEqualTo(GENERATED_PLAN_ID),
                () -> assertThat(generatedPlan.getIdentifierFirstRep().getValue())
                    .isEqualTo(GENERATED_PLAN_ID)
            );
        }

        @Test
        void expectTheEarliestOrderBasedOnReferencesTheOriginalPlan() {
            var resources = medicationRequestMapper
                .mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(), PRACTISE_CODE
                );

            var earliestOrder = getMedicationRequestById(resources, EARLIEST_ORDER_ID);

            assertAll(
                () -> assertThat(earliestOrder.getBasedOn().getFirst().getReferenceElement().getIdPart())
                    .isEqualTo(INITIAL_PLAN_ID),
                () -> assertThat(earliestOrder.getBasedOn().getFirst().getReferenceElement().getResourceType())
                    .isEqualTo(ResourceType.MedicationRequest.name())
            );
        }

        @Test
        void expectTheLatestOrderBasedOnReferencesTheGeneratedPlan() {
            var resources = medicationRequestMapper
                .mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(), PRACTISE_CODE);

            var latestOrder = getMedicationRequestById(resources, LATEST_ORDER_ID);
            var generatedPlan = getMedicationRequestById(resources, GENERATED_PLAN_ID);

            assertAll(
                () -> assertThat(latestOrder.getBasedOn().getFirst().getReferenceElement().getIdPart())
                    .isEqualTo(generatedPlan.getId()),
                () -> assertThat(latestOrder.getBasedOn().getFirst().getReferenceElement().getResourceType())
                    .isEqualTo(ResourceType.MedicationRequest.name())
            );
        }

        @Test
        void expectTheLatestOrderPriorPrescriptionReferencesTheOriginalPlan() {
            var resources = medicationRequestMapper
                .mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(), PRACTISE_CODE);

            var generatedPlanReferenceElement = getMedicationRequestById(resources, GENERATED_PLAN_ID)
                .getPriorPrescription()
                .getReferenceElement();

            assertAll(
                () -> assertThat(generatedPlanReferenceElement.getResourceType())
                    .isEqualTo(ResourceType.MedicationRequest.name()),
                () -> assertThat(generatedPlanReferenceElement.getIdPart())
                    .isEqualTo(INITIAL_PLAN_ID)
            );
        }

        @Test
        void expectGeneratedPlanDispenseRequestValidityPeriodIsCopiedFromTheLatestOrder() {
            var resources = medicationRequestMapper
                .mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(), PRACTISE_CODE);

            var latestOrderValidityPeriod = getMedicationRequestById(resources, LATEST_ORDER_ID)
                .getDispenseRequest()
                .getValidityPeriod();

            var generatedPlanValidityPeriod = getMedicationRequestById(resources, GENERATED_PLAN_ID)
                .getDispenseRequest()
                .getValidityPeriod();

            assertThat(generatedPlanValidityPeriod)
                .usingRecursiveComparison()
                .isEqualTo(latestOrderValidityPeriod);
        }

        @Test
        void expectTheLatestOrderUnchangedPropertiesAreCopiedToGeneratedPlan() {
            var resources = medicationRequestMapper
                .mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(), PRACTISE_CODE);

            var originalPlan = getMedicationRequestById(resources, INITIAL_PLAN_ID);
            var generatedPlan = getMedicationRequestById(resources, GENERATED_PLAN_ID);

            assertAll(
                () -> assertThat(originalPlan.getDosageInstructionFirstRep().getText())
                    .isEqualTo(generatedPlan.getDosageInstructionFirstRep().getText()),
                () -> assertThat(originalPlan.getDispenseRequest().getId())
                    .isEqualTo(generatedPlan.getDispenseRequest().getId()),
                () -> assertThat(originalPlan.getExtension().getFirst())
                    .usingRecursiveComparison()
                    .isEqualTo(generatedPlan.getExtension().getFirst()),
                () -> assertThat(originalPlan.getStatus())
                    .isEqualTo(generatedPlan.getStatus()),
                () -> assertThat(originalPlan.getNoteFirstRep().getText())
                    .isEqualTo(generatedPlan.getNoteFirstRep().getText()),
                () -> assertThat(originalPlan.getMedicationReference().getReference())
                    .isEqualTo(generatedPlan.getMedicationReference().getReference()),
                () -> assertThat(originalPlan.getMeta())
                    .usingRecursiveComparison()
                    .isEqualTo(generatedPlan.getMeta())
            );
        }

        @Test
        void expectAdditionalMedicationStatementCreated() {
            var resources = medicationRequestMapper
                .mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(), PRACTISE_CODE
                );

            var medicationStatements = resources.stream()
                .filter(MedicationStatement.class::isInstance)
                .map(MedicationStatement.class::cast);

            var orderMedicationRequests = resources.stream()
                .filter(MedicationRequest.class::isInstance)
                .map(MedicationRequest.class::cast)
                .filter(medicationRequest -> MedicationRequestIntent.ORDER.equals(medicationRequest.getIntent()))
                .toList();

            assertAll(
                () -> assertThat(medicationStatements).as("MedicationStatements").hasSize(2),
                () -> assertThat(orderMedicationRequests).as("Orders").hasSize(2)
            );
        }

        @Test
        void expectGeneratedMedicationStatementHasIdAndIdentityUpdatedToGeneratedPlanWithSuffix() {
            var resources = medicationRequestMapper
                .mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(), PRACTISE_CODE
                );

            var generatedMedicationStatement = getMedicationStatementById(resources, GENERATED_MEDICATION_STATEMENT_ID);

            assertAll(
                () -> assertThat(generatedMedicationStatement.getId())
                    .isEqualTo(GENERATED_PLAN_ID + "-MS"),
                () -> assertThat(generatedMedicationStatement.getIdentifierFirstRep().getValue())
                    .isEqualTo(GENERATED_PLAN_ID + "-MS")
            );
        }

        @Test
        void expectGeneratedMedicationStatementBasedOnReferencesTheGeneratedPlan() {
            var resources = medicationRequestMapper
                .mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(), PRACTISE_CODE
                );

            var generatedMedicationStatement = getMedicationStatementById(resources, GENERATED_MEDICATION_STATEMENT_ID);

            assertAll(
                () -> assertThat(generatedMedicationStatement.getBasedOn().getFirst().getReferenceElement().getIdPart())
                    .isEqualTo(GENERATED_PLAN_ID),
                () -> assertThat(generatedMedicationStatement.getBasedOn().getFirst().getReferenceElement().getResourceType())
                    .isEqualTo(ResourceType.MedicationRequest.name())
            );
        }

        @Test
        void When_Active_Expect_GeneratedMedicationStatementHasEffectivePeriodSetToOrderValidityPeriod() {
            var resources = medicationRequestMapper
                .mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(), PRACTISE_CODE
                );

            var latestOrder = getMedicationRequestById(resources, LATEST_ORDER_ID);
            var generatedMedicationStatement = getMedicationStatementById(resources, GENERATED_MEDICATION_STATEMENT_ID);

            assertAll(
                () -> assertThat(generatedMedicationStatement.getEffectivePeriod())
                    .usingRecursiveComparison()
                    .isEqualTo(latestOrder.getDispenseRequest().getValidityPeriod())
            );
        }

        @Test
        void When_NotActive_Expect_GeneratedStatementHasEffectiveTimeSetToOrderValidityPeriodWhenEndIsPresent() {
            setupMultipleOrdersToOnePlanStubs(ACUTE_PRESCRIPTION_EXTENSION, MedicationStatementStatus.COMPLETED, true);

            var resources = medicationRequestMapper
                .mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(), PRACTISE_CODE
                );

            var latestOrder = getMedicationRequestById(resources, LATEST_ORDER_ID);
            var generatedMedicationStatement = getMedicationStatementById(resources, GENERATED_MEDICATION_STATEMENT_ID);

            assertAll(
                () -> assertThat(generatedMedicationStatement.getEffectivePeriod())
                    .usingRecursiveComparison()
                    .isEqualTo(latestOrder.getDispenseRequest().getValidityPeriod())
            );
        }

        @Test
        void When_NotActive_Expect_GeneratedStatementHasEffectiveTimeEndSetToOrderValidityPeriodStartWhenEndIsNotPresent() {
            setupMultipleOrdersToOnePlanStubs(ACUTE_PRESCRIPTION_EXTENSION, MedicationStatementStatus.COMPLETED, false);

            var resources = medicationRequestMapper
                .mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(), PRACTISE_CODE
                );

            var latestOrder = getMedicationRequestById(resources, LATEST_ORDER_ID);
            var generatedMedicationStatement = getMedicationStatementById(resources, GENERATED_MEDICATION_STATEMENT_ID);
            assertAll(
                () -> assertThat(generatedMedicationStatement.getEffectivePeriod().getStart())
                    .as("Start")
                    .isEqualTo(latestOrder.getDispenseRequest().getValidityPeriod().getStart()),
                () -> assertThat(generatedMedicationStatement.getEffectivePeriod().getEnd())
                    .as("End")
                    .isEqualTo(latestOrder.getDispenseRequest().getValidityPeriod().getStart())
            );
        }

        @Test
        void expectGeneratedMedicationStatementLastIssueDateExtensionSetToValidityPeriodStart() {
            var resources = medicationRequestMapper
                .mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(), PRACTISE_CODE
                );

            var latestOrder = getMedicationRequestById(resources, LATEST_ORDER_ID);
            var generatedMedicationStatement = getMedicationStatementById(resources, GENERATED_MEDICATION_STATEMENT_ID);

            assertThat(generatedMedicationStatement.getExtensionByUrl(MEDICATION_STATEMENT_LAST_ISSUE_DATE_URL).getValue())
                .isEqualTo(latestOrder.getDispenseRequest().getValidityPeriod().getStartElement());
        }

        @Test
        void expectOriginalMedicationStatementLastIssueDateExtensionSetToValidityPeriodStart() {
            var resources = medicationRequestMapper
                    .mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(), PRACTISE_CODE
                    );

            var earliestOrder = getMedicationRequestById(resources, EARLIEST_ORDER_ID);
            var originalMedicationStatement = getMedicationStatementById(resources, INITIAL_MEDICATION_STATEMENT_ID);

            assertThat(originalMedicationStatement.getExtensionByUrl(MEDICATION_STATEMENT_LAST_ISSUE_DATE_URL).getValue())
                .isEqualTo(earliestOrder.getDispenseRequest().getValidityPeriod().getStartElement());
        }

        @Test
        void expectOriginalMedicationStatementUnchangedPropertiesAreCopiedToGeneratedMedicationStatement() {
            var resources = medicationRequestMapper
                .mapResources(ehrExtract, (Patient) new Patient().setId(PATIENT_ID), List.of(), PRACTISE_CODE
                );

            var originalMedicationStatement = getMedicationStatementById(resources, INITIAL_MEDICATION_STATEMENT_ID);
            var generatedMedicationStatement = getMedicationStatementById(resources, GENERATED_MEDICATION_STATEMENT_ID);

            assertAll(
                () -> assertThat(generatedMedicationStatement.getTaken())
                    .isEqualTo(originalMedicationStatement.getTaken()),
                () -> assertThat(generatedMedicationStatement.getDosage())
                    .usingRecursiveComparison()
                    .isEqualTo(originalMedicationStatement.getDosage()),
                () -> assertThat(generatedMedicationStatement.getExtension().getFirst())
                    .isEqualTo(generatedMedicationStatement.getExtension().getFirst()),
                () -> assertThat(generatedMedicationStatement.getMeta())
                    .usingRecursiveComparison()
                    .isEqualTo(originalMedicationStatement.getMeta()),
                () -> assertThat(generatedMedicationStatement.getMedicationReference().getReferenceElement())
                    .isEqualTo(originalMedicationStatement.getMedicationReference().getReferenceElement()),
                () -> assertThat(generatedMedicationStatement.getStatus())
                    .isEqualTo(originalMedicationStatement.getStatus())
            );
        }
    }

    @SneakyThrows
    private static RCMRMT030101UKEhrExtract unmarshallEhrExtract(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES_BASE + fileName), RCMRMT030101UKEhrExtract.class);
    }

    private void assertMedicationRequests(List<? extends DomainResource> resources) {
        resources.stream()
            .filter(resource -> ResourceType.MedicationRequest.equals(resource.getResourceType()))
            .map(MedicationRequest.class::cast)
            .map(MedicationRequest::getSubject)
            .forEach(this::assertReferenceIdIsPatientId);
    }

    private void assertMedicationStatements(List<? extends DomainResource> resources) {
        resources.stream()
            .filter(resource -> ResourceType.MedicationStatement.equals(resource.getResourceType()))
            .map(MedicationStatement.class::cast)
            .forEach(this::assertMedicationStatementDateAndIdIsPatient);
    }

    private void assertMedicationStatementDateAndIdIsPatient(MedicationStatement statement) {
        final Date date = statement.getDateAssertedElement().getValue();
        final Reference reference = statement.getSubject();

        assertReferenceIdIsPatientId(reference);
        assertThat(date).isEqualTo(EXPECTED_DATE_TIME_TYPE.getValue());
    }

    private void assertReferenceIdIsPatientId(Reference reference) {
        final String medicationRequestId = reference
            .getResource()
            .getIdElement()
            .getIdPart();

        assertThat(medicationRequestId).isEqualTo(PATIENT_ID);
    }

    private MedicationRequest buildMedicationRequestOrder(
        String id,
        String validityPeriodStartDate,
        String validityPeriodEndDate
    ) {
        return (MedicationRequest) new MedicationRequest()
            .setIntent(MedicationRequestIntent.ORDER)
            .addBasedOn(REFERENCE_TO_PLAN)
            .setDispenseRequest(
                new MedicationRequestDispenseRequestComponent()
                    .setValidityPeriod(
                        new Period()
                            .setStartElement(DateFormatUtil.parseToDateTimeType(validityPeriodStartDate))
                            .setEndElement(StringUtils.isNotEmpty(validityPeriodEndDate)
                                ? DateFormatUtil.parseToDateTimeType(validityPeriodEndDate)
                                : null
                            )
                    )
            )
            .setId(id);
    }

    private MedicationRequest buildMedicationRequestPlan(Extension extension) {
        var plan = new MedicationRequest();
        plan.addIdentifier(new Identifier().setValue(INITIAL_PLAN_ID));
        plan.setIntent(MedicationRequestIntent.PLAN);
        plan.addDosageInstruction(new Dosage().setText("TEST_DOSAGE"));
        plan.setDispenseRequest(
            (MedicationRequestDispenseRequestComponent)
                new MedicationRequestDispenseRequestComponent().setId("TEST_DISPENSE_REQUEST_ID")
        );
        plan.addExtension(new Extension("TEST_EXTENSION", new StringType("TEST_VALUE")));
        plan.setStatus(MedicationRequest.MedicationRequestStatus.COMPLETED);
        plan.addNote(new Annotation(new StringType("TEST_NOTE_TEXT")));
        plan.setPriorPrescription(new Reference().setDisplay("TEST_PRIOR_PRESCRIPTION_DISPLAY"));
        plan.setMedication(new Reference("MedicationRequest/00000000-0000-4000-0000-200000000000"));
        plan.setMeta(new Meta().addSecurity("TEST_SYSTEM", "TEST_CODE", "TEST_DISPLAY"));

        plan.setExtension(List.of(extension));
        plan.setId(INITIAL_PLAN_ID);

        return plan;
    }

    private MedicationStatement buildMedicationStatement(MedicationStatementStatus status) {
        return (MedicationStatement) new MedicationStatement()
            .addIdentifier(new Identifier().setValue(INITIAL_MEDICATION_STATEMENT_ID))
            .setTaken(MedicationStatementTaken.UNK)
            .addBasedOn(REFERENCE_TO_PLAN)
            .addDosage(new Dosage().setText("TEST_DOSAGE"))
            .setMedication(new Reference("MedicationRequest/00000000-0000-4000-0000-200000000000"))
            .setEffective(new Period().setStartElement(DateFormatUtil.parseToDateTimeType("20240101")))
            .setStatus(status)
            .addExtension(new Extension("TEST_EXTENSION", new StringType("TEST_VALUE")))
            .addExtension(new Extension(
                "https://fhir.nhs.uk/STU3/StructureDefinition/Extension-CareConnect-GPC-MedicationStatementLastIssueDate-1",
                DateFormatUtil.parseToDateTimeType("20240726")
            ))
            .setId(INITIAL_MEDICATION_STATEMENT_ID)
            .setMeta(new Meta().addSecurity("TEST_SYSTEM", "TEST_CODE", "TEST_DISPLAY"));
    }

    private static @NotNull MedicationRequest getMedicationRequestById(
        List<DomainResource> resources,
        String id
    ) {
        return resources.stream()
            .filter(MedicationRequest.class::isInstance)
            .map(MedicationRequest.class::cast)
            .filter(medicationRequest -> id.equals(medicationRequest.getId()))
            .findFirst()
            .orElseThrow();
    }

    private static @NotNull MedicationStatement getMedicationStatementById(
        List<DomainResource> resources,
        String id
    ) {
        return resources.stream()
            .filter(MedicationStatement.class::isInstance)
            .map(MedicationStatement.class::cast)
            .filter(medicationStatement -> id.equals(medicationStatement.getId()))
            .findFirst()
            .orElseThrow();
    }

    private void setupMultipleOrdersToOnePlanStubs(
        Extension planExtension,
        MedicationStatementStatus medicationStatementStatus,
        boolean hasOrderValidityPeriodEnd
    ) {
        lenient().when(
            medicationRequestPlanMapper.mapToPlanMedicationRequest(
                any(RCMRMT030101UKEhrExtract.class),
                any(RCMRMT030101UKEhrComposition.class),
                any(RCMRMT030101UKMedicationStatement.class),
                any(RCMRMT030101UKAuthorise.class),
                any(String.class)
            )
        ).thenReturn(buildMedicationRequestPlan(planExtension));

        var orderValidityPeriodEnd = hasOrderValidityPeriodEnd
            ? "20240103"
            : null;

        lenient().when(
            medicationRequestOrderMapper.mapToOrderMedicationRequest(
                any(RCMRMT030101UKEhrExtract.class),
                any(RCMRMT030101UKEhrComposition.class),
                any(RCMRMT030101UKMedicationStatement.class),
                any(RCMRMT030101UKPrescribe.class),
                any(String.class)
            )
        ).thenReturn(
            buildMedicationRequestOrder(LATEST_ORDER_ID, "20240102", orderValidityPeriodEnd),
            buildMedicationRequestOrder(EARLIEST_ORDER_ID, "20240101", orderValidityPeriodEnd)
        );

        lenient().when(idGeneratorService.generateUuid()).thenReturn(GENERATED_PLAN_ID);

        lenient().when(
            medicationStatementMapper.mapToMedicationStatement(
                any(RCMRMT030101UKEhrExtract.class),
                any(RCMRMT030101UKEhrComposition.class),
                any(RCMRMT030101UKMedicationStatement.class),
                any(RCMRMT030101UKAuthorise.class),
                any(String.class),
                any(DateTimeType.class)
            )
        ).thenReturn(buildMedicationStatement(medicationStatementStatus));
    }

    private void setupCommonStubs() {
        Mockito.lenient().when(medicationRequestPlanMapper.mapToPlanMedicationRequest(
            any(RCMRMT030101UKEhrExtract.class),
            any(RCMRMT030101UKEhrComposition.class),
            any(RCMRMT030101UKMedicationStatement.class),
            any(RCMRMT030101UKAuthorise.class),
            any(String.class)
        )).thenReturn(new MedicationRequest());

        Mockito.lenient().when(medicationRequestOrderMapper.mapToOrderMedicationRequest(
            any(RCMRMT030101UKEhrExtract.class),
            any(RCMRMT030101UKEhrComposition.class),
            any(RCMRMT030101UKMedicationStatement.class),
            any(RCMRMT030101UKPrescribe.class),
            any(String.class)
        )).thenReturn(new MedicationRequest());

        Mockito.lenient().when(medicationStatementMapper.mapToMedicationStatement(
            any(RCMRMT030101UKEhrExtract.class),
            any(RCMRMT030101UKEhrComposition.class),
            any(RCMRMT030101UKMedicationStatement.class),
            any(RCMRMT030101UKAuthorise.class),
            any(String.class),
            any(DateTimeType.class)
        )).thenReturn(new MedicationStatement());

        Mockito.lenient().when(medicationMapper.createMedication(
            any(RCMRMT030101UKConsumable.class)
        )).thenReturn(new Medication());
    }
}