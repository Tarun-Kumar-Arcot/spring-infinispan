# spring-infinispan

A Spring Boot application deployed on JBoss EAP 7.4 that provides a REST API for interacting with a **distributed** Infinispan cache. Cache entries written on one server are automatically visible on all other servers in the cluster — no extra code needed.

---

## How it works (plain English)

JBoss EAP 7.4 ships with Infinispan built in. When you run two EAP servers and configure them to talk to each other via JGroups (a clustering library), Infinispan keeps the cache in sync across both. This application looks up that shared cache via JNDI and exposes it over a simple REST API.

---

## Stack

| Component   | Version       |
|-------------|---------------|
| Spring Boot | 2.7.18        |
| Infinispan  | 11.0.13.Final |
| JBoss EAP   | 7.4           |
| Java        | 11+           |
| Packaging   | WAR           |

---

## Prerequisites

- JBoss EAP 7.4 installed. `$JBOSS_HOME` refers to its root folder (e.g. `/opt/jboss-eap-7.4`).
- Java 11 or later.
- Maven 3.6 or later.
- Two separate server instances. The easiest way on one machine is to copy the `standalone/` directory:

```bash
cp -r $JBOSS_HOME/standalone $JBOSS_HOME/standalone2
```

---

## Step 1 — Build the WAR

```bash
mvn clean package -DskipTests
```

The WAR is produced at `target/spring-infinispan.war`.

---

## Step 2 — Start both servers

Open two terminals.

**Terminal 1 — Server 1** (default ports, JGroups TCP on 7600):
```bash
$JBOSS_HOME/bin/standalone.sh -c standalone-ha.xml \
  -b 127.0.0.1 \
  -bmanagement 127.0.0.1
```

**Terminal 2 — Server 2** (port offset 100, JGroups TCP on 7700):
```bash
$JBOSS_HOME/bin/standalone.sh -c standalone-ha.xml \
  -b 127.0.0.1 \
  -bmanagement 127.0.0.1 \
  -Djboss.socket.binding.port-offset=100 \
  -Djboss.server.base.dir=$JBOSS_HOME/standalone2
```

> The `-c standalone-ha.xml` flag is required. The default `standalone.xml` does not include clustering support.

Port reference:

| Resource   | Server 1 | Server 2 |
|------------|----------|----------|
| HTTP       | 8080     | 8180     |
| Management | 9990     | 10090    |
| JGroups TCP| 7600     | 7700     |

---

## Step 3 — Configure JGroups TCPPing (run on each server)

By default JBoss EAP uses multicast (UDP) for cluster discovery. On localhost, TCP with explicit peer addresses (TCPPing) is more reliable.

Connect to **Server 1**:
```bash
$JBOSS_HOME/bin/jboss-cli.sh --connect --controller=localhost:9990
```

Run the following (the TCP stack already contains a TCPPING protocol — no need to add it):

```
batch

/subsystem=jgroups/channel=ee:write-attribute(name=stack,value=tcp)
/subsystem=jgroups/stack=tcp/protocol=org.jgroups.protocols.TCPPING/property=initial_hosts:add(value="localhost[7600],localhost[7700]")
/subsystem=jgroups/stack=tcp/protocol=org.jgroups.protocols.TCPPING/property=port_range:add(value=0)
/subsystem=jgroups/stack=tcp/protocol=org.jgroups.protocols.TCPPING/property=num_initial_members:add(value=2)

run-batch
```

Then connect to **Server 2** (`--controller=localhost:10090`) and run the exact same batch.

---

## Step 4 — Configure the Infinispan distributed cache (run on each server)

The application looks up the cache under the JNDI name `java:jboss/infinispan/container/custom-app-cache`. You need to create this cache container with a distributed cache named `custom-cache`.

Connect to **Server 1**:
```bash
$JBOSS_HOME/bin/jboss-cli.sh --connect --controller=localhost:9990
```

```
batch

/subsystem=infinispan/cache-container=custom-app-cache:add(default-cache=custom-cache,statistics-enabled=true)
/subsystem=infinispan/cache-container=custom-app-cache/transport=jgroups:add(channel=ee)
/subsystem=infinispan/cache-container=custom-app-cache/distributed-cache=custom-cache:add(mode=SYNC,owners=2,statistics-enabled=true)

run-batch
```

Then do the same on **Server 2** (`--controller=localhost:10090`).

After both batches complete, reload each server:

```
reload
```

---

## Step 5 — Verify the cluster formed

In the CLI of either server:

```
/subsystem=jgroups/channel=ee:read-attribute(name=view)
```

You should see both nodes in the view, for example:
```
"result" => "[jarvis-7600|1] (2) [jarvis-7600, jarvis-7700]"
```

The `(2)` confirms two members. If you see `(1)`, the nodes have not found each other — double-check the `initial_hosts` values in Step 3.

---

## Step 6 — Deploy the WAR to both servers

```bash
# Server 1
$JBOSS_HOME/bin/jboss-cli.sh --connect --controller=localhost:9990 \
  --command="deploy target/spring-infinispan.war --force"

# Server 2
$JBOSS_HOME/bin/jboss-cli.sh --connect --controller=localhost:10090 \
  --command="deploy target/spring-infinispan.war --force"
```

> Do not copy the WAR into the `deployments/` folder if you have already deployed via CLI. Doing both causes a **duplicate deployment error** and the server will fail to boot with `WFLYCTL0212: Duplicate resource`.

---

## Known issue — 500 Internal Server Error on first request

### What happens

On the very first HTTP request after deployment you may see:

```json
{"status": 500, "error": "Internal Server Error"}
```

The server log will show one of two errors depending on which code path was used:

**Error A** (old approach — looking up the cache directly):
```
javax.naming.NameNotFoundException: infinispan/cache/custom-app-cache/custom-cache
Caused by: java.lang.IllegalStateException
    at org.jboss.msc.value.InjectedValue.getValue
```

**Error B** (after switching to the container lookup):
```
org.infinispan.commons.CacheConfigurationException:
ISPN000436: Cache 'custom-cache' has been requested, but no matching cache configuration exists
```

### Why it happens

JBoss EAP 7.4 only supports `start=LAZY` for Infinispan caches (setting it to `EAGER` is rejected). This means:

1. The cache's internal service (MSC `InjectedValue`) is not started at server boot.
2. Looking up `java:jboss/infinispan/cache/custom-app-cache/custom-cache` directly succeeds at finding the JNDI name, but the backing value is null → `IllegalStateException`.
3. Looking up the container (`java:jboss/infinispan/container/custom-app-cache`) succeeds, but calling `getCache("custom-cache")` fails because the cache configuration has not been registered in the manager's `ConfigurationManager` yet (the lazy MSC service never ran).

### How it is fixed in this application

`InfinispanConfig.java` looks up the `EmbeddedCacheManager` from JNDI and checks whether the cache configuration is already registered. If not, it defines it programmatically before calling `getCache()`:

```java
EmbeddedCacheManager manager = (EmbeddedCacheManager) new InitialContext()
        .lookup("java:jboss/infinispan/container/" + containerName);

if (manager.getCacheConfiguration(cacheName) == null) {
    manager.defineConfiguration(cacheName, new ConfigurationBuilder()
            .clustering().cacheMode(CacheMode.DIST_SYNC)
            .hash().numOwners(2)
            .statistics().enabled(true)
            .build());
}
return manager.getCache(cacheName);
```

The `EmbeddedCacheManager` already has the JGroups transport from the subsystem configuration, so caches defined this way are fully distributed across the cluster.

---

## REST API

Base URL: `http://localhost:8080/spring-infinispan/api/cache`

### Add or update an entry

```bash
curl -X PUT http://localhost:8080/spring-infinispan/api/cache/mykey \
  -H "Content-Type: text/plain" \
  -d "hello"
```

Response:
```json
{"status": "stored", "key": "mykey", "value": "hello"}
```

### Retrieve an entry

```bash
curl http://localhost:8080/spring-infinispan/api/cache/mykey
```

Response (found):
```json
{"status": "found", "key": "mykey", "value": "hello"}
```

Response (not found):
```json
{"status": "not_found", "key": "mykey"}
```

### List all entries

```bash
curl http://localhost:8080/spring-infinispan/api/cache
```

Response:
```json
{"size": 1, "entries": {"mykey": "hello"}}
```

### Delete an entry

```bash
curl -X DELETE http://localhost:8080/spring-infinispan/api/cache/mykey
```

Response:
```json
{"status": "removed", "key": "mykey"}
```

### Clear all entries

```bash
curl -X DELETE http://localhost:8080/spring-infinispan/api/cache
```

Response:
```json
{"status": "cleared"}
```

---

## Verifying distribution

This is the key test. Write to Server 1, read from Server 2:

```bash
# Write on Server 1 (port 8080)
curl -X PUT http://localhost:8080/spring-infinispan/api/cache/testkey \
  -H "Content-Type: text/plain" \
  -d "hello-from-server1"

# Read from Server 2 (port 8180) — must return the same value
curl http://localhost:8180/spring-infinispan/api/cache/testkey
```

Expected response from Server 2:
```json
{"status": "found", "key": "testkey", "value": "hello-from-server1"}
```

If Server 2 returns `not_found`, the cluster has not formed correctly. Go back to Step 5 and verify the cluster view shows two members.

---

## Configuration properties

Defined in `src/main/resources/application.properties`:

| Property                    | Default          | Description                              |
|-----------------------------|------------------|------------------------------------------|
| `infinispan.container.name` | `custom-app-cache` | Must match the cache-container name in standalone-ha.xml |
| `infinispan.cache.name`     | `custom-cache`   | Must match the distributed-cache name in standalone-ha.xml |

---

## References

The following official Red Hat and Infinispan documentation was used as reference for the configuration and application changes in this project:

- [EAP 7.4 Configuration Guide — Configuring High Availability](https://docs.redhat.com/en/documentation/red_hat_jboss_enterprise_application_platform/7.4/html/configuration_guide/configuring_high_availability)
  Covers JGroups subsystem setup, TCPPing configuration, and Infinispan distributed cache CLI commands for `standalone-ha.xml`.

- [How to configure TCP-based clustering in EAP 7 / 8](https://access.redhat.com/solutions/3021711)
  Explains switching from UDP to TCP stack and setting `initial_hosts` for localhost clusters using TCPPING.

- [Infinispan subsystem changes between EAP 7.3 and EAP 7.4](https://access.redhat.com/solutions/6735751)
  Documents breaking changes in Infinispan 11 (shipped with EAP 7.4) including `object-memory` → `heap-memory` and `start=LAZY` being the only allowed value.

- [EAP 7.4 Management Model Reference — Infinispan cache-container](https://access.redhat.com/webassets/avalon/d/red_hat_jboss_enterprise_application_platform/7.4/mgmt_model/subsystem/infinispan/cache-container/local-cache/index.html)
  Authoritative reference for all CLI-configurable attributes on cache containers and distributed caches.

- [Red Hat Data Grid — Integration with EAP (JNDI lookup pattern)](https://access.redhat.com/documentation/en-us/red_hat_data_grid/7.2/html/developer_guide/integration_with_eap)
  Documents the `java:jboss/infinispan/container/<name>` JNDI binding for `EmbeddedCacheManager` and the `java:jboss/infinispan/cache/<container>/<cache>` binding for individual caches.

- [Infinispan — Embedding Infinispan in Java Applications](https://infinispan.org/docs/stable/titles/embedding/embedding.html)
  Reference for `EmbeddedCacheManager` API, `defineConfiguration()`, `getCacheConfiguration()`, and `getCache()` used in `InfinispanConfig.java`.
