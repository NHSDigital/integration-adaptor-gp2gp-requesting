# Developer Information

## Requirements:

* JDK 17 - We develop the adaptor in Java with Spring Boot
* Docker

## Project structure

    .
    ├── db                          # Dockerfile and scripts for local database setup
    ├── snomed-database-loader      # Scripts loading Snomed CT codes into database
    ├── common                      # Common module used by gp2gp-translator, gpc-api-facade and db-connector
    ├── db-connector                # Common module used by gp2gp-translator and gpc-api-facade, used for db-related classes
    ├── gp2gp-translator            # GP2GP Translator
    ├── gpc-api-facade              # GPC API Facade
    └── mhs-adaptor-mock            # Dockerfile and required files for mock of MHS Adaptor

## Snomed CT Database
Please make sure to load the latest release of Snomed CT UK Edition. See [Configuring the SNOMED Database](./README.md#configuring-the-snomed-database) and [snomed-database-loader](https://github.com/NHSDigital/nia-patient-switching-standard-adaptor/tree/main/snomed-database-loader) for more information.

## Local development
### How to start local environment
1. Go to `docker` directory
2. Create a copy of `example.vars.sh`, name it `vars.sh`
3. Fill in the passwords inside `vars.sh` file:
    - POSTGRES_PASSWORD: Password to be set for the user used to run migrations. It will also be the password for the default postgres user.
    - GPC_FACADE_USER_DB_PASSWORD: Password for the user connecting to the database in the GPC API Facade module.
    - GP2GP_TRANSLATOR_USER_DB_PASSWORD: Password for the user connecting to the database in the GP2GP Translator module.

   There is an option to set following env variables when needed:
    - PS_DB_URL: Database URL required to run migrations (for local environment set 'jdbc:postgresql://localhost:5436/patient_switching')
    - PS_DB_OWNER_NAME: Username of user used to run migrations.
    - POSTGRES_PASSWORD: Password to be set for the user used to run migrations. Also set for the default postgres user.
      This user is used to run the init script on the database. It is required during database creation.
    - GPC_FACADE_USER_DB_PASSWORD: Password for the user connecting to the database in the GPC API Facade module.
    - GP2GP_TRANSLATOR_USER_DB_PASSWORD: Password for the user connecting to the database in the GP2GP Translator module.
    - PS_AMQP_BROKER: Address of the broker with the pss queue
    - MHS_AMQP_BROKER: Address of the broker with the mhs queue
    - PS_QUEUE_NAME: Name of the pss queue
    - MHS_QUEUE_NAME: Name of the mhs queue
    - PS_AMQP_MAX_REDELIVERIES: How many times message should be retried in case of fail on pss queue
    - MHS_AMQP_MAX_REDELIVERIES: How many times message should be retried in case of fail on mhs queue
    - GPC_FACADE_SERVER_PORT: port of the GPC API Facade application
    - GP2GP_TRANSLATOR_SERVER_PORT: port of the GP2GP Translator application
    - MHS_BASE_URL: base URL of the MHS Adapter
    - SSL_ENABLED: is SS: enabled (default is false)
    - KEY_STORE: path to the keystore
    - KEY_STORE_PASSWORD: keystore password
    - KEY_PASSWORD: server private key password
    - TRUST_STORE: path to the truststore
    - TRUST_STORE_PASSWORD: truststore password

   The following variables are used determine if a migration has timed out:
    - SDS_BASE_URL: url of the SDS FHIR API (default is the Production environment)
    - SDS_API_KEY: authentication for the SDS FHIR API
    - TIMEOUT_EHR_EXTRACT_WEIGHTING: weighting factor to account transmission delays and volume throughput times of the RCMR_IN030000UK06 message (default is 1).
    - TIMEOUT_COPC_WEIGHTING: weighting factor to account transmission delays and volume throughput times of the COPC_IN000001UK01 message (default is 1).
    - TIMEOUT_CRON_TIME: cron schedule for the timeout check (default is every six hours)
    - TIMEOUT_SDS_POLL_FREQUENCY: The frequency SDS is polled for message persist durations (default is every 3 cron jobs).

   If you plan to use external queues (like ActiveMQ on AWS), you also need to set credentials for those queues:
    - PS_AMQP_USERNAME
    - PS_AMQP_PASSWORD
    - MHS_AMQP_USERNAME
    - MHS_AMQP_PASSWORD


3. Run `start-local-environment.sh` script:
   ```shell script
    ./start-local-environment.sh
   ```
   It will execute following steps:
    - create a default postgres database and patient_switching database,
    - start MHS Adaptor mock,
    - start ActiveMQ,
    - run migrations,
    - build and start GPC Api Facade service,
    - build and start GP2GP Translator application.
      All components will run in Docker.

4. Follow the `README` in `snomed-database-loader` directory to load Snomed CT into database

### Releasing a new version to Docker Hub

Make sure you are on the main branch, with the latest version of the code before following these instructions.

```shell
git checkout main
git pull
```

When running the buildx commands you may get an error asking you to run the following command, which you should do.
```shell
docker buildx create --use
```

Replace \<version\> with the [next version of the ps-adaptor to release](https://hub.docker.com/r/nhsdev/nia-ps-adaptor/tags).

```shell
docker buildx build -f docker/gp2gp-translator/Dockerfile . --platform linux/arm64/v8,linux/amd64 --tag nhsdev/nia-ps-adaptor:<version> --push
```

Replace \<version\> with the [next version of the gpc-facade to release](https://hub.docker.com/r/nhsdev/nia-ps-facade/tags).

```shell
docker buildx build -f docker/gpc-facade/Dockerfile . --platform linux/arm64/v8,linux/amd64 --tag nhsdev/nia-ps-facade:<version> --push
```

Replace \<version\> with the [next version of the ps-db-migration to release](https://hub.docker.com/r/nhsdev/nia-ps-db-migration/tags).

```shell
docker buildx build -f docker/db-migration/Dockerfile . --platform linux/arm64/v8,linux/amd64 --tag nhsdev/nia-ps-db-migration:<version> --push
```

### Rebuilding services
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
## Getting started for Windows Users
A setup guide is provided for Windows users [here](./getting-started-with-windows.md)