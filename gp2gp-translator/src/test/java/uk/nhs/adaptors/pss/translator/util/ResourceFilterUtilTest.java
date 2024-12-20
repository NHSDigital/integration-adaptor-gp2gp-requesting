package uk.nhs.adaptors.pss.translator.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.util.ResourceUtils.getFile;

import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import java.util.Objects;
import java.util.stream.Stream;

import org.hl7.v3.CR;
import org.hl7.v3.RCMRMT030101UKComponent;
import org.hl7.v3.RCMRMT030101UKComponent4;
import org.hl7.v3.RCMRMT030101UKCompoundStatement;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKLinkSet;
import org.hl7.v3.RCMRMT030101UKNarrativeStatement;
import org.hl7.v3.RCMRMT030101UKObservationStatement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import lombok.SneakyThrows;

public class ResourceFilterUtilTest {

    private static final String XML_RESOURCES = "xml/ResourceFilter/";

    @Test
    public void testIsDocumentReferenceResource() {
        final RCMRMT030101UKNarrativeStatement narrativeStatement = unmarshallNarrativeStatementElement(
            "document_reference_resource.xml");

        assertThat(ResourceFilterUtil.isDocumentReference(narrativeStatement)).isTrue();
    }

    @Test
    public void testIsNotDocumentReferenceResource() {
        final RCMRMT030101UKNarrativeStatement narrativeStatement = unmarshallNarrativeStatementElement(
            "observation_comment_resource.xml");

        assertThat(ResourceFilterUtil.isDocumentReference(narrativeStatement)).isFalse();
    }

    @Test
    public void testIsBloodPressureResource() {
        final RCMRMT030101UKCompoundStatement compoundStatement = unmarshallCompoundStatementElement("blood_pressure_resource.xml");

        assertThat(ResourceFilterUtil.isBloodPressure(compoundStatement)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("nonBloodPressureTestFiles")
    public void testIsNotBloodPressureResource(String inputXML) {
        final RCMRMT030101UKCompoundStatement compoundStatement = unmarshallCompoundStatementElement(inputXML);

        assertThat(ResourceFilterUtil.isBloodPressure(compoundStatement)).isFalse();
    }

    private static Stream<Arguments> nonBloodPressureTestFiles() {
        return Stream.of(
            Arguments.of("allergy_intolerance_resource.xml"),
            Arguments.of("diagnostic_report_resource.xml"),
            Arguments.of("template_resource.xml"),
            Arguments.of("specimen_resource.xml")
        );
    }

    @Test
    public void testIsAllergyIntoleranceResource() {
        final RCMRMT030101UKCompoundStatement compoundStatement = unmarshallCompoundStatementElement("allergy_intolerance_resource.xml");

        assertThat(ResourceFilterUtil.isAllergyIntolerance(compoundStatement)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("nonAllergyIntoleranceTestFiles")
    public void testIsNotAllergyIntoleranceResource(String inputXML) {
        final RCMRMT030101UKCompoundStatement compoundStatement = unmarshallCompoundStatementElement(inputXML);

        assertThat(ResourceFilterUtil.isAllergyIntolerance(compoundStatement)).isFalse();
    }

    private static Stream<Arguments> nonAllergyIntoleranceTestFiles() {
        return Stream.of(
            Arguments.of("blood_pressure_resource.xml"),
            Arguments.of("diagnostic_report_resource.xml"),
            Arguments.of("template_resource.xml"),
            Arguments.of("specimen_resource.xml")
        );
    }

    @Test
    public void testIsDiagnosticReportResource() {
        final RCMRMT030101UKCompoundStatement compoundStatement = unmarshallCompoundStatementElement("diagnostic_report_resource.xml");

        assertThat(ResourceFilterUtil.isDiagnosticReport(compoundStatement)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("nonDiagnosticReportTestFiles")
    public void testIsNotBloodPressure(String inputXML) {
        final RCMRMT030101UKCompoundStatement compoundStatement = unmarshallCompoundStatementElement(inputXML);

        assertThat(ResourceFilterUtil.isDiagnosticReport(compoundStatement)).isFalse();
    }

    private static Stream<Arguments> nonDiagnosticReportTestFiles() {
        return Stream.of(
            Arguments.of("allergy_intolerance_resource.xml"),
            Arguments.of("blood_pressure_resource.xml"),
            Arguments.of("template_resource.xml"),
            Arguments.of("specimen_resource.xml")
        );
    }

    @Test
    public void testIsTemplateResource() {
        final RCMRMT030101UKCompoundStatement compoundStatement = unmarshallCompoundStatementElement("template_resource.xml");

        assertThat(ResourceFilterUtil.isTemplate(compoundStatement)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("nonTemplateTestFiles")
    public void testIsNotTemplateResource(String inputXML) {
        final RCMRMT030101UKCompoundStatement compoundStatement = unmarshallCompoundStatementElement(inputXML);

        assertThat(ResourceFilterUtil.isTemplate(compoundStatement)).isFalse();
    }

    private static Stream<Arguments> nonTemplateTestFiles() {
        return Stream.of(
            Arguments.of("allergy_intolerance_resource.xml"),
            Arguments.of("diagnostic_report_resource.xml"),
            Arguments.of("blood_pressure_resource.xml"),
            Arguments.of("specimen_resource.xml")
        );
    }

    @Test
    public void testIsSpecimenResource() {
        final RCMRMT030101UKCompoundStatement compoundStatement = unmarshallCompoundStatementElement("specimen_resource.xml");

        assertThat(ResourceFilterUtil.isSpecimen(compoundStatement)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("nonSpecimenTestFiles")
    public void testIsNotSpecimenResource(String inputXML) {
        final RCMRMT030101UKCompoundStatement compoundStatement = unmarshallCompoundStatementElement(inputXML);

        assertThat(ResourceFilterUtil.isSpecimen(compoundStatement)).isFalse();
    }

    private static Stream<Arguments> nonSpecimenTestFiles() {
        return Stream.of(
            Arguments.of("allergy_intolerance_resource.xml"),
            Arguments.of("diagnostic_report_resource.xml"),
            Arguments.of("blood_pressure_resource.xml"),
            Arguments.of("template_resource.xml")
        );
    }

    @Test
    public void testIsReferralRequestToExternalDocumentLinkSet() {
        final var ehrExtract = unmarshallEhrExtractElement("ehr_extract_with_referral_request_to_external_document_linkset.xml");
        final var linkSet = extractFirstLinkSetFromEhrExtract(ehrExtract);

        assertThat(ResourceFilterUtil.isReferralRequestToExternalDocumentLinkSet(ehrExtract, linkSet))
            .isTrue();
    }

    @Test
    public void When_ActiveProblem_Expect_IsNotReferralRequestToExternalDocumentLinkSet() {
        final var ehrExtract = unmarshallEhrExtractElement("ehr_extract_with_referral_request_to_external_document_linkset.xml");
        final var linkSet = extractFirstLinkSetFromEhrExtract(ehrExtract);
        linkSet.getCode().setCode("394774009");

        assertThat(ResourceFilterUtil.isReferralRequestToExternalDocumentLinkSet(ehrExtract, linkSet))
            .isFalse();
    }

    @Test
    public void When_LinkSetHasReadV2Code_Expect_IsNotReferralRequestToExternalDocumentLinkSet() {
        final var ehrExtract = unmarshallEhrExtractElement("ehr_extract_with_referral_request_to_external_document_linkset.xml");
        final var linkSet = extractFirstLinkSetFromEhrExtract(ehrExtract);
        linkSet.getCode().setCodeSystem("2.16.840.1.113883.2.1.6.2");

        assertThat(ResourceFilterUtil.isReferralRequestToExternalDocumentLinkSet(ehrExtract, linkSet))
            .isFalse();
    }

    @Test
    public void When_LinkSetHasCodeWithQualifier_Expect_IsNotReferralRequestToExternalDocumentLinkSet() {
        final var ehrExtract = unmarshallEhrExtractElement("ehr_extract_with_referral_request_to_external_document_linkset.xml");
        final var linkSet = extractFirstLinkSetFromEhrExtract(ehrExtract);
        linkSet.getCode().getQualifier().add(new CR());

        assertThat(ResourceFilterUtil.isReferralRequestToExternalDocumentLinkSet(ehrExtract, linkSet))
            .isFalse();
    }

    @Test
    public void When_LinkSetHasCodeWithOriginalText_Expect_IsNotReferralRequestToExternalDocumentLinkSet() {
        final var ehrExtract = unmarshallEhrExtractElement("ehr_extract_with_referral_request_to_external_document_linkset.xml");
        final var linkSet = extractFirstLinkSetFromEhrExtract(ehrExtract);
        linkSet.getCode().setOriginalText("original-text");

        assertThat(ResourceFilterUtil.isReferralRequestToExternalDocumentLinkSet(ehrExtract, linkSet))
            .isFalse();
    }

    @Test
    public void When_LinkSetHasNoComponents_Expect_IsNotReferralRequestToExternalDocumentLinkSet() {
        final var ehrExtract = unmarshallEhrExtractElement("ehr_extract_with_referral_request_to_external_document_linkset.xml");
        final var linkSet = extractFirstLinkSetFromEhrExtract(ehrExtract);
        linkSet.getComponent().clear();

        assertThat(ResourceFilterUtil.isReferralRequestToExternalDocumentLinkSet(ehrExtract, linkSet))
            .isFalse();
    }

    @Test
    public void When_LinkSetHasNamedStatementRefWhichIsANarrativeStatement_Expect_IsNotReferralRequestToExternalDocumentLinkSet() {
        final var ehrExtract = unmarshallEhrExtractElement("ehr_extract_with_referral_request_to_external_document_linkset.xml");
        final var linkSet = extractFirstLinkSetFromEhrExtract(ehrExtract);
        linkSet.getConditionNamed().getNamedStatementRef().getId().setRoot("narrative-statement-1");

        assertThat(ResourceFilterUtil.isReferralRequestToExternalDocumentLinkSet(ehrExtract, linkSet))
            .isFalse();
    }

    @Test
    public void When_LinkSetHasComponentWhichReferencesRequestStatement_Expect_IsNotReferralRequestToExternalDocumentLinkSet() {
        final var ehrExtract = unmarshallEhrExtractElement("ehr_extract_with_referral_request_to_external_document_linkset.xml");
        final var linkSet = extractFirstLinkSetFromEhrExtract(ehrExtract);
        linkSet.getComponent().getFirst().getStatementRef().getId().setRoot("request-statement-1");

        assertThat(ResourceFilterUtil.isReferralRequestToExternalDocumentLinkSet(ehrExtract, linkSet))
            .isFalse();
    }

    private RCMRMT030101UKLinkSet extractFirstLinkSetFromEhrExtract(RCMRMT030101UKEhrExtract ehrExtract) {
        return ehrExtract.getComponent().stream()
            .map(RCMRMT030101UKComponent::getEhrFolder)
            .flatMap(ehrFolder -> ehrFolder.getComponent().stream())
            .flatMap(component -> component.getEhrComposition().getComponent().stream())
            .map(RCMRMT030101UKComponent4::getLinkSet)
            .filter(Objects::nonNull)
            .findFirst()
            .orElseThrow();
    }

    @SneakyThrows
    private RCMRMT030101UKCompoundStatement unmarshallCompoundStatementElement(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES + fileName),
            RCMRMT030101UKCompoundStatement.class);
    }

    @SneakyThrows
    private RCMRMT030101UKObservationStatement unmarshallObservationStatementElement(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES + fileName),
            RCMRMT030101UKObservationStatement.class);
    }

    @SneakyThrows
    private RCMRMT030101UKNarrativeStatement unmarshallNarrativeStatementElement(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES + fileName),
            RCMRMT030101UKNarrativeStatement.class);
    }

    @SneakyThrows
    private RCMRMT030101UKEhrExtract unmarshallEhrExtractElement(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES + fileName),
            RCMRMT030101UKEhrExtract.class);
    }
}
