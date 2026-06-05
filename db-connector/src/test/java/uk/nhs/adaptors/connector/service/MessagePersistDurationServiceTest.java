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

    public static final int THREE_H_SECONDS = 300;
    public static final int TEN = 10;
    public static final int TWO = 2;
    public static final int NINETY_NINE = 99;
    public static final int SEVEN = 7;
    public static final long PERSIST_DURATION = 300L;
    public static final int FIVE = 5;
    @Mock
    private MessagePersistDurationDao messagePersistDurationDao;

    @InjectMocks
    private MessagePersistDurationService messagePersistDurationService;

    @Test
    public void shouldSaveAndReturnPersistDuration() {
        var expected = MessagePersistDuration.builder()
            .id(1)
            .messageType("ehrExtract")
            .persistDuration(Duration.ofSeconds(THREE_H_SECONDS))
            .callsSinceUpdate(TWO)
            .migrationRequestId(TEN)
            .build();

        when(messagePersistDurationDao.getMessagePersistDuration(TEN, "ehrExtract")).thenReturn(expected);

        var actual = messagePersistDurationService.addMessagePersistDuration("ehrExtract", Duration.ofMinutes(FIVE), TWO, TEN);

        verify(messagePersistDurationDao).saveMessagePersistDuration("ehrExtract", PERSIST_DURATION, TWO, TEN);
        assertEquals(expected, actual);
    }

    @Test
    public void shouldReturnPersistDurationWhenMessageTypeExists() {
        var expected = MessagePersistDuration.builder()
            .messageType("COPC")
            .migrationRequestId(NINETY_NINE)
            .build();

        when(messagePersistDurationDao.messageTypeExists(NINETY_NINE, "COPC")).thenReturn(true);
        when(messagePersistDurationDao.getMessagePersistDuration(NINETY_NINE, "COPC")).thenReturn(expected);

        var actual = messagePersistDurationService.getMessagePersistDuration(NINETY_NINE, "COPC");

        assertTrue(actual.isPresent());
        assertEquals(expected, actual.get());
    }

    @Test
    public void shouldReturnEmptyWhenMessageTypeDoesNotExist() {
        when(messagePersistDurationDao.messageTypeExists(SEVEN, "missing")).thenReturn(false);

        var actual = messagePersistDurationService.getMessagePersistDuration(SEVEN, "missing");

        assertFalse(actual.isPresent());
        verify(messagePersistDurationDao, never()).getMessagePersistDuration(SEVEN, "missing");
    }
}

