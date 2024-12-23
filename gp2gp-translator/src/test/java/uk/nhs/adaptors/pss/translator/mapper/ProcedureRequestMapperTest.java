package uk.nhs.adaptors.pss.translator.mapper;

import static java.util.UUID.randomUUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.util.ResourceUtils.getFile;

import static uk.nhs.adaptors.pss.translator.util.MetaUtil.MetaType.META_WITHOUT_SECURITY;
import static uk.nhs.adaptors.pss.translator.util.MetaUtil.assertMetaSecurityIsPresent;
import static uk.nhs.adaptors.pss.translator.util.MetaUtil.assertMetaSecurityNotPresent;
import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;
import static uk.nhs.adaptors.pss.translator.util.MetaUtil.MetaType.META_WITH_SECURITY;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.ProcedureRequest;
import org.hl7.fhir.dstu3.model.ProcedureRequest.ProcedureRequestIntent;
import org.hl7.fhir.dstu3.model.ProcedureRequest.ProcedureRequestStatus;
import org.hl7.v3.CV;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKPlanStatement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.SneakyThrows;
import uk.nhs.adaptors.pss.translator.util.MetaUtil;
import uk.nhs.adaptors.pss.translator.TestUtility;
import uk.nhs.adaptors.pss.translator.service.ConfidentialityService;
import uk.nhs.adaptors.pss.translator.util.DateFormatUtil;
import uk.nhs.adaptors.pss.translator.util.DegradedCodeableConcepts;
import static uk.nhs.adaptors.common.util.CodeableConceptUtils.createCodeableConcept;
import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallString;

@ExtendWith(MockitoExtension.class)
public class ProcedureRequestMapperTest {

    private static final String XML_RESOURCES_BASE = "xml/ProcedureRequest/";
    private static final String META_PROFILE = "ProcedureRequest-1";
    private static final String PRACTISE_CODE = "TESTPRACTISECODE";
    private static final String IDENTIFIER_SYSTEM = "https://PSSAdaptor/TESTPRACTISECODE";
    private static final String CODING_DISPLAY = "Ischaemic heart disease";
    private static final String CODING_CODE = "2534664018";
    private static final String CODING_SYSTEM = "http://snomed.info/sct";
    private static final String ENCOUNTER_ID = "62A39454-299F-432E-993E-5A6232B4E099";
    private static final String STATUS_PENDING = "Status: Pending";
    private static final String STATUS_CLINICIAN_CANCELLED = "Status: Cancelled by clinician";
    private static final String STATUS_SUPERSEDED = "Status: Superseded";
    private static final String STATUS_SEEN = "Status: Seen";
    private static final List<Encounter> ENCOUNTERS = getEncounterList();
    private static final Patient SUBJECT = createPatient();
    private static final Meta META_WITH_SECURITY_ADDED = MetaUtil.getMetaFor(META_WITH_SECURITY, META_PROFILE);
    private static final Meta META_WITHOUT_SECURITY_ADDED = MetaUtil.getMetaFor(META_WITHOUT_SECURITY, META_PROFILE);
    private static final CV NOPAT_CV = TestUtility.createCv(
        "NOPAT",
        "http://hl7.org/fhir/v3/ActCode",
        "no disclosure to patient, family or caregivers without attending provider's authorization");

    private static final CV ALTERNATIVE_CV = TestUtility.createCv(
        "NOSCRUB",
        "http://hl7.org/fhir/v3/FakeCode",
        "no scrubbing of the patient, family or caregivers without attending provider's authorization");

    private static Stream<Arguments> planStatementStatuses() {
        return Stream.of(
                Arguments.of("", ProcedureRequestStatus.UNKNOWN),
                Arguments.of(STATUS_PENDING, ProcedureRequestStatus.ACTIVE),
                Arguments.of(STATUS_CLINICIAN_CANCELLED, ProcedureRequestStatus.CANCELLED),
                Arguments.of(STATUS_SUPERSEDED, ProcedureRequestStatus.CANCELLED),
                Arguments.of(STATUS_SEEN, ProcedureRequestStatus.COMPLETED)
        );
    }

    @Mock
    private ConfidentialityService confidentialityService;

    @Mock
    private CodeableConceptMapper codeableConceptMapper;

    @InjectMocks
    private ProcedureRequestMapper procedureRequestMapper;

    private static Patient createPatient() {
        Patient patient = new Patient();
        patient.setId(randomUUID().toString());
        return patient;
    }

    private static List<Encounter> getEncounterList() {
        var encounter = new Encounter();
        encounter.setId(ENCOUNTER_ID);
        return List.of(encounter);
    }

    @Test
    public void mapProcedureRequestWithDegradedPlan() {
        var ehrExtract = unmarshallCodeElement("full_valid_data_example.xml");

        var planStatement = getPlanStatement(ehrExtract);
        planStatement.setConfidentialityCode(new CV());
        var ehrComposition = getEhrComposition(ehrExtract);
        ehrComposition.setConfidentialityCode(new CV());

        when(codeableConceptMapper.mapToCodeableConcept(any())).thenReturn(new CodeableConcept());

        ProcedureRequest procedureRequest = procedureRequestMapper.mapToProcedureRequest(ehrComposition,
                planStatement, SUBJECT, ENCOUNTERS, PRACTISE_CODE);

        assertNotNull(procedureRequest.getCode().getCodingFirstRep());
        assertThat(procedureRequest.getCode().getCodingFirstRep())
                .isEqualTo(DegradedCodeableConcepts.DEGRADED_PLAN);
    }

    @Test
    public void mapProcedureRequestWithNoOptionalFields() {
        var ehrExtract = unmarshallCodeElement("no_optional_data_example.xml");
        var planStatement = getPlanStatement(ehrExtract);
        planStatement.setConfidentialityCode(new CV());
        var ehrComposition = getEhrComposition(ehrExtract);
        ehrComposition.setConfidentialityCode(new CV());

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            planStatement.getConfidentialityCode()
        )).thenReturn(META_WITH_SECURITY_ADDED);

        ProcedureRequest procedureRequest = procedureRequestMapper.mapToProcedureRequest(ehrComposition,
            planStatement, SUBJECT, ENCOUNTERS, PRACTISE_CODE);

        assertFixedValues(planStatement, procedureRequest);
        assertNull(procedureRequest.getOccurrence());
        assertNull(procedureRequest.getAuthoredOn());
        assertNull(procedureRequest.getNoteFirstRep());
    }

    @Test
    public void mapProcedureRequestMetaSecurityWithNoPatWhenConfidentialityCodeIsPresentForPlanStatementAndEhrComposition() {
        var ehrExtract = unmarshallCodeElement("no_optional_data_example.xml");

        var planStatement = getPlanStatement(ehrExtract);
        planStatement.setConfidentialityCode(NOPAT_CV);
        var ehrComposition = getEhrComposition(ehrExtract);
        ehrComposition.setConfidentialityCode(NOPAT_CV);

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            planStatement.getConfidentialityCode()
        )).thenReturn(META_WITH_SECURITY_ADDED);

        ProcedureRequest procedureRequest = procedureRequestMapper.mapToProcedureRequest(ehrComposition,
                                                                                         planStatement, SUBJECT, ENCOUNTERS, PRACTISE_CODE);

        assertMetaSecurityIsPresent(META_WITH_SECURITY_ADDED, procedureRequest.getMeta());
    }

    @Test
    public void mapProcedureRequestMetaSecurityWithNoPatWhenConfidentialityCodeIsPresentOnlyForEhrComposition() {
        var ehrExtract = unmarshallCodeElement("no_optional_data_example.xml");
        var planStatement = getPlanStatement(ehrExtract);
        var ehrComposition = getEhrComposition(ehrExtract);
        ehrComposition.setConfidentialityCode(NOPAT_CV);

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            Optional.empty()
        )).thenReturn(META_WITH_SECURITY_ADDED);

        var procedureRequest = procedureRequestMapper.mapToProcedureRequest(ehrComposition,
                                                                            planStatement,
                                                                            SUBJECT,
                                                                            ENCOUNTERS,
                                                                            PRACTISE_CODE);

        assertMetaSecurityIsPresent(META_WITH_SECURITY_ADDED, procedureRequest.getMeta());
    }

    @Test
    public void mapProcedureRequestMetaSecurityWithNoScrubWhenConfidentialityCodeIsNotPresent() {
        var ehrExtract = unmarshallCodeElement("no_optional_data_example.xml");
        var planStatement = getPlanStatement(ehrExtract);
        var ehrComposition = getEhrComposition(ehrExtract);
        ehrComposition.setConfidentialityCode(ALTERNATIVE_CV);

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            Optional.empty()
        )).thenReturn(META_WITHOUT_SECURITY_ADDED);

        ProcedureRequest procedureRequest = procedureRequestMapper.mapToProcedureRequest(
            ehrComposition,
            planStatement,
            SUBJECT,
            ENCOUNTERS,
            PRACTISE_CODE);

        assertMetaSecurityNotPresent(procedureRequest, META_PROFILE);
    }

    @Test
    public void mapProcedureRequestWithNoReferencedEncounter() {
        var ehrExtract = unmarshallCodeElement("no_referenced_encounter_example.xml");
        var planStatement = getPlanStatement(ehrExtract);

        ProcedureRequest procedureRequest = procedureRequestMapper.mapToProcedureRequest(getEhrComposition(ehrExtract),
            planStatement, SUBJECT, ENCOUNTERS, PRACTISE_CODE);

        assertNull(procedureRequest.getContext().getResource());
    }

    @Test
    public void mapProcedureRequestWithPrfParticipant() {
        var ehrExtract = unmarshallCodeElement("prf_participant_example.xml");
        var planStatement = getPlanStatement(ehrExtract);
        planStatement.setConfidentialityCode(new CV());
        var ehrComposition = getEhrComposition(ehrExtract);
        ehrComposition.setConfidentialityCode(new CV());
        setUpCodeableConceptMock();

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            planStatement.getConfidentialityCode()
        )).thenReturn(META_WITH_SECURITY_ADDED);

        ProcedureRequest procedureRequest = procedureRequestMapper.mapToProcedureRequest(ehrComposition,
            planStatement, SUBJECT, ENCOUNTERS, PRACTISE_CODE);

        assertFixedValues(planStatement, procedureRequest);
        assertThat(procedureRequest.getCode().getCodingFirstRep().getDisplay()).isEqualTo(
            planStatement.getCode().getDisplayName());
        assertThat(procedureRequest.getRequester().getAgent().getReference())
            .isEqualTo("Practitioner/9C1610C2-5E48-4ED5-882B-5A4A172AFA35");
        assertThat(procedureRequest.getContext().getResource().getIdElement().getValue()).isEqualTo(ENCOUNTER_ID);
    }

    @Test
    public void mapProcedureRequestWithParticipant2() {
        var ehrExtract = unmarshallCodeElement("participant2_example.xml");
        var planStatement = getPlanStatement(ehrExtract);
        planStatement.setConfidentialityCode(new CV());
        var ehrComposition = getEhrComposition(ehrExtract);
        ehrComposition.setConfidentialityCode(new CV());
        setUpCodeableConceptMock();

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            planStatement.getConfidentialityCode()
        )).thenReturn(META_WITH_SECURITY_ADDED);

        ProcedureRequest procedureRequest = procedureRequestMapper.mapToProcedureRequest(getEhrComposition(ehrExtract),
            planStatement, SUBJECT, ENCOUNTERS, PRACTISE_CODE);

        assertFixedValues(planStatement, procedureRequest);
        assertThat(procedureRequest.getCode().getCodingFirstRep().getDisplay()).isEqualTo(
            planStatement.getCode().getDisplayName());
        assertThat(procedureRequest.getRequester().getAgent().getReference())
            .isEqualTo("Practitioner/2D70F602-6BB1-47E0-B2EC-39912A59787D");
        assertThat(procedureRequest.getContext().getResource().getIdElement().getValue()).isEqualTo(ENCOUNTER_ID);
    }

    @Test
    public void mapProcedureRequestWithEhrCompositionAvailabilityTime() {
        var ehrExtract = unmarshallCodeElement("ehr_composition_availability_time_example.xml");
        var ehrComposition = getEhrComposition(ehrExtract);
        ehrComposition.setConfidentialityCode(new CV());
        var planStatement = getPlanStatement(ehrExtract);
        planStatement.setConfidentialityCode(new CV());
        setUpCodeableConceptMock();

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            planStatement.getConfidentialityCode()
        )).thenReturn(META_WITH_SECURITY_ADDED);

        ProcedureRequest procedureRequest = procedureRequestMapper.mapToProcedureRequest(getEhrComposition(ehrExtract),
            planStatement, SUBJECT, ENCOUNTERS, PRACTISE_CODE);

        assertFixedValues(planStatement, procedureRequest);
        assertThat(procedureRequest.getAuthoredOn()).isEqualTo(
            DateFormatUtil.parseToDateTimeType(ehrComposition.getAvailabilityTime().getValue()).getValue());
        assertThat(procedureRequest.getCode().getCodingFirstRep().getDisplay()).isEqualTo(
            planStatement.getCode().getDisplayName());
        assertThat(procedureRequest.getContext().getResource().getIdElement().getValue()).isEqualTo(ENCOUNTER_ID);
    }

    @Test
    public void mapProcedureRequestWithAuthorTime() {
        var ehrExtract = unmarshallCodeElement("ehr_extract_author_time_example.xml");
        var planStatement = getPlanStatement(ehrExtract);
        planStatement.setConfidentialityCode(new CV());
        var ehrComposition = getEhrComposition(ehrExtract);
        ehrComposition.setConfidentialityCode(new CV());
        setUpCodeableConceptMock();

        when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            planStatement.getConfidentialityCode()
        )).thenReturn(META_WITH_SECURITY_ADDED);

        ProcedureRequest procedureRequest = procedureRequestMapper.mapToProcedureRequest(ehrComposition,
            planStatement, SUBJECT, ENCOUNTERS, PRACTISE_CODE);

        assertFixedValues(planStatement, procedureRequest);
        assertThat(procedureRequest.getAuthoredOn()).isEqualTo(
            DateFormatUtil.parseToDateTimeType(getEhrComposition(ehrExtract).getAuthor().getTime().getValue()).getValue());
        assertThat(procedureRequest.getCode().getCodingFirstRep().getDisplay()).isEqualTo(
            planStatement.getCode().getDisplayName());
        assertThat(procedureRequest.getContext().getResource().getIdElement().getValue()).isEqualTo(ENCOUNTER_ID);
    }

    @ParameterizedTest
    @MethodSource("planStatementStatuses")
    public void When_PlanStatementTextStartsWithStatus_Expect_CorrectStatusIsMapped(
            String statusIdentifier,
            ProcedureRequestStatus expectedStatus) {
        var inputXml = """
                <EhrExtract xmlns="urn:hl7-org:v3" classCode="EXTRACT" moodCode="EVN">
                    <component typeCode="COMP">
                        <ehrFolder classCode="FOLDER" moodCode="EVN">
                            <component typeCode="COMP">
                                <ehrComposition classCode="COMPOSITION" moodCode="EVN">
                                    <id root="7DFFAEC4-7527-4D80-A2BD-81BDEBA04400" />
                                    <component typeCode="COMP" >
                                        <PlanStatement classCode="OBS" moodCode="INT">
                                            <id root="6DFFAEC4-7527-4D80-A2BD-81BDEBA04400" />
                                            <text>{statusIdentifier} this is some text after the status</text>
                                            <code code="123456" codeSystem="1.2.3.4.5" displayName="12345"></code>
                                            <statusCode code="COMPLETE" />
                                        </PlanStatement>
                                    </component>
                                </ehrComposition>
                            </component>
                        </ehrFolder>
                    </component>
                </EhrExtract>
                """.replace("{statusIdentifier}", statusIdentifier);

        var ehrExtract = unmarshallCodeElementFromString(inputXml);
        var planStatement = getPlanStatement(ehrExtract);

        when(codeableConceptMapper.mapToCodeableConcept(any()))
                .thenReturn(new CodeableConcept());

        ProcedureRequest procedureRequest = procedureRequestMapper.mapToProcedureRequest(getEhrComposition(ehrExtract),
                planStatement, SUBJECT, ENCOUNTERS, PRACTISE_CODE);

        assertThat(procedureRequest.getStatus()).isEqualTo(expectedStatus);
    }

    @Test
    public void When_PlanStatementDoesNotContainText_Expect_StatusIsSetToUnknown() {
        var inputXml = """
                <EhrExtract xmlns="urn:hl7-org:v3" classCode="EXTRACT" moodCode="EVN">
                    <component typeCode="COMP">
                        <ehrFolder classCode="FOLDER" moodCode="EVN">
                            <component typeCode="COMP">
                                <ehrComposition classCode="COMPOSITION" moodCode="EVN">
                                    <id root="7DFFAEC4-7527-4D80-A2BD-81BDEBA04400" />
                                    <component typeCode="COMP" >
                                        <PlanStatement classCode="OBS" moodCode="INT">
                                            <id root="6DFFAEC4-7527-4D80-A2BD-81BDEBA04400" />
                                            <code code="123456" codeSystem="1.2.3.4.5" displayName="12345"></code>
                                            <statusCode code="COMPLETE" />
                                        </PlanStatement>
                                    </component>
                                </ehrComposition>
                            </component>
                        </ehrFolder>
                    </component>
                </EhrExtract>
                """;

        var ehrExtract = unmarshallCodeElementFromString(inputXml);
        var planStatement = getPlanStatement(ehrExtract);

        when(codeableConceptMapper.mapToCodeableConcept(any()))
                .thenReturn(new CodeableConcept());

        ProcedureRequest procedureRequest = procedureRequestMapper.mapToProcedureRequest(getEhrComposition(ehrExtract),
                planStatement, SUBJECT, ENCOUNTERS, PRACTISE_CODE);

        assertThat(procedureRequest.getStatus()).isEqualTo(ProcedureRequestStatus.UNKNOWN);
    }

    private void assertFixedValues(RCMRMT030101UKPlanStatement planStatement, ProcedureRequest procedureRequest) {
        assertThat(procedureRequest.getId()).isEqualTo(planStatement.getId().getRoot());
        assertThat(procedureRequest.getIntent()).isEqualTo(ProcedureRequestIntent.PLAN);
        assertThat(procedureRequest.getIdentifierFirstRep().getSystem()).isEqualTo(IDENTIFIER_SYSTEM);
        assertThat(procedureRequest.getIdentifierFirstRep().getValue()).isEqualTo(planStatement.getId().getRoot());
        assertThat(procedureRequest.getMeta().getProfile().getFirst().getValue()).isEqualTo(META_PROFILE);
        assertThat(procedureRequest.getSubject().getResource().getIdElement().getValue()).isEqualTo(SUBJECT.getId());
    }

    private RCMRMT030101UKPlanStatement getPlanStatement(RCMRMT030101UKEhrExtract ehrExtract) {
        return ehrExtract.getComponent().getFirst()
            .getEhrFolder().getComponent().getFirst()
            .getEhrComposition().getComponent().getFirst()
            .getPlanStatement();
    }

    private RCMRMT030101UKEhrComposition getEhrComposition(RCMRMT030101UKEhrExtract ehrExtract) {

        return ehrExtract.getComponent().getFirst()
            .getEhrFolder().getComponent().getFirst()
            .getEhrComposition();
    }

    private void setUpCodeableConceptMock() {
        var codeableConcept = createCodeableConcept(CODING_CODE, CODING_SYSTEM, CODING_DISPLAY);
        when(codeableConceptMapper.mapToCodeableConcept(any())).thenReturn(codeableConcept);
    }

    @SneakyThrows
    private RCMRMT030101UKEhrExtract unmarshallCodeElement(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES_BASE + fileName), RCMRMT030101UKEhrExtract.class);
    }

    @SneakyThrows
    private RCMRMT030101UKEhrExtract unmarshallCodeElementFromString(String inputXml) {
        return unmarshallString(inputXml, RCMRMT030101UKEhrExtract.class);
    }
}
