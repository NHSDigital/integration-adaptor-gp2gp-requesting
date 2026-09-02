package uk.nhs.adaptors.pss.translator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
public class Gp2gpTranslatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(Gp2gpTranslatorApplication.class, args);
    }
}
