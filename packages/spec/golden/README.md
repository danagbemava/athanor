# Golden Determinism Corpus (Scaffold)

This folder defines canonical scenario fixtures used by deterministic integration tests.

Each fixture directory contains:

- `manifest.json` with `scenario_source`, `expected_bundle_hash`, and `runs[]`
- `scenario.json` placeholder source graph

Runtime assertions are scaffold-level and will be strengthened in NEX-196 implementation.
