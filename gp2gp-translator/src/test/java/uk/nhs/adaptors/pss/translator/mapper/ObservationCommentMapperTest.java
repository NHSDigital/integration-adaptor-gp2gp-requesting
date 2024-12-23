package uk.nhs.adaptors.pss.translator.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.util.ResourceUtils.getFile;

import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import java.util.Collections;
import java.util.List;

import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKNarrativeStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lombok.SneakyThrows;
import uk.nhs.adaptors.pss.translator.util.DateFormatUtil;

public class ObservationCommentMapperTest {

    private static final String XML_RESOURCES_BASE = "xml/ObservationComment/";
    private static final String PATIENT_ID = "123.456";
    private static final String ENCOUNTER_ID = "5E496953-065B-41F2-9577-BE8F2FBD0757";
    private static final String META_URL = "https://fhir.nhs.uk/STU3/StructureDefinition/CareConnect-GPC-Observation-1";
    private static final String CODING_SYSTEM = "http://snomed.info/sct";
    private static final String CODING_CODE = "37331000000100";
    private static final String PRACTISE_CODE = "TESTPRACTISECODE";
    private static final String IDENTIFIER_SYSTEM = "https://PSSAdaptor/TESTPRACTISECODE";
    private static final int EXPECTED_OBSERVATION_COUNT = 3;

    private final ObservationCommentMapper observationCommentMapper = new ObservationCommentMapper();
    private Patient patient;

    @BeforeEach
    public void setUp() {
        patient = new Patient();
        patient.setId(PATIENT_ID);
    }

    @Test
    public void mapObservationsWithFullDataSingleObservation() {
        var ehrExtract = unmarshallEhrExtract("single_narrative_statement.xml");
        var narrativeStatement = getNarrativeStatement(ehrExtract);
        var narrativeStatementId = narrativeStatement.getId().getRoot();

        var encounter = new Encounter();
        encounter.setId(ENCOUNTER_ID);

        List<Observation> observations =
            observationCommentMapper.mapResources(ehrExtract, patient, Collections.singletonList(encounter), PRACTISE_CODE);

        var observation = observations.getFirst();

        assertThat(observation.getId()).isEqualTo(narrativeStatementId);
        assertThat(observation.getMeta().getProfile().getFirst().getValue()).isEqualTo(META_URL);
        assertThat(observation.getStatus()).isEqualTo(Observation.ObservationStatus.FINAL);
        assertThat(observation.getSubject().getResource()).isEqualTo(patient);

        var coding = observation.getCode().getCoding();
        assertThat(coding.getFirst().getCode()).isEqualTo(CODING_CODE);
        assertThat(coding.getFirst().getSystem()).isEqualTo(CODING_SYSTEM);

        assertThat(observation.getEffective().toString()).isEqualTo(
            DateFormatUtil.parseToDateTimeType(narrativeStatement.getAvailabilityTime().getValue()).toString());

        assertThat(observation.getIssuedElement().asStringValue()).isEqualTo("2020-10-12T13:33:44.000+00:00");

        var identifier = observation.getIdentifier().getFirst();
        assertThat(identifier.getValue()).isEqualTo(narrativeStatementId);
        assertThat(identifier.getSystem()).isEqualTo(IDENTIFIER_SYSTEM);

        assertThat(observation.getPerformer().getFirst().getReference()).isEqualTo("Practitioner/2D70F602-6BB1-47E0-B2EC-39912A59787D");
        assertThat(observation.getComment()).isEqualTo("Some example text");
        assertThat(observation.getContext().getResource()).isEqualTo(encounter);
    }

    @Test
    public void When_MappingObservationsWithBloodPressureTriple_Expect_ObservationCommentNotToBeCreated() {
        var ehrExtract = unmarshallEhrExtract("narrative_statement_in_blood_pressure.xml");

        var encounter = new Encounter();
        encounter.setId(ENCOUNTER_ID);

        List<Observation> observations = observationCommentMapper.mapResources(
                ehrExtract,
                patient,
                Collections.singletonList(encounter),
                PRACTISE_CODE);

        assertThat(observations).isEmpty();
    }

    @Test
    public void mapObservationsWithFullDataMultipleObservations() {
        var ehrExtract = unmarshallEhrExtract("multiple_narrative_statements.xml");

        List<Observation> observations =
            observationCommentMapper.mapResources(ehrExtract, patient, Collections.emptyList(), PRACTISE_CODE);

        assertThat(observations).hasSize(EXPECTED_OBSERVATION_COUNT);
    }

    @Test
    public void mapObservationsNoObservationToMap() {
        var ehrExtract = unmarshallEhrExtract("no_narrative_statement.xml");

        List<Observation> observations =
            observationCommentMapper.mapResources(ehrExtract, patient, Collections.emptyList(), PRACTISE_CODE);

        assertThat(observations).isEmpty();
    }

    @Test
    public void mapObservationsNarrativeStatementHasReferredToExternalDocuments() {
        var ehrExtract = unmarshallEhrExtract("narrative_statement_has_referred_to_external_document.xml");

        List<Observation> observations =
            observationCommentMapper.mapResources(ehrExtract, patient, Collections.emptyList(), PRACTISE_CODE);

        assertThat(observations).isEmpty();
    }

    @Test
    public void mapObservationsCompositionHasNoAvailabilityTime() {
        var ehrExtract = unmarshallEhrExtract("nullflavour_availability_time.xml");

        List<Observation> observations =
            observationCommentMapper.mapResources(ehrExtract, patient, Collections.emptyList(), PRACTISE_CODE);

        assertNull(observations.getFirst().getIssuedElement().asStringValue());
    }

    @Test
    public void mapObservationWithFullDataNoMappedEncounter() {
        var ehrExtract = unmarshallEhrExtract("single_narrative_statement.xml");

        List<Observation> observations =
            observationCommentMapper.mapResources(ehrExtract, patient, Collections.emptyList(), PRACTISE_CODE);

        // Calling `getContext` auto creates a Reference object so asserting the reference is null
        assertNull(observations.getFirst().getContext().getReference());
    }

    @Test
    public void mapObservationWithNoComment() {
        var ehrExtract = unmarshallEhrExtract("whitespace_only_text_field.xml");

        List<Observation> observations =
            observationCommentMapper.mapResources(ehrExtract, patient, Collections.emptyList(), PRACTISE_CODE);

        assertNull(observations.getFirst().getComment());
    }

    private RCMRMT030101UKNarrativeStatement getNarrativeStatement(RCMRMT030101UKEhrExtract ehrExtract) {
        return ehrExtract.getComponent().getFirst()
            .getEhrFolder().getComponent().getFirst()
            .getEhrComposition().getComponent().getFirst()
            .getNarrativeStatement();
    }

    @SneakyThrows
    private RCMRMT030101UKEhrExtract unmarshallEhrExtract(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES_BASE + fileName), RCMRMT030101UKEhrExtract.class);
    }
}
