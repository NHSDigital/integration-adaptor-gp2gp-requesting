# Developer Information

This guide is for contributors developing, testing, and releasing the GP2GP FHIR Request Adaptor.
Use it alongside the project overview in [README.md](README.md), the operating guide in [OPERATING.md](OPERATING.md),
and the platform-specific setup guides.

## Start here

- [Project overview and API guidance](README.md)
- [Set up the GP2GP adaptors in INT](getting-started-instructions.md)
- [Set up on Windows](getting-started-with-windows.md)
- [Operate the adaptor](OPERATING.md)

## Requirements

- JDK 21 - the adaptor is developed in Java with Spring Boot
- Docker
- A bash-compatible shell for the project scripts. On Windows, use WSL as described in [getting-started-with-windows.md](./getting-started-with-windows.md)
- A current SNOMED CT UK Edition monolith zip file.
  See [First installation](./OPERATING.md#first-installation) for instructions on how to download one.
- Windows users should complete the [prerequisite setup steps](./getting-started-with-windows.md)

## Project structure

    .
    ├── common                      # Shared code used by gp2gp-translator, gpc-api-facade and db-connector
    ├── db-connector                # Database migrations and shared DB-related classes
    ├── docker                      # Local Docker environment, compose files and helper scripts
    ├── gp2gp-translator            # GP2GP Translator service
    ├── gpc-api-facade              # GPC API Facade service
    ├── schema                      # Shared schema module
    ├── smoke-tests                 # Smoke-test project
    ├── snomed-database-loader      # Scripts for loading SNOMED CT data into PostgreSQL
    └── test-suite                  # Postman, mock MHS service and local end-to-end test environment

## Local development

### Start the local environment

1. Go to the `docker` directory.
2. Create a copy of `example.vars.sh` named `vars.sh`.
3. Fill in the `SNOMED_CT_TERMINOLOGY_FILE` variable inside `docker/vars.sh` with the path to your SNOMED ZIP file.
   For the description and purpose of other environment variables, refer to the [operating guide](OPERATING.md#environment-variables).
4. Run the `start-local-environment.sh` script:
   ```shell script
    ./start-local-environment.sh
   ```
   It will execute the following steps, and can take up to 30 minutes:
    - create a default PostgreSQL database and `patient_switching` database,
    - start the MHS Adaptor mock,
    - start ActiveMQ,
    - run migrations,
    - populate SNOMED data into PostgreSQL,
    - build and start the GPC API Facade service,
    - build and start the GP2GP Translator application.
      All components run in Docker.

5. To run the integration tests, stop the translator and facade containers started by step 4, otherwise they will consume the AMQP messages first.
   Hit Ctrl-C in the terminal where you ran `./start-local-environment.sh`.
   That stops the foreground `gpc_facade` and `gp2gp_translator` containers while leaving ActiveMQ, PostgreSQL and the MHS adaptor mock running in the background.

   From the repository root, run the checks you need:

   - `./gradlew gp2gp-translator:check`
   - `./gradlew gpc-api-facade:check`
   - `./gradlew common:check`
   - `./gradlew db-connector:check`

6. To get the adaptor to translate a GP2GP XML file to a GP Connect JSON file, place the XML file you wish to be
   translated inside `gp2gp-translator/src/transformXmlToJson/resources/input/` and then run the
   `transformXmlToJson` gradle task. The task will log out details of what it has transformed.

   - `cd gp2gp-translator && ./gradlew transformXmlToJson`

## Releasing a new version to Docker Hub

First identify the most recent commit within GitHub that contains only changes marked as Done within Jira.
You can also review which commits have gone in by using the git log command or your IDE.

### Perform a smoke test of the release

Deploy this commit to the AWS Path to Live environment.

1. Click through to the successful GitHub Actions build of your commit.
1. Navigate to the "Build / Generate Build Id" section of the pipeline, looking for an entry which looks like
   ```
   Run chmod +x ./create_build_id.sh
   Generated the build tag: PR-001-000a0a1
   ```
1. Make a note of the `<TAG_NAME>` so it can be deployed in the step below.
1. Navigate to the [Terraform project](https://github.com/NHSDigital/integration-adaptors-deployment) and specify project=`nia`, Environment=`ptl`,
   component=`pss`, action=`apply`, variables=`pss_build_id=<TAG_NAME>` and click the Build button waiting
   for the build to finish successfully


Perform an end-to-end smoke test of the adaptor by transferring the patient `9732596910` from `C88046` to `P83007` using the
[instructions on Confluence][e2e-ptl-test-instructions] by setting the `to-ods: C88046` and `to-asid: 858000001001`.
This patient record has:

1. An allergy to penicillin
1. A picture of some marbles as a document

Request the patient using the adaptor and check that the allergy is mapped into the Bundle,
and that the document has been transferred to S3 and the image is downloadable via a browser.

If you get a `404` response with an `OperationOutcome` coding of `PATIENT_NOT_FOUND` you may need to [regenerate the patient]
and then request that patient instead.

Reject the transfer by sending a FAILED_TO_INTEGRATE response, that way we can reuse the same patient.

[regenerate the patient]: https://nhse-dsic.atlassian.net/wiki/spaces/NIA/pages/12540018795/Testing+an+NME+winning+scenario+PS+Adaptor#Recreating-the-Smoke-Test-Patient
[e2e-ptl-test-instructions]: https://gpitbjss.atlassian.net/wiki/spaces/NIA/pages/12540018795/Testing+an+NME+winning+scenario+PS+Adaptor

### Performing the release

Make a note of the most recent Release within GitHub, and identify what the next version number to use will be.

Create a new release within GitHub, specifying the tag as the version to use (e.g. 1.2.7), and the target being the commit you identified.
Click on the "Generate release notes" button and this will list all the current changes from the recent commit.
Click "Publish Release" which will trigger a GitHub Actions job called "Push Docker Image", which will build and
push images to DockerHub.

Update the `CHANGELOG.md` file, moving the UNRELEASED entries into a line for the new release.
Raise a PR for your changes.

## Rebuilding services

From the `docker` directory, rebuild the GPC API Facade with:
```shell script
 ./rebuild-and-restart-gpc-facade.sh
```

From the `docker` directory, rebuild the GP2GP Translator with:
```shell script
 ./rebuild-and-restart-gp2gp-translator.sh
```

From the `docker` directory, remove the local Docker containers, images and dangling volumes with:
```shell script
 ./clear-docker.sh
```
