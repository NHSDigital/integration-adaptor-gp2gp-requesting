package uk.nhs.adaptors.pss.translator.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Slf4j
@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "S3Client is immutable and thread-safe.")
public class AWSStorageService implements StorageService {

    private static final long SIXTY_MINUTES = 60;
    private final S3Client s3Client;
    private final String bucketName;
    private final S3Presigner s3Presigner;

    public AWSStorageService(final S3Client s3Client,
                             StorageServiceConfiguration configuration,
                             final S3Presigner s3Presigner) {

        if (accessKeyProvided(configuration)) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(configuration.getAccountReference(),
                                                                         configuration.getAccountSecret());
            StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);

            this.s3Client = S3Client.builder()
                                    .region(Region.of(configuration.getRegion()))
                                    .credentialsProvider(credentialsProvider)
                                    .build();

            this.s3Presigner = S3Presigner.builder()
                                          .region(Region.of(configuration.getRegion()))
                                          .credentialsProvider(credentialsProvider)
                                          .build();
        } else {
            this.s3Client = s3Client;
            this.s3Presigner = s3Presigner;
        }

        this.bucketName = configuration.getContainerName();
    }

    public void uploadFile(String filename, byte[] fileAsString) throws StorageException {

        try {
            final var putObjectRequest = PutObjectRequest.builder().bucket(bucketName).key(filename).build();
            InputStream is = new ByteArrayInputStream(fileAsString);
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(is, fileAsString.length));
        } catch (Exception e) {
            throw new StorageException("Error occurred uploading to S3 Bucket", e);
        }
    }

    public byte[] downloadFile(String filename) throws StorageException {
        try {
            var stream = downloadFileToStream(filename);
            return IOUtils.toByteArray(stream);
        } catch (IOException e) {
            throw new StorageException("Error occurred downloading from S3 Bucket", e);
        }
    }

    public void deleteFile(String filename) {

        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                                                               .bucket(bucketName)
                                                               .key(filename)
                                                               .build();
        s3Client.deleteObject(deleteRequest);
        LOGGER.info("{} was successfully deleted", filename);
    }

    public String getFileLocation(String filename) {

        Duration expiration = Duration.ofMinutes(SIXTY_MINUTES);

        GetObjectRequest getObjectRequest = getGetObjectRequest(filename);
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                                                                        .getObjectRequest(getObjectRequest)
                                                                        .signatureDuration(expiration)
                                                                        .build();

        URL presignedUrl = s3Presigner.presignGetObject(presignRequest).url();
        return presignedUrl.toString();
    }

    private ResponseInputStream<GetObjectResponse> downloadFileToStream(String filename) throws StorageException {
        try {
            final var request = getGetObjectRequest(filename);
            return s3Client.getObject(request);
        } catch (Exception exception) {
            throw new StorageException("Error occurred downloading from S3 Bucket", exception);
        }
    }

    private GetObjectRequest getGetObjectRequest(String filename) {
        return GetObjectRequest.builder().bucket(bucketName).key(filename).build();
    }

    private boolean accessKeyProvided(StorageServiceConfiguration configuration) {

        if (configuration.getAccountSecret() == null || configuration.getAccountSecret().isBlank()) {
            return false;
        }
        return configuration.getAccountReference() != null && !configuration.getAccountReference().isBlank();
    }
}