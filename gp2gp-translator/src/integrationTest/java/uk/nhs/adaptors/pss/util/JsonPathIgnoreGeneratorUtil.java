package uk.nhs.adaptors.pss.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.ListResource;
import org.hl7.fhir.dstu3.model.MedicationRequest;
import org.hl7.fhir.dstu3.model.MedicationStatement;
import org.hl7.fhir.dstu3.model.Practitioner;
import org.hl7.fhir.dstu3.model.ProcedureRequest;
import org.hl7.fhir.dstu3.model.Resource;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.hl7.fhir.instance.model.api.IBaseResource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class JsonPathIgnoreGeneratorUtil {

    private static final String CONSULTATION_DISPLAY = "Consultation";
    private static final String TOPIC_DISPLAY = "Topic (EHR)";
    private static final String UNKNOWN_USER = "Unknown User";

    private static final List<IgnoreParameters> IGNORED_RESOURCES_BUILDER = List.of(
        new IgnoreParameters(ResourceType.Medication, "id"),
        new IgnoreParameters(ResourceType.MedicationRequest, "medicationReference.reference",
            JsonPathIgnoreGeneratorUtil::hasMedicationReference),
        new IgnoreParameters(ResourceType.MedicationStatement, "medicationReference.reference",
            JsonPathIgnoreGeneratorUtil::hasMedicationReference),
        new IgnoreParameters(ResourceType.List, "id", resource -> isList(resource, TOPIC_DISPLAY)),
        new IgnoreParameters(ResourceType.List, "entry[*].item.reference",
            resource -> isList(resource, CONSULTATION_DISPLAY)),
        new IgnoreParameters(ResourceType.Practitioner, "id", JsonPathIgnoreGeneratorUtil::isUnknownPractitioner),
        new IgnoreParameters(ResourceType.ProcedureRequest, "performer", JsonPathIgnoreGeneratorUtil::isUnknownPerformer),
        new IgnoreParameters(ResourceType.DocumentReference, "custodian")
    );

    public static List<String> generateJsonPathIgnores(Bundle fhirBundle) {
        var resources = fhirBundle.getEntry();
        List<String> ignores = new ArrayList<>();
        for (int i = 0; i < resources.size(); i++) {
            String ignore = "entry[" + i + "].resource.";
            var resource = resources.get(i);
            for (IgnoreParameters resourceTypeAndField : IGNORED_RESOURCES_BUILDER) {
                if (resourceTypeAndField.getResourceType().equals(resource.getResource().getResourceType())) {
                    if (resourceTypeAndField.hasChecker() && resourceTypeAndField.getChecker().apply(resource.getResource())) {
                        ignores.add(ignore + resourceTypeAndField.getFieldName());
                    } else if (!resourceTypeAndField.hasChecker()) {
                        ignores.add(ignore + resourceTypeAndField.getFieldName());
                    }
                }
            }
        }
        return ignores;
    }

    private static boolean hasMedicationReference(Resource resource) {
        if (ResourceType.MedicationRequest.equals(resource.getResourceType())) {
            return ((MedicationRequest) resource).hasMedicationReference();
        }
        if (ResourceType.MedicationStatement.equals(resource.getResourceType())) {
            return ((MedicationStatement) resource).hasMedicationReference();
        }
        return false;
    }

    private static boolean isList(Resource resource, String listDisplay) {
        if (ResourceType.List.equals(resource.getResourceType())) {
            var consultationList = (ListResource) resource;
            return consultationList.getCode().getCodingFirstRep().getDisplay().equals(listDisplay);
        }

        return false;
    }

    private static Boolean isUnknownPractitioner(IBaseResource resource) {
        Practitioner practitioner = (Practitioner) resource;

        return UNKNOWN_USER.equals(practitioner.getNameFirstRep().getText());
    }

    private static Boolean isUnknownPerformer(Resource resource) {
        IBaseResource performer = ((ProcedureRequest) resource).getPerformer().getResource();

        return performer instanceof Practitioner ? isUnknownPractitioner(performer) : false;
    }

    @Data
    @RequiredArgsConstructor
    @AllArgsConstructor
    private static final class IgnoreParameters {
        private final ResourceType resourceType;
        private final String fieldName;
        private Function<Resource, Boolean> checker;

        public boolean hasChecker() {
            return checker != null;
        }
    }
}
