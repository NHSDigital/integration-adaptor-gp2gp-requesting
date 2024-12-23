String tfProject             = "nia"
String tfComponent           = "pss"
Boolean publishGPC_FacadeImage  = true // true: to publish gpc_facade image to AWS ECR gpc_facade
Boolean publishGP2GP_TranslatorImage  = true // true: to publish gp2gp_translator image to AWS ECR gp2gp-translator
Boolean publishMhsMockImage  = true // true: to publish mhs mock image to AWS ECR pss-mock-mhs
Boolean publishSnomedSchemaImage = true // true: to publish SNOMED schema image to AWS ECR pss_snomed_schema
Boolean publishDbMigrationImage = true // true: to publish DB migration image to AWS ECR pss_db_migration


pipeline {
    agent{
        label 'jenkins-workers'
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: "10"))
        timeout(time: 1, unit: 'HOURS')
    }

    environment {
        BUILD_TAG = sh label: 'Generating build tag', returnStdout: true, script: 'python3 scripts/tag.py ${GIT_BRANCH} ${BUILD_NUMBER} ${GIT_COMMIT}'

        GPC_FACADE_ECR_REPO_DIR = "pss_gpc_facade"
        GP2GP_TRANSLATOR_ECR_REPO_DIR = "pss_gp2gp-translator"
        SNOMED_SCHEMA_ECR_REPO_DIR = "pss_snomed_schema"
        DB_MIGRATION_ECR_REPO_DIR = "pss_db_migration"
        MHS_MOCK_ECR_REPO_DIR = "pss-mock-mhs"

        GPC_FACADE_DOCKER_IMAGE = "${DOCKER_REGISTRY}/${GPC_FACADE_ECR_REPO_DIR}:${BUILD_TAG}"
        GP2GP_TRANSLATOR_DOCKER_IMAGE = "${DOCKER_REGISTRY}/${GP2GP_TRANSLATOR_ECR_REPO_DIR}:${BUILD_TAG}"
        SNOMED_SCHEMA_DOCKER_IMAGE = "${DOCKER_REGISTRY}/${SNOMED_SCHEMA_ECR_REPO_DIR}:${BUILD_TAG}"
        DB_MIGRATION_DOCKER_IMAGE = "${DOCKER_REGISTRY}/${DB_MIGRATION_ECR_REPO_DIR}:${BUILD_TAG}"
        MHS_MOCK_DOCKER_IMAGE  = "${DOCKER_REGISTRY}/${MHS_MOCK_ECR_REPO_DIR}:${BUILD_TAG}"
    }

    stages {
        stage('Build') {
            stages {
                stage('Tests') {
                    stages {
                        stage('Common modules Check') {
                            steps {
                                script {
                                    sh '''
                                        docker network create ps-network || true
                                        docker-compose -f docker/docker-compose.yml -f docker/docker-compose-checks.yml build common_modules
                                        docker-compose -f docker/docker-compose.yml -f docker/docker-compose-checks.yml up --exit-code-from common_modules common_modules
                                    '''
                                }
                            }
                            post {
                                always {
                                    sh "docker cp common_modules_checks:/home/gradle/service/db-connector/build db-connector-build"
                                    sh "docker cp common_modules_checks:/home/gradle/service/common/build common-build"
                                    archiveArtifacts artifacts: '**/reports/**/*.*', fingerprint: true
                                    junit '**/**/test-results/**/*.xml'
                                }
                            }
                        }
                        stage('DB setup') {
                            steps {
                                script {
                                    sh '''
                                        source docker/vars.local.tests.sh
                                        docker-compose -f docker/docker-compose.yml up -d ps_db
                                        docker-compose -f docker/docker-compose.yml up db_migration
                                        aws s3 cp s3://snomed-schema/uk_sct2mo_39.0.0_20240925000001Z.zip ./snomed-database-loader/uk_sct2mo_39.0.0_20240925000001Z.zip
                                        # As Jenkins is running inside of Docker too, can't just reference the snomed file as a volume as part of the docker run command
                                        # Instead copy the file into a named volume first as a separate docker command
                                        docker volume create --name snomed
                                        cat ./snomed-database-loader/uk_sct2mo_39.0.0_20240925000001Z.zip | docker run --rm --interactive -v snomed:/snomed alpine sh -c "cat > /snomed/uk_sct2mo_39.0.0_20240925000001Z.zip"
                                        docker-compose -f docker/docker-compose.yml run --rm --volume snomed:/snomed snomed_schema /snomed/uk_sct2mo_39.0.0_20240925000001Z.zip
                                        docker volume rm snomed
                                    '''
                                }
                            }
                        }
                        stage('Immunizations Check') {
                            steps {
                                script {
                                    sh '''
                                        source docker/vars.local.tests.sh
                                        cat ./snomed-database-loader/test-load-immunization-codes.sh |  docker run --rm --interactive -v snomed:/snomed alpine sh -c "cat > /snomed/test-load-immunization-codes.sh"
                                        docker-compose -f docker/docker-compose.yml run --entrypoint "bash /snomed/test-load-immunization-codes.sh" --rm --volume snomed:/snomed snomed_schema
                                    '''
                                }
                            }
                        }
                        stage('GPC API Facade Check') {
                            steps {
                                script {
                                    sh '''
                                        source docker/vars.local.tests.sh
                                        docker-compose -f docker/docker-compose.yml -f docker/docker-compose-checks.yml build gpc_facade
                                        docker-compose -f docker/docker-compose.yml -f docker/docker-compose-checks.yml up --exit-code-from gpc_facade gpc_facade activemq
                                    '''
                                }
                            }
                            post {
                                always {
                                    sh "docker cp gpc_facade_tests:/home/gradle/service/gpc-api-facade/build gpc-facade-build"
                                    archiveArtifacts artifacts: 'gpc-facade-build/reports/**/*.*', fingerprint: true
                                    junit '**/gpc-facade-build/test-results/**/*.xml'
                                }
                            }
                        }
                        stage('GP2GP Translator Check') {
                             steps {
                                script {
                                    sh '''
                                       source docker/vars.local.tests.sh
                                       docker-compose -f docker/docker-compose.yml up --build --force-recreate --no-deps -d activemq mhs-adaptor-mock
                                       docker-compose -f docker/docker-compose.yml -f docker/docker-compose-checks.yml build gp2gp_translator
                                       docker-compose -f docker/docker-compose.yml -f docker/docker-compose-checks.yml up --exit-code-from gp2gp_translator gp2gp_translator
                                   '''
                               }
                           }
                           post {
                                always {
                                    sh "docker cp gp2gp_translator_tests:/home/gradle/service/gp2gp-translator/build gp2gp-translator-build"
                                    archiveArtifacts artifacts: 'gp2gp-translator-build/reports/**/*.*', fingerprint: true
                                    junit '**/gp2gp-translator-build/test-results/**/*.xml'
                                }
                           }
                       }
                    }
                    post {
                        always {
                            recordIssues(
                                enabledForFailure: true,
                                tools: [
                                    checkStyle(pattern: '**/reports/checkstyle/*.xml'),
                                    spotBugs(pattern: '**/reports/spotbugs/*.xml')
                                ]
                            )
                            sh '''
                               docker-compose -f docker/docker-compose.yml -f docker/docker-compose-checks.yml down --rmi all --remove-orphans --volumes
                               docker network rm ps-network
                            '''
                            sh "rm -rf db-connector-build"
                            sh "rm -rf common-build"
                            sh "rm -rf gpc-facade-build"
                            sh "rm -rf gp2gp-translator-build"
                        }
                    }
                }
                stage('Build Docker Images') {
                    steps {
                        script {
                            if (publishGPC_FacadeImage) {
                                if (sh(label: "Running ${GPC_FACADE_ECR_REPO_DIR} docker build", script: 'docker build -f docker/gpc-facade/Dockerfile -t ${GPC_FACADE_DOCKER_IMAGE} .', returnStatus: true) != 0) {error("Failed to build ${GPC_FACADE_ECR_REPO_DIR} Docker image")}
                            }
                            if (publishGP2GP_TranslatorImage) {
                                if (sh(label: "Running ${GP2GP_TRANSLATOR_ECR_REPO_DIR} docker build", script: 'docker build -f docker/gp2gp-translator/Dockerfile -t ${GP2GP_TRANSLATOR_DOCKER_IMAGE} .', returnStatus: true) != 0) {error("Failed to build ${GP2GP_TRANSLATOR_ECR_REPO_DIR} Docker image")}
                            }
                            if (publishSnomedSchemaImage) {
                                if (sh(label: "Running ${GP2GP_TRANSLATOR_ECR_REPO_DIR} docker build", script: 'docker build -f docker/snomed-schema/Dockerfile -t ${SNOMED_SCHEMA_DOCKER_IMAGE} .', returnStatus: true) != 0) {error("Failed to build ${SNOMED_SCHEMA_ECR_REPO_DIR} Docker image")}
                            }
                            if (publishDbMigrationImage) {
                                if (sh(label: "Running ${DB_MIGRATION_ECR_REPO_DIR} docker build", script: 'docker build -f docker/db-migration/Dockerfile -t ${DB_MIGRATION_DOCKER_IMAGE} .', returnStatus: true) != 0) {error("Failed to build ${DB_MIGRATION_ECR_REPO_DIR} Docker image")}
                            }
                            if (publishMhsMockImage) {
                                if (sh(label: "Running ${MHS_MOCK_ECR_REPO_DIR} docker build", script: 'docker build -f docker/mhs-adaptor-mock/Dockerfile -t ${MHS_MOCK_DOCKER_IMAGE} docker/mhs-adaptor-mock', returnStatus: true) != 0) {error("Failed to build ${MHS_MOCK_ECR_REPO_DIR} Docker image")}
                            }

                        }
                    }
                }

                stage('Push Image') {
                    when {
                        expression { currentBuild.resultIsBetterOrEqualTo('SUCCESS') }
                    }
                    steps {
                        script {
                            if (ecrLogin(TF_STATE_BUCKET_REGION) != 0 )  { error("Docker login to ECR failed") }

                            if (publishGPC_FacadeImage) {
                                if (sh (label: "Pushing GPC_Facade image", script: "docker push ${GPC_FACADE_DOCKER_IMAGE}", returnStatus: true) !=0) { error("Docker push ${GPC_FACADE_ECR_REPO_DIR} image failed") }
                            }

                            if (publishMhsMockImage) {
                                if (sh(label: "Pushing MHS Mock image", script: "docker push ${MHS_MOCK_DOCKER_IMAGE}", returnStatus: true) != 0) {error("Docker push ${MHS_MOCK_ECR_REPO_DIR} image failed") }
                            }

                            if (publishGP2GP_TranslatorImage) {
                                if (sh(label: "Pushing GP2GP_Translator image", script: "docker push ${GP2GP_TRANSLATOR_DOCKER_IMAGE}", returnStatus: true) != 0) {error("Docker push ${GP2GP_TRANSLATOR_ECR_REPO_DIR} image failed") }
                            }

                            if (publishSnomedSchemaImage) {
                                if (sh(label: "Pushing SNOMED Schema image", script: "docker push ${SNOMED_SCHEMA_DOCKER_IMAGE}", returnStatus: true) != 0) {error("Docker push ${SNOMED_SCHEMA_ECR_REPO_DIR} image failed") }
                            }

                            if (publishDbMigrationImage) {
                                if (sh(label: "Pushing DB migration image", script: "docker push ${DB_MIGRATION_DOCKER_IMAGE}", returnStatus: true) != 0) {error("Docker push ${DB_MIGRATION_ECR_REPO_DIR} image failed") }
                            }

                        }
                    } // steps
                } //stage push images
            } //stages
        } //Stage Build
    } //Stages
      post {
       always {
            sh label: 'Remove exited containers', script: 'docker container prune --force'
            sh label: 'Remove images tagged with current BUILD_TAG', script: 'docker image rm -f $(docker images "*/*:*${BUILD_TAG}" -q) $(docker images "*/*/*:*${BUILD_TAG}" -q) || true'
            sh label: 'Delete Snomed CT database zip', script: 'rm ./snomed-database-loader/uk_sct2mo_39.0.0_20240925000001Z.zip'
            sh label: 'clean up dangling images', script: 'docker image prune -f'
        } // always
      } // post
} //Pipeline

String tfEnv(String tfEnvRepo="https://github.com/tfutils/tfenv.git", String tfEnvPath="~/.tfenv") {
  sh(label: "Get tfenv" ,  script: "git clone ${tfEnvRepo} ${tfEnvPath}", returnStatus: true)
  sh(label: "Install TF",  script: "${tfEnvPath}/bin/tfenv install"     , returnStatus: true)
  return "${tfEnvPath}/bin/terraform"
}

int terraformInit(String tfStateBucket, String project, String environment, String component, String region) {
  String terraformBinPath = tfEnv()
  println("Terraform Init for Environment: ${environment} Component: ${component} in region: ${region} using bucket: ${tfStateBucket}")
  String command = "${terraformBinPath} init -backend-config='bucket=${tfStateBucket}' -backend-config='region=${region}' -backend-config='key=${project}-${environment}-${component}.tfstate' -input=false -no-color"
  dir("components/${component}") {
    return( sh( label: "Terraform Init", script: command, returnStatus: true))
  } // dir
} // int TerraformInit

int terraformInitreconfigure(String tfStateBucket, String project, String environment, String component, String region) {
  String terraformBinPath = tfEnv()
  println("Terraform Init for Environment: ${environment} Component: ${component} in region: ${region} using bucket: ${tfStateBucket}")
  String command = "${terraformBinPath} init -reconfigure -backend-config='bucket=${tfStateBucket}' -backend-config='region=${region}' -backend-config='key=${project}-${environment}-${component}.tfstate' -input=false -no-color"
  dir("components/${component}") {
    return( sh( label: "Terraform Init", script: command, returnStatus: true))
  } // dir
} // int TerraformInit

int terraform(String action, String tfStateBucket, String project, String environment, String component, String region, Map<String, String> variables=[:], List<String> parameters=[]) {
    println("Running Terraform ${action} in region ${region} with: \n Project: ${project} \n Environment: ${environment} \n Component: ${component}")
    variablesMap = variables
    variablesMap.put('region',region)
    variablesMap.put('project', project)
    variablesMap.put('environment', environment)
    variablesMap.put('tf_state_bucket',tfStateBucket)
    parametersList = parameters
    parametersList.add("-no-color")

    // Get the secret variables for global
    String secretsFile = "etc/secrets.tfvars"
    writeVariablesToFile(secretsFile,getAllSecretsForEnvironment(environment,"nia",region))
    String terraformBinPath = tfEnv()
    List<String> variableFilesList = [
      "-var-file=../../etc/global.tfvars",
      "-var-file=../../etc/${region}_${environment}.tfvars",
      "-var-file=../../${secretsFile}"
    ]
    if (action == "apply"|| action == "destroy") {parametersList.add("-auto-approve")}
    List<String> variablesList=variablesMap.collect { key, value -> "-var ${key}=${value}" }
    String command = "${terraformBinPath} ${action} ${variableFilesList.join(" ")} ${parametersList.join(" ")} ${variablesList.join(" ")} "
    dir("components/${component}") {
      return sh(label:"Terraform: "+action, script: command, returnStatus: true)
    } // dir
} // int Terraform

int terraformOutput(String tfStateBucket, String project, String environment, String component, String region) {
  List<String> psDbSecretslist = getSecretsByPrefix("postgres",region)
  Map<String,Object> psDbSecretsExtracted = [:]
  Map<String,Object> psDbSecrets = [:]
    psDbSecretslist.each {
        String rawSecret = getSecretValue(it,region)
        psDbSecrets.put(it,rawSecret)
    }
    psDbSecretsExtracted.put("export PS_DB_OWNER_NAME",psDbSecrets.get('postgres-master-username'))
    psDbSecretsExtracted.put("export POSTGRES_PASSWORD",psDbSecrets.get('postgres-master-password'))
    psDbSecretsExtracted.put("export GP2GP_TRANSLATOR_USER_DB_PASSWORD",psDbSecrets.get('postgres_psdb_gp2gp_translator_user_password'))
    psDbSecretsExtracted.put("export GPC_FACADE_USER_DB_PASSWORD",psDbSecrets.get('postgres_psdb_gpc_facade_user_password'))

    writeVariablesToFile("~/.psdbsecrets.tfvars",psDbSecretsExtracted)

  String terraformBinPath = tfEnv()
  println("Terraform outputs for Environment: ${environment} Component: ${component} in region: ${region} using bucket: ${tfStateBucket}")
  String command = "${terraformBinPath} output > ~/.tfoutput.tfvars"
  dir("components/${component}") {
    return( sh( label: "Terraform Output", script: command, returnStatus: true))
  } // dir
} // int TerraformOutput

Map<String,String> collectTfOutputs(String component) {
  Map<String,String> returnMap = [:]
  dir("components/${component}") {
    String terraformBinPath = tfEnv()
    List<String> outputsList = sh (label: "Listing TF outputs", script: "${terraformBinPath} output", returnStdout: true).split("\n")
    outputsList.each {
      returnMap.put(it.split("=")[0].trim(),it.split("=")[1].trim())
    }
  } // dir
  return returnMap
}

int ecrLogin(String aws_region) {
    String dockerLogin = "aws ecr get-login-password --region ${aws_region} | docker login -u AWS --password-stdin \"https://\$(aws sts get-caller-identity --query 'Account' --output text).dkr.ecr.${aws_region}.amazonaws.com\""
    return sh(label: "Logging in with Docker", script: dockerLogin, returnStatus: true)
}

// Retrieving Secrets from AWS Secrets
String getSecretValue(String secretName, String region) {
  String awsCommand = "aws secretsmanager get-secret-value --region ${region} --secret-id ${secretName} --query SecretString --output text"
  return sh(script: awsCommand, returnStdout: true).trim()
}

Map<String,Object> decodeSecretKeyValue(String rawSecret) {
  List<String> secretsSplit = rawSecret.replace("{","").replace("}","").split(",")
  Map<String,Object> secretsDecoded = [:]
  secretsSplit.each {
    String key = it.split(":")[0].trim().replace("\"","")
    Object value = it.split(":")[1]
    secretsDecoded.put(key,value)
  }
  return secretsDecoded
}

List<String> getSecretsByPrefix(String prefix, String region) {
  String awsCommand = "aws secretsmanager list-secrets --region ${region} --query SecretList[].Name --output text"
  List<String> awsReturnValue = sh(script: awsCommand, returnStdout: true).split()
  return awsReturnValue.findAll { it.startsWith(prefix) }
}

Map<String,Object> getAllSecretsForEnvironment(String environment, String secretsPrefix, String region) {
  List<String> globalSecrets = getSecretsByPrefix("${secretsPrefix}-global",region)
  println "global secrets:" + globalSecrets
  List<String> environmentSecrets = getSecretsByPrefix("${secretsPrefix}-${environment}",region)
  println "env secrets:" + environmentSecrets
  Map<String,Object> secretsMerged = [:]
  globalSecrets.each {
    String rawSecret = getSecretValue(it,region)
    if (it.contains("-kvp")) {
      secretsMerged << decodeSecretKeyValue(rawSecret)
    } else {
      secretsMerged.put(it.replace("${secretsPrefix}-global-",""),rawSecret)
    }
  }
  environmentSecrets.each {
    String rawSecret = getSecretValue(it,region)
    if (it.contains("-kvp")) {
      secretsMerged << decodeSecretKeyValue(rawSecret)
    } else {
      secretsMerged.put(it.replace("${secretsPrefix}-${environment}-",""),rawSecret)
    }
  }
  return secretsMerged
}

void writeVariablesToFile(String fileName, Map<String,Object> variablesMap) {
  List<String> variablesList=variablesMap.collect { key, value -> "${key} = ${value}" }
  sh (script: "touch ${fileName} && echo '\n' > ${fileName}")
  variablesList.each {
    sh (script: "echo '${it}' >> ${fileName}")
  }
}
