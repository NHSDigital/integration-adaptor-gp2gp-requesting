package uk.nhs.adaptors.pss.translator.mapper.diagnosticreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hl7.fhir.dstu3.model.Observation.ObservationStatus;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.util.ResourceUtils.getFile;

import static uk.nhs.adaptors.pss.translator.util.DateFormatUtil.parseToDateTimeType;
import static uk.nhs.adaptors.pss.translator.util.DateFormatUtil.parseToInstantType;
import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.dstu3.model.DiagnosticReport;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Observation.ObservationRelationshipType;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.v3.RCMRMT030101UKEhrExtract;

import org.hl7.v3.RCMRMT030101UKCompoundStatement;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.SneakyThrows;
import uk.nhs.adaptors.pss.translator.mapper.CodeableConceptMapper;
import uk.nhs.adaptors.pss.translator.mapper.diagnosticreport.SpecimenBatteryMapper.SpecimenBatteryParameters;
import uk.nhs.adaptors.pss.translator.util.CompoundStatementResourceExtractors;
import uk.nhs.adaptors.pss.translator.util.DegradedCodeableConcepts;
import static uk.nhs.adaptors.common.util.CodeableConceptUtils.createCodeableConcept;
import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallString;

@ExtendWith(MockitoExtension.class)
public class SpecimenBatteryMapperTest {

    public static final String EHR_EXTRACT_WRAPPER = """
        <EhrExtract xmlns="urn:hl7-org:v3" classCode="EXTRACT" moodCode="EVN">
            <component>
                <ehrFolder>
                    <component>
                        {{ehrComposition}}
                    </component>
                </ehrFolder>
            </component>
        </EhrExtract>
        """;

    @Mock
    private CodeableConceptMapper codeableConceptMapper;

    @InjectMocks
    private SpecimenBatteryMapper specimenBatteryMapper;

    @Test
    void When_MappingObservationWithAvailabilityTimeInDiagnosticReport_Expect_MoreThanOneCompoundStatement() {
        final var ehrCompositionXml = """
            <ehrComposition>
                <id root="ENCOUNTER_ID"/>
                <component>
                    <CompoundStatement classCode="CLUSTER">
                        <id root="DR_TEST_ID1"/>
                        <availabilityTime value="20100225154201"/>
                        <component>
                            <CompoundStatement classCode="CLUSTER">
                                <id root="TEST_SPECIMEN_ID_1"/>
                                <component typeCode="COMP" contextConductionInd="true">
                                    <CompoundStatement classCode="BATTERY" moodCode="EVN">
                                        <id root="SPECIMEN_CHILD_BATTERY_COMPOUND_STATEMENT_ID_1"/>
                                    </CompoundStatement>
                                </component>
                            </CompoundStatement>
                        </component>
                    </CompoundStatement>
                </component>
                <component>
                    <CompoundStatement classCode="CLUSTER">
                        <id root="DR_TEST_ID2"/>
                        <availabilityTime value="20100225154202"/>
                        <component>
                            <CompoundStatement classCode="CLUSTER">
                                <id root="TEST_SPECIMEN_ID_2"/>
                                <component typeCode="COMP" contextConductionInd="true">
                                    <CompoundStatement classCode="BATTERY" moodCode="EVN">
                                        <id root="SPECIMEN_CHILD_BATTERY_COMPOUND_STATEMENT_ID_2"/>
                                    </CompoundStatement>
                                </component>
                            </CompoundStatement>
                        </component>
                    </CompoundStatement>
                </component>
                <component>
                    <CompoundStatement classCode="CLUSTER">
                        <id root="DR_TEST_ID3"/>
                        <availabilityTime value="20100225154203"/>
                        <component>
                            <CompoundStatement classCode="CLUSTER">
                                <id root="TEST_SPECIMEN_ID_3"/>
                                <component typeCode="COMP" contextConductionInd="true">
                                    <CompoundStatement classCode="BATTERY" moodCode="EVN">
                                        <id root="SPECIMEN_CHILD_BATTERY_COMPOUND_STATEMENT_ID_3"/>
                                    </CompoundStatement>
                                </component>
                            </CompoundStatement>
                        </component>
                    </CompoundStatement>
                </component>
            </ehrComposition>
            """;

        final var ehrExtract = unmarshallEhrExtractFromEhrCompositionXml(ehrCompositionXml);
        final var batteryCompoundStatements = getBatteryCompoundStatements(ehrExtract);

        assertThat(batteryCompoundStatements)
            .extracting(batteryCompoundStatement -> batteryCompoundStatement.getId().getFirst().getRoot())
            .containsExactly(
                "SPECIMEN_CHILD_BATTERY_COMPOUND_STATEMENT_ID_1",
                "SPECIMEN_CHILD_BATTERY_COMPOUND_STATEMENT_ID_2",
                "SPECIMEN_CHILD_BATTERY_COMPOUND_STATEMENT_ID_3"
            );
    }

    @Test void When_MappingObservationWithAvailabilityTimeInBatteryCompoundStatement_Expect_IssuedUsesThisValue() {
        final var ehrCompositionXml = """
            <ehrComposition>
                <id root="ENCOUNTER_ID"/>
                <component>
                    <CompoundStatement classCode="CLUSTER">
                        <id root="DR_TEST_ID"/>
                        <component>
                            <CompoundStatement classCode="CLUSTER">
                                <id root="TEST_SPECIMEN_ID_1"/>
                                <component typeCode="COMP" contextConductionInd="true">
                                    <CompoundStatement classCode="BATTERY" moodCode="EVN">
                                        <id root="SPECIMEN_CHILD_BATTERY_COMPOUND_STATEMENT_ID_1"/>
                                        <availabilityTime value="20100225154300"/>
                                    </CompoundStatement>
                                </component>
                            </CompoundStatement>
                        </component>
                    </CompoundStatement>
                </component>
            </ehrComposition>
            """;

        final var ehrExtract = unmarshallEhrExtractFromEhrCompositionXml(ehrCompositionXml);
        final var batteryCompoundStatement = getBatteryCompoundStatement(ehrExtract);
        final var batteryParameters = getSpecimenBatteryParameters(
            ehrExtract,
            batteryCompoundStatement,
            getObservations(),
            getObservationComments()
        );

        final Observation observation = specimenBatteryMapper.mapBatteryObservation(batteryParameters);

        assertThat(observation.getIssuedElement().asStringValue())
            .isEqualTo(parseToInstantType("20100225154300").asStringValue());
    }

    @Test void When_MappingObservationWithAvailabilityTimeInDiagnosticReport_Expect_IssuedUsesThisValue() {
        final var ehrCompositionXml = """
            <ehrComposition>
                <id root="ENCOUNTER_ID"/>
                <component>
                    <CompoundStatement classCode="CLUSTER">
                        <id root="DR_TEST_ID"/>
                        <availabilityTime value="20100225154200"/>
                        <component>
                            <CompoundStatement classCode="CLUSTER">
                                <id root="TEST_SPECIMEN_ID_1"/>
                                <component typeCode="COMP" contextConductionInd="true">
                                    <CompoundStatement classCode="BATTERY" moodCode="EVN">
                                        <id root="SPECIMEN_CHILD_BATTERY_COMPOUND_STATEMENT_ID_1"/>
                                    </CompoundStatement>
                                </component>
                            </CompoundStatement>
                        </component>
                    </CompoundStatement>
                </component>
            </ehrComposition>
            """;

        final var ehrExtract = unmarshallEhrExtractFromEhrCompositionXml(ehrCompositionXml);
        final var batteryCompoundStatement = getBatteryCompoundStatement(ehrExtract);
        final var batteryParameters = getSpecimenBatteryParameters(
            ehrExtract,
            batteryCompoundStatement,
            getObservations(),
            getObservationComments()
        );

        final Observation observation = specimenBatteryMapper.mapBatteryObservation(batteryParameters);

        assertThat(observation.getIssuedElement().asStringValue())
            .isEqualTo(parseToInstantType("20100225154200").asStringValue());
    }

    @Test void When_MappingObservationOnlyEhrCompositionAuthorTime_Expect_IssuedUsesThisValue() {
        final var ehrCompositionXml = """
            <ehrComposition>
                <id root="ENCOUNTER_ID"/>
                <author typeCode="AUT" contextControlCode="OP">
                    <time value="20220302105070"/>
                    <agentRef classCode="AGNT">
                        <id root="749107A2-4975-441F-8EDF-ADFF451FD12D"/>
                    </agentRef>
                </author>
                <component>
                    <CompoundStatement classCode="CLUSTER">
                        <id root="DR_TEST_ID"/>
                        <component>
                            <CompoundStatement classCode="CLUSTER">
                                <id root="TEST_SPECIMEN_ID_1"/>
                                <component typeCode="COMP" contextConductionInd="true">
                                    <CompoundStatement classCode="BATTERY" moodCode="EVN">
                                        <id root="SPECIMEN_CHILD_BATTERY_COMPOUND_STATEMENT_ID_1"/>
                                    </CompoundStatement>
                                </component>
                            </CompoundStatement>
                        </component>
                    </CompoundStatement>
                </component>
            </ehrComposition>
            """;

        final var ehrExtract = unmarshallEhrExtractFromEhrCompositionXml(ehrCompositionXml);
        final var batteryCompoundStatement = getBatteryCompoundStatement(ehrExtract);
        final var batteryParameters = getSpecimenBatteryParameters(
            ehrExtract,
            batteryCompoundStatement,
            getObservations(),
            getObservationComments()
        );

        final Observation observation = specimenBatteryMapper.mapBatteryObservation(batteryParameters);

        assertThat(observation.getIssuedElement().asStringValue())
            .isEqualTo(parseToInstantType("20220302105070").asStringValue());
    }

    @Test
    public void When_MappingObservation_Expect_ObservationFieldsAreCorrectlyMapped() {
        final var ehrCompositionXml =
            """
            <ehrComposition classCode="COMPOSITION" moodCode="EVN">
                <id root="ENCOUNTER_ID" />
                <component typeCode="COMP">
                    <CompoundStatement classCode="CLUSTER" moodCode="EVN">
                        <id root="DR_TEST_ID" />
                        <component typeCode="COMP" contextConductionInd="true">
                            <CompoundStatement classCode="CLUSTER" moodCode="EVN">
                                <id root="TEST_SPECIMEN_ID_1" />
                                <component typeCode="COMP" contextConductionInd="true">
                                    <CompoundStatement classCode="BATTERY" moodCode="EVN">
                                        <id root="SPECIMEN_CHILD_BATTERY_COMPOUND_STATEMENT_ID_1" />
                                        <effectiveTime>
                                            <center value="20100223000000" />
                                        </effectiveTime>
                                        <component typeCode="COMP" contextConductionInd="true">
                                            <NarrativeStatement classCode="OBS" moodCode="EVN">
                                                    <id root="BATTERY_DIRECT_CHILD_NARRATIVE_STATEMENT_ID"/>
                                                    <text mediaType="text/x-h7uk-pmip">Looks like Covid</text>
                                            </NarrativeStatement>
                                        </component>
                                    </CompoundStatement>
                                </component>
                            </CompoundStatement>
                        </component>
                    </CompoundStatement>
                </component>
            </ehrComposition>
            """;

        final var ehrExtract = unmarshallEhrExtractFromEhrCompositionXml(ehrCompositionXml);
        final var batteryCompoundStatement = getBatteryCompoundStatement(ehrExtract);
        final var batteryParameters = getSpecimenBatteryParameters(
            ehrExtract,
            batteryCompoundStatement,
            getObservations(),
            getObservationComments()
        );

        final Observation observation = specimenBatteryMapper.mapBatteryObservation(batteryParameters);

        assertAll(
            () -> assertThat(observation.getId())
                .isEqualTo("SPECIMEN_CHILD_BATTERY_COMPOUND_STATEMENT_ID_1"),
            () -> assertThat(observation.getIdentifierFirstRep().getSystem())
                .contains("TEST_PRACTISE_CODE"),
            () -> assertThat(observation.getEffectiveDateTimeType().getValueAsString())
                .isEqualTo(parseToDateTimeType("20100223000000").getValueAsString()),
            () -> assertThat(observation.getSpecimen().getReference())
                .contains("TEST_SPECIMEN_ID_1"),
            () -> assertThat(observation.getStatus())
                .isEqualTo(ObservationStatus.FINAL),
            () -> assertThat(observation.getMeta().getProfile().getFirst().getValue())
                .contains("Observation-1"),
            () -> assertThat(observation.getComment())
                .isEqualTo("Looks like Covid"),
            () -> assertThat(observation.getContext().getReference())
                .contains("ENCOUNTER_ID"),
            () -> assertThat(observation.getSubject().getResource().getIdElement().getValue())
                .isEqualTo("TEST_PATIENT_ID")
        );
    }

    @Test
    public void When_MappingObservation_Expect_ObservationRelationshipsSet() {
        final var ehrExtract = getSpecimenBatteryEhrExtract();
        final var batteryCompoundStatement = getBatteryCompoundStatement(ehrExtract);
        final var observations = getObservations();
        final var batteryParameters = getSpecimenBatteryParameters(
            ehrExtract,
            batteryCompoundStatement,
            observations,
            getObservationComments());

        final Observation observation = specimenBatteryMapper.mapBatteryObservation(batteryParameters);

        assertAll(
            () -> assertThat(observations.getFirst().getRelatedFirstRep().getType())
                .isEqualTo(ObservationRelationshipType.DERIVEDFROM),
            () -> assertThat(observation.getRelatedFirstRep().getTarget().getReference())
                .contains("BATTERY_DIRECT_CHILD_OBSERVATION_STATEMENT"),
            () -> assertThat(observation.getRelated().get(1).getTarget().getReference())
                .contains("OBSERVATION_STATEMENT_ID")
        );
    }

    @Test
    public void When_MappingObservation_Expect_ObservationCommentsDoNotContainBatteryDirectChildNarrativeStatement() {
        final var ehrExtract = getSpecimenBatteryEhrExtract();
        final var batteryCompoundStatement = getBatteryCompoundStatement(ehrExtract);
        final var observationComments = getObservationComments();
        final var batteryParameters = getSpecimenBatteryParameters(
            ehrExtract,
            batteryCompoundStatement,
            getObservations(),
            observationComments);

        specimenBatteryMapper.mapBatteryObservation(batteryParameters);

        final var observationCommentIds = observationComments.stream().map(Observation::getId).toList();

        assertAll(
            () -> assertThat(observationComments)
                .hasSize(2),
            () -> assertThat(observationCommentIds)
                .doesNotContain("BATTERY_DIRECT_CHILD_NARRATIVE_STATEMENT_ID")
        );
    }

    @Test
    public void When_MappingObservationFromBatteryCompoundStatementWithSnomedCode_Expect_CorrectlyMapped() {
        final var codeableConcept = createCodeableConcept("1.2.3.4.5", "http://snomed.info/sct", "Test Display");
        when(codeableConceptMapper.mapToCodeableConcept(any())).thenReturn(codeableConcept);

        final RCMRMT030101UKEhrExtract ehrExtract = getSpecimenBatteryEhrExtract();
        var batteryCompoundStatement = getBatteryCompoundStatement(ehrExtract);

        var batteryParameters = getSpecimenBatteryParameters(
            ehrExtract,
            batteryCompoundStatement,
            getObservations(),
            getObservationComments());

        final Observation observation = specimenBatteryMapper.mapBatteryObservation(batteryParameters);

        assertThat(observation.getCode())
            .isEqualTo(codeableConcept);
    }

    @Test
    public void When_MappingObservationFromBatteryCompoundStatementWithoutSnomedCode_Expect_DegradedCode() {
        var codeableConcept = createCodeableConcept("1.2.3.4.5", null, "Test Display");
        when(codeableConceptMapper.mapToCodeableConcept(any())).thenReturn(codeableConcept);

        final RCMRMT030101UKEhrExtract ehrExtract = getSpecimenBatteryEhrExtract();
        var batteryCompoundStatement = getBatteryCompoundStatement(ehrExtract);

        final List<Observation> observations = getObservations();
        final List<Observation> observationComments = getObservationComments();

        var batteryParameters = getSpecimenBatteryParameters(ehrExtract, batteryCompoundStatement, observations, observationComments);

        final Observation observation = specimenBatteryMapper.mapBatteryObservation(batteryParameters);

        assertThat(observation.getCode().getCodingFirstRep())
            .isEqualTo(DegradedCodeableConcepts.DEGRADED_OTHER);
    }

    private List<Observation> getObservationComments() {
        List<Observation> observationComments = new ArrayList<>();
        var batteryObservationComment = new Observation()
            .setComment("""
                           CommentType:SUPER COMMENT
                           CommentDate:20220321163025

                           Looks like Covid
                           """);

        batteryObservationComment.setId("BATTERY_DIRECT_CHILD_NARRATIVE_STATEMENT_ID");

        var otherClusterObservationComment = new Observation()
            .setComment("""
                CommentType:OTHER COMMENT
                CommentDate:20220321162705

                Or maybe not?
                """);

        otherClusterObservationComment.setId("OTHER_COMMENT_NARRATIVE_STATEMENT_ID");

        var userObservationComment = new Observation()
            .setComment("""
                CommentType:USER COMMENT
                CommentDate:20100223000000

                This should not be a part of Battery Observation.comment
                """);

        userObservationComment.setId("USER_COMMENT_NARRATIVE_STATEMENT_ID");

        observationComments.add(batteryObservationComment);
        observationComments.add(otherClusterObservationComment);
        observationComments.add(userObservationComment);

        return observationComments;
    }

    private SpecimenBatteryParameters getSpecimenBatteryParameters(
        RCMRMT030101UKEhrExtract ehrExtract,
        RCMRMT030101UKCompoundStatement batteryCompoundStatement,
        List<Observation> observations,
        List<Observation> observationComments) {

        return SpecimenBatteryParameters.builder()
            .ehrExtract(ehrExtract)
            .batteryCompoundStatement(batteryCompoundStatement)
            .specimenCompoundStatement(getSpecimenCompoundStatement(ehrExtract))
            .ehrComposition(getEhrComposition(ehrExtract))
            .diagnosticReport(getDiagnosticReport(ehrExtract))
            .patient((Patient) new Patient().setId("TEST_PATIENT_ID"))
            .encounters(List.of((Encounter) new Encounter().setId("ENCOUNTER_ID")))
            .practiceCode("TEST_PRACTISE_CODE")
            .observations(observations)
            .observationComments(observationComments)
            .build();
    }

    private RCMRMT030101UKEhrComposition getEhrComposition(RCMRMT030101UKEhrExtract ehrExtract) {
        return ehrExtract.getComponent().getFirst().getEhrFolder().getComponent().getFirst().getEhrComposition();
    }

    private RCMRMT030101UKCompoundStatement getSpecimenCompoundStatement(RCMRMT030101UKEhrExtract ehrExtract) {
        return getEhrComposition(ehrExtract).getComponent().getFirst().getCompoundStatement()
            .getComponent().getFirst().getCompoundStatement();
    }

    private DiagnosticReport getDiagnosticReport(RCMRMT030101UKEhrExtract ehrExtract) {
        var compoundStatement = getEhrComposition(ehrExtract).getComponent().getFirst().getCompoundStatement();
        var diagnosticReport = new DiagnosticReport();
        diagnosticReport.setId(compoundStatement.getId().getFirst().getRoot());

        if (compoundStatement.getAvailabilityTime() != null) {
            diagnosticReport.setIssued(
                parseToDateTimeType(compoundStatement.getAvailabilityTime().getValue()).getValue()
            );
        }

        return diagnosticReport;
    }

    private List<Observation> getObservations() {
        return List.of(
            (Observation) new Observation().setId("BATTERY_DIRECT_CHILD_OBSERVATION_STATEMENT"),
            (Observation) new Observation().setId("OBSERVATION_STATEMENT_ID")
        );
    }

    private RCMRMT030101UKCompoundStatement getBatteryCompoundStatement(RCMRMT030101UKEhrExtract ehrExtract) {
        return getEhrComposition(ehrExtract).getComponent()
            .stream()
            .flatMap(CompoundStatementResourceExtractors::extractAllCompoundStatements)
            .filter(compoundStatement -> "BATTERY".equals(compoundStatement.getClassCode().getFirst()))
            .findFirst()
            .orElseThrow();
    }

    private List<RCMRMT030101UKCompoundStatement> getBatteryCompoundStatements(RCMRMT030101UKEhrExtract ehrExtract) {
        return getEhrComposition(ehrExtract).getComponent()
            .stream()
            .flatMap(CompoundStatementResourceExtractors::extractAllCompoundStatements)
            .filter(compoundStatement -> "BATTERY".equals(compoundStatement.getClassCode().getFirst()))
            .toList();
    }

    @SneakyThrows
    private RCMRMT030101UKEhrExtract getSpecimenBatteryEhrExtract() {
        return unmarshallFile(
            getFile("classpath:xml/SpecimenBattery/specimen_battery_compound_statement.xml"),
            RCMRMT030101UKEhrExtract.class
        );
    }

    @SneakyThrows
    private RCMRMT030101UKEhrExtract unmarshallEhrExtractFromEhrCompositionXml(String ehrCompositionXml) {
        var ehrExtractXml = EHR_EXTRACT_WRAPPER.replace("{{ehrComposition}}", ehrCompositionXml);
        return unmarshallString(ehrExtractXml, RCMRMT030101UKEhrExtract.class);
    }
}

