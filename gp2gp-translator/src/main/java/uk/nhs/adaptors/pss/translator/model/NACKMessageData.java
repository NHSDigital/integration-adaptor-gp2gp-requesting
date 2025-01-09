package uk.nhs.adaptors.pss.translator.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

@Builder
@Getter
@EqualsAndHashCode
public class NACKMessageData {
    @NonNull
    private String conversationId;

    @NonNull
    private NACKReason nackReason;

    @NonNull
    private String toOdsCode;

    @NonNull
    private String messageRef;

    @NonNull
    private String toAsid;

    @NonNull
    private String fromAsid;
}
