# IIDM TCK

The IIDM network model from powsybl-core is defined through **interfaces** (`Network`, `Line`,
`Generator`, etc., in `powsybl-iidm-api`). The TCK (Technology Compatibility Kit) is a suite of
tests, written solely against these interfaces, that guarantees an IIDM implementation honors the
expected contract (equipment behavior, variant management, validations, exceptions...). Powsybl-core
provides this TCK through the `powsybl-iidm-tck` module, which exposes abstract test classes
(`AbstractLineTest`, `AbstractGeneratorTest`, ...) packaged as a *test-jar* and reusable by any IIDM
implementation.

See also the [`powsybl-network-store`](https://github.com/powsybl/powsybl-network-store#iidm-tck)
README for how the same TCK is also run fully in-memory, at the `network-store-iidm-impl` unit test
level.

## How the TCK is used in this repo

The `network-store-iidm-tck` module runs this TCK **end-to-end**: each test class (e.g.
`LineIT extends AbstractLineTest`) runs as-is, through `RestNetworkStoreClient` → HTTP → a
real `NetworkStoreApplication` (`@SpringBootTest`) → JDBC → an H2 database. Networks are resolved
via powsybl-core's SPI (`NetworkFactoryTestService`, named `"NetworkStoreTest"`). Some tests are
intentionally overridden with an empty body and a `FIXME` comment where this store's behavior
still diverges from powsybl-core's reference implementation.

## Limitations

This end-to-end setup does not fully reflect production usage, and has a few known blind spots:

- **Partial caching, no buffering**: `NetworkFactoryTestService` uses a bare `RestNetworkStoreClient`,
  unlike production (`NetworkStoreService`) which wraps it with `CachedNetworkStoreClient` and
  `BufferedNetworkStoreClient`. The client-side object index (`NetworkObjectIndex`, always present)
  already caches single-object reads by id — e.g. repeated calls to `getGenerator("id").getP()` are
  served from memory, not re-fetched. What's missing here is the response-level cache: fetching a
  whole collection (e.g. `getGenerators()`) always goes back to the server, and writes are not
  batched — every attribute change triggers its own individual HTTP request instead of being grouped
  on flush.
- **Limited verification of actual persistence**: once an equipment has been created or loaded, its
  getters read from the in-memory object held by `NetworkObjectIndex`, never from the server again.
  So even after writing an attribute and flushing, nothing in a typical test actually re-reads it back
  from the database — a bug that silently corrupts or drops data on the server side (bad SQL mapping,
  wrong column...) would not be caught, since every subsequent assertion is served from the client-side
  cache rather than a fresh server round-trip. Variant-switching is the main exception: it forces every
  already-loaded object to be re-fetched from the server, so tests exercising variant changes (e.g.
  `ManipulationsOnVariantsIT`) do incidentally re-verify real persistence.
- **Slower than the in-memory TCK**: the first load or write of each resource still involves a real
  HTTP call and SQL round-trip (the in-memory TCK has neither) — so this module runs noticeably
  slower overall, even though later assertions on an already-cached object add no extra cost, for
  largely the same model-behavior coverage.

Most of these gaps are actually covered **outside of the TCK**: caching and buffering are
unit-tested directly in `network-store-client` (`CachedNetworkStoreClientTest`,
`BufferedNetworkStoreClientTest`, using a mocked REST server) — in the companion repo
[`powsybl-network-store`](https://github.com/powsybl/powsybl-network-store) where that module
lives — and real end-to-end persistence is verified in `network-store-integration-test` (in this
repo), which reloads networks from a fresh `NetworkStoreService` to assert on the actual stored
data.

## Added value

Despite the limitations above, running the TCK this way brings real benefits:

- **Behavioral conformance for free**: `network-store-iidm-impl` reimplements the IIDM object
  model independently from powsybl-core (topology computation, bus/breaker and bus views,
  connected components, validation rules...). Reusing powsybl-core's own several hundred tests,
  rather than writing and maintaining an equivalent suite from scratch, is a large maintenance win,
  and keeps this implementation aligned as powsybl-core's reference behavior evolves over time. Most
  of this conformance already surfaces at the client level, through the faster in-memory TCK in
  `network-store-iidm-impl` (see the companion repo above); running it again here, end-to-end, is
  mainly a safety net against server-side changes (e.g. a migration or serialization bug) silently
  breaking model behavior that the client-only run wouldn't catch.
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
