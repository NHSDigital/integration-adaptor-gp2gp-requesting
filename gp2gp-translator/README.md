# GP2GP Translator

GP2GP Translator - a new system that is the heart of the PSS Adaptor.
It is responsible for GPC ↔︎ GP2GP translations, assembly of binary document data,
communication with the incumbent system via MHS Adaptor and data cleanup.

## How to run tests

### How to run unit tests:

```shell script
./gradlew test
```

### How to run all checks:

```shell script
./gradlew check
```

### How to run integration tests:

Integration tests require a running database and queue.
Make sure the GP2GP Translator application is off, because if it's not, it will steal the message from the `inbound` queue
before the application started by the tests has a chance to grab it.

Running tests from the terminal:
```shell script
./gradlew integrationTest
```

You can also run tests from IntelliJ, just remember to set the database password
inside the `gp2gp-translator/src/integrationTest/resources/application.yml` file (or set the `GP2GP_TRANSLATOR_USER_DB_PASSWORD` variable).

If you get lots of integration test failures within the fixtures, you can set the `BaseEhrHandler.OVERWRITE_EXPECTED_JSON`
to `true` to regenerate them.
Once regenerated, review the changes made using `git diff` or similar.

## Troubleshooting

### `gradle-wrapper.jar` doesn't exist

If `gradle-wrapper.jar` doesn't exist, run in terminal:
* Install Gradle (macOS): `brew install gradle`
* Update Gradle: `gradle wrapper`
