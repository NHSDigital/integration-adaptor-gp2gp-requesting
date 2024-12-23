package uk.nhs.adaptors.pss.translator.mapper;

import org.hl7.v3.CD;
import org.hl7.v3.CV;
import org.hl7.v3.II;
import org.hl7.v3.INT;
import org.hl7.v3.RCMRMT030101UKAnnotation;
import org.hl7.v3.RCMRMT030101UKComponent;
import org.hl7.v3.RCMRMT030101UKComponent3;
import org.hl7.v3.RCMRMT030101UKComponent4;
import org.hl7.v3.RCMRMT030101UKConditionNamed;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKEhrFolder;
import org.hl7.v3.RCMRMT030101UKInformant;
import org.hl7.v3.RCMRMT030101UKLinkSet;
import org.hl7.v3.RCMRMT030101UKObservationStatement;
import org.hl7.v3.RCMRMT030101UKParticipant;
import org.hl7.v3.RCMRMT030101UKPertinentInformation02;
import org.hl7.v3.RCMRMT030101UKReason;
import org.hl7.v3.RCMRMT030101UKReference;
import org.hl7.v3.RCMRMT030101UKReferenceRange;
import org.hl7.v3.RCMRMT030101UKReplacementOf;
import org.hl7.v3.RCMRMT030101UKSequelTo;
import org.hl7.v3.RCMRMT030101UKSpecimen;
import org.hl7.v3.RCMRMT030101UKStatementRef;
import org.hl7.v3.RCMRMT030101UKSubject;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.math.BigInteger;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("checkstyle:MagicNumber")
class DuplicateObservationStatementMapperTest {

    private final DuplicateObservationStatementMapper mapper = new DuplicateObservationStatementMapper();

    @Test
    public void ignoresLinksetsWithoutAConditionNamedElement() {
        var ehrExtract = createExtract(List.of(
                generateLinksetComponent()
        ));

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);

        assertThat(firstEhrComposition(ehrExtract)).hasSize(1);
    }

    @Test
    public void removesUnreferencedObservationFromEhrExtractWhenSharedDescription() {
        var ehrExtract = createExtract(List.of(
                createObservation("ID-2", "101", "This is an observation which ends with ellipses but there is more."),
                createObservation("ID-1", "101", "This is an observation which ends with ellipses..."),
                generateLinksetComponent("ID-1")
        ));

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);

        assertThat(firstEhrComposition(ehrExtract)).hasSize(2);
        assertThat(firstEhrComposition(ehrExtract).getFirst().getObservationStatement().getId().getRoot()).isEqualTo("ID-1");
    }

    @Test
    public void removesFirstObservationWhenThereAreMultipleWithMatchingDescriptions() {
        var ehrExtract = createExtract(List.of(
                createObservation("ID-2", "101", "This is an observation which ends with ellipses but there is more"),
                createObservation("ID-3", "101", "This is an observation which ends with ellipses but there is MORE"),
                createObservation("ID-1", "101", "This is an observation which ends with ellipses..."),
                generateLinksetComponent("ID-1")
        ));

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);

        assertThat(firstEhrComposition(ehrExtract)).hasSize(3);
        assertThat(firstEhrComposition(ehrExtract).getFirst().getObservationStatement().getId().getRoot()).isEqualTo("ID-3");
    }

    @Test
    public void removesObservationInSecondEhrComposition() {
        var ehrExtract = createExtract(
                List.of(),
                List.of(
                        createObservation("ID-2", "101", "This is an observation which ends with ellipses removed."),
                        createObservation("ID-1", "101", "This is an observation which ends with ellipses..."),
                        generateLinksetComponent("ID-1")
                )
        );

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);

        assertThat(secondEhrComposition(firstEhrFolder(ehrExtract)).getComponent()).hasSize(2);
    }

    @Test
    public void removesObservationInSecondEhrFolder() {
        var ehrExtract = createExtract(
                createEhrFolder(List.of()),
                createEhrFolder(List.of(
                        createObservation("ID-2", "101", "This is an observation which ends with ellipses removed."),
                        createObservation("ID-1", "101", "This is an observation which ends with ellipses..."),
                        generateLinksetComponent("ID-1")
                ))
        );

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);

        assertThat(firstEhrComposition(secondEhrFolder(ehrExtract)).getComponent()).hasSize(2);
    }

    @Test
    public void doesntMergeObservationsWhichAreNotReferencedByALinkset() {
        var ehrExtract = createExtract(List.of(
                createObservation("ID-1", "101", "This is an observation which ends with ellipses..."),
                createObservation("ID-2", "101", "This is an observation which ends with ellipses..."),
                createObservation("ID-1", "101", "This is an observation which ends with ellipses...")
        ));

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);

        assertThat(firstEhrComposition(ehrExtract)).hasSize(3);
    }

    @Test
    public void doesntMergeObservationWhichIsntReferencedByDifferentLinkset() {
        var ehrExtract = createExtract(List.of(
                createObservation("ID-1", "101", "This is an observation which ends with ellipses..."),
                createObservation("ID-2", "101", "This is an observation which ends with ellipses..."),
                generateLinksetComponent("ID-3")
        ));

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);

        assertThat(firstEhrComposition(ehrExtract)).hasSize(3);
    }

    @Test
    public void doesntMergeObservationWhereTheDescriptionDoesntSubStringOfAnother() {
        var ehrExtract = createExtract(List.of(
                createObservation("ID-1", "101", "This is an observation which ends with ellipses..."),
                createObservation("ID-2", "101", "ThIS iS An oBSerVATion whIch EnDs wItH eLliPseS bUt there is more."),
                generateLinksetComponent("ID-1")
        ));

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);

        assertThat(firstEhrComposition(ehrExtract)).hasSize(3);
    }

    @Test
    public void doesntMergeObservationWhereTheCodesArentTheSame() {
        Logger fooLogger = (Logger) LoggerFactory.getLogger(DuplicateObservationStatementMapper.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        fooLogger.addAppender(listAppender);

        var ehrExtract = createExtract(List.of(
            createObservation("ID-1", "101", "This is an observation which ends with ellipses..."),
            createObservation("ID-2", "102", "This is an observation which ends with ellipses but there is more."),
            generateLinksetComponent("ID-1")
                                              ));

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);

        assertThat(firstEhrComposition(ehrExtract)).hasSize(3);
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(Level.INFO, logsList.get(0).getLevel());
        assertEquals("ObservationStatement: 'ID-1' appears to have been truncated but no match was found.",
                     logsList.get(0).getFormattedMessage());
    }


    @Test
    public void mergesWhenTextIsPrefixedWithTextObservationWhereTheCodesArentTheSame() {
        var ehrExtract = createExtract(List.of(
                createObservation("ID-1", "101", "PREFIXED STUFF This is an observation which ends with ellipses..."),
                createObservation("ID-2", "101", "This is an observation which ends with ellipses but there is more"),
                generateLinksetComponent("ID-1")
        ));

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);

        assertThat(firstEhrComposition(ehrExtract)).hasSize(2);
    }

    @Test
    public void doesntMergeObservationWhereTheLinkedObservationSequenceNumberIsNot1() {
        var ehrExtract = createExtract(List.of(
                createObservation("ID-1", generateCodeableConcept("101"), "This is an observation which ends with ellipses...", 0),
                createObservation("ID-2", "101", "This is an observation which ends with ellipses removed."),
                generateLinksetComponent("ID-1")
        ));

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);

        assertThat(firstEhrComposition(ehrExtract)).hasSize(3);
    }

    @Test
    public void doesntMergeObservationWhereTheMatchingObservationSequenceNumberIsNot1() {
        var ehrExtract = createExtract(List.of(
                createObservation("ID-1", "101", "This is an observation which ends with ellipses..."),
                createObservation("ID-2", generateCodeableConcept("101"), "This is an observation which ends with ellipses removed.", 0),
                generateLinksetComponent("ID-1")
        ));

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);

        assertThat(firstEhrComposition(ehrExtract)).hasSize(3);
    }

    @Test
    public void doesntMergeObservationWhereTheLinkedObservationHasMultiplePertinentInformation() {
        var ehrExtract = createExtract(List.of(
                createObservation("ID-2", "101", "This is an observation which ends with ellipses removed."),
                createObservationWithMultiplePertinentInformation("ID-1", "101", "This is an observation which ends with ellipses..."),
                generateLinksetComponent("ID-1")
        ));

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);

        assertThat(firstEhrComposition(ehrExtract)).hasSize(3);
    }

    @Test
    public void doesntMergeObservationWhereTheMatchingObservationHasMultiplePertinentInformation() {
        var ehrExtract = createExtract(List.of(
            createObservationWithMultiplePertinentInformation("ID-2", "101", "This is an observation which ends with ellipses removed."),
            createObservation("ID-1", "101", "This is an observation which ends with ellipses..."),
            generateLinksetComponent("ID-1")
        ));

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);

        assertThat(firstEhrComposition(ehrExtract)).hasSize(3);
    }

    @Test
    public void doesntMergeObservationWhereTheObservationDoesNotEndInEllipses() {
        var ehrExtract = createExtract(List.of(
                createObservation("ID-1", "101", "This is an observation doesnt end with ellipses:::"),
                createObservation("ID-2", "101", "This is an observation which ends with ellipses removed."),
                generateLinksetComponent("ID-1")
        ));

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);

        assertThat(firstEhrComposition(ehrExtract)).hasSize(3);
    }

    @Test
    public void doNotRemoveObservationWhereTheObservationDoesNotEndInEllipsesButObservationStatementsAllHavePertinentAnnotation() {
        var ehrExtract = createExtract(
            List.of(createObservation("ID-1", "101", "This is an observation doesnt end with ellipses:::"),
                    createObservation("ID-2", "101", "This is an observation which ends with ellipses removed."),
                    generateLinksetComponent("ID-1")));

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);

        assertThat(firstEhrComposition(ehrExtract)).hasSize(3);
    }

    @Test
    public void removeObservationWithoutPertinentAnnotation() {
        var ehrExtract = createExtract(
            List.of(createObservation("ID-1", "101", "This is an observation doesnt end with ellipses:::"),
                    createObservationWithoutPertinentAnnotation("ID-2", "101"),
                    generateLinksetComponent("ID-1")));

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);

        assertThat(firstEhrComposition(ehrExtract)).hasSize(2);
    }

    @Test
    public void doesNotRemoveObservationWithTwoPertinentAnnotations() {
        var ehrExtract = createExtract(
            List.of(createObservation("ID-1", "101", "This is an observation doesnt end with ellipses:::"),
                    createObservationWithTwoPertinentAnnotations("ID-2", "101", "Pertinent annotation 1",
                                                                 "Pertinent annotation 2"),
                    generateLinksetComponent("ID-1")));

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);

        assertThat(firstEhrComposition(ehrExtract)).hasSize(3);
    }

    @Test
    public void doesntMergeObservationWhereTheObservationIsLessThan47CharsAndCodeableConceptsAreDifferent() {
        var ehrExtract = createExtract(List.of(
                createObservation("ID-1", "101", "This text is too short..."),
                createObservation("ID-2", "102", "This text is too short to be replaced."),
                generateLinksetComponent("ID-1")
        ));

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);

        assertThat(firstEhrComposition(ehrExtract)).hasSize(3);
    }

    @Test
    public void replacesTextWithinTruncatedObservationWithFullObservation() {
        var ehrExtract = createExtract(List.of(
                createObservation("ID-1", "101", "FIRST PREFIX This is an observation which ends with ellipses..."),
                createObservation("ID-2", "101", "SECOND PREFIX This is an observation which ends with ellipses removed."),
                generateLinksetComponent("ID-1")
        ));

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);

        assertThat(firstPertinentInformationText(firstEhrComposition(ehrExtract).getFirst().getObservationStatement())).isEqualTo(
                "FIRST PREFIX SECOND PREFIX This is an observation which ends with ellipses removed."
        );
    }

    @Test
    public void mergesMultipleTruncatedLinksets() {
        var ehrExtract = createExtract(List.of(
                createObservation("ID-2", "101", "This is an observation which ends with ellipses removed."),
                createObservation("ID-1", "101", "This is an observation which ends with ellipses..."),
                generateLinksetComponent("ID-1"),
                createObservation("ID-4", "102", "THIS IS AN OBSERVATION which ends with ellipses removed."),
                createObservation("ID-3", "102", "THIS IS AN OBSERVATION which ends with ellipses..."),
                generateLinksetComponent("ID-3")
        ));

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);

        assertThat(firstEhrComposition(ehrExtract)).hasSize(4);
    }

    @ParameterizedTest
    @MethodSource("generator")
    public void doesntMergeObservationWhereTheLinkedObservationFieldIsPopulated(
            Consumer<RCMRMT030101UKObservationStatement> observation) {

        var ehrExtract = createExtract(List.of(
                createObservation("ID-2", "101", "This is an observation which ends with ellipses but there is more."),
                createObservationWithFollowingFieldIsPopulated("ID-1", "101", "This is an observation which ends with ellipses...",
                        1, observation),
                generateLinksetComponent("ID-1")
        ));

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);
        assertThat(firstEhrComposition(ehrExtract)).hasSize(3);

    }

    @ParameterizedTest
    @MethodSource("generator")
    public void doesntMergeObservationWhereTheObservationFieldIsPopulated(
            Consumer<RCMRMT030101UKObservationStatement> observation) {

        var ehrExtract = createExtract(List.of(
                createObservationWithFollowingFieldIsPopulated("ID-2", "101",
                        "This is an observation which ends with ellipses but there is more.",
                        1, observation),
                createObservation("ID-1", "101", "This is an observation which ends with ellipses..."),
                generateLinksetComponent("ID-1")
        ));

        mapper.mergeOrRemoveDuplicateObservationStatements(ehrExtract);
        assertThat(firstEhrComposition(ehrExtract)).hasSize(3);

    }


    private static String firstPertinentInformationText(RCMRMT030101UKObservationStatement observationStatement) {
        return observationStatement.getPertinentInformation().getFirst().getPertinentAnnotation().getText();
    }

    private static List<RCMRMT030101UKComponent4> firstEhrComposition(RCMRMT030101UKEhrExtract ehrExtract) {
        return firstEhrComposition(firstEhrFolder(ehrExtract)).getComponent();
    }

    private static RCMRMT030101UKEhrComposition firstEhrComposition(RCMRMT030101UKEhrFolder rcmrmt030101UKEhrFolder) {
        return rcmrmt030101UKEhrFolder.getComponent().getFirst().getEhrComposition();
    }

    private static RCMRMT030101UKEhrComposition secondEhrComposition(RCMRMT030101UKEhrFolder rcmrmt030101UKEhrFolder) {
        return rcmrmt030101UKEhrFolder.getComponent().get(1).getEhrComposition();
    }

    private static RCMRMT030101UKEhrFolder firstEhrFolder(RCMRMT030101UKEhrExtract ehrExtract) {
        return ehrExtract.getComponent().getFirst().getEhrFolder();
    }

    private static RCMRMT030101UKEhrFolder secondEhrFolder(RCMRMT030101UKEhrExtract ehrExtract) {
        return ehrExtract.getComponent().get(1).getEhrFolder();
    }

    @NotNull
    private static RCMRMT030101UKComponent4 generateLinksetComponent(String namedConditionId) {
        RCMRMT030101UKComponent4 component = generateLinksetComponent();
        RCMRMT030101UKConditionNamed namedCondition = new RCMRMT030101UKConditionNamed();
        RCMRMT030101UKStatementRef value2 = new RCMRMT030101UKStatementRef();
        value2.setId(generateId(namedConditionId));
        namedCondition.setNamedStatementRef(value2);
        component.getLinkSet().setConditionNamed(namedCondition);
        return component;
    }

    @NotNull
    private static RCMRMT030101UKComponent4 generateLinksetComponent() {
        RCMRMT030101UKComponent4 component = new RCMRMT030101UKComponent4();
        component.setLinkSet(new RCMRMT030101UKLinkSet());
        return component;
    }

    @NotNull
    private static RCMRMT030101UKComponent4 createObservationWithoutPertinentAnnotation(String id, String code) {
        return createObservationWithoutPertinentAnnotation(id, generateCodeableConcept(code));
    }

    @NotNull
    private static RCMRMT030101UKComponent4 createObservationWithTwoPertinentAnnotations(String id,
                                                                                         String code,
                                                                                         String pertinentAnnotation1,
                                                                                         String pertinentAnnotation2) {
        return createObservationWithTwoPertinentAnnotations(id, generateCodeableConcept(code),
                                                            pertinentAnnotation1, pertinentAnnotation2, 1);
    }

    @NotNull
    private static RCMRMT030101UKComponent4 createObservation(String id, String code, String pertinentAnnotation) {
        return createObservation(id, generateCodeableConcept(code), pertinentAnnotation, 1);
    }

    @NotNull
    private static RCMRMT030101UKComponent4 createObservationWithTwoPertinentAnnotations(
        String id, @NotNull CD code, String pertinentAnnotation1, String pertinentAnnotation2, int annotationSequenceNumber) {
        RCMRMT030101UKComponent4 rcmrmt030101UKComponent4 = new RCMRMT030101UKComponent4();
        RCMRMT030101UKObservationStatement observationStatement = new RCMRMT030101UKObservationStatement();
        observationStatement.setCode(code);
        observationStatement.setId(generateId(id));
        observationStatement.getPertinentInformation().add(getPertinentInformation(pertinentAnnotation1, annotationSequenceNumber));
        observationStatement.getPertinentInformation().add(getPertinentInformation(pertinentAnnotation2, annotationSequenceNumber + 1));
        rcmrmt030101UKComponent4.setObservationStatement(observationStatement);
        return rcmrmt030101UKComponent4;
    }

    @NotNull
    private static RCMRMT030101UKComponent4 createObservation(
            String id, @NotNull CD code, String pertinentAnnotation, int annotationSequenceNumber) {
        RCMRMT030101UKComponent4 rcmrmt030101UKComponent4 = new RCMRMT030101UKComponent4();
        RCMRMT030101UKObservationStatement observationStatement = new RCMRMT030101UKObservationStatement();
        observationStatement.setCode(code);
        observationStatement.setId(generateId(id));
        observationStatement.getPertinentInformation().add(getPertinentInformation(pertinentAnnotation, annotationSequenceNumber));
        rcmrmt030101UKComponent4.setObservationStatement(observationStatement);
        return rcmrmt030101UKComponent4;
    }

    @NotNull
    private static RCMRMT030101UKComponent4 createObservationWithoutPertinentAnnotation(String id, @NotNull CD code) {
        RCMRMT030101UKComponent4 rcmrmt030101UKComponent4 = new RCMRMT030101UKComponent4();
        RCMRMT030101UKObservationStatement observationStatement = new RCMRMT030101UKObservationStatement();
        observationStatement.setCode(code);
        observationStatement.setId(generateId(id));
        rcmrmt030101UKComponent4.setObservationStatement(observationStatement);
        return rcmrmt030101UKComponent4;
    }

    @NotNull
    private static RCMRMT030101UKComponent4 createObservationWithFollowingFieldIsPopulated(
            String id, String code, String pertinentAnnotation, int annotationSequenceNumber,
            Consumer<RCMRMT030101UKObservationStatement> observationLambda) {
        RCMRMT030101UKComponent4 rcmrmt030101UKComponent4 = new RCMRMT030101UKComponent4();
        RCMRMT030101UKObservationStatement observationStatement = new RCMRMT030101UKObservationStatement();
        observationStatement.setCode(generateCodeableConcept(code));
        observationStatement.setId(generateId(id));
        observationStatement.getPertinentInformation().add(getPertinentInformation(pertinentAnnotation, annotationSequenceNumber));
        observationLambda.accept(observationStatement);
        rcmrmt030101UKComponent4.setObservationStatement(observationStatement);
        return rcmrmt030101UKComponent4;
    }

    @NotNull
    private static RCMRMT030101UKComponent4 createObservationWithMultiplePertinentInformation(
            String id, String code, String pertinentAnnotation) {
        var observation = createObservation(id, code, pertinentAnnotation);
        observation.getObservationStatement().getPertinentInformation().add(
                getPertinentInformation(pertinentAnnotation, -1)
        );
        return observation;
    }

    @NotNull
    private static RCMRMT030101UKPertinentInformation02 getPertinentInformation(String pertinentAnnotation, int sequenceNumber) {
        RCMRMT030101UKPertinentInformation02 e = new RCMRMT030101UKPertinentInformation02();
        e.setSequenceNumber(getAnInt(sequenceNumber));
        e.setPertinentAnnotation(getAnnotation(pertinentAnnotation));
        return e;
    }

    @NotNull
    private static RCMRMT030101UKAnnotation getAnnotation(String pertinentAnnotation) {
        RCMRMT030101UKAnnotation annotation = new RCMRMT030101UKAnnotation();
        annotation.setText(pertinentAnnotation);
        return annotation;
    }

    @NotNull
    private static INT getAnInt(int anInt) {
        INT i = new INT();
        i.setValue(BigInteger.valueOf(anInt));
        return i;
    }

    @NotNull
    private static II generateId(String id) {
        II value = new II();
        value.setRoot(id);
        return value;
    }

    @NotNull
    private static CD generateCodeableConcept(String code) {
        CD value = new CD();
        value.setCode(code);
        return value;
    }

    private static RCMRMT030101UKEhrExtract createExtract(RCMRMT030101UKComponent... folders) {
        RCMRMT030101UKEhrExtract rcmrmt030101UKEhrExtract = new RCMRMT030101UKEhrExtract();
        for (var folder : folders) {
            rcmrmt030101UKEhrExtract.getComponent().add(folder);
        }
        return rcmrmt030101UKEhrExtract;
    }

    @SafeVarargs
    private static RCMRMT030101UKEhrExtract createExtract(List<RCMRMT030101UKComponent4>... components) {
        return createExtract(createEhrFolder(components));
    }

    @NotNull
    @SafeVarargs
    private static RCMRMT030101UKComponent createEhrFolder(List<RCMRMT030101UKComponent4>... components) {
        RCMRMT030101UKComponent rcmrmt030101UKComponent = new RCMRMT030101UKComponent();
        RCMRMT030101UKEhrFolder rcmrmt030101UKEhrFolder = new RCMRMT030101UKEhrFolder();
        for (List<RCMRMT030101UKComponent4> component : components) {
            rcmrmt030101UKEhrFolder.getComponent().add(generateEhrComposition(component));
        }
        rcmrmt030101UKComponent.setEhrFolder(rcmrmt030101UKEhrFolder);
        return rcmrmt030101UKComponent;
    }

    @NotNull
    private static RCMRMT030101UKComponent3 generateEhrComposition(List<RCMRMT030101UKComponent4> components) {
        RCMRMT030101UKComponent3 rcmrmt030101UKComponent3 = new RCMRMT030101UKComponent3();
        RCMRMT030101UKEhrComposition rcmrmt030101UKEhrComposition = new RCMRMT030101UKEhrComposition();
        rcmrmt030101UKEhrComposition.getComponent().addAll(components);
        rcmrmt030101UKComponent3.setEhrComposition(rcmrmt030101UKEhrComposition);
        return rcmrmt030101UKComponent3;
    }

    private static Stream<Arguments> generator() {

        return Stream.of(
                Arguments.of(Named.of("Priority Code", (Consumer<RCMRMT030101UKObservationStatement>)
                        (RCMRMT030101UKObservationStatement observation) -> observation.setPriorityCode(new CV()))),
                Arguments.of(Named.of("Uncertainity Code", (Consumer<RCMRMT030101UKObservationStatement>)
                        (RCMRMT030101UKObservationStatement observation) -> observation.setUncertaintyCode(new CV()))),
                Arguments.of(Named.of("Value Code", (Consumer<RCMRMT030101UKObservationStatement>)
                        (RCMRMT030101UKObservationStatement observation) -> observation.setValue(new CV()))),
                Arguments.of(Named.of("Interpretation Value", (Consumer<RCMRMT030101UKObservationStatement>)
                        (RCMRMT030101UKObservationStatement observation) -> observation.setValue(new CV()))),
                Arguments.of(Named.of("Subject Value", (Consumer<RCMRMT030101UKObservationStatement>)
                        (RCMRMT030101UKObservationStatement observation) -> observation.setSubject(new RCMRMT030101UKSubject()))),
                Arguments.of(Named.of("Specimen Value", (Consumer<RCMRMT030101UKObservationStatement>)
                        (RCMRMT030101UKObservationStatement observation) -> observation.getSpecimen()
                                .add(new RCMRMT030101UKSpecimen()))),
                Arguments.of(Named.of("Reference Range Value", (Consumer<RCMRMT030101UKObservationStatement>)
                        (RCMRMT030101UKObservationStatement observation) -> observation.getReferenceRange()
                                .add(new RCMRMT030101UKReferenceRange()))),
                Arguments.of(Named.of("Informant Value", (Consumer<RCMRMT030101UKObservationStatement>)
                        (RCMRMT030101UKObservationStatement observation) -> observation.getInformant()
                                .add(new RCMRMT030101UKInformant()))),
                Arguments.of(Named.of("Participant Value", (Consumer<RCMRMT030101UKObservationStatement>)
                        (RCMRMT030101UKObservationStatement observation) -> observation.getParticipant()
                                .add(new RCMRMT030101UKParticipant()))),
                Arguments.of(Named.of("ReplacementOf Value ", (Consumer<RCMRMT030101UKObservationStatement>)
                        (RCMRMT030101UKObservationStatement observation) -> observation.getReplacementOf()
                                .add(new RCMRMT030101UKReplacementOf()))),
                Arguments.of(Named.of("Reason Value ", (Consumer<RCMRMT030101UKObservationStatement>)
                        (RCMRMT030101UKObservationStatement observation) -> observation.getReason()
                                .add(new RCMRMT030101UKReason()))),
                Arguments.of(Named.of("Reference Value ", (Consumer<RCMRMT030101UKObservationStatement>)
                        (RCMRMT030101UKObservationStatement observation) -> observation.getReference()
                                .add(new RCMRMT030101UKReference()))),
                Arguments.of(Named.of("SequelTo Value ", (Consumer<RCMRMT030101UKObservationStatement>)
                        (RCMRMT030101UKObservationStatement observation) -> observation.getSequelTo()
                                .add(new RCMRMT030101UKSequelTo())))
        );
    }

}