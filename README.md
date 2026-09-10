# PowSyBl Network Store Server

[![Actions Status](https://github.com/powsybl/powsybl-network-store-server/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/powsybl/powsybl-network-store-server/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=com.powsybl%3Apowsybl-network-store-server&metric=coverage)](https://sonarcloud.io/component_measures?id=com.powsybl%3Apowsybl-network-store-server&metric=coverage)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)
[![Slack](https://img.shields.io/badge/slack-powsybl-blueviolet.svg?logo=slack)](https://join.slack.com/t/powsybl/shared_invite/zt-36jvd725u-cnquPgZb6kpjH8SKh~FWHQ)

## Description

The **powsybl-network-store-server** is a microservice providing an **alternative, database-backed implementation of the PowSyBl Core Network API** ([powsybl-core](https://github.com/powsybl/powsybl-core) IIDM model). Instead of keeping a power network (substations, voltage levels, lines, transformers, generators, ...) in memory only, it **persists it in a PostgreSQL database** and exposes it through a REST API.

It is meant to be used together with its client library, [network-store-client](https://github.com/powsybl/powsybl-network-store), which implements the `com.powsybl.iidm.network.Network` interface on top of this REST API so that any powsybl-core based application can transparently work with a DB-backed network instead of the default in-memory implementation.

It provides the following capabilities:

- **Store and manage networks**: create, read, update and delete networks, including support for multiple **variants** (what-if scenarios cloned from a base variant) per network.
- **Store and manage all IIDM equipment types**: substations, voltage levels (with busbar sections, switches, generators, batteries, loads, shunt compensators, static var compensators, VSC/LCC converter stations, grounds, configured buses), 2/3-winding transformers, lines, tie lines, HVDC lines, dangling (boundary) lines, and areas — full CRUD for each type.
- **Update calculated/state values independently** (`.../sv` endpoints) without rewriting the whole equipment, to optimize computation-result write-backs (e.g. after a load flow).
- **Manage operational limits groups** per branch/side, including the notion of a "selected" limits group.
- **Manage extensions** attached to identifiables (get/delete by identifiable id).
- **Support data model migrations**: expose an endpoint (e.g. `PUT /v1/migration/v237limits/{networkId}/{variantNum}`) to (re-)run, on demand for a specific network/variant, a one-off migration converting data to the current model (e.g. operational limits groups) — the same migration also runs automatically once for all existing networks via a Liquibase custom change at server startup. These endpoints are temporary and removed once every environment has migrated.

---

## Technical Stack

- Spring Boot (Web, Actuator)
- PostgreSQL (via `spring-boot-starter-data-jdbc`, used only for datasource/autoconfiguration — persistence itself is **hand-written SQL/JDBC**, not JPA/Hibernate) + Liquibase (schema management)
- API documentation: OpenAPI / Swagger (`springdoc`)
- Micrometer / Prometheus
- [powsybl-ws-commons](https://github.com/powsybl/powsybl-ws-commons) : shared GridSuite/PowSyBl web-service utilities
- Jib (`com.google.cloud.tools:jib-maven-plugin`) : builds the Docker image without a handwritten Dockerfile

---

## Modules

This repository is a multi-module Maven project:

| Module | Purpose |
|---|---|
| `network-store-server` | The REST/DB server itself (this README's main subject). |
| `network-store-integration-test` | Integration tests running the real server + client stack against a database. |
| `network-store-iidm-tck` | Technology Compatibility Kit: reuses/parallels powsybl-core's IIDM behavioral test suite (e.g. `LineIT`, `VoltageLevelIT`, `NodeBreakerIT`, `ManipulationsOnVariantsIT`, extension tests) to validate that this store's IIDM implementation behaves identically to powsybl-core's reference in-memory implementation. |
| `network-store-tools` | Command-line tools plugged into PowSyBl's [`itools`](https://powsybl.readthedocs.io/projects/powsybl-core/en/stable/user/itools/index.html) framework (auto-discovered, no extra config needed beyond the standard `network-store` module — see [network-store-client configuration](https://github.com/powsybl/powsybl-network-store#standalone--platformconfig-configuration)). See details below. |

The `network-store-client` library itself (the IIDM implementation consuming this server's REST API) lives in a separate module tree, [powsybl-network-store](https://github.com/powsybl/powsybl-network-store).

### `network-store-tools` commands

| Command | Purpose |
|---|---|
| `network-store-import` | Import a network file (CGMES, UCTE, XIIDM, ...) into the store, using any powsybl-core importer. |
| `network-store-list` | List all networks currently stored on the server (UUID and name). |
| `network-store-delete` | Delete a network from the store by UUID. |
| `network-store-script` | Load a stored network and run a Groovy script against it (`network` and `out` bound as script variables) — useful for ad hoc inspection or fixes without writing a dedicated Java tool. |

These tools are primarily meant for local development, CI, and operational tasks (seeding test data, inspecting or cleaning up stored networks, running one-off diagnostic/fix scripts).

---

## Development Scripts

Build Docker image:

```shell
mvn install -DskipTests -Dpowsybl.docker.install
```

Please read [liquibase usage](https://github.com/powsybl/powsybl-parent/#liquibase-usage) for instructions to automatically generate changesets. After you generated a changeset do not forget to add it to git and in `network-store-server/src/main/resources/db/changelog/db.changelog-master.yaml`.

---

## Database

- **PostgreSQL** is the only supported database (`powsybl-ws.database.name: iidm`).
- Persistence is implemented with **plain JDBC** in `NetworkStoreRepository`, backed by a SQL query builder (`QueryCatalog`) and row-mapping utilities (`Mappings`) — not JPA/Hibernate. This choice favors fine-grained control over the (large) batch of SQL statements needed to read/write a full network efficiently.
- **Schema management is fully handled by Liquibase**, which runs automatically at server startup. The master changelog (`db.changelog-master.yaml`) references dated changesets (declarative XML and, when needed, raw SQL migration scripts) added incrementally as the model evolves (tap changers, regulation points, operational limits groups, CGMES extensions, areas, ...).
- The connection pool (HikariCP) is tuned with a higher size than the default (`minimum-idle: 10`, `maximum-pool-size: 20`) since this service is on the critical path of most computations and can be queried heavily, especially when clients preload full equipment collections.

---


## Micrometer Observability

Repository operations (network cloning, bulk removal, limits group retrieval, ...) are wrapped in named Micrometer observations via a `networkStoreObserver`, enabling timing metrics without cluttering the persistence code.

---

## Useful Links

- [PowSyBl Network Store GitHub repository](https://github.com/powsybl/powsybl-network-store) (client library and IIDM model)
- [PowSyBl Core IIDM documentation](https://powsybl.readthedocs.io/projects/powsybl-core/en/stable/grid_model/index.html)
</content>
