FROM gradle:8.8-jdk21 AS build
COPY --chown=gradle:gradle settings.gradle /home/gradle/service/
COPY --chown=gradle:gradle ./gp2gp-translator /home/gradle/service/gp2gp-translator
COPY --chown=gradle:gradle ./common /home/gradle/service/common
COPY --chown=gradle:gradle ./db-connector /home/gradle/service/db-connector
COPY --chown=gradle:gradle ./schema /home/gradle/service/schema
COPY --chown=gradle:gradle ./config /home/gradle/service/config

WORKDIR /home/gradle/service/gp2gp-translator
RUN gradle --build-cache bootJar

FROM eclipse-temurin:21-jre-jammy

EXPOSE 8085

RUN mkdir /app

COPY --from=build /home/gradle/service/gp2gp-translator/build/libs/gp2gp-translator.jar /app/gp2gp-translator.jar

ENTRYPOINT ["java", "-cp", "/app/gp2gp-translator.jar", "-Dloader.main=uk.nhs.adaptors.pss.translator.Gp2gpTranslatorApplication", "org.springframework.boot.loader.launch.PropertiesLauncher"]
