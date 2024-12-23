package uk.nhs.adaptors.pss.translator.mapper.medication;

import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.Annotation;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.MedicationRequest;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.hl7.fhir.dstu3.model.StringType;
import org.hl7.fhir.dstu3.model.UnsignedIntType;
import org.hl7.v3.RCMRMT030101UKAuthorise;
import org.hl7.v3.RCMRMT030101UKComponent;
import org.hl7.v3.RCMRMT030101UKComponent3;
import org.hl7.v3.RCMRMT030101UKComponent2;
import org.hl7.v3.RCMRMT030101UKDiscontinue;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKEhrFolder;
import org.hl7.v3.RCMRMT030101UKMedicationStatement;
import org.hl7.v3.RCMRMT030101UKPertinentInformation2;
import org.hl7.v3.RCMRMT030101UKSupplyAnnotation;
import org.springframework.stereotype.Service;
import uk.nhs.adaptors.pss.translator.service.ConfidentialityService;
import uk.nhs.adaptors.pss.translator.util.DateFormatUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.hl7.fhir.dstu3.model.MedicationRequest.MedicationRequestIntent.PLAN;
import static org.hl7.fhir.dstu3.model.MedicationRequest.MedicationRequestStatus.ACTIVE;
import static org.hl7.fhir.dstu3.model.MedicationRequest.MedicationRequestStatus.COMPLETED;
import static org.hl7.fhir.dstu3.model.MedicationRequest.MedicationRequestStatus.STOPPED;
import static uk.nhs.adaptors.pss.translator.mapper.medication.MedicationMapperUtils.buildDispenseRequestPeriodEnd;
import static uk.nhs.adaptors.pss.translator.mapper.medication.MedicationMapperUtils.buildDosage;
import static uk.nhs.adaptors.pss.translator.mapper.medication.MedicationMapperUtils.buildDosageQuantity;
import static uk.nhs.adaptors.pss.translator.mapper.medication.MedicationMapperUtils.buildNotes;
import static uk.nhs.adaptors.pss.translator.mapper.medication.MedicationMapperUtils.buildPrescriptionTypeExtension;
import static uk.nhs.adaptors.pss.translator.mapper.medication.MedicationMapperUtils.extractEhrSupplyAuthoriseId;
import static uk.nhs.adaptors.pss.translator.mapper.medication.MedicationMapperUtils.extractMatchingDiscontinue;
import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.buildIdentifier;

@Service
@AllArgsConstructor
public class MedicationRequestPlanMapper {
    private static final String REPEAT_INFORMATION_URL
        = "https://fhir.nhs.uk/STU3/StructureDefinition/Extension-CareConnect-GPC-MedicationRepeatInformation-1";
    private static final String STATUS_CHANGE_URL
        = "https://fhir.nhs.uk/STU3/StructureDefinition/Extension-CareConnect-GPC-MedicationStatusReason-1";
    private static final String REPEATS_ISSUED_URL = "numberOfRepeatPrescriptionsIssued";
    private static final String REPEATS_ALLOWED_URL = "numberOfRepeatPrescriptionsAllowed";
    private static final String REPEATS_EXPIRY_DATE_URL = "authorisationExpiryDate";
    private static final String STATUS_CHANGE_DATE_URL = "statusChangeDate";
    private static final String STATUS_REASON = "statusReason";

    private static final String COMPLETE = "COMPLETE";
    private static final String NHS_PRESCRIPTION = "NHS prescription";
    private static final String PRESCRIPTION_TYPE = "Prescription type: ";
    private static final String MISSING_REASON_STRING = "No information available";

    private final MedicationMapper medicationMapper;
    private final ConfidentialityService confidentialityService;

    public MedicationRequest mapToPlanMedicationRequest(RCMRMT030101UKEhrExtract ehrExtract,
                                                        RCMRMT030101UKEhrComposition ehrComposition,
                                                        RCMRMT030101UKMedicationStatement medicationStatement,
                                                        RCMRMT030101UKAuthorise supplyAuthorise,
                                                        String practiseCode) {

        var ehrSupplyAuthoriseIdExtract = extractEhrSupplyAuthoriseId(supplyAuthorise);

        if (ehrSupplyAuthoriseIdExtract.isPresent()) {
            var ehrSupplyAuthoriseId = ehrSupplyAuthoriseIdExtract.get();
            var discontinue = extractMatchingDiscontinue(ehrSupplyAuthoriseId, ehrExtract);

            final var medicationRequest =
                initializeMedicationRequest(ehrComposition, medicationStatement, supplyAuthorise, practiseCode, ehrSupplyAuthoriseId);

            List<Extension> repeatInformationExtensions = new ArrayList<>();
            extractSupplyAuthoriseRepeatInformation(supplyAuthorise).ifPresent(repeatInformationExtensions::add);
            extractRepeatInformationIssued(ehrExtract, supplyAuthorise, ehrSupplyAuthoriseId).ifPresent(repeatInformationExtensions::add);
            extractAuthorisationExpiryDate(supplyAuthorise).ifPresent(repeatInformationExtensions::add);

            buildCondensedExtensions(REPEAT_INFORMATION_URL, repeatInformationExtensions)
                .ifPresent(medicationRequest::addExtension);

            List<Extension> statusChangeExtensions = discontinue
                .map(this::getStatusChangedExtensions)
                .orElse(Collections.emptyList());

            var status = discontinue.isPresent()
                ? buildMedicationRequestStatus(statusChangeExtensions)
                : buildMedicationRequestStatus(supplyAuthorise);
            medicationRequest.setStatus(status);

            buildCondensedExtensions(STATUS_CHANGE_URL, statusChangeExtensions)
                .ifPresent(medicationRequest::addExtension);
            buildPrescriptionTypeExtension(supplyAuthorise).ifPresent(medicationRequest::addExtension);

            buildNotesForAuthorise(supplyAuthorise).forEach(medicationRequest::addNote);
            extractPriorPrescription(supplyAuthorise).ifPresent(medicationRequest::setPriorPrescription);
            medicationMapper.extractMedicationReference(medicationStatement).ifPresent(medicationRequest::setMedication);

            return medicationRequest;
        }
        return null;
    }

    private MedicationRequest initializeMedicationRequest(RCMRMT030101UKEhrComposition ehrComposition,
                                                            RCMRMT030101UKMedicationStatement medicationStatement,
                                                            RCMRMT030101UKAuthorise supplyAuthorise, String practiseCode,
                                                            String ehrSupplyAuthoriseId) {

        final Meta meta = confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            MedicationMapperUtils.META_PROFILE,
            medicationStatement.getConfidentialityCode(),
            ehrComposition.getConfidentialityCode()
                                                                                                      );

        final var medicationRequest = new MedicationRequest();
        medicationRequest.addIdentifier(buildIdentifier(ehrSupplyAuthoriseId, practiseCode))
                         .setIntent(PLAN)
                         .addDosageInstruction(buildDosage(medicationStatement.getPertinentInformation()))
                         .setDispenseRequest(buildDispenseRequestForAuthorise(supplyAuthorise, medicationStatement))
                         .setMeta(meta)
                         .setId(ehrSupplyAuthoriseId);

        return medicationRequest;
    }

    private List<Extension> getStatusChangedExtensions(RCMRMT030101UKDiscontinue discontinue) {
        List<Extension> statusChangeExtensions = new ArrayList<>();
        var dateExt = buildStatusChangeDateExtension(discontinue);

        if (dateExt.isPresent()) {
            var reasonText = extractTermText(discontinue);

            statusChangeExtensions.add(dateExt.orElseThrow());
            statusChangeExtensions.add(buildStatusReasonCodeableConceptExtension(reasonText));
        }

        return statusChangeExtensions;
    }

    private Optional<Extension> extractSupplyAuthoriseRepeatInformation(RCMRMT030101UKAuthorise supplyAuthorise) {
        if (supplyAuthorise.hasRepeatNumber() && supplyAuthorise.getRepeatNumber().getValue().intValue() != 0) {
            return Optional.of(
                new Extension(REPEATS_ALLOWED_URL, new UnsignedIntType(supplyAuthorise.getRepeatNumber().getValue().intValue())));
        }
        return Optional.empty();
    }

    private Optional<Extension> extractRepeatInformationIssued(RCMRMT030101UKEhrExtract ehrExtract,
                                                               RCMRMT030101UKAuthorise supplyAuthorise, String supplyAuthoriseId) {

        if ((supplyAuthorise.hasRepeatNumber() && supplyAuthorise.getRepeatNumber().getValue().intValue() != 0)
            || !supplyAuthorise.hasRepeatNumber()) {

            var repeatCount = ehrExtract.getComponent().stream()
                .map(RCMRMT030101UKComponent::getEhrFolder)
                .map(RCMRMT030101UKEhrFolder::getComponent)
                .flatMap(List::stream)
                .map(RCMRMT030101UKComponent3::getEhrComposition)
                .map(RCMRMT030101UKEhrComposition::getComponent)
                .flatMap(List::stream)
                .flatMap(MedicationMapperUtils::extractAllMedications)
                .filter(Objects::nonNull)
                .map(RCMRMT030101UKMedicationStatement::getComponent)
                .flatMap(List::stream)
                .filter(prescribe -> hasInFulfillmentOfReference(prescribe, supplyAuthoriseId))
                .count();

            return Optional.of(
                new Extension(REPEATS_ISSUED_URL, new UnsignedIntType(repeatCount)));
        }
        return Optional.empty();
    }

    private Optional<Extension> extractAuthorisationExpiryDate(RCMRMT030101UKAuthorise supplyAuthorise) {

        if (supplyAuthorise.hasEffectiveTime() && supplyAuthorise.getEffectiveTime().hasHigh()) {
            return Optional.of(new Extension(
                REPEATS_EXPIRY_DATE_URL, DateFormatUtil.parseToDateTimeType(supplyAuthorise.getEffectiveTime().getHigh().getValue())));
        }

        return Optional.empty();
    }

    private String extractTermText(RCMRMT030101UKDiscontinue discontinue) {
        var pertinentInfo = discontinue.getPertinentInformation()
                .stream()
                .map(RCMRMT030101UKPertinentInformation2::getPertinentSupplyAnnotation)
                .map(RCMRMT030101UKSupplyAnnotation::getText)
                .filter(StringUtils::isNotBlank)
                .toList();

        var termText = new StringBuilder();

        if (discontinue.hasCode() && discontinue.getCode().hasOriginalText()) {
            final var originalText = discontinue.getCode().getOriginalText();

            final boolean hasIncumbentSystemDuplicatedOriginalTextWithinPertinentInfo = pertinentInfo.stream().anyMatch(
                annotation -> annotation.regionMatches(0, originalText, 0, originalText.length())
            );
            if (!hasIncumbentSystemDuplicatedOriginalTextWithinPertinentInfo) {
                termText.append('(').append(originalText).append(") ");
            }
        }

        termText.append(
            pertinentInfo.isEmpty()
                ? MISSING_REASON_STRING
                : String.join(",", pertinentInfo)
        );

        return termText.toString();
    }

    private Optional<Reference> extractPriorPrescription(RCMRMT030101UKAuthorise supplyAuthorise) {
        if (supplyAuthorise.hasPredecessor() && supplyAuthorise.getPredecessorFirstRep().hasPriorMedicationRef()
            && supplyAuthorise.getPredecessorFirstRep().getPriorMedicationRef().hasId()
            && supplyAuthorise.getPredecessorFirstRep().getPriorMedicationRef().getId().hasRoot()) {
            return Optional.of(new Reference(
                new IdType(ResourceType.MedicationRequest.name(),
                    supplyAuthorise.getPredecessorFirstRep().getPriorMedicationRef().getId().getRoot()
                )));
        }
        return Optional.empty();
    }

    private MedicationRequest.MedicationRequestStatus buildMedicationRequestStatus(RCMRMT030101UKAuthorise supplyAuthorise) {
        if (supplyAuthorise.hasStatusCode() && supplyAuthorise.getStatusCode().hasCode()
            && COMPLETE.equals(supplyAuthorise.getStatusCode().getCode())) {
            return COMPLETED;
        } else {
            return ACTIVE;
        }
    }

    private MedicationRequest.MedicationRequestStatus buildMedicationRequestStatus(List<Extension> statusChangedExt) {

        if (statusChangedExt.isEmpty()) {
            return COMPLETED;
        }

        return STOPPED;
    }

    private MedicationRequest.MedicationRequestDispenseRequestComponent buildDispenseRequestForAuthorise(
        RCMRMT030101UKAuthorise supplyAuthorise, RCMRMT030101UKMedicationStatement medicationStatement) {

        MedicationRequest.MedicationRequestDispenseRequestComponent dispenseRequest
            = new MedicationRequest.MedicationRequestDispenseRequestComponent();

        if (supplyAuthorise.hasQuantity()) {
            buildDosageQuantity(supplyAuthorise.getQuantity()).ifPresent(dispenseRequest::setQuantity);
        }

        var period = buildDispenseRequestPeriodEnd(supplyAuthorise, medicationStatement);
        MedicationMapperUtils.extractDispenseRequestPeriodStart(supplyAuthorise).ifPresent(period::setStartElement);

        return dispenseRequest.setValidityPeriod(period);
    }

    private Optional<Extension> buildCondensedExtensions(String url, List<Extension> extensionList) {
        if (!extensionList.isEmpty()) {
            return Optional.of((Extension) new Extension(url).setExtension(extensionList));
        }
        return Optional.empty();
    }

    private Optional<Extension> buildStatusChangeDateExtension(RCMRMT030101UKDiscontinue discontinue) {
        if (discontinue.hasAvailabilityTime() && discontinue.getAvailabilityTime().hasValue()) {
            return Optional.of(
                new Extension(STATUS_CHANGE_DATE_URL,
                    DateFormatUtil.parseToDateTimeType(discontinue.getAvailabilityTime().getValue()))
            );
        }
        return Optional.empty();
    }

    private Extension buildStatusReasonCodeableConceptExtension(String statusReason) {
        return new Extension(STATUS_REASON, new CodeableConcept().setText(statusReason));
    }

    private List<Annotation> buildNotesForAuthorise(RCMRMT030101UKAuthorise supplyAuthorise) {
        var notes = buildNotes(supplyAuthorise.getPertinentInformation());
        if (supplyAuthorise.hasCode() && supplyAuthorise.getCode().hasDisplayName()
            && !NHS_PRESCRIPTION.equalsIgnoreCase(supplyAuthorise.getCode().getDisplayName())) {
            notes.add(new Annotation(
                new StringType(PRESCRIPTION_TYPE + supplyAuthorise.getCode().getDisplayName())
            ));
        }
        return notes;
    }

    private boolean hasInFulfillmentOfReference(RCMRMT030101UKComponent2 component, String id) {

        return component.hasEhrSupplyPrescribe()
            && component.getEhrSupplyPrescribe().hasInFulfillmentOf()
            && component.getEhrSupplyPrescribe().getInFulfillmentOf().hasPriorMedicationRef()
            && component.getEhrSupplyPrescribe().getInFulfillmentOf().getPriorMedicationRef().hasId()
            && component.getEhrSupplyPrescribe().getInFulfillmentOf().getPriorMedicationRef().getId().hasRoot()
            && component.getEhrSupplyPrescribe().getInFulfillmentOf().getPriorMedicationRef().getId().getRoot().equals(id);
    }
}
