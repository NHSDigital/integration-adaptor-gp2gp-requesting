package uk.nhs.adaptors.pss.translator.mapper.medication;

import static org.hl7.fhir.dstu3.model.MedicationRequest.MedicationRequestIntent.ORDER;
import static org.hl7.fhir.dstu3.model.MedicationRequest.MedicationRequestIntent.PLAN;
import static org.hl7.fhir.dstu3.model.MedicationStatement.MedicationStatementStatus.ACTIVE;
import static uk.nhs.adaptors.pss.translator.util.CompoundStatementResourceExtractors.extractAllMedications;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.DomainResource;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.Medication;
import org.hl7.fhir.dstu3.model.MedicationRequest;
import org.hl7.fhir.dstu3.model.MedicationStatement;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.Resource;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.hl7.v3.RCMRMT030101UKComponent2;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKMedicationStatement;
import org.hl7.v3.TS;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import uk.nhs.adaptors.pss.translator.mapper.AbstractMapper;
import uk.nhs.adaptors.pss.translator.service.IdGeneratorService;
import uk.nhs.adaptors.pss.translator.util.DateFormatUtil;

@Slf4j
@Service
@AllArgsConstructor
public class MedicationRequestMapper extends AbstractMapper<DomainResource> {
    private MedicationMapper medicationMapper;
    private MedicationRequestOrderMapper medicationRequestOrderMapper;
    private MedicationRequestPlanMapper medicationRequestPlanMapper;
    private MedicationStatementMapper medicationStatementMapper;
    private MedicationMapperContext medicationMapperContext;
    private final IdGeneratorService idGeneratorService;

    private static final String PPRF = "PPRF";
    private static final String PRF = "PRF";

    private static final String PRESCRIPTION_TYPE_EXTENSION_URL
        = "https://fhir.nhs.uk/STU3/StructureDefinition/Extension-CareConnect-GPC-PrescriptionType-1";
    private static final String PRESCRIPTION_TYPE_CODING_SYSTEM
        = "https://fhir.nhs.uk/STU3/CodeSystem/CareConnect-PrescriptionType-1";
    private static final String MEDICATION_STATEMENT_LAST_ISSUE_DATE_URL =
        "https://fhir.nhs.uk/STU3/StructureDefinition/Extension-CareConnect-GPC-MedicationStatementLastIssueDate-1";



    public List<DomainResource> mapResources(RCMRMT030101UKEhrExtract ehrExtract, Patient patient, List<Encounter> encounters,
        String practiseCode) {
        try {
            var resources = mapEhrExtractToFhirResource(ehrExtract, (extract, composition, component) ->
                extractAllMedications(component)
                    .filter(Objects::nonNull)
                    .flatMap(medicationStatement -> mapMedicationStatement(
                        ehrExtract, composition, medicationStatement, patient, encounters, practiseCode)))
                .collect(Collectors.toCollection(ArrayList::new));

            generateResourcesForMultipleOrdersLinkedToASingleAcutePlan(resources);

            return resources;
        } finally {
            medicationMapperContext.reset();
        }
    }

    private void generateResourcesForMultipleOrdersLinkedToASingleAcutePlan(ArrayList<DomainResource> resources) {
        var acutePlans = getAcutePlans(resources);
        var medicationStatements = getMedicationStatements(resources);
        var ordersGroupedByAcutePlan = getOrdersGroupedByReferencedAcutePlan(resources, acutePlans);

        ordersGroupedByAcutePlan
            .forEach((plan, orders) -> {
                if (orders.size() > 1) {
                    sortOrdersByValidityPeriodStart(orders);

                    // the index starts at one as the earliest order remains referenced to the original plan
                    for (var index = 1; index < orders.size(); index++) {
                        var duplicatedPlan = duplicateOrderBasedOnSharedAcutePlan(plan, orders, index);
                        var duplicatedMedicationStatement = duplicateMedicationStatementForOrderBasedOnSharedAcutePlan(
                                medicationStatements,
                                orders.get(index),
                                plan,
                                duplicatedPlan
                            );
                        resources.add(duplicatedPlan);
                        resources.add(duplicatedMedicationStatement);
                    }

                    final var originalMedicationStatement = getMedicationStatementByPlanId(medicationStatements, plan.getId());
                    originalMedicationStatement.getExtensionByUrl(MEDICATION_STATEMENT_LAST_ISSUE_DATE_URL).setValue(
                        orders.get(0).getDispenseRequest().getValidityPeriod().getStartElement()
                    );
                }
            });
    }

    private @NotNull MedicationRequest duplicateOrderBasedOnSharedAcutePlan(
        MedicationRequest plan,
        List<MedicationRequest> orders,
        int index
    ) {
        var duplicatedPlan = plan.copy();
        var duplicatedPlanId = idGeneratorService.generateUuid();
        duplicatedPlan.setId(duplicatedPlanId);
        duplicatedPlan.getIdentifierFirstRep().setValue(duplicatedPlanId);
        var basedOn = orders.get(index).getBasedOn();
        updateBasedOnReferenceToReferenceDuplicatedPlan(basedOn, duplicatedPlanId);
        var previousOrderBasedOn = orders.get(index - 1).getBasedOn();
        var previousBasedOnReference = getMedicationRequestBasedOnReference(previousOrderBasedOn);
        previousBasedOnReference.ifPresent(duplicatedPlan::setPriorPrescription);
        var validityPeriod = orders.get(index).getDispenseRequest().getValidityPeriod();
        duplicatedPlan.getDispenseRequest().setValidityPeriod(validityPeriod);

        LOGGER.info(
            "Generated MedicationRequest(Plan) with Id: {} for MedicationRequest(Order) with Id: {}",
            duplicatedPlan.getId(),
            orders.get(index).getId()
        );

        return duplicatedPlan;
    }

    private static void updateBasedOnReferenceToReferenceDuplicatedPlan(List<Reference> basedOn, String duplicatedPlanId) {
        var basedOnReference = getMedicationRequestBasedOnReference(basedOn);

        if (basedOnReference.isPresent()) {
            var replacementIndex = basedOn.indexOf(basedOnReference.get());
            basedOn.set(replacementIndex, new Reference(
                    new IdType(ResourceType.MedicationRequest.name(), duplicatedPlanId)
                )
            );
        }
    }

    private @NotNull MedicationStatement duplicateMedicationStatementForOrderBasedOnSharedAcutePlan(
        List<MedicationStatement> medicationStatements,
        MedicationRequest order,
        MedicationRequest originalPlan,
        MedicationRequest duplicatedPlan
    ) {
        var originalMedicationStatement = getMedicationStatementByPlanId(medicationStatements, originalPlan.getId());

        var duplicatedMedicationStatement = originalMedicationStatement.copy();

        duplicatedMedicationStatement.setId(duplicatedPlan.getId() + "-MS");
        duplicatedMedicationStatement.getIdentifierFirstRep().setValue(duplicatedPlan.getId() + "-MS");
        duplicatedMedicationStatement
            .getExtensionByUrl(MEDICATION_STATEMENT_LAST_ISSUE_DATE_URL)
            .setValue(order.getDispenseRequest().getValidityPeriod().getStartElement());
        updateDuplicatedMedicationStatementEffectivePeriod(duplicatedMedicationStatement, order);
        updateBasedOnReferenceToReferenceDuplicatedPlan(
            duplicatedMedicationStatement.getBasedOn(),
            duplicatedPlan.getId()
        );

        LOGGER.info(
            "Generated MedicationStatement with Id: {} for MedicationRequest(Order) with Id: {}",
            duplicatedMedicationStatement.getId(),
            order.getId()
        );

        return duplicatedMedicationStatement;
    }

    private static void updateDuplicatedMedicationStatementEffectivePeriod(
        MedicationStatement duplicatedMedicationStatement,
        MedicationRequest order

    ) {
        duplicatedMedicationStatement.setEffective(order.getDispenseRequest().getValidityPeriod().copy());

        var effectivePeriod = duplicatedMedicationStatement.getEffectivePeriod();
        if (!ACTIVE.equals(duplicatedMedicationStatement.getStatus()) && !effectivePeriod.hasEnd()) {
            effectivePeriod.setEndElement(effectivePeriod.getStartElement());
        }
    }

    private static MedicationStatement getMedicationStatementByPlanId(
        List<MedicationStatement> medicationStatements,
        String originalPlanId
    ) {
        // There will always be a medication statement related to a plan
        //noinspection OptionalGetWithoutIsPresent
        return medicationStatements
            .stream()
            .filter(medicationStatement -> medicationStatement.getId().equals(originalPlanId + "-MS"))
            .findFirst()
            .get();
    }

    private static @NotNull Optional<Reference> getMedicationRequestBasedOnReference(List<Reference> basedOn) {
        return basedOn
            .stream()
            .filter(reference ->
                ResourceType.MedicationRequest.name()
                    .equals(reference.getReferenceElement().getResourceType())
            )
            .findFirst();
    }

    // for Orders, GP Connect specification state that validity period will always have a start date.
    private void sortOrdersByValidityPeriodStart(List<MedicationRequest> orders) {
        orders.sort(Comparator.comparing(medicationRequest ->
            medicationRequest.getDispenseRequest().getValidityPeriod().getStart()
        ));
    }

    private List<MedicationRequest> getAcutePlans(List<DomainResource> resources) {
        return resources.stream()
            .filter(MedicationRequest.class::isInstance)
            .map(MedicationRequest.class::cast)
            .filter(this::isAcutePlan)
            .toList();
    }

    private List<MedicationStatement> getMedicationStatements(List<DomainResource> resources) {
        return resources.stream()
            .filter(MedicationStatement.class::isInstance)
            .map(MedicationStatement.class::cast)
            .toList();
    }

    private Map<MedicationRequest, List<MedicationRequest>> getOrdersGroupedByReferencedAcutePlan(
        List<DomainResource> resources,
        List<MedicationRequest> acutePlans
    ) {
        return resources.stream()
            .filter(MedicationRequest.class::isInstance)
            .map(MedicationRequest.class::cast)
            .filter(this::isOrder)
            .filter(MedicationRequest::hasBasedOn)
            .map(order -> getOrderToAcutePlanMapEntry(order, acutePlans))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(
                Collectors.groupingBy(
                    Map.Entry::getKey,
                    () -> new TreeMap<>(Comparator.comparing(Resource::getId)),
                    Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                )
            );
    }

    private Optional<AbstractMap.SimpleEntry<MedicationRequest, MedicationRequest>> getOrderToAcutePlanMapEntry(
        MedicationRequest order,
        List<MedicationRequest> plans
    ) {
        if (!order.hasBasedOn()) {
            return Optional.empty();
        }

        var planReferenceId =  order
            .getBasedOn()
            .stream()
            .map(reference -> reference.getReference().split("/")[1])
            .findFirst()
            .orElse(StringUtils.EMPTY);

        if (StringUtils.isEmpty(planReferenceId)) {
            return Optional.empty();
        }

        var referencedPlan = plans.stream()
            .filter(plan -> planReferenceId.equals(plan.getId()))
            .findFirst();

        return referencedPlan
            .map(medicationRequest -> new AbstractMap.SimpleEntry<>(medicationRequest, order));

    }

    private Stream<DomainResource> mapMedicationStatement(RCMRMT030101UKEhrExtract ehrExtract,
                                                          RCMRMT030101UKEhrComposition ehrComposition,
                                                          RCMRMT030101UKMedicationStatement medicationStatement,
                                                          Patient subject, List<Encounter> encounters, String practiseCode) {

        var context = encounters.stream()
            .filter(encounter1 -> encounter1.getId().equals(ehrComposition.getId().getRoot())).findFirst();
        var authoredOn = getAuthoredOn(medicationStatement.getAvailabilityTime(), ehrComposition);
        var dateAsserted = extractDateAsserted(ehrComposition, ehrExtract);
        var requester = extractRequester(ehrComposition, medicationStatement);
        var recorder = extractRecorder(ehrComposition, medicationStatement);

        List<Medication> medications = mapMedications(medicationStatement);

        List<MedicationRequest> medicationRequestsOrder = mapMedicationRequestsOrder(
            ehrExtract,
            ehrComposition,
            medicationStatement,
            practiseCode
        );

        List<MedicationRequest> medicationRequestsPlan = mapMedicationRequestsPlan(
            ehrExtract,
            ehrComposition,
            medicationStatement,
            practiseCode
        );

        List<MedicationStatement> medicationStatements = mapMedicationStatements(
            ehrExtract,
            ehrComposition,
            medicationStatement,
            context,
            subject,
            authoredOn,
            practiseCode,
            dateAsserted
        );

        return Stream.of(medications, medicationRequestsOrder, medicationRequestsPlan, medicationStatements)
            .flatMap(List::stream)
            .map(DomainResource.class::cast)
            .map(medicationRequest -> setCommonFields(
                medicationRequest, requester, recorder, subject, context, authoredOn));
    }

    private DomainResource setCommonFields(DomainResource resource, Optional<Reference> requester,
        Optional<Reference> recorder, Patient patient, Optional<Encounter> context, DateTimeType authoredOn) {

        if (ResourceType.MedicationRequest.equals(resource.getResourceType())) {
            ((MedicationRequest) resource).setSubject(new Reference(patient));
            context.ifPresent(context1 -> ((MedicationRequest) resource).setContext(new Reference(context1)));

            requester.map(MedicationRequest.MedicationRequestRequesterComponent::new)
                .ifPresent(((MedicationRequest) resource)::setRequester);
            recorder.ifPresent(((MedicationRequest) resource)::setRecorder);
            ((MedicationRequest) resource).setAuthoredOnElement(authoredOn);
        }

        return resource;
    }


    private DateTimeType getAuthoredOn(TS availabilityTime,
                                       RCMRMT030101UKEhrComposition ehrComposition) {
        if (availabilityTime != null && availabilityTime.hasValue()) {
            return DateFormatUtil.parseToDateTimeType(availabilityTime.getValue());
        } else {
            if (ehrComposition.getAvailabilityTime() != null && ehrComposition.getAvailabilityTime().hasValue()) {
                return DateFormatUtil.parseToDateTimeType(ehrComposition.getAvailabilityTime().getValue());
            }
            if (ehrComposition.hasAuthor() && ehrComposition.getAuthor().hasTime() && ehrComposition.getAuthor().getTime().hasValue()) {
                return DateFormatUtil.parseToDateTimeType(ehrComposition.getAuthor().getTime().getValue());
            }
        }

        return null;
    }

    private DateTimeType extractDateAsserted(RCMRMT030101UKEhrComposition ehrComposition, RCMRMT030101UKEhrExtract ehrExtract) {

        if (ehrComposition.hasAuthor() && ehrComposition.getAuthor().hasTime() && ehrComposition.getAuthor().getTime().hasValue()) {
            return DateFormatUtil.parseToDateTimeType(ehrComposition.getAuthor().getTime().getValue());
        } else {
            if (ehrExtract.getAvailabilityTime().hasValue()) {
                return DateFormatUtil.parseToDateTimeType(ehrExtract.getAvailabilityTime().getValue());
            }
            return null;
        }
    }

    private List<Medication> mapMedications(RCMRMT030101UKMedicationStatement medicationStatement) {
        return medicationStatement.getConsumable()
            .stream()
            .map(medicationMapper::createMedication)
            .filter(Objects::nonNull)
            .toList();
    }

    private List<MedicationRequest> mapMedicationRequestsOrder(RCMRMT030101UKEhrExtract ehrExtract,
                                                               RCMRMT030101UKEhrComposition ehrComposition,
                                                               RCMRMT030101UKMedicationStatement medicationStatement,
                                                               String practiseCode) {

        return medicationStatement.getComponent()
            .stream()
            .filter(RCMRMT030101UKComponent2::hasEhrSupplyPrescribe)
            .map(RCMRMT030101UKComponent2::getEhrSupplyPrescribe)
            .map(supplyPrescribe -> medicationRequestOrderMapper.mapToOrderMedicationRequest(
                ehrExtract,
                ehrComposition,
                medicationStatement,
                supplyPrescribe,
                practiseCode)
            ).filter(Objects::nonNull)
            .toList();
    }

    private List<MedicationRequest> mapMedicationRequestsPlan(RCMRMT030101UKEhrExtract ehrExtract,
        RCMRMT030101UKEhrComposition ehrComposition,
        RCMRMT030101UKMedicationStatement medicationStatement, String practiseCode) {

        return medicationStatement.getComponent()
            .stream()
            .filter(RCMRMT030101UKComponent2::hasEhrSupplyAuthorise)
            .map(RCMRMT030101UKComponent2::getEhrSupplyAuthorise)
            .map(supplyAuthorise -> medicationRequestPlanMapper.mapToPlanMedicationRequest(ehrExtract, ehrComposition, medicationStatement,
                supplyAuthorise, practiseCode))
            .filter(Objects::nonNull)
            .toList();
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private List<MedicationStatement> mapMedicationStatements(RCMRMT030101UKEhrExtract ehrExtract,
          RCMRMT030101UKEhrComposition ehrComposition,
          RCMRMT030101UKMedicationStatement medicationStatement, Optional<Encounter> context, Patient subject,
          DateTimeType authoredOn, String practiseCode, DateTimeType dateAsserted) {

        return medicationStatement.getComponent()
            .stream()
            .filter(RCMRMT030101UKComponent2::hasEhrSupplyAuthorise)
            .map(RCMRMT030101UKComponent2::getEhrSupplyAuthorise)
            .map(supplyAuthorise -> medicationStatementMapper
                .mapToMedicationStatement(ehrExtract, ehrComposition, medicationStatement, supplyAuthorise, practiseCode, authoredOn))
            .filter(Objects::nonNull)
            .peek(medicationStatement1 -> {
                context.ifPresent(context1 -> medicationStatement1.setContext(new Reference(context1)));
                medicationStatement1.setSubject(new Reference(subject));
                medicationStatement1.setDateAssertedElement(dateAsserted);
            })
            .toList();
    }

    private Optional<Reference> extractRequester(RCMRMT030101UKEhrComposition ehrComposition,
        RCMRMT030101UKMedicationStatement medicationStatement) {

        if (medicationStatement.hasParticipant()) {
            var pprfRequester = medicationStatement.getParticipant()
                .stream()
                .filter(participant -> !participant.hasNullFlavour())
                .filter(participant -> participant.getTypeCode().stream().anyMatch(PPRF::equals)
                    || participant.getTypeCode().stream().anyMatch(PRF::equals))
                .findFirst();
            if (pprfRequester.isPresent()) {
                return pprfRequester
                    .map(requester -> new IdType(ResourceType.Practitioner.name(), requester.getAgentRef().getId().getRoot()))
                    .map(Reference::new);
            }
        }

        if (ehrComposition.hasParticipant2()) {
            var requester = ehrComposition.getParticipant2()
                .stream()
                .filter(participant -> !participant.hasNullFlavor())
                .findFirst();

            if (requester.isPresent()) {
                return requester
                    .map(requester1 -> new IdType(ResourceType.Practitioner.name(), requester1.getAgentRef().getId().getRoot()))
                    .map(Reference::new);
            }
        }
        return Optional.empty();
    }

    private Optional<Reference> extractRecorder(RCMRMT030101UKEhrComposition ehrComposition,
                                                RCMRMT030101UKMedicationStatement medicationStatement) {

        return extractRequester(ehrComposition, medicationStatement);
    }

    private boolean isAcutePlan(MedicationRequest medicationRequest) {
        if (medicationRequest.hasExtension(PRESCRIPTION_TYPE_EXTENSION_URL) && PLAN.equals(medicationRequest.getIntent())) {
            var extensionCodeableConcept = (CodeableConcept) medicationRequest
                .getExtensionByUrl(PRESCRIPTION_TYPE_EXTENSION_URL)
                .getValue();
            return (extensionCodeableConcept.hasCoding(PRESCRIPTION_TYPE_CODING_SYSTEM, "acute"));
        }

        return false;
    }

    private boolean isOrder(MedicationRequest medicationRequest) {
        return ORDER.equals(medicationRequest.getIntent());
    }
}
