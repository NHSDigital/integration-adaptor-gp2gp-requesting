package uk.nhs.adaptors.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class MDCServiceTest {

    private final MDCService mdcService = new MDCService();

    @AfterEach
    public void tearDown() {
        mdcService.resetAllMdcKeys();
    }

    @Test
    public void shouldApplyAndGetConversationId() {
        mdcService.applyConversationId("conversation-id");

        assertEquals("conversation-id", mdcService.getConversationId());
    }

    @Test
    public void shouldResetConversationId() {
        mdcService.applyConversationId("conversation-id");

        mdcService.resetAllMdcKeys();

        assertNull(mdcService.getConversationId());
    }
}

