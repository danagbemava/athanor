# ADR-004: Bundle Content-Addressed Storage
Status: Accepted
Date: 2026-03-05
## Context
Compiled artifacts must be immutable and deduplicated across environments.
## Decision
Use SHA-256 content-addressed bundle hashes stored in S3-compatible object storage.
## Consequences
Storage and retrieval are straightforward; canonical serialization rules become critical.
