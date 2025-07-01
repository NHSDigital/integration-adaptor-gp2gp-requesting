package uk.nhs.adaptors.pss.translator;

import jakarta.xml.bind.JAXBException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.FilenameUtils;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.v3.RCMRIN030000UKMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import uk.nhs.adaptors.common.util.fhir.FhirParser;
import uk.nhs.adaptors.pss.translator.exception.BundleMappingException;
import uk.nhs.adaptors.pss.translator.service.BundleMapperService;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallString;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Slf4j
@SpringBootApplication
public class TransformXmlToJsonApplication implements CommandLineRunner {
    public static final String XML_INPUT_PATH =
        Paths.get("gp2gp-translator/src/").toFile().getAbsoluteFile().getAbsolutePath() + "/transformXmlToJson/resources/input/";

    public static final String JSON_OUTPUT_PATH =
        Paths.get("gp2gp-translator/src/").toFile().getAbsoluteFile().getAbsolutePath() + "/transformXmlToJson/resources/output/";

    private final FhirParser fhirParser;
    private final BundleMapperService bundleMapperService;

    public static void main(String[] args) {
        SpringApplication.run(TransformXmlToJsonApplication.class, args).close();
    }

    @Override
    public void run(String... args) {
        getInputFilePaths().forEach(this::convertXmlToJsonAndSaveToOutputFolder);
    }

    private List<Path> getInputFilePaths() {
        var path = Paths.get(XML_INPUT_PATH);

        if (!Files.isDirectory(path) || !Files.isReadable(path)) {
            throw new NoXmlFilesFound("Input folder is missing or unreadable: " +  XML_INPUT_PATH);
        }


        File[] files = path.toFile()
            .listFiles((dir, name) -> name.toLowerCase().endsWith(".xml"));

        if (files == null || files.length == 0) {
            throw new NoXmlFilesFound("No XML files found in " + XML_INPUT_PATH);
        }

        return Arrays.stream(files)
            .map(File::toPath)
            .collect(Collectors.toList());
    }

    private void convertXmlToJsonAndSaveToOutputFolder(Path input) {
        try {
            RCMRIN030000UKMessage rcmrin030000UKMessage = unmarshallString(Files.readString(input), RCMRIN030000UKMessage.class);
            var mappedBundle = mapToBundle(rcmrin030000UKMessage);
            String bundleJson = fhirParser.encodeToJson(mappedBundle);
            writeToFile(bundleJson, input.getFileName().toString());
        } catch (JAXBException | IOException | BundleMappingException e) {
            LOGGER.debug("Unable to convert [{}]", input, e);
        }
    }

    private void writeToFile(String bundleJson, String sourceFileName) {
        var outputFileName = FilenameUtils.removeExtension(sourceFileName);

        var path = Paths.get(JSON_OUTPUT_PATH);

        if (!Files.isDirectory(path) || !Files.isReadable(path)) {
            throw new NoXmlFilesFound("Output folder is missing or unreadable: " +  JSON_OUTPUT_PATH);
        }

        var outputFilePath = JSON_OUTPUT_PATH + outputFileName + ".json";
        try (
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath, StandardCharsets.UTF_8))
        ) {
            LOGGER.debug("Writing [{}] characters to [{}]", bundleJson.length(), outputFilePath);
            writer.write(bundleJson);
            LOGGER.info("Contents of file: {}. Saved to: {}.json", sourceFileName, outputFileName);
        } catch (IOException e) {
            LOGGER.error("Could not send Xml result to the file", e);
        }
    }

    private Bundle mapToBundle(RCMRIN030000UKMessage rcmrin030000UKMessage) throws BundleMappingException {
        return bundleMapperService.mapToBundle(rcmrin030000UKMessage, "AB03", List.of());
    }

    public static class NoXmlFilesFound extends RuntimeException {
        public NoXmlFilesFound(String errorMessage) {
            super(errorMessage);
        }
    }
}
