# Athanor UI (Scaffold)

## Purpose

Nuxt 3 scaffold for Scenario Studio graph authoring and analytics.

## Setup

- Node.js 22+
- Install deps: `npm --prefix apps/ui install`

## Run

- `npm --prefix apps/ui run dev`

## Quality Gates

- Lint: `make ui-lint`
- Test: `make ui-test`
- Build: `make ui-build`

## Notes

- Type checking is enforced via `nuxi typecheck`.
- Canvas editor and simulation panel are deferred to Phase 1/2 issues.
