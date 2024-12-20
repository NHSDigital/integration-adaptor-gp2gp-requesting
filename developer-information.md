# Developer Information

## Requirements:

* JDK 21 - We develop the adaptor in Java with Spring Boot
* Docker
* Version 38.2 of the SNOMED CT UK Edition monolith zip file.
  See [First Installation](./OPERATING.md#populating-the-snomed-database) for instructions on how to download.
* Windows users have followed the [prerequisite setup steps](./getting-started-with-windows.md)

## Project structure

    .
    ├── db                          # Dockerfile and scripts for local database setup
    ├── snomed-database-loader      # Scripts loading Snomed CT codes into database
    ├── common                      # Common module used by gp2gp-translator, gpc-api-facade and db-connector
    ├── db-connector                # Common module used by gp2gp-translator and gpc-api-facade, used for db-related classes
    ├── gp2gp-translator            # GP2GP Translator
    ├── gpc-api-facade              # GPC API Facade
    └── mhs-adaptor-mock            # Dockerfile and required files for mock of MHS Adaptor

## Local development
### How to start local environment
1. Go to `docker` directory
2. Create a copy of `example.vars.sh`, name it `vars.sh`
3. Fill in the `SNOMED_CT_TERMINOLOGY_FILE` variable inside `vars.sh` file with the path to where your SNOMED ZIP file
   is downloaded to. For the description and purpose of other environment variables, refer to the
   [end user OPERATING guidance](OPERATING.md#environment-variables).
3. Run `start-local-environment.sh` script:
   ```shell script
    ./start-local-environment.sh
   ```
   It will execute following steps, and take up to 30 minutes:
    - create a default postgres database and patient_switching database,
    - start MHS Adaptor mock,
    - start ActiveMQ,
    - run migrations,
    - populate SNOMED data into postgres, 
    - build and start GPC Api Facade service,
    - build and start GP2GP Translator application.
      All components will run in Docker.

4. To run the integration tests you will need to stop the translator and facade containers running in Docker from step 3
   as otherwise they will steal the messages off of AMQP.
   To stop the translator and facade, hit Ctrl-C in the terminal where you ran `./start-local-environment.sh`.
   You will want ActiveMQ, Postgres and MHS Adaptor Mock to continue running in the background. 

   - For the translator: `cd ../gp2gp-translator/ && ./gradlew check`
   - For the facade: `cd ../gpc-api-facade/ && ./gradlew check`
   - For common code: `cd ../ && ./gradlew common:check`
   - For DB connector code: `cd ../ && ./gradlew db-connector:check`

5. To get the Adaptor to translate a GP2GP XML file to a GP Connect JSON file, place the XML file you wish to be
   translated inside the folder `/gp2gp-translator/src/transformXmlToJson/resources/input/` and then run the
   `transformXmlToJson` gradle task. The task will log out details of what it has transformed.

   - `cd gp2gp-translator && ./gradlew transformXmlToJson`

## Releasing a new version to Docker Hub

First identify which is the most recent commit within GitHub which contains only changes which are marked as Done within Jira.
You can also review what commits have gone in by using the git log command or IDE.

### Perform a smoke test of the release

Deploy this commit to the AWS Path to Live environment.

1. Clicking through to the successful Jenkins build of your commit
1. Navigate to the "Push Image" section of the pipeline, looking for an entry which looks like
   ```
   docker push ...amazonaws.com/pss_gpc_facade:<TAG_NAME>
   ```
1. Make a note of the <TAG_NAME> so it can be deployed in the step below.
1. Log into the [Jenkins Terraform project][jenkins-terraform] and specify project=`nia`, Environment=`ptl`,
   component=`pss`, action=`apply`, variables=`pss_build_id=<TAG_NAME>` and click the Build button waiting
   for the build to finish successfully

[jenkins-terraform]: http://ec2-35-177-12-25.eu-west-2.compute.amazonaws.com/job/Terraform/build?delay=0sec

Perform an end to end smoke test of the adaptor by transferring the patient 9732596910 from C88046 to P83007 using the
[instructions on Confluence][e2e-ptl-test-instructions] by setting the `to-ods: C88046` and `to-asid: 858000001001`.
This patient record has:

1. An allergy to penicillin
1. A picture of some marbles as a document

Request the patient using the adaptor and check that the allergy is mapped into the Bundle,
and that the document has been transferred to S3 and the image is downloadable via a browser.

If you get a 404 response with an `OperationOutcome` coding of "PATIENT_NOT_FOUND" you may need to [regenerate the patient]
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
To rebuild the GPC Api Facade run
```shell script
 ./rebuild-and-restart-gpc-facade.sh
```

To rebuild the GP2GP Translator run
```shell script
 ./rebuild-and-restart-gp2gp-translator.sh
```

To clean all containers run
```shell script
 ./clear-docker.sh
```
