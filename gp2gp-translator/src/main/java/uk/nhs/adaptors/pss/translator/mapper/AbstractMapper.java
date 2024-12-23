package uk.nhs.adaptors.pss.translator.mapper;

import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.lang3.function.TriFunction;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.v3.RCMRMT030101UKComponent;
import org.hl7.v3.RCMRMT030101UKEhrComposition;
import org.hl7.v3.RCMRMT030101UKEhrExtract;
import org.hl7.v3.RCMRMT030101UKEhrFolder;
import org.hl7.v3.RCMRMT030101UKComponent4;
import org.hl7.v3.RCMRMT030101UKComponent3;

public abstract class AbstractMapper<T> {

    public Stream<T> mapEhrExtractToFhirResource(RCMRMT030101UKEhrExtract ehrExtract,
        TriFunction<RCMRMT030101UKEhrExtract, RCMRMT030101UKEhrComposition, RCMRMT030101UKComponent4,
                    Stream<T>> extractResourceFunction) {

        return ehrExtract.getComponent()
            .stream()
            .map(RCMRMT030101UKComponent::getEhrFolder)
            .map(RCMRMT030101UKEhrFolder::getComponent)
            .flatMap(List::stream)
            .map(RCMRMT030101UKComponent3::getEhrComposition)
            .flatMap(ehrComposition -> ehrComposition.getComponent()
                .stream()
                .flatMap(component4 -> extractResourceFunction.apply(ehrExtract, ehrComposition, component4)));
    }

    public abstract List<T> mapResources(RCMRMT030101UKEhrExtract ehrExtract, Patient patient,
        List<Encounter> encounters, String practiceCode);
}
