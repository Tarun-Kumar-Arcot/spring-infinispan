# spring-infinispan

A Spring Boot application deployable on JBoss EAP 7.4 that provides a REST API for interacting with an embedded Infinispan cache backed by a local file store.

---

## Stack

| Component       | Version         |
|-----------------|-----------------|
| Spring Boot     | 2.7.18          |
| Infinispan      | 11.0.13.Final   |
| JBoss EAP       | 7.4             |
| Java            | 11+             |
| Packaging       | WAR             |

---
## Configure JBoss EAP standalone-ha.xml using jboss-cli:-

~~~
batch
  /subsystem=infinispan/cache-container=custom:add(statistics-enabled=true)
  /subsystem=infinispan/cache-container=custom/transport=jgroups:add(lock-timeout=60000)
  /subsystem=infinispan/cache-container=custom/local-cache=custom-cache:add(statistics-enabled=true)
  /subsystem=infinispan/cache-container=custom/local-cache=custom-cache/store=file:add(relative-to=jboss.server.data.dir,path=custom-cache,passivation=false,purge=false,preload=true,shared=false)
  /subsystem=infinispan/cache-container=custom:write-attribute(name=default-cache,value=custom-cache)
  run-batch
~~~

  or in the infinispan subsystem add the following:-

~~~
  <cache-container name="custom" default-cache="custom-cache" statistics-enabled="true">
    <transport lock-timeout="60000"/>
    <local-cache name="custom-cache" statistics-enabled="true">
    <file-store path="custom-cache" relative-to="jboss.server.data.dir" passivation="false" preload="true" purge="false" shared="false"/>
    </local-cache>
  </cache-container>
~~~

## Build

```bash
mvn clean package
```

The WAR is produced at `target/spring-infinispan.war`.

---

## Deployment

Copy the WAR to JBoss EAP's deployment directory:

```bash
cp target/spring-infinispan.war $JBOSS_HOME/standalone/deployments/
```

Start JBoss EAP with the HA profile:

```bash
$JBOSS_HOME/bin/standalone.sh -c standalone-ha.xml
```

The application is available at:

```
http://localhost:8080/spring-infinispan
```

---

## Configuration

Defaults can be overridden in `src/main/resources/application.properties` or via system properties at runtime.

| Property                   | Default               | Description                         |
|----------------------------|-----------------------|-------------------------------------|
| `infinispan.cache.name`    | `custom-cache`        | Name of the Infinispan cache        |
| `infinispan.store.location`| `/tmp/infinispan-store` | Directory for the local file store |

Override at runtime example:

```bash
$JBOSS_HOME/bin/standalone.sh -c standalone-ha.xml \
  -Dinfinispan.store.location=/data/myapp/cache
```

---

## REST API

Base path: `/spring-infinispan/api/cache`

### Add or update an entry

```
PUT /api/cache/{key}
Content-Type: text/plain

{value}
```

```bash
curl -X PUT http://localhost:8080/spring-infinispan/api/cache/name \
  -H "Content-Type: text/plain" \
  -d "John"
```

Response:
```json
{"status":"stored","key":"name","value":"John"}
```

---

### Retrieve an entry

```
GET /api/cache/{key}
```

```bash
curl http://localhost:8080/spring-infinispan/api/cache/name
```

Response (found):
```json
{"status":"found","key":"name","value":"John"}
```

Response (not found):
```json
{"status":"not_found","key":"name"}
```

---

### List all entries

```
GET /api/cache
```

```bash
curl http://localhost:8080/spring-infinispan/api/cache
```

Response:
```json
{"size":3,"entries":{"name":"John","city":"Bangalore","env":"production"}}
```

---

### Delete an entry

```
DELETE /api/cache/{key}
```

```bash
curl -X DELETE http://localhost:8080/spring-infinispan/api/cache/name
```

Response:
```json
{"status":"removed","key":"name"}
```

---

### Clear all entries

```
DELETE /api/cache
```

```bash
curl -X DELETE http://localhost:8080/spring-infinispan/api/cache
```

Response:
```json
{"status":"cleared"}
```

---

## File Store

Cache entries are persisted to the local filesystem at the path configured by `infinispan.store.location` (default: `/tmp/infinispan-store`).

Verify files exist after adding entries:

```bash
ls /tmp/infinispan-store/
```

You will see `custom-cache.dat` and `custom-cache.idx`.

### Persistence across restarts

The file store is configured with `preload=true` and `purgeOnStartup=false`. Entries survive a JBoss EAP restart and are automatically reloaded into memory on the next startup.

---

## Running Tests

```bash
mvn test
```

Tests use the same embedded Infinispan configuration and run without a JBoss EAP server.
