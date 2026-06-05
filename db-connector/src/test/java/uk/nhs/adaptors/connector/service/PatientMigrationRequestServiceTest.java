package uk.nhs.adaptors.connector.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import uk.nhs.adaptors.common.enums.MigrationStatus;
import uk.nhs.adaptors.connector.dao.PatientMigrationRequestDao;
import uk.nhs.adaptors.connector.model.PatientMigrationRequest;

@ExtendWith(MockitoExtension.class)
public class PatientMigrationRequestServiceTest {

    @Mock
    private PatientMigrationRequestDao patientMigrationRequestDao;

    @InjectMocks
    private PatientMigrationRequestService patientMigrationRequestService;

    @Test
    public void shouldReturnRequestsByMigrationStatus() {
        var statuses = List.of(MigrationStatus.REQUEST_RECEIVED);
        var request = PatientMigrationRequest.builder()
            .id(11)
            .conversationId("conversation-id-123")
            .build();

        when(patientMigrationRequestDao.getMigrationRequestsByLatestMigrationStatusIn(statuses)).thenReturn(List.of(request));

        var actual = patientMigrationRequestService.getMigrationRequestsByMigrationStatusIn(statuses);

        assertEquals(1, actual.size());
        assertEquals("conversation-id-123", actual.get(0).getConversationId());
    }

    @Test
    public void shouldReturnTrueWhenMigrationRequestExists() {
        when(patientMigrationRequestDao.existsByConversationId("conversation-id-123")).thenReturn(true);

        var exists = patientMigrationRequestService.hasMigrationRequest("conversation-id-123");

        assertTrue(exists);
        verify(patientMigrationRequestDao).existsByConversationId("conversation-id-123");
    }
}

