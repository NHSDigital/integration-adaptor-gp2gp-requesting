package uk.nhs.adaptors.connector.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import uk.nhs.adaptors.connector.dao.MessagePersistDurationDao;
import uk.nhs.adaptors.connector.model.MessagePersistDuration;

@ExtendWith(MockitoExtension.class)
public class MessagePersistDurationServiceTest {

    @Mock
    private MessagePersistDurationDao messagePersistDurationDao;

    @InjectMocks
    private MessagePersistDurationService messagePersistDurationService;

    @Test
    public void shouldSaveAndReturnPersistDuration() {
        var expected = MessagePersistDuration.builder()
            .id(1)
            .messageType("ehrExtract")
            .persistDuration(Duration.ofSeconds(300))
            .callsSinceUpdate(2)
            .migrationRequestId(10)
            .build();

        when(messagePersistDurationDao.getMessagePersistDuration(10, "ehrExtract")).thenReturn(expected);

        var actual = messagePersistDurationService.addMessagePersistDuration("ehrExtract", Duration.ofMinutes(5), 2, 10);

        verify(messagePersistDurationDao).saveMessagePersistDuration("ehrExtract", 300L, 2, 10);
        assertEquals(expected, actual);
    }

    @Test
    public void shouldReturnPersistDurationWhenMessageTypeExists() {
        var expected = MessagePersistDuration.builder()
            .messageType("copc")
            .migrationRequestId(99)
            .build();

        when(messagePersistDurationDao.messageTypeExists(99, "copc")).thenReturn(true);
        when(messagePersistDurationDao.getMessagePersistDuration(99, "copc")).thenReturn(expected);

        var actual = messagePersistDurationService.getMessagePersistDuration(99, "copc");

        assertTrue(actual.isPresent());
        assertEquals(expected, actual.get());
    }

    @Test
    public void shouldReturnEmptyWhenMessageTypeDoesNotExist() {
        when(messagePersistDurationDao.messageTypeExists(7, "missing")).thenReturn(false);

        var actual = messagePersistDurationService.getMessagePersistDuration(7, "missing");

        assertFalse(actual.isPresent());
        verify(messagePersistDurationDao, never()).getMessagePersistDuration(7, "missing");
    }
}

