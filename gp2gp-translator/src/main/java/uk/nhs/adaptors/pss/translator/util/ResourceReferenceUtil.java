package uk.nhs.adaptors.pss.translator.util;

import static uk.nhs.adaptors.pss.translator.util.ResourceFilterUtil.isAllergyIntolerance;
import static uk.nhs.adaptors.pss.translator.util.ResourceFilterUtil.isBloodPressure;
import static uk.nhs.adaptors.pss.translator.util.ResourceFilterUtil.isDiagnosticReport;
import static uk.nhs.adaptors.pss.translator.util.ResourceFilterUtil.isDocumentReference;
import java.util.List;

import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.hl7.v3.CR;
import org.hl7.v3.RCMRMT030101UKCompoundStatement;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKLinkSet;
import org.hl7.v3.RCMRMT030101UKMedicationStatement;
import org.hl7.v3.RCMRMT030101UKNarrativeStatement;
import org.hl7.v3.RCMRMT030101UKObservationStatement;
import org.hl7.v3.RCMRMT030101UKPlanStatement;
import org.hl7.v3.RCMRMT030101UKRequestStatement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ResourceReferenceUtil {

    private static final String QUESTIONNAIRE_ID = "%s-QRSP";
    private static final String OBSERVATION_REFERENCE = "Observation/%s";
    private static final String QUESTIONNAIRE_REFERENCE = "QuestionnaireResponse/%s";
    private static final String SELF_REFERRAL = "SelfReferral";

    private final DatabaseImmunizationChecker immunizationChecker;

    public void extractChildReferencesFromEhrComposition(RCMRMT030101UKEhrComposition ehrComposition,
                                                         List<Reference> entryReferences) {

        ehrComposition.getComponent().forEach(component -> {
            addPlanStatementEntry(component.getPlanStatement(), entryReferences);
            addRequestStatementEntry(component.getRequestStatement(), entryReferences);
            addLinkSetEntry(component.getLinkSet(), entryReferences);
            addObservationStatementEntry(component.getObservationStatement(), entryReferences, null);
            addNarrativeStatementEntry(component.getNarrativeStatement(), entryReferences);
            addMedicationEntry(component.getMedicationStatement(), entryReferences);
            extractChildReferencesFromCompoundStatement(component.getCompoundStatement(), entryReferences);
        });
    }

    public void extractChildReferencesFromCompoundStatement(RCMRMT030101UKCompoundStatement compoundStatement,
                                                            List<Reference> entryReferences) {
        if (compoundStatement != null) {
            if (isDiagnosticReport(compoundStatement)) {
                addDiagnosticReportEntry(compoundStatement, entryReferences);
            } else {

                compoundStatement.getComponent().forEach(component -> {
                    addObservationStatementEntry(
                        component.getObservationStatement(), entryReferences, compoundStatement);
                    addPlanStatementEntry(component.getPlanStatement(), entryReferences);
                    addRequestStatementEntry(component.getRequestStatement(), entryReferences);
                    addLinkSetEntry(component.getLinkSet(), entryReferences);
                    addMedicationEntry(component.getMedicationStatement(), entryReferences);

                    if (isNotIgnoredResource(compoundStatement, entryReferences)) {
                        addNarrativeStatementEntry(component.getNarrativeStatement(), entryReferences);
                    }

                    extractChildReferencesFromCompoundStatement(component.getCompoundStatement(), entryReferences);
                });
            }
        }
    }

    public void extractChildReferencesFromTemplate(RCMRMT030101UKCompoundStatement compoundStatement,
                                                   List<Reference> entryReferences) {

        compoundStatement.getComponent().forEach(component -> {
            addObservationStatementEntry(component.getObservationStatement(), entryReferences, compoundStatement);
            addPlanStatementEntry(component.getPlanStatement(), entryReferences);
            addRequestStatementEntry(component.getRequestStatement(), entryReferences);
            addLinkSetEntry(component.getLinkSet(), entryReferences);
            addMedicationEntry(component.getMedicationStatement(), entryReferences);
            addNarrativeStatementEntry(component.getNarrativeStatement(), entryReferences);
            extractChildReferencesFromCompoundStatement(component.getCompoundStatement(), entryReferences);
        });
    }

    private static boolean isNotIgnoredResource(RCMRMT030101UKCompoundStatement compoundStatement,
                                                List<Reference> entryReferences) {

        var references = entryReferences.stream()
            .map(Reference::getReference)
            .toList();

        return compoundStatement == null
            || references.contains(QUESTIONNAIRE_ID.formatted(QUESTIONNAIRE_REFERENCE.formatted(
            compoundStatement.getId().getFirst().getRoot())))
            || !references.contains(OBSERVATION_REFERENCE.formatted(compoundStatement.getId().getFirst().getRoot()));
    }

    private void addObservationStatementEntry(RCMRMT030101UKObservationStatement observationStatement,
                                              List<Reference> entryReferences, RCMRMT030101UKCompoundStatement compoundStatement) {
        if (observationStatement != null && isNotIgnoredResource(compoundStatement, entryReferences)) {
            if (isBloodPressure(compoundStatement)) {
                addBloodPressureEntry(compoundStatement, entryReferences);
            } else if (isAllergyIntolerance(compoundStatement)) {
                addAllergyIntoleranceEntry(compoundStatement, entryReferences);
            } else if (observationStatement.hasCode() && immunizationChecker.isImmunization(observationStatement)) {
                addImmunizationEntry(observationStatement, entryReferences);
            } else {
                addUncategorisedObservationEntry(observationStatement, entryReferences);
            }
        }
    }

    private static void addAllergyIntoleranceEntry(RCMRMT030101UKCompoundStatement compoundStatement,
                                                   List<Reference> entryReferences) {

        var observationStatementPart = compoundStatement.getComponent().getFirst().getObservationStatement();

        entryReferences.add(createResourceReference(ResourceType.AllergyIntolerance.name(),
                observationStatementPart.getId().getRoot()));
    }

    private static void addDiagnosticReportEntry(RCMRMT030101UKCompoundStatement compoundStatement,
                                                 List<Reference> entryReferences) {
        entryReferences.add(createResourceReference(ResourceType.DiagnosticReport.name(),
                compoundStatement.getId().getFirst().getRoot()));
    }

    private static void addMedicationEntry(RCMRMT030101UKMedicationStatement medicationStatement,
                                           List<Reference> entryReferences) {
        if (medicationStatement != null) {
            medicationStatement.getComponent().forEach(component -> {
                if (component.hasEhrSupplyAuthorise()) {
                    var id = component.getEhrSupplyAuthorise().getId().getRoot();
                    entryReferences.add(createResourceReference(ResourceType.MedicationRequest.name(), id));
                } else if (component.hasEhrSupplyPrescribe()) {
                    entryReferences.add(createResourceReference(ResourceType.MedicationRequest.name(),
                        component.getEhrSupplyPrescribe().getId().getRoot()));
                }
            });
        }
    }

    private static void addBloodPressureEntry(RCMRMT030101UKCompoundStatement compoundStatement,
                                              List<Reference> entryReferences) {

        entryReferences.add(createResourceReference(ResourceType.Observation.name(),
                compoundStatement.getId().getFirst().getRoot()));
    }

    private static void addImmunizationEntry(RCMRMT030101UKObservationStatement observationStatement,
                                             List<Reference> entryReferences) {
        entryReferences.add(createResourceReference(ResourceType.Immunization.name(),
                observationStatement.getId().getRoot()));
    }

    private static void addUncategorisedObservationEntry(RCMRMT030101UKObservationStatement observationStatement,
                                                         List<Reference> entryReferences) {
        entryReferences.add(createResourceReference(ResourceType.Observation.name(),
                observationStatement.getId().getRoot()));
    }

    private static void addPlanStatementEntry(RCMRMT030101UKPlanStatement planStatement,
                                              List<Reference> entryReferences) {
        if (planStatement != null) {
            entryReferences.add(createResourceReference(ResourceType.ProcedureRequest.name(),
                    planStatement.getId().getRoot()));
        }
    }

    private static void addRequestStatementEntry(RCMRMT030101UKRequestStatement requestStatement,
                                                 List<Reference> entryReferences) {
        if (requestStatement != null) {

            //Qualifier null then add ReferralRequest as normal.
            if (requestStatement.getCode().getQualifier() == null
                    || requestStatement.getCode().getQualifier().isEmpty()) {
                entryReferences.add(createResourceReference(
                        ResourceType.ReferralRequest.name(), requestStatement.getId().getFirst().getRoot()));
            }

            //If qualifier present then add Observation or ReferralRequest depending on Value.
            for (CR qualifier : requestStatement.getCode().getQualifier()) {
                if (qualifier.getValue().getCode().equals(SELF_REFERRAL)) {
                    entryReferences.add(createResourceReference(
                            ResourceType.Observation.name(), requestStatement.getId().getFirst().getRoot()));
                } else {
                    entryReferences.add(createResourceReference(
                            ResourceType.ReferralRequest.name(), requestStatement.getId().getFirst().getRoot()));
                }
            }
        }
    }

    private static void addNarrativeStatementEntry(RCMRMT030101UKNarrativeStatement narrativeStatement,
                                                   List<Reference> entryReferences) {
        if (narrativeStatement != null) {
            if (isDocumentReference(narrativeStatement)) {
                // document references actually use the narrative statement id rather than the referenceDocument root id in EMIS data
                // if EMIS is incorrect, replace the id below with the following...
                // narrativeStatement.getReference().getFirst().getReferredToExternalDocument().getId().getRoot()
                entryReferences.add(createResourceReference(ResourceType.DocumentReference.name(),
                    narrativeStatement.getId().getRoot()));
            } else {
                entryReferences.add(createResourceReference(ResourceType.Observation.name(), narrativeStatement.getId().getRoot()));
            }
        }
    }

    private static void addLinkSetEntry(RCMRMT030101UKLinkSet linkSet,
                                        List<Reference> entryReferences) {
        if (linkSet != null) {
            entryReferences.add(createResourceReference(ResourceType.Condition.name(), linkSet.getId().getRoot()));
        }
    }

    private static Reference createResourceReference(String resourceName, String id) {
        return new Reference(new IdType(resourceName, id));
    }
}
