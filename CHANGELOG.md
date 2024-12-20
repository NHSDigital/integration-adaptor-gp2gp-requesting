# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),

## [Unreleased]

## [3.0.8] - 2024-11-22

### Fixed

* When a `LinkSet` which is a linkage between a `ReferralRequest` and one or more `DocumentReferences` is mapped then a
  `Condition` will no longer be mapped for this `Linkset`.
  Instead, the `statementRef`s will be added to the `supportingInfo` when mapping the `ReferralRequest`
  as `DocumentReferences`

## [3.0.7] - 2024-11-19

### Fixed

* When provided an `agentPerson` without a `name` element, the adaptor will no longer throw an exception, but will
  instead map the `Practitioner` with a `familyName` of `"unknown"`.

## [3.0.6] - 2024-11-11

### Fixed
* Remove 20 MB data processing limit to enable the Adaptor to handle larger pieces of data.

## [3.0.5] - 2024-11-05

### Added
* Documented how the Adaptor behaves when dependent services are unavailable.
* Expanded the functionality added in [version 2.1.0](#210---2024-04-17) to deduplicate SystmOne Problems.
  Handle the case where a duplicate, empty `ObservationStatement` is provided by SystmOne and ignore it from the
  generated FHIR output.

## [3.0.4] - 2024-10-28

### Fixed

- In the event that an inbound MHS message cannot be processed and needs to be sent to the dead letter queue, the
  adaptor will now emit a log message at INFO level as opposed to DEBUG level.
- Fixed a bug when mapping a post-dated acute prescription with multiple issues, whereby the adaptor was generating
  a `MedicationRequest [Plan]` which was being referenced by multiple `MedicationRequest [Order]`.
  Now each acute prescription issue gets its own `MedicationRequest [Plan]`, with the `priorPrescription` field linking
  the plans together.

## [3.0.3] - 2024-08-23

> [!NOTE]
> **Upgrade information** This release includes an update to the SNOMED database
> Users will need to perform an [update of their SNOMED database](OPERATING.md#updating-the-snomed-database).
> This will need to be performed first, followed by deploying the updated version of the translator image.

## Added
* If a `medicationStatement` or `medicationRequest` record includes a `confidentialityCode`, the `meta.security` field of the
corresponding FHIR resource will now be [appropriately populated][nopat-docs].
* If a `referralRequest` record includes a `confidentialityCode`, the `meta.security` field of the
  corresponding FHIR resource will now be [appropriately populated][nopat-docs].
* If a `procedureRequest` record includes a `confidentialityCode`, the `meta.security` field of the
    corresponding FHIR resource will now be [appropriately populated][nopat-docs].
* If a `linkset` record includes a `confidentialityCode`, the `meta.security` field of the
  corresponding FHIR resource will now be [appropriately populated][nopat-docs].
* If a `documentReference` record includes a `confidentialityCode`, the `meta.security` field of the
  corresponding FHIR resource will now be [appropriately populated][nopat-docs].
* If an `immunization` record includes a `confidentialityCode`, the `meta.security` field of the
    corresponding FHIR resource will now be [appropriately populated][nopat-docs].
* If a `bloodPressure` record includes a `confidentialityCode`, the `meta.security` field of the
    corresponding FHIR resource will now be [appropriately populated][nopat-docs].
* If a `Observation`, `Specimen`, `DiagnosticReport` record includes a `confidentialityCode`, the `meta.security` field of the
  corresponding FHIR resource will now be [appropriately populated][nopat-docs].

### Fixed
* Resolved issue where the SNOMED import script would reject a password containing a '%' character.
* Fixed some Test Results being given a duplicated `Observation.category` entries for `Laboratory`.
* Fixed issue where the GPC Facade was not returning an error when an invalid `ConversationId` header 
  was provided. The Facade will now return a 400 instead of a 500 HTTP response.
* Filing Comments were creating with incorrect `effectiveDateTime`, this is now set from the 
  `ehrComposition /author / time` instead.
* Filing Comments were creating with an incorrect `performer`, this now references the
  `ehrComposition / author / agentRef` instead.
* Addressed a bug where immunizations were incorrectly mapped to observations when the Snomed CT code being sent was a
  Description ID.
  The adaptor previously only checked against known vaccination Concept IDs.

## [3.0.2] - 2024-07-18

### Added
* If a `ehrComposition` record includes a `confidentialityCode`, the `meta.security` field of the corresponding
  `Encounter` FHIR resource will now be [appropriately populated][nopat-docs].
* Add support for an Organization being referenced within the `ReferralRequest.recipient` field.

## [3.0.1] - 2024-07-12

### Added
* The AllergyIntoleranceMapper has been enhanced to support the redaction fix. If an Allergy Intolerance record includes a `confidentialityCode`,
  the `meta.security` field of the corresponding FHIR resource will now be [appropriately populated][nopat-docs].
* When the SNOMED DB ingest script has not completed successfully, The GP2GP Translator Service will now exit and throw a
  RuntimeException with the following message:
  
  ```
  FATAL: Expected Immunization codes not found in snomedct.immunization_codes view.
  SNOMED CT Database not set up correctly.
  Please update / reload the SNOMED DB.  
  ```

[nopat-docs]: https://simplifier.net/guide/gpconnect-data-model/Home/Build/FHIR-resources?version=current#Resources-not-to-be-disclosed-to-a-patient

### Fixed
* `DiagnosticReport.result`s now preserve original ordering provided in the HL7.

## [3.0.0] - 2024-07-02

### Removed
* The adaptor no longer checks incoming attachment content types aginst the `SUPPORTED_FILE_TYPES` list.
  This functionality can be implemented outside the adaptor if still desired.

## [2.1.2] - 2024-06-28

### Changed
* The `/$gpc.ack` endpoint is now case insensitive and supports uppercase and lowercase values for the `conversationId` header values.

### Added
* Retry mechanism has been added for when the MHS outbound returns a 500 server response when acknowledging COPC messages.

### Fixed
* Mapping of `issued` for Test Group Headers, Test Results, Filing Comment and has been updated to use time value from
  the GP2GP `ObservationStatement / availabilityTime` field and use `EhrComposition / author / time` if not available.

## [2.1.1] - 2024-06-19

### Changed
* Cron time schedule has been changed from 6h to 2h so that the timeouts can be identified earlier

### Fixed
* When mapping a `MedicationRequest (PLAN)`, if the text in the original text is duplicated by a pertinent information
  text, then the original text is disregarded to avoid the unnecessary duplication.

## [2.1.0] - 2024-04-17
### Added

* SystmOne send a problem over GP2GP as two `ObservationStatement`s, where one is truncated and one isn't.
  This truncation can be identified where an `ObservationStatement` is part of a problem, and the note ends in '...'.
  The adaptor will now identify this duplication, and merge the two into one `ObservationStatement`.

  <details>
    <summary>Example</summary>
    Original XML:
    <pre>
  &lt;ehrComposition xmlns=&quot;urn:hl7-org:v3&quot; classCode=&quot;COMPOSITION&quot; moodCode=&quot;EVN&quot;&gt;
    &lt;id root=&quot;e9105749-5aef-400d-8ad4-649ff03ac879&quot;/&gt;
    &lt;code code=&quot;25671000000102&quot; codeSystem=&quot;2.16.840.1.113883.2.1.3.2.4.15&quot; displayName=&quot;Surgery Consultation Note&quot;/&gt;
    &lt;statusCode code=&quot;COMPLETE&quot;/&gt;
    ...
    &lt;component typeCode=&quot;COMP&quot;&gt;
        &lt;ObservationStatement classCode=&quot;OBS&quot; moodCode=&quot;EVN&quot;&gt;
            &lt;id root=&quot;ba4edfcd-142e-4090-8a81-9ca2790849a8&quot;/&gt;
            &lt;code code=&quot;14J..&quot; codeSystem=&quot;2.16.840.1.113883.2.1.3.2.4.14&quot; displayName=&quot;H/O: injury&quot;&gt;
                &lt;translation code=&quot;161586000&quot; codeSystem=&quot;2.16.840.1.113883.2.1.3.2.4.15&quot; displayName=&quot;H/O: injury&quot;/&gt;
                &lt;translation code=&quot;14J..00&quot; codeSystem=&quot;2.16.840.1.113883.2.1.6.2&quot; displayName=&quot;H/O: injury&quot;/&gt;
            &lt;/code&gt;
            &lt;statusCode code=&quot;COMPLETE&quot;/&gt;
            &lt;effectiveTime&gt;
                &lt;low value=&quot;20001004&quot;/&gt;
                &lt;high value=&quot;19000101000000&quot;/&gt;
            &lt;/effectiveTime&gt;
            &lt;availabilityTime value=&quot;20001004&quot;/&gt;
            &lt;pertinentInformation typeCode=&quot;PERT&quot;&gt;
                &lt;sequenceNumber value=&quot;+1&quot;/&gt;
                &lt;pertinentAnnotation classCode=&quot;OBS&quot; moodCode=&quot;EVN&quot;&gt;
                    &lt;text&gt;Problem severity: Minor H/O: injury to little finger left hand poss gla...&lt;/text&gt;
                &lt;/pertinentAnnotation&gt;
            &lt;/pertinentInformation&gt;
        &lt;/ObservationStatement&gt;
    &lt;/component&gt;
    &lt;component typeCode=&quot;COMP&quot;&gt;
        &lt;LinkSet classCode=&quot;OBS&quot; moodCode=&quot;EVN&quot;&gt;
            &lt;id root=&quot;7f384915-a2f9-44df-b407-e9c339707297&quot;/&gt;
            &lt;code code=&quot;394775005&quot; codeSystem=&quot;2.16.840.1.113883.2.1.3.2.4.15&quot; displayName=&quot;Inactive Problem&quot;/&gt;
            &lt;statusCode code=&quot;COMPLETE&quot;/&gt;
            &lt;effectiveTime&gt;
                &lt;low value=&quot;20001004&quot;/&gt;
                &lt;high value=&quot;19000101000000&quot;/&gt;
            &lt;/effectiveTime&gt;
            &lt;availabilityTime value=&quot;20001004&quot;/&gt;
            &lt;conditionNamed inversionInd=&quot;true&quot; typeCode=&quot;NAME&quot;&gt;
                &lt;namedStatementRef classCode=&quot;OBS&quot; moodCode=&quot;EVN&quot;&gt;
                    &lt;id root=&quot;ba4edfcd-142e-4090-8a81-9ca2790849a8&quot;/&gt;
                &lt;/namedStatementRef&gt;
            &lt;/conditionNamed&gt;
        &lt;/LinkSet&gt;
    &lt;/component&gt;
    &lt;component typeCode=&quot;COMP&quot;&gt;
        &lt;ObservationStatement classCode=&quot;OBS&quot; moodCode=&quot;EVN&quot;&gt;
            &lt;id root=&quot;f04d2995-c6f8-4cf0-8e07-5362a950e2a5&quot;/&gt;
            &lt;code code=&quot;14J..&quot; codeSystem=&quot;2.16.840.1.113883.2.1.3.2.4.14&quot; displayName=&quot;H/O: injury&quot;&gt;
                &lt;translation code=&quot;161586000&quot; codeSystem=&quot;2.16.840.1.113883.2.1.3.2.4.15&quot; displayName=&quot;H/O: injury&quot;/&gt;
                &lt;translation code=&quot;14J..00&quot; codeSystem=&quot;2.16.840.1.113883.2.1.6.2&quot; displayName=&quot;H/O: injury&quot;/&gt;
            &lt;/code&gt;
            &lt;statusCode code=&quot;COMPLETE&quot;/&gt;
            &lt;effectiveTime&gt;
                &lt;center nullFlavor=&quot;NI&quot;/&gt;
            &lt;/effectiveTime&gt;
            &lt;availabilityTime value=&quot;20001004&quot;/&gt;
            &lt;pertinentInformation typeCode=&quot;PERT&quot;&gt;
                &lt;sequenceNumber value=&quot;+1&quot;/&gt;
                &lt;pertinentAnnotation classCode=&quot;OBS&quot; moodCode=&quot;EVN&quot;&gt;
                    &lt;text&gt;(New Episode). H/O: injury to little finger left hand poss glass in wound therefore refered to A+E&lt;/text&gt;
                &lt;/pertinentAnnotation&gt;
            &lt;/pertinentInformation&gt;
        &lt;/ObservationStatement&gt;
    &lt;/component&gt;
  &lt;/ehrComposition&gt;
    </pre>
    Gets merged into:
    <pre>
  &lt;ehrComposition xmlns=&quot;urn:hl7-org:v3&quot; classCode=&quot;COMPOSITION&quot; moodCode=&quot;EVN&quot;&gt;
    &lt;id root=&quot;e9105749-5aef-400d-8ad4-649ff03ac879&quot;/&gt;
    &lt;code code=&quot;25671000000102&quot; codeSystem=&quot;2.16.840.1.113883.2.1.3.2.4.15&quot; displayName=&quot;Surgery Consultation Note&quot;/&gt;
    &lt;statusCode code=&quot;COMPLETE&quot;/&gt;
    ...
    &lt;component typeCode=&quot;COMP&quot;&gt;
        &lt;ObservationStatement classCode=&quot;OBS&quot; moodCode=&quot;EVN&quot;&gt;
            &lt;id root=&quot;ba4edfcd-142e-4090-8a81-9ca2790849a8&quot;/&gt;
            &lt;code code=&quot;14J..&quot; codeSystem=&quot;2.16.840.1.113883.2.1.3.2.4.14&quot; displayName=&quot;H/O: injury&quot;&gt;
                &lt;translation code=&quot;161586000&quot; codeSystem=&quot;2.16.840.1.113883.2.1.3.2.4.15&quot; displayName=&quot;H/O: injury&quot;/&gt;
                &lt;translation code=&quot;14J..00&quot; codeSystem=&quot;2.16.840.1.113883.2.1.6.2&quot; displayName=&quot;H/O: injury&quot;/&gt;
            &lt;/code&gt;
            &lt;statusCode code=&quot;COMPLETE&quot;/&gt;
            &lt;effectiveTime&gt;
                &lt;low value=&quot;20001004&quot;/&gt;
                &lt;high value=&quot;19000101000000&quot;/&gt;
            &lt;/effectiveTime&gt;
            &lt;availabilityTime value=&quot;20001004&quot;/&gt;
            &lt;pertinentInformation typeCode=&quot;PERT&quot;&gt;
                &lt;sequenceNumber value=&quot;+1&quot;/&gt;
                &lt;pertinentAnnotation classCode=&quot;OBS&quot; moodCode=&quot;EVN&quot;&gt;
                    &lt;text&gt;Problem severity: Minor (New Episode). H/O: injury to little finger left hand poss glass in wound therefore refered to A+E&lt;/text&gt;
                &lt;/pertinentAnnotation&gt;
            &lt;/pertinentInformation&gt;
        &lt;/ObservationStatement&gt;
    &lt;/component&gt;
    &lt;component typeCode=&quot;COMP&quot;&gt;
        &lt;LinkSet classCode=&quot;OBS&quot; moodCode=&quot;EVN&quot;&gt;
            &lt;id root=&quot;7f384915-a2f9-44df-b407-e9c339707297&quot;/&gt;
            &lt;code code=&quot;394775005&quot; codeSystem=&quot;2.16.840.1.113883.2.1.3.2.4.15&quot; displayName=&quot;Inactive Problem&quot;/&gt;
            &lt;statusCode code=&quot;COMPLETE&quot;/&gt;
            &lt;effectiveTime&gt;
                &lt;low value=&quot;20001004&quot;/&gt;
                &lt;high value=&quot;19000101000000&quot;/&gt;
            &lt;/effectiveTime&gt;
            &lt;availabilityTime value=&quot;20001004&quot;/&gt;
            &lt;conditionNamed inversionInd=&quot;true&quot; typeCode=&quot;NAME&quot;&gt;
                &lt;namedStatementRef classCode=&quot;OBS&quot; moodCode=&quot;EVN&quot;&gt;
                    &lt;id root=&quot;ba4edfcd-142e-4090-8a81-9ca2790849a8&quot;/&gt;
                &lt;/namedStatementRef&gt;
            &lt;/conditionNamed&gt;
        &lt;/LinkSet&gt;
    &lt;/component&gt;
  &lt;/ehrComposition&gt;
    </pre>

  </details>

* When a `CommentType: USER COMMENT` `NarrativeStatement` is located within the `BATTERY` of a `Filed Report`,
  the adaptor now generates a new [filing comment][filing-comment] `Observation` and [references this][diagnostic-report-result]
  in the result property of the parent `DiagnosticReport`.
  This change makes the adaptor more closely resemble the GP Connect specification for DiagnosticReport and filing
  comments.
  The generated `Observation` filing comment will have the `status` of `unknown`.

[filing-comment]: https://developer.nhs.uk/apis/gpconnect-1-6-0/accessrecord_structured_development_observation_filingComments.html
[diagnostic-report-result]: https://developer.nhs.uk/apis/gpconnect-1-6-0/accessrecord_structured_development_DiagnosticReport.html#result

## [2.0.0] - 2024-04-12

### Fixed

* **Breaking Change** Identifier values and code systems where an OID is provided 
  (such as `2.16.840.1.113883.2.1.6.9`) will now be provided as a URN (i.e. `urn:oid:2.16.840.1.113883.2.1.6.9`),
  as per GP Connect specification.

## [1.4.7] - 2024-04-02

### Changed
* The source of date for List (Consultation) is now ehrComposition author time otherwise from 
  the following fields in order of precedence:
    - ehrComposition availibiltyTime
    - ehrComposition effectiveTime - center
    - EhrComposition effectiveTime - high
    - EhrComposition effectiveTime - low

## [1.4.6] - 2024-03-21

* `Condition.onsetDateTime` is now set to NULL when low or center entries of effectiveTime are `nullFlavor="UNK"`.

## [1.4.5] - 2024-03-01

### Fixed
* Removed the `EhrExtract / AvailabilityTime` field as a fallback value from:
  - `Condition.assertedDate`
  - `List.date`
  - `DiagnosticReport.issued`
  - `Observation.issuedDate`

* Replaced the `EhrExtract / AvailabilityTime` field as a fallback value with `EhrComposition / author / time[@value]` for:
  - `AllergyIntolerance.assertedDate`
  - `MedicationRequest.authoredOn`

## [1.4.4] - 2024-02-28

### Fixed
* Plan Statements (Recalls) were previously mapped with a fixed value of `active` for the Status field.
  The adaptor will now interrogate the text field to see if a status has been provided, otherwise defaulting to `unknown`.

## [1.4.3] - 2024-02-15

### Added
* Added Materialised View in the SnomedCT database to be used when querying for preferred terms.

> [!NOTE]
> **Upgrade information** This release includes an update to the SNOMED database
> Users will need to perform an [update of their SNOMED database](OPERATING.md#updating-the-snomed-database).
> This will need to be performed first, followed by deploying the updated version of the translator image.

## [1.4.2] - 2024-01-31

### Added
* Added Episodicity information to `comment` property of Observations.

### Fixed
* Fixed malformed role coding defect introduced in version 1.4.1

## [1.4.1] - 2024-01-17

### Added
* Assigning a role to a practitioner who records vaccinations

### Fixed
* Fixed an issue where `Observation Test Group` or `Observation Test Results` were incorrectly creating a relationship to `Filing Comments` using the `has-member` relationship
* `ProcedureRequestMapper.authoredOn` is no longer populated with `EhrExtract / availabilityTime` as a fallback,
  but does use `EhrComposition / author / time` as a fallback instead now.

## [1.4.0] - 2024-01-10

### Changed
* REST buffer size has been set to 150Mb

### Fixed
* Fixed issue where mapping failed due to a Referral Request Priority not being found.
* Codings are now provided (code, display and system) in `PractionionerRole.code` and `Organization.type` fields,
  where only the `text` attribute was provided previously.
* Fixed a bug which could lead to medication resource not being mapped if a failure had occurred when processing the previous EhrExtract during the medication mapping stage


## [1.3.0] - 2023-12-11

### Added 
* In the event of a GP2GP failure, the raw error code is now available in the `/Patient/$gpc.migratestructuredrecord` response section with code system `2.16.840.1.113883.2.1.3.2.4.17.101`.

### Changed
* Removed surplus references to Medication Statements from the Topic and Category entry arrays. The
  [GP Connect documentation](https://developer.nhs.uk/apis/gpconnect-1-6-0/accessrecord_structured_development_consultation_guidance.html#clinical-item-references) states Medications should be referenced using the Medication Request resource, which is already done.
* Updated postman collection to include attachment tests.

## [1.2.1] - 2023-11-23

### Fixed

* Swapped the mixed-up population of 'author' and 'practitioner' fields in AllergyIntolerance.
* A SystmOne attachment split into multiple chunks can arrive with the same filename "Untitled".
  Previously each fragment would be stored using the filename provided, which in the case of SystmOne caused chunks to overwrite each other.
  The adaptor now generates a UUID which is prepended to the filename of a provided chunk to ensure uniqueness.

## [1.2.0] - 2023-11-17

### Added

* Populate `identifier` field for `Practitioner` resources using the General Practitioner (GMP) number if available.

### Fixed

* Prevent `NarrativeStatement / text` sat within a blood pressure `CompoundStatement` from being mapped
  into separate "Comment note" Observation resources. This information is already populated within
  the blood pressure `Observation.comment` field, so creating a separate resource was duplicating this info.

### Changed

* Changed `ReferralRequest` mapping to use `ASAP` instead of the `Stat` value.

## [1.1.0] - 2023-11-09

### Changed

* Updated the way immunization codes are loaded to build these from the relationships reference set in the SNOMED CT 
  release rather than a static set of values.
  The previous static list of values was incomplete and included invalid codes.
  More information about how this improved functionality works can be found in the [SNOMED database documentation](snomed-database-loader/README.md#immunization-codes).

> [!NOTE]
> **Upgrade information** This release includes a [database migration](OPERATING.md#updating-the-application-schema).
> This database migration will need to be performed first, followed by deploying the updated version of the facade and translator images.
> Finally users will need to perform an [update of their SNOMED database](OPERATING.md#updating-the-snomed-database).

## [1.0.1] - 2023-11-21

This release backported the following fix to the v1.0 release.

### Fixed

* A SystmOne attachment split into multiple chunks can arrive with the same filename "Untitled".
  Previously each fragment would be stored using the filename provided, which in the case of SystmOne caused chunks to overwrite each other.
  The adaptor now generates a UUID which is prepended to the filename of a provided chunk to ensure uniqueness.

## [1.0.0] - 2023-11-01 🎉

### Added

* Added functionality to include episodicity qualifiers when mapping allergy intolerances.

### Changed

* Preserve the ordering of code / translation elements when mapping Codeable Concepts.

### Fixed

* Fix invalid state transition bug which caused the adaptor to move from a failed state to an in-progress state when a
  positive acknowledgement was received.

## [0.16] - 2023-10-31

### Fixed

* Add additional error handling for exceptions raised when processing PSS queue, and MHS queue messages.
* Fix bug in some SQL statements which caused excessively large amounts of data to be returned, sometimes resulting in 
a PostgresSQL Out of Memory error.

## [0.15] - 2023-10-24

* Fixed issue with some `ObservationStatement` coded as blood pressure readings not being output into Bundle.
* Fixed issue with `AllergyIntolerance` recorder and asserter fields mapping.

## [0.14] - 2023-10-17

### Added

* Documented database requirements in [OPERATING.md](/OPERATING.md#database-requirements)
* New docker image published as [nhsdev/nia-ps-db-migration](https://hub.docker.com/r/nhsdev/nia-ps-db-migration)
* Querying SDS service using an addtional endpoint to fetch ODS code

### Fixed

* Fix issue where continue message was not accepted by EMIS
* Fixed issue where EMIS `cid` references caused large message merging to fail 
* Fix issue where attachments were given the incorrect object storage URL
* Fix issue where attachments were not de-compressed
* Enable logging of migration status updates when PS_LOGGING_LEVEL is set to DEBUG
* Fix acknowledgement message as it was not accepted by system one 
* Fix SDS failing as part of GP2GP transfer process

## [0.13] - 2023-09-13

### Added

* Add details about object storage to operating documentation.
* Enable AWS Instance Roles to be used for connecting to S3

### Fixed

* Fix issue where attachments with filenames containing special characters causes the transfer to fail.
* Fix issue where some resource types do not contain a degraded code when a SNOMED code has not been provided.
* Fix issue where unnecessary length checks on inline attachments could cause a transfer to fail.

## [0.12] - 2023-08-29

### Changed

* Change the codeable concept mapping to map Egton Codes found in `code` elements.
* Change the codeable concept mapping to map Read Codes (READV2, CTV3) found in `code` elements.
* Change the codeable concept mapping to map unknown code systems found in `code` elements.

### Fixed

* Fix issue where Inbound Adaptor rejects an EHR Extract from EMIS containing inline attachments where the description 
contains only the filename.
* Fix issue where Allergy Intolerances are referenced incorrectly when they are referenced from a list.
* Fix issue where a `Condition` is not correctly mapping the `code` element in all instances.


