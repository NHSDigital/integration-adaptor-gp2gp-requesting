package uk.nhs.adaptors.pss.translator.util;

import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Resource;
import org.hl7.fhir.dstu3.model.UriType;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

public final class MetaUtil {

    private MetaUtil() { }
    private static final Coding NOPAT_CODING = new Coding()
        .setSystem("http://hl7.org/fhir/v3/ActCode")
        .setCode("NOPAT")
        .setDisplay("no disclosure to patient, family or caregivers without attending provider's authorization");

    public static Meta getMetaFor(final MetaType metaType, final String profile) {
        return switch (metaType) {
            case META_WITHOUT_SECURITY -> getBaseMeta(profile);
            case META_WITH_SECURITY -> getMetaWithSecurity(profile);
        };
    }

    public static Coding getNopatCoding() {
        return NOPAT_CODING;
    }

    private static Meta getBaseMeta(final String profile) {
        return new Meta().setProfile(
            Collections.singletonList(
                new UriType(profile)
            )
        );
    }

    private static Meta getMetaWithSecurity(final String profile) {
        return getBaseMeta(profile).addSecurity(NOPAT_CODING);
    }

    public enum MetaType {
        META_WITH_SECURITY,
        META_WITHOUT_SECURITY
    }

    public static void assertMetaSecurityIsPresent(Meta initialMetaWithSecurityAdded, final Meta meta) {
        final List<Coding> metaSecurity = meta.getSecurity();
        final int metaSecuritySize = metaSecurity.size();

        assertAll(
            () -> assertThat(metaSecuritySize).isEqualTo(1),
            () -> assertThat(meta).usingRecursiveComparison().isEqualTo(initialMetaWithSecurityAdded)
        );
    }

    public static void assertMetaSecurityNotPresent(final Resource resource, String metaProfile) {
        final Meta meta = resource.getMeta();

        assertMetaSecurityIsNotPresent(meta, metaProfile);
    }

    public static void assertMetaSecurityIsNotPresent(final Meta meta, String metaProfile) {
        assertAll(
            () -> assertThat(meta.getSecurity()).isEmpty(),
            () -> assertThat(meta.getProfile().getFirst().getValue()).isEqualTo(metaProfile)
        );
    }

}