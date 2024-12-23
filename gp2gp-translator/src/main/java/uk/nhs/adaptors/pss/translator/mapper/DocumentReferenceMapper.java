package uk.nhs.adaptors.pss.translator.mapper;

import static org.hl7.fhir.dstu3.model.Enumerations.DocumentReferenceStatus;

import static uk.nhs.adaptors.pss.translator.util.CompoundStatementResourceExtractors.extractAllNonBloodPressureNarrativeStatements;
import static uk.nhs.adaptors.pss.translator.util.ParticipantReferenceUtil.getParticipantReference;
import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.buildIdentifier;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.Attachment;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.DocumentReference;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.InstantType;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKExternalDocument;
import org.hl7.v3.RCMRMT030101UKNarrativeStatement;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.adaptors.connector.model.PatientAttachmentLog;
import uk.nhs.adaptors.pss.translator.service.ConfidentialityService;
import uk.nhs.adaptors.pss.translator.util.DateFormatUtil;
import uk.nhs.adaptors.pss.translator.util.DegradedCodeableConcepts;
import uk.nhs.adaptors.pss.translator.util.ResourceFilterUtil;

@Slf4j
@Service
@AllArgsConstructor
public class DocumentReferenceMapper extends AbstractMapper<DocumentReference> {

    // TODO: Add file Size using the uncompressed/un-encoded size of the document (NIAD-2030)

    private static final String META_PROFILE = "DocumentReference-1";
    private static final String ABSENT_ATTACHMENT = "AbsentAttachment";
    private static final String PLACEHOLDER_VALUE = "GP2GP generated placeholder. Original document not available. See notes for details";
    private static final String INVALID_CONTENT_TYPE = "Content type was not a valid MIME type";

    private CodeableConceptMapper codeableConceptMapper;
    private ConfidentialityService confidentialityService;

    public List<DocumentReference> mapResources(RCMRMT030101UKEhrExtract ehrExtract, Patient patient,
                                                List<Encounter> encounterList, Organization organization,
                                                List<PatientAttachmentLog> attachments) {

        return mapEhrExtractToFhirResource(ehrExtract, (extract, composition, component) ->
            extractAllNonBloodPressureNarrativeStatements(component)
                .filter(Objects::nonNull)
                .filter(ResourceFilterUtil::isDocumentReference)
                .map(narrativeStatement -> mapDocumentReference(narrativeStatement, composition, patient, encounterList,
                    organization, attachments)))
            .toList();
    }

    public boolean hasDocumentReferences(RCMRMT030101UKEhrExtract ehrExtract) {

        return !mapEhrExtractToFhirResource(ehrExtract, (extract, composition, component) ->
            extractAllNonBloodPressureNarrativeStatements(component)
               .filter(Objects::nonNull)
               .filter(ResourceFilterUtil::isDocumentReference)
               .map(narrativeStatement -> new DocumentReference())
        ).toList().isEmpty();
    }

    private DocumentReference mapDocumentReference(RCMRMT030101UKNarrativeStatement narrativeStatement,
                                                   RCMRMT030101UKEhrComposition ehrComposition, Patient patient,
                                                   List<Encounter> encounterList,
                                                   Organization organization, List<PatientAttachmentLog> attachments) {

        final DocumentReference documentReference = new DocumentReference();

        // document references actually use the narrative statement id rather than the referenceDocument root id in EMIS data
        // if EMIS is incorrect, replace the id below with the following...
        // narrativeStatement.getReference().getFirst().getReferredToExternalDocument().getId().getRoot()
        var id = narrativeStatement.getId().getRoot();

        documentReference.addIdentifier(buildIdentifier(id, organization.getIdentifierFirstRep().getValue()));
        documentReference.setId(id);
        documentReference.setMeta(generateMeta(narrativeStatement));
        documentReference.setStatus(DocumentReferenceStatus.CURRENT);
        documentReference.setType(getType(narrativeStatement));
        documentReference.setSubject(new Reference(patient));
        documentReference.setDescription(buildDescription(narrativeStatement));
        documentReference.setIndexedElement(getIndexed(ehrComposition));
        documentReference.setCustodian(new Reference(organization));
        documentReference.setCreatedElement(getCreatedTime(ehrComposition));
        getAuthor(narrativeStatement, ehrComposition).ifPresent(documentReference::addAuthor);

        var encounterReference = encounterList.stream()
            .filter(encounter -> encounter.getId().equals(ehrComposition.getId().getRoot()))
            .findFirst()
            .map(Reference::new);

        if (encounterReference.isPresent()) {
            DocumentReference.DocumentReferenceContextComponent documentReferenceContextComponent =
                new DocumentReference.DocumentReferenceContextComponent().setEncounter(encounterReference.get());

            documentReference.setContext(documentReferenceContextComponent);
        }

        setContentAttachments(documentReference, narrativeStatement, attachments);

        return documentReference;
    }

    private Meta generateMeta(RCMRMT030101UKNarrativeStatement narrativeStatement) {
        final RCMRMT030101UKExternalDocument externalDocument = narrativeStatement
            .getReference()
            .getFirst()
            .getReferredToExternalDocument();

        return confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            externalDocument.getConfidentialityCode()
        );
    }

    private DateTimeType getCreatedTime(RCMRMT030101UKEhrComposition ehrComposition) {
        if (ehrComposition.hasAvailabilityTime() && ehrComposition.getAvailabilityTime().hasValue()) {
            return DateFormatUtil.parseToDateTimeType(ehrComposition.getAvailabilityTime().getValue());
        }
        return null;
    }

    private InstantType getIndexed(RCMRMT030101UKEhrComposition ehrComposition) {
        if (ehrComposition.hasAuthor() && ehrComposition.getAuthor().hasTime()
            && ehrComposition.getAuthor().getTime().hasValue()) {
            return DateFormatUtil.parseToInstantType(ehrComposition.getAuthor().getTime().getValue());
        }
        return null;
    }

    private CodeableConcept getType(RCMRMT030101UKNarrativeStatement narrativeStatement) {

        var referenceToExternalDocument = narrativeStatement.getReference().getFirst().getReferredToExternalDocument();
        CodeableConcept codeableConcept = null;
        if (referenceToExternalDocument != null && referenceToExternalDocument.hasCode()) {
            if (!referenceToExternalDocument.getCode().hasOriginalText() && referenceToExternalDocument.getCode().hasDisplayName()) {
                codeableConcept = codeableConceptMapper.mapToCodeableConcept(referenceToExternalDocument.getCode())
                    .setText(referenceToExternalDocument.getCode().getDisplayName());
            } else if (referenceToExternalDocument.getCode().hasOriginalText()) {
                codeableConcept = codeableConceptMapper.mapToCodeableConcept(referenceToExternalDocument.getCode())
                    .setText(referenceToExternalDocument.getCode().getOriginalText());
            }
        }

        DegradedCodeableConcepts.addDegradedEntryIfRequired(codeableConcept, DegradedCodeableConcepts.DEGRADED_OTHER);
        return codeableConcept;
    }

    private Optional<Reference> getAuthor(RCMRMT030101UKNarrativeStatement narrativeStatement,
                                          RCMRMT030101UKEhrComposition ehrComposition) {

        final Reference ref = getParticipantReference(narrativeStatement.getParticipant(), ehrComposition);
        if (ref != null) {
            return Optional.of(ref);
        }
        return Optional.empty();
    }

    private String buildDescription(RCMRMT030101UKNarrativeStatement narrativeStatement) {

        if (narrativeStatement.hasText()) {
            return narrativeStatement.getText();
        }

        if (isAbsentAttachment(narrativeStatement)) {
            return PLACEHOLDER_VALUE;
        } else {
            return buildFileName(narrativeStatement.getReference().getFirst()
                .getReferredToExternalDocument().getText().getReference().getValue());
        }
    }

    private boolean isAbsentAttachment(RCMRMT030101UKNarrativeStatement narrativeStatement) {

        return narrativeStatement.getReference().getFirst()
            .getReferredToExternalDocument().getText().getReference().getValue().contains(ABSENT_ATTACHMENT);
    }

    private void setContentAttachments(DocumentReference documentReference, RCMRMT030101UKNarrativeStatement narrativeStatement,
                                       List<PatientAttachmentLog> patientAttachmentLogs) {

        var referenceToExternalDocument = narrativeStatement.getReference().getFirst().getReferredToExternalDocument();
        var attachment = new Attachment();
        if (referenceToExternalDocument.hasText()) {
            var mediaType = referenceToExternalDocument.getText().getMediaType();
            var filenameReference = referenceToExternalDocument.getText().getReference().getValue();
            var attachmentSize = getAttachmentSize(patientAttachmentLogs, filenameReference);

            if (isAbsentAttachment(narrativeStatement)) {
                attachment.setTitle(PLACEHOLDER_VALUE);
            }

            // Always provide a reference to the file, absent attachement placeholders container details about the original file
            attachment.setUrl(referenceToExternalDocument.getText().getReference().getValue());

            if (attachmentSize != null) {
                attachment.setSize(attachmentSize);
            }

            if (isContentTypeValid(mediaType)) {
                attachment.setContentType(mediaType);
            } else {
                attachment.setContentType(PLACEHOLDER_VALUE);
                addContentTypeToNotes(documentReference);
                LOGGER.info("Content type: '{}' was not a valid MIME type", attachment.getContentType());
            }

            documentReference.addContent(new DocumentReference.DocumentReferenceContentComponent(attachment));
        }
    }

    private void addContentTypeToNotes(DocumentReference documentReference) {
        if (documentReference.getDescription().isEmpty()) {
            documentReference.setDescription(INVALID_CONTENT_TYPE);
        } else {
            String previousDesc = documentReference.getDescription();
            String newDesc = previousDesc + StringUtils.SPACE + INVALID_CONTENT_TYPE;
            documentReference.setDescription(newDesc);
        }
    }

    private String buildFileName(String text) {
        return text.replace("file://localhost/", "");
    }

    private boolean isContentTypeValid(String mediaType) {
        String validContentTypeFormat = ".*/.*";
        return Pattern.matches(validContentTypeFormat, mediaType);
    }

    private Integer getAttachmentSize(List<PatientAttachmentLog> patientAttachmentLogs, String filename) {

        if (patientAttachmentLogs != null && !patientAttachmentLogs.isEmpty()) {
            var attachmentSize = patientAttachmentLogs.stream()
                .filter(patientAttachmentLog -> filename.contains(patientAttachmentLog.getFilename()))
                .findFirst()
                .map(PatientAttachmentLog::getPostProcessedLengthNum);

            if (attachmentSize.isPresent()) {
                return attachmentSize.get();
            }
        }

        return null;
    }

    // stubbed method for abstract class
    public List<DocumentReference> mapResources(RCMRMT030101UKEhrExtract ehrExtract, Patient patient,
        List<Encounter> encounterList, String practiceCode) {
        return List.of();
    }
}
