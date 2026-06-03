FROM postgres:16.0

RUN apt-get update && apt-get install -y unzip
COPY /snomed-database-loader/ /snomed-database-loader/
WORKDIR /snomed-database-loader
ENTRYPOINT [ "./load_release-postgresql.sh" ]