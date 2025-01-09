package uk.nhs.adaptors.pss.translator.util.template.parameter;

import lombok.Builder;
import lombok.Getter;
import uk.nhs.adaptors.pss.translator.model.NACKReason;

@Getter
@Builder
public class ApplicationAcknowledgmentParams {
    private String messageId;
    private String creationTime;
    private NACKReason nackReason;
    private String messageRef;
    private String toAsid;
    private String fromAsid;
}
