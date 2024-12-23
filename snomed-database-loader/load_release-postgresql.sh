#!/bin/bash
set -e;

basedir="$(pwd)"
dbName=patient_switching
releasePath=$1
snomedCtSchema=snomedct
isMonolith=false

if [ -z ${releasePath} ]
then
  echo "Please set the path to zipped SnomedCT RF2 release as the first argument, e.g. ./load_release-postgresql.sh uk_sct2mo_39.0.0_20240925000001Z.zip"
	exit -1
fi

if [ -z ${PS_DB_OWNER_NAME} ]
then
  echo "Please set the following env var: PS_DB_OWNER_NAME, e.g. \"export PS_DB_OWNER_NAME='postgres'\""
	exit -1
fi

if [ -z ${PS_DB_HOST} ]
then
  echo "Please set the following env var: PS_DB_HOST, e.g. \"export PS_DB_HOST='localhost'\""
	exit -1
fi

if [ -z ${PS_DB_PORT} ]
then
  echo "Please set the following env var: PS_DB_PORT, e.g. \"export PS_DB_PORT='5432'\""
	exit -1
fi

if [ -z ${POSTGRES_PASSWORD} ]
then
  echo "Please set the following env var: POSTGRES_PASSWORD, e.g. \"export POSTGRES_PASSWORD='********'\""
	exit -1
fi

if [[ $1 == *uk_sct2mo* ]]; then
	isMonolith=true
fi

percentEncoder() {
    local length="${#1}"
    for (( i = 0; i < length; i++ )); do
        local character="${1:i:1}"
        case $character in
            [a-zA-Z0-9.~_-]) printf "$character" ;;
            *) printf '%%%02X' "'$character" ;;
        esac
    done
}

ENCODED_POSTGRES_PASSWORD="$(percentEncoder ${POSTGRES_PASSWORD})"
databaseUri="postgresql://${PS_DB_OWNER_NAME}:${ENCODED_POSTGRES_PASSWORD}@${PS_DB_HOST}:${PS_DB_PORT}/${dbName}"

#Unzip the files here, junking the structure
localExtract="tmp_extracted"
generatedLoadScript="tmp_loader.sh"
generatedEnvScript="tmp_environment-postgresql.sql"

fileTypes=(Snapshot)
unzip -j ${releasePath} "*Snapshot*" -d ${localExtract}

#Determine the release date from the filenames
if [[ $isMonolith == true ]]; then
	releaseDateMonoGb=`ls -1 ${localExtract}/*MONOSnapshot*.txt | head -1 | egrep -o '[0-9]{8}'`
else
	releaseDateINT=`ls -1 ${localExtract}/*INT*.txt | head -1 | egrep -o '[0-9]{8}'`
	releaseDateUK=`ls -1 ${localExtract}/*UKEDSnapshot*.txt | head -1 | egrep -o '[0-9]{8}'`
fi

function addLoadScript() {
	fileName=${1/TYPE/${2}}
	fileName=${fileName/DATE/${4}}
	#Check file exists - try beta version if not
	if [ ! -f ${localExtract}/${fileName} ]; then
		origFilename=${fileName}
		fileName="x${fileName}"
		if [ ! -f ${localExtract}/${fileName} ]; then
			echo "Unable to find ${origFilename} or beta version"
			exit -1
		fi
	fi
	tableName=${3}_s
	echo -e "psql ${databaseUri} -c \"\\\copy ${snomedCtSchema}.${tableName} FROM '${basedir}/${localExtract}/${fileName}' DELIMITER E'	' CSV HEADER QUOTE E'\b'\"\n" >> ${generatedLoadScript}
}

echo -e "\nGenerating loading script"
echo "#!/bin/bash" >> ${generatedLoadScript}
echo "# Generated Loader Script" >  ${generatedLoadScript}
chmod +x ${generatedLoadScript}

if [[ $isMonolith == true ]]; then
	addLoadScript sct2_Description_TYPE-en_GB_DATE.txt MONOSnapshot description $releaseDateMonoGb
	addLoadScript der2_cRefset_LanguageTYPE-en_GB_DATE.txt MONOSnapshot langrefset $releaseDateMonoGb
	addLoadScript sct2_Relationship_TYPE_GB_DATE.txt MONOSnapshot relationship $releaseDateMonoGb
else
	addLoadScript sct2_Description_TYPE-en_GB_DATE.txt UKEDSnapshot description $releaseDateUK
	addLoadScript sct2_Description_TYPE-en_INT_DATE.txt Snapshot description $releaseDateINT
	addLoadScript der2_cRefset_LanguageTYPE-en_GB_DATE.txt UKEDSnapshot langrefset $releaseDateUK
fi

#create schema, tables, indexes
psql ${databaseUri} << EOF
	\ir create-database-postgres.sql;
EOF

#load data
./${generatedLoadScript}

#refresh materialized view - this is intentionally completed last as there isn't an transactional safety in this script,
#however the GP2GP Translator service will terminate if the immunization codes have not loaded successfully.
psql "${databaseUri}" << SQL
REFRESH MATERIALIZED VIEW ${snomedCtSchema}.immunization_codes;
REFRESH MATERIALIZED VIEW ${snomedCtSchema}.preferred_terms
SQL

#cleanup
rm -rf $localExtract
rm ${generatedLoadScript}
