#!/usr/bin/env bash

export MHS_INBOUND_VERSION="nhsdev/nia-mhs-inbound:1.3.0"
export MHS_OUTBOUND_VERSION="nhsdev/nia-mhs-outbound:1.3.0"

export PS_TRANSLATOR_VERSION="nhsdev/nia-ps-adaptor:1.0.0"
export PS_FACADE_VERSION="nhsdev/nia-ps-facade:1.0.0"
export PS_DB_MIGRATION_VERSION="nhsdev/nia-ps-db-migration:1.0.0"

export ACTIVEMQ_VERSION="nhsdev/nia-ps-activemq:0.1-amd64" # has an arm64 and amd64 version
export POSTGRES_VERSION="postgres:16.0"
export DYNAMODB_VERSION="nhsdev/nia-dynamodb-local:1.0.3"
export REDIS_VERSION="redis"
