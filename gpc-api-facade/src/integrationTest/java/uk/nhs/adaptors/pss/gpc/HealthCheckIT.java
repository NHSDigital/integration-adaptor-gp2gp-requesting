package uk.nhs.adaptors.pss.gpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = GpcFacadeApplication.class)
@AutoConfigureMockMvc
public class HealthCheckIT {

    private static final String HEALTHCHECK_ENDPOINT = "/healthcheck";

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void When_GettingHealthCheck_Expect_ServiceIsUp() throws Exception {
        var response = mockMvc.perform(get(HEALTHCHECK_ENDPOINT))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertThat(response).contains("UP");
    }
}
