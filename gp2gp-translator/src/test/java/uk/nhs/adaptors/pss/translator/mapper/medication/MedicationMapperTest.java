package uk.nhs.adaptors.pss.translator.mapper.medication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallString;

import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.v3.RCMRMT030101UKConsumable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.SneakyThrows;
import uk.nhs.adaptors.pss.translator.mapper.CodeableConceptMapper;
import uk.nhs.adaptors.pss.translator.service.IdGeneratorService;
import uk.nhs.adaptors.pss.translator.util.DegradedCodeableConcepts;

@ExtendWith(MockitoExtension.class)
public class MedicationMapperTest {
    @Mock
    private CodeableConceptMapper codeableConceptMapper;

    @Mock
    private IdGeneratorService idGeneratorService;

    @Mock
    private MedicationMapperContext medicationMapperContext;

    @InjectMocks
    private MedicationMapper medicationMapper;

    @BeforeEach
    public void setup() {
        when(codeableConceptMapper.mapToCodeableConceptForMedication(any())).thenReturn(new CodeableConcept());
    }

    @Test
    public void When_MappingConsumable_Expect_CorrectFieldsMapped() {

        var inputXml = """
                <consumable xmlns="urn:hl7-org:v3" typeCode="CSM">
                    <manufacturedProduct classCode="MANU">
                        <manufacturedMaterial classCode="MMAT" determinerCode="KIND">
                            <code codeSystem="2.16.840.1.113883.2.1.6.15" code="58976020"
                            displayName="Bendroflumethiazide 2.5mg tablets">
                                <originalText>BENDROFLUMETHIAZIDE tabs 2.5mg</originalText>
                                <translation code="317919004" displayName="Bendroflumethiazide 2.5mg tablets"
                                codeSystem="2.16.840.1.113883.2.1.3.2.4.15"/>
                                <translation code="b211.00" codeSystem="2.16.840.1.113883.2.1.6.2"/>
                            </code>
                        </manufacturedMaterial>
                    </manufacturedProduct>
                </consumable>
                """;

        var consumable = unmarshallConsumable(inputXml);
        var medication = medicationMapper.createMedication(consumable);

        verify(codeableConceptMapper, times(1)).mapToCodeableConceptForMedication(any());

        assertThat(medication.getMeta()).isNotNull();
        assertThat(medication.getCode()).isNotNull();
    }

    @Test
    public void When_MappingConsumableWithCodeNotFound_Expect_CorrectFieldsMappedWithDegraded() {
        var inputXml = """
                <consumable xmlns="urn:hl7-org:v3" typeCode="CSM">
                    <manufacturedProduct classCode="MANU">
                        <manufacturedMaterial classCode="MMAT" determinerCode="KIND">
                            <code code="05652001"
                            codeSystem="2.16.840.1.113883.2.1.6.4"
                            displayName="AGAROL liquid [WARN/LAMB]"/>
                        </manufacturedMaterial>
                    </manufacturedProduct>
                </consumable>
                """;

        var consumable = unmarshallConsumable(inputXml);
        var medication = medicationMapper.createMedication(consumable);

        verify(codeableConceptMapper, times(1)).mapToCodeableConceptForMedication(any());

        assertThat(medication.getMeta()).isNotNull();
        assertThat(medication.getCode()).isNotNull();
        assertThat(medication.getCode().getCoding()).isNotNull();
        assertThat(medication.getCode().getCoding().getFirst()).isEqualTo(DegradedCodeableConcepts.DEGRADED_MEDICATION);
    }

    @SneakyThrows
    private RCMRMT030101UKConsumable unmarshallConsumable(String inputXml) {
        return unmarshallString(inputXml, RCMRMT030101UKConsumable.class);
    }
}
