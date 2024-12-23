package uk.nhs.adaptors.pss.translator.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.util.ResourceUtils.getFile;

import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import java.math.BigDecimal;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.InstantType;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKObservationStatement;
import org.junit.jupiter.api.Test;


import lombok.SneakyThrows;

public class ObservationUtilTest {
    private static final String XML_RESOURCES_BASE = "xml/Observation/";
    private static final String ISSUED_EHR_COMPOSITION_EXAMPLE = "2020-01-01T01:01:01.000+00:00";
    private static final String INTERPRETATION_SYSTEM = "http://hl7.org/fhir/v2/0078";
    private static final String EFFECTIVE_START_DATE_1 = "2010-05-21";
    private static final String EFFECTIVE_START_DATE_2 = "2010-05-20";
    private static final String EFFECTIVE_END_DATE = "2010-05-22";
    private static final String QUANTITY_UNIT = "milliliter";
    private static final String QUANTITY_EXTENSION_URL = "https://fhir.hl7.org.uk/STU3/StructureDefinition/Extension-CareConnect"
        + "-ValueApproximation-1";
    private static final BigDecimal PQ_QUANTITY_VALUE_BASE = new BigDecimal(100);
    private static final BigDecimal PQ_QUANTITY_VALUE = PQ_QUANTITY_VALUE_BASE.setScale(3);
    private static final BigDecimal IVL_PQ_QUANTITY_VALUE = new BigDecimal(200);
    private static final BigDecimal REFERENCE_RANGE_LOW_VALUE_1 = new BigDecimal(10);
    private static final BigDecimal REFERENCE_RANGE_LOW_VALUE_2 = new BigDecimal(20);
    private static final BigDecimal REFERENCE_RANGE_HIGH_VALUE_1 = new BigDecimal(12);
    private static final BigDecimal REFERENCE_RANGE_HIGH_VALUE_2 = new BigDecimal(22);
    private static final Double REFERENCE_RANGE_LOW_VALUE_DECIMAL = 10.5;
    private static final Double REFERENCE_RANGE_HIGH_VALUE_DECIMAL = 12.2;


    @SneakyThrows
    private RCMRMT030101UKEhrExtract unmarshallEhrExtractElement(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES_BASE + fileName), RCMRMT030101UKEhrExtract.class);
    }

    private RCMRMT030101UKObservationStatement getObservationStatementFromEhrExtract(RCMRMT030101UKEhrExtract ehrExtract) {
        return ehrExtract.getComponent().getFirst().getEhrFolder().getComponent().getFirst().getEhrComposition().getComponent().getFirst()
            .getObservationStatement();
    }

    private RCMRMT030101UKEhrComposition getEhrCompositionFromEhrExtract(RCMRMT030101UKEhrExtract ehrExtract) {
        return ehrExtract.getComponent().getFirst().getEhrFolder().getComponent().getFirst().getEhrComposition();
    }

    private void assertInterpretation(CodeableConcept interpretation, String text, String code, String display) {
        assertThat(interpretation.getText()).isEqualTo(text);
        assertThat(interpretation.getCoding().getFirst().getCode()).isEqualTo(code);
        assertThat(interpretation.getCoding().getFirst().getDisplay()).isEqualTo(display);
        assertThat(interpretation.getCoding().getFirst().getSystem()).isEqualTo(INTERPRETATION_SYSTEM);
    }

    @Test
    public void mapValueQuantityUsingPqQuantity() {

        var ehrExtract = unmarshallEhrExtractElement("pq_value_quantity_observation_example.xml");
        var observationStatement = getObservationStatementFromEhrExtract(ehrExtract);

        var quantity = ObservationUtil.getValueQuantity(
            observationStatement.getValue(),
            observationStatement.getUncertaintyCode()
        );

        assertThat(quantity.getValue()).isEqualTo(PQ_QUANTITY_VALUE);
        assertThat(quantity.getUnit()).isEqualTo(QUANTITY_UNIT);
    }

    @Test
    public void mapValueQuantityUsingIvlPqQuantity() {

        var ehrExtract = unmarshallEhrExtractElement("ivl_pq_value_quantity_observation_example.xml");
        var observationStatement = getObservationStatementFromEhrExtract(ehrExtract);

        var quantity = ObservationUtil.getValueQuantity(observationStatement.getValue(),
            observationStatement.getUncertaintyCode());

        assertThat(quantity.getValue()).isEqualTo(IVL_PQ_QUANTITY_VALUE);
        assertThat(quantity.getUnit()).isEqualTo(QUANTITY_UNIT);
    }

    @Test
    public void mapValueQuantityWithUncertaintyCodeSet() {
        var ehrExtract = unmarshallEhrExtractElement(
            "value_quantity_with_extension_observation_example.xml");
        var observationStatement = getObservationStatementFromEhrExtract(ehrExtract);

        var quantity = ObservationUtil.getValueQuantity(observationStatement.getValue(),
            observationStatement.getUncertaintyCode());

        assertThat(quantity.getExtension().getFirst().getUrl()).isEqualTo(QUANTITY_EXTENSION_URL);
        assertThat(quantity.getExtension().getFirst().getValueAsPrimitive().getValue()).isEqualTo(true);
    }

    @Test
    public void mapIssuedUsingEhrComposition() {
        var ehrExtract = unmarshallEhrExtractElement(
            "issued_using_ehr_composition_observation_example.xml");
        var ehrComposition = getEhrCompositionFromEhrExtract(ehrExtract);

        InstantType issued = ObservationUtil.getIssued(ehrComposition);

        assertThat(issued.asStringValue()).isEqualTo(ISSUED_EHR_COMPOSITION_EXAMPLE);
    }

    @Test
    public void mapIssuedUsingEhrExtractExpectNull() {
        var ehrExtract = unmarshallEhrExtractElement("issued_using_ehr_extract_observation_example.xml");
        var ehrComposition = getEhrCompositionFromEhrExtract(ehrExtract);

        InstantType issued = ObservationUtil.getIssued(ehrComposition);

        assertThat(issued).isNull();
    }

    @Test
    public void mapInterpretationWithInterpretationCodeHigh() {
        var ehrExtract = unmarshallEhrExtractElement("interpretation_high_observation_example.xml");
        var observationStatement = getObservationStatementFromEhrExtract(ehrExtract);

        CodeableConcept interpretation = ObservationUtil.getInterpretation(observationStatement.getInterpretationCode());

        assertInterpretation(interpretation, "High Text", "H", "High");
    }

    @Test
    public void mapInterpretationWithInterpretationCodeLow() {
        var ehrExtract = unmarshallEhrExtractElement("interpretation_low_observation_example.xml");
        var observationStatement = getObservationStatementFromEhrExtract(ehrExtract);

        CodeableConcept interpretation = ObservationUtil.getInterpretation(observationStatement.getInterpretationCode());

        assertInterpretation(interpretation, "Low Text", "L", "Low");
    }

    @Test
    public void mapInterpretationWithInterpretationCodeNullFlavor() {
        var ehrExtract = unmarshallEhrExtractElement("interpretation_null_flavor_observation_example.xml");
        var observationStatement = getObservationStatementFromEhrExtract(ehrExtract);

        CodeableConcept interpretation = ObservationUtil.getInterpretation(observationStatement.getInterpretationCode());

        assertThat(interpretation).isNull();
    }

    @Test
    public void mapInterpretationWithInterpretationCodeAbnormal() {
        var ehrExtract = unmarshallEhrExtractElement("interpretation_abnormal_observation_example.xml");
        var observationStatement = getObservationStatementFromEhrExtract(ehrExtract);

        CodeableConcept interpretation = ObservationUtil.getInterpretation(observationStatement.getInterpretationCode());

        assertInterpretation(interpretation, "Abnormal Text", "A", "Abnormal");
    }

    @Test
    public void mapEffectiveDateTimeUsingEffectiveCenter() {
        var ehrExtract = unmarshallEhrExtractElement(
            "effective_date_time_type_using_effective_time_center.xml");
        var observationStatement = getObservationStatementFromEhrExtract(ehrExtract);

        DateTimeType effective = (DateTimeType) ObservationUtil.getEffective(observationStatement.getEffectiveTime(),
            observationStatement.getAvailabilityTime());

        assertThat(effective.getValueAsString()).isEqualTo(EFFECTIVE_START_DATE_1);
    }

    @Test
    public void mapEffectivePeriodStartEndUsingEffectiveTime() {
        var ehrExtract = unmarshallEhrExtractElement(
            "effective_period_start_end_using_effective_time_observation_example.xml");
        var observationStatement = getObservationStatementFromEhrExtract(ehrExtract);

        Period effective = (Period) ObservationUtil.getEffective(observationStatement.getEffectiveTime(),
            observationStatement.getAvailabilityTime());

        assertThat(effective.getStartElement().getValueAsString()).isEqualTo(EFFECTIVE_START_DATE_1);
        assertThat(effective.getEndElement().getValueAsString()).isEqualTo(EFFECTIVE_END_DATE);
    }

    @Test
    public void mapEffectivePeriodStartNoEnd() {
        var ehrExtract = unmarshallEhrExtractElement(
            "effective_period_start_no_high_observation_example.xml");
        var observationStatement = getObservationStatementFromEhrExtract(ehrExtract);

        Period effective = (Period) ObservationUtil.getEffective(observationStatement.getEffectiveTime(),
            observationStatement.getAvailabilityTime());

        assertThat(effective.getStartElement().getValueAsString()).isEqualTo(EFFECTIVE_START_DATE_1);
        assertThat(effective.getEnd()).isNull();
    }

    @Test
    public void mapEffectivePeriodEndNoStart() {
        var ehrExtract = unmarshallEhrExtractElement(
            "effective_period_end_no_low_observation_example.xml");
        var observationStatement = getObservationStatementFromEhrExtract(ehrExtract);

        Period effective = (Period) ObservationUtil.getEffective(observationStatement.getEffectiveTime(),
            observationStatement.getAvailabilityTime());

        assertThat(effective.getStart()).isNull();
        assertThat(effective.getEndElement().getValueAsString()).isEqualTo(EFFECTIVE_END_DATE);
    }

    @Test
    public void mapEffectivePeriodStartEndLowIsAvailabilityTime() {
        var ehrExtract = unmarshallEhrExtractElement(
            "effective_period_start_end_low_is_availability_time_observation_example.xml");
        var observationStatement = getObservationStatementFromEhrExtract(ehrExtract);

        Period effective = (Period) ObservationUtil.getEffective(observationStatement.getEffectiveTime(),
            observationStatement.getAvailabilityTime());

        assertThat(effective.getStartElement().getValueAsString()).isEqualTo(EFFECTIVE_START_DATE_2);
        assertThat(effective.getEndElement().getValueAsString()).isEqualTo(EFFECTIVE_END_DATE);
    }

    @Test
    public void mapEffectiveDateTimeTypeUsingAvailabilityTime() {
        var ehrExtract = unmarshallEhrExtractElement(
            "effective_date_time_type_using_availability_time_observation_example.xml");
        var observationStatement = getObservationStatementFromEhrExtract(ehrExtract);

        DateTimeType effective = (DateTimeType) ObservationUtil.getEffective(observationStatement.getEffectiveTime(),
            observationStatement.getAvailabilityTime());

        assertThat(effective.getValueAsString()).isEqualTo(EFFECTIVE_START_DATE_2);
    }

    @Test
    public void noValidEffectiveInObservationStatement() {
        var ehrExtract = unmarshallEhrExtractElement("no_effective_observation_example.xml");
        var observationStatement = getObservationStatementFromEhrExtract(ehrExtract);

        var effective = ObservationUtil.getEffective(observationStatement.getEffectiveTime(),
            observationStatement.getAvailabilityTime());

        assertThat(effective).isNull();
    }

    @Test
    public void mapReferenceRangeWithLowHighSetInInputReferenceRange() {
        var ehrExtract = unmarshallEhrExtractElement("high_low_reference_range_observation_example.xml");
        var observationStatement = getObservationStatementFromEhrExtract(ehrExtract);

        var referenceRanges = ObservationUtil.getReferenceRange(observationStatement.getReferenceRange());

        assertThat(referenceRanges.getFirst().getText()).isEqualTo("Test Range 1");
        assertThat(referenceRanges.getFirst().getLow().getValue()).isEqualTo(REFERENCE_RANGE_LOW_VALUE_1);
        assertThat(referenceRanges.getFirst().getHigh().getValue()).isEqualTo(REFERENCE_RANGE_HIGH_VALUE_1);
    }

    @Test
    public void mapReferenceRangeWithOnlyLowSetInInputReferenceRange() {
        var ehrExtract = unmarshallEhrExtractElement("low_reference_range_observation_example.xml");
        var observationStatement = getObservationStatementFromEhrExtract(ehrExtract);

        var referenceRanges = ObservationUtil.getReferenceRange(observationStatement.getReferenceRange());

        assertThat(referenceRanges.getFirst().getLow().getValue()).isEqualTo(REFERENCE_RANGE_LOW_VALUE_1);
        assertThat(referenceRanges.getFirst().getHigh().getValue()).isNull();
    }

    @Test
    public void mapReferenceRangeWithOnlyHighSetInInputReferenceRange() {
        var ehrExtract = unmarshallEhrExtractElement("high_reference_range_observation_example.xml");
        var observationStatement = getObservationStatementFromEhrExtract(ehrExtract);

        var referenceRanges = ObservationUtil.getReferenceRange(observationStatement.getReferenceRange());

        assertThat(referenceRanges.getFirst().getLow().getValue()).isNull();
        assertThat(referenceRanges.getFirst().getHigh().getValue()).isEqualTo(REFERENCE_RANGE_HIGH_VALUE_1);
    }

    @Test
    public void mapReferenceRangeWithNoTextInReferenceRange() {
        var ehrExtract = unmarshallEhrExtractElement("no_text_reference_range_observation_example.xml");
        var observationStatement = getObservationStatementFromEhrExtract(ehrExtract);

        var referenceRanges = ObservationUtil.getReferenceRange(observationStatement.getReferenceRange());

        assertThat(StringUtils.isEmpty(referenceRanges.getFirst().getText())).isTrue();
    }

    @Test
    public void mapReferenceRangeWithMultipleReferenceRanges() {
        var ehrExtract = unmarshallEhrExtractElement("multiple_reference_range_observation_example.xml");
        var observationStatement = getObservationStatementFromEhrExtract(ehrExtract);

        var referenceRanges = ObservationUtil.getReferenceRange(observationStatement.getReferenceRange());

        assertThat(referenceRanges.getFirst().getText()).isEqualTo("Test Range 1");
        assertThat(referenceRanges.getFirst().getLow().getValue()).isEqualTo(REFERENCE_RANGE_LOW_VALUE_1);
        assertThat(referenceRanges.getFirst().getHigh().getValue()).isEqualTo(REFERENCE_RANGE_HIGH_VALUE_1);
        assertThat(referenceRanges.get(1).getText()).isEqualTo("Test Range 2");
        assertThat(referenceRanges.get(1).getLow().getValue()).isEqualTo(REFERENCE_RANGE_LOW_VALUE_2);
        assertThat(referenceRanges.get(1).getHigh().getValue()).isEqualTo(REFERENCE_RANGE_HIGH_VALUE_2);
    }

    @Test
    public void mapReferenceRangeWithDecimalValues() {
        var ehrExtract = unmarshallEhrExtractElement("decimal_reference_range_observation_example.xml");
        var observationStatement = getObservationStatementFromEhrExtract(ehrExtract);

        var referenceRange = ObservationUtil.getReferenceRange(observationStatement.getReferenceRange());

        assertThat(referenceRange.getFirst().getText()).isEqualTo("Test Range 1");
        assertThat(referenceRange.getFirst().getLow().getValue().doubleValue()).isEqualTo(REFERENCE_RANGE_LOW_VALUE_DECIMAL);
        assertThat(referenceRange.getFirst().getHigh().getValue().doubleValue()).isEqualTo(REFERENCE_RANGE_HIGH_VALUE_DECIMAL);
    }
}
