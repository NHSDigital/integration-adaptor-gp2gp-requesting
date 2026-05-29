package uk.nhs.adaptors.pss.gpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest(classes = GpcFacadeApplication.class, webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
public class HealthCheckIT {

    private static final String HEALTHCHECK_ENDPOINT = "/healthcheck";

    @LocalServerPort
    private int port;

    @Test
    public void When_GettingHealthCheck_Expect_ServiceIsUp() {
        var response = WebClient.builder()
                .baseUrl("http://localhost:" + port)
                .build()
                .get()
                .uri(builder -> builder.path(HEALTHCHECK_ENDPOINT).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        assertThat(response).contains("UP");
    }
}
