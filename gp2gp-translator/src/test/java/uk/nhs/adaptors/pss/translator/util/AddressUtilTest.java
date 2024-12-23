package uk.nhs.adaptors.pss.translator.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.util.ResourceUtils.getFile;

import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import org.hl7.fhir.dstu3.model.Address;
import org.hl7.v3.AD;
import org.junit.jupiter.api.Test;

import lombok.SneakyThrows;

public class AddressUtilTest {
    private static final String XML_RESOURCES_BASE = "xml/Address/";

    @Test
    public void mapWpAddressWithAllFields() {
        var inputAddress = unmarshallAddressElement("full_address.xml");

        var address = AddressUtil.mapAddress(inputAddress);

        assertThat(address.getUse()).isEqualTo(Address.AddressUse.WORK);
        assertThat(address.getType()).isEqualTo(Address.AddressType.PHYSICAL);
        assertThat(address.getLine().getFirst().getValue()).isEqualTo("234 ASHTREE ROAD");
        assertThat(address.getLine().get(1).getValue()).isEqualTo("LEEDS");
        assertThat(address.getLine().get(2).getValue()).isEqualTo("YORKSHIRE");
        assertThat(address.getPostalCode()).isEqualTo("LS12 3RT");
    }

    @Test
    public void mapWpAddressWithNoPostcode() {
        var inputAddress = unmarshallAddressElement("no_postcode_address.xml");

        var address = AddressUtil.mapAddress(inputAddress);

        assertThat(address.getUse()).isEqualTo(Address.AddressUse.WORK);
        assertThat(address.getType()).isEqualTo(Address.AddressType.PHYSICAL);
        assertThat(address.getLine().getFirst().getValue()).isEqualTo("234 ASHTREE ROAD");
        assertThat(address.getLine().get(1).getValue()).isEqualTo("LEEDS");
        assertThat(address.getLine().get(2).getValue()).isEqualTo("YORKSHIRE");
        assertThat(address.getPostalCode()).isNull();
    }

    @Test
    public void mapWpAddressWithNoDetails() {
        var inputAddress = unmarshallAddressElement("no_details_address.xml");

        var address = AddressUtil.mapAddress(inputAddress);

        assertThat(address.getUse()).isEqualTo(Address.AddressUse.WORK);
        assertThat(address.getType()).isEqualTo(Address.AddressType.PHYSICAL);
        assertThat(address.getLine()).isEmpty();
        assertThat(address.getPostalCode()).isNull();
    }

    @Test
    public void mapNonWpAddress() {
        var inputAddress = unmarshallAddressElement("non_wp_address.xml");

        var address = AddressUtil.mapAddress(inputAddress);

        assertThat(address).isNull();
    }

    @SneakyThrows
    private AD unmarshallAddressElement(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES_BASE + fileName), AD.class);
    }
}
