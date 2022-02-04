package uk.nhs.adaptors.pss.translator.util;

import org.hl7.fhir.dstu3.model.Address;
import org.hl7.v3.AD;

import io.micrometer.core.instrument.util.StringUtils;

public class AddressUtil {
    private static final String WORK_PLACE = "WP";

    public static Address getAddress(AD address) {
        if (isValidAddress(address)) {
            var mappedAddress = new Address();

            if (address.getStreetAddressLine() != null) {
                address.getStreetAddressLine()
                    .forEach(addressLine -> mappedAddress.addLine(addressLine));
            }
            if (StringUtils.isNotEmpty(address.getPostalCode())) {
                mappedAddress.setPostalCode(address.getPostalCode());
            }

            return mappedAddress
                .setUse(Address.AddressUse.WORK)
                .setType(Address.AddressType.PHYSICAL);
        }
        return null;
    }

    private static boolean isValidAddress(AD address) {
        if (address != null && address.getUse() != null) {
            var use = address.getUse().stream().findFirst();
            return use.isPresent() && use.stream().anyMatch(addressUse -> WORK_PLACE.equals(addressUse.value()));
        }

        return false;
    }
}
