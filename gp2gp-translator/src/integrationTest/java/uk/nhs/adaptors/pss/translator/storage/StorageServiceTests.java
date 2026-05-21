package uk.nhs.adaptors.pss.translator.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.io.IOException;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import uk.nhs.adaptors.pss.translator.Gp2gpTranslatorApplication;

@SpringBootTest(webEnvironment = RANDOM_PORT, classes = Gp2gpTranslatorApplication.class)
@ExtendWith({SpringExtension.class})
@DirtiesContext
public class StorageServiceTests {

    @Autowired
    private StorageService storageService;

    @Test
    public void When_FileIsUploadedToStorage_Expect_SameFileIsDownloaded() throws IOException {
        var filename = UUID.randomUUID().toString() + ".txt";
        try (var fileUploadStream = StorageServiceTests.class.getResourceAsStream("/test.txt");
            var expectedFileStream = StorageServiceTests.class.getResourceAsStream("/test.txt")) {
            var expectedFileBytes = expectedFileStream.readAllBytes();

            storageService.uploadFile(filename, fileUploadStream.readAllBytes());

            byte[] fileDownloadAsByteArray = storageService.downloadFile(filename);
            assertThat(fileDownloadAsByteArray).isEqualTo(expectedFileBytes);
        }
    }
}
