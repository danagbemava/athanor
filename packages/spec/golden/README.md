# Golden Determinism Corpus

This folder defines canonical scenario fixtures used by deterministic compiler and worker tests.

Each fixture directory contains:

- `manifest.json` with `scenario_source`, `expected_bundle_hash`, and `runs[]`
- `scenario.json` as the raw `ScenarioGraph` source

Test coverage is split across the repo:

- API tests compile each `scenario_source` with the real compiler and assert `expected_bundle_hash` plus schema validity.
- Worker tests load the same `scenario_source`, project it into the runtime bundle shape used by the worker, and assert deterministic outcomes for the listed runs.

The current runtime/compiler support static chance weights, equality guards, and basic state effects. Catalogue scenarios that depend on state-driven probability expressions are intentionally excluded from this pass until expression evaluation lands.
