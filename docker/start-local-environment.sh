#!/usr/bin/env bash
set -x -e


# #these two lines of code, makes the docker work with intellij (Windows modification)
#docker-compose down --rmi=local --remove-orphans
#docker-compose rm

source vars.sh

if [[ "$(docker network ls | grep "ps-network")" == "" ]] ; then
    docker network create ps-network
fi
docker-compose build ps_db mhs-adaptor-mock activemq;
docker-compose up -d ps_db mhs-adaptor-mock activemq;

cd ../db-connector
./gradlew update

##### comment out if there is no uk_sct2mo_*.zip file
cd ../snomed-database-loader
./load_release-postgresql.sh "$SNOMED_CT_TERMINOLOGY_FILE"

#####

cd ../docker

docker-compose build gpc_facade gp2gp_translator;
docker-compose up gpc_facade gp2gp_translator;
