# GPC API Facade

GPC API Facade - HTTP FHIR API that is similar to GPC 1.6.0 standard.
It enables NME to request and fetch patient data in the FHIR format.
This facade only serves light tasks, delegating all the work to the async GP2GP Translator component.

## Enabling TLS
To turn on TLS Mutual Authentication (MA), set the `SSL_ENABLED` environment variable to `true`. You will also need to create a keystore and a truststore
and set paths to them along with required passwords in those variables:
- `KEY_STORE`: path to the keystore
- `KEY_STORE_PASSWORD`: keystore password
- `KEY_PASSWORD`: server private key password
- `TRUST_STORE`: path to the truststore
- `TRUST_STORE_PASSWORD`: truststore password

To allow Docker to fetch the keystore and truststore, you need to bind a directory containing them.
You can achieve this by adding these lines to the `gpc_facade` service in `docker-compose.yml`, below the `networks` part:
```yml
volumes:
  - type: bind
    source: /Users/someuser/certs #path on your machine
    target: /certs #path on the docker volume
```

You also need to import the server's private key to the keystore, and import the trusted client's public certificates
(or root CA certificate) to the truststore. Below you can find the instructions on how to create the server and client certificates
and how to import them.

### Creation of certificates

1. Create the Root CA:
```
openssl req -newkey rsa:4096 -keyform PEM -keyout ca.key -x509 -days 3650 -outform PEM -out ca.cer
```

2. Create the Server Private Key
```
openssl genrsa -out server_private.key 4096
```

3. Create the CSR with private key for Root CA
```
openssl req -new -key server_private.key -out server.csr -sha256
```

4. Create the Server Public Certificate
```
openssl x509 -req -in server.csr -CA ca.cer -CAkey ca.key -set_serial 100 -extensions server -days 1460 -outform PEM -out server_public.cer -sha256
```

5. Create a PKCS12 cert for the server (it's needed to import the private key to the keystore)
```
openssl pkcs12 -export -inkey server_private.key -in server_public.cer -out server_pkcs.p12
```

6. Create the client private key
```
openssl genrsa -out client_private.key 4096
```

7. Create the CSR with client private key
```
openssl req -new -key client_private.key -out client.csr
```

8. Create the client public certificate
```
openssl x509 -req -in client.csr -CA ca.cer -CAkey ca.key -set_serial 101 -extensions client -days 365 -outform PEM -out client_public.cer
```

9. Create a PKCS12 cert for the client
```
openssl pkcs12 -export -inkey client_private.key -in client_public.cer -out client_pkcs.p12
```

10. Import the server private key (using PKCS) to the keystore (keystore will be created if it doesn't exist)
```
keytool -importkeystore -srckeystore server_pkcs.p12 -srcstoretype PKCS12 -destkeystore keystore.jks -deststoretype JKS
```

11. Import the public client certificate to the truststore (truststore will be created if it doesn't exist)
```
keytool -keystore truststore.jks -importcert -file client_public.cer -alias client
```

## How to run tests

### How to run unit tests:

```shell script
./gradlew test
```

### How to run all checks:

```shell script
./gradlew check
```

### How to run integration tests:
*Integration tests require a running database and queue.*
Running tests from the terminal:
```shell script
./gradlew integrationTest
```
You can also run tests from IntelliJ, just remember to set the database password
inside the `gpc-api-facade/src/integrationTest/resources/application.yml` file (or set the `GPC_FACADE_USER_DB_PASSWORD` variable).

## Troubleshooting

### `gradle-wrapper.jar` doesn't exist

If `gradle-wrapper.jar` doesn't exist, run in terminal:
* Install Gradle (macOS): `brew install gradle`
* Update Gradle: `gradle wrapper`
