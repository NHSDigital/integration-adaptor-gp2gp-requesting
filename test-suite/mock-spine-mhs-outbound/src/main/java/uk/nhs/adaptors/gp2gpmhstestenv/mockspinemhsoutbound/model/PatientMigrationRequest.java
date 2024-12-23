package uk.nhs.adaptors.gp2gpmhstestenv.mockspinemhsoutbound.model;

import lombok.*;

import jakarta.persistence.*;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonManagedReference;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "patient_migration_request", schema = "public")
public class PatientMigrationRequest {
    @Id
    @Column(name = "id")
    private int id;
    @Column(name = "patient_nhs_number")
    private String patientNhsNumber;
    @Column(name = "bundle_resource")
    private String bundleResource;
    @Column(name = "inbound_message")
    private String inboundMessage;
    @Column(name = "conversation_id")
    private String conversationId;
    @Column(name = "losing_practice_ods_code")
    private String losingPracticeOdsCode;
    @Column(name = "winning_practice_ods_code")
    private String winningPracticeOdsCode;

    @JsonManagedReference
    @OneToMany(mappedBy = "patientMigrationRequest")
    private List<MigrationStatusLog> migrationStatusLog;

    @JsonManagedReference
    @OneToMany(mappedBy = "patientMigrationRequest")
    private List<MessagePersistDuration> messagePersistDurationList;

    @JsonManagedReference
    @OneToMany(mappedBy = "patientMigrationRequest")
    private List<PatientAttachmentLog> patientAttachmentLog;

}
