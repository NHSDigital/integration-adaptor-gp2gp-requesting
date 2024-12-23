package uk.nhs.adaptors.pss.translator.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallString;

import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.v3.CD;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.SneakyThrows;
import uk.nhs.adaptors.connector.dao.SnomedCTDao;
import uk.nhs.adaptors.connector.model.SnomedCTDescription;

@ExtendWith(MockitoExtension.class)
public class CodeableConceptMapperTest {

    private static final String CONCEPT_ID = "22298006";
    private static final String DISPLAY_NAME_1 = "Myocardial infarction";
    private static final String DISPLAY_NAME_2 = "Heart attack";
    private static final String DISPLAY_NAME_3 = "Different display name";
    private static final String ORIGINAL_TEXT = "Test original text";
    private static final String DESCRIPTION_ID = "descriptionId";
    private static final String DESCRIPTION_DISPLAY = "descriptionDisplay";
    private static final int EXPECTED_CODING_SIZE = 3;
    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    private static final SnomedCTDescription SNOMED_DESCRIPTION = SnomedCTDescription.builder()
        .id("test_description_id")
        .conceptid("test_concept_id")
        .term("Heart attack")
        .build();
    private static final SnomedCTDescription SNOMED_PREFERRED = SnomedCTDescription.builder()
        .id("test_preferred_id")
        .conceptid("test_concept_id")
        .term("Myocardial infarction")
        .build();

    @Mock
    private SnomedCTDao snomedCTDao;

    @InjectMocks
    private CodeableConceptMapper codeableConceptMapper;

    @Test
    public void mapSnomedCodeWithConceptIdDisplayNameDescriptionIsPreferredTerm() {
        when(snomedCTDao.getSnomedDescriptionUsingConceptIdAndDisplayName(any(), any())).thenReturn(SNOMED_PREFERRED);
        when(snomedCTDao.getSnomedDescriptionPreferredTermUsingConceptId(any())).thenReturn(SNOMED_PREFERRED);
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="22298006" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
            displayName="Myocardial infarction">
                <originalText>Test original text</originalText>
            </code>
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getCoding().getFirst().getCode())
            .isEqualTo(CONCEPT_ID);
        assertThat(codeableConcept.getCoding().getFirst().getDisplay())
            .isEqualTo(SNOMED_PREFERRED.getTerm());
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().getFirst().getUrl())
            .isEqualTo(DESCRIPTION_ID);
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().getFirst().getValue())
            .isEqualTo(new IdType(SNOMED_PREFERRED.getId()));
        assertThat(codeableConcept.getText())
            .isEqualTo(ORIGINAL_TEXT);
    }

    @Test
    public void mapSnomedCodeWithConceptIdDisplayNameDescriptionIsNotPreferredTerm() {
        when(snomedCTDao.getSnomedDescriptionUsingConceptIdAndDisplayName(any(), any())).thenReturn(SNOMED_DESCRIPTION);
        when(snomedCTDao.getSnomedDescriptionPreferredTermUsingConceptId(any())).thenReturn(SNOMED_PREFERRED);
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="22298006" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
            displayName="Myocardial infarction">
                <originalText>Test original text</originalText>
            </code>
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getCoding().getFirst().getCode())
            .isEqualTo("22298006");
        assertThat(codeableConcept.getCoding().getFirst().getDisplay())
            .isEqualTo(SNOMED_PREFERRED.getTerm());
        assertTrue(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().size() == 2);
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().getFirst().getUrl())
            .isEqualTo(DESCRIPTION_ID);
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().getFirst().getValue())
            .isEqualTo(new IdType(SNOMED_DESCRIPTION.getId()));
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().get(1).getUrl())
            .isEqualTo(DESCRIPTION_DISPLAY);
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().get(1).getValue()
            .toString()).isEqualTo(SNOMED_DESCRIPTION.getTerm());
        assertThat(codeableConcept.getText())
            .isEqualTo(ORIGINAL_TEXT);
    }

    @Test
    public void mapSnomedCodeWithConceptIdDisplayNameOriginalTextNoMatchedDescription() {
        when(snomedCTDao.getSnomedDescriptionUsingConceptIdAndDisplayName(any(), any())).thenReturn(null);
        when(snomedCTDao.getSnomedDescriptionPreferredTermUsingConceptId(any())).thenReturn(SNOMED_PREFERRED);
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="22298006" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
            displayName="Myocardial infarction">
                <originalText>Test original text</originalText>
            </code>
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getCoding().getFirst().getCode())
            .isEqualTo(CONCEPT_ID);
        assertThat(codeableConcept.getCoding().getFirst().getDisplay())
            .isEqualTo(DISPLAY_NAME_1);
        assertTrue(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().size() == 1);
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().getFirst().getUrl())
            .isEqualTo(DESCRIPTION_DISPLAY);
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().getFirst().getValue().toString())
            .isEqualTo((SNOMED_PREFERRED.getTerm()));
        assertThat(codeableConcept.getText())
            .isEqualTo(ORIGINAL_TEXT);
    }

    @Test
    public void mapSnomedCodeWithConceptIdDisplayNameNoOriginalTextNoMatchedDescription() {
        when(snomedCTDao.getSnomedDescriptionUsingConceptIdAndDisplayName(any(), any())).thenReturn(null);
        when(snomedCTDao.getSnomedDescriptionPreferredTermUsingConceptId(any())).thenReturn(SNOMED_PREFERRED);
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="22298006" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
            displayName="Myocardial infarction" />
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getCoding().getFirst().getCode())
            .isEqualTo(CONCEPT_ID);
        assertThat(codeableConcept.getCoding().getFirst().getDisplay())
            .isEqualTo(DISPLAY_NAME_1);
        assertTrue(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().size() == 1);
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().getFirst().getUrl())
            .isEqualTo(DESCRIPTION_DISPLAY);
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().getFirst().getValue().toString())
            .isEqualTo((SNOMED_PREFERRED.getTerm()));
        assertThat(codeableConcept.getText())
            .isEqualTo(DISPLAY_NAME_1);
    }

    @Test
    public void mapSnomedCodeWithConceptIdNoDisplayName() {
        when(snomedCTDao.getSnomedDescriptionUsingConceptId(any())).thenReturn(SNOMED_DESCRIPTION);
        when(snomedCTDao.getSnomedDescriptionPreferredTermUsingConceptId(any())).thenReturn(SNOMED_PREFERRED);
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="22298006" codeSystem="2.16.840.1.113883.2.1.3.2.4.15">
                <originalText>Test original text</originalText>
            </code>
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getCoding().getFirst().getCode())
            .isEqualTo(CONCEPT_ID);
        assertThat(codeableConcept.getCoding().getFirst().getDisplay())
            .isEqualTo(DISPLAY_NAME_1);
        assertTrue(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().size() == 1);
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().getFirst().getUrl())
            .isEqualTo(DESCRIPTION_ID);
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().getFirst().getValue())
            .isEqualTo(new IdType(SNOMED_DESCRIPTION.getId()));
    }

    @Test
    public void mapSnomedCodeWithConceptIdDisplayNameInTranslationMainCode() {
        when(snomedCTDao.getSnomedDescriptionPreferredTermUsingConceptId(any())).thenReturn(SNOMED_PREFERRED);
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="24591000000103" codeSystem="2.16.840.1.113883.2.1.3.2.4.14"
            displayName="Heart attack">
                <translation code="22298006" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"/>
                <originalText>Test original text</originalText>
            </code>
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getCoding().get(1).getCode())
            .isEqualTo(CONCEPT_ID);
        assertThat(codeableConcept.getCoding().get(1).getDisplay())
            .isEqualTo(DISPLAY_NAME_1);
        assertTrue(codeableConcept.getCoding().get(1).getExtension().getFirst().getExtension().size() == 1);
        assertThat(codeableConcept.getCoding().get(1).getExtension().getFirst().getExtension().getFirst().getUrl())
            .isEqualTo(DESCRIPTION_DISPLAY);
        assertThat(codeableConcept.getCoding().get(1).getExtension().getFirst().getExtension().getFirst().getValue().toString())
            .isEqualTo((SNOMED_DESCRIPTION.getTerm()));
        assertThat(codeableConcept.getText())
            .isEqualTo(ORIGINAL_TEXT);
    }

    @Test
    public void mapSnomedCodeWithConceptIdDisplayNameInTranslationMainCodeNoOriginalText() {
        when(snomedCTDao.getSnomedDescriptionPreferredTermUsingConceptId(any())).thenReturn(SNOMED_PREFERRED);
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="24591000000103" codeSystem="2.16.840.1.113883.2.1.3.2.4.14"
            displayName="Heart attack">
                <translation code="22298006" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"/>
            </code>
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getCoding().get(1).getCode())
            .isEqualTo(CONCEPT_ID);
        assertThat(codeableConcept.getCoding().get(1).getDisplay())
            .isEqualTo(DISPLAY_NAME_1);
        assertTrue(codeableConcept.getCoding().get(1).getExtension().getFirst().getExtension().size() == 1);
        assertThat(codeableConcept.getCoding().get(1).getExtension().getFirst().getExtension().getFirst().getUrl())
            .isEqualTo(DESCRIPTION_DISPLAY);
        assertThat(codeableConcept.getCoding().get(1).getExtension().getFirst().getExtension().getFirst().getValue().toString())
            .isEqualTo((SNOMED_DESCRIPTION.getTerm()));
        assertThat(codeableConcept.getText())
            .isEqualTo(DISPLAY_NAME_2);
    }

    @Test
    public void mapSnomedCodeWithConceptIdNoMatchingDescriptionOriginalText() {
        when(snomedCTDao.getSnomedDescriptionUsingConceptIdAndDisplayName(any(), any())).thenReturn(null);
        when(snomedCTDao.getSnomedDescriptionPreferredTermUsingConceptId(any())).thenReturn(null);
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="22298006" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
            displayName="Myocardial infarction">
                <originalText>Test original text</originalText>
            </code>
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getCoding().getFirst().getCode())
            .isEqualTo(CONCEPT_ID);
        assertThat(codeableConcept.getCoding().getFirst().getDisplay())
            .isEqualTo(DISPLAY_NAME_1);
        assertThat(codeableConcept.getCoding().getFirst().getExtension())
            .isNullOrEmpty();
        assertThat(codeableConcept.getText())
            .isEqualTo(ORIGINAL_TEXT);
    }

    @Test
    public void mapSnomedCodeWithConceptIdNoMatchingDescriptionNoOriginalText() {
        when(snomedCTDao.getSnomedDescriptionUsingConceptIdAndDisplayName(any(), any())).thenReturn(null);
        when(snomedCTDao.getSnomedDescriptionPreferredTermUsingConceptId(any())).thenReturn(null);
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="22298006" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
            displayName="Myocardial infarction" />
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getCoding().getFirst().getCode())
            .isEqualTo(CONCEPT_ID);
        assertThat(codeableConcept.getCoding().getFirst().getDisplay())
            .isEqualTo(DISPLAY_NAME_1);
        assertThat(codeableConcept.getCoding().getFirst().getExtension())
            .isNullOrEmpty();
        assertNull(codeableConcept.getText());
    }

    @Test
    public void mapSnomedCodeWithConceptIdNoMatchingDescriptionNoDisplayInTranslationNoOriginalText() {
        when(snomedCTDao.getSnomedDescriptionPreferredTermUsingConceptId(any())).thenReturn(null);
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="24591000000103" codeSystem="2.16.840.1.113883.2.1.3.2.4.14"
            displayName="Heart attack">
                <translation code="22298006" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"/>
            </code>
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getCoding().get(1).getCode())
            .isEqualTo(CONCEPT_ID);
        assertThat(codeableConcept.getCoding().get(1).getDisplay())
            .isEqualTo(DISPLAY_NAME_2);
        assertThat(codeableConcept.getCoding().get(1).getExtension())
            .isNullOrEmpty();
    }

    @Test
    public void mapSnomedCodeWithDescriptionIdDescriptionIsPreferredTerm() {
        when(snomedCTDao.getSnomedDescriptionUsingDescriptionId(any())).thenReturn(SNOMED_PREFERRED);
        when(snomedCTDao.getSnomedDescriptionPreferredTermUsingConceptId(any())).thenReturn(SNOMED_PREFERRED);
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="37436014" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
            displayName="Myocardial infarction">
                <originalText>Test original text</originalText>
            </code>
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getCoding().getFirst().getCode())
            .isEqualTo(SNOMED_PREFERRED.getConceptid());
        assertThat(codeableConcept.getCoding().getFirst().getDisplay())
            .isEqualTo(SNOMED_PREFERRED.getTerm());
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().size() == 1)
            .isTrue();
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().getFirst().getUrl())
            .isEqualTo(DESCRIPTION_ID);
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().getFirst().getValue())
            .isEqualTo(new IdType(SNOMED_PREFERRED.getId()));
        assertThat(codeableConcept.getText())
            .isEqualTo(ORIGINAL_TEXT);
    }

    @Test
    public void mapSnomedCodeWithDescriptionIdPreferredTermDisplayMatchesTermNoOriginalText() {
        when(snomedCTDao.getSnomedDescriptionUsingDescriptionId(any())).thenReturn(SNOMED_PREFERRED);
        when(snomedCTDao.getSnomedDescriptionPreferredTermUsingConceptId(any())).thenReturn(SNOMED_PREFERRED);
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="37436014" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
            displayName="Myocardial infarction" />
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getCoding().getFirst().getCode())
            .isEqualTo(SNOMED_PREFERRED.getConceptid());
        assertThat(codeableConcept.getCoding().getFirst().getDisplay())
            .isEqualTo(SNOMED_PREFERRED.getTerm());
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().size() == 1)
            .isTrue();
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().getFirst().getUrl())
            .isEqualTo(DESCRIPTION_ID);
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().getFirst().getValue())
            .isEqualTo(new IdType(SNOMED_PREFERRED.getId()));
        assertThat(codeableConcept.getText()).isNull();
    }

    @Test
    public void mapSnomedCodeWithDescriptionIdPreferredTermDisplayDoesNotMatchTermNoOriginalText() {

        when(snomedCTDao.getSnomedDescriptionUsingDescriptionId(any())).thenReturn(SNOMED_PREFERRED);
        when(snomedCTDao.getSnomedDescriptionPreferredTermUsingConceptId(any())).thenReturn(SNOMED_PREFERRED);
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="37436014" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
            displayName="Different display name" />
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertEquals(SNOMED_PREFERRED.getConceptid(), codeableConcept.getCoding().getFirst().getCode());
        assertEquals(SNOMED_PREFERRED.getTerm(), codeableConcept.getCoding().getFirst().getDisplay());
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().size() == 1)
            .isTrue();
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().getFirst().getUrl())
            .isEqualTo(DESCRIPTION_ID);
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().getFirst().getValue())
            .isEqualTo(new IdType(SNOMED_PREFERRED.getId()));
        assertThat(codeableConcept.getText())
            .isEqualTo(DISPLAY_NAME_3);
    }

    @Test
    public void mapSnomedCodeWithDescriptionIdDescriptionIsNotPreferredTerm() {
        when(snomedCTDao.getSnomedDescriptionUsingDescriptionId(any())).thenReturn(SNOMED_DESCRIPTION);
        when(snomedCTDao.getSnomedDescriptionPreferredTermUsingConceptId(any())).thenReturn(SNOMED_PREFERRED);
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="37436014" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
            displayName="Myocardial infarction">
                <originalText>Test original text</originalText>
            </code>
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getCoding().getFirst().getCode())
            .isEqualTo(SNOMED_DESCRIPTION.getConceptid());
        assertThat(codeableConcept.getCoding().getFirst().getDisplay())
            .isEqualTo(SNOMED_PREFERRED.getTerm());
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().size() == 2)
            .isTrue();
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().getFirst().getUrl())
            .isEqualTo(DESCRIPTION_ID);
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().getFirst().getValue())
            .isEqualTo(new IdType(SNOMED_DESCRIPTION.getId()));
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().get(1).getUrl())
            .isEqualTo(DESCRIPTION_DISPLAY);
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().get(1).getValue().toString())
            .isEqualTo(SNOMED_DESCRIPTION.getTerm());
        assertThat(codeableConcept.getText())
            .isEqualTo(ORIGINAL_TEXT);
    }

    @Test
    public void mapSnomedCodeWithDescriptionIdNotPreferredTermDisplayMatchesTermNoOriginalText() {
        when(snomedCTDao.getSnomedDescriptionUsingDescriptionId(any())).thenReturn(SNOMED_DESCRIPTION);
        when(snomedCTDao.getSnomedDescriptionPreferredTermUsingConceptId(any())).thenReturn(SNOMED_PREFERRED);
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="37436014" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
            displayName="Myocardial infarction" />
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getCoding().getFirst().getCode())
            .isEqualTo(SNOMED_DESCRIPTION.getConceptid());
        assertThat(codeableConcept.getCoding().getFirst().getDisplay())
            .isEqualTo(SNOMED_PREFERRED.getTerm());
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().size() == 2)
            .isTrue();
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().getFirst().getUrl())
            .isEqualTo(DESCRIPTION_ID);
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().getFirst().getValue())
            .isEqualTo(new IdType(SNOMED_DESCRIPTION.getId()));
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().get(1).getUrl())
            .isEqualTo(DESCRIPTION_DISPLAY);
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().get(1).getValue().toString())
            .isEqualTo(SNOMED_DESCRIPTION.getTerm());
        assertThat(codeableConcept.getText()).isNull();
    }

    @Test
    public void mapSnomedCodeWithDescriptionIdNotPreferredTermDisplayDoesNotMatchTermNoOriginalText() {
        when(snomedCTDao.getSnomedDescriptionUsingDescriptionId(any())).thenReturn(SNOMED_DESCRIPTION);
        when(snomedCTDao.getSnomedDescriptionPreferredTermUsingConceptId(any())).thenReturn(SNOMED_PREFERRED);
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="37436014" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
            displayName="Different display name" />
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getCoding().getFirst().getCode())
            .isEqualTo(SNOMED_DESCRIPTION.getConceptid());
        assertThat(codeableConcept.getCoding().getFirst().getDisplay())
            .isEqualTo(SNOMED_PREFERRED.getTerm());
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().size() == 2)
            .isTrue();
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().getFirst().getUrl())
            .isEqualTo(DESCRIPTION_ID);
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().getFirst().getValue())
            .isEqualTo(new IdType(SNOMED_DESCRIPTION.getId()));
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().get(1).getUrl())
            .isEqualTo(DESCRIPTION_DISPLAY);
        assertThat(codeableConcept.getCoding().getFirst().getExtension().getFirst().getExtension().get(1).getValue().toString())
            .isEqualTo(SNOMED_DESCRIPTION.getTerm());
        assertThat(codeableConcept.getText())
            .isEqualTo(DISPLAY_NAME_3);
    }

    @Test
    public void mapSnomedCodeWithDescriptionIdNoMatchingDescriptionWithDisplayNameNoOriginalText() {
        when(snomedCTDao.getSnomedDescriptionUsingDescriptionId(any())).thenReturn(null);
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="37436014" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
            displayName="Myocardial infarction" />
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getText())
            .isEqualTo(DISPLAY_NAME_1);
        assertThat(codeableConcept.getCoding())
            .isNullOrEmpty();
    }

    @Test
    public void mapSnomedCodeWithTranslationDescriptionIdNoMatchingDescriptionWithMainCodeDisplayNameNoOriginalText() {
        when(snomedCTDao.getSnomedDescriptionUsingDescriptionId(any())).thenReturn(null);
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="24591000000103" codeSystem="2.16.840.1.113883.2.1.3.2.4.14"
            displayName="Heart attack">
                <translation code="37436014" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"/>
            </code>
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getText())
            .isEqualTo(DISPLAY_NAME_2);
        assertThat(codeableConcept.getCoding())
            .hasSize(1);
    }

    @Test
    public void mapSnomedCodeWithDescriptionIdNoMatchingDescriptionWithOriginalTextNoDisplayName() {
        when(snomedCTDao.getSnomedDescriptionUsingDescriptionId(any())).thenReturn(null);
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="37436014" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
            displayName="Myocardial infarction">
                <originalText>Test original text</originalText>
            </code>
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getText())
            .isEqualTo(ORIGINAL_TEXT);
        assertThat(codeableConcept.getCoding())
            .isNullOrEmpty();
    }

    @Test
    public void mapSnomedCodeForMedicationResourceDescriptionIdExtensionIgnored() {
        when(snomedCTDao.getSnomedDescriptionUsingConceptIdAndDisplayName(any(), any())).thenReturn(SNOMED_PREFERRED);
        when(snomedCTDao.getSnomedDescriptionPreferredTermUsingConceptId(any())).thenReturn(SNOMED_PREFERRED);
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="22298006" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
            displayName="Myocardial infarction">
                <originalText>Test original text</originalText>
            </code>
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConceptForMedication(codedData);

        assertThat(codeableConcept.getCoding().getFirst().getCode())
            .isEqualTo(CONCEPT_ID);
        assertThat(codeableConcept.getCoding().getFirst().getDisplay())
            .isEqualTo(SNOMED_PREFERRED.getTerm());
        assertThat(codeableConcept.getCoding().getFirst().getExtension())
            .isNullOrEmpty();
        assertThat(codeableConcept.getText())
            .isEqualTo(ORIGINAL_TEXT);
    }

    @Test
    public void mapSnomedCodeForMedicationResourceDescriptionIdAndDisplayExtensionsIgnored() {
        when(snomedCTDao.getSnomedDescriptionUsingConceptIdAndDisplayName(any(), any())).thenReturn(SNOMED_DESCRIPTION);
        when(snomedCTDao.getSnomedDescriptionPreferredTermUsingConceptId(any())).thenReturn(SNOMED_PREFERRED);
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="22298006" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
             displayName="Myocardial infarction">
                <originalText>Test original text</originalText>
            </code>
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);

        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConceptForMedication(codedData);

        assertThat(codeableConcept.getCoding().getFirst().getCode())
            .isEqualTo("22298006");
        assertThat(codeableConcept.getCoding().getFirst().getDisplay())
            .isEqualTo(SNOMED_PREFERRED.getTerm());
        assertThat(codeableConcept.getCoding().getFirst().getExtension())
            .isNullOrEmpty();
        assertThat(codeableConcept.getText())
            .isEqualTo(ORIGINAL_TEXT);
    }

    @Test
    public void mapSnomedCodeForMedicationResourceUsingDescriptionId() {
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="37436014" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
            displayName="Myocardial infarction" />
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConceptForMedication(codedData);

        assertThat(codeableConcept.getCoding())
            .isNullOrEmpty();
        assertThat(codeableConcept.getText())
            .isEqualTo(DISPLAY_NAME_1);
    }

    @Test
    public void mapNoSnomedCodeWithOriginalText() {
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="24591000000103" codeSystem="1234"
            displayName="O/E - blood pressure reading">
                <translation code="163020007" codeSystem="12345" displayName="O/E - blood pressure reading"/>
                <translation code="24591000000106" codeSystem="123456" displayName="O/E - blood pressure reading"/>
                <originalText>Inactive Problem, minor</originalText>
            </code>
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getText())
            .isEqualTo("Inactive Problem, minor");
        assertThat(codeableConcept.getCoding())
            .hasSize(EXPECTED_CODING_SIZE);
    }

    @Test
    public void mapNoSnomedCodeWithoutOriginalText() {
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="24591000000103" codeSystem="1234"
            displayName="O/E - blood pressure reading">
                <translation code="163020007" codeSystem="12345" displayName="O/E - blood pressure reading"/>
                <translation code="24591000000106" codeSystem="123456" displayName="O/E - blood pressure reading"/>
            </code>
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getText())
            .isEqualTo("O/E - blood pressure reading");
        assertThat(codeableConcept.getCoding())
            .hasSize(EXPECTED_CODING_SIZE);
    }

    @Test
    public void mapSnomedCodeInMainWithOriginalText() {
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="24591000000103" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
            displayName="O/E - blood pressure reading">
                <translation code="163020007" codeSystem="2.16.840.1.113883.2.1.3.2.4.14"
                displayName="blood pressure reading"/>
                <translation code="24591000000104" codeSystem="2.16.840.1.113883.2.1.6.2"
                displayName="O/E - blood pressure"/>
                <originalText>Inactive Problem, minor</originalText>
            </code>
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getText())
            .isEqualTo("Inactive Problem, minor");
        assertThat(codeableConcept.getCodingFirstRep().getCode())
            .isEqualTo("24591000000103");
        assertThat(codeableConcept.getCodingFirstRep().getSystem())
            .isEqualTo("http://snomed.info/sct");
        assertThat(codeableConcept.getCodingFirstRep().getDisplay())
            .isEqualTo("O/E - blood pressure reading");
    }

    @Test
    public void mapSnomedCodeInMainWithoutOriginalText() {
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="24591000000103" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
            displayName="O/E - blood pressure reading">
                <translation code="163020007" codeSystem="2.16.840.1.113883.2.1.3.2.4.14"
                displayName="blood pressure reading"/>
                <translation code="24591000000104" codeSystem="2.16.840.1.113883.2.1.6.2"
                displayName="O/E - blood pressure"/>
            </code>
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.hasText())
            .isFalse();
        assertThat(codeableConcept.getCodingFirstRep().getCode())
            .isEqualTo("24591000000103");
        assertThat(codeableConcept.getCodingFirstRep().getSystem())
            .isEqualTo("http://snomed.info/sct");
        assertThat(codeableConcept.getCodingFirstRep().getDisplay())
            .isEqualTo("O/E - blood pressure reading");
    }

    @Test
    public void mapSnomedCodeInTranslationWithoutDisplayName() {
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="24591000000103" codeSystem="2.16.840.1.113883.2.1.3.2.4.14"
            displayName="O/E - blood pressure reading">
                <translation code="163020007" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"/>
                <translation code="24591000000103" codeSystem="2.16.840.1.113883.2.1.6.2"
                displayName="O/E - blood pressure"/>
            </code>
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.hasText())
            .isFalse();
        assertThat(codeableConcept.getCoding().get(1).getCode())
            .isEqualTo("163020007");
        assertThat(codeableConcept.getCoding().get(1).getSystem())
            .isEqualTo("http://snomed.info/sct");
        assertThat(codeableConcept.getCoding().get(1).getDisplay())
            .isEqualTo("O/E - blood pressure reading");
    }

    @Test
    public void mapSnomedCodeInTranslationWithDisplayName() {
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="24591000000103" codeSystem="2.16.840.1.113883.2.1.3.2.4.14"
            displayName="O/E - blood pressure reading">
                <translation code="163020007" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
                displayName="blood pressure reading"/>
                <translation code="24591000000109" codeSystem="2.16.840.1.113883.2.1.6.2"
                displayName="O/E - blood pressure"/>
            </code>
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.hasText())
            .isFalse();
        assertThat(codeableConcept.getCoding().get(1).getCode())
            .isEqualTo("163020007");
        assertThat(codeableConcept.getCoding().get(1).getSystem())
            .isEqualTo("http://snomed.info/sct");
        assertThat(codeableConcept.getCoding().get(1).getDisplay())
            .isEqualTo("blood pressure reading");
    }

    @Test
    public void mapSnomedCodeInTranslationWithDisplayNameWithOriginalText() {
        var inputXML = """
            <code xmlns="urn:hl7-org:v3" code="24591000000103" codeSystem="2.16.840.1.113883.2.1.3.2.4.14"
            displayName="O/E - blood pressure reading">
                <translation code="163020007" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
                displayName="blood pressure reading"/>
                <translation code="24591000000109" codeSystem="2.16.840.1.113883.2.1.6.2"
                displayName="O/E - blood pressure"/>
                <originalText>Inactive Problem, minor</originalText>
            </code>
            """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        CodeableConcept codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getText())
            .isEqualTo("Inactive Problem, minor");
        assertThat(codeableConcept.getCoding().get(1).getCode())
            .isEqualTo("163020007");
        assertThat(codeableConcept.getCoding().get(1).getSystem())
            .isEqualTo("http://snomed.info/sct");
        assertThat(codeableConcept.getCoding().get(1).getDisplay())
            .isEqualTo("blood pressure reading");
    }

    @Test
    public void mapKnownNonSnomedCodeInCodeWithOriginalText() {
        var inputXML = """
                <code xmlns="urn:hl7-org:v3" code="ALLERGY138185NEMIS" codeSystem="2.16.840.1.113883.2.1.6.3"
                displayName="Adverse reaction to Comirnaty Covid-19 mRna Vaccine">
                    <originalText>Test original text</originalText>
                </code>
                """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        var codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getText())
            .isEqualTo("Test original text");
        assertThat(codeableConcept.getCodingFirstRep().getCode())
            .isEqualTo("ALLERGY138185NEMIS");
        assertThat(codeableConcept.getCodingFirstRep().getSystem())
            .isEqualTo("https://fhir.hl7.org.uk/Id/egton-codes");
        assertThat(codeableConcept.getCodingFirstRep().getDisplay())
            .isEqualTo("Adverse reaction to Comirnaty Covid-19 mRna Vaccine");
    }
    @Test
    public void mapKnownNonSnomedCodeInCodeWithoutOriginalText() {
        var inputXML = """
                <code xmlns="urn:hl7-org:v3" code="ALLERGY138185NEMIS" codeSystem="2.16.840.1.113883.2.1.6.3"
                displayName="Adverse reaction to Comirnaty Covid-19 mRna Vaccine" />
                """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        var codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getText())
            .isEqualTo("Adverse reaction to Comirnaty Covid-19 mRna Vaccine");
        assertThat(codeableConcept.getCodingFirstRep().getCode())
            .isEqualTo("ALLERGY138185NEMIS");
        assertThat(codeableConcept.getCodingFirstRep().getSystem())
            .isEqualTo("https://fhir.hl7.org.uk/Id/egton-codes");
        assertThat(codeableConcept.getCodingFirstRep().getDisplay())
            .isEqualTo("Adverse reaction to Comirnaty Covid-19 mRna Vaccine");
    }

    @Test
    public void mapKnownNonSnomedCodeInValue() {
        var inputXML = """
                <value xmlns="urn:hl7-org:v3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="CD"
                code="ALLERGY-SNOMED-13482891000006110" codeSystem="2.16.840.1.113883.2.1.6.3"
                displayName="Adverse reaction to Comirnaty Covid-19 mRna Vaccine" />
                """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        var codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getText())
            .isEqualTo("Adverse reaction to Comirnaty Covid-19 mRna Vaccine");
        assertThat(codeableConcept.getCodingFirstRep().getCode())
            .isEqualTo("ALLERGY-SNOMED-13482891000006110");
        assertThat(codeableConcept.getCodingFirstRep().getSystem())
            .isEqualTo("https://fhir.hl7.org.uk/Id/egton-codes");
        assertThat(codeableConcept.getCodingFirstRep().getDisplay())
            .isEqualTo("Adverse reaction to Comirnaty Covid-19 mRna Vaccine");
    }

    @Test
    public void mapKnownNonSnomedCodeInTranslationWithSnomedCodeWithOriginalText() {
        var inputXML = """
                <code xmlns="urn:hl7-org:v3" code="24591000000103" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
                displayName="O/E - blood pressure reading">
                    <translation code="ALLERGY138185NEMIS" codeSystem="2.16.840.1.113883.2.1.6.3"
                    displayName="Adverse reaction to Comirnaty Covid-19 mRna Vaccine"/>
                    <originalText>Test original text</originalText>
                </code>
                """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        var codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getText())
            .isEqualTo("Test original text");
        assertThat(codeableConcept.getCodingFirstRep().getCode())
            .isEqualTo("24591000000103");
        assertThat(codeableConcept.getCodingFirstRep().getSystem())
            .isEqualTo("http://snomed.info/sct");
        assertThat(codeableConcept.getCodingFirstRep().getDisplay())
            .isEqualTo("O/E - blood pressure reading");
        assertThat(codeableConcept.getCoding().get(1).getCode())
            .isEqualTo("ALLERGY138185NEMIS");
        assertThat(codeableConcept.getCoding().get(1).getSystem())
            .isEqualTo("https://fhir.hl7.org.uk/Id/egton-codes");
        assertThat(codeableConcept.getCoding().get(1).getDisplay())
            .isEqualTo("Adverse reaction to Comirnaty Covid-19 mRna Vaccine");
    }

    @Test
    public void mapKnownNonSnomedCodeInTranslationWithSnomedCodeWithoutOriginalText() {
        var inputXML = """
                <code xmlns="urn:hl7-org:v3" code="24591000000103" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
                displayName="O/E - blood pressure reading">
                    <translation code="ALLERGY138185NEMIS" codeSystem="2.16.840.1.113883.2.1.6.3"
                    displayName="Adverse reaction to Comirnaty Covid-19 mRna Vaccine"/>
                </code>
                """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        var codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getText())
            .isNull();
        assertThat(codeableConcept.getCodingFirstRep().getCode())
            .isEqualTo("24591000000103");
        assertThat(codeableConcept.getCodingFirstRep().getSystem())
            .isEqualTo("http://snomed.info/sct");
        assertThat(codeableConcept.getCodingFirstRep().getDisplay())
            .isEqualTo("O/E - blood pressure reading");
        assertThat(codeableConcept.getCoding().get(1).getCode())
            .isEqualTo("ALLERGY138185NEMIS");
        assertThat(codeableConcept.getCoding().get(1).getSystem())
            .isEqualTo("https://fhir.hl7.org.uk/Id/egton-codes");
        assertThat(codeableConcept.getCoding().get(1).getDisplay())
            .isEqualTo("Adverse reaction to Comirnaty Covid-19 mRna Vaccine");
    }

    @Test
    public void mapKnownNonSnomedCodeInMainCodeAndTranslationWithOriginalText() {
        var inputXML = """
                <code xmlns="urn:hl7-org:v3" code="ALLERGY-SNOMED-13482891000006110"
                codeSystem="2.16.840.1.113883.2.1.6.3"
                displayName="Adverse reaction to Comirnaty Covid-19 mRna Vaccine">
                    <translation code="22A..00" codeSystem="2.16.840.1.113883.2.1.6.2"
                    displayName="O/E - weight"/>
                    <originalText>Test original text</originalText>
                </code>
                """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        var codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getText())
            .isEqualTo("Test original text");
        assertThat(codeableConcept.getCodingFirstRep().getCode())
            .isEqualTo("ALLERGY-SNOMED-13482891000006110");
        assertThat(codeableConcept.getCodingFirstRep().getSystem())
            .isEqualTo("https://fhir.hl7.org.uk/Id/egton-codes");
        assertThat(codeableConcept.getCodingFirstRep().getDisplay())
            .isEqualTo("Adverse reaction to Comirnaty Covid-19 mRna Vaccine");
        assertThat(codeableConcept.getCoding().get(1).getCode())
            .isEqualTo("22A..00");
        assertThat(codeableConcept.getCoding().get(1).getSystem())
            .isEqualTo("http://read.info/readv2");
        assertThat(codeableConcept.getCoding().get(1).getDisplay())
            .isEqualTo("O/E - weight");
    }

    @Test
    public void mapKnownNonSnomedCodeInMainCodeAndTranslationWithoutOriginalText() {
        var inputXML = """
                <code xmlns="urn:hl7-org:v3" code="ALLERGY-SNOMED-13482891000006110"
                codeSystem="2.16.840.1.113883.2.1.6.3"
                displayName="Adverse reaction to Comirnaty Covid-19 mRna Vaccine">
                    <translation code="22A.." codeSystem="2.16.840.1.113883.2.1.3.2.4.14" displayName="O/E - weight"/>
                </code>
                """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        var codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getText())
            .isEqualTo("Adverse reaction to Comirnaty Covid-19 mRna Vaccine");
        assertThat(codeableConcept.getCodingFirstRep().getCode())
            .isEqualTo("ALLERGY-SNOMED-13482891000006110");
        assertThat(codeableConcept.getCodingFirstRep().getSystem())
            .isEqualTo("https://fhir.hl7.org.uk/Id/egton-codes");
        assertThat(codeableConcept.getCodingFirstRep().getDisplay())
            .isEqualTo("Adverse reaction to Comirnaty Covid-19 mRna Vaccine");
        assertThat(codeableConcept.getCoding().get(1).getCode())
            .isEqualTo("22A..");
        assertThat(codeableConcept.getCoding().get(1).getSystem())
            .isEqualTo("http://read.info/ctv3");
        assertThat(codeableConcept.getCoding().get(1).getDisplay())
            .isEqualTo("O/E - weight");
    }

    @Test
    public void mapUnknownNonSnomedCodeInMainCode() {
        var inputXML = """
                <code xmlns="urn:hl7-org:v3" code="TESTCODE" codeSystem="TESTCODE_SYSTEM"
                displayName="TEST_DISPLAY_NAME" />
                """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        var codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getText())
            .isEqualTo("TEST_DISPLAY_NAME");
        assertThat(codeableConcept.getCodingFirstRep().getCode())
            .isEqualTo("TESTCODE");
        assertThat(codeableConcept.getCodingFirstRep().getSystem())
            .isEqualTo("TESTCODE_SYSTEM");
        assertThat(codeableConcept.getCodingFirstRep().getDisplay())
            .isEqualTo("TEST_DISPLAY_NAME");
    }

    @Test
    public void mapUnknownNonSnomedCodeInTranslation() {
        var inputXML = """
                <code xmlns="urn:hl7-org:v3" code="24591000000103" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
                displayName="O/E - blood pressure reading">
                    <translation code="TESTCODE" codeSystem="TESTCODE_SYSTEM" displayName="TEST_DISPLAY_NAME"/>
                </code>
                """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        var codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertThat(codeableConcept.getText())
            .isNull();
        assertThat(codeableConcept.getCodingFirstRep().getCode())
            .isEqualTo("24591000000103");
        assertThat(codeableConcept.getCodingFirstRep().getSystem())
            .isEqualTo("http://snomed.info/sct");
        assertThat(codeableConcept.getCodingFirstRep().getDisplay())
            .isEqualTo("O/E - blood pressure reading");
        assertThat(codeableConcept.getCoding().get(1).getCode())
            .isEqualTo("TESTCODE");
        assertThat(codeableConcept.getCoding().get(1).getSystem())
            .isEqualTo("TESTCODE_SYSTEM");
        assertThat(codeableConcept.getCoding().get(1).getDisplay())
            .isEqualTo("TEST_DISPLAY_NAME");
    }

    /**
     * This has been added as we have had a change in requirement to preserve the original ordering
     * of the codes in the code block.
     * See NIAD-2902 for details
     */
    @Test
    public void When_MappingCodeableConcept_Expect_CodesToBeInTheProvidedOrder() {
        var inputXML = """
                <code xmlns="urn:hl7-org:v3" code="22K.." codeSystem="2.16.840.1.113883.2.1.3.2.4.14"
                displayName="Body mass index - observation">
                <originalText>Body mass index</originalText>
                <translation code="60621009" codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
                    displayName="Body mass index"/>
                    <translation code="22K..00" codeSystem="2.16.840.1.113883.2.1.6.2" displayName="Body Mass Index"/>
                </code>
                """;

        var codedData = unmarshallCodeElementFromXMLString(XML_HEADER + inputXML);
        var codeableConcept = codeableConceptMapper.mapToCodeableConcept(codedData);

        assertAll(
                () -> assertThat(codeableConcept.getCoding().getFirst().getCode()).isEqualTo("22K.."),
                () -> assertThat(codeableConcept.getCoding().get(1).getCode()).isEqualTo("60621009"),
                () -> assertThat(codeableConcept.getCoding().get(2).getCode()).isEqualTo("22K..00")
        );

    }

    @SneakyThrows
    private CD unmarshallCodeElementFromXMLString(String xmlString) {
        return unmarshallString(xmlString, CD.class);
    }
}
