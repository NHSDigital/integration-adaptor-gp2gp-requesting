package uk.nhs.adaptors.pss.translator.service;

import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.v3.CV;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.generateMeta;

@Service
public class ConfidentialityService {
    private static final Coding CONFIDENTIALITY_CODING = new Coding()
        .setSystem("http://hl7.org/fhir/v3/ActCode")
        .setCode("NOPAT")
        .setDisplay("no disclosure to patient, family or caregivers without attending provider's authorization");

    @SafeVarargs
    public final Meta createMetaAndAddSecurityIfConfidentialityCodesPresent(String metaProfile, Optional<CV>... cvs) {
        return Arrays.stream(cvs).flatMap(Optional::stream).anyMatch(this::isNopat)
            ? generateMeta(metaProfile).setSecurity(Collections.singletonList(CONFIDENTIALITY_CODING))
            : generateMeta(metaProfile);
    }

    private boolean isNopat(CV coding) {
        return "NOPAT".equals(coding.getCode());
    }
}