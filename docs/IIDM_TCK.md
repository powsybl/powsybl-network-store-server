# IIDM TCK

The IIDM network model from powsybl-core is defined through interfaces (`Network`, `Line`,
`Generator`, etc.). The TCK (Technology Compatibility Kit), provided by powsybl-core's
`powsybl-iidm-tck` module, is a suite of abstract tests written against these interfaces,
guaranteeing that an IIDM implementation honors the expected contract (equipment behavior,
variant management, validations, exceptions...).

## How the TCK is used in this repo

The `network-store-iidm-tck` module runs this TCK **end-to-end**: each test class (e.g.
`LineIT extends AbstractLineTest`) runs unmodified, through `RestNetworkStoreClient` → HTTP → a
real `NetworkStoreApplication` (`@SpringBootTest`) → JDBC → an H2 database. Networks are resolved
via powsybl-core's SPI (`NetworkFactoryTestService`, named `"NetworkStoreTest"`). Some tests are
intentionally overridden with an empty body and a `FIXME` comment where this store's behavior
still diverges from powsybl-core's reference implementation.

A different, faster usage of the same TCK also exists in the companion repo
[`powsybl-network-store`](https://github.com/powsybl/powsybl-network-store)
(`network-store-iidm-impl` module), running fully in-memory with no server, HTTP, or database
involved — a cheap unit-level check of the IIDM object model, complementary to this one.

## Limitations

This end-to-end setup does not fully reflect production usage, and has a few known blind spots:

- **No caching or buffering**: `NetworkFactoryTestService` uses a bare `RestNetworkStoreClient`,
  unlike production (`NetworkStoreService`) which wraps it with `CachedNetworkStoreClient` and
  `BufferedNetworkStoreClient`. As a result, reads never go through a cache, and every single
  attribute change triggers its own individual HTTP request instead of being batched on flush.
- **No verification of actual persistence**: equipment getters read from an in-memory object held
  by the client, never from the server, so assertions never confirm that data was correctly
  written to, and re-readable from, the database.
- **Mostly generic model behavior, not storage-specific assertions**: most of the several hundred
  inherited tests check IIDM validation rules and model behavior rather than storage concerns. They still
  provide useful regression coverage of the REST/SQL plumbing (serialization, query correctness),
  but can only catch hard failures (exceptions), not silent data corruption.
- **Slower than the in-memory TCK**: every assertion involves a real HTTP call and SQL round-trip,
  so this module runs noticeably slower than the equivalent in-memory suite, for largely the same
  model-behavior coverage.

Most of these gaps are actually covered **outside of the TCK**: caching and buffering are
unit-tested directly in `network-store-client` (`CachedNetworkStoreClientTest`,
`BufferedNetworkStoreClientTest`, using a mocked REST server) — in the companion repo
[`powsybl-network-store`](https://github.com/powsybl/powsybl-network-store) where that module
lives — and real end-to-end persistence is verified in `network-store-integration-test` (in this
repo), which reloads networks from a fresh `NetworkStoreService` to assert on the actual stored
data.

## Added value

Despite the limitations above, this suite brings real benefits:

- **Behavioral conformance for free**: `network-store-iidm-impl` reimplements the IIDM object
  model independently from powsybl-core (topology computation, bus/breaker and bus views,
  connected components, validation rules...). Reusing powsybl-core's own several hundred tests,
  rather than writing and maintaining an equivalent suite from scratch, is a large maintenance win,
  and keeps this implementation aligned as powsybl-core's reference behavior evolves over time.
- **Genuine storage-specific coverage for variant management**: unlike most other operations,
  variant cloning/removal is not just generic model logic — `VariantManagerImpl` delegates to
  `NetworkStoreClient.cloneNetwork()`, which performs real duplication of stored data. Running the
  TCK's variant-related tests (e.g. `ManipulationsOnVariantsIT`) end-to-end does exercise and
  validate this storage-specific behavior, not just the generic IIDM model.
- **Broad regression coverage of the REST/SQL plumbing**: because every operation genuinely goes
  through `RestNetworkStoreClient` → HTTP → `NetworkStoreController` → `NetworkStoreRepository` →
  SQL, this suite exercises JSON (de)serialization and SQL query correctness across a far wider and
  more realistic variety of data shapes (every equipment type, every topology kind, extensions,
  limits...) than a hand-written storage-focused test suite would typically cover. Any wiring,
  mapping, or schema regression causing an exception is likely to be caught.
- **Cross-version/contract safety net**: running the client and server together, end-to-end, also
  indirectly checks that both sides of the system still agree on the same REST contract and data
  model — something a purely unit-tested client or purely unit-tested server could each miss on
  their own.
