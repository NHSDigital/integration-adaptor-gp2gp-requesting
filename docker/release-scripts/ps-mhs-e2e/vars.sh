#!/usr/bin/env bash

# MHS VARS

#SECRET KEY VARS
#User-specific env variables
export SNOMED_FILE_LOCATION="../uk_sct2mo_39.0.0_20240925000001Z.zip"

export MHS_SECRET_PARTY_KEY="Y90664-9198273"
export MHS_SECRET_CLIENT_CERT="-----BEGIN CERTIFICATE-----

-----END CERTIFICATE-----"
export MHS_SECRET_CLIENT_KEY="-----BEGIN RSA PRIVATE KEY-----

-----END RSA PRIVATE KEY-----"
export MHS_SECRET_CA_CERTS="-----BEGIN CERTIFICATE-----

-----END CERTIFICATE-----
-----BEGIN CERTIFICATE-----

-----END CERTIFICATE-----"

#DYNAMODB VARS
export DYNAMODB_PORT="8000"

#MONGODB VARS
export MONGODB_PORT="27017"

#REDIS VARS
export REDIS_PORT="6379"

#GLOBAL VARS
export BUILD_TAG="latest" #need to change back to "latest"
export BASE_IMAGE_TAG="latest"
export MHS_LOG_LEVEL="DEBUG"
export AWS_ACCESS_KEY_ID="FILL IN"
export AWS_SECRET_ACCESS_KEY="FILL IN"
export MHS_STATE_TABLE_NAME="mhs_state"
export MHS_SYNC_ASYNC_STATE_TABLE_NAME="sync_async_state"
export MHS_DB_ENDPOINT_URL="http://dynamodb:`echo $DYNAMODB_PORT`"

# MHS OUTBOUND VARS
export MHS_OUTBOUND_PORT="80"
export MHS_RESYNC_INTERVAL="1"
export MAX_RESYNC_RETRIES="20"
export MHS_SPINE_ROUTE_LOOKUP_URL="http://route"
export MHS_SPINE_ORG_CODE="YES"
export MHS_SPINE_REQUEST_MAX_SIZE="4999600"
export MHS_FORWARD_RELIABLE_ENDPOINT_URL="https://192.168.128.11/reliablemessaging/forwardreliable"
export MHS_OUTBOUND_VALIDATE_CERTIFICATE="False"
#MHS_OUTBOUND_ROUTING_LOOKUP_METHOD = SPINE_ROUTE_LOOKUP or SDS_API VARS
export MHS_OUTBOUND_ROUTING_LOOKUP_METHOD="SPINE_ROUTE_LOOKUP"
#export MHS_SDS_API_URL=""
#export MHS_SDS_API_KEY=""

# #RABBITMQ VARS
# export RABBITMQ_PORT_1="15672"
# export RABBITMQ_PORT_2="5672"
# export RABBITMQ_HOSTNAME="localhost"

#MHS INBOUND VARS
export MHS_INBOUND_PORT="443"
export MHS_INBOUND_SERVICE_PORTS="`echo $MHS_INBOUND_PORT`,`echo $MHS_OUTBOUND_PORT`"
export MHS_INBOUND_QUEUE_BROKERS="amqp://activemq:5672"
export MHS_INBOUND_QUEUE_NAME="inbound"
export MHS_SECRET_INBOUND_QUEUE_USERNAME="guest"
export MHS_SECRET_INBOUND_QUEUE_PASSWORD="guest"
export MHS_INBOUND_QUEUE_MESSAGE_TTL_IN_SECONDS="1000"
export MHS_INBOUND_USE_SSL="True"
export MHS_INBOUND_QUEUE_MAX_RETRIES="3"
export MHS_INBOUND_QUEUE_RETRY_DELAY="500"
export MHS_INBOUND_HEALTHCHECK_SERVER_PORT="8083"

#MHS ROUTE VARS
export MHS_ROUTE_PORT="8082"
export MHS_SDS_URL="ldap://192.168.128.11"
export MHS_SDS_SEARCH_BASE="ou=services,o=nhs"
export MHS_DISABLE_SDS_TLS="True"
export MHS_SDS_REDIS_CACHE_HOST="redis"
export MHS_SDS_REDIS_DISABLE_TLS="True"

export MHS_STATE_TABLE_NAME="mhs_state"
export MHS_SYNC_ASYNC_STATE_TABLE_NAME="sync_async_state"

####################################

# GP2GP Vars

export PS_DB_PORT=5436;
export PS_DB_URL="jdbc:postgresql://localhost:${PS_DB_PORT}";
export PS_DB_URL_INTERNAL="jdbc:postgresql://host.docker.internal:${PS_DB_PORT}";
export PS_DB_OWNER_NAME="postgres";
export PS_FROM_ODS_CODE="PSS_001";
export PS_DB_HOST="localhost"
export PS_DB_OWNER_PASSWORD="testpassword"; # change
export PS_DB_OWNER_PASSWORD="testpassword"; # change
export POSTGRES_PASSWORD="testpassword"; # change
export GPC_FACADE_USER_DB_PASSWORD="testpassword"; # change
export GP2GP_TRANSLATOR_USER_DB_PASSWORD="testpassword"; # change
export GP2GP_TRANSLATOR_SERVER_PORT=8085;
export GPC_FACADE_SERVER_PORT=8081;
export PS_QUEUE_NAME="pssQueue";
export MHS_QUEUE_NAME="mhsQueue";
export PS_AMQP_MAX_REDELIVERIES=3;
export MHS_AMQP_MAX_REDELIVERIES=3;
export MHS_BASE_URL="http://localhost:8082/";
export PS_LOGGING_LEVEL="DEBUG";
export PS_AMQP_USERNAME="admin";
export PS_AMQP_PASSWORD="admin";
export DB_HOSTNAME="localhost";

export PS_LOGGING_LEVEL="DEBUG"

