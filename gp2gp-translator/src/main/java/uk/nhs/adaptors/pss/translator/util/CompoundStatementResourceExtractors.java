package uk.nhs.adaptors.pss.translator.util;

import static uk.nhs.adaptors.pss.translator.util.BloodPressureValidatorUtil.isBloodPressureWithBatteryAndBloodPressureTriple;
import static uk.nhs.adaptors.pss.translator.util.ResourceFilterUtil.isAllergyIntolerance;

import java.util.Objects;
import java.util.stream.Stream;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hl7.v3.RCMRMT030101UK04Component4;
import org.hl7.v3.RCMRMT030101UK04CompoundStatement;
import org.hl7.v3.RCMRMT030101UK04LinkSet;
import org.hl7.v3.RCMRMT030101UK04MedicationStatement;
import org.hl7.v3.RCMRMT030101UK04NarrativeStatement;
import org.hl7.v3.RCMRMT030101UK04ObservationStatement;
import org.hl7.v3.RCMRMT030101UK04PlanStatement;
import org.hl7.v3.RCMRMT030101UK04RequestStatement;
import org.hl7.v3.RCMRMT030101UKComponent02;
import org.hl7.v3.RCMRMT030101UKCompoundStatement;
import org.hl7.v3.RCMRMT030101UKObservationStatement;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CompoundStatementResourceExtractors {

    public static Stream<RCMRMT030101UK04CompoundStatement> extractAllCompoundStatements(RCMRMT030101UK04Component4 component4) {
        return Stream.concat(
            Stream.of(component4.getCompoundStatement()),
            component4.hasCompoundStatement()
                ? CompoundStatementUtil.extractResourcesFromCompound(component4.getCompoundStatement(),
                    RCMRMT030101UKComponent02::hasCompoundStatement, RCMRMT030101UKComponent02::getCompoundStatement)
                .stream()
                .map(RCMRMT030101UK04CompoundStatement.class::cast)
                : Stream.empty()
        );
    }

    public static Stream<RCMRMT030101UKCompoundStatement> extractAllChildCompoundStatements(RCMRMT030101UKComponent02 component02) {
        return Stream.concat(
            Stream.of(component02.getCompoundStatement()),
            component02.hasCompoundStatement()
                ? CompoundStatementUtil.extractCompoundsFromCompound(component02.getCompoundStatement())
                    .stream()
                    .filter(Objects::nonNull)
                    .map(RCMRMT030101UK04CompoundStatement.class::cast)
                : Stream.empty()
        );
    }

    public static Stream<RCMRMT030101UK04LinkSet> extractAllLinkSets(RCMRMT030101UK04Component4 component4) {
        return Stream.concat(
            Stream.of(component4.getLinkSet()),
            component4.hasCompoundStatement()
                ? CompoundStatementUtil.extractResourcesFromCompound(component4.getCompoundStatement(),
                    RCMRMT030101UKComponent02::hasLinkSet, RCMRMT030101UKComponent02::getLinkSet)
                .stream()
                .map(RCMRMT030101UK04LinkSet.class::cast)
                : Stream.empty()
        );
    }

    public static Stream<RCMRMT030101UK04ObservationStatement> extractAllObservationStatements(RCMRMT030101UK04Component4 component4) {
        return Stream.concat(
            Stream.of(component4.getObservationStatement()),
            component4.hasCompoundStatement() ? CompoundStatementUtil.extractResourcesFromCompound(component4.getCompoundStatement(),
                    RCMRMT030101UKComponent02::hasObservationStatement, RCMRMT030101UKComponent02::getObservationStatement)
                .stream()
                .map(RCMRMT030101UK04ObservationStatement.class::cast)
                : Stream.empty()
        );
    }

    public static Stream<RCMRMT030101UKObservationStatement> extractInnerObservationStatements(RCMRMT030101UKComponent02 component02) {

        return Stream.concat(
            Stream.of(component02.getObservationStatement()),
            component02.hasCompoundStatement()
                ? CompoundStatementUtil.extractResourcesFromCompound(component02.getCompoundStatement(),
                    RCMRMT030101UKComponent02::hasObservationStatement, RCMRMT030101UKComponent02::getObservationStatement)
                    .stream()
                    .map(RCMRMT030101UKObservationStatement.class::cast)
                : Stream.empty()
        );
    }

    public static Stream<RCMRMT030101UK04ObservationStatement> extractAllObservationStatementsWithoutAllergiesAndBloodPressures(
        RCMRMT030101UK04Component4 component4) {
        return Stream.concat(
            Stream.of(component4.getObservationStatement()),
            component4.hasCompoundStatement() && !isBloodPressureWithBatteryAndBloodPressureTriple(component4.getCompoundStatement())
                ? CompoundStatementUtil.extractResourcesFromCompound(
                        component4.getCompoundStatement(),
                        RCMRMT030101UKComponent02::hasObservationStatement,
                        RCMRMT030101UKComponent02::getObservationStatement,
                        CompoundStatementResourceExtractors::isNotAllergy
                    ).stream()
                    .map(RCMRMT030101UK04ObservationStatement.class::cast)
                : Stream.empty()
        );
    }

    public static Stream<RCMRMT030101UK04PlanStatement> extractAllPlanStatements(RCMRMT030101UK04Component4 component4) {
        return Stream.concat(
            Stream.of(component4.getPlanStatement()),
            component4.hasCompoundStatement()
                ? CompoundStatementUtil.extractResourcesFromCompound(component4.getCompoundStatement(),
                    RCMRMT030101UKComponent02::hasPlanStatement, RCMRMT030101UKComponent02::getPlanStatement)
                .stream()
                .map(RCMRMT030101UK04PlanStatement.class::cast)
                : Stream.empty()
        );
    }

    public static Stream<RCMRMT030101UK04RequestStatement> extractAllRequestStatements(RCMRMT030101UK04Component4 component4) {
        return Stream.concat(
            Stream.of(component4.getRequestStatement()),
            component4.hasCompoundStatement()
                ? CompoundStatementUtil.extractResourcesFromCompound(component4.getCompoundStatement(),
                    RCMRMT030101UKComponent02::hasRequestStatement, RCMRMT030101UKComponent02::getRequestStatement)
                .stream()
                .map(RCMRMT030101UK04RequestStatement.class::cast)
                : Stream.empty()
        );
    }

    public static Stream<RCMRMT030101UK04NarrativeStatement> extractAllNonBloodPressureNarrativeStatements(
            RCMRMT030101UK04Component4 component4) {

        /*
            As blood pressures already map their own NarrativeStatements, we do not want to map these again if the
            provided component contains a blood pressure triple.
            See PR #367 AKA NIAD-2843 for details.
         */
        Stream<RCMRMT030101UK04NarrativeStatement> childNarrativeStatements =
                hasCompoundStatementAndIsNotBloodPressure(component4)
                        ? CompoundStatementUtil.extractResourcesFromCompound(
                                component4.getCompoundStatement(),
                                RCMRMT030101UKComponent02::hasNarrativeStatement,
                                RCMRMT030101UKComponent02::getNarrativeStatement)
                            .stream()
                            .map(RCMRMT030101UK04NarrativeStatement.class::cast)
                        : Stream.empty();

        return Stream.concat(Stream.of(component4.getNarrativeStatement()), childNarrativeStatements);
    }

    public static Stream<RCMRMT030101UK04MedicationStatement> extractAllMedications(RCMRMT030101UK04Component4 component4) {
        return Stream.concat(
            Stream.of(component4.getMedicationStatement()),
            component4.hasCompoundStatement()
                ? CompoundStatementUtil.extractResourcesFromCompound(component4.getCompoundStatement(),
                    RCMRMT030101UKComponent02::hasMedicationStatement, RCMRMT030101UKComponent02::getMedicationStatement)
                .stream()
                .map(RCMRMT030101UK04MedicationStatement.class::cast)
                : Stream.empty()
        );
    }

    private static boolean isNotAllergy(RCMRMT030101UKCompoundStatement compoundStatement) {
        if (compoundStatement.hasCode() && compoundStatement.getCode().hasCodeSystem()) {
            return !isAllergyIntolerance(compoundStatement);
        }
        return true;
    }

    private static boolean hasCompoundStatementAndIsNotBloodPressure(RCMRMT030101UK04Component4 component4) {
        return component4.hasCompoundStatement()
                && !isBloodPressureWithBatteryAndBloodPressureTriple(component4.getCompoundStatement());
    }
}
