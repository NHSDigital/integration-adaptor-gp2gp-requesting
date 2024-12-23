#!/usr/bin/env bash

export DB_PORT='5436';
export PS_DB_PORT='5436';
export HOSTNAME='localhost';

export PS_DB_URL="jdbc:postgresql://$HOSTNAME:$DB_PORT";
export PS_DB_OWNER_NAME="postgres";
export PS_FROM_ODS_CODE="PSS_001";
export PS_DB_OWNER_PASSWORD="123456";
export PS_DB_HOST="$HOSTNAME";
export POSTGRES_PASSWORD="123456";
export GPC_FACADE_USER_DB_PASSWORD="123456";
export GP2GP_TRANSLATOR_USER_DB_PASSWORD="123456";
export GP2GP_TRANSLATOR_SERVER_PORT="8085";
export GPC_FACADE_SERVER_PORT="8081";
export PS_QUEUE_NAME="pssQueue";
export MHS_QUEUE_NAME="mhsQueue";
export PS_AMQP_MAX_REDELIVERIES="3";
export MHS_AMQP_MAX_REDELIVERIES="3";
export MHS_BASE_URL="http://$HOSTNAME:8080/";
export PS_LOGGING_LEVEL="DEBUG";

export PS_AMQP_USERNAME="admin";
export PS_AMQP_PASSWORD="admin";

export MHS_AMQP_USERNAME="admin";
export MHS_AMQP_PASSWORD="admin";

export SSL_ENABLED=false;
export KEY_STORE=/certs/keystore.jks;
export TRUST_STORE=/certs/truststore.jks;

export STORAGE_TYPE="LocalMock";
export STORAGE_REGION="";
export STORAGE_CONTAINER_NAME=""; #Local Bucket or Azure Container name
export STORAGE_REFERENCE=""; #Azure blob name or AWS user reference”
export STORAGE_SECRET=""; #Secret Key

export SDS_API_KEY=""
export SDS_BASE_URL="https://int.api.service.nhs.uk/spine-directory/FHIR/R4/"

export SKIP_DECODE="false"
#change path for snomedFile fath
#export SNOMED_CT_TERMINOLOGY_FILE="/mnt/c/Users/yourUserName/yourPath/uk_sct2mo_39.0.0_20240925000001Z.zip";