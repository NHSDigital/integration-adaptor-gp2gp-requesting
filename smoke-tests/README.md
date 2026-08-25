# Smoke Tests

## Running the smoke tests

1. Make sure the environment is set up
2. Ensure you are in the `smoke-tests` directory
3. Run `./run-smoke-tests.sh <path to your vars.sh script>`, where the parameter is the location of your configuration shell script.

### Troubleshooting on M1 Mac

Issue: `zsh: permission denied: ./run-smoke-tests.sh`

Resolution: To give the smoke tests script the permission to run, in your terminal run `chmod +x run-smoke-tests.sh`

----

Issue: `zsh: permission denied: ./gradlew`

Resolution: To give `gradlew` the permission to run, in your terminal run `chmod +x gradlew`

----

Issue: `Error: Could not find or load main class org.gradle.wrapper.GradleWrapperMain`

Resolution: Ensure Gradle is installed and the correct version is present within the `smoke-tests` directory. Run `brew install gradle`
