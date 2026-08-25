# DB connector

This is a common module, used by GP2GP Translator and GPC API Facade.
It holds services needed for communication with the database.

## Migrations
Migrations are implemented using Liquibase and need to be run manually before starting the applications.

To run the migrations, you need to specify the database URL and credentials of the database user
that will be used to run the migrations. **This user needs to have permissions to create a database.**
The first migration will try to connect to the default postgres database, to be able to create
the `patient_switching` database used by the adaptor. Then, the next set of migrations
will be executed on the `patient_switching` database.

Set the following environment variables:
- `PS_DB_OWNER_NAME`
- `POSTGRES_PASSWORD`
- `PS_DB_URL`

To run the migrations use the following command:
```shell script
./gradlew update
```
The changelog can be found under the `/changelog` path.

### How to add migrations
To add a new migration, create a new XML file inside the `/changelog/migration` directory.
All files from this directory are included in the `db.changelog-master.xml` file.

## How to run tests

### How to run unit tests:

```shell script
./gradlew test
```

### How to run all checks:

```shell script
./gradlew check
```

## Troubleshooting

### `gradle-wrapper.jar` doesn't exist

If `gradle-wrapper.jar` doesn't exist, run in terminal:
* Install Gradle (macOS): `brew install gradle`
* Update Gradle: `gradle wrapper`
