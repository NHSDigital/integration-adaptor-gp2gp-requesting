package uk.nhs.adaptors.pss.translator.mapper;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hl7.fhir.dstu3.model.Observation.ObservationRelationshipType.DERIVEDFROM;
import static org.hl7.fhir.dstu3.model.Observation.ObservationRelationshipType.HASMEMBER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.util.ResourceUtils.getFile;

import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import java.util.List;

import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.DomainResource;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.QuestionnaireResponse;
import org.hl7.v3.RCMRMT030101UK04EhrExtract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.SneakyThrows;
import uk.nhs.adaptors.pss.translator.util.DateFormatUtil;
import uk.nhs.adaptors.pss.translator.util.DegradedCodeableConcepts;
import uk.nhs.adaptors.pss.translator.util.ResourceReferenceUtil;
import static uk.nhs.adaptors.common.util.CodeableConceptUtils.createCodeableConcept;

@ExtendWith(MockitoExtension.class)
public class TemplateMapperTest {
    private static final String XML_RESOURCES_BASE = "xml/Template/";
    private static final String PRACTISE_CODE = "TESTPRACTISECODE";
    private static final String COMPOUND_ID = "C8B1BEAF-FB71-45D1-89DA-298148C00CE1";
    private static final String PATIENT_ID = "9A5D5A78-1F63-434C-9637-1D7E7843341B";
    private static final String ENCOUNTER_ID = "TEST_ID_MATCHING_ENCOUNTER";
    private static final String CODING_DISPLAY_MOCK = "Test Display";
    private static final String QUESTIONNAIRE_SUFFIX = "-QRSP";
    private static final String QUESTIONNAIRE_META = "https://fhir.nhs.uk/STU3/StructureDefinition/CareConnect-GPC"
        + "-QuestionnaireResponse-1";
    private static final String OBSERVATION_META = "https://fhir.nhs.uk/STU3/StructureDefinition/CareConnect-GPC-Observation-1";
    private static final String IDENTIFIER = "https://PSSAdaptor/TESTPRACTISECODE";
    private static final int THREE = 3;
    private static final String SNOMED_SYSTEM = "http://snomed.info/sct";
    private static final CodeableConcept CODEABLE_CONCEPT = createCodeableConcept(SNOMED_SYSTEM, null, CODING_DISPLAY_MOCK);

    private static final List<Encounter> ENCOUNTER_LIST = List.of(
        (Encounter) new Encounter().setId(ENCOUNTER_ID)
    );

    @Mock
    private CodeableConceptMapper codeableConceptMapper;

    @Mock
    private ResourceReferenceUtil resourceReferenceUtil;

    @InjectMocks
    private TemplateMapper templateMapper;

    @Test
    // Questionnaires have been removed from the PS Specification, test have been modified to make sure
    // they are not imported into the translation
    public void testMapTemplateWithAllData() {
        when(codeableConceptMapper.mapToCodeableConcept(any())).thenReturn(CODEABLE_CONCEPT);

        var ehrExtract = unmarshallEhrExtractElement("full_valid_template.xml");
        var mappedResources = templateMapper.mapResources(ehrExtract, getPatient(), ENCOUNTER_LIST, PRACTISE_CODE);

        assertThat(mappedResources.size()).isEqualTo(1);
        // var questionnaireResponse = (QuestionnaireResponse) mappedResources.get(0);
        var parentObservation = (Observation) mappedResources.get(0);

        // assertQuestionnaireResponse(questionnaireResponse, ENCOUNTER_ID, "original-text", "20100113151332");
        assertParentObservation(parentObservation, ENCOUNTER_ID, "20100113151332", "3707E1F0-9011-11EC-B1E5-0800200C9A66");

        // verify(resourceReferenceUtil, atLeast(1)).extractChildReferencesFromTemplate(any(), anyList());
    }

    @Test
    // Questionnaires have been removed from the PS Specification, test have been modified to make sure
    // they are not imported into the translation
    public void testMapTemplateWithFallbackData() {
        when(codeableConceptMapper.mapToCodeableConcept(any())).thenReturn(CODEABLE_CONCEPT);

        var ehrExtract = unmarshallEhrExtractElement("fallback_template.xml");
        var mappedResources = templateMapper.mapResources(ehrExtract, getPatient(), ENCOUNTER_LIST, PRACTISE_CODE);

        assertThat(mappedResources.size()).isEqualTo(1);
//      var questionnaireResponse = (QuestionnaireResponse) mappedResources.get(0);
        var parentObservation = (Observation) mappedResources.get(0);

//      assertQuestionnaireResponse(questionnaireResponse, null, "display-text", "20200101010101");
        assertParentObservation(parentObservation, null, "20200101010101", "9007E1F0-9011-11EC-B1E5-0800200C9A66");

        // The assertion below is poor as we manually select 1 item above / removed
//      assertThat(questionnaireResponse.getItem().size()).isOne();
    }

    @Test
    // Questionnaires have been removed from the PS Specification, test have been modified to make
    // sure they are not imported into the translation
    public void testMapNestedTemplate() {
        when(codeableConceptMapper.mapToCodeableConcept(any())).thenReturn(CODEABLE_CONCEPT);

        var ehrExtract = unmarshallEhrExtractElement("nested_template.xml");
        var mappedResources = templateMapper.mapResources(ehrExtract, getPatient(), ENCOUNTER_LIST, PRACTISE_CODE);

        assertThat(mappedResources.size()).isEqualTo(1);
        //var questionnaireResponse = (QuestionnaireResponse) mappedResources.get(0);
        var parentObservation = (Observation) mappedResources.get(0);

        //assertQuestionnaireResponse(questionnaireResponse, null, "display-text", "20200101010101");
        assertParentObservation(parentObservation, null, "20200101010101", "9007E1F0-9011-11EC-B1E5-0800200C9A66");

        // verify(resourceReferenceUtil, atLeast(1)).extractChildReferencesFromTemplate(any(), anyList());
    }

    @Test
    public void testNoMappableTemplates() {
        var ehrExtract = unmarshallEhrExtractElement("no_mappable_template.xml");
        var mappedResources = templateMapper.mapResources(ehrExtract, getPatient(), ENCOUNTER_LIST, PRACTISE_CODE);

        assertThat(mappedResources.size()).isZero();
    }

    @Test
    public void When_AddReferences_With_ValidTemplate_Expect_ReferencesAdded() {
        List<DomainResource> templates = List.of(
            (Observation) new Observation().setId("PARENT_OBSERVATION_ID")
        );

        var observations = List.of(
            (Observation) new Observation().setId("CHILD_OBSERVATION_ID_1"),
            (Observation) new Observation().setId("CHILD_OBSERVATION_ID_2")
        );

        var ehrExtract = unmarshallEhrExtractElement("unnested_observations_template.xml");

        templateMapper.addReferences(templates, observations, ehrExtract);

        var parentRelations = ((Observation) templates.get(0)).getRelated();
        var fistChildRelations = observations.get(0).getRelated();
        var secondChildRelations = observations.get(1).getRelated();

        assertThat(parentRelations.size()).isEqualTo(2);
        assertThat(parentRelations.get(0).getTarget().getReferenceElement().getIdPart()).isEqualTo("CHILD_OBSERVATION_ID_1");
        assertThat(parentRelations.get(0).getType()).isEqualTo(HASMEMBER);
        assertThat(parentRelations.get(1).getTarget().getReferenceElement().getIdPart()).isEqualTo("CHILD_OBSERVATION_ID_2");
        assertThat(parentRelations.get(1).getType()).isEqualTo(HASMEMBER);

        assertThat(fistChildRelations.size()).isOne();
        assertThat(fistChildRelations.get(0).getTarget().getReferenceElement().getIdPart()).isEqualTo("PARENT_OBSERVATION_ID");
        assertThat(fistChildRelations.get(0).getType()).isEqualTo(DERIVEDFROM);

        assertThat(secondChildRelations.size()).isOne();
        assertThat(secondChildRelations.get(0).getTarget().getReferenceElement().getIdPart()).isEqualTo("PARENT_OBSERVATION_ID");
        assertThat(secondChildRelations.get(0).getType()).isEqualTo(DERIVEDFROM);
    }

    @Test
    public void When_MapTemplateWithSnomedCode_Expect_CorrectlyMapped() {
        when(codeableConceptMapper.mapToCodeableConcept(any())).thenReturn(CODEABLE_CONCEPT);

        var ehrExtract = unmarshallEhrExtractElement("full_valid_template.xml");
        var mappedResources = templateMapper.mapResources(ehrExtract, getPatient(), ENCOUNTER_LIST, PRACTISE_CODE);
        var parentObservation = (Observation) mappedResources.get(0);

        assertThat(parentObservation.getCode()).isEqualTo(CODEABLE_CONCEPT);
    }

    @Test
    public void When_MapTemplateWithoutSnomedCode_Expect_DegradedCode() {
        var codeableConcept = createCodeableConcept("1.2.3.4.5", null, CODING_DISPLAY_MOCK);
        when(codeableConceptMapper.mapToCodeableConcept(any())).thenReturn(codeableConcept);

        var ehrExtract = unmarshallEhrExtractElement("full_valid_template.xml");
        var mappedResources = templateMapper.mapResources(ehrExtract, getPatient(), ENCOUNTER_LIST, PRACTISE_CODE);
        var parentObservation = (Observation) mappedResources.get(0);

        assertThat(parentObservation.getCode().getCodingFirstRep()).isEqualTo(DegradedCodeableConcepts.DEGRADED_OTHER);
    }

    private void assertQuestionnaireResponse(QuestionnaireResponse questionnaireResponse, String encounter, String linkId,
        String authored) {
        assertThat(questionnaireResponse.getId()).isEqualTo(COMPOUND_ID + QUESTIONNAIRE_SUFFIX);
        assertThat(questionnaireResponse.getMeta().getProfile().get(0).getValue()).isEqualTo(QUESTIONNAIRE_META);
        assertThat(questionnaireResponse.getIdentifier().getSystem()).isEqualTo(IDENTIFIER);
        assertThat(questionnaireResponse.getIdentifier().getValue()).isEqualTo(COMPOUND_ID);
        assertThat(questionnaireResponse.getParentFirstRep().getResource().getIdElement().getValue()).isEqualTo(COMPOUND_ID);
        assertThat(questionnaireResponse.getStatus()).isEqualTo(QuestionnaireResponse.QuestionnaireResponseStatus.COMPLETED);
        assertThat(questionnaireResponse.getSubject().getResource().getIdElement().getValue()).isEqualTo(PATIENT_ID);
        assertThat(questionnaireResponse.getItemFirstRep().getLinkId()).isEqualTo(linkId);
        assertThat(questionnaireResponse.getAuthoredElement().asStringValue()).isEqualTo(
            DateFormatUtil.parseToDateTimeType(authored).asStringValue());

        if (encounter == null) {
            assertThat(questionnaireResponse.getContext().getResource()).isNull();
        } else {
            assertThat(questionnaireResponse.getContext().getResource().getIdElement().getValue()).isEqualTo(encounter);
        }
    }

    private void assertParentObservation(Observation parentObservation, String encounter, String issued, String performer) {
        assertThat(parentObservation.getId()).isEqualTo(COMPOUND_ID);
        assertThat(parentObservation.getMeta().getProfile().get(0).getValue()).isEqualTo(OBSERVATION_META);
        assertThat(parentObservation.getIdentifierFirstRep().getSystem()).isEqualTo(IDENTIFIER);
        assertThat(parentObservation.getIdentifierFirstRep().getValue()).isEqualTo(COMPOUND_ID);
        assertThat(parentObservation.getSubject().getResource().getIdElement().getValue()).isEqualTo(PATIENT_ID);
        assertThat(parentObservation.getStatus()).isEqualTo(Observation.ObservationStatus.FINAL);
        assertThat(parentObservation.getCode().getCodingFirstRep().getDisplay()).isEqualTo(CODING_DISPLAY_MOCK);
        assertThat(parentObservation.getIssuedElement().asStringValue()).isEqualTo(
            DateFormatUtil.parseToInstantType(issued).asStringValue());
        assertThat(parentObservation.getPerformerFirstRep().getReference()).contains(performer);

        if (encounter == null) {
            assertThat(parentObservation.getContext().getResource()).isNull();
        } else {
            assertThat(parentObservation.getContext().getResource().getIdElement().getValue()).isEqualTo(encounter);
        }
    }

    private Patient getPatient() {
        var patient = new Patient();
        patient.setId(PATIENT_ID);
        return patient;
    }

    @SneakyThrows
    private RCMRMT030101UK04EhrExtract unmarshallEhrExtractElement(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES_BASE + fileName), RCMRMT030101UK04EhrExtract.class);
    }
}
