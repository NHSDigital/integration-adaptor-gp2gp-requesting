package uk.nhs.adaptors.connector.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;

import org.jdbi.v3.core.statement.StatementContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class PatientMigrationRequestRowMapperTest {

    public static final String PATIENT_NHS_NUMBER = "1234567890";
    public static final int ID = 99999;
    public static final String BUNDLE_RESOURCE = "bundle";
    public static final String INBOUND = "inbound";
    public static final String CONVERSATION_ID = "conversation-id";
    public static final String LOOSING_ODS_CODE = "A123";
    public static final String WINNING_ODS_CODE = "B123";

    @Test
    public void shouldMapPatientMigrationRequestFields() throws Exception {
        var mapper = new PatientMigrationRequestRowMapper();
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        StatementContext statementContext = Mockito.mock(StatementContext.class);

        when(resultSet.getInt("id")).thenReturn(ID);
        when(resultSet.getString("patient_nhs_number")).thenReturn(PATIENT_NHS_NUMBER);
        when(resultSet.getString("bundle_resource")).thenReturn(BUNDLE_RESOURCE);
        when(resultSet.getString("inbound_message")).thenReturn(INBOUND);
        when(resultSet.getString("conversation_id")).thenReturn(CONVERSATION_ID);
        when(resultSet.getString("losing_practice_ods_code")).thenReturn(LOOSING_ODS_CODE);
        when(resultSet.getString("winning_practice_ods_code")).thenReturn(WINNING_ODS_CODE);

        var mapped = mapper.map(resultSet, statementContext);

        assertEquals(ID, mapped.getId());
        assertEquals(PATIENT_NHS_NUMBER, mapped.getPatientNhsNumber());
        assertEquals(BUNDLE_RESOURCE, mapped.getBundleResource());
        assertEquals(INBOUND, mapped.getInboundMessage());
        assertEquals(CONVERSATION_ID, mapped.getConversationId());
        assertEquals(LOOSING_ODS_CODE, mapped.getLosingPracticeOdsCode());
        assertEquals(WINNING_ODS_CODE, mapped.getWinningPracticeOdsCode());
    }
}
