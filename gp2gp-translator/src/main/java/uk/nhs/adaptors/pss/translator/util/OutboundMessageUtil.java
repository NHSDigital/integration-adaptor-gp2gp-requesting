package uk.nhs.adaptors.pss.translator.util;

import org.hl7.v3.RCMRIN030000UKMessage;
import org.springframework.stereotype.Component;

@Component
public class OutboundMessageUtil {
    public String parseFromAsid(RCMRIN030000UKMessage payload) {
        return payload.getCommunicationFunctionRcv()
            .get(0)
            .getDevice()
            .getId()
            .get(0)
            .getExtension();
    }

    public String parseToAsid(RCMRIN030000UKMessage payload) {
        return payload.getCommunicationFunctionSnd()
            .getDevice()
            .getId()
            .get(0)
            .getExtension();
    }

    public String parseToOdsCode(RCMRIN030000UKMessage payload) {
        return payload.getControlActEvent()
            .getSubject()
            .getEhrExtract()
            .getAuthor()
            .getAgentOrgSDS()
            .getAgentOrganizationSDS()
            .getId()
            .getExtension();
    }

    public String parseMessageRef(RCMRIN030000UKMessage payload) {
        return payload.getId().getRoot();
    }
}
