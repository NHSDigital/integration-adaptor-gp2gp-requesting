package uk.nhs.adaptors.connector.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.adaptors.common.util.DateUtils;
import uk.nhs.adaptors.connector.dao.MigrationStatusLogDao;
import uk.nhs.adaptors.connector.dao.PatientMigrationRequestDao;
import uk.nhs.adaptors.common.enums.MigrationStatus;
import uk.nhs.adaptors.connector.model.MigrationStatusLog;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class MigrationStatusLogService {

    private final PatientMigrationRequestDao patientMigrationRequestDao;
    private final MigrationStatusLogDao migrationStatusLogDao;
    private final DateUtils dateUtils;

    public void addMigrationStatusLog(MigrationStatus migrationStatus, String conversationId, String messageId, String gp2gpErrorCode) {

        int migrationRequestId = patientMigrationRequestDao.getMigrationRequestId(conversationId.toUpperCase(Locale.ROOT));
        migrationStatusLogDao.addMigrationStatusLog(
            migrationStatus,
            dateUtils.getCurrentOffsetDateTime(),
            migrationRequestId,
            messageId,
            gp2gpErrorCode
        );

        LOGGER.debug("Changed MigrationStatus of PatientMigrationRequest with id=[{}] to [{}]", migrationRequestId, migrationStatus.name());
    }

    public MigrationStatusLog getLatestMigrationStatusLog(String conversationId) {
        int migrationRequestId = patientMigrationRequestDao.getMigrationRequestId(conversationId);
        return migrationStatusLogDao.getLatestMigrationStatusLog(migrationRequestId);
    }

    public List<MigrationStatusLog> getMigrationStatusLogs(String conversationId) {
        int migrationRequestId = patientMigrationRequestDao.getMigrationRequestId(conversationId);
        return migrationStatusLogDao.getLatestMigrationStatusLogs(migrationRequestId);
    }

    public void updatePatientMigrationRequestAndAddMigrationStatusLog(String conversationId, String bundle, String inboundMessage,
        MigrationStatus migrationStatus, String messageId) {
        patientMigrationRequestDao.saveBundleAndInboundMessageData(conversationId, bundle, inboundMessage);
        addMigrationStatusLog(migrationStatus, conversationId, messageId, null);
    }
}
