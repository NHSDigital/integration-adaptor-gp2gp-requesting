package uk.nhs.adaptors.pss.translator.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.util.ResourceUtils.getFile;

import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import java.util.List;

import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.ListResource;
import org.hl7.fhir.dstu3.model.ListResource.ListMode;
import org.hl7.fhir.dstu3.model.ListResource.ListStatus;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.v3.CD;
import org.hl7.v3.CsNullFlavor;
import org.hl7.v3.II;
import org.hl7.v3.RCMRMT030101UKCompoundStatement;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.TS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.SneakyThrows;
import uk.nhs.adaptors.pss.translator.service.IdGeneratorService;
import uk.nhs.adaptors.pss.translator.util.DateFormatUtil;
import static uk.nhs.adaptors.common.util.CodeableConceptUtils.createCodeableConcept;

@ExtendWith(MockitoExtension.class)
public class ConsultationListMapperTest {

    private static final String XML_RESOURCES_BASE = "xml/ConsultationList/";
    private static final String ENCOUNTER_ID = "823ACEB0-90C2-11EC-B1E5-0800200C9A66";
    private static final String CONSULTATION_ID_SUFFIX = "-CONS";
    private static final String LIST_META_PROFILE = "https://fhir.nhs.uk/STU3/StructureDefinition/CareConnect-GPC-List-1";
    private static final String LIST_ORDERED_BY_SYSTEM = "http://hl7.org/fhir/list-order";
    private static final String LIST_ORDERED_BY_CODE = "system";
    private static final String LIST_ORDERED_BY_DISPLAY = "Sorted by System";
    private static final String LIST_CODE_SYSTEM = "http://snomed.info/sct";
    private static final String CONSULTATION_CODE_CODE = "325851000000107";
    private static final String CONSULTATION_CODE_DISPLAY = "Consultation";
    private static final String TOPIC_CODE_CODE = "25851000000105";
    private static final String TOPIC_CODE_DISPLAY = "Topic (EHR)";
    private static final String CATEGORY_CODE_CODE = "24781000000107";
    private static final String CATEGORY_CODE_DISPLAY = "Category (EHR)";
    private static final String PATIENT_ID = "0E6F45F0-8D7B-11EC-B1E5-0800200C9A66";
    private static final String FLAT_TOPIC_ID = "AEE5F640-90A6-11EC-B1E5-0800200C9A66";
    private static final String COMPOUND_STATEMENT_ID = "68E66550-90DB-11EC-B1E5-0800200C9A66";
    private static final String FULL_VALID_CONSULTATION_LIST_XML = "full_valid_consultation_list.xml";
    private static final String FULL_VALID_CONSULTATION_LIST_NOAUTHOR_TIME_XML = "full_valid_consultation_list_no_author_time.xml";
    private static final String FULL_VALID_CONSULTATION_NOAUTHOR_LIST_XML = "full_valid_consultation_noAuthor_list.xml";

    private static final String FULL_VALID_CONSULTATION_NOAUTHOR_NOAVAILABILITY_EFFECTLOW_LIST_XML =
                                    "full_valid_consultation_noAuthor_noavailability_effectLow_list.xml";
    private static final String FULL_VALID_CONSULTATION_NOAUTHORTIME_NOAVAILABILITY_EFFECTLOW_LIST_XML =
            "full_valid_consultation_list_no_authortime_no_availability.xml";

    private static final String FULL_VALID_CONSULTATION_NOAUTHOR_NOAVAILABILITY_LIST_XML =
                                    "full_valid_consultation_noAuthor_noavailability_list.xml";
    private static final String FULL_VALID_CONSULTATION_NOAUTHOR_NOAVAILABILITY_EFFECTCENTER_LIST_XML =
            "full_valid_consultation_noAuthor_noavailability_effectCenter_list.xml";


    @Mock
    private IdGeneratorService idGenerator;

    @Mock
    private CodeableConceptMapper codeableConceptMapper;

    @InjectMocks
    private ConsultationListMapper listMapper;

    private Encounter encounter;

    @BeforeEach
    public void setup() {
        encounter = new Encounter();
    }

    @Test
    public void testValidFullDataConsultationList() {
        var ehrExtract = unmarshallEhrExtractElement(FULL_VALID_CONSULTATION_LIST_XML);
        setUpEncounter("20100113152000", "20150213152000", "test-display", "test-text");

        var comp = ehrExtract.getComponent().getFirst().getEhrFolder().
                getComponent().getFirst().getEhrComposition();
        var consultation = listMapper.mapToConsultation(comp, encounter);

        assertConsultation(consultation, "20100113151332", "test-text");
    }

    @Test
    public void testValidFullDataConsultationNoAuthorNoAvailTimeListEffectCenter() {
        var ehrExtract = unmarshallEhrExtractElement(
                FULL_VALID_CONSULTATION_NOAUTHOR_NOAVAILABILITY_EFFECTCENTER_LIST_XML);
        setUpEncounter("20100113152000", "20150213152000", "test-display", "test-text");

        var comp = ehrExtract.getComponent().getFirst().getEhrFolder().
                getComponent().getFirst().getEhrComposition();
        var consultation = listMapper.mapToConsultation(comp, encounter);

        assertConsultation(consultation, "20100113155000", "test-text");
    }

    @Test
    public void testValidFullDataConsultationNoAuthorTimeNoAvailTimeListEffectCenter() {
        var ehrExtract = unmarshallEhrExtractElement(
                FULL_VALID_CONSULTATION_NOAUTHOR_NOAVAILABILITY_EFFECTCENTER_LIST_XML);
        setUpEncounter("20100113152000", "20150213152000", "test-display", "test-text");

        var comp = ehrExtract.getComponent().getFirst().getEhrFolder().
                getComponent().getFirst().getEhrComposition();
        var consultation = listMapper.mapToConsultation(comp, encounter);

        assertConsultation(consultation, "20100113155000", "test-text");
    }

    @Test
    public void testValidFullDataConsultationNoAuthorNoAvailTimeListEffectLow() {
        var ehrExtract = unmarshallEhrExtractElement(
                FULL_VALID_CONSULTATION_NOAUTHOR_NOAVAILABILITY_EFFECTLOW_LIST_XML);
        setUpEncounter("20100113152000", "20150213152000", "test-display", "test-text");

        var comp = ehrExtract.getComponent().getFirst().getEhrFolder().
                getComponent().getFirst().getEhrComposition();
        var consultation = listMapper.mapToConsultation(comp, encounter);

        assertConsultation(consultation, "20100113152000", "test-text");
    }
    @Test
    public void testValidFullDataConsultationNoAuthorNoAvailTimeList() {
        var ehrExtract = unmarshallEhrExtractElement(
                FULL_VALID_CONSULTATION_NOAUTHOR_NOAVAILABILITY_LIST_XML);
        setUpEncounter("20100113152000", "20150213152000", "test-display", "test-text");

        var comp = ehrExtract.getComponent().getFirst().getEhrFolder().
                getComponent().getFirst().getEhrComposition();
        var consultation = listMapper.mapToConsultation(comp, encounter);

        assertConsultation(consultation, "20100113153000", "test-text");
    }

    @Test
    public void testValidFullDataConsultationNoAuthorTimeNoAvailTimeList() {
        var ehrExtract = unmarshallEhrExtractElement(
                FULL_VALID_CONSULTATION_NOAUTHORTIME_NOAVAILABILITY_EFFECTLOW_LIST_XML);
        setUpEncounter("20100113152000", "20150213152000", "test-display", "test-text");

        var comp = ehrExtract.getComponent().getFirst().getEhrFolder().
                getComponent().getFirst().getEhrComposition();
        var consultation = listMapper.mapToConsultation(comp, encounter);

        assertConsultation(consultation, "20100113152000", "test-text");
    }
    @Test
    public void testValidFullDataConsultationNoAuthorTimeList() {
        var ehrExtract = unmarshallEhrExtractElement(FULL_VALID_CONSULTATION_LIST_NOAUTHOR_TIME_XML);
        setUpEncounter("20100113152000", "20150213152000", "test-display", "test-text");

        var comp = ehrExtract.getComponent().getFirst().getEhrFolder().
                getComponent().getFirst().getEhrComposition();
        var consultation = listMapper.mapToConsultation(comp, encounter);

        assertConsultation(consultation, "20100113152000", "test-text");
    }

    @Test
    public void testValidFullDataConsultationNoAuthorList() {
        var ehrExtract = unmarshallEhrExtractElement(FULL_VALID_CONSULTATION_NOAUTHOR_LIST_XML);
        setUpEncounter("20100113152000", "20150213152000", "test-display", "test-text");

        var comp = ehrExtract.getComponent().getFirst().getEhrFolder().
                getComponent().getFirst().getEhrComposition();
        var consultation = listMapper.mapToConsultation(comp, encounter);

        assertConsultation(consultation, "20100113152000", "test-text");
    }

    @Test
    public void testValidNoOptionalDataConsultationList() {
        var ehrExtract = unmarshallEhrExtractElement(FULL_VALID_CONSULTATION_LIST_XML);
        setUpEncounter(null, null, "test-display", null);

        var comp = ehrExtract.getComponent().getFirst().getEhrFolder().
                getComponent().getFirst().getEhrComposition();

        var consultation = listMapper.mapToConsultation(comp, encounter);

        assertConsultation(consultation, "20100113151332", "test-display");
    }

    @Test
    public void testValidFullDataStructuredTopicList() {
        setUpCodeableConceptMock("test-display", "test-text");
        setUpEncounter("20100113152000", "20130213152000", "test-display", "test-text");
        var consultation = setUpConsultation();
        var compoundStatement = setUpCompoundStatement("test-text", "test-display",
            "20150213152000", false);

        var topic = listMapper.mapToTopic(consultation, compoundStatement);

        assertTopic(topic, compoundStatement.getId().getFirst().getRoot(), "20150213152000", "test-text");
    }

    @Test
    public void testValidFallbackDataStructuredTopicList() {
        setUpCodeableConceptMock("test-display", null);
        setUpEncounter(null, null, "test-display", null);
        var consultation = setUpConsultation();
        var compoundStatement = setUpCompoundStatement(null, "test-display",
            null, true);

        var topic = listMapper.mapToTopic(consultation, compoundStatement);

        assertTopic(topic, compoundStatement.getId().getFirst().getRoot(), "20130213152000", null);
    }

    @Test
    public void testValidFullDataFlatTopicList() {
        when(idGenerator.generateUuid()).thenReturn(FLAT_TOPIC_ID);
        setUpEncounter("20100113152000", "20150213152000", "test-display", "test-text");
        var consultation = setUpConsultation();

        var topic = listMapper.mapToTopic(consultation, null);

        assertTopic(topic, FLAT_TOPIC_ID, "20130213152000", null);
    }

    @Test
    public void testValidFullDataCategoryList() {
        setUpCodeableConceptMock("test-display", "test-text");
        setUpEncounter("20100113152000", "20130213152000", "test-display", "test-text");
        var topic = setUpTopic();
        var compoundStatement = setUpCompoundStatement("test-text", "test-display",
            "20150213152000", false);

        var category = listMapper.mapToCategory(topic, compoundStatement);

        assertCategory(category, "20150213152000", "test-text");
    }

    @Test
    public void testValidFallbackDataCategoryList() {
        setUpCodeableConceptMock("test-display", null);
        setUpEncounter("20100113152000", null, "test-display", null);
        var topic = setUpTopic();
        var compoundStatement = setUpCompoundStatement(null, "test-display",
            null, false);

        var category = listMapper.mapToCategory(topic, compoundStatement);

        assertCategory(category, "20110213152000", "test-display");
    }

    private void assertCategory(ListResource category, String date, String title) {
        assertThat(category.getId()).isEqualTo(COMPOUND_STATEMENT_ID);
        assertThat(category.getMeta().getProfile().getFirst().getValue()).isEqualTo(LIST_META_PROFILE);
        assertThat(category.getMode()).isEqualTo(ListMode.SNAPSHOT);
        assertThat(category.getStatus()).isEqualTo(ListStatus.CURRENT);
        assertThat(category.getSubject().getResource().getIdElement().getValue()).isEqualTo(PATIENT_ID);
        assertThat(category.getEncounter().getResource()).isEqualTo(encounter);
        assertThat(category.getDateElement().getValue()).isEqualTo(DateFormatUtil.parseToDateTimeType(date).getValueAsString());
        assertThat(category.getTitle()).isEqualTo(title);
        assertThat(category.getEntry()).isEmpty();
        assertCoding(category.getCode().getCodingFirstRep(), LIST_CODE_SYSTEM, CATEGORY_CODE_CODE, CATEGORY_CODE_DISPLAY);
        assertCoding(category.getOrderedBy().getCodingFirstRep(), LIST_ORDERED_BY_SYSTEM, LIST_ORDERED_BY_CODE,
            LIST_ORDERED_BY_DISPLAY);
    }

    private void assertTopic(ListResource topic, String id, String date, String title) {
        assertThat(topic.getId()).isEqualTo(id);
        assertThat(topic.getMeta().getProfile().getFirst().getValue()).isEqualTo(LIST_META_PROFILE);
        assertThat(topic.getMode()).isEqualTo(ListMode.SNAPSHOT);
        assertThat(topic.getStatus()).isEqualTo(ListStatus.CURRENT);
        assertThat(topic.getSubject().getResource().getIdElement().getValue()).isEqualTo(PATIENT_ID);
        assertThat(topic.getEncounter().getResource()).isEqualTo(encounter);
        assertThat(topic.getDateElement().getValue()).isEqualTo(DateFormatUtil.parseToDateTimeType(date).getValueAsString());
        assertThat(topic.getTitle()).isEqualTo(title);
        assertThat(topic.getEntry()).isEmpty();
        assertCoding(topic.getCode().getCodingFirstRep(), LIST_CODE_SYSTEM, TOPIC_CODE_CODE, TOPIC_CODE_DISPLAY);
        assertCoding(topic.getOrderedBy().getCodingFirstRep(), LIST_ORDERED_BY_SYSTEM, LIST_ORDERED_BY_CODE,
            LIST_ORDERED_BY_DISPLAY);
    }

    private void assertConsultation(ListResource consultation, String date, String title) {
        assertThat(consultation.getId()).isEqualTo(encounter.getId() + CONSULTATION_ID_SUFFIX);
        assertThat(consultation.getMeta().getProfile().getFirst().getValue()).isEqualTo(LIST_META_PROFILE);
        assertThat(consultation.getMode()).isEqualTo(ListMode.SNAPSHOT);
        assertThat(consultation.getStatus()).isEqualTo(ListStatus.CURRENT);
        assertThat(consultation.getSubject().getResource().getIdElement().getValue()).isEqualTo(PATIENT_ID);
        assertThat(consultation.getEncounter().getResource()).isEqualTo(encounter);
        assertThat(consultation.getTitle()).isEqualTo(title);
        assertThat(consultation.getEntry()).isEmpty();
        assertCoding(consultation.getCode().getCodingFirstRep(), LIST_CODE_SYSTEM, CONSULTATION_CODE_CODE, CONSULTATION_CODE_DISPLAY);
        assertCoding(consultation.getOrderedBy().getCodingFirstRep(), LIST_ORDERED_BY_SYSTEM, LIST_ORDERED_BY_CODE,
            LIST_ORDERED_BY_DISPLAY);

        if (date == null) {
            assertThat(consultation.getDateElement().getValue()).isNull();
        } else {
            assertThat(consultation.getDateElement().getValue()).isEqualTo(DateFormatUtil.parseToDateTimeType(date).getValueAsString());
        }
    }

    private void assertCoding(Coding coding, String system, String code, String display) {
        assertThat(coding.getSystem()).isEqualTo(system);
        assertThat(coding.getCode()).isEqualTo(code);
        assertThat(coding.getDisplay()).isEqualTo(display);
    }

    private void setUpEncounter(String startDate, String endDate, String display, String text) {
        Period period = new Period();

        if (startDate != null) {
            period.setStartElement(DateFormatUtil.parseToDateTimeType(startDate));
        }
        if (endDate != null) {
            period.setEndElement(DateFormatUtil.parseToDateTimeType(endDate));
        }

        CodeableConcept type = createCodeableConcept(null, null, display);
        type.setText(text);

        encounter
            .setType(List.of(type))
            .setPeriod(period)
            .setSubject(new Reference(new Patient().setId(PATIENT_ID)))
            .setId(ENCOUNTER_ID);
    }

    private ListResource setUpConsultation() {
        ListResource consultation = new ListResource();

        consultation
            .setDateElement(DateFormatUtil.parseToDateTimeType("20130213152000"))
            .setTitle("test-title")
            .setSubject(new Reference(new Patient().setId(PATIENT_ID)))
            .setEncounter(new Reference(encounter))
            .setId(ENCOUNTER_ID + CONSULTATION_ID_SUFFIX);

        return consultation;
    }

    private ListResource setUpTopic() {
        ListResource consultation = new ListResource();

        consultation
            .setDateElement(DateFormatUtil.parseToDateTimeType("20110213152000"))
            .setTitle("test-title")
            .setSubject(new Reference(new Patient().setId(PATIENT_ID)))
            .setEncounter(new Reference(encounter))
            .setId(COMPOUND_STATEMENT_ID);

        return consultation;
    }

    private RCMRMT030101UKCompoundStatement setUpCompoundStatement(String originalText, String display,
                                                                   String availabilityTime, boolean nullFlavorCode) {
        RCMRMT030101UKCompoundStatement compoundStatement = new RCMRMT030101UKCompoundStatement();
        II id = new II();
        id.setRoot(COMPOUND_STATEMENT_ID);

        CD cd = new CD();
        if (nullFlavorCode) {
            cd.setNullFlavor(CsNullFlavor.UNK);
        } else {
            cd.setOriginalText(originalText);
            cd.setDisplayName(display);
            cd.setCodeSystem("2.16.840.1.113883.2.1.6.2");
            cd.setCode("14L..00");
        }

        TS ts = new TS();
        if (availabilityTime != null) {
            ts.setValue(availabilityTime);
        } else {
            ts.setNullFlavor(CsNullFlavor.NI);
        }
        compoundStatement.setAvailabilityTime(ts);

        compoundStatement.getId().add(id);
        compoundStatement.setCode(cd);

        return compoundStatement;
    }

    private void setUpCodeableConceptMock(String display, String text) {
        var codeableConcept = createCodeableConcept(null, null, display);
        codeableConcept.setText(text);
        when(codeableConceptMapper.mapToCodeableConcept(any())).thenReturn(codeableConcept);
    }

    @SneakyThrows
    private RCMRMT030101UKEhrExtract unmarshallEhrExtractElement(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES_BASE + fileName), RCMRMT030101UKEhrExtract.class);
    }

}
