package uk.nhs.adaptors.pss.translator.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.util.ResourceUtils.getFile;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import org.hl7.fhir.dstu3.model.ContactPoint;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Location;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.junit.jupiter.api.Test;

import lombok.SneakyThrows;
import uk.nhs.adaptors.pss.translator.service.IdGeneratorService;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocationMapperTest {

    private static final String XML_RESOURCES_BASE = "xml/Location/";
    private static final String META_PROFILE = "https://fhir.nhs.uk/STU3/StructureDefinition/CareConnect-GPC-Location-1";
    private static final String PRACTISE_CODE = "TESTPRACTISECODE";
    private static final String IDENTIFIER_SYSTEM = "https://PSSAdaptor/TESTPRACTISECODE";
    private static final String UNKNOWN_NAME = "Unknown";
    private static final int TELECOM_RANK = 1;
    private static final int TEL_PREFIX_INT = 4;

    private final LocationMapper locationMapper = new LocationMapper(new IdGeneratorService());

    @Test
    public void mapLocationWithValidData() {
        var ehrComposition = unmarshallCodeElement("full_valid_location_example.xml");

        Location location = locationMapper.mapToLocation(ehrComposition.getLocation(), PRACTISE_CODE);

        assertThatIdentifierIsValid(location.getIdentifierFirstRep(), location.getId());
        assertThat(location.getStatus()).isEqualTo(Location.LocationStatus.ACTIVE);
        assertThat(location.getMeta().getProfile().getFirst().getValue()).isEqualTo(META_PROFILE);
        assertThat(location.getName()).isEqualTo(ehrComposition.getLocation().getLocatedEntity().getLocatedPlace().getName());
        assertTelecom(location.getTelecomFirstRep(), ehrComposition.getLocation().getLocatedEntity().getLocatedPlace()
                .getTelecom().getFirst().getValue().substring(TEL_PREFIX_INT));
        assertThat(location.getAddress().getLine().toString()).hasToString(ehrComposition.getLocation().getLocatedEntity()
                .getLocatedPlace().getAddr().getStreetAddressLine().toString());
        assertThat(location.getAddress().getPostalCode()).isEqualTo(ehrComposition.getLocation().getLocatedEntity()
                .getLocatedPlace().getAddr().getPostalCode());
    }

    @Test
    public void mapLocationWithNoLocatedEntity() {
        var ehrComposition = unmarshallCodeElement("no_located_entity_example.xml");

        Location location = locationMapper.mapToLocation(ehrComposition.getLocation(), PRACTISE_CODE);

        assertThatIdentifierIsValid(location.getIdentifierFirstRep(), location.getId());
        assertThat(location.getStatus()).isEqualTo(Location.LocationStatus.ACTIVE);
        assertThat(location.getMeta().getProfile().getFirst().getValue()).isEqualTo(META_PROFILE);
    }

    @Test
    public void mapLocationWithNoLocatedPlace() {
        var ehrComposition = unmarshallCodeElement("no_located_place_example.xml");

        Location location = locationMapper.mapToLocation(ehrComposition.getLocation(), PRACTISE_CODE);

        assertThatIdentifierIsValid(location.getIdentifierFirstRep(), location.getId());
        assertThat(location.getStatus()).isEqualTo(Location.LocationStatus.ACTIVE);
        assertThat(location.getMeta().getProfile().getFirst().getValue()).isEqualTo(META_PROFILE);
    }

    @Test
    public void mapLocationWithKnownName() {
        var ehrComposition = unmarshallCodeElement("known_name_example.xml");

        Location location = locationMapper.mapToLocation(ehrComposition.getLocation(), PRACTISE_CODE);

        assertThatIdentifierIsValid(location.getIdentifierFirstRep(), location.getId());
        assertThat(location.getStatus()).isEqualTo(Location.LocationStatus.ACTIVE);
        assertThat(location.getMeta().getProfile().getFirst().getValue()).isEqualTo(META_PROFILE);
        assertThat(location.getName()).isEqualTo(ehrComposition.getLocation().getLocatedEntity().getLocatedPlace().getName());
    }

    @Test
    public void mapLocationWithUnknownName() {
        var ehrComposition = unmarshallCodeElement("unknown_name_example.xml");

        Location location = locationMapper.mapToLocation(ehrComposition.getLocation(), PRACTISE_CODE);

        assertThatIdentifierIsValid(location.getIdentifierFirstRep(), location.getId());
        assertThat(location.getStatus()).isEqualTo(Location.LocationStatus.ACTIVE);
        assertThat(location.getMeta().getProfile().getFirst().getValue()).isEqualTo(META_PROFILE);
        assertThat(location.getName()).isEqualTo(UNKNOWN_NAME);
    }

    @Test
    public void mapLocationWithTelecomUseWP() {
        var ehrComposition = unmarshallCodeElement("wp_telecom_example.xml");

        Location location = locationMapper.mapToLocation(ehrComposition.getLocation(), PRACTISE_CODE);

        assertThatIdentifierIsValid(location.getIdentifierFirstRep(), location.getId());
        assertThat(location.getStatus()).isEqualTo(Location.LocationStatus.ACTIVE);
        assertThat(location.getMeta().getProfile().getFirst().getValue()).isEqualTo(META_PROFILE);
        assertThat(location.getName()).isEqualTo(ehrComposition.getLocation().getLocatedEntity().getLocatedPlace().getName());
        assertTelecom(location.getTelecomFirstRep(), ehrComposition.getLocation().getLocatedEntity().getLocatedPlace()
                .getTelecom().getFirst().getValue().substring(TEL_PREFIX_INT));
    }

    @Test
    public void mapLocationWithTelecomNoWP() {
        var ehrComposition = unmarshallCodeElement("not_wp_telecom_example.xml");

        Location location = locationMapper.mapToLocation(ehrComposition.getLocation(), PRACTISE_CODE);

        assertThatIdentifierIsValid(location.getIdentifierFirstRep(), location.getId());
        assertThat(location.getStatus()).isEqualTo(Location.LocationStatus.ACTIVE);
        assertThat(location.getMeta().getProfile().getFirst().getValue()).isEqualTo(META_PROFILE);
        assertThat(location.getName()).isEqualTo(ehrComposition.getLocation().getLocatedEntity().getLocatedPlace().getName());
        assertTelecom(location.getTelecomFirstRep(), ehrComposition.getLocation().getLocatedEntity().getLocatedPlace()
                .getTelecom().getFirst().getValue());
    }

    @Test
    public void mapLocationWithAddressAndPostcode() {
        var ehrComposition = unmarshallCodeElement("address_with_postcode_example.xml");

        Location location = locationMapper.mapToLocation(ehrComposition.getLocation(), PRACTISE_CODE);

        assertThatIdentifierIsValid(location.getIdentifierFirstRep(), location.getId());
        assertThat(location.getStatus()).isEqualTo(Location.LocationStatus.ACTIVE);
        assertThat(location.getMeta().getProfile().getFirst().getValue()).isEqualTo(META_PROFILE);
        assertThat(location.getName()).isEqualTo(ehrComposition.getLocation().getLocatedEntity().getLocatedPlace().getName());
        assertThat(location.getAddress().getLine().toString()).hasToString(ehrComposition.getLocation().getLocatedEntity()
                .getLocatedPlace().getAddr().getStreetAddressLine().toString());
        assertThat(location.getAddress().getPostalCode()).isEqualTo(ehrComposition.getLocation().getLocatedEntity()
                .getLocatedPlace().getAddr().getPostalCode());
    }

    @Test
    public void mapLocationWithAddressNoPostCode() {
        var ehrComposition = unmarshallCodeElement("address_without_postcode_example.xml");

        Location location = locationMapper.mapToLocation(ehrComposition.getLocation(), PRACTISE_CODE);

        assertThatIdentifierIsValid(location.getIdentifierFirstRep(), location.getId());
        assertThat(location.getStatus()).isEqualTo(Location.LocationStatus.ACTIVE);
        assertThat(location.getMeta().getProfile().getFirst().getValue()).isEqualTo(META_PROFILE);
        assertThat(location.getName()).isEqualTo(ehrComposition.getLocation().getLocatedEntity().getLocatedPlace().getName());
        assertThat(location.getAddress().getLine().toString()).hasToString(ehrComposition.getLocation().getLocatedEntity()
                .getLocatedPlace().getAddr().getStreetAddressLine().toString());
        assertNull(location.getAddress().getPostalCode());
    }

    @Test
    public void mapLocationWithAddressNoWP() {
        var ehrComposition = unmarshallCodeElement("address_without_wp_example.xml");

        Location location = locationMapper.mapToLocation(ehrComposition.getLocation(), PRACTISE_CODE);

        assertThatIdentifierIsValid(location.getIdentifierFirstRep(), location.getId());
        assertThat(location.getStatus()).isEqualTo(Location.LocationStatus.ACTIVE);
        assertThat(location.getMeta().getProfile().getFirst().getValue()).isEqualTo(META_PROFILE);
        assertThat(location.getName()).isEqualTo(ehrComposition.getLocation().getLocatedEntity().getLocatedPlace().getName());
        assertTrue(location.getAddress().isEmpty());
    }

    private void assertThatIdentifierIsValid(Identifier identifier, String id) {
        assertThat(identifier.getSystem()).isEqualTo(IDENTIFIER_SYSTEM);
        var regex = "^[0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12}$";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(id);

        assertTrue(id != null && matcher.matches()); //add a regular expression here
    }

    private void assertTelecom(ContactPoint mappedTelecom, String value) {
        assertThat(mappedTelecom.getSystem()).isEqualTo(ContactPoint.ContactPointSystem.PHONE);
        assertThat(mappedTelecom.getUse()).isEqualTo(ContactPoint.ContactPointUse.WORK);
        assertThat(mappedTelecom.getRank()).isEqualTo(TELECOM_RANK);
        assertThat(mappedTelecom.getValue()).isEqualTo(value);
    }

    @SneakyThrows
    private RCMRMT030101UKEhrComposition unmarshallCodeElement(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES_BASE + fileName), RCMRMT030101UKEhrComposition.class);
    }
}
