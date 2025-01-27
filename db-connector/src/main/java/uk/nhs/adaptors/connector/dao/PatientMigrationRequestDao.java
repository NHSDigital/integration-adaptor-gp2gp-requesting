package uk.nhs.adaptors.connector.dao;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.locator.UseClasspathSqlLocator;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import uk.nhs.adaptors.common.enums.MigrationStatus;
import uk.nhs.adaptors.connector.model.PatientMigrationRequest;

public abstract class PatientMigrationRequestDao {

    @SqlUpdate("insert_patient_migration_request")
    @UseClasspathSqlLocator
    public abstract void addNewRequest(@Bind("nhsNumber") String patientNhsNumber, @Bind("conversationId") String conversationId,
        @Bind("losingOdsCode") String losingOdsCode, @Bind("winningOdsCode") String winningOdsCode);

    @SqlQuery("select_patient_migration_request")
    @UseClasspathSqlLocator
    public abstract PatientMigrationRequest getMigrationRequest(@Bind("conversationId") String conversationId);

    @SqlQuery("select_patient_migration_request_id")
    @UseClasspathSqlLocator
    @Deprecated
    abstract int getMigrationRequestId(@Bind("conversationId") String conversationId);

    public int getMigrationRequestId(@Bind("conversationId") UUID conversationId) {
        return this.getMigrationRequestId(conversationId.toString().toUpperCase(Locale.ROOT));
    }

    @SqlUpdate("save_bundle_resource_and_inbound_message_data")
    @UseClasspathSqlLocator
    public abstract void saveBundleAndInboundMessageData(@Bind("conversationId") String conversationId, @Bind("bundle") String bundle,
        @Bind("inboundMessage") String inboundMessage);

    @SqlQuery("exists_by_conversation_id")
    @UseClasspathSqlLocator
    public abstract boolean existsByConversationId(@Bind("conversationId") String conversationId);

    @SqlQuery("select_patient_migration_request_by_patient_nhs_number")
    @UseClasspathSqlLocator
    public abstract PatientMigrationRequest getLatestMigrationRequestByPatientNhsNumber(@Bind("patientNhsNumber") String patientNhsNumber);

    @SqlQuery("select_patient_migration_requests_by_latest_migration_status")
    @UseClasspathSqlLocator
    public abstract List<PatientMigrationRequest> getMigrationRequestsByLatestMigrationStatusIn(
        @BindList("statusList") List<MigrationStatus> statusList);
}
