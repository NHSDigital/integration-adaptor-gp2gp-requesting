package uk.nhs.adaptors.gp2gpmhstestenv.mockspinemhsoutbound.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonBackReference;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "patient_attachment_log", schema = "public")
public class PatientAttachmentLog {

    @Id
    @Column(name = "id")
    private String id;
    @Column(name = "mid")
    private String mid;
    @Column(name = "parent_mid")
    private String parentMid;
    @Column(name = "filename")
    private String filename;
    @Column(name = "content_type")
    private String contentType;
    @Column(name = "compressed")
    private Boolean compressed;
    @Column(name = "large_attachment")
    private Boolean largeAttachment;
    @Column(name = "base64")
    private Boolean base64;
    @Column(name = "skeleton")
    private Boolean skeleton;
    @Column(name = "uploaded")
    private Boolean uploaded;
    @Column(name = "length_num")
    private Integer lengthNum;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_migration_req_id")
    private PatientMigrationRequest patientMigrationRequest;

    @Column(name = "order_num")
    private Integer orderNum;

    @Column(name = "deleted")
    private Boolean deleted;
}
