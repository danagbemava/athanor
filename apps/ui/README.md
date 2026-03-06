# Athanor UI (Nuxt + shadcn)

## Purpose

Nuxt 3 app for Scenario Studio graph authoring and validation workflows.

## Stack

- Nuxt 3
- Tailwind CSS
- shadcn-vue via `shadcn-nuxt`

## Setup

- Node.js 22+
- Install deps: `npm --prefix apps/ui install`

## Run

- Dev server: `npm --prefix apps/ui run dev`

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
