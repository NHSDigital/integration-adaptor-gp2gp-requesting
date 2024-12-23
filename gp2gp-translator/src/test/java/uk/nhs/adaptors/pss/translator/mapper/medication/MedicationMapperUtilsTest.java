package uk.nhs.adaptors.pss.translator.mapper.medication;

import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.v3.INT;
import org.hl7.v3.RCMRMT030101UKAuthorise;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKMedicationStatement;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.util.ResourceUtils.getFile;

import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import lombok.SneakyThrows;

import java.math.BigInteger;

public class MedicationMapperUtilsTest {

    private static final String XML_RESOURCES_SUPPLY_AUTHORISE = "xml/EhrSupplyAuthorise/";
    private static final String XML_RESOURCES_MEDICATION_STATEMENT = "xml/MedicationStatement/";
    private static final int EXPECTED_SIZE_NOTES = 3;
    private static final int EXPECTED_QUANTITY_SIZE = 28;
    private static final String EXPECTED_DOSAGE_TEXT = "One To Be Taken Each Day";
    private static final String EXPECTED_DOSAGE_TEXT_WHEN_MISSING = "No Information available";
    private static final String EXPECTED_CODE_DISPLAY = "NHS prescription";

    private static final String AUTHORISE_ID = "8866381C-E5B8-4A6E-ADBC-964F9A77D407";

    @Test
    public void When_CreatingPrescriptionTypeExtension_Expect_AcuteValues() {
        var supplyAuth = unmarshallSupplyAuthorise("buildPrescriptionAcute.xml");
        var extension = MedicationMapperUtils.buildPrescriptionTypeExtension(supplyAuth);
        assertThat(extension).isPresent();
        extension.ifPresent(extension1 -> {
            assertThat(extension1.getValue()).isInstanceOf(CodeableConcept.class);
            var codeableConcept = (CodeableConcept) extension1.getValue();
            assertThat(codeableConcept.getCodingFirstRep().getDisplay()).isEqualTo("Acute");
        });
    }

    @Test
    public void When_CreatingPrescriptionTypeExtension_Expect_RepeatValues() {
        var supplyAuth = unmarshallSupplyAuthorise("buildPrescriptionRepeat.xml");
        var extension = MedicationMapperUtils.buildPrescriptionTypeExtension(supplyAuth);
        assertThat(extension).isPresent();
        extension.ifPresent(extension1 -> {
            assertThat(extension1.getValue()).isInstanceOf(CodeableConcept.class);
            var codeableConcept = (CodeableConcept) extension1.getValue();
            assertThat(codeableConcept.getCodingFirstRep().getDisplay()).isEqualTo("Repeat");
        });
    }

    @Test
    public void When_CreatingNotes_Expect_AllPertinentInformationToBeMapped() {
        var supplyAuth = unmarshallSupplyAuthorise("buildNotesForAuthMultiple.xml");
        var notes = MedicationMapperUtils.buildNotes(supplyAuth.getPertinentInformation());

        assertThat(notes).hasSize(EXPECTED_SIZE_NOTES);
    }

    @Test
    public void When_CreatingDosages_Expect_PertinentMedicationDosageToBeMapped() {
        var medicationStatement = unmarshallMedicationStatement("buildDosage.xml");
        var dosages = MedicationMapperUtils.buildDosage(medicationStatement.getPertinentInformation());

        assertThat(dosages.getText()).isEqualTo(EXPECTED_DOSAGE_TEXT);
    }

    @Test
    public void When_CreatingDosages_Expect_CorrectMessageWhenDosageMissing() {
        var medicationStatement = unmarshallMedicationStatement("noDosage.xml");
        var dosages = MedicationMapperUtils.buildDosage(medicationStatement.getPertinentInformation());

        assertThat(dosages.getText()).isEqualTo(EXPECTED_DOSAGE_TEXT_WHEN_MISSING);
    }

    @Test
    public void When_CreatingDosageQuantity_Expect_ValueToBeMapped() {
        var supplyAuth = unmarshallSupplyAuthorise("supplyWithQuantity.xml");
        var quantity = MedicationMapperUtils.buildDosageQuantity(supplyAuth.getQuantity());

        assertThat(quantity).isPresent();
        quantity.ifPresent(value -> {
            assertThat(value.getValue().intValue()).isEqualTo(EXPECTED_QUANTITY_SIZE);
            assertThat(value.getUnit()).isEqualTo("capsule");
        });
    }

    @Test
    public void When_CreatingDosageQuantityWithInvalidValue_Expect_ErrorToBeThrown() {
        var supplyAuth = unmarshallSupplyAuthorise("supplyWithInvalidQuantity.xml");
        assertThatThrownBy(() -> MedicationMapperUtils.buildDosageQuantity(supplyAuth.getQuantity()))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    public void When_ExtractingSupplyAuthoriseId_Expect_IdToBeExtractedCorrectly() {
        var supplyAuth = unmarshallSupplyAuthorise("supplyWithInvalidQuantity.xml");
        var authoriseId = MedicationMapperUtils.extractEhrSupplyAuthoriseId(supplyAuth);

        assertThat(authoriseId).isPresent();
        authoriseId.ifPresent(
            id -> {
                assertThat(id).isEqualTo(AUTHORISE_ID);
            }
        );
    }

    @Test
    public void When_ExtractingSupplyAuthorise_Expect_CorrectIdToBeReturned() {
        var ehrExtract = unmarshallEhrExtract("ehrExtract4.xml");
        var supplyAuthorise = MedicationMapperUtils.extractSupplyAuthorise(ehrExtract, AUTHORISE_ID);

        assertThat(supplyAuthorise.hasId()).isTrue();
        assertThat(supplyAuthorise.getId().hasRoot()).isTrue();
        assertThat(supplyAuthorise.getId().getRoot()).isEqualTo(AUTHORISE_ID);

        assertThat(supplyAuthorise.hasCode()).isTrue();
        assertThat(supplyAuthorise.getCode().hasDisplayName()).isTrue();
        assertThat(supplyAuthorise.getCode().getDisplayName()).isEqualTo(EXPECTED_CODE_DISPLAY);
    }

    @Test
    public void When_IsAcutePrescriptionAndThereIsNoRepeatInformation_Expect_IsNotAcutePrescription() {
        var supplyAuthorise = new RCMRMT030101UKAuthorise();

        var isAcutePrescription = MedicationMapperUtils.isAcutePrescription(supplyAuthorise);

        assertThat(isAcutePrescription).isFalse();
    }

    @Test
    public void When_IsAcutePrescriptionAndRepeatInformationHasValueOfOne_Expect_IsNotAcutePrescription() {
        var supplyAuthorise = new RCMRMT030101UKAuthorise();
        supplyAuthorise.setRepeatNumber(createINTFromInteger(1));

        var isAcutePrescription = MedicationMapperUtils.isAcutePrescription(supplyAuthorise);

        assertThat(isAcutePrescription).isFalse();
    }

    @Test
    public void When_IsAcutePrescriptionAndRepeatInformationHasValueOfZero_Expect_IsAcutePrescription() {
        var supplyAuthorise = new RCMRMT030101UKAuthorise();
        supplyAuthorise.setRepeatNumber(createINTFromInteger(0));

        var isAcutePrescription = MedicationMapperUtils.isAcutePrescription(supplyAuthorise);

        assertThat(isAcutePrescription).isTrue();
    }

    @SneakyThrows
    private RCMRMT030101UKAuthorise unmarshallSupplyAuthorise(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES_SUPPLY_AUTHORISE + fileName), RCMRMT030101UKAuthorise.class);
    }

    @SneakyThrows
    private RCMRMT030101UKMedicationStatement unmarshallMedicationStatement(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES_MEDICATION_STATEMENT + fileName),
            RCMRMT030101UKMedicationStatement.class);
    }

    @SneakyThrows
    private RCMRMT030101UKEhrExtract unmarshallEhrExtract(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES_MEDICATION_STATEMENT + fileName), RCMRMT030101UKEhrExtract.class);
    }

    @NotNull
    private static INT createINTFromInteger(int value) {
        INT intValue = new INT();
        intValue.setValue(BigInteger.valueOf(value));
        return intValue;
    }

}
