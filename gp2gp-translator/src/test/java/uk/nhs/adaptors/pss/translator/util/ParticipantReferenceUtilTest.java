package uk.nhs.adaptors.pss.translator.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.util.ResourceUtils.getFile;
import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import java.util.List;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKParticipant;
import org.junit.jupiter.api.Test;

import lombok.SneakyThrows;

public class ParticipantReferenceUtilTest {
    private static final String XML_RESOURCES_BASE = "xml/Participant/";

    @SneakyThrows
    private RCMRMT030101UKEhrComposition unmarshallEhrCompositionElement(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES_BASE + fileName), RCMRMT030101UKEhrComposition.class);
    }

    private List<RCMRMT030101UKParticipant> getParticipants(RCMRMT030101UKEhrComposition ehrComposition) {
        return ehrComposition.getComponent().getFirst().getPlanStatement().getParticipant();
    }

    @Test
    public void mapResourceWithPprfParticipant() {
        var ehrComposition = unmarshallEhrCompositionElement("pprf_participant.xml");
        var participants = getParticipants(ehrComposition);

        Reference participantReference = ParticipantReferenceUtil.getParticipantReference(participants, ehrComposition);

        assertThat(participantReference.getReference()).isEqualTo("Practitioner/8D1610C2-5E48-4ED5-882B-5A4A172AFA35");
    }

    @Test
    public void mapResourceWithPrfParticipant() {
        var ehrComposition = unmarshallEhrCompositionElement("prf_participant.xml");
        var participants = getParticipants(ehrComposition);

        Reference participantReference = ParticipantReferenceUtil.getParticipantReference(participants, ehrComposition);

        assertThat(participantReference.getReference()).isEqualTo("Practitioner/9C1610C2-5E48-4ED5-882B-5A4A172AFA35");
    }

    @Test
    public void mapResourceWithParticipant2() {
        var ehrComposition = unmarshallEhrCompositionElement("participant2_participant.xml");
        var participants = getParticipants(ehrComposition);

        Reference participantReference = ParticipantReferenceUtil.getParticipantReference(participants, ehrComposition);

        assertThat(participantReference.getReference()).isEqualTo("Practitioner/2D70F602-6BB1-47E0-B2EC-39912A59787D");
    }

    @Test
    public void mapResourceWithNullFlavorParticipants() {
        var ehrComposition = unmarshallEhrCompositionElement("null_flavor_participants.xml");
        var participants = getParticipants(ehrComposition);

        Reference participantReference = ParticipantReferenceUtil.getParticipantReference(participants, ehrComposition);

        assertThat(participantReference).isNull();
    }

    @Test
    public void mapResourceWithNoValidParticipant() {
        var ehrComposition = unmarshallEhrCompositionElement("no_valid_participant.xml");
        var participants = getParticipants(ehrComposition);

        Reference participantReference = ParticipantReferenceUtil.getParticipantReference(participants, ehrComposition);

        assertThat(participantReference).isNull();
    }
}
