package uk.nhs.adaptors.pss.translator.mapper;

import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.buildIdentifier;
import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.generateMeta;

import lombok.RequiredArgsConstructor;
import org.hl7.fhir.dstu3.model.Address;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Location;
import org.hl7.fhir.dstu3.model.ContactPoint;
import org.hl7.v3.RCMRMT030101UKPlace;
import org.hl7.v3.RCMRMT030101UKLocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import uk.nhs.adaptors.pss.translator.service.IdGeneratorService;
import uk.nhs.adaptors.pss.translator.util.AddressUtil;
import uk.nhs.adaptors.pss.translator.util.TelecomUtil;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class LocationMapper {

    private static final String UNKNOWN_NAME = "Unknown";
    private static final String META_PROFILE = "Location-1";

    private final IdGeneratorService idGeneratorService;

    public Location mapToLocation(RCMRMT030101UKLocation location, String practiceCode) {
        var id = idGeneratorService.generateUuid().toUpperCase();
        var identifier = buildIdentifier(id, practiceCode);

        if (location.getLocatedEntity() != null && location.getLocatedEntity().getLocatedPlace() != null) {
            var locatedPlace = location.getLocatedEntity().getLocatedPlace();
            var name = getName(locatedPlace);
            var telecom = getLocationTelecom(locatedPlace);
            var address = getLocationAddress(locatedPlace);

            return createLocation(id, identifier, name, telecom, address);
        }

        return createLocation(id, identifier, null, null, null);
    }

    private String getName(RCMRMT030101UKPlace locatedPlace) {
        var name = locatedPlace.getName();
        return name != null ? name : UNKNOWN_NAME;
    }

    private ContactPoint getLocationTelecom(RCMRMT030101UKPlace locatedPlace) {
        var telecom = locatedPlace.getTelecom().stream().findFirst();
        if (telecom.isPresent()) {
            return TelecomUtil.mapTelecom(telecom.get());
        }

        return null;
    }

    private Address getLocationAddress(RCMRMT030101UKPlace locatedPlace) {
        var address = locatedPlace.getAddr();
        if (address != null) {
            return AddressUtil.mapAddress(address);
        }

        return null;
    }

    private Location createLocation(String id, Identifier identifier, String name, ContactPoint telecom, Address address) {
        var location = new Location();
        location.getIdentifier().add(identifier);
        location.setMeta(generateMeta(META_PROFILE));
        location.getTelecom().add(telecom);
        location.setName(name)
                .setAddress(address)
                .setStatus(Location.LocationStatus.ACTIVE)
                .setId(id);
        return location;
    }
}
