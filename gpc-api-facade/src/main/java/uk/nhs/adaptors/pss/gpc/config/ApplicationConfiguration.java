package uk.nhs.adaptors.pss.gpc.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.hl7.fhir.dstu3.model.Parameters;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.nhs.adaptors.common.config.CommonConfiguration;
import uk.nhs.adaptors.common.util.fhir.FhirParser;
import uk.nhs.adaptors.connector.config.DbConnectorConfiguration;
import uk.nhs.adaptors.pss.gpc.config.serialization.ParametersDeserializer;


@Configuration
@Import({DbConnectorConfiguration.class, CommonConfiguration.class})
public class ApplicationConfiguration implements BeanPostProcessor {

    @Autowired
    private FhirParser fhirParser;

    @Bean
    public MappingJackson2HttpMessageConverter jacksonConverter(ObjectMapper objectMapper) {
        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);

        return jacksonConverter;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof ObjectMapper objectMapper) {
            SimpleModule module = new SimpleModule();
            module.addDeserializer(Parameters.class, new ParametersDeserializer(fhirParser));
            objectMapper.registerModule(module);
        }
        return bean;
    }
}
