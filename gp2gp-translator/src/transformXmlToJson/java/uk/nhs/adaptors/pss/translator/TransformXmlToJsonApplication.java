package uk.nhs.adaptors.pss.translator;

import jakarta.xml.bind.JAXBException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.v3.RCMRIN030000UKMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import uk.nhs.adaptors.common.util.fhir.FhirParser;
import uk.nhs.adaptors.pss.translator.exception.BundleMappingException;
import uk.nhs.adaptors.pss.translator.service.BundleMapperService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallString;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Slf4j
@SpringBootApplication
public class TransformXmlToJsonApplication implements CommandLineRunner {
    public static final String RESOURCES_FOLDER = "src/transformXmlToJson/resources/";

    private final FhirParser fhirParser;
    private final BundleMapperService bundleMapperService;

    public static void main(String[] args) {
        SpringApplication.run(TransformXmlToJsonApplication.class, args).close();
    }

    @Override
    public void run(String... args) {
        Arrays.stream(new File(RESOURCES_FOLDER + "input").listFiles())
            .map(File::toPath)
            .filter(it ->  it.toString().endsWith("xml"))
            .forEach(this::convertXmlToJsonAndSaveToOutputFolder);
    }

    private void convertXmlToJsonAndSaveToOutputFolder(Path input) {
        try {
            RCMRIN030000UKMessage rcmrin030000UKMessage = unmarshallString(Files.readString(input), RCMRIN030000UKMessage.class);
            String gpConnectOutput = fhirParser.encodeToJson(mapToBundle(rcmrin030000UKMessage));
            Path outputPath = outputPath(input);
            LOGGER.debug("Writing [{}] characters to [{}]", gpConnectOutput.length(), outputPath);
            Files.writeString(outputPath, gpConnectOutput);
        } catch (JAXBException | IOException | BundleMappingException e) {
            LOGGER.debug("Unable to convert [{}]", input, e);
        }
    }

    private Bundle mapToBundle(RCMRIN030000UKMessage rcmrin030000UKMessage) throws BundleMappingException {
        return bundleMapperService.mapToBundle(rcmrin030000UKMessage, "AB03", List.of());
    }

    private static Path outputPath(Path input) throws IOException {
        Path fileName = input.getFileName();
        if (fileName == null) {
            throw new IOException("Input path missing fileName - " + input);
        }
        final var outputFilename = fileName.toString().replace(".xml", ".json");
        return Paths.get(RESOURCES_FOLDER + "output/", outputFilename);
    }
}
