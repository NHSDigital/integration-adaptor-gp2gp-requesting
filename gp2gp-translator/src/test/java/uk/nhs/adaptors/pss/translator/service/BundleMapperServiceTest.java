package uk.nhs.adaptors.pss.translator.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.util.ResourceUtils.getFile;

import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.DomainResource;
import org.hl7.fhir.dstu3.model.Location;
import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.v3.RCMRIN030000UKMessage;
import org.hl7.v3.RCMRMT030101UKAgentDirectory;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKLocation;
import org.hl7.v3.RCMRMT030101UKPatient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.SneakyThrows;
import uk.nhs.adaptors.pss.translator.exception.BundleMappingException;
import uk.nhs.adaptors.pss.translator.generator.BundleGenerator;
import uk.nhs.adaptors.pss.translator.mapper.AgentDirectoryMapper;
import uk.nhs.adaptors.pss.translator.mapper.AllergyIntoleranceMapper;
import uk.nhs.adaptors.pss.translator.mapper.BloodPressureMapper;
import uk.nhs.adaptors.pss.translator.mapper.ConditionMapper;
import uk.nhs.adaptors.pss.translator.mapper.DocumentReferenceMapper;
import uk.nhs.adaptors.pss.translator.mapper.DuplicateObservationStatementMapper;
import uk.nhs.adaptors.pss.translator.mapper.EncounterMapper;
import uk.nhs.adaptors.pss.translator.mapper.ImmunizationMapper;
import uk.nhs.adaptors.pss.translator.mapper.LocationMapper;
import uk.nhs.adaptors.pss.translator.mapper.ObservationCommentMapper;
import uk.nhs.adaptors.pss.translator.mapper.ObservationMapper;
import uk.nhs.adaptors.pss.translator.mapper.OrganizationMapper;
import uk.nhs.adaptors.pss.translator.mapper.PatientMapper;
import uk.nhs.adaptors.pss.translator.mapper.ProcedureRequestMapper;
import uk.nhs.adaptors.pss.translator.mapper.ReferralRequestMapper;
import uk.nhs.adaptors.pss.translator.mapper.TemplateMapper;
import uk.nhs.adaptors.pss.translator.mapper.UnknownPractitionerHandler;
import uk.nhs.adaptors.pss.translator.mapper.diagnosticreport.DiagnosticReportMapper;
import uk.nhs.adaptors.pss.translator.mapper.diagnosticreport.SpecimenCompoundsMapper;
import uk.nhs.adaptors.pss.translator.mapper.diagnosticreport.SpecimenMapper;
import uk.nhs.adaptors.pss.translator.mapper.medication.MedicationRequestMapper;

@ExtendWith(MockitoExtension.class)
public class BundleMapperServiceTest {

    private static final String XML_RESOURCES_BASE = "xml/RCMRIN030000UK06/";
    private static final String STRUCTURED_RECORD_XML = "structuredRecord.xml";
    private static final String ENCOUNTER_KEY = "encounters";
    private static final String CONSULTATION_KEY = "consultations";
    private static final String TOPIC_KEY = "topics";
    private static final String CATEGORY_KEY = "categories";
    private static final String LOSING_ODS_CODE = "S234";

    @Mock
    private BundleGenerator bundleGenerator;
    @Mock
    private PatientMapper patientMapper;
    @Mock
    private AgentDirectoryMapper agentDirectoryMapper;
    @Mock
    private LocationMapper locationMapper;
    @Mock
    private EncounterMapper encounterMapper;
    @Mock
    private ProcedureRequestMapper procedureRequestMapper;
    @Mock
    private ReferralRequestMapper referralRequestMapper;
    @Mock
    private BloodPressureMapper bloodPressureMapper;
    @Mock
    private ObservationMapper observationMapper;
    @Mock
    private ConditionMapper conditionMapper;
    @Mock
    private ImmunizationMapper immunizationMapper;
    @Mock
    private ObservationCommentMapper observationCommentMapper;
    @Mock
    private MedicationRequestMapper medicationRequestMapper;
    @Mock
    private UnknownPractitionerHandler unknownPractitionerHandler;
    @Mock
    private DocumentReferenceMapper documentReferenceMapper;
    @Mock
    private TemplateMapper templateMapper;
    @Mock
    private OrganizationMapper organizationMapper;
    @Mock
    private AllergyIntoleranceMapper allergyIntoleranceMapper;
    @Mock
    private DiagnosticReportMapper diagnosticReportMapper;
    @Mock
    private SpecimenMapper specimenMapper;
    @Mock
    private SpecimenCompoundsMapper specimenCompoundsMapper;

    @Mock
    private DuplicateObservationStatementMapper duplicateObservationStatementMapper;

    @InjectMocks
    private BundleMapperService bundleMapperService;

    @BeforeEach
    public void setup() {
        when(bundleGenerator.generateBundle()).thenReturn(new Bundle());

        var agentResourceList = new ArrayList<DomainResource>();
        agentResourceList.add(new Organization());
        List mockedList = mock(List.class);

        Map<String, List<? extends DomainResource>> encounterResources = new HashMap<>();
        encounterResources.put(ENCOUNTER_KEY, new ArrayList<>());
        encounterResources.put(CONSULTATION_KEY, new ArrayList<>());
        encounterResources.put(TOPIC_KEY, new ArrayList<>());
        encounterResources.put(CATEGORY_KEY, new ArrayList<>());

        var location1 = new Location();
        location1.setName("EMIS Test Practice Location");
        location1.setId("1");

        when(locationMapper.mapToLocation(any(RCMRMT030101UKLocation.class), anyString())).thenReturn(location1);

        when(agentDirectoryMapper.mapAgentDirectory(any())).thenReturn(mockedList);
        when(mockedList.stream()).thenReturn(agentResourceList.stream());
        when(patientMapper.mapToPatient(any(RCMRMT030101UKPatient.class), any(Organization.class))).thenReturn(new Patient());
        when(encounterMapper.mapEncounters(any(RCMRMT030101UKEhrExtract.class), any(Patient.class), any(String.class), any(List.class)))
            .thenReturn(encounterResources);
        when(organizationMapper.mapAuthorOrganization(anyString(), anyList())).thenReturn(new Organization());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAllMappersHaveBeenUsed() throws BundleMappingException {
        final RCMRIN030000UKMessage xml = unmarshallCodeElement(STRUCTURED_RECORD_XML);
        Bundle bundle = bundleMapperService.mapToBundle(xml, LOSING_ODS_CODE, new ArrayList<>());

        verify(patientMapper).mapToPatient(any(RCMRMT030101UKPatient.class), any(Organization.class));
        verify(organizationMapper).mapAuthorOrganization(anyString(), anyList());
        verify(duplicateObservationStatementMapper).mergeOrRemoveDuplicateObservationStatements(any(RCMRMT030101UKEhrExtract.class));
        verify(agentDirectoryMapper).mapAgentDirectory(any(RCMRMT030101UKAgentDirectory.class));
        verify(locationMapper, atLeast(1)).mapToLocation(any(RCMRMT030101UKLocation.class), anyString());
        verify(encounterMapper).mapEncounters(any(RCMRMT030101UKEhrExtract.class), any(Patient.class), anyString(), anyList());
        verify(procedureRequestMapper).mapResources(
            any(RCMRMT030101UKEhrExtract.class),
            any(Patient.class),
            anyList(),
            anyString()
        );
        verify(referralRequestMapper).mapResources(
            any(RCMRMT030101UKEhrExtract.class),
            any(Patient.class),
            anyList(),
            anyString()
        );
        verify(bloodPressureMapper).mapResources(any(RCMRMT030101UKEhrExtract.class), any(Patient.class), anyList(),
            any(String.class));
        verify(observationMapper).mapResources(any(RCMRMT030101UKEhrExtract.class), any(Patient.class), anyList(), anyString());
        verify(conditionMapper).mapResources(any(RCMRMT030101UKEhrExtract.class), any(Patient.class), anyList(), anyString());
        verify(immunizationMapper).mapResources(any(RCMRMT030101UKEhrExtract.class), any(Patient.class), anyList(),
            any(String.class));
        verify(observationCommentMapper).mapResources(any(RCMRMT030101UKEhrExtract.class), any(Patient.class), anyList(),
            anyString());
        verify(medicationRequestMapper, atLeast(1))
            .mapResources(any(RCMRMT030101UKEhrExtract.class), any(Patient.class), anyList(), anyString());
        verify(unknownPractitionerHandler).updateUnknownPractitionersRefs(bundle);
        verify(documentReferenceMapper).mapResources(any(RCMRMT030101UKEhrExtract.class), any(Patient.class), anyList(),
            any(Organization.class), anyList());
        verify(templateMapper).mapResources(any(RCMRMT030101UKEhrExtract.class), any(Patient.class), anyList(),
            anyString());
        verify(allergyIntoleranceMapper).mapResources(any(RCMRMT030101UKEhrExtract.class), any(Patient.class), anyList(), anyString());
        verify(diagnosticReportMapper).mapResources(
            any(RCMRMT030101UKEhrExtract.class), any(Patient.class), anyList(), any(String.class), any(ArrayList.class));
        verify(specimenMapper).mapSpecimens(any(RCMRMT030101UKEhrExtract.class), anyList(), any(Patient.class), anyString());
        verify(diagnosticReportMapper).handleChildObservationComments(any(RCMRMT030101UKEhrExtract.class), anyList());
        verify(specimenCompoundsMapper).handleSpecimenChildComponents(
            any(RCMRMT030101UKEhrExtract.class), anyList(), anyList(), anyList(), any(Patient.class), anyList(), anyString()
        );
    }

    @SneakyThrows
    private RCMRIN030000UKMessage unmarshallCodeElement(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES_BASE + fileName), RCMRIN030000UKMessage.class);
    }
}
