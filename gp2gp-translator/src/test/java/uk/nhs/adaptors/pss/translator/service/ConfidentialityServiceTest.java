package uk.nhs.adaptors.pss.translator.service;

import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Meta;

import org.hl7.v3.CV;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.nhs.adaptors.pss.translator.TestUtility;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static uk.nhs.adaptors.pss.translator.util.MetaUtil.assertMetaSecurityIsNotPresent;

class ConfidentialityServiceTest {

    private static final String DUMMY_PROFILE = "Test-MyProfile-1";
    private static final String DUMMY_PROFILE_URI = "https://fhir.nhs.uk/STU3/StructureDefinition/CareConnect-GPC-%s".formatted(DUMMY_PROFILE);

    private static final CV ALTERNATIVE_CV = TestUtility.createCv(
        "NOSCRUB",
        "http://hl7.org/fhir/v3/FakeCode",
        "no scrubbing of the patient, family or caregivers without attending provider's authorization"
    );

    private static final CV NOPAT_CV = TestUtility.createCv(
        "NOPAT",
        "http://hl7.org/fhir/v3/ActCode",
        "no disclosure to patient, family or caregivers without attending provider's authorization"
    );

    private static ConfidentialityService confidentialityService;

    @BeforeAll
    static void beforeAll() {
        confidentialityService = new ConfidentialityService();
    }

    @Test
    void When_CreateMetaAndAddSecurityIfConfidentialityCodesPresent_With_ValidMetaProfile_Expect_MetaWithoutSecurity() {
        final Meta result = confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            DUMMY_PROFILE
        );

        assertMetaSecurityIsNotPresent(result, DUMMY_PROFILE_URI);
    }

    @Test
    void When_CreateMetaAndAddSecurityIfConfidentialityCodesPresent_With_ValidMetaProfileAndNopatCv_Expect_MetaWithSecurity() {
        final Meta result = confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            DUMMY_PROFILE,
            Optional.of(NOPAT_CV)
        );

        assertMetaSecurityIsPresent(result);
    }

    @Test
    void When_CreateMetaAndAddSecurityIfConfidentialityCodesPresent_With_ValidMetaProfileAndAlternativeCv_Expect_MetaWithoutSecurity() {
        final Meta result = confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            DUMMY_PROFILE,
            Optional.of(ALTERNATIVE_CV)
        );

        assertMetaSecurityIsNotPresent(result, DUMMY_PROFILE_URI);
    }

    @Test
    void When_CreateMetaAndAddSecurityIfConfidentialityCodesPresent_With_ValidMetaProfileAndNoCv_Expect_MetaWithoutSecurity() {
        final Meta result = confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            DUMMY_PROFILE,
            Optional.empty()
        );

        assertMetaSecurityIsNotPresent(result, DUMMY_PROFILE_URI);
    }

    @Test
    void When_CreateMetaAndAddSecurityIfConfidentialityCodesPresent_With_ValidMetaProfileAndSecondCvIsNoPat_Expect_MetaWithSecurity() {
        final Meta result = confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            DUMMY_PROFILE,
            Optional.empty(),
            Optional.of(NOPAT_CV)
        );

        assertMetaSecurityIsPresent(result);
    }

    private void assertMetaSecurityIsPresent(final Meta meta) {
        assertAll(
            () -> assertThat(meta.getSecurity()).usingRecursiveComparison().isEqualTo(
                Collections.singletonList(
                    new Coding()
                        .setSystem("http://hl7.org/fhir/v3/ActCode")
                        .setCode("NOPAT")
                        .setDisplay("no disclosure to patient, family or caregivers without attending provider's authorization")
                )
            ),
            () -> assertThat(meta.getProfile().getFirst().getValue()).isEqualTo(DUMMY_PROFILE_URI)
        );
    }

}