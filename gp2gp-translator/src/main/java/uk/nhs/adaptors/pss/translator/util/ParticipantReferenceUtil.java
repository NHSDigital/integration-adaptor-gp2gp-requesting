package uk.nhs.adaptors.pss.translator.util;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.v3.II;
import org.hl7.v3.RCMRMT030101UKAgentRef;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKParticipant;
import org.hl7.v3.RCMRMT030101UKParticipant2;

import static uk.nhs.adaptors.pss.translator.util.AuthorUtil.getAuthorReference;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ParticipantReferenceUtil {
    private static final String PRF_TYPE_CODE = "PRF";
    private static final String PPRF_TYPE_CODE = "PPRF";
    private static final String PRACTITIONER_REFERENCE_PREFIX = "Practitioner/%s";
    public static final String ASSERTER = "asserter";
    public static final String RECORDER = "recorder";

    public static Reference getParticipantReference(List<RCMRMT030101UKParticipant> participantList,
                                                    RCMRMT030101UKEhrComposition ehrComposition) {

        var nonNullFlavorParticipants = participantList.stream()
            .filter(ParticipantReferenceUtil::isNotNullFlavour)
            .toList();

        var pprfParticipants = getParticipantReference(nonNullFlavorParticipants, PPRF_TYPE_CODE);
        if (pprfParticipants.isPresent()) {
            return new Reference(PRACTITIONER_REFERENCE_PREFIX.formatted(pprfParticipants.get()));
        }

        var prfParticipants = getParticipantReference(nonNullFlavorParticipants, PRF_TYPE_CODE);
        if (prfParticipants.isPresent()) {
            return new Reference(PRACTITIONER_REFERENCE_PREFIX.formatted(prfParticipants.get()));
        }

        var participant2Reference = getParticipant2Reference(ehrComposition);
        if (participant2Reference.isPresent()) {
            return new Reference(PRACTITIONER_REFERENCE_PREFIX.formatted(participant2Reference.get()));
        }

        return null;
    }

    private static Optional<String> getParticipantReference(List<RCMRMT030101UKParticipant> participantList, String typeCode) {

        return participantList.stream()
            .filter(participant -> hasTypeCode(participant, typeCode))
            .filter(ParticipantReferenceUtil::hasAgentReference)
            .map(RCMRMT030101UKParticipant::getAgentRef)
            .map(RCMRMT030101UKAgentRef::getId)
            .filter(II::hasRoot)
            .map(II::getRoot)
            .findFirst();
    }


    public static Map<String, Optional<Reference>> fetchRecorderAndAsserter(RCMRMT030101UKEhrComposition ehrComposition) {

        var practitioner = Optional.ofNullable(getParticipant2Reference(ehrComposition, "RESP"));
        var author = getAuthorReference(ehrComposition);

        return Map.of(RECORDER, author, ASSERTER, practitioner);
    }

    public static Reference getParticipant2Reference(RCMRMT030101UKEhrComposition ehrComposition, String typeCode) {

        var participant2Reference = ehrComposition.getParticipant2().stream()
            .filter(participant2 -> participant2.getNullFlavor() == null)
            .filter(participant2 -> typeCode.equals(participant2.getTypeCode().getFirst()))
            .map(RCMRMT030101UKParticipant2::getAgentRef)
            .map(RCMRMT030101UKAgentRef::getId)
            .filter(II::hasRoot)
            .map(II::getRoot)
            .findFirst();

        if (participant2Reference.isPresent()) {
            return new Reference(PRACTITIONER_REFERENCE_PREFIX.formatted(participant2Reference.get()));
        }
        return null;
    }

    private static Optional<String> getParticipant2Reference(RCMRMT030101UKEhrComposition ehrComposition) {

        return ehrComposition.getParticipant2().stream()
            .filter(participant2 -> participant2.getNullFlavor() == null)
            .map(RCMRMT030101UKParticipant2::getAgentRef)
            .map(RCMRMT030101UKAgentRef::getId)
            .filter(II::hasRoot)
            .map(II::getRoot)
            .findFirst();
    }

    private static boolean hasAgentReference(RCMRMT030101UKParticipant participant) {
        return participant.getAgentRef() != null && participant.getAgentRef().getId() != null;
    }

    private static boolean hasTypeCode(RCMRMT030101UKParticipant participant, String typeCode) {
        return !participant.getTypeCode().isEmpty() && participant.getTypeCode().getFirst().equals(typeCode);
    }

    private static boolean isNotNullFlavour(RCMRMT030101UKParticipant participant) {
        return participant.getNullFlavor() == null;
    }
}
