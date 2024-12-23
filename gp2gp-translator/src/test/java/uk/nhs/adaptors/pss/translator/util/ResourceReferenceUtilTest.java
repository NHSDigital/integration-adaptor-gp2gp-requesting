package uk.nhs.adaptors.pss.translator.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.springframework.util.ResourceUtils.getFile;

import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.v3.RCMRMT030101UKCompoundStatement;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKObservationStatement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import lombok.SneakyThrows;

@ExtendWith(MockitoExtension.class)
public class ResourceReferenceUtilTest {

    private static final String XML_RESOURCES_COMPOSITION = "xml/ResourceReference/EhrComposition/";
    private static final String XML_RESOURCES_COMPOUND = "xml/ResourceReference/CompoundStatement/";
    private static final int TWO = 2;


    @Mock
    private DatabaseImmunizationChecker immunizationChecker;

    @InjectMocks
    private ResourceReferenceUtil resourceReferenceUtil;

    @Test
    public void testMedicationResourcesReferencedAtEhrCompositionLevel() {
        final RCMRMT030101UKEhrComposition ehrComposition = unmarshallEhrCompositionElement("ehr_composition_medication.xml");

        List<Reference> references = new ArrayList<>();
        resourceReferenceUtil.extractChildReferencesFromEhrComposition(ehrComposition, references);

        assertThat(references).hasSize(TWO);
        assertThat(references.getFirst().getReference()).isEqualTo("MedicationRequest/A0A70B62-2649-4C8F-B3AB-618B8257C942");
        assertThat(references.get(1).getReference()).isEqualTo("MedicationRequest/9B4B797A-D674-4362-B666-2ADC8551EEDA");
    }

    @Test
    public void testMedicationResourcesReferencedAtCompoundStatementLevel() {
        final RCMRMT030101UKCompoundStatement compoundStatement = unmarshallCompoundStatementElement("compound_statement_medication.xml");

        List<Reference> references = new ArrayList<>();
        resourceReferenceUtil.extractChildReferencesFromCompoundStatement(compoundStatement, references);

        assertThat(references).hasSize(TWO);
        assertThat(references.getFirst().getReference()).isEqualTo("MedicationRequest/A0A70B62-2649-4C8F-B3AB-618B8257C942");
        assertThat(references.get(1).getReference()).isEqualTo("MedicationRequest/9B4B797A-D674-4362-B666-2ADC8551EEDA");
    }

//    removed as part of NIAD_2190
//    @Test
//    public void testTemplateResourcesReferencedAtEhrCompositionLevel() {
//        final RCMRMT030101UK04EhrComposition ehrComposition = unmarshallEhrCompositionElement("ehr_composition_template.xml");
//
//        List<Reference> references = new ArrayList<>();
//        resourceReferenceUtil.extractChildReferencesFromEhrComposition(ehrComposition, references);
//
//        assertThat(references.size()).isEqualTo(FOUR);
//        assertThat(references.getFirst().getReference()).isEqualTo("QuestionnaireResponse/7334D39A-BBB3-424A-B5D3-E841BCA39BF7-QRSP");
//        assertThat(references.get(1).getReference()).isEqualTo("Observation/7334D39A-BBB3-424A-B5D3-E841BCA39BF7");
//        assertThat(references.get(2).getReference()).isEqualTo("Observation/3DCC9FC9-1873-4004-9789-C4E5C52B02B9");
//        assertThat(references.get(THREE).getReference()).isEqualTo("Observation/278ADD5F-2AC7-48DC-966A-0BA7C029C793");
//    }

//    removed as part of NIAD_2190
//    @Test
//    public void testTemplateResourcesReferencedAtCompoundStatementLevel() {
//        final RCMRMT030101UK04CompoundStatement compoundStatement = unmarshallCompoundStatementElement("compound_statement_template.xml");
//
//        List<Reference> references = new ArrayList<>();
//        resourceReferenceUtil.extractChildReferencesFromCompoundStatement(compoundStatement, references);
//
//        assertThat(references.size()).isEqualTo(FOUR);
//        assertThat(references.getFirst().getReference()).isEqualTo("QuestionnaireResponse/7334D39A-BBB3-424A-B5D3-E841BCA39BF7-QRSP");
//        assertThat(references.get(1).getReference()).isEqualTo("Observation/7334D39A-BBB3-424A-B5D3-E841BCA39BF7");
//        assertThat(references.get(2).getReference()).isEqualTo("Observation/3DCC9FC9-1873-4004-9789-C4E5C52B02B9");
//        assertThat(references.get(THREE).getReference()).isEqualTo("Observation/278ADD5F-2AC7-48DC-966A-0BA7C029C793");
//    }

    @Test
    public void testTemplateChildResourcesReferencedAsQuestionnaireAnswers() {
        final RCMRMT030101UKEhrComposition ehrComposition = unmarshallEhrCompositionElement("ehr_composition_template.xml");

        List<Reference> references = new ArrayList<>();
        resourceReferenceUtil.extractChildReferencesFromTemplate(
            ehrComposition.getComponent().getFirst().getCompoundStatement(), references);

        assertThat(references).hasSize(2);
        assertThat(references.getFirst().getReference()).isEqualTo("Observation/3DCC9FC9-1873-4004-9789-C4E5C52B02B9");
        assertThat(references.get(1).getReference()).isEqualTo("Observation/278ADD5F-2AC7-48DC-966A-0BA7C029C793");
    }

    @ParameterizedTest
    @MethodSource("ehrCompositionResourceFiles")
    public void testResourcesReferencedAtEhrCompositionLevel(String inputXML, String referenceString) {
        final RCMRMT030101UKEhrComposition ehrComposition = unmarshallEhrCompositionElement(inputXML);
        lenient().when(immunizationChecker.isImmunization(any())).thenAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                RCMRMT030101UKObservationStatement statement = (RCMRMT030101UKObservationStatement) invocation.getArgument(0);
                return statement.getCode().getCode().equals("1664081000000114");
            }
        });

        List<Reference> references = new ArrayList<>();
        resourceReferenceUtil.extractChildReferencesFromEhrComposition(ehrComposition, references);

        assertThat(references.size()).isOne();
        assertThat(references.getFirst().getReference()).isEqualTo(referenceString);
    }

    private static Stream<Arguments> ehrCompositionResourceFiles() {
        return Stream.of(
            Arguments.of("ehr_composition_observation_comment.xml", "Observation/5E496953-065B-41F2-9577-BE8F2FBD0757"),
            Arguments.of("ehr_composition_document_reference.xml", "DocumentReference/5E496953-065B-41F2-9577-BE8F2FBD0757"),
            Arguments.of("ehr_composition_immunization.xml", "Immunization/82A39454-299F-432E-993E-5A6232B4E099"),
            Arguments.of("ehr_composition_allergy_intolerance.xml", "AllergyIntolerance/6D35AFC6-464A-4432-88E0-0A7380E281C5"),
            Arguments.of("ehr_composition_observation_uncategorised.xml", "Observation/E9396E5B-B81A-4D69-BF0F-DFB1DFE80A33"),
            Arguments.of("ehr_composition_condition.xml", "Condition/5968B6B2-8E9A-4A78-8979-C8F14F4D274B"),
            Arguments.of("ehr_composition_blood_pressure.xml", "Observation/FE739904-2AAB-4B3F-9718-84BE019FD483"),
            Arguments.of("ehr_composition_diagnostic_report.xml", "DiagnosticReport/2E135210-74C2-478A-90DC-0FC9F7B8103C")
        );
    }

    @ParameterizedTest
    @MethodSource("compoundStatementResourceFiles")
    public void testResourcesReferencedAtCompoundStatementLevel(String inputXML, String referenceString) {
        final RCMRMT030101UKCompoundStatement compoundStatement = unmarshallCompoundStatementElement(inputXML);
        lenient().when(immunizationChecker.isImmunization(any())).thenAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                RCMRMT030101UKObservationStatement statement = (RCMRMT030101UKObservationStatement) invocation.getArgument(0);
                return statement.getCode().getCode().equals("1664081000000114");
            }
        });

        List<Reference> references = new ArrayList<>();
        resourceReferenceUtil.extractChildReferencesFromCompoundStatement(compoundStatement, references);

        assertThat(references.size()).isOne();
        assertThat(references.getFirst().getReference()).isEqualTo(referenceString);
    }

    private static Stream<Arguments> compoundStatementResourceFiles() {
        return Stream.of(
            Arguments.of("compound_statement_observation_comment.xml", "Observation/5E496953-065B-41F2-9577-BE8F2FBD0757"),
            Arguments.of("compound_statement_document_reference.xml", "DocumentReference/5E496953-065B-41F2-9577-BE8F2FBD0757"),
            Arguments.of("compound_statement_immunization.xml", "Immunization/82A39454-299F-432E-993E-5A6232B4E099"),
            Arguments.of("compound_statement_allergy_intolerance.xml", "AllergyIntolerance/6D35AFC6-464A-4432-88E0-0A7380E281C5"),
            Arguments.of("compound_statement_observation_uncategorised.xml", "Observation/E9396E5B-B81A-4D69-BF0F-DFB1DFE80A33"),
            Arguments.of("compound_statement_condition.xml", "Condition/5968B6B2-8E9A-4A78-8979-C8F14F4D274B"),
            Arguments.of("compound_statement_blood_pressure.xml", "Observation/FE739904-2AAB-4B3F-9718-84BE019FD483"),
            Arguments.of("compound_statement_diagnostic_report.xml", "DiagnosticReport/2E135210-74C2-478A-90DC-0FC9F7B8103C")
        );
    }

    @SneakyThrows
    private RCMRMT030101UKCompoundStatement unmarshallCompoundStatementElement(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES_COMPOUND + fileName),
            RCMRMT030101UKCompoundStatement.class);
    }

    @SneakyThrows
    private RCMRMT030101UKEhrComposition unmarshallEhrCompositionElement(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES_COMPOSITION + fileName), RCMRMT030101UKEhrComposition.class);
    }
}
