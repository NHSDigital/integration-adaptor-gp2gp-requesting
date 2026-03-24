# Common

This is a common module used by GP2GP Translator, GPC API Facade and DB Connector modules.
It holds any common util classes used by more than one module.

## Utility classes description
### DateUtils
Utility class for date-related methods.
- `getCurrentOffsetDateTime()` method returns the current UTC `OffsetDateTime`.

### FhirParser
This class offers methods for parsing and encoding FHIR resources.
- `parseResource(String body, Class<T> fhirClass)` method parses a `String` to a given FHIR resource.
- `encodeToJson(IBaseResource resource)` method encodes a FHIR resource to a `String`.

### ParametersUtils
This class provides a method related to the `Parameters` FHIR resource.
- `getNhsNumberFromParameters(Parameters parameters)` method retrieves the patient's NHS number value
 from a `Parameters` resource.

### CreateParametersUtil
This class provides convenient methods for creating `Parameters` FHIR resource objects.
Meant to be used inside test classes.
- `createValidParametersResource(String nhsNumberValue)` method returns a `Parameters` resource
 with a given patient NHS number set.
