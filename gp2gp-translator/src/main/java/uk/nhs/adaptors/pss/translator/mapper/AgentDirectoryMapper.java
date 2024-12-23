package uk.nhs.adaptors.pss.translator.mapper;

import static org.apache.logging.log4j.util.Strings.isNotEmpty;
import static uk.nhs.adaptors.pss.translator.util.OrganizationUtil.findDuplicateOrganisation;
import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.generateMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.Address;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.ContactPoint;
import org.hl7.fhir.dstu3.model.DomainResource;
import org.hl7.fhir.dstu3.model.HumanName;
import org.hl7.fhir.dstu3.model.HumanName.NameUse;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Practitioner;
import org.hl7.fhir.dstu3.model.PractitionerRole;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.StringType;
import org.hl7.v3.AD;
import org.hl7.v3.CV;
import org.hl7.v3.II;
import org.hl7.v3.PN;
import org.hl7.v3.RCCTMT120101UK01Agent;
import org.hl7.v3.RCCTMT120101UK01Organization;
import org.hl7.v3.RCCTMT120101UK01Person;
import org.hl7.v3.RCMRMT030101UKPart;
import org.hl7.v3.RCMRMT030101UKAgentDirectory;
import org.hl7.v3.TEL;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import uk.nhs.adaptors.pss.translator.util.AddressUtil;
import uk.nhs.adaptors.pss.translator.util.CodeSystemsUtil;
import uk.nhs.adaptors.pss.translator.util.TelecomUtil;
import static uk.nhs.adaptors.common.util.CodeableConceptUtils.createCodeableConcept;

@Service
public class AgentDirectoryMapper {
    private static final String PRACTITIONER_META_PROFILE = "Practitioner-1";
    private static final String ORG_META_PROFILE = "Organization-1";
    private static final String PRACTITIONER_ROLE_META_PROFILE = "PractitionerRole-1";
    private static final String ORG_IDENTIFIER_SYSTEM = "https://fhir.nhs.uk/Id/ods-organization-code";
    private static final String ORG_PREFIX = "Organization/";
    private static final String PRACTITIONER_PREFIX = "Practitioner/";
    private static final String ORG_ID_SUFFIX = "-ORG";
    private static final String PRACTITIONER_ROLE_SUFFIX = "-PR";
    private static final String ORG_ROOT = "2.16.840.1.113883.2.1.4.3";
    private static final String UNKNOWN = "Unknown";
    private static final String GMP_NUMBER_SYSTEM_CODE = "https://fhir.hl7.org.uk/Id/gmp-number";
    public List<? extends DomainResource> mapAgentDirectory(RCMRMT030101UKAgentDirectory agentDirectory) {
        var partList = agentDirectory.getPart();
        if (!CollectionUtils.isEmpty(partList)) {

            List<DomainResource> agentResources = Collections.synchronizedList(new ArrayList<>());

            partList.stream()
                .map(RCMRMT030101UKPart::getAgent)
                .forEach(agent -> mapAgent(agent, agentResources));

            return agentResources;
        }

        return List.of();
    }

    private void mapAgent(RCCTMT120101UK01Agent agent, List<DomainResource> agentResourceList) {
        var agentPerson = agent.getAgentPerson();
        var agentOrganization = agent.getAgentOrganization();
        var representedOrganization = agent.getRepresentedOrganization();
        var resourceId = agent.getId().getFirst().getRoot();
        var gpNumber = agent.getId().size() > 1 ? agent.getId().get(1).getExtension() : "";

        if (agentPerson != null && representedOrganization != null) {
            agentResourceList.add(createPractitioner(agentPerson, resourceId, gpNumber));

            var representedOrganisation = createRepresentedOrganization(representedOrganization, resourceId);
            Optional<Organization> duplicateOrganisation = findDuplicateOrganisation(representedOrganisation, agentResourceList);

            if (duplicateOrganisation.isEmpty()) {
                agentResourceList.add(representedOrganisation);
                agentResourceList.add(createPractitionerRole(resourceId, agent.getCode(), representedOrganisation.getId()));
            } else {
                agentResourceList.add(createPractitionerRole(resourceId, agent.getCode(), duplicateOrganisation.orElseThrow().getId()));
            }
        } else if (agentPerson != null && agentOrganization == null) {
            agentResourceList.add(createPractitioner(agentPerson, resourceId, gpNumber));
        } else if (agentPerson == null && agentOrganization != null) {
            agentResourceList.add(createAgentOrganization(agentOrganization, resourceId, agent.getCode()));
        }
    }

    private Practitioner createPractitioner(RCCTMT120101UK01Person agentPerson, String id, String gpNumber) {
        var practitioner = new Practitioner();

        practitioner.setId(id);
        practitioner.setMeta(generateMeta(PRACTITIONER_META_PROFILE));
        practitioner.setName(getPractitionerName(agentPerson.getName()));

        if (isNotEmpty(gpNumber)) {
            Identifier identifier = new Identifier()
                .setSystem(GMP_NUMBER_SYSTEM_CODE)
                .setValue(gpNumber);
            practitioner.setIdentifier(List.of(identifier));
        }

        return practitioner;
    }

    private List<HumanName> getPractitionerName(PN name) {
        var nameList = new ArrayList<HumanName>();
        var humanName = new HumanName().setUse(NameUse.OFFICIAL);
        nameList.add(humanName);

        if (name == null) {
            humanName.setFamily(UNKNOWN);
            return nameList;
        }

        humanName.setFamily(getPractitionerFamily(name.getFamily()));

        var given = getPractitionerGiven(name.getGiven());
        if (given != null) {
            humanName.getGiven().add(given);
        }

        var prefix = getPractitionerPrefix(name.getPrefix());
        if (prefix != null) {
            humanName.getPrefix().add(prefix);
        }

        return nameList;
    }

    private String getPractitionerFamily(String family) {
        return StringUtils.isNotEmpty(family) ? family : UNKNOWN;
    }

    private StringType getPractitionerGiven(String given) {
        return StringUtils.isNotEmpty(given) ? new StringType(given) : null;
    }

    private StringType getPractitionerPrefix(String prefix) {
        return StringUtils.isNotEmpty(prefix) ? new StringType(prefix) : null;
    }

    private Organization createRepresentedOrganization(RCCTMT120101UK01Organization representedOrg, String id) {
        var organization = new Organization();

        organization
            .setName(getOrganizationName(representedOrg.getName()))
            .setId(id + ORG_ID_SUFFIX);
        organization.setMeta(generateMeta(ORG_META_PROFILE));
        var identifier = getOrganizationIdentifier(representedOrg.getId());
        if (identifier.isPresent()) {
            organization.getIdentifier().add(identifier.orElseThrow());
        }

        return addOrganisationAddressAndTelecomIfPresent(representedOrg, organization);
    }

    private Organization createAgentOrganization(RCCTMT120101UK01Organization agentOrg, String id, CV code) {
        var organization = new Organization();
        organization
            .setName(getOrganizationName(agentOrg.getName()))
            .setId(id);
        organization.setMeta(generateMeta(ORG_META_PROFILE));

        var identifier = getOrganizationIdentifier(agentOrg.getId());
        if (identifier.isPresent()) {
            organization.getIdentifier().add(identifier.orElseThrow());
        }

        var text = getText(code);
        if (text != null) {
            organization.getType().add(text);
        }

        return addOrganisationAddressAndTelecomIfPresent(agentOrg, organization);
    }

    private Organization addOrganisationAddressAndTelecomIfPresent(RCCTMT120101UK01Organization agentOrg, Organization organization) {
        var address = getOrganizationAddress(agentOrg.getAddr());
        if (address != null) {
            organization.getAddress().add(address);
        }

        var telecom = getOrganizationTelecom(agentOrg.getTelecom());
        if (telecom != null) {
            organization.getTelecom().add(telecom);
        }

        return organization;
    }

    private ContactPoint getOrganizationTelecom(List<TEL> telecomList) {
        if (!telecomList.isEmpty()) {
            return TelecomUtil.mapTelecom(telecomList.getFirst());
        }

        return null;
    }

    private Address getOrganizationAddress(List<AD> addressList) {
        if (!addressList.isEmpty()) {
            return AddressUtil.mapAddress(addressList.getFirst());
        }

        return null;
    }

    private String getOrganizationName(String name) {
        return name != null ? name : UNKNOWN;
    }

    /**
     * @param code <a href="https://data.developer.nhs.uk/dms/mim/4.2.00/Domains/CMETs/Tabular%20View/RCCT_HD120100UK01-NoEdit.htm#Agent">See the code field of Agent in MiM</a>
     */
    private CodeableConcept getText(CV code) {
        /* It is unclear if it is valid to have a code without a code system, an assumption has been made for NIAD-2989
           that in the case of missing code system we will leave the codeSystem blank.
           This may need to be revisited if this is not the case.

           See example in gp2gp-translator/src/integrationTest/resources/json/LargeMessage/Scenario_5/uk06.json
             <code code="309394004" displayName="General Practitioner Principal">
        */

        if (code != null) {
            var codeSystem = code.hasCodeSystem()
                    ? CodeSystemsUtil.getFhirCodeSystem(code.getCodeSystem())
                    : null;

            if (StringUtils.isNotEmpty(code.getOriginalText())) {
                return createCodeableConcept(
                        code.getCode(),
                        codeSystem,
                        code.getDisplayName(),
                        code.getOriginalText());

            } else if (StringUtils.isNotEmpty(code.getDisplayName())) {
                return createCodeableConcept(
                        code.getCode(),
                        codeSystem,
                        code.getDisplayName());
            }
        }

        return null;
    }

    private Optional<Identifier> getOrganizationIdentifier(II id) {
        var identifier = new Identifier();
        if (isValidIdentifier(id)) {
            return Optional.of(identifier
                .setSystem(ORG_IDENTIFIER_SYSTEM)
                .setValue(id.getExtension())
            );
        }

        return Optional.empty();
    }

    private boolean isValidIdentifier(II id) {
        return id != null && id.getRoot() != null && id.getExtension() != null && ORG_ROOT.equals(id.getRoot());
    }

    private PractitionerRole createPractitionerRole(String id, CV code, String organisationId) {
        var practitionerRole = new PractitionerRole();

        practitionerRole.setId(id + PRACTITIONER_ROLE_SUFFIX);
        practitionerRole.setMeta(generateMeta(PRACTITIONER_ROLE_META_PROFILE));
        practitionerRole.setPractitioner(new Reference(PRACTITIONER_PREFIX + id));
        practitionerRole.setOrganization(new Reference(ORG_PREFIX + organisationId));

        var text = getText(code);
        if (text != null) {
            practitionerRole.getCode().add(getText(code));
        }

        return practitionerRole;
    }
}
