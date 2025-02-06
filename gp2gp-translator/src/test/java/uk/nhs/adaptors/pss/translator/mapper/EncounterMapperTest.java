package uk.nhs.adaptors.pss.translator.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.util.ResourceUtils.getFile;

import static uk.nhs.adaptors.pss.translator.util.MetaUtil.MetaType.META_WITHOUT_SECURITY;
import static uk.nhs.adaptors.pss.translator.util.MetaUtil.MetaType.META_WITH_SECURITY;
import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallFile;

import static uk.nhs.adaptors.common.util.CodeableConceptUtils.createCodeableConcept;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.DomainResource;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Encounter.EncounterStatus;

import org.hl7.fhir.dstu3.model.ListResource;
import org.hl7.fhir.dstu3.model.Location;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.Reference;

import org.hl7.v3.CD;
import org.hl7.v3.CV;
import org.hl7.v3.RCMRMT030101UKCompoundStatement;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import lombok.SneakyThrows;

import uk.nhs.adaptors.pss.translator.util.MetaUtil;
import uk.nhs.adaptors.pss.translator.TestUtility;
import uk.nhs.adaptors.pss.translator.service.ConfidentialityService;
import uk.nhs.adaptors.pss.translator.util.DatabaseImmunizationChecker;
import uk.nhs.adaptors.pss.translator.util.DegradedCodeableConcepts;
import uk.nhs.adaptors.pss.translator.util.ResourceReferenceUtil;

@ExtendWith(MockitoExtension.class)
public class EncounterMapperTest {

    private static final String XML_RESOURCES_BASE = "xml/Encounter/";
    private static final String ENCOUNTER_ID = "5EB5D070-8FE1-11EC-B1E5-0800200C9A66";
    private static final String ENCOUNTER_ID_2 = "6EB5D070-8FE1-11EC-B1E5-0800200C9A66";
    private static final String ENCOUNTER_KEY = "encounters";
    private static final String CONSULTATION_KEY = "consultations";
    private static final String TOPIC_KEY = "topics";
    private static final String CATEGORY_KEY = "categories";
    private static final String LOCATION_PREFIX = "Location/";
    private static final String META_PROFILE = "Encounter-1";
    private static final String PRACTISE_CODE = "TESTPRACTISECODE";
    private static final String IDENTIFIER_SYSTEM = "https://PSSAdaptor/TESTPRACTISECODE";
    private static final String PATIENT_ID = "0E6F45F0-8D7B-11EC-B1E5-0800200C9A66";
    private static final String CODING_DISPLAY = "Ischaemic heart disease";
    private static final String FULL_VALID_STRUCTURED_ENCOUNTER_XML = "full_valid_structured_encounter.xml";
    private static final String FULL_VALID_STRUCTURED_ENCOUNTER_WITH_LINKSET_XML = "full_valid_structured_encounter_with_linkset.xml";
    private static final String FULL_VALID_FLAT_ENCOUNTER_XML = "full_valid_flat_encounter.xml";
    private static final String NO_OPTIONAL_FLAT_ENCOUNTER_XML = "no_optional_valid_flat_encounter.xml";
    private static final String INVALID_ENCOUNTER_CODE_1_XML = "invalid_encounter_code_1.xml";
    private static final String INVALID_ENCOUNTER_CODE_2_XML = "invalid_encounter_code_2.xml";
    private static final String SUPPRESSED_COMPOSITION_WITH_EHR_EMPTY_XML = "suppressed_with_ehr_empty.xml";
    private static final String SUPPRESSED_COMPOSITION_WITH_REGISTRATION_STATEMENT_XML = "suppressed_with_ehr_registration.xml";
    private static final String EFFECTIVE_CENTER_ENCOUNTER_PERIOD_XML = "effective_center_encounter_period.xml";
    private static final String EFFECTIVE_LOW_AND_HIGH_ENCOUNTER_PERIOD_XML = "effective_low_and_high_encounter_period.xml";
    private static final String EFFECTIVE_LOW_ENCOUNTER_PERIOD_XML = "effective_low_encounter_period.xml";
    private static final String EFFECTIVE_HIGH_ENCOUNTER_PERIOD_XML = "effective_high_encounter_period.xml";
    private static final String EFFECTIVE_CENTER_UNK_NULL_FLAVOR_ENCOUNTER_PERIOD_XML = "effective_center_null_flavor_encounter_period.xml";
    private static final String AVAILABILITY_TIME_ENCOUNTER_PERIOD_XML = "availability_time_encounter_period.xml";
    private static final String NO_ENCOUNTER_PERIOD_XML = "no_encounter_period.xml";
    private static final String NULL_FLAVOR_EFFECTIVE_TIMES_XML = "null_flavor_effective_times.xml";
    private static final String ENCOUNTER_WITH_MULTIPLE_COMPOUND_STATEMENTS_XML = "encounter_with_multiple_compound_statements.xml";
    private static final String FULL_VALID_STRUCTURED_ENCOUNTER_WITH_RESOURCES_XML = "full_valid_structured_encounter_with_resources.xml";
    private static final String FULL_VALID_FLAT_ENCOUNTER_WITH_RESOURCES_XML = "full_valid_flat_encounter_with_resources.xml";
    private static final String FULL_VALID_FLAT_ENCOUNTER_WITH_LINK_SET_XML = "full_valid_flat_encounter_with_linkset.xml";
    private static final int ONE_MAPPED_RESOURCE = 1;
    private static final int TWO_MAPPED_RESOURCES = 2;
    private static final String LINKSET_REFERENCE = "Condition/DCC26FC9-4D1C-11E3-A2DD-010000000161";
    private static final String RELATED_PROBLEM_EXT_URL = "https://fhir.hl7.org.uk/STU3/StructureDefinition/Extension-CareConnect-RelatedProblemHeader-1";
    private static final String RELATED_PROBLEM_TARGET_URL = "target";
    private static final String ENCOUNTER_WITH_NOPAT_CONFIDENTIALITY_CODE_WITHIN_EHR_COMPOSITION
        = "encounter_with_nopat_confidentiality_code_within_ehr_composition.xml";
    private static final String ENCOUNTER_WITH_NOSCRUB_CONFIDENTIALITY_CODE_WITHIN_EHR_COMPOSITION
        = "encounter_with_noscrub_confidentiality_code_within_ehr_composition.xml";

    @Mock
    private CodeableConceptMapper codeableConceptMapper;

    @Mock
    private ConsultationListMapper consultationListMapper;

    @Mock
    private DatabaseImmunizationChecker immunizationChecker;

    @Mock
    private ResourceReferenceUtil resourceReferenceUtil;

    @Mock
    private ConfidentialityService confidentialityService;

    @InjectMocks
    private EncounterMapper encounterMapper;

    private Patient patient;

    private List<Location> entryLocations;

    private static final String LOCATION_ID =  "3";

    private static final String SNOMED_SYSTEM = "http://snomed.info/sct";

    @BeforeEach
    public void setup() {
        patient = new Patient();
        patient.setId(PATIENT_ID);
        setUpCodeableConceptMock();

        var location1 = new Location();
        location1.setName("Branch Surgery");
        location1.setId("1");

        var location2 = new Location();
        location2.setName("EMIS LV Test Practice 1");
        location2.setId("2");

        var location3 = new Location();
        location3.setName("test location");
        location3.setId(LOCATION_ID);

        entryLocations = List.of(location1, location2, location3);

        Mockito.lenient()
            .when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
                any(String.class), any(Optional.class)
            )).thenReturn(MetaUtil.getMetaFor(META_WITHOUT_SECURITY, META_PROFILE));
    }

    @Test
    public void testEncountersWithMultipleCompoundStatements() {
        when(consultationListMapper.mapToConsultation(any(RCMRMT030101UKEhrComposition.class), any(Encounter.class)))
            .thenReturn(getList());
        when(consultationListMapper.mapToTopic(any(ListResource.class), any(RCMRMT030101UKCompoundStatement.class)))
            .thenReturn(getList());
        when(consultationListMapper.mapToCategory(any(ListResource.class), any(RCMRMT030101UKCompoundStatement.class)))
            .thenReturn(getList());

        var ehrExtract = unmarshallEhrExtractElement(ENCOUNTER_WITH_MULTIPLE_COMPOUND_STATEMENTS_XML);

        Map<String, List<? extends DomainResource>> mappedResources = encounterMapper.mapEncounters(
                ehrExtract, patient, PRACTISE_CODE, entryLocations
        );

        var encounterList = mappedResources.get(ENCOUNTER_KEY);
        assertThat(mappedResources.get(ENCOUNTER_KEY).size()).isOne();
        assertThat(mappedResources.get(CONSULTATION_KEY).size()).isOne();
        assertThat(mappedResources.get(TOPIC_KEY)).hasSize(TWO_MAPPED_RESOURCES);
        assertThat(mappedResources.get(CATEGORY_KEY)).hasSize(ONE_MAPPED_RESOURCE);
        assertThat(encounterList).hasSize(1);
        verifyCreateMetaAndAddSecurityCalled(1, Optional.empty());
    }

    @Test
    public void testMapValidEncounterWithSnomedCode() {
        when(consultationListMapper.mapToConsultation(any(RCMRMT030101UKEhrComposition.class), any(Encounter.class)))
            .thenReturn(getList());
        when(consultationListMapper.mapToTopic(any(ListResource.class), any(RCMRMT030101UKCompoundStatement.class)))
            .thenReturn(getList());
        when(consultationListMapper.mapToCategory(any(ListResource.class), any(RCMRMT030101UKCompoundStatement.class)))
            .thenReturn(getList());

        var ehrExtract = unmarshallEhrExtractElement(FULL_VALID_STRUCTURED_ENCOUNTER_XML);

        Map<String, List<? extends DomainResource>> mappedResources = encounterMapper.mapEncounters(
            ehrExtract, patient, PRACTISE_CODE, entryLocations
        );

        assertThat(mappedResources.get(ENCOUNTER_KEY).size()).isOne();
        assertThat(mappedResources.get(CONSULTATION_KEY).size()).isOne();
        assertThat(mappedResources.get(TOPIC_KEY).size()).isOne();
        assertThat(mappedResources.get(CATEGORY_KEY).size()).isOne();

        var encounter = (Encounter) mappedResources.get(ENCOUNTER_KEY).getFirst();

        assertThat(encounter.getType().getFirst().getCodingFirstRep().getDisplay())
            .isEqualTo(CODING_DISPLAY);
        verifyCreateMetaAndAddSecurityCalled(1, Optional.empty());
    }

    @Test
    public void testMapValidEncounterWithoutSnomedCode() {

        var codeableConcept = createCodeableConcept(null, "1.2.3.4.5", CODING_DISPLAY);

        when(codeableConceptMapper.mapToCodeableConcept(any())).thenReturn(codeableConcept);
        when(consultationListMapper.mapToConsultation(any(RCMRMT030101UKEhrComposition.class), any(Encounter.class)))
            .thenReturn(getList());
        when(consultationListMapper.mapToTopic(any(ListResource.class), any(RCMRMT030101UKCompoundStatement.class)))
            .thenReturn(getList());
        when(consultationListMapper.mapToCategory(any(ListResource.class), any(RCMRMT030101UKCompoundStatement.class)))
            .thenReturn(getList());

        var ehrExtract = unmarshallEhrExtractElement(FULL_VALID_STRUCTURED_ENCOUNTER_XML);

        Map<String, List<? extends DomainResource>> mappedResources = encounterMapper.mapEncounters(
            ehrExtract, patient, PRACTISE_CODE, entryLocations
        );

        assertThat(mappedResources.get(ENCOUNTER_KEY).size()).isOne();
        assertThat(mappedResources.get(CONSULTATION_KEY).size()).isOne();
        assertThat(mappedResources.get(TOPIC_KEY).size()).isOne();
        assertThat(mappedResources.get(CATEGORY_KEY).size()).isOne();

        var encounter = (Encounter) mappedResources.get(ENCOUNTER_KEY).getFirst();

        assertThat(encounter.getType().getFirst().getCodingFirstRep())
            .isEqualTo(DegradedCodeableConcepts.DEGRADED_OTHER);
        verifyCreateMetaAndAddSecurityCalled(1, Optional.empty());
    }

    @Test
    public void testMapValidEncounterWithNopatConfidentialityCodeWithinEhrCompositionExpectMetaSecurityAdded() {
        final Meta stubbedMeta = MetaUtil.getMetaFor(META_WITH_SECURITY, META_PROFILE);
        final CodeableConcept codeableConcept = createCodeableConcept(null, "1.2.3.4.5", CODING_DISPLAY);

        when(codeableConceptMapper.mapToCodeableConcept(
            any(CD.class))
        ).thenReturn(codeableConcept);
        when(consultationListMapper.mapToConsultation(
            any(RCMRMT030101UKEhrComposition.class), any(Encounter.class))
        ).thenReturn(getList());
        when(consultationListMapper.mapToTopic(
            any(ListResource.class), any(RCMRMT030101UKCompoundStatement.class))
        ).thenReturn(getList());
        when(consultationListMapper.mapToCategory(
            any(ListResource.class), any(RCMRMT030101UKCompoundStatement.class))
        ).thenReturn(getList());

        Mockito
            .lenient()
            .when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
                any(String.class), any(Optional.class)
            )).thenReturn(stubbedMeta);

        final RCMRMT030101UKEhrExtract ehrExtract =
            unmarshallEhrExtractElement(ENCOUNTER_WITH_NOPAT_CONFIDENTIALITY_CODE_WITHIN_EHR_COMPOSITION);
        final RCMRMT030101UKEhrComposition ehrComposition =
            TestUtility.GET_EHR_COMPOSITION.apply(ehrExtract);

        Map<String, List<? extends DomainResource>> mappedResources = encounterMapper.mapEncounters(
            ehrExtract, patient, PRACTISE_CODE, entryLocations
        );

        var encounter = (Encounter) mappedResources.get(ENCOUNTER_KEY).getFirst();

        assertThat(encounter.getType().getFirst().getCodingFirstRep()).isEqualTo(DegradedCodeableConcepts.DEGRADED_OTHER);
        assertMetaSecurityPresent(encounter.getMeta());
        verifyCreateMetaAndAddSecurityCalled(1,  ehrComposition.getConfidentialityCode());
    }

    @Test
    public void testMapValidEncounterWithNoscrubConfidentialityCodeWithinEhrCompositionExpectMetaSecurityNotAdded() {
        final Meta metaWithoutSecurity = MetaUtil.getMetaFor(META_WITHOUT_SECURITY, META_PROFILE);
        final CodeableConcept codeableConcept = createCodeableConcept(null, "1.2.3.4.5", CODING_DISPLAY);

        when(codeableConceptMapper.mapToCodeableConcept(
            any(CD.class))
        ).thenReturn(codeableConcept);
        when(consultationListMapper.mapToConsultation(
            any(RCMRMT030101UKEhrComposition.class), any(Encounter.class))
        ).thenReturn(getList());
        when(consultationListMapper.mapToTopic(
            any(ListResource.class), any(RCMRMT030101UKCompoundStatement.class))
        ).thenReturn(getList());
        when(consultationListMapper.mapToCategory(
            any(ListResource.class), any(RCMRMT030101UKCompoundStatement.class))
        ).thenReturn(getList());

        Mockito
            .lenient()
            .when(confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
                any(String.class), any(Optional.class)
            )).thenReturn(metaWithoutSecurity);

        final RCMRMT030101UKEhrExtract ehrExtract =
            unmarshallEhrExtractElement(ENCOUNTER_WITH_NOSCRUB_CONFIDENTIALITY_CODE_WITHIN_EHR_COMPOSITION);
        final RCMRMT030101UKEhrComposition ehrComposition =
            TestUtility.GET_EHR_COMPOSITION.apply(ehrExtract);

        Map<String, List<? extends DomainResource>> mappedResources = encounterMapper.mapEncounters(
            ehrExtract, patient, PRACTISE_CODE, entryLocations
        );

        var encounter = (Encounter) mappedResources.get(ENCOUNTER_KEY).getFirst();

        assertThat(encounter.getType().getFirst().getCodingFirstRep()).isEqualTo(DegradedCodeableConcepts.DEGRADED_OTHER);
        assertThat(encounter.getMeta().getSecurity()).isEmpty();
        verifyCreateMetaAndAddSecurityCalled(1,  ehrComposition.getConfidentialityCode());
    }

    @Test
    public void testValidEncounterWithFullDataWithStructuredConsultation() {
        when(consultationListMapper.mapToConsultation(any(RCMRMT030101UKEhrComposition.class), any(Encounter.class)))
            .thenReturn(getList());
        when(consultationListMapper.mapToTopic(any(ListResource.class), any(RCMRMT030101UKCompoundStatement.class)))
            .thenReturn(getList());
        when(consultationListMapper.mapToCategory(any(ListResource.class), any(RCMRMT030101UKCompoundStatement.class)))
            .thenReturn(getList());

        var ehrExtract = unmarshallEhrExtractElement(FULL_VALID_STRUCTURED_ENCOUNTER_XML);

        Map<String, List<? extends DomainResource>> mappedResources = encounterMapper.mapEncounters(
                ehrExtract, patient, PRACTISE_CODE, entryLocations
        );

        assertThat(mappedResources.get(ENCOUNTER_KEY).size()).isOne();
        assertThat(mappedResources.get(CONSULTATION_KEY).size()).isOne();
        assertThat(mappedResources.get(TOPIC_KEY).size()).isOne();
        assertThat(mappedResources.get(CATEGORY_KEY).size()).isOne();

        var encounter = (Encounter) mappedResources.get(ENCOUNTER_KEY).getFirst();

        assertEncounter(encounter, "2485BC20-90B4-11EC-B1E5-0800200C9A66", true,
            "2010-01-13T15:20:00+00:00", "2010-01-13T15:20:00+00:00");

        var consultation = (ListResource) mappedResources.get(CONSULTATION_KEY).getFirst();
        assertThat(consultation.getEncounter().getReference()).isEqualTo(ENCOUNTER_ID);

        var topic = (ListResource) mappedResources.get(TOPIC_KEY).getFirst();
        assertThat(topic.getEncounter().getReference()).isEqualTo(ENCOUNTER_ID);
        assertThat(consultation.getEntryFirstRep().getItem().getReference())
            .isEqualTo(ENCOUNTER_ID);

        var category = (ListResource) mappedResources.get(CATEGORY_KEY).getFirst();
        assertThat(category.getEncounter().getReference()).isEqualTo(ENCOUNTER_ID);
        assertThat(topic.getEntryFirstRep().getItem().getReference())
            .isEqualTo(ENCOUNTER_ID);
        verifyCreateMetaAndAddSecurityCalled(1, Optional.empty());
    }

    @Test
    public void testValidEncounterWithLinkSetWithStructuredConsultation() {
        when(consultationListMapper.mapToConsultation(any(RCMRMT030101UKEhrComposition.class), any(Encounter.class)))
            .thenReturn(getList());
        when(consultationListMapper.mapToTopic(any(ListResource.class), any(RCMRMT030101UKCompoundStatement.class)))
            .thenReturn(getList());
        when(consultationListMapper.mapToCategory(any(ListResource.class), any(RCMRMT030101UKCompoundStatement.class)))
            .thenReturn(getList());

        var ehrExtract = unmarshallEhrExtractElement(FULL_VALID_STRUCTURED_ENCOUNTER_WITH_LINKSET_XML);

        Map<String, List<? extends DomainResource>> mappedResources = encounterMapper.mapEncounters(
                ehrExtract, patient, PRACTISE_CODE, entryLocations
        );

        assertThat(mappedResources.get(ENCOUNTER_KEY).size()).isOne();
        assertThat(mappedResources.get(CONSULTATION_KEY).size()).isOne();
        assertThat(mappedResources.get(TOPIC_KEY).size()).isOne();
        assertThat(mappedResources.get(CATEGORY_KEY).size()).isOne();

        var encounter = (Encounter) mappedResources.get(ENCOUNTER_KEY).getFirst();

        assertEncounter(
                encounter,
                "2485BC20-90B4-11EC-B1E5-0800200C9A66",
                true, "2010-01-13T15:20:00+00:00",
                "2010-01-13T15:20:00+00:00"
        );

        var consultation = (ListResource) mappedResources.get(CONSULTATION_KEY).getFirst();
        assertThat(consultation.getEncounter().getReference()).isEqualTo(ENCOUNTER_ID);

        var topic = (ListResource) mappedResources.get(TOPIC_KEY).getFirst();
        assertThat(topic.getEncounter().getReference()).isEqualTo(ENCOUNTER_ID);
        assertThat(consultation.getEntry().getFirst().getItem().getReference())
            .isEqualTo(ENCOUNTER_ID);

        var relatedProblemExt = topic.getExtensionsByUrl(RELATED_PROBLEM_EXT_URL);
        assertThat(relatedProblemExt.size()).isOne();

        var relatedProblemTarget = relatedProblemExt.getFirst().getExtensionsByUrl(RELATED_PROBLEM_TARGET_URL);
        assertThat(relatedProblemTarget.size()).isOne();

        var relatedProblemReference = (Reference) relatedProblemTarget.getFirst().getValue();
        assertThat(relatedProblemReference.getReference()).isEqualTo(LINKSET_REFERENCE);

        var category = (ListResource) mappedResources.get(CATEGORY_KEY).getFirst();
        assertThat(category.getEncounter().getReference()).isEqualTo(ENCOUNTER_ID);
        assertThat(topic.getEntryFirstRep().getItem().getReference())
            .isEqualTo(ENCOUNTER_ID);
        verifyCreateMetaAndAddSecurityCalled(1, Optional.empty());
    }

    @Test
    public void testValidEncounterWithFlatConsultationWithLinkSet() {
        when(consultationListMapper.mapToConsultation(any(RCMRMT030101UKEhrComposition.class), any(Encounter.class)))
            .thenReturn(getList());
        when(consultationListMapper.mapToTopic(any(ListResource.class), isNull())).thenReturn(getList());

        var ehrExtract = unmarshallEhrExtractElement(FULL_VALID_FLAT_ENCOUNTER_WITH_LINK_SET_XML);

        Map<String, List<? extends DomainResource>> mappedResources = encounterMapper.mapEncounters(
            ehrExtract, patient, PRACTISE_CODE, entryLocations
        );

        assertThat(mappedResources.get(ENCOUNTER_KEY).size()).isOne();
        assertThat(mappedResources.get(CONSULTATION_KEY).size()).isOne();
        assertThat(mappedResources.get(TOPIC_KEY).size()).isOne();
        assertThat(mappedResources.get(CATEGORY_KEY)).isEmpty();

        var encounter = (Encounter) mappedResources.get(ENCOUNTER_KEY).getFirst();

        assertEncounter(encounter, "5EB5D070-8FE1-11EC-B1E5-0800200C9A66", true,
            "2010-01-13T15:20:00+00:00", "2010-01-13T15:20:00+00:00");

        var consultation = (ListResource) mappedResources.get(CONSULTATION_KEY).getFirst();
        assertThat(consultation.getEncounter().getReference()).isEqualTo(ENCOUNTER_ID);

        var topic = (ListResource) mappedResources.get(TOPIC_KEY).getFirst();
        assertThat(topic.getEncounter().getReference()).isEqualTo(ENCOUNTER_ID);
        assertThat(consultation.getEntryFirstRep().getItem().getReference()).isEqualTo(ENCOUNTER_ID);

        var relatedProblemExt = topic.getExtensionsByUrl(RELATED_PROBLEM_EXT_URL);
        assertThat(relatedProblemExt).isEmpty();
        verifyCreateMetaAndAddSecurityCalled(1, Optional.empty());
    }

    @Test
    public void testValidEncounterWithFullDataWithFlatConsultation() {
        when(consultationListMapper.mapToConsultation(any(RCMRMT030101UKEhrComposition.class), any(Encounter.class)))
            .thenReturn(getList());
        when(consultationListMapper.mapToTopic(any(ListResource.class), isNull()))
            .thenReturn(getList());

        var ehrExtract = unmarshallEhrExtractElement(FULL_VALID_FLAT_ENCOUNTER_XML);

        Map<String, List<? extends DomainResource>> mappedResources = encounterMapper.mapEncounters(
                ehrExtract, patient, PRACTISE_CODE, entryLocations
        );

        assertThat(mappedResources.get(ENCOUNTER_KEY).size()).isOne();
        assertThat(mappedResources.get(CONSULTATION_KEY).size()).isOne();
        assertThat(mappedResources.get(TOPIC_KEY).size()).isOne();
        assertThat(mappedResources.get(CATEGORY_KEY)).isEmpty();

        var encounter = (Encounter) mappedResources.get(ENCOUNTER_KEY).getFirst();

        assertEncounter(encounter, "5EB5D070-8FE1-11EC-B1E5-0800200C9A66", true,
            "2010-01-13T15:20:00+00:00", "2010-01-13T15:20:00+00:00");

        var consultation = (ListResource) mappedResources.get(CONSULTATION_KEY).getFirst();
        assertThat(consultation.getEncounter().getReference()).isEqualTo(ENCOUNTER_ID);

        var topic = (ListResource) mappedResources.get(TOPIC_KEY).getFirst();
        assertThat(topic.getEncounter().getReference()).isEqualTo(ENCOUNTER_ID);
        assertThat(consultation.getEntryFirstRep().getItem().getReference()).isEqualTo(ENCOUNTER_ID);
        verifyCreateMetaAndAddSecurityCalled(1, Optional.empty());
    }

    @Test
    public void testValidEncounterWithNoOptionalDataWithFlatConsultation() {
        when(consultationListMapper.mapToConsultation(any(RCMRMT030101UKEhrComposition.class), any(Encounter.class)))
            .thenReturn(getList());
        when(consultationListMapper.mapToTopic(any(ListResource.class), isNull()))
            .thenReturn(getList());

        var ehrExtract = unmarshallEhrExtractElement(NO_OPTIONAL_FLAT_ENCOUNTER_XML);

        Map<String, List<? extends DomainResource>> mappedResources = encounterMapper.mapEncounters(
                ehrExtract, patient, PRACTISE_CODE, entryLocations
        );

        assertThat(mappedResources.get(ENCOUNTER_KEY).size()).isOne();
        assertThat(mappedResources.get(CONSULTATION_KEY).size()).isOne();
        assertThat(mappedResources.get(TOPIC_KEY).size()).isOne();
        assertThat(mappedResources.get(CATEGORY_KEY)).isEmpty();

        var encounter = (Encounter) mappedResources.get(ENCOUNTER_KEY).getFirst();

        assertEncounter(encounter, "5EB5D070-8FE1-11EC-B1E5-0800200C9A66", false, null, null);

        var consultation = (ListResource) mappedResources.get(CONSULTATION_KEY).getFirst();
        assertThat(consultation.getEncounter().getReference()).isEqualTo(ENCOUNTER_ID);

        var topic = (ListResource) mappedResources.get(TOPIC_KEY).getFirst();
        assertThat(topic.getEncounter().getReference()).isEqualTo(ENCOUNTER_ID);
        assertThat(consultation.getEntryFirstRep().getItem().getReference()).isEqualTo(ENCOUNTER_ID);
        verifyCreateMetaAndAddSecurityCalled(1, Optional.empty());
    }

    @Test
    public void testEncounterWithMappedResourcesWithStructuredConsultation() {
        when(consultationListMapper.mapToConsultation(any(RCMRMT030101UKEhrComposition.class), any(Encounter.class)))
            .thenReturn(getList());
        when(consultationListMapper.mapToTopic(any(ListResource.class), any(RCMRMT030101UKCompoundStatement.class)))
            .thenReturn(getList());
        when(consultationListMapper.mapToCategory(any(ListResource.class), any(RCMRMT030101UKCompoundStatement.class)))
            .thenReturn(getList());

        var ehrExtract = unmarshallEhrExtractElement(FULL_VALID_STRUCTURED_ENCOUNTER_WITH_RESOURCES_XML);

        Map<String, List<? extends DomainResource>> mappedResources = encounterMapper.mapEncounters(
                ehrExtract, patient, PRACTISE_CODE, entryLocations
        );

        assertThat(mappedResources.get(ENCOUNTER_KEY).size()).isOne();
        assertThat(mappedResources.get(CONSULTATION_KEY).size()).isOne();
        assertThat(mappedResources.get(TOPIC_KEY).size()).isOne();
        assertThat(mappedResources.get(CATEGORY_KEY).size()).isOne();

        var encounter = (Encounter) mappedResources.get(ENCOUNTER_KEY).getFirst();

        assertEncounter(encounter, "2485BC20-90B4-11EC-B1E5-0800200C9A66", true,
            "2010-01-13T15:20:00+00:00", "2010-01-13T15:20:00+00:00");

        var consultation = (ListResource) mappedResources.get(CONSULTATION_KEY).getFirst();
        assertThat(consultation.getEncounter().getReference()).isEqualTo(ENCOUNTER_ID);

        var topic = (ListResource) mappedResources.get(TOPIC_KEY).getFirst();
        assertThat(topic.getEncounter().getReference()).isEqualTo(ENCOUNTER_ID);
        assertThat(consultation.getEntryFirstRep().getItem().getReference())
            .isEqualTo(ENCOUNTER_ID);

        var category = (ListResource) mappedResources.get(CATEGORY_KEY).getFirst();
        assertThat(category.getEncounter().getReference()).isEqualTo(ENCOUNTER_ID);
        assertThat(topic.getEntryFirstRep().getItem().getReference())
            .isEqualTo(ENCOUNTER_ID);

        verify(resourceReferenceUtil, atLeast(1))
            .extractChildReferencesFromCompoundStatement(any(), any());
        verifyCreateMetaAndAddSecurityCalled(1, Optional.empty());
    }

    @Test
    public void testEncounterWithMappedResourcesWithFlatConsultation() {
        when(consultationListMapper.mapToConsultation(any(RCMRMT030101UKEhrComposition.class), any(Encounter.class)))
            .thenReturn(getList());
        when(consultationListMapper.mapToTopic(any(ListResource.class), isNull())).thenReturn(getList());

        var ehrExtract = unmarshallEhrExtractElement(FULL_VALID_FLAT_ENCOUNTER_WITH_RESOURCES_XML);

        Map<String, List<? extends DomainResource>> mappedResources = encounterMapper.mapEncounters(
                ehrExtract, patient, PRACTISE_CODE, entryLocations
        );

        assertThat(mappedResources.get(ENCOUNTER_KEY).size()).isOne();
        assertThat(mappedResources.get(CONSULTATION_KEY).size()).isOne();
        assertThat(mappedResources.get(TOPIC_KEY).size()).isOne();
        assertThat(mappedResources.get(CATEGORY_KEY)).isEmpty();

        var encounter = (Encounter) mappedResources.get(ENCOUNTER_KEY).getFirst();

        assertEncounter(encounter, "5EB5D070-8FE1-11EC-B1E5-0800200C9A66", true,
            "2010-01-13T15:20:00+00:00", "2010-01-13T15:20:00+00:00");

        var consultation = (ListResource) mappedResources.get(CONSULTATION_KEY).getFirst();
        assertThat(consultation.getEncounter().getReference()).isEqualTo(ENCOUNTER_ID);

        var topic = (ListResource) mappedResources.get(TOPIC_KEY).getFirst();
        assertThat(topic.getEncounter().getReference()).isEqualTo(ENCOUNTER_ID);
        assertThat(consultation.getEntryFirstRep().getItem().getReference()).isEqualTo(ENCOUNTER_ID);

        verify(resourceReferenceUtil, atLeast(1))
            .extractChildReferencesFromEhrComposition(any(), any());
        verifyCreateMetaAndAddSecurityCalled(1, Optional.empty());
    }

    @Test
    public void testEncounterWithMappedResourcesWithFlatConsultationThatItReturnsRelatedProblemsForConditions() {
        when(consultationListMapper.mapToConsultation(any(RCMRMT030101UKEhrComposition.class), any(Encounter.class)))
            .thenReturn(getList());
        when(consultationListMapper.mapToTopic(any(ListResource.class), isNull())).thenReturn(getList());
        doAnswer(invocation -> {
            List<Reference> list = invocation.getArgument(1);
            list.add(new Reference("Observation/4971E81B-693C-11EE-9D98-00155D78C707"));
            list.add(new Reference("Condition/4971E81C-693C-11EE-9D98-00155D78C707"));
            list.add(new Reference("Observation/4971E81D-693C-11EE-9D98-00155D78C707"));
            list.add(new Reference("Condition/4971E81E-693C-11EE-9D98-00155D78C707"));
            list.add(new Reference("Condition/4971E81E-693C-11EE-9D98-00155D78C707"));
            return null;
        }).when(resourceReferenceUtil).extractChildReferencesFromEhrComposition(
            any(RCMRMT030101UKEhrComposition.class),
            anyList());

        var ehrExtract = unmarshallEhrExtractElement(FULL_VALID_FLAT_ENCOUNTER_WITH_RESOURCES_XML);

        Map<String, List<? extends DomainResource>> mappedResources = encounterMapper.mapEncounters(
            ehrExtract, patient, PRACTISE_CODE, entryLocations);

        assertThat(mappedResources.get(TOPIC_KEY).size()).isOne();
        assertThat(mappedResources.get(TOPIC_KEY).getFirst().getExtension()).hasSize(2);
        assertEquals(RELATED_PROBLEM_EXT_URL, mappedResources.get(TOPIC_KEY).getFirst().getExtension().get(0).getUrl());
        assertEquals(RELATED_PROBLEM_EXT_URL, mappedResources.get(TOPIC_KEY).getFirst().getExtension().get(1).getUrl());
    }

    @ParameterizedTest
    @MethodSource("encounterPeriodTestFiles")
    public void testEncounterPeriod(String inputXML, String startDate, String endDate)   {
        when(consultationListMapper.mapToConsultation(any(RCMRMT030101UKEhrComposition.class), any(Encounter.class)))
            .thenReturn(getList());
        when(consultationListMapper.mapToTopic(any(ListResource.class), isNull()))
            .thenReturn(getList());

        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtractElement(inputXML);

        Map<String, List<? extends DomainResource>> mappedResources = encounterMapper.mapEncounters(
                ehrExtract, patient, PRACTISE_CODE, entryLocations
        );

        var encounterList = mappedResources.get(ENCOUNTER_KEY);
        assertThat(encounterList.size()).isOne();

        var encounter = (Encounter) mappedResources.get(ENCOUNTER_KEY).getFirst();
        assertEncounter(encounter, "5EB5D070-8FE1-11EC-B1E5-0800200C9A66", false, startDate, endDate);
        verifyCreateMetaAndAddSecurityCalled(1, Optional.empty());
    }

    @ParameterizedTest
    @MethodSource("invalidEhrCompositionTestFiles")
    public void testInvalidEhrCompositions(String inputXML) {
        final RCMRMT030101UKEhrExtract ehrExtract = unmarshallEhrExtractElement(inputXML);

        Map<String, List<? extends DomainResource>> mappedResources = encounterMapper.mapEncounters(
                ehrExtract, patient, PRACTISE_CODE, entryLocations
        );

        assertThat(mappedResources.get(ENCOUNTER_KEY)).isEmpty();
        assertThat(mappedResources.get(CONSULTATION_KEY)).isEmpty();
        assertThat(mappedResources.get(TOPIC_KEY)).isEmpty();
        assertThat(mappedResources.get(CATEGORY_KEY)).isEmpty();
    }

    private static Stream<Arguments> encounterPeriodTestFiles() {
        return Stream.of(
            Arguments.of(EFFECTIVE_CENTER_ENCOUNTER_PERIOD_XML, "2010-01-13T15:20:00+00:00", null),
            Arguments.of(EFFECTIVE_LOW_AND_HIGH_ENCOUNTER_PERIOD_XML, "2010-01-13T15:20:00+00:00", "2015-01-13T15:20:00+00:00"),
            Arguments.of(EFFECTIVE_LOW_ENCOUNTER_PERIOD_XML, "2010-01-13T15:20:00+00:00", null),
            Arguments.of(EFFECTIVE_HIGH_ENCOUNTER_PERIOD_XML, null, "2010-01-13T15:20:00+00:00"),
            Arguments.of(EFFECTIVE_CENTER_UNK_NULL_FLAVOR_ENCOUNTER_PERIOD_XML, null, null),
            Arguments.of(AVAILABILITY_TIME_ENCOUNTER_PERIOD_XML, "2010-01-13T15:20:00+00:00", null),
            Arguments.of(NO_ENCOUNTER_PERIOD_XML, "2012-06-15T14:20:00+00:00", null),
            Arguments.of(NULL_FLAVOR_EFFECTIVE_TIMES_XML, "2010-01-13T15:20:00+00:00", null)
        );
    }

    private static Stream<Arguments> invalidEhrCompositionTestFiles() {
        return Stream.of(
            Arguments.of(INVALID_ENCOUNTER_CODE_1_XML),
            Arguments.of(INVALID_ENCOUNTER_CODE_2_XML),
            Arguments.of(SUPPRESSED_COMPOSITION_WITH_EHR_EMPTY_XML),
            Arguments.of(SUPPRESSED_COMPOSITION_WITH_REGISTRATION_STATEMENT_XML)
        );
    }

    private void assertEncounter(
            Encounter encounter,
            String id,
            Boolean hasLocation,
            String startDate,
            String endDate
    ) {
        assertThat(encounter.getId()).isEqualTo(id);
        assertThat(encounter.getMeta().getProfile().getFirst().getValue()).isEqualTo(META_PROFILE);
        assertThat(encounter.getIdentifierFirstRep().getSystem()).isEqualTo(IDENTIFIER_SYSTEM);
        assertThat(encounter.getIdentifierFirstRep().getValue()).isEqualTo(id);
        assertThat(encounter.getStatus()).isEqualTo(EncounterStatus.FINISHED);
        assertThat(encounter.getSubject().getResource()).isEqualTo(patient);
        assertLocation(encounter, hasLocation);
        assertPeriod(encounter.getPeriod(), startDate, endDate);
    }

    private void assertLocation(Encounter encounter, boolean hasLocation) {
        if (hasLocation) {
            assertThat(encounter.getLocationFirstRep().getLocation().getReference())
                .isEqualTo(LOCATION_PREFIX + EncounterMapperTest.LOCATION_ID);
        } else {
            assertThat(encounter.getLocation()).isEmpty();
        }
    }

    private void assertMetaSecurityPresent(Meta meta) {
        assertAll(
            () -> assertThat(meta.getSecurity()).hasSize(1),
            () -> assertThat(meta.getSecurity().getFirst().getCode())
                .isEqualTo("NOPAT"),
            () -> assertThat(meta.getSecurity().getFirst().getSystem())
                .isEqualTo("http://hl7.org/fhir/v3/ActCode"),
            () -> assertThat(meta.getSecurity().getFirst().getDisplay())
                .isEqualTo("no disclosure to patient, family or caregivers without attending provider's authorization")
        );
    }

    private void assertPeriod(Period period, String startDate, String endDate) {
        assertThat(period.getStartElement().getValueAsString()).isEqualTo(startDate);
        assertThat(period.getEndElement().getValueAsString()).isEqualTo(endDate);
    }

    private void setUpCodeableConceptMock() {

        var codeableConcept = createCodeableConcept(null, SNOMED_SYSTEM, CODING_DISPLAY);
        lenient().when(codeableConceptMapper.mapToCodeableConcept(any())).thenReturn(codeableConcept);
        lenient().when(immunizationChecker.isImmunization(any())).thenAnswer((Answer<Boolean>) invocation -> {
            String code = invocation.getArgument(0);
            return code.equals("1664081000000114");
        });
    }

    private ListResource getList() {
        ListResource listResource = new ListResource();
        listResource.setEncounter(new Reference(ENCOUNTER_ID));
        listResource.getEntry().add(new ListResource.ListEntryComponent().setItem(new Reference(ENCOUNTER_ID)));
        listResource.getEntry().add(new ListResource.ListEntryComponent().setItem(new Reference(ENCOUNTER_ID_2)));
        return listResource;
    }

    @SneakyThrows
    private RCMRMT030101UKEhrExtract unmarshallEhrExtractElement(String fileName) {
        return unmarshallFile(getFile("classpath:" + XML_RESOURCES_BASE + fileName), RCMRMT030101UKEhrExtract.class);
    }

    @SafeVarargs
    private void verifyCreateMetaAndAddSecurityCalled(int expectedCalls, Optional<CV>... cvs) {
        verify(confidentialityService, times(expectedCalls))
            .createMetaAndAddSecurityIfConfidentialityCodesPresent(META_PROFILE, cvs);
    }
}