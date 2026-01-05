# **GP2GP Redactions**

Redaction is the process of restricting access or ‘hiding’ information in the online viewer from the patient and anyone 
they have granted proxy access to.  It does not remove the information from the patient’s record.

Before information is shared, sensitive information which could be harmful to a patient or is about or refers to other
people (third parties) should be assessed, and a decision taken about whether or not to redact it.  

Individual words, sentences, or paragraphs within an entry cannot be redacted.  The entire entry, for instance the
consultation or document must be either shared (visible online) or redacted i.e. made not visible online).

## **Why are redactions used**

When GP records are shared with patients or their representatives (nominated proxy), the GP practice is responsible for
ensuring that only appropriate information is disclosed.  To ensure this happens, information in both the existing record
and any new items should be checked and where necessary, redacted.

Most records will not have content that requires redaction.  For individual requests for full online record access (i.e.
past, historic and current records) it is best practice for all of the records to be checked in advance of being shared.

For more information about redactions please review the NHS England Documentation for redactions:

[*https://www.england.nhs.uk/long-read/redacting-information-for-online-record-access/*](https://www.england.nhs.uk/long-read/redacting-information-for-online-record-access/)


## Enabling redactions support in the GP2GP adaptors

The GP2GP Adaptor needs to be deployed with the necessary configuration for redactions to be enabled.

To enable Redactions, the GP2GP Adaptor should be deployed with the following environment variable set as follows:

***`GP2GP_REDACTIONS_ENABLED: true`***

To disable Redactions, the GP2GP Adaptor should be deployed with the following environment variable set as follows:

***`GP2GP_REDACTIONS_ENABLED: false`***

Note that if redactions are not enabled, the resultant XML be produced with an `interactionId` of `RCMR_IN030000UK06` and
redaction security labels will not be populated.

**This setting should be set to `false` until the incumbent systems have enabled redactions functionality across their
whole estate.  If in any doubt please contact NIA Support.**


## How are redactions identified

When sending a patient record using the GP2GP System, a JSON FHIR Bundle is sent. Certain resources (covered below) can 
be marked as redacted by applying a `NOPAT` security label within the resource metadata. `NOPAT` is a code within the 
*ActCode Code System* and signifies that the information should not not be disclosed to the patient, family or 
caregivers.

This label should be applied to the `meta.security` element with the `system`, `code` and `display` values set exactly as
below:

```json
{
  "meta":{
    "security":[
      {
        "system":"http://hl7.org/fhir/v3/ActCode",
        "code":"NOPAT",
        "display":"no disclosure to patient, family or caregivers without attending provider's authorization"
      }
    ]
  }
}
```

When a patient record is received from an incumbent system using the GP2GP System, an `interactionId` of `RCMR_IN030000UK07`
will be provided. Certain elements within the XML may be marked as redacted by a `confidentialityCode` security label
containing a `code` value of `NOPAT`.

This security label should be applied to element being redacted and should be exactly as below:

```xml
<confidentialityCode code="NOPAT" codeSystem="2.16.840.1.113883.4.642.3.47" displayName="no disclosure to patient, family or caregivers without attending provider's authorization"/>
```

## **GP2GP Requesting Adaptor Redactions**

This section details the resource types which can be redacted when using the GP2GP Request Adaptor.

**This also includes details of any known issues with the redaction being applied when the patient record is sent by an incumbent (Optum / TPP).**

### Laboratory Results

Laboratory Results consist of a number of resources which can have the `NOPAT` security label applied.

**TPP only allows laboratory results to be marked as redacted at the consultation level, it is not possible to redact individual items.**

#### Diagnostic Report

**Both Optum and TPP do not allow confidentiality tags to be added to  `DiagnosticReport` resources in their respective systems.  This has been raised with Optium to investigate further.**

#### Specimen

**Both Optum and TPP do not allow confidentiality tags to be added to  `Specimen` resources in their respective systems.  This has been raised with Optium to investigate further.**

#### Observation \- Filing Comment

To mark an `Observation (Filing Comment)` as redacted, the `NOPAT` security label should be applied to either of the following elements:

1. The actual `NarrativeStatement / confidentialityCode`.
2. The `CompoundStatement / confidentialityCode` when the `CompoundStatement` is the parent of the actual `NarrativeStatement`.
3. The containing `EhrComposition / confidentialityCode`.

This will populate the `Observation(Filing Comment)` in the resultant JSON FHIR with the `NOPAT` security label.

#### Observation \- Test Group Header

To mark an `Observation (Test Group)` as redacted, the `NOPAT` security label should be applied to either of the following elements:

1. The actual `CompoundStatement / confidentialityCode`.
2. The associated `RequestStatement / confidentialityCode`.
3. The containing `EhrComposition / confidentialityCode`.

This will populate the `Observation(Test Group)` in the resultant JSON FHIR with the `NOPAT` security label.

#### Observation \- Test Result

To mark an `Observation (Test Result)` as redacted, the `NOPAT` security label should be applied to either of the following elements:

1. The actual `ObservationStatement / confidentialityCode`.
2. The associated `RequestStatement / confidentialityCode`.
3. The containing `EhrComposition / confidentialityCode`.

This will populate the `Observation(Test Result)` in the resultant JSON FHIR with the `NOPAT` security label.

### Allergy Intolerance

To mark a `Drug Allergy` or `Non-Drug Allergy` as redacted, the `NOPAT` security label should be applied to either of the following elements:

1. The actual `ObservationStatement / confidentialityCode`.
2. The containing `EhrComposition / confidentialityCode`.

This will populate the `AllergyIntollerance` in the resultant JSON FHIR with the `NOPAT` security label.

### Condition

To mark a `Condition` as redacted, the `NOPAT` security label should be applied to either of the following elements:

1. `LinkSet / confidentialityCode`.
2. `EhrComposition / confidentialityCode`.
3. `ObservationStatement / confidentialityCode`.

This will populate the relevant `Condition` in the resultant JSON FHIR with the `NOPAT` security label.

### Immunization

To mark an `Immunization` as redacted, the `NOPAT` security label should be applied to either of the following elements:

1. `ObservationStatement / confidentialityCode`.
2. `EhrComposition / confidentialityCode`.

This will populate the relevant `Immunization` in the resultant JSON FHIR with the `NOPAT` security label.

### Medication Request / Medication Statement

To mark a `MedicationRequest` and associated `MedicationStatement`  as redacted, the `NOPAT` security label should be applied to the `MedicationStatement / confidentialityCode` element.

This will populate the relevant `MedicationRequest`  and `MedicationStatement` resources in the resultant JSON FHIR with the `NOPAT` security label.

### Document Reference

To mark a `DocumentReference` as redacted, the `NOPAT` security label should be applied to a `NarrativeStatement / reference / referredToExternalDocument / confidentialityCode` element.

This will populate the relevant `DocumentReference` resource in the resultant JSON FHIR with the `NOPAT` security label.

### Procedure Request

To mark a `ProcedureRequest` as redacted, the `NOPAT` security label should be applied to either of the following elements:

4. `PlanStatement / confidentialityCode`.
5. `EhrComposition / confidentialityCode` .

This will populate the relevant `ProcedureRequest` in the resultant JSON FHIR with the `NOPAT` security label.

**In Optum, the equivalent of a ProcedureRequest is a Diary Entry.  It is not possible to apply a redaction to Diary Entries.  This has been reviewed and does not present a clinical risk.**

### Referral Request

To mark a `ReferralRequest` as redacted, the `NOPAT` security label should be applied to either of the following elements:

6. `RequestStatement / confidentialityCode`.
7. `EhrComposition / confidentialityCode`.

This will populate the relevant `ReferralRequest` in the resultant JSON FHIR with the `NOPAT` security label.

### Observation

An Observation can contain a variety of data.  In addition to the *laboratory results observations* documented above, the following observation resources can also be redacted.

#### 

#### Observation \- Blood Pressure

To mark an `Observation(Blood Pressure)` as redacted either of the following conditions must occur:

1. The `CompoundStatement / confidentialityCode` where the `CompoundStatement` is the `Battery` containing the measurements has the `NOPAT` security label applied.
2. When any contained `ObservationStatement / confidentialityCode` within a  `CompoundStatement` which is a `Battery` have the `NOPAT` security label applied.
3. `EhrComposition / confidentialityCode` has the `NOPAT` security label applied.

This will populate the `Observation(Blood Pressure)` in the resultant JSON FHIR with the `NOPAT` security label.

#### Observation \- Uncategorised

To mark an `Observation (Uncategorised)` as redacted, the `NOPAT` security label should be applied to either of the following elements:

1. The actual `ObservationStatement / confidentialityCode`.
2. The containing `EhrComposition / confidentialityCode`.

This will populate the `Observation(Uncategorised)` in the resultant JSON FHIR with the `NOPAT` security label.

### Encounter

To mark an `Encounter` as redacted, the `NOPAT` security label should be applied to the `EhrComposition / confidentialityCode` element.

This will populate the relevant `Encounter` in the resultant JSON FHIR with the `NOPAT` security label.

###  Referral Request

To mark a `ReferralRequest` as redacted, the `NOPAT` security label should be applied to either:

1. The relevant `RequestStatement / confidentialityCode`.
2. The associated `ehrComposition / confidentialityCode`

This will populate the relevant `ReferralRequest.meta` field in the resultant JSON FHIR with the `NOPAT` security label.

### List \- Topic

**EMIS and TPP do not support the concept of redacting at a topic level.**