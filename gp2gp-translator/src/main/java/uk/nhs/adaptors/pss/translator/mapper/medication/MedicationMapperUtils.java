package uk.nhs.adaptors.pss.translator.mapper.medication;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hl7.fhir.dstu3.model.Annotation;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.Dosage;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.SimpleQuantity;
import org.hl7.fhir.dstu3.model.StringType;
import org.hl7.v3.II;
import org.hl7.v3.PQ;
import org.hl7.v3.RCMRMT030101UKAuthorise;
import org.hl7.v3.RCMRMT030101UKComponent;
import org.hl7.v3.RCMRMT030101UKComponent02;
import org.hl7.v3.RCMRMT030101UKComponent2;
import org.hl7.v3.RCMRMT030101UKComponent3;
import org.hl7.v3.RCMRMT030101UKComponent4;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKEhrFolder;
import org.hl7.v3.RCMRMT030101UKDiscontinue;
import org.hl7.v3.RCMRMT030101UKMedicationRef;
import org.hl7.v3.RCMRMT030101UKMedicationStatement;
import org.hl7.v3.RCMRMT030101UKPertinentInformation;
import org.hl7.v3.RCMRMT030101UKPertinentInformation2;
import org.hl7.v3.RCMRMT030101UKMedicationDosage;

import lombok.extern.slf4j.Slf4j;
import org.hl7.v3.RCMRMT030101UKReversalOf;
import org.hl7.v3.RCMRMT030101UKSupplyAnnotation;
import org.jetbrains.annotations.NotNull;
import uk.nhs.adaptors.pss.translator.util.CompoundStatementUtil;
import uk.nhs.adaptors.pss.translator.util.DateFormatUtil;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MedicationMapperUtils {

    public static final String META_PROFILE = "MedicationRequest-1";
    private static final String ACUTE = "Acute";
    private static final String REPEAT = "Repeat";
    private static final String NO_INFORMATION_AVAILABLE = "No Information available";
    private static final String PRESCRIPTION_TYPE_EXTENSION_URL
        = "https://fhir.nhs.uk/STU3/StructureDefinition/Extension-CareConnect-GPC-PrescriptionType-1";
    private static final String PRESCRIPTION_TYPE_CODING_SYSTEM
        = "https://fhir.nhs.uk/STU3/CodeSystem/CareConnect-PrescriptionType-1";

    public static Optional<Extension> buildPrescriptionTypeExtension(RCMRMT030101UKAuthorise supplyAuthorise) {
        if (supplyAuthorise != null && isAcutePrescription(supplyAuthorise)) {
            return Optional.of(new Extension(PRESCRIPTION_TYPE_EXTENSION_URL, new CodeableConcept(
                new Coding(PRESCRIPTION_TYPE_CODING_SYSTEM, ACUTE.toLowerCase(), ACUTE)
            )));
        }
        return Optional.of(new Extension(PRESCRIPTION_TYPE_EXTENSION_URL, new CodeableConcept(
            new Coding(PRESCRIPTION_TYPE_CODING_SYSTEM, REPEAT.toLowerCase(), REPEAT)
        )));
    }

    public static boolean isAcutePrescription(@NotNull RCMRMT030101UKAuthorise supplyAuthorise) {
        return supplyAuthorise.hasRepeatNumber()
            && supplyAuthorise.getRepeatNumber().getValue().intValue() == 0;
    }

    public static List<Annotation> buildNotes(List<RCMRMT030101UKPertinentInformation2> pertinentInformationList) {
        return pertinentInformationList
            .stream()
            .filter(RCMRMT030101UKPertinentInformation2::hasPertinentSupplyAnnotation)
            .map(RCMRMT030101UKPertinentInformation2::getPertinentSupplyAnnotation)
            .filter(RCMRMT030101UKSupplyAnnotation::hasText)
            .map(RCMRMT030101UKSupplyAnnotation::getText)
            .map(text -> text + System.lineSeparator())
            .map(StringType::new)
            .map(Annotation::new)
            .collect(Collectors.toList());
    }

    public static Dosage buildDosage(List<RCMRMT030101UKPertinentInformation> pertinentInformationList) {
        Dosage dosage = new Dosage();
        var pertinentInformationDosage = pertinentInformationList.stream()
            .filter(RCMRMT030101UKPertinentInformation::hasPertinentMedicationDosage)
            .map(RCMRMT030101UKPertinentInformation::getPertinentMedicationDosage)
            .filter(RCMRMT030101UKMedicationDosage::hasText)
            .map(RCMRMT030101UKMedicationDosage::getText)
            .findFirst();

        pertinentInformationDosage.ifPresentOrElse(dosage::setText,
            () -> dosage.setText(NO_INFORMATION_AVAILABLE));

        return dosage;
    }

    public static Optional<SimpleQuantity> buildDosageQuantity(PQ quantitySupplied) {
        SimpleQuantity quantity = new SimpleQuantity();
        quantity.setValue(Double.parseDouble(quantitySupplied.getValue()));
        if (quantitySupplied.hasTranslation()
            && quantitySupplied.getTranslation().getFirst().hasOriginalText()) {
            quantity.setUnit(quantitySupplied.getTranslation().getFirst().getOriginalText());
        }
        return Optional.of(quantity);
    }

    public static Optional<String> extractEhrSupplyAuthoriseId(RCMRMT030101UKAuthorise supplyAuthorise) {
        if (supplyAuthorise.hasId() && supplyAuthorise.getId().hasRoot()) {
            return Optional.of(supplyAuthorise.getId().getRoot());
        }
        return Optional.empty();
    }

    public static RCMRMT030101UKAuthorise extractSupplyAuthorise(RCMRMT030101UKEhrExtract ehrExtract, String id) {

        return ehrExtract.getComponent()
            .stream()
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
            .map(RCMRMT030101UKComponent2::getEhrSupplyAuthorise)
            .filter(Objects::nonNull)
            .filter(authorise -> authorise.getId().getRoot().equals(id))
            .findFirst()
            .orElse(null);
    }

    public static Optional<DateTimeType> extractDispenseRequestPeriodStart(RCMRMT030101UKAuthorise supplyAuthorise) {
        if (supplyAuthorise.hasEffectiveTime() && supplyAuthorise.getEffectiveTime().hasCenter()
            && supplyAuthorise.getEffectiveTime().getCenter().hasValue()) {
            return Optional.of(DateFormatUtil.parseToDateTimeType(supplyAuthorise.getEffectiveTime().getCenter().getValue()));
        }
        if (supplyAuthorise.hasEffectiveTime() && supplyAuthorise.getEffectiveTime().hasLow()
            && supplyAuthorise.getEffectiveTime().getLow().hasValue()) {
            return Optional.of(DateFormatUtil.parseToDateTimeType(supplyAuthorise.getEffectiveTime().getLow().getValue()));
        }
        if (supplyAuthorise.hasAvailabilityTime() && supplyAuthorise.getAvailabilityTime().hasValue()) {
            return Optional.of(DateFormatUtil.parseToDateTimeType(supplyAuthorise.getAvailabilityTime().getValue()));
        }
        return Optional.empty();
    }

    public static Stream<RCMRMT030101UKMedicationStatement> extractAllMedications(RCMRMT030101UKComponent4 component4) {

        return Stream.concat(
            Stream.of(component4.getMedicationStatement()),
            extractNestedMedications(component4)
        );
    }

    public static Period buildDispenseRequestPeriodEnd(RCMRMT030101UKAuthorise supplyAuthorise,
                                                       RCMRMT030101UKMedicationStatement medicationStatement) {

        if (supplyAuthorise.hasEffectiveTime() && supplyAuthorise.getEffectiveTime().hasHigh()) {

            String effectiveTimeValue = supplyAuthorise.getEffectiveTime().getHigh().getValue();
            String effectiveTimeValueSuffix = "000000"; //GP2GP can malform the value and add this suffix.

            String effectiveTimeEnd =
                    effectiveTimeValue.endsWith(effectiveTimeValueSuffix)
                        ? effectiveTimeValue.substring(0, effectiveTimeValue.length() - effectiveTimeValueSuffix.length())
                                : effectiveTimeValue;

            return new Period().setEndElement(
                DateFormatUtil.parseToDateTimeType(effectiveTimeEnd));
        }

        if (medicationStatement.hasEffectiveTime() && medicationStatement.getEffectiveTime().hasHigh()) {
            return new Period().setEndElement(
                DateFormatUtil.parseToDateTimeType(medicationStatement.getEffectiveTime().getHigh().getValue()));
        }
        return new Period();
    }

    public static Optional<Period> buildMedicationStatementEffectivePeriodEnd(RCMRMT030101UKDiscontinue supplyDiscontinue) {
        if (supplyDiscontinue.hasAvailabilityTime() && supplyDiscontinue.getAvailabilityTime().hasValue()) {
            return Optional.of(new Period().setEndElement(
               DateFormatUtil.parseToDateTimeType(supplyDiscontinue.getAvailabilityTime().getValue())
            ));
        }

        return Optional.empty();
    }

    public static Optional<RCMRMT030101UKDiscontinue> extractMatchingDiscontinue(String supplyAuthoriseId,
                                                                                 RCMRMT030101UKEhrExtract ehrExtract) {
        return ehrExtract.getComponent()
            .stream()
            .filter(RCMRMT030101UKComponent::hasEhrFolder)
            .map(RCMRMT030101UKComponent::getEhrFolder)
            .map(RCMRMT030101UKEhrFolder::getComponent)
            .flatMap(List::stream)
            .filter(RCMRMT030101UKComponent3::hasEhrComposition)
            .map(RCMRMT030101UKComponent3::getEhrComposition)
            .map(RCMRMT030101UKEhrComposition::getComponent)
            .flatMap(List::stream)
            .filter(RCMRMT030101UKComponent4::hasMedicationStatement)
            .map(RCMRMT030101UKComponent4::getMedicationStatement)
            .map(RCMRMT030101UKMedicationStatement::getComponent)
            .flatMap(List::stream)
            .filter(RCMRMT030101UKComponent2::hasEhrSupplyDiscontinue)
            .map(RCMRMT030101UKComponent2::getEhrSupplyDiscontinue)
            .filter(discontinue1 -> hasReversalIdMatchingAuthorise(discontinue1.getReversalOf(), supplyAuthoriseId))
            .findFirst();
    }

    private static boolean hasReversalIdMatchingAuthorise(List<RCMRMT030101UKReversalOf> reversalOf, String supplyAuthoriseId) {
        return reversalOf.stream()
            .map(RCMRMT030101UKReversalOf::getPriorMedicationRef)
            .map(RCMRMT030101UKMedicationRef::getId)
            .map(II::getRoot)
            .anyMatch(supplyAuthoriseId::equals);
    }

    public static List<RCMRMT030101UKMedicationStatement> getMedicationStatements(RCMRMT030101UKEhrExtract ehrExtract) {
        return ehrExtract.getComponent()
            .stream()
            .map(RCMRMT030101UKComponent::getEhrFolder)
            .map(RCMRMT030101UKEhrFolder::getComponent)
            .flatMap(List::stream)
            .map(RCMRMT030101UKComponent3::getEhrComposition)
            .map(RCMRMT030101UKEhrComposition::getComponent)
            .flatMap(List::stream)
            .flatMap(MedicationMapperUtils::extractAllMedications)
            .filter(Objects::nonNull)
            .toList();
    }

    private static Stream<RCMRMT030101UKMedicationStatement> extractNestedMedications(RCMRMT030101UKComponent4 component4) {

        return component4.hasCompoundStatement()
            ? CompoundStatementUtil.extractResourcesFromCompound(component4.getCompoundStatement(),
                RCMRMT030101UKComponent02::hasMedicationStatement, RCMRMT030101UKComponent02::getMedicationStatement)
            .stream()
            .map(RCMRMT030101UKMedicationStatement.class::cast)
            : Stream.empty();
    }
}
