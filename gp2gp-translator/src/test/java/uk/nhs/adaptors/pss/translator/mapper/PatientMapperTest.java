package uk.nhs.adaptors.pss.translator.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.util.ResourceUtils.getFile;

import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.v3.RCMRMT030101UKPatient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.SneakyThrows;
import uk.nhs.adaptors.pss.translator.service.IdGeneratorService;

@ExtendWith(MockitoExtension.class)
public class PatientMapperTest {

    private static final String XML_RESOURCES_BASE = "xml/Patient/";
    private static final String PATIENT_EXAMPLE_XML = "patient_example.xml";

    private static final String TEST_PATIENT_ID = "TEST_PATIENT_ID";
    private static final String TEST_ORGANIZATION_ID = "TEST_ORGANIZATION_ID";

    private static final String EXPECTED_NHS_NUMBER_SYSTEM_URL = "https://fhir.nhs.uk/Id/nhs-number";
    private static final String EXPECTED_META_PROFILE_URL = "https://fhir.nhs.uk/STU3/StructureDefinition/CareConnect-GPC-Patient-1";
    private static final String EXPECTED_NHS_NUMBER = "1234567890";
    private static final String EXPECTED_META_VERSION_ID = "1521806400000";
    private static final Organization ORGANIZATION = (Organization) new Organization().setId(TEST_ORGANIZATION_ID);

    private static RCMRMT030101UKPatient patientXml;

    @Mock
    private IdGeneratorService idGenerator;


    @InjectMocks
    private PatientMapper patientMapper;

    @BeforeAll
    @SneakyThrows
    static void setUpClass() {
        patientXml = unmarshallFile(getFile("classpath:" + XML_RESOURCES_BASE + PATIENT_EXAMPLE_XML), RCMRMT030101UKPatient.class);
    }

    @BeforeEach
    public void setup() {
        when(idGenerator.generateUuid()).thenReturn(TEST_PATIENT_ID);
    }

    @Test
    public void testIdMetaAndNhsNumberIsAddedToPatient() {
        Patient patient = patientMapper.mapToPatient(patientXml, null);

        assertThat(patient.getId()).isEqualTo(TEST_PATIENT_ID);

        assertThat(patient.hasMeta()).isTrue();
        assertThat(patient.getMeta().getVersionId()).isEqualTo(EXPECTED_META_VERSION_ID);
        assertThat(patient.getMeta().getProfile().getFirst().getValue()).isEqualTo(EXPECTED_META_PROFILE_URL);

        assertThat(patient.hasIdentifier()).isTrue();
        assertThat(patient.getIdentifierFirstRep().getSystem()).isEqualTo(EXPECTED_NHS_NUMBER_SYSTEM_URL);
        assertThat(patient.getIdentifierFirstRep().getValue()).isEqualTo(EXPECTED_NHS_NUMBER);

        assertThat(patient.hasManagingOrganization()).isFalse();
    }

    @Test
    public void testOrganizationReferenceIsAddedToPatient() {
        Patient patient = patientMapper.mapToPatient(patientXml, ORGANIZATION);

        assertThat(patient.hasIdentifier()).isTrue();
        assertThat(patient.hasManagingOrganization()).isTrue();
        assertThat(patient.getManagingOrganization().getResource()).isEqualTo(ORGANIZATION);
    }
}
