package uk.nhs.adaptors.pss.translator.mapper;

import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.Quantity;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.StringType;
import org.hl7.v3.CD;
import org.hl7.v3.CR;
import org.hl7.v3.CV;
import org.hl7.v3.RCMRMT030101UKAnnotation;
import org.hl7.v3.RCMRMT030101UKCompoundStatement;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKObservationStatement;
import org.hl7.v3.RCMRMT030101UKPertinentInformation02;
import org.hl7.v3.RCMRMT030101UKRequestStatement;
import org.hl7.v3.RCMRMT030101UKSubject;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.nhs.adaptors.pss.translator.service.ConfidentialityService;
import uk.nhs.adaptors.pss.translator.util.CompoundStatementResourceExtractors;
import uk.nhs.adaptors.pss.translator.util.DatabaseImmunizationChecker;
import uk.nhs.adaptors.pss.translator.util.DegradedCodeableConcepts;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hl7.fhir.dstu3.model.Observation.ObservationStatus.FINAL;
import static uk.nhs.adaptors.pss.translator.util.CompoundStatementResourceExtractors.extractAllObservationStatementsWithoutAllergiesAndBloodPressures;
import static uk.nhs.adaptors.pss.translator.util.CompoundStatementResourceExtractors.extractAllRequestStatements;
import static uk.nhs.adaptors.pss.translator.util.ObservationUtil.getEffective;
import static uk.nhs.adaptors.pss.translator.util.ObservationUtil.getInterpretation;
import static uk.nhs.adaptors.pss.translator.util.ObservationUtil.getIssued;
import static uk.nhs.adaptors.pss.translator.util.ObservationUtil.getReferenceRange;
import static uk.nhs.adaptors.pss.translator.util.ObservationUtil.getValueQuantity;
import static uk.nhs.adaptors.pss.translator.util.ParticipantReferenceUtil.getParticipantReference;
import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.buildIdentifier;
import static uk.nhs.adaptors.pss.translator.util.ResourceUtil.addContextToObservation;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ObservationMapper extends AbstractMapper<Observation> {
    private static final String META_PROFILE = "Observation-1";
    private static final String SUBJECT_COMMENT = "Subject: %s";
    private static final String SELF_REFERRAL = "SelfReferral";
    private static final String URGENCY = "Urgency";
    private static final String TEXT = "Text";
    private static final String EPISODICITY_COMMENT = "Episodicity : %s";
    private static final BigInteger MINUS_ONE = new BigInteger("-1");

    private final CodeableConceptMapper codeableConceptMapper;
    private final DatabaseImmunizationChecker immunizationChecker;
    private final ConfidentialityService confidentialityService;

    public List<Observation> mapResources(RCMRMT030101UKEhrExtract ehrExtract, Patient patient, List<Encounter> encounters,
                                          String practiceCode) {

        List<Observation> selfReferralObservations =
                mapEhrExtractToFhirResource(ehrExtract, (extract, composition, component) ->
                extractAllRequestStatements(component)
                        .filter(Objects::nonNull)
                        .filter(this::isSelfReferral)
                        .map(observationStatement
                                -> mapObservationFromRequestStatement(composition, observationStatement,
                                                                      patient, encounters, practiceCode)))
                .toList();

        List<Observation> observations =
                mapEhrExtractToFhirResource(ehrExtract, (extract, composition, component) ->
                extractAllObservationStatementsWithoutAllergiesAndBloodPressures(component)
                .filter(Objects::nonNull)
                .filter(this::isNotImmunization)
                .map(observationStatement
                    -> mapObservation(composition, observationStatement, patient, encounters, practiceCode)))
            .toList();

        return Stream.concat(selfReferralObservations.stream(), observations.stream()).collect(Collectors.toList());
    }

    private Observation mapObservation(RCMRMT030101UKEhrComposition ehrComposition,
                                       RCMRMT030101UKObservationStatement observationStatement,
                                       Patient patient,
                                       List<Encounter> encounters,
                                       String practiceCode) {

        var compoundStatement = ehrComposition
            .getComponent()
            .stream()
            .flatMap(CompoundStatementResourceExtractors::extractAllCompoundStatements)
            .filter(Objects::nonNull)
            .findFirst().orElseGet(RCMRMT030101UKCompoundStatement::new);

        var id = observationStatement.getId().getRoot();
        var meta = confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            observationStatement.getConfidentialityCode(),
            compoundStatement.getConfidentialityCode());

        var observation = buildBaseObservation(ehrComposition, observationStatement, patient, practiceCode, id, meta);

        addContextToObservation(observation, encounters, ehrComposition);
        addValue(observation, getValueQuantity(observationStatement.getValue(), observationStatement.getUncertaintyCode()),
            getValueString(observationStatement.getValue()));
        addEffective(observation,
            getEffective(observationStatement.getEffectiveTime(), observationStatement.getAvailabilityTime()));

        return observation;
    }

    private @NotNull Observation buildBaseObservation(RCMRMT030101UKEhrComposition ehrComposition,
                                                RCMRMT030101UKObservationStatement observationStatement,
                                                Patient patient,
                                                String practiceCode,
                                                String id,
                                                Meta meta) {
        var observation = new Observation()
            .setStatus(FINAL)
            .addIdentifier(buildIdentifier(id, practiceCode))
            .setCode(getCode(observationStatement.getCode()))
            .setIssuedElement(getIssued(ehrComposition))
            .addPerformer(getParticipantReference(observationStatement.getParticipant(), ehrComposition))
            .setInterpretation(getInterpretation(observationStatement.getInterpretationCode()))
            .setComment(getComment(
                observationStatement.getPertinentInformation(),
                observationStatement.getSubject(),
                observationStatement.getCode(),
                Optional.of(getQualifiers(observationStatement))
            ))
            .setReferenceRange(getReferenceRange(observationStatement.getReferenceRange()))
            .setSubject(new Reference(patient))
            .setMeta(meta)
            .setId(id);

        return (Observation) observation;
    }

    private static @NotNull List<CR> getQualifiers(RCMRMT030101UKObservationStatement observationStatement) {
        return Optional.ofNullable(observationStatement.getCode())
            .map(CD::getQualifier)
            .orElse(Collections.emptyList());
    }

    private Observation mapObservationFromRequestStatement(RCMRMT030101UKEhrComposition ehrComposition,
                                                           RCMRMT030101UKRequestStatement requestStatement, Patient patient,
                                                           List<Encounter> encounters, String practiceCode) {

        var observation = initializeObservation(ehrComposition, requestStatement, patient, practiceCode);

        addContextToObservation(observation, encounters, ehrComposition);
        addEffective(observation, getEffective(requestStatement.getEffectiveTime(), requestStatement.getAvailabilityTime()));

        return observation;
    }

    private Observation initializeObservation(RCMRMT030101UKEhrComposition ehrComposition,
                                                       RCMRMT030101UKRequestStatement requestStatement,
                                                       Patient patient,
                                                       String practiceCode) {

        var id = requestStatement.getId().getFirst().getRoot();

        final Meta meta = confidentialityService.createMetaAndAddSecurityIfConfidentialityCodesPresent(
            META_PROFILE,
            ehrComposition.getConfidentialityCode(),
            requestStatement.getConfidentialityCode());

        Observation observation = new Observation();
        observation
            .setStatus(FINAL)
            .addIdentifier(buildIdentifier(id, practiceCode))
            .setCode(getCode(requestStatement.getCode()))
            .setIssuedElement(getIssued(ehrComposition))
            .addPerformer(getParticipantReference(requestStatement.getParticipant(), ehrComposition))
            .setComment(SELF_REFERRAL)
            .setSubject(new Reference(patient))
            .setComponent(createComponentList(requestStatement))
            .setMeta(meta)
            .setId(id);

        return observation;
    }

    private boolean isNotImmunization(RCMRMT030101UKObservationStatement observationStatement) {

        if (observationStatement.hasCode() && observationStatement.getCode().hasCode()) {
            return !immunizationChecker.isImmunization(observationStatement);
        }
        return true;
    }

    private boolean isSelfReferral(RCMRMT030101UKRequestStatement requestStatement) {

        for (CR qualifier : requestStatement.getCode().getQualifier()) {
            if (qualifier.getValue().getCode().equals(SELF_REFERRAL)) {
                return true;
            }
        }
        return false;
    }

    private void addEffective(Observation observation, Object effective) {
        if (effective instanceof DateTimeType dateTimeType) {
            observation.setEffective(dateTimeType);
        } else if (effective instanceof Period period) {
            observation.setEffective(period);
        }
    }

    private void addValue(Observation observation, Quantity valueQuantity, String valueString) {
        if (valueQuantity != null) {
            observation.setValue(valueQuantity);
        } else if (StringUtils.isNotEmpty(valueString)) {
            observation.setValue(new StringType().setValue(valueString));
        }
    }

    private CodeableConcept getCode(CD code) {
        if (code == null) {
            return null;
        }

        var codeableConcept = codeableConceptMapper.mapToCodeableConcept(code);
        DegradedCodeableConcepts.addDegradedEntryIfRequired(codeableConcept, DegradedCodeableConcepts.DEGRADED_OTHER);
        return codeableConcept;
    }

    private String getValueString(Object value) {
        if (value instanceof String simpleValue) {
            return simpleValue;
        } else if (value instanceof CV cvValue) {
            return cvValue.getOriginalText() != null ? cvValue.getOriginalText() : cvValue.getDisplayName();
        }
        return null;
    }

    private String getComment(List<RCMRMT030101UKPertinentInformation02> pertinentInformation,
                              RCMRMT030101UKSubject subject,
                              CD code,
                              Optional<List<CR>> qualifiers) {
        StringJoiner stringJoiner = new StringJoiner(StringUtils.SPACE);

        if (subjectHasOriginalText(subject)) {
            stringJoiner.add(String.format(SUBJECT_COMMENT, subject.getPersonalRelationship().getCode().getOriginalText()));
        } else if (subjectHasDisplayName(subject)) {
            stringJoiner.add(String.format(SUBJECT_COMMENT, subject.getPersonalRelationship().getCode().getDisplayName()));
        }

        Optional<String> minusOneSequenceComment = extractSequenceCommentOfValue(MINUS_ONE, pertinentInformation);
        Optional<String> zeroSequenceComment = extractSequenceCommentOfValue(ZERO, pertinentInformation);
        Optional<String> postFixedSequenceComments = extractAllPostFixedSequenceComments(pertinentInformation);

        if (minusOneSequenceComment.isPresent()) {
            stringJoiner.add(minusOneSequenceComment.orElseThrow());

            if (code.hasOriginalText()) {
                stringJoiner.add(code.getOriginalText());
            }
        }

        zeroSequenceComment.ifPresent(stringJoiner::add);
        postFixedSequenceComments.ifPresent(stringJoiner::add);

        // Append episodicity to the comment.
        qualifiers.ifPresent(q -> appendEpisodicity(q, stringJoiner));

        return stringJoiner.toString();
    }

    /**
     * Append episodicity to comment separating from existing comments with <br> tag.
     * @param qualifiers
     * @param stringJoiner
     */
    private void appendEpisodicity(List<CR> qualifiers, StringJoiner stringJoiner) {
        qualifiers.stream()
                .map(this::buildEpisodicityText)
                .filter(Objects::nonNull)
                .forEach(et ->
                    stringJoiner.add("{" + EPISODICITY_COMMENT.formatted(et) + "}")
            );
    }

    /**
     * Build out the episodicity text in the same style as AllergyIntolerance.
     * @param qualifier
     * @return
     */
    private String buildEpisodicityText(CR qualifier) {
        var qualifierName = qualifier.getName();

        if (qualifierName == null) {
            return null;
        }

        var text = "code=" + qualifierName.getCode()
                + ", displayName=" + qualifierName.getDisplayName();

        if (qualifierName.hasOriginalText()) {
            return text + ", originalText=" + qualifierName.getOriginalText();
        }

        return text;
    }

    private Optional<String> extractSequenceCommentOfValue(BigInteger value,
        List<RCMRMT030101UKPertinentInformation02> pertinentInformation) {
        return pertinentInformation.stream()
            .filter(this::pertinentInformationHasOriginalText)
            .filter(pertinentInfo -> !pertinentInfo.getSequenceNumber().hasNullFlavor())
            .filter(pertinentInfo -> pertinentInfo.getSequenceNumber().getValue().equals(value))
            .map(RCMRMT030101UKPertinentInformation02::getPertinentAnnotation)
            .map(RCMRMT030101UKAnnotation::getText)
            .findFirst();
    }

    private Optional<String> extractAllPostFixedSequenceComments(List<RCMRMT030101UKPertinentInformation02> pertinentInformation) {
        String postFixedSequenceComments = pertinentInformation.stream()
            .filter(this::pertinentInformationHasOriginalText)
            .filter(pertinentInfo -> pertinentInfo.getSequenceNumber().hasNullFlavor()
                || pertinentInfo.getSequenceNumber().getValue().equals(ONE))
            .map(RCMRMT030101UKPertinentInformation02::getPertinentAnnotation)
            .map(RCMRMT030101UKAnnotation::getText)
            .collect(Collectors.joining(StringUtils.SPACE));

        if (StringUtils.isEmpty(postFixedSequenceComments)) {
            return Optional.empty();
        }

        return Optional.of(postFixedSequenceComments);
    }

    private boolean pertinentInformationHasOriginalText(RCMRMT030101UKPertinentInformation02 pertinentInformation) {
        return pertinentInformation != null && pertinentInformation.getPertinentAnnotation() != null
            && pertinentInformation.getPertinentAnnotation().getText() != null;
    }

    private boolean subjectHasOriginalText(RCMRMT030101UKSubject subject) {
        return subject != null && subject.getPersonalRelationship() != null
            && subject.getPersonalRelationship().getCode() != null && subject.getPersonalRelationship().getCode().getOriginalText() != null;
    }

    private boolean subjectHasDisplayName(RCMRMT030101UKSubject subject) {
        return subject != null && subject.getPersonalRelationship() != null
            && subject.getPersonalRelationship().getCode() != null && subject.getPersonalRelationship().getCode().getDisplayName() != null;
    }
    private List<Observation.ObservationComponentComponent> createComponentList(RCMRMT030101UKRequestStatement requestStatement) {
        List<Observation.ObservationComponentComponent> componentList = new ArrayList<>();

        Observation.ObservationComponentComponent urgency =
                new Observation.ObservationComponentComponent(
                        new CodeableConcept().setTextElement(new StringType(URGENCY)));
        urgency.setProperty("value[x]", new StringType(requestStatement.getPriorityCode().getOriginalText()));
        componentList.add(urgency);

        Observation.ObservationComponentComponent text =
                new Observation.ObservationComponentComponent(
                        new CodeableConcept().setTextElement(new StringType(TEXT)));
        text.setProperty("value[x]", new StringType(requestStatement.getText()));
        componentList.add(text);

        return componentList;
    }
}
