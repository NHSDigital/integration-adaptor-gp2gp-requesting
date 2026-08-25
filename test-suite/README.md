# Getting started with Postman tests

## Prerequisites before running Postman tests
1. Ensure Docker and Postman are installed.
2. If you are on Windows, run the shell scripts from WSL/bash as described in [getting-started-with-windows.md](../getting-started-with-windows.md).
3. Review `test-suite/vars.sh` before starting the environment. At minimum, make sure `SNOMED_FILE_LOCATION` points to a local SNOMED zip file and the MHS certificate variables are populated.
4. Import the Postman collection from `test-suite/postman/Test Collection PSS Adaptor.postman_collection.json`.
5. Add the Postman certificates from `test-suite/postman/localhost-certificates`:
    1. Open Postman > Settings.
    2. Go to the Certificates tab.
    3. Turn on CA Certificates and add `rootCA.pem`.
    4. Add a client certificate using `spineClient.crt` and `spineClient.key`.
    5. Set the host to `localhost` and the port to `443`.
    6. Save the certificate configuration and close Settings.

## Setting up test-suite adaptors to run Postman tests
1. Open a shell in `test-suite` folder location.
2. Run the start script: `./start-test-environment.sh`.
3. The script stops and removes existing Docker containers before starting the test suite, so make sure there is nothing else running that you want to keep.
4. Allow the services to build and start.
5. After startup, your Docker suite should look something like this (containers can appear in any order):

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
1. Ensure the test-suite environment is running from the steps above.
2. Stop both `ps_gp2gp_translator-1` and `gpc_facade-1` in Docker Desktop so the local applications can bind to the same ports and consume the AMQP messages.
3. Open the repository root: `integration-adaptor-gp2gp-requesting`.
4. Run `gp2gp-translator/src/main/java/uk/nhs/adaptors/pss/translator/Gp2gpTranslatorApplication.java` with environment variables similar to the following. These use `localhost` values instead of the container hostnames from `test-suite/vars.sh`.

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

   # Change path for SNOMED filepath
   SNOMED_CT_TERMINOLOGY_FILE: "/snomed/file/location/uk_sct2mo_42.0.0_20260408000001Z.zip"

   PS_LOGGING_LEVEL: "DEBUG"

   ```

</details>

5. Run `gpc-api-facade/src/main/java/uk/nhs/adaptors/pss/gpc/GpcFacadeApplication.java` with environment variables similar to the following:

<details>

```
GPC_FACADE_USER_DB_PASSWORD: "123456"
```
</details>

6. Open Postman and run through the tests.


### Troubleshooting:
- Check that both `Gp2gpTranslatorApplication.java` and `GpcFacadeApplication.java` are running locally
- Check the environment variables, especially any values copied from `test-suite/vars.sh` that need to use `localhost` when running in the IDE
- Check that both `ps_gp2gp_translator-1` and `gpc_facade-1` have stopped running in Docker
- Check the certificates in Postman
- If the start script fails immediately, verify `vars.sh`, the SNOMED file path, and that you are running the script from a bash-compatible shell
  and that the script is executable or run with elevated privileges i.e. `sudo ./start-test-environment.sh`
