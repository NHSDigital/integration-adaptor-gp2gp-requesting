package uk.nhs.adaptors.pss.translator.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.util.ResourceUtils.getFile;

import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import java.util.List;
import java.util.stream.Stream;

import org.hl7.fhir.dstu3.model.AllergyIntolerance;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Condition;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import lombok.SneakyThrows;

@SpringBootTest
@ExtendWith({SpringExtension.class, MockitoExtension.class})
public class ConditionMapperIT {

    private static final String CONDITION_RESOURCES_BASE = "xml/condition/";
    private static final String NAMED_STATEMENT_REF_ID = "NAMED_STATEMENT_REF_ID";
    private static final String PATIENT_ID = "PATIENT_ID";
    private static final String PRACTISE_CODE = "TESTPRACTISECODE";

    private static final String SNOMED_SYSTEM = "http://snomed.info/sct";
    private static final String OBSERVATION_CONCEPT_ID = "240534005";
    private static final String OBSERVATION_CONCEPT_DESCRIPTION = "Hand wart";
    private static final String ALLERGY_CONCEPT_ID = "442728009";
    private static final String ALLERGY_CONCEPT_DESCRIPTION = "Non-coeliac gluten sensitivity";

    @Autowired
    private ConditionMapper conditionMapper;

    private Patient patient;

    @BeforeEach
    public void setUp() {
        patient = (Patient) new Patient().setId(PATIENT_ID);
    }

    public static Stream<Arguments> ehrExtractsWithObservationActualProblemCode() {
        return Stream.of(
            Arguments.of("linkset_problem_observation-in-category.xml"),
            Arguments.of("linkset-problem-observation-flat.xml"),
            Arguments.of("linkset-problem-observation-in-topic.xml")
        );
    }

    @ParameterizedTest
    @MethodSource("ehrExtractsWithObservationActualProblemCode")
    public void When_AddReferences_With_ObservationAsProblem_Expect_CodeAddedToCondition(String filename) {

        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract(filename);
        final List<Condition> conditions = conditionMapper.mapResources(ehrExtract, patient, List.of(), PRACTISE_CODE);

        conditionMapper.addReferences(buildBundleWithNamedStatementObservation(), conditions, ehrExtract);

        var code = conditions.getFirst().getCode().getCodingFirstRep();

        assertEquals(OBSERVATION_CONCEPT_DESCRIPTION, code.getDisplay());
        assertEquals(OBSERVATION_CONCEPT_ID, code.getCode());
        assertEquals(SNOMED_SYSTEM, code.getSystem());
    }

    public static Stream<Arguments> ehrExtractsWithAllergyIntoleranceActualProblemCode() {
        return Stream.of(
            Arguments.of("linkset-problem-allergy-in-category.xml"),
            Arguments.of("linkset-problem-allergy-flat.xml"),
            Arguments.of("linkset-problem-allergy-in-topic.xml")
        );
    }

    @ParameterizedTest
    @MethodSource("ehrExtractsWithAllergyIntoleranceActualProblemCode")
    public void When_AddReferences_With_AllergyIntoleranceAsProblem_Expect_CodeAddedToCondition(String filename) {

        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtract(filename);
        final List<Condition> conditions = conditionMapper.mapResources(ehrExtract, patient, List.of(), PRACTISE_CODE);

        conditionMapper.addReferences(buildBundleWithNamedStatementAllergy(), conditions, ehrExtract);

        var code = conditions.getFirst().getCode().getCoding().get(1);

        assertEquals(ALLERGY_CONCEPT_DESCRIPTION, code.getDisplay());
        assertEquals(ALLERGY_CONCEPT_ID, code.getCode());
        assertEquals(SNOMED_SYSTEM, code.getSystem());
    }

    @SneakyThrows
    private RCMRMT030101UKEhrExtract unmarshallEhrExtract(String filename) {
        return unmarshallFile(getFile("classpath:" + CONDITION_RESOURCES_BASE + filename), RCMRMT030101UKEhrExtract.class);
    }

    private Bundle buildBundleWithNamedStatementObservation() {
        return new Bundle()
            .addEntry(new Bundle.BundleEntryComponent()
                .setResource(new Observation().setId(NAMED_STATEMENT_REF_ID)));
    }

    private Bundle buildBundleWithNamedStatementAllergy() {
        return new Bundle()
            .addEntry(new Bundle.BundleEntryComponent()
                .setResource(new AllergyIntolerance().setId(NAMED_STATEMENT_REF_ID)));
    }

}
