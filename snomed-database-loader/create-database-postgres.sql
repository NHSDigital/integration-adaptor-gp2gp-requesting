drop schema if exists snomedct cascade;
create schema snomedct;

grant usage on schema snomedct to application_user;
alter default privileges in schema snomedct
grant select on tables to application_user;
set schema 'snomedct';

create table description_s(
                              id varchar(18) not null,
                              effectivetime char(8) not null,
                              active char(1) not null,
                              moduleid varchar(18) not null,
                              conceptid varchar(18) not null,
                              languagecode varchar(2) not null,
                              typeid varchar(18) not null,
                              term text not null,
                              casesignificanceid varchar(18) not null,
                              PRIMARY KEY(id, effectivetime)
);
CREATE INDEX description_conceptid_idx ON snomedct.description_s
    USING btree (conceptid COLLATE pg_catalog."default");

create table langrefset_s(
                             id uuid not null,
                             effectivetime char(8) not null,
                             active char(1) not null,
                             moduleid varchar(18) not null,
                             refsetid varchar(18) not null,
                             referencedcomponentid varchar(18) not null,
                             acceptabilityid varchar(18) not null,
                             PRIMARY KEY(id, effectivetime)
);
CREATE INDEX langrefset_referencedcomponentid_idx ON snomedct.langrefset_s
    USING btree (referencedcomponentid);

CREATE table relationship_s(
                            id varchar(18) not null,
                            effectivetime char(8) not null,
                            active char(1) not null,
                            moduleid varchar(18) not null,
                            sourceId varchar(18) not null,
                            destinationId varchar(18) not null,
                            relationshipGroup char(8) not null,
                            typeId varchar(18) not null,
                            characteristicTypeId varchar(18) not null,
                            modifierId varchar(18) not null,
                            PRIMARY KEY(id, effectivetime)
);
CREATE INDEX relationship_sourceId_idx ON snomedct.relationship_s
    USING btree (sourceId);
CREATE INDEX relationship_destinationId_idx ON snomedct.relationship_s
    USING btree (destinationId);
CREATE INDEX relationship_typeId_idx ON snomedct.relationship_s
    USING btree (typeId);

CREATE MATERIALIZED VIEW immunization_codes AS
WITH RECURSIVE immunization_heirarchy AS (
    -- Start with the root conceptIds for immunizations
    SELECT DISTINCT sourceId AS conceptId
    FROM snomedct.relationship_s
    WHERE typeId = '116680003' -- relationshipType (typeId) = 'IsA' (meaning child of)
      AND (
        -- these are the root conceptIds
        destinationId IN ('787859002','127785005','304250009','90351000119108','713404003')
            OR
            -- ensure the original root conceptIds are also included
        sourceId IN ('787859002','127785005','304250009','90351000119108','713404003'))
    UNION
    -- Recursively find child records
    -- if no records are found then the maximum depth for this recursion has been reached
    SELECT r.sourceId
    FROM snomedct.relationship_s r
             JOIN immunization_heirarchy i ON r.destinationId = i.conceptId
    WHERE r.typeId = '116680003' -- relationshipType (typeId) is 'IsA' (child of)
)
SELECT ih.conceptId as concept_or_description_id
FROM immunization_heirarchy ih
UNION ALL
SELECT ds.id
FROM immunization_heirarchy ih
JOIN snomedct.description_s ds on ih.conceptId = ds.conceptid;

CREATE INDEX immunization_codes_conceptid_idx ON immunization_codes
    USING btree (concept_or_description_id);

CREATE MATERIALIZED VIEW preferred_terms AS
SELECT d.id, d.conceptid, d.term, d.active
FROM langrefset_s l
         INNER JOIN description_s d
             ON l.referencedcomponentid=d.id
  WHERE l.refsetid IN ('999001261000000100','999000691000001104')
  AND d.typeid = '900000000000013009'
  AND l.acceptabilityid = '900000000000548007';

CREATE INDEX preferred_terms_conceptid_idx ON preferred_terms
    USING btree (conceptid);

GRANT USAGE ON SCHEMA snomedct TO application_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA snomedct TO application_user;