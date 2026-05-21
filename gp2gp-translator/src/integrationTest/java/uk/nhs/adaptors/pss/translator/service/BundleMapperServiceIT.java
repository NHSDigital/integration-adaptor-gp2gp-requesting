package uk.nhs.adaptors.pss.translator.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.util.ResourceUtils.getFile;

import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.DocumentReference;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Practitioner;
import org.hl7.fhir.dstu3.model.PractitionerRole;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.hl7.v3.RCMRIN030000UKMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import lombok.SneakyThrows;
import uk.nhs.adaptors.common.util.fhir.FhirParser;
import uk.nhs.adaptors.pss.translator.exception.BundleMappingException;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
@ExtendWith({SpringExtension.class, MockitoExtension.class})
@DirtiesContext
public class BundleMapperServiceIT {

    private static final String XML_RESOURCES_BASE = "xml/mapping/ehr-extract/";
    private static final String LOSING_PRACTICE_ODS_CODE = "A12345";

    @Autowired
    private BundleMapperService bundleMapperService;

    @Autowired
    private FhirParser fhirParser;

    @Test
    public void When_MappingBundle_With_ObservationsThatHaveDuplicateCodeableConcepts_Expect_ObservationRemoved()
        throws BundleMappingException {

        var ehrMessage = unmarshallEhrExtractFromFile("ehr-document-and-no-organisations.xml");

        var bundle = bundleMapperService.mapToBundle(ehrMessage, LOSING_PRACTICE_ODS_CODE, new ArrayList<>());
        var observations = extractObservationsFromBundle(bundle);

        assertThat(observations.size()).isOne();
    }

    @Test
    public void When_MappingBundle_With_RepresentedOrganisation_Expect_OrganisationMapped() throws BundleMappingException {
        var ehrMessage = unmarshallEhrExtractFromFile("ehr-single-represented-organisation.xml");

        var bundle = bundleMapperService.mapToBundle(ehrMessage, LOSING_PRACTICE_ODS_CODE, new ArrayList<>());
        var organisations = extractOrganisationsFromBundle(bundle);
        var practitioners = extractPractitionersFromBundle(bundle);
        var practitionerRoles = extractPractitionerRolesFromBundle(bundle);

        assertThat(organisations.size()).isOne();
        assertThat(practitioners.size()).isOne();
        assertThat(practitionerRoles.size()).isOne();

        var organisationId = organisations.getFirst().getId();
        var practitionerId = practitioners.getFirst().getId();
        var practitionerRole = practitionerRoles.getFirst();

        assertThat(practitionerRole.getPractitioner().getReferenceElement().getIdPart()).isEqualTo(practitionerId);
        assertThat(practitionerRole.getOrganization().getReferenceElement().getIdPart()).isEqualTo(organisationId);
    }

    @Test
    public void When_MappingBundle_With_DuplicateRepresentedOrganisations_Expect_DuplicateRemoved() throws BundleMappingException {
        var ehrMessage = unmarshallEhrExtractFromFile("ehr-duplicate-represented-organisations.xml");

        var bundle = bundleMapperService.mapToBundle(ehrMessage, LOSING_PRACTICE_ODS_CODE, new ArrayList<>());
        var organisations = extractOrganisationsFromBundle(bundle);
        var practitioners = extractPractitionersFromBundle(bundle);
        var practitionerRoles = extractPractitionerRolesFromBundle(bundle);

        assertThat(organisations.size()).isOne();
        assertThat(practitioners.size()).isEqualTo(2);
        assertThat(practitionerRoles.size()).isEqualTo(2);

        var organisationId = organisations.getFirst().getId();

        assertThat(practitionerRoles.getFirst().getOrganization().getReferenceElement().getIdPart())
            .isEqualTo(organisationId);

        assertThat(practitionerRoles.get(1).getOrganization().getReferenceElement().getIdPart())
            .isEqualTo(organisationId);
    }

    @Test
    public void When_MappingBundle_With_Documents_Expect_OrganisationAdded() throws BundleMappingException {
        var ehrMessage = unmarshallEhrExtractFromFile("ehr-document-and-no-organisations.xml");

        var bundle = bundleMapperService.mapToBundle(ehrMessage, LOSING_PRACTICE_ODS_CODE, new ArrayList<>());
        var organisations = extractOrganisationsFromBundle(bundle);
        var practitioners = extractPractitionersFromBundle(bundle);
        var practitionerRoles = extractPractitionerRolesFromBundle(bundle);
        var documentReferences = extractDocumentReferencesFromBundle(bundle);

        assertThat(organisations.size()).isOne();
        assertThat(practitioners.size()).isOne();
        assertThat(practitionerRoles.size()).isZero();
        assertThat(documentReferences.size()).isOne();

        var organisationId = organisations.getFirst()
            .getId();

        var custodianId = documentReferences.getFirst()
            .getCustodian()
            .getResource()
            .getIdElement()
            .getIdPart();

        assertThat(custodianId).isEqualTo(organisationId);

    }

    @Test
    public void When_MappingBundle_With_NoDocuments_Expect_OrganisationNotAdded() throws BundleMappingException {
        var ehrMessage = unmarshallEhrExtractFromFile("ehr-no-organisation-or-documents.xml");

        var bundle = bundleMapperService.mapToBundle(ehrMessage, LOSING_PRACTICE_ODS_CODE, new ArrayList<>());
        var organisations = extractOrganisationsFromBundle(bundle);
        var practitioners = extractPractitionersFromBundle(bundle);
        var practitionerRoles = extractPractitionerRolesFromBundle(bundle);
        var documentReferences = extractDocumentReferencesFromBundle(bundle);

        assertThat(organisations.size()).isZero();
        assertThat(practitioners.size()).isOne();
        assertThat(practitionerRoles.size()).isZero();
        assertThat(documentReferences.size()).isZero();
    }

    @Test
    public void When_MappingBundle_With_DocumentsAndMatchingRepresentedOrganisation_Expect_NoDuplicate() throws BundleMappingException {
        var ehrMessage = unmarshallEhrExtractFromFile("ehr-document-and-organisation.xml");

        var bundle = bundleMapperService.mapToBundle(ehrMessage, "A86005", new ArrayList<>());
        var organisations = extractOrganisationsFromBundle(bundle);
        var practitioners = extractPractitionersFromBundle(bundle);
        var practitionerRoles = extractPractitionerRolesFromBundle(bundle);
        var documentReferences = extractDocumentReferencesFromBundle(bundle);

        assertThat(organisations.size()).isOne();
        assertThat(practitioners.size()).isOne();
        assertThat(practitionerRoles.size()).isOne();
        assertThat(documentReferences.size()).isOne();

        var organisationId = organisations.getFirst()
            .getId();

        var custodianId = documentReferences.getFirst()
            .getCustodian()
            .getResource()
            .getIdElement()
            .getIdPart();

        assertThat(custodianId).isEqualTo(organisationId);

        assertThat(practitionerRoles.getFirst().getOrganization().getReferenceElement().getIdPart())
            .isEqualTo(organisationId);
        assertThat(practitionerRoles.getFirst().getPractitioner().getReferenceElement().getIdPart())
            .isEqualTo(practitioners.getFirst().getId());
    }

    @SneakyThrows
    private RCMRIN030000UKMessage unmarshallEhrExtractFromFile(String filename) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES_BASE + filename), RCMRIN030000UKMessage.class);
    }

    private List<Organization> extractOrganisationsFromBundle(Bundle bundle) {
        return bundle.getEntry().stream()
            .map(Bundle.BundleEntryComponent::getResource)
            .filter(resource -> resource.getResourceType().equals(ResourceType.Organization))
            .map(Organization.class::cast)
            .toList();
    }

    private List<Practitioner> extractPractitionersFromBundle(Bundle bundle) {
        return bundle.getEntry().stream()
            .map(Bundle.BundleEntryComponent::getResource)
            .filter(resource -> resource.getResourceType().equals(ResourceType.Practitioner))
            .map(Practitioner.class::cast)
            .toList();
    }

    private List<PractitionerRole> extractPractitionerRolesFromBundle(Bundle bundle) {
        return bundle.getEntry().stream()
            .map(Bundle.BundleEntryComponent::getResource)
            .filter(resource -> resource.getResourceType().equals(ResourceType.PractitionerRole))
            .map(PractitionerRole.class::cast)
            .toList();
    }

    private List<DocumentReference> extractDocumentReferencesFromBundle(Bundle bundle) {
        return bundle.getEntry().stream()
            .map(Bundle.BundleEntryComponent::getResource)
            .filter(resource -> resource.getResourceType().equals(ResourceType.DocumentReference))
            .map(DocumentReference.class::cast)
            .toList();
    }

    private List<Observation> extractObservationsFromBundle(Bundle bundle) {
        return bundle.getEntry().stream()
            .map(Bundle.BundleEntryComponent::getResource)
            .filter(resource -> resource.getResourceType().equals(ResourceType.Observation))
            .map(Observation.class::cast)
            .toList();
    }
}
