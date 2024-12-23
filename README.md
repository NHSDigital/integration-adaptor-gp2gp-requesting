# GP2GP FHIR Request Adaptor
National Integration Adaptor - [GP2GP Requesting Adaptor](https://digital.nhs.uk/developer/api-catalogue/gp2gp/gp2gp-requesting-adaptor)

Incumbent providers (e.g. TPP, EMIS, SystemOne) would have to make changes to their GP Connect interface implementations
in order to deploy GP2GP Adaptor in their infrastructure to support the losing practice scenario - i.e. whereby a 
different practice transfers patient data from the incumbent. In particular, they would need to implement 1.6.0 version
that is required by the GPC Consumer and GP2GP adaptors.

This business case is not always easy to be accepted by the incumbent providers, as they would have to invest time to
make those changes.

The motivation for the GP2GP FHIR Request   Adaptor is to remove the dependency from incumbent providers to do that work.
The idea is to build an adaptor that could be installed and configured in a New Market Entrant (NME) infrastructure,
and could work with the incumbent’s GPC < 1.6.0.

Adaptor consists of two main components:
- GPC API Facade
- GP2GP Translator

Both are Java Spring Boot applications, released as separate docker images.


## Table of contents

1. [Guidance for setting up the GP2GP adaptors in INT](/getting-started-instructions.md)
1. [Guidance for operating the adaptor as a New Market Entrant](/OPERATING.md)
1. [Guidance on integrating with the adaptors APIs](#endpoints)
1. [Guidance for developing the adaptor](/developer-information.md)
1. [Documentation on how this adaptor maps GP2GP concepts to GPConnect concepts](https://github.com/NHSDigital/patient-switching-adaptors-mapping-documentation)

## Endpoints

The Adaptor's facade provides two main endpoints for interacting with patient records.

### POST /Patient/$gpc.migratestructuredrecord

The migratestructuredrecord endpoint is the primary endpoint for the adaptor.
This endpoint initiates the electronic health record (EHR) transfer process. 
To use this endpoint, you need to provide the following headers:

- TO-ASID : ASID identifier of the losing incumbent
- FROM-ASID : ASID identifier of the winning New Market Entrant (NME)
- TO-ODS : ODS identifier of the losing incumbent
- FROM-ODS : ODS identifier of the winning New Market Entrant (NME)
- ConversationId : A unique UUID for the request. If not provided, the adaptor will generate one and include it in the response headers.
  It must be used for all further calls for the patient's NHS number.

If a `ConversationId` header is provided where the value is populated but does not contain a valid UUID, then the 
following response will be returned:

```json
{
	"resourceType": "OperationOutcome",
	"meta": {
		"profile": ["https://fhir.nhs.uk/STU3/StructureDefinition/GPConnect-OperationOutcome-1"]
	},
	"issue": [{
		"severity": "error",
		"code": "invalid",
		"details": {
			"coding": [{
				"system": "https://fhir.nhs.uk/STU3/ValueSet/Spine-ErrorOrWarningCode-1",
				"code": "BAD_REQUEST",
				"display": "Bad request"
			}]
		},
		"diagnostics": "ConversationId header must be either be empty or a valid UUID"
	}]
}
```

For more details on how to query the losing practice details, see the [requesting site requirements].

[requesting site requirements]: https://nhse-dsic.atlassian.net/wiki/spaces/DCSDCS/pages/12512034968/GP2GP+Requesting+Adaptor#Registration-Process-&-EHR-Request

The endpoint also requires a JSON body that includes the needed patient NHS number.
Request Body Example:

   ```json
   {
      "resourceType": "Parameters",
      "parameter": [
         {
            "name": "patientNHSNumber",
            "valueIdentifier": {
               "system": "https://fhir.nhs.uk/Id/nhs-number",
               "value": "{{nhs-number-test-group-8}}"
            }
         },
         {
            "name": "includeFullRecord",
            "part": [
               {
                  "name": "includeSensitiveInformation",
                  "valueBoolean": true
               }
            ]
         }
      ]
   }
   ```

Responds with one of:

1. Initial request: If you successfully configure the endpoint described above and call it, you should receive a 202-accepted response. This means the adaptor has received the request and is making the relevant requests.
2. Polling the request: after receiving a 202 response, we recommend polling the endpoint at regular intervals using an
   increasing call gap strategy until you get a 200 response.
   Each poll can return one of the following responses:
    - 204 No content: this response indicates that we are still processing the requests / waiting for the EHR message response.
    - 200 Success: this response indicates we have successfully received and converted the EHR to JSON; you will also receive the FHIR bundle in the response's body.
      An example of this response can be found within [expectedBundle.json](gp2gp-translator/src/integrationTest/resources/json/expectedBundle.json).
    - 400,404,500,501: The endpoint can return all these possible error codes. These will all provide a detailed error with an operationOutcome JSON model response in the body. This looks like...

      ```json
      {
         "resourceType": "OperationOutcome",
         "meta": {
            "profile": [
               "https://fhir.nhs.uk/STU3/StructureDefinition/GPConnect-OperationOutcome-1"
            ]
         },
         "issue": [
            {
               "severity": "error",
               "code": "exception",
               "details": {
                  "coding": [
                     {
                        "system": "https://fhir.nhs.uk/STU3/ValueSet/Spine-ErrorOrWarningCode-1",
                        "code": "PATIENT_NOT_FOUND",
                        "display": "GP2GP - Patient is not registered at the practice"
                     },
                     {
                       "system": "urn:oid:2.16.840.1.113883.2.1.3.2.4.17.101",
                       "code": "06",
                       "display": "Patient not at surgery"
                     }
                  ]
               }
            }
         ]
      }
      ```

### POST /$gpc.ack

This endpoint finalizes the EHR transfer process.
If you do not call this endpoint after receiving an EHR from the migratestructuredrecord enpoint, then you risk the losing practise triggering off the manual postal transfer.

To use this endpoint, you need to provide the following headers:

- CONVERSATIONID: ID from the initial request.
- CONFIRMATIONRESPONSE: Status of the EHR integration.
    - ACCEPTED: EHR integration successful.
    - FAILED_TO_INTEGRATE: Error encountered; triggers postal process.

Endpoint calling:

This endpoint is a fire-and-forget endpoint.
- If your request is successful, you will get a 200: Success response.
- If your request is unsuccessful, you will get a 500: Server error response.
- If you receive a 500 response, you can retry again at any point, however, it should be noted that you must receive a 200: Success response from the migratestructuredrecord for the given conversation ID to receive a 200: Success from this endpoint.

## Licensing
This code is dual licensed under the MIT license and the OGL (Open Government License).
Any new work added to this repository must conform to the conditions of these licenses.
In particular this means that this project may not depend on GPL-licensed or AGPL-licensed libraries,
as these would violate the terms of those libraries' licenses.

The contents of this repository are protected by Crown Copyright (C).

## Performance

Case 1: Performance Testing with JMeter

We conducted a performance test of the Adaptor using the JMeter tool. 
The use case focused on simulating a patient transfer request, 
where Electronic Health Record (EHR) requests were sent to the Adaptor, expecting a bundle in return. 
This test involved two text attachments with sizes of 2.44 MB and 0.7 MB, respectively. 
The primary goal was to evaluate how the Adaptor manages a heavy workload and to monitor CPU and memory usage 
during the process.

Test Setup and Parameters:

 - GP2GP FHIR Request Adaptor: Deployed in an ECS AWS environment with 4 CPUs and 16 GB of memory (shared between the PS Translator and Facade).
 - MHS Adaptor: Deployed in an ECS AWS environment with 4 CPUs and 16 GB of memory (shared between inbound and outbound).
 - Message Queue: Utilized the mq.m5.xlarge instance type.
 - RDS Database: Hosted on a db.t3.xlarge instance.

The test involved 2000 patient transfers, divided into 5 batches of 400 transfers each. 
The total test duration was measured from the start of the initial request until the completion of all transfers. 
A random pause time between 100 ms and 1100 ms was introduced between transfers, with a socket timeout set to 2 minutes.

Results:

The test load of 2000 transactions was successfully completed in 14 minutes, averaging 420-435 ms per transfer.
CPU utilization during the test was around 50-60%, and memory usage was approximately 70%, 
indicating sufficient capacity for additional load.

-----------------------------------------------------------------

Case 2: Performance Testing via TPP/EMIS and Medicus

In a separate series of tests, up to 10 concurrent patient transfers were completed, 
involving varying attachment types (100, 500, 1000) and file sizes (1 KB, 100 KB, 500 KB, 3.5 MB, 5 MB). 
These tests were conducted with TPP/EMIS as the sending systems and Medicus as the receiving system.

Resource Allocation:

GP2GP FHIR Request Adaptor:
    Facade: 2 vCPUs, 4 GB RAM
    Translator: 2 vCPUs, 4 GB RAM
MHS Adaptor:
    Inbound: 2 vCPUs, 4 GB RAM
    Outbound: 0.25 vCPUs, 0.5 GB RAM
Message Queue: 
    Instance Type: mq.m5.large

Results:

A single patient record of 10 MB was processed in approximately 20 seconds.
On average, the transfer of up to 10 patients was completed within 1 to 1 minute and 40 seconds.


![report1.jpg](test-suite%2Fnon-functional-tests%2Ftest-scenario%2Fperf_report%2Freport1.jpg)

Overall performance statistics:
![report2.png](test-suite%2Fnon-functional-tests%2Ftest-scenario%2Fperf_report%2Freport2.png)

Active transfers per one iteration was 400:
![report4.png](test-suite%2Fnon-functional-tests%2Ftest-scenario%2Fperf_report%2Freport4.png)

Response time:
![report5.png](test-suite%2Fnon-functional-tests%2Ftest-scenario%2Fperf_report%2Freport5.png)

Results can be seen in using graphs by importing results8.jtl into Jmeter.
