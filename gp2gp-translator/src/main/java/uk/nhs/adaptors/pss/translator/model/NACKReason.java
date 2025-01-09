package uk.nhs.adaptors.pss.translator.model;

import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_NEGATIVE_ACK_ABA_INCORRECT_PATIENT;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_NEGATIVE_ACK_FAILED_TO_INTEGRATE;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_NEGATIVE_ACK_NON_ABA_INCORRECT_PATIENT;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_NEGATIVE_ACK_SUPPRESSED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_PATIENT_NOT_REGISTERED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_GENERAL_PROCESSING_ERROR;
import static uk.nhs.adaptors.common.enums.MigrationStatus.ERROR_EXTRACT_CANNOT_BE_PROCESSED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.ERROR_LRG_MSG_ATTACHMENTS_NOT_RECEIVED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.ERROR_LRG_MSG_GENERAL_FAILURE;
import static uk.nhs.adaptors.common.enums.MigrationStatus.ERROR_LRG_MSG_REASSEMBLY_FAILURE;
import static uk.nhs.adaptors.common.enums.MigrationStatus.ERROR_LRG_MSG_TIMEOUT;

import lombok.Getter;
import uk.nhs.adaptors.common.enums.MigrationStatus;

@Getter
public enum NACKReason {

    LARGE_MESSAGE_REASSEMBLY_FAILURE(
        "29", "Large Message Re-assembly failure"),
    LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED(
        "31", "The overall EHR Extract has been rejected because one or more attachments via Large Messages were not received."),
    LARGE_MESSAGE_GENERAL_FAILURE(
        "30", "Large Message general failure"),
    LARGE_MESSAGE_TIMEOUT(
        "25", "Large messages rejected due to timeout duration reached of overall transfer"),
    CLINICAL_SYSTEM_INTEGRATION_FAILURE(
        "11", "Failed to successfully integrate EHR Extract."),
    EHR_EXTRACT_CANNOT_BE_PROCESSED(
        "21", "EHR Extract message not well-formed or not able to be processed"),
    UNEXPECTED_CONDITION(
        "99", "Unexpected condition."),
    ABA_EHR_EXTRACT_REJECTED_WRONG_PATIENT(
        "17", "A-B-A EHR Extract Received and rejected due to wrong record or wrong patient"),
    ABA_EHR_EXTRACT_SUPPRESSED(
        "15", "A-B-A EHR Extract Received and Stored As Suppressed Record"),
    PATIENT_NOT_AT_SURGERY(
        "06", "Patient not at surgery."),
    NON_ABA_EHR_EXTRACT_REJECTED_WRONG_PATIENT(
        "28", "Non A-B-A EHR Extract Received and rejected due to wrong record or wrong patient");

    private final String code;

    private final String responseText;

    NACKReason(String code, String responseText) {
        this.code = code;
        this.responseText = responseText;
    }

    public MigrationStatus getMigrationStatus() {
        return switch (this) {
            case LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED -> ERROR_LRG_MSG_ATTACHMENTS_NOT_RECEIVED;
            case LARGE_MESSAGE_GENERAL_FAILURE -> ERROR_LRG_MSG_GENERAL_FAILURE;
            case LARGE_MESSAGE_REASSEMBLY_FAILURE -> ERROR_LRG_MSG_REASSEMBLY_FAILURE;
            case LARGE_MESSAGE_TIMEOUT -> ERROR_LRG_MSG_TIMEOUT;
            case UNEXPECTED_CONDITION -> EHR_GENERAL_PROCESSING_ERROR;
            case EHR_EXTRACT_CANNOT_BE_PROCESSED -> ERROR_EXTRACT_CANNOT_BE_PROCESSED;
            case CLINICAL_SYSTEM_INTEGRATION_FAILURE -> EHR_EXTRACT_NEGATIVE_ACK_FAILED_TO_INTEGRATE;
            case ABA_EHR_EXTRACT_SUPPRESSED  ->  EHR_EXTRACT_NEGATIVE_ACK_SUPPRESSED;
            case ABA_EHR_EXTRACT_REJECTED_WRONG_PATIENT -> EHR_EXTRACT_NEGATIVE_ACK_ABA_INCORRECT_PATIENT;
            case NON_ABA_EHR_EXTRACT_REJECTED_WRONG_PATIENT -> EHR_EXTRACT_NEGATIVE_ACK_NON_ABA_INCORRECT_PATIENT;
            case PATIENT_NOT_AT_SURGERY -> EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_PATIENT_NOT_REGISTERED;
        };
    }
}

