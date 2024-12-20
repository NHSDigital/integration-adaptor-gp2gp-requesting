# Service Failures and Expected Behavior

This guide outlines the behavior of the Requesting Adaptor when various dependent components are down.
In the event of a service outage, the Requesting Adaptor system behaves differently based on the specific service affected.
The guide explains the effects of outages on different components and how the system behaves in each case.
In some scenarios, the system will automatically recover and resume processing once the affected service is restored.
However, some outages, particularly those involving the queue or MHS outbound, may require manual intervention to resend
requests that were missed during downtime.

## System Overview

The Requesting Adaptor initiates a conversation for transferring patient records, relying on several interconnected services:

- GP2GP Translator: Sends patient records to the Requesting Adaptor via a queue.
- Facade Application: Works with the Requesting Adaptor to manage transfer requests.
- MHS Adaptor: Manages inbound and outbound queues, which are responsible for transferring patient records to the incumbent system.
- Other Dependencies: Include Requesting Adaptor database, MHS inbound and outbound services and File storage.

## Service Outage Scenarios

Initial Request - This describes what happen to the initial request made by the User to the Facade to start a GP2GP transfer. 

Transfer in Progress - This describes what happens to a GP2GP transfer has already been requested from the sending system
but is either still being transferred from the sending system, or is waiting for the User to acknowledge the transfer.

Sending acknowledgement to sending system - This describes what happens to the GP2GP transfer when the User is making a
request to the Facade acknowledgement endpoint during the stated scenario.

1. GP2GP Translator is Down
   - Initial Request:
      - Scenario: The GP2GP Translator is not operational
      - Expected Behavior: No request is made to the incumbent system to start the GP2GP process while the translator is down.
        Once the GP2GP Translator recovers, the transfer process resumes.
      - Recovery: Automatic upon Translator recovery; transfer resumes without manual intervention.
   
   - Transfer in Progress:
      - Scenario: The GP2GP Translator is not operational
      - Expected Behaviour: GP2GP transfer is delayed while the Translator is not operational. 
        Facade will continue to respond to requests without being affected.
      - Recovery: When the GP2GP Translator recovers the transfer is processed as normal.

   - Sending acknowledgement to sending system:
      - Scenario: The GP2GP Translator is not operational
      - Expected Behaviour: GP2GP acknowledgement is delayed while the Translator is not operational.
        Facade will continue to respond to requests without being affected.
      - Recovery: When the GP2GP Translator recovers the acknowledgement is sent as normal.
 
2. Message Broker is Down
   - Initial Request:
      - Scenario: The message broker responsible for transferring data between the GP2GP Translator and Requesting Adaptor is down.
      - Expected Behavior: The GP2GP transfer is never sent to the sending adapter. The Facade responds with 500 "Internal Server Error".
      - Recovery: After the queue is restored, any transfers requested during downtime are not sent,
                  but the transfer can be requested again by the user via the Facade.

   - Transfer in Progress:
      - Scenario: The message broker responsible for storing MHS Inbound messages is down.
      - Expected Behaviour: The MHS inbound responds with a 500 error to spine, and that inbound message is lost.
        The GP2GP transfer will appear stuck even after the message broker is restored as the Requesting Adaptor is
        waiting for a message it won't get. The Facade responds with a 204 status code.
      - Recovery: The transfer is non-recoverable.

   - Sending acknowledgement to sending system:
      - Scenario: The message broker responsible for transferring data between the GP2GP Translator and Requesting Adaptor is down.
      - Expected Behavior: Facade responds with 500 and no acknowledgement is sent to the sending system.
      - Recovery: Once the queue is working again another acknowledgement request can be made for the transfer which will be sent to the sending system.

4. Requesting Adaptor Database (DB) is Down
   - Initial Request:
      - Scenario: The Requesting Adaptor's database is not operational.
      - Expected Behavior: The GP2GP transfer is never sent to the sending adapter. The Facade responds with 500 "Internal Server Error".
      - Recovery: After the DB is restored, any transfers requested during downtime are not sent,
        but the transfer can be requested again by the user via the Facade.
     
   - Transfer in Progress:
      - Scenario: The Requesting Adaptor's database is not operational.
      - Expected Behavior: Facade responds with 500 "Internal Server Error". GP2GP transfer is delayed while the Translator is not operational. 
      - Recovery: After the database is restored the transfer is processed as normal.

   - Sending acknowledgement to sending system:
      - Scenario: The Requesting Adaptor's database is not operational.
      - Expected Behavior: Facade responds with 500 and no acknowledgement is sent to the sending system.
      - Recovery: Once the DB is working again another acknowledgement request can be made for the transfer which will be sent to the sending system.

4. Facade Application is Down
   - Initial Request:
      - Scenario: The Facade Application is down.
      - Expected Behavior: The GP2GP transfer is never sent to the sending adapter.
      - Recovery: Once the Facade Application is working again another transfer request can be made which will be sent to the sending system.
     
   - Transfer in Progress:
      - Scenario: The Facade Application is down.
      - Expected Behavior: No negative effects on the ongoing transfer and it proceeds as normal
      - Recovery: Once the Facade Application is operational, the status of the transfer can be checked as normal.

   - Sending acknowledgement to sending system:
      - Scenario: The Facade Application is down.
      - Expected Behavior: No acknowledgement is sent to the sending system.
      - Recovery: Once the Facade Application is working again another acknowledgement request can be made for the transfer which will be sent to the sending system.

5. MHS Outbound Adaptor is down
   - Initial Request:
      - Scenario: The MHS Outbound adaptor is not operational.
      - Expected Behavior: The EHR request is not sent to the incumbent system while the MHS Outbound Adaptor is down. 
        Facade responds with 204 indicating the migration is still in progress.
      - Recovery: Once the MHS Outbound adaptor recovers, the transfer is resumed as normal.
   
   - Transfer in Progress:
      - Scenario: The MHS Outbound adaptor is not operational.
      - Expected Behavior: Facade responds with 204, indicating the migration is still in progress.
      - Recovery: When the MHS Outbound service recovers, the transfer is processed as normal.

   - Sending acknowledgement to sending system:
      - Scenario: The MHS Outbound adaptor is not operational.
      - Expected Behavior: The acknowledgement is queued up but not sent to the sending system.
      - Recovery: When the MHS Outbound service recovers, the acknowledgement is sent as normal.


6. MHS Inbound Adaptor is Down
   - Initial Request:
      - Scenario: The MHS Inbound adaptor is not operational.
      - Expected Behavior: Facade responds with 204, indicating the migration is still in progress.
      - Recovery: After the service is restored, any transfers requested during downtime remain pending. The transfer is non-recoverable.

   - Transfer in Progress:
      - Scenario: The MHS Inbound adaptor is not operational.
      - Expected Behavior: As the EHR extract never reaches the adaptor, the facade receives 204 "No Content" response.
      - Recovery: After the service is restored, any transfers requested during downtime remain pending. The transfer is non-recoverable.

7. File Storage is Down
   - Initial Request:
      - Scenario: The file storage system is not operational.
      - Expected Behavior: 
          - If the EHR Extract doesn't contain attachments, the Facade responds with 204, to indicate the migration is in progress.
          - If the EHR Extract does contain attachments, the Facacde responds with 500 "Internal Server Error" and the body doesn't have any error details.
      - Recovery: After the file storage is restored, the transfer request can be repeated.

   - Transfer in Progress:
      - Scenario: The file storage system is not operational.
      - Expected Behavior:
          - If the EHR Extract doesn't contain attachments, the transfer will complete successfully.
          - If the EHR Extract does contain attachments, the Facade responds with 500 "Internal Server Error" and the body doesn't have any error details.
      - Recovery: After the file storage is restored, the transfer request can be repeated.