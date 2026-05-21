package uk.nhs.adaptors.pss.translator;

import org.hl7.v3.CD;
import org.hl7.v3.RCMRMT030101UKObservationStatement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.nhs.adaptors.pss.translator.util.DatabaseImmunizationChecker;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.nhs.adaptors.pss.translator.TestUtility.createCd;

@SpringBootTest
@DirtiesContext
@ExtendWith(SpringExtension.class)
class DatabaseImmunizationCheckerIT {
    private static final String SNOMED_CODE_SYSTEM_OID = "2.16.840.1.113883.2.1.3.2.4.15";
    private static final String DISPLAY_NAME = "Influenza vaccination";

    @Autowired
    private DatabaseImmunizationChecker databaseImmunizationChecker;

    @Test
    void When_Immunization_With_SnomedDescriptionId_Expect_True() {
        final String immunizationDescriptionSnomedId = "142934010";
        final CD cd = getCdForSnomedId(immunizationDescriptionSnomedId);
        final RCMRMT030101UKObservationStatement observationStatement =
            new RCMRMT030101UKObservationStatement();

        observationStatement.setCode(cd);

        final boolean result = databaseImmunizationChecker.isImmunization(observationStatement);

        assertThat(result).isTrue();
    }

    @Test
    void When_Immunization_With_SnomedConceptId_Expect_True() {
        final String immunizationConceptSnomedId = "86198006";
        final CD cd = getCdForSnomedId(immunizationConceptSnomedId);
        final RCMRMT030101UKObservationStatement observationStatement =
            new RCMRMT030101UKObservationStatement();

        observationStatement.setCode(cd);

        final boolean result = databaseImmunizationChecker.isImmunization(observationStatement);

        assertThat(result).isTrue();
    }

    private CD getCdForSnomedId(String snomedId) {
        return createCd(
            snomedId,
            SNOMED_CODE_SYSTEM_OID,
            DISPLAY_NAME
        );
    }
}
