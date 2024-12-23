package uk.nhs.adaptors.pss.translator.service;


import static uk.nhs.adaptors.pss.translator.util.OrganizationUtil.organisationIsNotDuplicate;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.Comparator;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.DomainResource;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.ListResource;
import org.hl7.fhir.dstu3.model.Location;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.hl7.v3.RCMRIN030000UKMessage;
import org.hl7.v3.RCMRMT030101UKComponent3;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKEhrFolder;
import org.hl7.v3.RCMRMT030101UKPatient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.adaptors.connector.model.PatientAttachmentLog;
import uk.nhs.adaptors.pss.translator.exception.BundleMappingException;
import uk.nhs.adaptors.pss.translator.generator.BundleGenerator;
import uk.nhs.adaptors.pss.translator.mapper.AgentDirectoryMapper;
import uk.nhs.adaptors.pss.translator.mapper.AllergyIntoleranceMapper;
import uk.nhs.adaptors.pss.translator.mapper.BloodPressureMapper;
import uk.nhs.adaptors.pss.translator.mapper.ConditionMapper;
import uk.nhs.adaptors.pss.translator.mapper.DocumentReferenceMapper;
import uk.nhs.adaptors.pss.translator.mapper.DuplicateObservationStatementMapper;
import uk.nhs.adaptors.pss.translator.mapper.diagnosticreport.DiagnosticReportMapper;
import uk.nhs.adaptors.pss.translator.mapper.EncounterMapper;
import uk.nhs.adaptors.pss.translator.mapper.ImmunizationMapper;
import uk.nhs.adaptors.pss.translator.mapper.LocationMapper;
import uk.nhs.adaptors.pss.translator.mapper.ObservationCommentMapper;
import uk.nhs.adaptors.pss.translator.mapper.ObservationMapper;
import uk.nhs.adaptors.pss.translator.mapper.OrganizationMapper;
import uk.nhs.adaptors.pss.translator.mapper.PatientMapper;
import uk.nhs.adaptors.pss.translator.mapper.ProcedureRequestMapper;
import uk.nhs.adaptors.pss.translator.mapper.ReferralRequestMapper;
import uk.nhs.adaptors.pss.translator.mapper.diagnosticreport.SpecimenCompoundsMapper;
import uk.nhs.adaptors.pss.translator.mapper.diagnosticreport.SpecimenMapper;
import uk.nhs.adaptors.pss.translator.mapper.TemplateMapper;
import uk.nhs.adaptors.pss.translator.mapper.UnknownPractitionerHandler;
import uk.nhs.adaptors.pss.translator.mapper.medication.MedicationRequestMapper;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class BundleMapperService {
    private static final String ENCOUNTER_KEY = "encounters";
    private static final String CONSULTATION_KEY = "consultations";
    private static final String TOPIC_KEY = "topics";
    private static final String CATEGORY_KEY = "categories";

    private final BundleGenerator generator;

    private final PatientMapper patientMapper;
    private final AgentDirectoryMapper agentDirectoryMapper;
    private final EncounterMapper encounterMapper;
    private final LocationMapper locationMapper;
    private final ProcedureRequestMapper procedureRequestMapper;
    private final ReferralRequestMapper referralRequestMapper;
    private final MedicationRequestMapper medicationRequestMapper;
    private final ObservationCommentMapper observationCommentMapper;
    private final BloodPressureMapper bloodPressureMapper;
    private final ObservationMapper observationMapper;
    private final ConditionMapper conditionMapper;
    private final ImmunizationMapper immunizationMapper;
    private final UnknownPractitionerHandler unknownPractitionerHandler;
    private final DocumentReferenceMapper documentReferenceMapper;
    private final TemplateMapper templateMapper;
    private final OrganizationMapper organizationMapper;
    private final AllergyIntoleranceMapper allergyIntoleranceMapper;
    private final DiagnosticReportMapper diagnosticReportMapper;
    private final SpecimenMapper specimenMapper;
    private final SpecimenCompoundsMapper specimenCompoundsMapper;
    private final DuplicateObservationStatementMapper duplicateObservationStatementMapper;

    public Bundle mapToBundle(RCMRIN030000UKMessage xmlMessage, String losingPracticeOdsCode,
                              List<PatientAttachmentLog> attachments) throws BundleMappingException {
        try {

            Bundle bundle = generator.generateBundle();
            final RCMRMT030101UKEhrExtract ehrExtract = getEhrExtract(xmlMessage);
            duplicateObservationStatementMapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);

            final RCMRMT030101UKEhrFolder ehrFolder = getEhrFolder(xmlMessage);

            var locations = mapLocations(ehrFolder, losingPracticeOdsCode);

            var agents = mapAgentDirectories(ehrFolder);
            var patient = mapPatient(getEhrExtract(xmlMessage), getPatientOrganization(agents));
            addEntry(bundle, patient);

            Organization authorOrg = organizationMapper.mapAuthorOrganization(losingPracticeOdsCode, agents);
            if (documentReferenceMapper.hasDocumentReferences(ehrExtract) && organisationIsNotDuplicate(authorOrg, agents)) {
                addEntry(bundle, authorOrg);
            }
            addEntries(bundle, agents);

            var mappedEncounterEhrCompositions = mapEncounters(ehrExtract, patient, losingPracticeOdsCode, locations);
            var encounters = handleMappedEncounterResources(mappedEncounterEhrCompositions, bundle);

            addEntries(bundle, locations);

            var procedureRequests = procedureRequestMapper.mapResources(ehrExtract, patient, encounters, losingPracticeOdsCode);
            addEntries(bundle, procedureRequests);

            var referralRequests = referralRequestMapper.mapResources(ehrExtract, patient, encounters, losingPracticeOdsCode);
            addEntries(bundle, referralRequests);

            var medicationResources = medicationRequestMapper.mapResources(ehrExtract, patient, encounters, losingPracticeOdsCode);
            addEntries(bundle, medicationResources);

            var bloodPressures = bloodPressureMapper.mapResources(ehrExtract, patient, encounters, losingPracticeOdsCode);
            addEntries(bundle, bloodPressures);

            var observations = observationMapper.mapResources(ehrExtract, patient, encounters, losingPracticeOdsCode);
            addEntries(bundle, observations);

            var immunizations = immunizationMapper.mapResources(ehrExtract, patient, encounters, losingPracticeOdsCode);
            addEntries(bundle, immunizations);

            var conditions = conditionMapper.mapResources(ehrExtract, patient, encounters, losingPracticeOdsCode);
            addEntries(bundle, conditions);

            var observationComments = observationCommentMapper.mapResources(ehrExtract, patient, encounters, losingPracticeOdsCode);

            var documentReferences = documentReferenceMapper.mapResources(ehrExtract, patient, encounters, authorOrg, attachments);
            addEntries(bundle, documentReferences);

            var templates = templateMapper.mapResources(ehrExtract, patient, encounters, losingPracticeOdsCode);
            addEntries(bundle, templates);

            var allergyIntolerances = allergyIntoleranceMapper.mapResources(ehrExtract, patient, encounters, losingPracticeOdsCode);
            addEntries(bundle, allergyIntolerances);

            mapDiagnosticReports(bundle, ehrExtract, patient, encounters, observations, observationComments, losingPracticeOdsCode);

            conditionMapper.addReferences(bundle, conditions, ehrExtract);
            conditionMapper.addHierarchyReferencesToConditions(conditions, ehrExtract);
            unknownPractitionerHandler.updateUnknownPractitionersRefs(bundle);
            templateMapper.addReferences(templates, observations, ehrExtract);


            LOGGER.debug("Mapped Bundle with [{}] entries", bundle.getEntry().size());

            return bundle;
        } catch (Exception e) {
            e.printStackTrace();
            throw new BundleMappingException(e.getMessage());
        }
    }

    private void mapDiagnosticReports(Bundle bundle, RCMRMT030101UKEhrExtract ehrExtract, Patient patient, List<Encounter> encounters,
                                      List<Observation> observations, List<Observation> observationComments, String practiceCode) {
        var diagnosticReports = diagnosticReportMapper.mapResources(ehrExtract, patient, encounters, practiceCode, observationComments);

        diagnosticReportMapper.handleChildObservationComments(ehrExtract, observationComments);

        var specimens = specimenMapper.mapSpecimens(ehrExtract, diagnosticReports, patient, practiceCode);
        addEntries(bundle, diagnosticReports);
        addEntries(bundle, specimens);

        observationComments = specimenMapper.removeSurplusObservationComments(ehrExtract, observationComments);

        var batteryObservations = specimenCompoundsMapper.handleSpecimenChildComponents(ehrExtract, observations, observationComments,
            diagnosticReports, patient, encounters, practiceCode);

        addEntries(bundle, observationComments);
        addEntries(bundle, batteryObservations);
    }

    private List<Encounter> handleMappedEncounterResources(
            Map<String, List<? extends DomainResource>> mappedEncounterEhrCompositions,
            Bundle bundle) {

        var encounters = (List<Encounter>) mappedEncounterEhrCompositions.get(ENCOUNTER_KEY);
        var consultations = (List<ListResource>) mappedEncounterEhrCompositions.get(CONSULTATION_KEY);
        var topics = (List<ListResource>) mappedEncounterEhrCompositions.get(TOPIC_KEY);
        var categories = (List<ListResource>) mappedEncounterEhrCompositions.get(CATEGORY_KEY);

        addEntries(bundle, encounters);
        addEntries(bundle, consultations);
        addEntries(bundle, topics);
        addEntries(bundle, categories);

        return encounters;
    }

    private Map<String, List<? extends DomainResource>> mapEncounters(
            RCMRMT030101UKEhrExtract ehrExtract,
            Patient patient,
            String losingPracticeOdsCode,
            List<Location> locations) {
        return encounterMapper.mapEncounters(ehrExtract, patient, losingPracticeOdsCode, locations);
    }

    private List<? extends DomainResource> mapAgentDirectories(RCMRMT030101UKEhrFolder ehrFolder) {
        return agentDirectoryMapper.mapAgentDirectory(ehrFolder.getResponsibleParty().getAgentDirectory());
    }

    private List<Location> mapLocations(RCMRMT030101UKEhrFolder ehrFolder, String losingPracticeOdsCode) {

        return ehrFolder.getComponent().stream()
                .map(RCMRMT030101UKComponent3::getEhrComposition)
                .filter(ehrComposition -> ehrComposition.getLocation() != null)
                .map(
                        ehrComposition -> locationMapper.mapToLocation(
                                ehrComposition.getLocation(),
                                losingPracticeOdsCode)
                )
                .collect(
                        Collectors.collectingAndThen(
                                Collectors.toCollection(
                                        () -> new TreeSet<>(Comparator.comparing(Location::getName))
                                ),
                                ArrayList::new
                        )
                );
    }

    private Patient mapPatient(RCMRMT030101UKEhrExtract ehrExtract, Organization organization) {
        RCMRMT030101UKPatient xmlPatient = ehrExtract.getRecordTarget().getPatient();
        return patientMapper.mapToPatient(xmlPatient, organization);
    }

    private Organization getPatientOrganization(List<? extends DomainResource> agents) {
        return agents.stream()
            .filter(agent -> ResourceType.Organization.equals(agent.getResourceType()))
            .map(Organization.class::cast)
            .findFirst()
            .orElse(null);
    }

    private RCMRMT030101UKEhrFolder getEhrFolder(RCMRIN030000UKMessage xmlMessage) {
        return getEhrExtract(xmlMessage).getComponent().getFirst().getEhrFolder();
    }

    private RCMRMT030101UKEhrExtract getEhrExtract(RCMRIN030000UKMessage xmlMessage) {
        return xmlMessage.getControlActEvent().getSubject().getEhrExtract();
    }

    private <T extends DomainResource> void addEntries(Bundle bundle, Collection<T> resources) {
        resources.forEach(it -> addEntry(bundle, it));
    }

    private <T extends DomainResource> void addEntry(Bundle bundle, T resource) {
        bundle.addEntry(new BundleEntryComponent().setResource(resource));
    }
}