package uk.nhs.adaptors.pss.translator.util;

import static uk.nhs.adaptors.pss.translator.util.BloodPressureValidatorUtil.isBloodPressureWithBatteryAndBloodPressureTriple;
import static uk.nhs.adaptors.pss.translator.util.ResourceFilterUtil.isAllergyIntolerance;

import java.util.Objects;
import java.util.stream.Stream;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hl7.v3.RCMRMT030101UKCompoundStatement;
import org.hl7.v3.RCMRMT030101UKLinkSet;
import org.hl7.v3.RCMRMT030101UKMedicationStatement;
import org.hl7.v3.RCMRMT030101UKNarrativeStatement;
import org.hl7.v3.RCMRMT030101UKObservationStatement;
import org.hl7.v3.RCMRMT030101UKPlanStatement;
import org.hl7.v3.RCMRMT030101UKRequestStatement;
import org.hl7.v3.RCMRMT030101UKComponent02;
import org.hl7.v3.RCMRMT030101UKComponent4;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CompoundStatementResourceExtractors {

    public static Stream<RCMRMT030101UKCompoundStatement> extractAllCompoundStatements(RCMRMT030101UKComponent4 component4) {

        return Stream.concat(
            Stream.of(component4.getCompoundStatement()),
            component4.hasCompoundStatement()
                ? CompoundStatementUtil.extractResourcesFromCompound(component4.getCompoundStatement(),
                    RCMRMT030101UKComponent02::hasCompoundStatement, RCMRMT030101UKComponent02::getCompoundStatement)
                .stream()
                .map(RCMRMT030101UKCompoundStatement.class::cast)
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
                    .map(RCMRMT030101UKCompoundStatement.class::cast)
                : Stream.empty()
        );
    }

    public static Stream<RCMRMT030101UKLinkSet> extractAllLinkSets(RCMRMT030101UKComponent4 component4) {

        return Stream.concat(
            Stream.of(component4.getLinkSet()),
            component4.hasCompoundStatement()
                ? CompoundStatementUtil.extractResourcesFromCompound(component4.getCompoundStatement(),
                    RCMRMT030101UKComponent02::hasLinkSet, RCMRMT030101UKComponent02::getLinkSet)
                .stream()
                .map(RCMRMT030101UKLinkSet.class::cast)
                : Stream.empty()
        );
    }

    public static Stream<RCMRMT030101UKObservationStatement> extractAllObservationStatements(RCMRMT030101UKComponent4 component4) {

        return Stream.concat(
            Stream.of(component4.getObservationStatement()),
            component4.hasCompoundStatement() ? CompoundStatementUtil.extractResourcesFromCompound(component4.getCompoundStatement(),
                    RCMRMT030101UKComponent02::hasObservationStatement, RCMRMT030101UKComponent02::getObservationStatement)
                .stream()
                .map(RCMRMT030101UKObservationStatement.class::cast)
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

    public static Stream<RCMRMT030101UKObservationStatement> extractAllObservationStatementsWithoutAllergiesAndBloodPressures(
                                                                     RCMRMT030101UKComponent4 component4) {

        return Stream.concat(
            Stream.of(component4.getObservationStatement()),
            component4.hasCompoundStatement()
                ? CompoundStatementUtil.extractResourcesFromCompound(
                        component4.getCompoundStatement(),
                        RCMRMT030101UKComponent02::hasObservationStatement,
                        RCMRMT030101UKComponent02::getObservationStatement,
                        compoundStatement -> CompoundStatementResourceExtractors.isNotAllergy(compoundStatement)
                                             &&  !isBloodPressureWithBatteryAndBloodPressureTriple(compoundStatement)
                    ).stream()
                    .map(RCMRMT030101UKObservationStatement.class::cast)
                : Stream.empty()
        );
    }

    public static Stream<RCMRMT030101UKPlanStatement> extractAllPlanStatements(RCMRMT030101UKComponent4 component4) {

        return Stream.concat(
            Stream.of(component4.getPlanStatement()),
            component4.hasCompoundStatement()
                ? CompoundStatementUtil.extractResourcesFromCompound(component4.getCompoundStatement(),
                    RCMRMT030101UKComponent02::hasPlanStatement, RCMRMT030101UKComponent02::getPlanStatement)
                .stream()
                .map(RCMRMT030101UKPlanStatement.class::cast)
                : Stream.empty()
        );
    }

    public static Stream<RCMRMT030101UKRequestStatement> extractAllRequestStatements(RCMRMT030101UKComponent4 component4) {

        return Stream.concat(
            Stream.of(component4.getRequestStatement()),
            component4.hasCompoundStatement()
                ? CompoundStatementUtil.extractResourcesFromCompound(component4.getCompoundStatement(),
                    RCMRMT030101UKComponent02::hasRequestStatement, RCMRMT030101UKComponent02::getRequestStatement)
                .stream()
                .map(RCMRMT030101UKRequestStatement.class::cast)
                : Stream.empty()
        );
    }

    public static Stream<RCMRMT030101UKNarrativeStatement> extractAllNonBloodPressureNarrativeStatements(
            RCMRMT030101UKComponent4 component4) {

        /*
            As blood pressures already map their own NarrativeStatements, we do not want to map these again if the
            provided component contains a blood pressure triple.
            See PR #367 AKA NIAD-2843 for details.
         */
        Stream<RCMRMT030101UKNarrativeStatement> childNarrativeStatements =
                hasCompoundStatementAndIsNotBloodPressure(component4)
                        ? CompoundStatementUtil.extractResourcesFromCompound(
                                component4.getCompoundStatement(),
                                RCMRMT030101UKComponent02::hasNarrativeStatement,
                                RCMRMT030101UKComponent02::getNarrativeStatement)
                            .stream()
                            .map(RCMRMT030101UKNarrativeStatement.class::cast)
                        : Stream.empty();

        return Stream.concat(Stream.of(component4.getNarrativeStatement()), childNarrativeStatements);
    }

    public static Stream<RCMRMT030101UKMedicationStatement> extractAllMedications(RCMRMT030101UKComponent4 component4) {

        return Stream.concat(
            Stream.of(component4.getMedicationStatement()),
            component4.hasCompoundStatement()
                ? CompoundStatementUtil.extractResourcesFromCompound(component4.getCompoundStatement(),
                    RCMRMT030101UKComponent02::hasMedicationStatement, RCMRMT030101UKComponent02::getMedicationStatement)
                .stream()
                .map(RCMRMT030101UKMedicationStatement.class::cast)
                : Stream.empty()
        );
    }

    private static boolean isNotAllergy(RCMRMT030101UKCompoundStatement compoundStatement) {
        if (compoundStatement.hasCode() && compoundStatement.getCode().hasCodeSystem()) {
            return !isAllergyIntolerance(compoundStatement);
        }
        return true;
    }

    private static boolean hasCompoundStatementAndIsNotBloodPressure(RCMRMT030101UKComponent4 component4) {
        return component4.hasCompoundStatement()
                && !isBloodPressureWithBatteryAndBloodPressureTriple(component4.getCompoundStatement());
    }
}
