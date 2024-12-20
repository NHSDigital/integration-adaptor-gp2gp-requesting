package uk.nhs.adaptors.pss.translator.mapper;

import lombok.SneakyThrows;

import org.hl7.fhir.dstu3.model.Address;
import org.hl7.fhir.dstu3.model.Address.AddressUse;
import org.hl7.fhir.dstu3.model.Address.AddressType;
import org.hl7.fhir.dstu3.model.ContactPoint;
import org.hl7.fhir.dstu3.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.dstu3.model.ContactPoint.ContactPointUse;
import org.hl7.fhir.dstu3.model.HumanName.NameUse;
import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Practitioner;
import org.hl7.fhir.dstu3.model.PractitionerRole;
import org.hl7.v3.RCMRMT030101UKAgentDirectory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.util.ResourceUtils.getFile;

import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;
import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallString;

public class AgentDirectoryMapperTest {

    private static final String XML_RESOURCES_BASE = "xml/AgentDirectory/";
    private static final String PRACT_META_PROFILE = "https://fhir.nhs.uk/STU3/StructureDefinition/CareConnect-GPC-Practitioner-1";
    private static final String ORG_META_PROFILE = "https://fhir.nhs.uk/STU3/StructureDefinition/CareConnect-GPC-Organization-1";
    private static final String PRACT_ROLE_META_PROFILE = "https://fhir.nhs.uk/STU3/StructureDefinition/CareConnect-GPC"
            + "-PractitionerRole-1";
    private final AgentDirectoryMapper agentDirectoryMapper = new AgentDirectoryMapper();
    private static final String ORG_IDENTIFIER_SYSTEM = "https://fhir.nhs.uk/Id/ods-organization-code";
    private static final int TELECOM_RANK = 1;
    private static final int THREE_RESOURCES_MAPPED = 3;
    private static final int FIVE_RESOURCES_MAPPED = 5;

    @Test
    public void mapAgentDirectoryWithMultipleAgents() {
        var agentDirectory = unmarshallAgentDirectoryElement("multiple_agents_example.xml");

        var mappedAgents = agentDirectoryMapper.mapAgentDirectory(agentDirectory);

        // asserts that for the 3 varying Agents in the input, that there are correctly 5 mapped resources output
        assertEquals(FIVE_RESOURCES_MAPPED, mappedAgents.size());
    }

    @Test
    public void mapAgentDirectoryWithAgentPersonAndRepresentedOrganizationNoCode() {
        var agentDirectory = unmarshallAgentDirectoryElement("agent_person_and_represented_org_example.xml");

        var mappedAgents = agentDirectoryMapper.mapAgentDirectory(agentDirectory);

        assertEquals(THREE_RESOURCES_MAPPED, mappedAgents.size());

        var practitioner = (Practitioner) mappedAgents.getFirst();
        assertEquals("94F00D99-0601-4A8E-AD1D-1B564307B0A6", practitioner.getId());
        assertEquals(PRACT_META_PROFILE, practitioner.getMeta().getProfile().getFirst().getValue());
        assertEquals(NameUse.OFFICIAL, practitioner.getNameFirstRep().getUse());
        assertEquals("Test", practitioner.getNameFirstRep().getFamily());

        var organization = (Organization) mappedAgents.get(1);
        assertEquals("94F00D99-0601-4A8E-AD1D-1B564307B0A6-ORG", organization.getId());
        assertEquals(ORG_META_PROFILE, organization.getMeta().getProfile().getFirst().getValue());
        assertEquals("TEMPLE SOWERBY MEDICAL PRACTICE", organization.getName());
        assertThat(organization.getType()).isEmpty();

        var practitionerRole = (PractitionerRole) mappedAgents.get(2);
        assertEquals("94F00D99-0601-4A8E-AD1D-1B564307B0A6-PR", practitionerRole.getId());
        assertEquals(PRACT_ROLE_META_PROFILE, practitionerRole.getMeta().getProfile().getFirst().getValue());
        assertEquals("Practitioner/94F00D99-0601-4A8E-AD1D-1B564307B0A6", practitionerRole.getPractitioner().getReference());
        assertEquals("Organization/94F00D99-0601-4A8E-AD1D-1B564307B0A6-ORG", practitionerRole.getOrganization().getReference());
        assertThat(practitionerRole.getCode()).isEmpty();
    }

    @Test
    public void mapAgentDirectoryWithAgentPersonAndGeneralPractitionerNumber() {
        var agentDirectory = unmarshallAgentDirectoryElement("agent_org_with_general_practitioner_number.xml");

        var mappedAgents = agentDirectoryMapper.mapAgentDirectory(agentDirectory);
        var practitioner = (Practitioner) mappedAgents.getFirst();

        assertEquals("E7E7B550-09EF-BE85-C20F-34598014166C", practitioner.getId());
        assertThat(practitioner.getIdentifier()).isNotEmpty();
        assertEquals("https://fhir.hl7.org.uk/Id/gmp-number", practitioner.getIdentifier().getFirst().getSystem());
        assertEquals("12345", practitioner.getIdentifier().getFirst().getValue());
    }

    @Test
    public void mapAgentDirectoryWithAgentPersonAndRepresentedOrganizationWithOriginalText() {
        var agentDirectory = unmarshallAgentDirectoryElement("agent_person_and_represented_org_with_original_text_example.xml");

        var mappedAgents = agentDirectoryMapper.mapAgentDirectory(agentDirectory);

        assertEquals(THREE_RESOURCES_MAPPED, mappedAgents.size());

        var practitioner = (Practitioner) mappedAgents.getFirst();
        assertEquals("94F00D99-0601-4A8E-AD1D-1B564307B0A6", practitioner.getId());
        assertEquals(PRACT_META_PROFILE, practitioner.getMeta().getProfile().getFirst().getValue());
        assertEquals(NameUse.OFFICIAL, practitioner.getNameFirstRep().getUse());
        assertEquals("Test", practitioner.getNameFirstRep().getFamily());

        var organization = (Organization) mappedAgents.get(1);
        assertEquals("94F00D99-0601-4A8E-AD1D-1B564307B0A6-ORG", organization.getId());
        assertEquals(ORG_META_PROFILE, organization.getMeta().getProfile().getFirst().getValue());
        assertEquals("TEMPLE SOWERBY MEDICAL PRACTICE", organization.getName());
        assertThat(organization.getType()).isEmpty();

        var practitionerRole = (PractitionerRole) mappedAgents.get(2);
        assertEquals("94F00D99-0601-4A8E-AD1D-1B564307B0A6-PR", practitionerRole.getId());
        assertEquals(PRACT_ROLE_META_PROFILE, practitionerRole.getMeta().getProfile().getFirst().getValue());
        assertEquals("Practitioner/94F00D99-0601-4A8E-AD1D-1B564307B0A6", practitionerRole.getPractitioner().getReference());
        assertEquals("Organization/94F00D99-0601-4A8E-AD1D-1B564307B0A6-ORG", practitionerRole.getOrganization().getReference());
        assertEquals("Clerical Worker", practitionerRole.getCodeFirstRep().getText());
    }

    @Test
    public void mapAgentDirectoryWithAgentPersonAndRepresentedOrganizationWithDisplayName() {
        var agentDirectory = unmarshallAgentDirectoryElement("agent_person_and_represented_org_with_display_name_example.xml");

        var mappedAgents = agentDirectoryMapper.mapAgentDirectory(agentDirectory);

        assertEquals(THREE_RESOURCES_MAPPED, mappedAgents.size());

        var practitioner = (Practitioner) mappedAgents.getFirst();
        assertEquals("94F00D99-0601-4A8E-AD1D-1B564307B0A6", practitioner.getId());
        assertEquals(PRACT_META_PROFILE, practitioner.getMeta().getProfile().getFirst().getValue());
        assertEquals(NameUse.OFFICIAL, practitioner.getNameFirstRep().getUse());
        assertEquals("Test", practitioner.getNameFirstRep().getFamily());

        var organization = (Organization) mappedAgents.get(1);
        assertEquals("94F00D99-0601-4A8E-AD1D-1B564307B0A6-ORG", organization.getId());
        assertEquals(ORG_META_PROFILE, organization.getMeta().getProfile().getFirst().getValue());
        assertEquals("TEMPLE SOWERBY MEDICAL PRACTICE", organization.getName());
        assertThat(organization.getType()).isEmpty();

        var practitionerRole = (PractitionerRole) mappedAgents.get(2);
        assertEquals("94F00D99-0601-4A8E-AD1D-1B564307B0A6-PR", practitionerRole.getId());
        assertEquals(PRACT_ROLE_META_PROFILE, practitionerRole.getMeta().getProfile().getFirst().getValue());
        assertEquals("Practitioner/94F00D99-0601-4A8E-AD1D-1B564307B0A6", practitionerRole.getPractitioner().getReference());
        assertEquals("Organization/94F00D99-0601-4A8E-AD1D-1B564307B0A6-ORG", practitionerRole.getOrganization().getReference());
        assertEquals("General practice", practitionerRole.getCode().getFirst().getCoding().getFirst().getDisplay());
    }

    @Test
    public void mapAgentDirectoryOnlyAgentPersonNoOptionalFields() {
        var agentDirectory = unmarshallAgentDirectoryElement("agent_person_only_example.xml");

        var mappedAgents = agentDirectoryMapper.mapAgentDirectory(agentDirectory);

        assertThat(mappedAgents).hasSize(1);

        var practitioner = (Practitioner) mappedAgents.getFirst();
        assertEquals("95D00D99-0601-4A8E-AD1D-1B564307B0A6", practitioner.getId());
        assertEquals(PRACT_META_PROFILE, practitioner.getMeta().getProfile().getFirst().getValue());
        assertEquals(NameUse.OFFICIAL, practitioner.getNameFirstRep().getUse());
        assertEquals("Test", practitioner.getNameFirstRep().getFamily());
        assertThat(practitioner.getNameFirstRep().getGiven()).isEmpty();
        assertThat(practitioner.getNameFirstRep().getPrefix()).isEmpty();
    }

    @Test
    public void mapAgentDirectoryOnlyAgentPersonWithEmptyNameElement() {
        var agentDirectory = unmarshallAgentDirectoryElement("agent_person_only_no_name_example.xml");

        var mappedAgents = agentDirectoryMapper.mapAgentDirectory(agentDirectory);

        assertThat(mappedAgents).hasSize(1);

        var practitioner = (Practitioner) mappedAgents.getFirst();

        assertThat(practitioner.getId()).isEqualTo("95D00D99-0601-4A8E-AD1D-1B564307B0A6");
        assertThat(practitioner.getMeta().getProfile().getFirst().getValue()).isEqualTo(PRACT_META_PROFILE);
        assertThat(practitioner.getNameFirstRep().getUse()).isEqualTo(NameUse.OFFICIAL);
        assertThat(practitioner.getNameFirstRep().getFamily()).isEqualTo("Unknown");
        assertThat(practitioner.getNameFirstRep().getGiven()).isEmpty();
        assertThat(practitioner.getNameFirstRep().getPrefix()).isEmpty();
    }

    @Test
    public void When_MappingAgentDirectoryWithAgentWithoutNameElement_Expect_ExpectFamilyNameIsSetToUnknown() {
        var agentDirectoryXml = """
            <agentDirectory xmlns="urn:hl7-org:v3" classCode="AGNT">
                <part typeCode="PART">
                    <Agent classCode="AGNT">
                        <id root="6C8AC7E0-4D90-10C5-5FD3-F2C05C658E18" />
                            <code code="125676002" codeSystem="2.16.840.1.113883.2.1.3.2.4.15" displayName="Person">
                               <originalText>Other</originalText>
                            </code>
                        <agentPerson classCode="PSN" determinerCode="INSTANCE" />
                    </Agent>
                </part>
            </agentDirectory>""";
        var agentDirectory = unmarshallAgentDirectoryFromXmlString(agentDirectoryXml);

        var mappedAgents = agentDirectoryMapper.mapAgentDirectory(agentDirectory);
        var practitioner = (Practitioner) mappedAgents.getFirst();

        assertAll(
            () -> assertThat(practitioner.getNameFirstRep().getUse()).isEqualTo(NameUse.OFFICIAL),
            () -> assertThat(practitioner.getNameFirstRep().getFamily()).isEqualTo("Unknown"),
            () -> assertThat(practitioner.getNameFirstRep().getGiven()).isEmpty(),
            () -> assertThat(practitioner.getNameFirstRep().getPrefix()).isEmpty()
        );
    }

    @Test
    public void mapAgentDirectoryOnlyAgentPersonFullName() {
        var agentDirectory = unmarshallAgentDirectoryElement("agent_person_only_full_name_example.xml");

        var mappedAgents = agentDirectoryMapper.mapAgentDirectory(agentDirectory);

        assertThat(mappedAgents).hasSize(1);

        var practitioner = (Practitioner) mappedAgents.getFirst();
        assertThat(practitioner.getId()).isEqualTo("95D00D99-0601-4A8E-AD1D-1B564307B0A6");
        assertThat(practitioner.getMeta().getProfile().getFirst().getValue()).isEqualTo(PRACT_META_PROFILE);
        assertThat(practitioner.getNameFirstRep().getUse()).isEqualTo(NameUse.OFFICIAL);
        assertThat(practitioner.getNameFirstRep().getFamily()).isEqualTo("Test");
        assertThat(practitioner.getNameFirstRep().getGiven().getFirst().getValue()).isEqualTo("NHS");
        assertThat(practitioner.getNameFirstRep().getPrefix().getFirst().getValue()).isEqualTo("Mr");
    }

    @Test
    public void mapAgentDirectoryOnlyAgentOrganizationNoOptionalFields() {
        var agentDirectory = unmarshallAgentDirectoryElement("agent_org_only_example.xml");

        var mappedAgents = agentDirectoryMapper.mapAgentDirectory(agentDirectory);

        assertThat(mappedAgents).hasSize(1);

        var organization = (Organization) mappedAgents.getFirst();
        assertThat(organization.getId()).isEqualTo("1D9BDC28-50AB-440D-B421-0E5E049526FA");
        assertThat(organization.getMeta().getProfile().getFirst().getValue()).isEqualTo(ORG_META_PROFILE);
        assertThat(organization.getName()).isEqualTo("The Health Centre");
        assertThat(organization.getIdentifier()).isEmpty();
        assertThat(organization.getTelecom()).isEmpty();
        assertThat(organization.getAddress()).isEmpty();
    }

    @Test
    public void mapAgentDirectoryOnlyAgentOrganizationUnknownName() {
        var agentDirectory = unmarshallAgentDirectoryElement("agent_org_no_name_example.xml");

        var mappedAgents = agentDirectoryMapper.mapAgentDirectory(agentDirectory);

        assertThat(mappedAgents).hasSize(1);

        var organization = (Organization) mappedAgents.getFirst();
        assertThat(organization.getId()).isEqualTo("1D9BDC28-50AB-440D-B421-0E5E049526FA");
        assertThat(organization.getMeta().getProfile().getFirst().getValue()).isEqualTo(ORG_META_PROFILE);
        assertThat(organization.getName()).isEqualTo("Unknown");
        assertThat(organization.getIdentifier()).isEmpty();
        assertThat(organization.getTelecom()).isEmpty();
        assertThat(organization.getAddress()).isEmpty();
    }

    @Test
    public void mapAgentDirectoryOnlyAgentOrganizationWithValidIdentifier() {
        var agentDirectory = unmarshallAgentDirectoryElement("agent_org_valid_identifier_example.xml");

        var mappedAgents = agentDirectoryMapper.mapAgentDirectory(agentDirectory);

        assertEquals(1, mappedAgents.size());

        var organization = (Organization) mappedAgents.getFirst();
        assertEquals("1D9BDC28-50AB-440D-B421-0E5E049526FA", organization.getId());
        assertEquals(ORG_META_PROFILE, organization.getMeta().getProfile().getFirst().getValue());
        assertEquals("The Health Centre", organization.getName());
        assertEquals(ORG_IDENTIFIER_SYSTEM, organization.getIdentifierFirstRep().getSystem());
        assertEquals("A81001", organization.getIdentifierFirstRep().getValue());
        assertThat(organization.getTelecom()).isEmpty();
        assertThat(organization.getAddress()).isEmpty();
    }

    @Test
    public void mapAgentDirectoryOnlyAgentOrganizationWithInvalidIdentifier() {
        var agentDirectory = unmarshallAgentDirectoryElement("agent_org_invalid_identifier_example.xml");

        var mappedAgents = agentDirectoryMapper.mapAgentDirectory(agentDirectory);

        assertEquals(1, mappedAgents.size());

        var organization = (Organization) mappedAgents.getFirst();
        assertEquals("1D9BDC28-50AB-440D-B421-0E5E049526FA", organization.getId());
        assertEquals(ORG_META_PROFILE, organization.getMeta().getProfile().getFirst().getValue());
        assertEquals("The Health Centre", organization.getName());
        assertThat(organization.getIdentifier()).isEmpty();
        assertThat(organization.getTelecom()).isEmpty();
        assertThat(organization.getAddress()).isEmpty();
    }

    @Test
    public void mapAgentDirectoryOnlyAgentOrganizationWithNonWPAddressAndTelecom() {
        var agentDirectory = unmarshallAgentDirectoryElement("agent_org_non_wp_address_and_telecom_example.xml");

        var mappedAgents = agentDirectoryMapper.mapAgentDirectory(agentDirectory);

        assertEquals(1, mappedAgents.size());

        var organization = (Organization) mappedAgents.getFirst();
        assertEquals("1D9BDC28-50AB-440D-B421-0E5E049526FA", organization.getId());
        assertEquals(ORG_META_PROFILE, organization.getMeta().getProfile().getFirst().getValue());
        assertEquals("The Health Centre", organization.getName());
        assertThat(organization.getIdentifier()).isEmpty();
        assertTelecom(organization.getTelecomFirstRep(), "01234567890");
        assertThat(organization.getAddress()).isEmpty();
    }

    @Test
    public void mapAgentDirectoryOnlyAgentOrganizationWithWPTelecom() {
        var agentDirectory = unmarshallAgentDirectoryElement("agent_org_wp_telecom_example.xml");

        var mappedAgents = agentDirectoryMapper.mapAgentDirectory(agentDirectory);

        assertEquals(1, mappedAgents.size());

        var organization = (Organization) mappedAgents.getFirst();
        assertEquals("1D9BDC28-50AB-440D-B421-0E5E049526FA", organization.getId());
        assertEquals(ORG_META_PROFILE, organization.getMeta().getProfile().getFirst().getValue());
        assertEquals("The Health Centre", organization.getName());
        assertThat(organization.getIdentifier()).isEmpty();
        assertTelecom(organization.getTelecomFirstRep(), "01234567890");
        assertThat(organization.getAddress()).isEmpty();
    }

    @Test
    public void  mapAgentDirectoryOnlyAgentOrganizationWithWPAddress() {
        var agentDirectory = unmarshallAgentDirectoryElement("agent_org_wp_address_example.xml");

        var mappedAgents = agentDirectoryMapper.mapAgentDirectory(agentDirectory);

        assertEquals(1, mappedAgents.size());

        var organization = (Organization) mappedAgents.getFirst();
        assertEquals("1D9BDC28-50AB-440D-B421-0E5E049526FA", organization.getId());
        assertEquals(ORG_META_PROFILE, organization.getMeta().getProfile().getFirst().getValue());
        assertEquals("The Health Centre", organization.getName());
        assertThat(organization.getIdentifier()).isEmpty();
        assertThat(organization.getTelecom()).isEmpty();
        assertAddress(organization.getAddressFirstRep());
    }

    @Test
    public void When_AgentContainsCodeWithSnomedSystemCodeProvided_Expect_SnomedCodeSystemUrlIsMappedToSystem() {
        var inputXml = """
                    <agentDirectory xmlns="urn:hl7-org:v3" classCode="AGNT">
                        <part typeCode="PART">
                            <Agent classCode="AGNT">
                                <id root="94F00D99-0601-4A8E-AD1D-1B564307B0A6"/>
                                <code codeSystem="2.16.840.1.113883.2.1.3.2.4.15"
                                code="1234"
                                displayName="General practice" />
                                <agentPerson classCode="PSN" determinerCode="INSTANCE">
                                    <name>
                                        <family>Test</family>
                                    </name>
                                </agentPerson>
                                <representedOrganization classCode="ORG" determinerCode="INSTANCE">
                                    <name>TEMPLE SOWERBY MEDICAL PRACTICE</name>
                                </representedOrganization>
                            </Agent>
                        </part>
                    </agentDirectory>
                    """;
        var agentDirectory = unmarshallAgentDirectoryFromXmlString(inputXml);

        var mappedAgents = agentDirectoryMapper.mapAgentDirectory(agentDirectory);

        var practitionerRole = (PractitionerRole) mappedAgents.get(2);
        var coding = practitionerRole.getCode().getFirst().getCoding().getFirst();

        assertAll(
            () -> assertEquals("1234", coding.getCode()),
            () -> assertEquals("http://snomed.info/sct", coding.getSystem()),
            () -> assertEquals("General practice", coding.getDisplay())
        );
    }

    @Test
    public void When_AgentContainsCodeWithKnownNonSnomedSystemCodeProvided_Expect_CodeSystemUrlIsMappedToSystem() {
        // The MiM states that:
        //  > The codeSystem attribute will contain an OID with the value "2.16.840.1.113883.6.96";
        // We haven't seen any Read codes, but it seems prudent to map the codeSystem if we ever did see it.

        var inputXml = """
                    <agentDirectory xmlns="urn:hl7-org:v3" classCode="AGNT">
                        <part typeCode="PART">
                            <Agent classCode="AGNT">
                                <id root="94F00D99-0601-4A8E-AD1D-1B564307B0A6"/>
                                <code codeSystem="2.16.840.1.113883.2.1.6.2"
                                code="1234"
                                displayName="General practice" />
                                <agentPerson classCode="PSN" determinerCode="INSTANCE">
                                    <name>
                                        <family>Test</family>
                                    </name>
                                </agentPerson>
                                <representedOrganization classCode="ORG" determinerCode="INSTANCE">
                                    <name>TEMPLE SOWERBY MEDICAL PRACTICE</name>
                                </representedOrganization>
                            </Agent>
                        </part>
                    </agentDirectory>
                    """;
        var agentDirectory = unmarshallAgentDirectoryFromXmlString(inputXml);

        var agents = agentDirectoryMapper.mapAgentDirectory(agentDirectory);
        var practitionerRole = (PractitionerRole) agents.get(2);
        var coding = practitionerRole.getCode().getFirst().getCoding().getFirst();

        assertAll(
            () -> assertEquals("1234", coding.getCode()),
            () -> assertEquals("http://read.info/readv2", coding.getSystem()),
            () -> assertEquals("General practice", coding.getDisplay())
        );
    }

    @Test
    public void When_AgentContainsCodeWithNonSnomedSystemCodeProvided_Expect_SystemCodeIsMappedToSystem() {
        var inputXml = """
                    <agentDirectory xmlns="urn:hl7-org:v3" classCode="AGNT">
                        <part typeCode="PART">
                            <Agent classCode="AGNT">
                                <id root="E9F2B192-6DC7-11EE-9D98-00155D78C707" />
                                <code code="1234"
                                codeSystem="1.2.3.4.5.6"
                                displayName="Other person" />
                                <agentPerson classCode="PSN" determinerCode="INSTANCE">
                                    <name>
                                        <family>Test</family>
                                    </name>
                                </agentPerson>
                                <representedOrganization classCode="ORG" determinerCode="INSTANCE">
                                    <name>TEMPLE SOWERBY MEDICAL PRACTICE</name>
                                </representedOrganization>
                            </Agent>
                        </part>
                    </agentDirectory>
                    """;
        var agentDirectory = unmarshallAgentDirectoryFromXmlString(inputXml);

        var mappedAgents = agentDirectoryMapper.mapAgentDirectory(agentDirectory);

        var practitionerRole = (PractitionerRole) mappedAgents.get(2);
        var coding = practitionerRole.getCode().getFirst().getCoding().getFirst();

        assertAll(
            () -> assertEquals("1234", coding.getCode()),
            () -> assertEquals("urn:oid:1.2.3.4.5.6", coding.getSystem()),
            () -> assertEquals("Other person", coding.getDisplay())
        );
    }

    private void assertAddress(Address address) {
        assertEquals(AddressUse.WORK, address.getUse());
        assertEquals(AddressType.PHYSICAL, address.getType());
        assertEquals("234 ASHTREE ROAD", address.getLine().getFirst().getValue());
        assertEquals("LEEDS", address.getLine().get(1).getValue());
        assertEquals("YORKSHIRE", address.getLine().get(2).getValue());
        assertEquals("LS12 3RT", address.getPostalCode());
    }

    private void assertTelecom(ContactPoint telecom, String value) {
        assertEquals(ContactPointSystem.PHONE, telecom.getSystem());
        assertEquals(ContactPointUse.WORK, telecom.getUse());
        assertEquals(TELECOM_RANK, telecom.getRank());
        assertEquals(value, telecom.getValue());
    }

    @SneakyThrows
    private RCMRMT030101UKAgentDirectory unmarshallAgentDirectoryElement(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES_BASE + fileName), RCMRMT030101UKAgentDirectory.class);
    }

    @SneakyThrows
    private RCMRMT030101UKAgentDirectory unmarshallAgentDirectoryFromXmlString(String inputXml) {
        return unmarshallString(inputXml, RCMRMT030101UKAgentDirectory.class);
    }
}
