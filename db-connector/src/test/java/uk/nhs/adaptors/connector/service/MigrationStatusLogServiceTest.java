package uk.nhs.adaptors.connector.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import uk.nhs.adaptors.common.util.DateUtils;
import uk.nhs.adaptors.connector.dao.MigrationStatusLogDao;
import uk.nhs.adaptors.connector.dao.PatientMigrationRequestDao;
import uk.nhs.adaptors.common.enums.MigrationStatus;

@ExtendWith(MockitoExtension.class)
public class MigrationStatusLogServiceTest {
    private static final int MIGRATION_REQUEST_ID = 10;

    @Mock
    private PatientMigrationRequestDao patientMigrationRequestDao;

    @Mock
    private MigrationStatusLogDao migrationStatusLogDao;

    @Mock
    private DateUtils dateUtils;

    @InjectMocks
    private MigrationStatusLogService migrationStatusLogService;

    @Test
    public void testAddMigrationStatusLog() {
        String nhsNumber = "123456";
        OffsetDateTime now = OffsetDateTime.now();

        when(patientMigrationRequestDao.getMigrationRequestId(UUID.fromString(nhsNumber))).thenReturn(MIGRATION_REQUEST_ID);
        when(dateUtils.getCurrentOffsetDateTime()).thenReturn(now);

        migrationStatusLogService.addMigrationStatusLog(MigrationStatus.MIGRATION_COMPLETED, nhsNumber, null, null);

        verify(migrationStatusLogDao).addMigrationStatusLog(MigrationStatus.MIGRATION_COMPLETED, now, MIGRATION_REQUEST_ID, null, null);
    }
}
