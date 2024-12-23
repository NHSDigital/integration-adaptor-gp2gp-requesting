package uk.nhs.adaptors.pss.translator.util;

import java.util.List;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.UriType;
import org.hl7.v3.RCMRMT030101UKEhrComposition;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ResourceUtil {

    private static final String META_PROFILE_TEMPLATE = "https://fhir.nhs.uk/STU3/StructureDefinition/CareConnect-GPC-%s";
    private static final String IDENTIFIER_SYSTEM = "https://PSSAdaptor/%s";

    public static Meta generateMeta(String urlProfile) {
        Meta meta = new Meta();
        UriType profile = new UriType(String.format(META_PROFILE_TEMPLATE, urlProfile));
        meta.setProfile(List.of(profile));
        return meta;
    }

    public static Identifier buildIdentifier(String rootId, String practiceCode) {
        Identifier identifier = new Identifier();
        identifier.setSystem(IDENTIFIER_SYSTEM.formatted(practiceCode));
        identifier.setValue(rootId);

        return identifier;
    }

    public static Extension buildReferenceExtension(String url, Reference reference) {
        return new Extension(url, reference);
    }

    public static void addContextToObservation(Observation observation, List<Encounter> encounters,
        RCMRMT030101UKEhrComposition ehrComposition) {

        encounters.stream()
            .filter(encounter -> encounter.getId().equals(ehrComposition.getId().getRoot()))
            .findFirst()
            .map(Reference::new)
            .ifPresent(observation::setContext);
    }

}
