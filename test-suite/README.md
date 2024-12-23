# Getting started with Postman tests

## Pre-reqs before running Postman tests
1. Ensure you have built the docker containers by following the build process from the root [README](../README.md)
2. Postman installed
3. Import the scripts into Postman `nia-patient-switching-standard-adaptor\test-suite\postman\Medicus Test Collection PSS Adaptor.postman_collection.json`
4. Certificates have been added to postman
    1. Open Postman / Settings (cog symbol)
    2. Go to Certificates tab inside Settings menu
    3. Turn on CA Certificates and add the `rootCA.pem` file from `nia-patient-switching-standard-adaptor\test-suite\postman\localhost-certificates`
    4. Click on Add certificate next to Client Certificates
    5. Add the `spineClient` file
    6. Add the `spintClient.key`
    7. Change the Host location to `localhost` : `443`
    8. Click on Add
    9. Close Settings menu

## Setting up test-suite adaptors to run Postman tests
1. Use the terminal and CD into folder `\nia-patient-switching-standard-adaptor\test-suite`
2. Run the start script `./start-test-environment.sh`
3. Allow the system to build adaptors
4. After building your docker suite should look something like this (containers can appear in any order):

<details>
    ```
    - test-suite (expand the folder)
		- ps_gp2gp_translator-1
		- gpc_facade-1
		- mock-spine-mhs-1
		- inbound-1
		- ps_db-1
		- outbound-1
		- activemq-1
		- redis-1
		- dynamodb-1
    ```
</details>

The test-suite creates a Docker volume called `test-suite_pgdata` to store PostgreSQL data between executions of `start-test-environment.sh`.
This is especially useful for the SNOMED data which takes a long time to import.
If you want to start from a fresh DB, delete the volume with `docker volume rm test-suite_pgdata` and then run `./start-test-environment.sh`.

## Running the translator and facade in your IDE for debugging
1. Ensure test-suite environment is setup from steps above
2. Turn off both the `ps_gp2gp_transaltor-1` and `gpc_facade-1` in docker desktop
3. Open the project:`nia-patient-switching-standard-adaptor`
4. Navigate to `nia-patient-switching-standard-adaptor\gp2gp-translator\src\main\java\uk\nhs\adaptors\pss\translator\Gp2gpTranslatorApplication.java`
5. Run `Gp2gpTranslatorApplication.java` with the environment variables below:

<details>

   ```   
   DB_PORT: '5436'
   PS_DB_PORT: '5436'
   HOSTNAME: 'localhost'
   
   PS_DB_URL: "jdbc:postgresql://localhost:5436"
   PS_DB_OWNER_NAME: "postgres"
   PS_FROM_ODS_CODE: "PSS_001"
   PS_DB_OWNER_PASSWORD: "123456"
   PS_DB_HOST: "localhost"
   POSTGRES_PASSWORD: "123456"
   GPC_FACADE_USER_DB_PASSWORD: "123456"
   GP2GP_TRANSLATOR_USER_DB_PASSWORD: "123456"
   GP2GP_TRANSLATOR_SERVER_PORT: "8085"
   GPC_FACADE_SERVER_PORT: "8081"
   PS_QUEUE_NAME: "pssQueue"
   MHS_QUEUE_NAME: "mhsQueue"
   MHS_BASE_URL: "http://localhost:8084/"
   
   PS_AMQP_USERNAME: "admin"
   PS_AMQP_PASSWORD: "admin"
   MHS_AMQP_USERNAME: "admin"
   MHS_AMQP_PASSWORD: "admin"
   
   SDS_API_KEY: "change_if_needed" # used for calculating migration timeouts 
   
   #changepathforsnomedFilepath
   SNOMED_CT_TERMINOLOGY_FILE: "/snomed/file/location/uk_sct2mo_39.0.0_20240925000001Z.zip"
   
   PS_LOGGING_LEVEL: "DEBUG"
   
   ```

</details>

6. Navigate to `nia-patient-switching-standard-adaptor\gpc-api-facade\src\main\java\uk\nhs\adaptors\pss\gpc\GpcFacadeApplication.java`
7. Run `GpcFacadeApplication.java` with the environment variables below:

<details>

```
GPC_FACADE_USER_DB_PASSWORD: "123456"
```
</details>

8. Open Postman and run through the tests


### Troubleshooting:
- Check that both `Gp2gpTranslatorApplication.java` + `GpcFacadeApplication.java` are running locally
- Check the environment variables
- Check that both the `ps_gp2gp_translator-1` + `gpc_facade-1` have stopped running
- Check the certificates in postman
- If the start script doesn't run, check the file permissions to ensure the script is executable or run with 
elevated privileges i.e. `sudo ./start-test-environment.sh`   
