package uk.nhs.adaptors.pss.translator.mapper;

import static java.util.UUID.randomUUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import org.hl7.fhir.dstu3.model.Organization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import uk.nhs.adaptors.pss.translator.service.IdGeneratorService;

@ExtendWith(MockitoExtension.class)
public class OrganizationMapperTest {

    private static final String ID = randomUUID().toString();
    private static final String ODS_CODE = "A5785";
    private static final String ODS_SYSTEM = "https://fhir.nhs.uk/Id/ods-organization-code";
    private static final String CARE_CONNECT_PROFILE_ORG = "https://fhir.nhs.uk/STU3/StructureDefinition/CareConnect-GPC-Organization-1";

    @Mock
    private IdGeneratorService idGenerator;

    @InjectMocks
    private OrganizationMapper organizationMapper;

    @Test
    public void mapAuthorOrganization() {
        when(idGenerator.generateUuid()).thenReturn(ID);

        Organization organization = organizationMapper.mapAuthorOrganization(ODS_CODE, new ArrayList<>());

        assertThat(organization.getId()).isEqualTo(ID);
        assertThat(organization.getIdentifierFirstRep().getSystem()).isEqualTo(ODS_SYSTEM);
        assertThat(organization.getIdentifierFirstRep().getValue()).isEqualTo(ODS_CODE);
        assertThat(organization.getMeta().getProfile().getFirst().getValue()).isEqualTo(CARE_CONNECT_PROFILE_ORG);
    }
}
