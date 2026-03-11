# Athanor UI (Nuxt + shadcn)

## Purpose

Nuxt 3 app for Scenario Studio workflows, including:

- graph authoring and editing
- scenario validation and versioning
- simulation submission, progress, and result inspection
- optimization submission, progress, and parameter application

## Stack

- Nuxt 3
- Tailwind CSS
- shadcn-vue via `shadcn-nuxt`

## Setup

- Node.js 22+
- Install deps: `npm --prefix apps/ui install`

## Run

- Dev server: `npm --prefix apps/ui run dev`
- Typical local API override: `NUXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8080 npm --prefix apps/ui run dev -- --host 127.0.0.1 --port 3001`

## Quality Gates

- Lint/typecheck: `make ui-lint`
- Tests: `make ui-test`
- Production build: `make ui-build`

## Design System

- Source of truth: `apps/ui/docs/design-system.md`
- Theme tokens: `apps/ui/assets/css/tailwind.css`
- UI primitives: `apps/ui/components/ui/*`

## Notes

- `components.json` configures shadcn-vue generation for this app.
- API base URL defaults to `http://localhost:8080`.
- Override API URL with `NUXT_PUBLIC_API_BASE_URL`.
- The main authoring surface is the builder page, which drives both simulation and optimization flows against the API.
