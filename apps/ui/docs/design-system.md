# Athanor UI Design System

## 1. Purpose

Athanor UI is an authoring and operations product for scenario graph workflows.
The visual system is Geist-inspired: minimal, neutral, precise, and content-first.

The design system prioritizes:

- Fast operator comprehension
- High signal for validation and error states
- Consistent interaction patterns across authoring and diagnostics views
- Quiet UI chrome so scenario data and actions stay dominant

## 2. Foundations

### 2.1 Color tokens (semantic)

All color usage should reference semantic tokens through Tailwind classes (`bg-primary`, `text-muted-foreground`, etc.), not raw hex/HSL values in components.

Color direction:

- Monochrome-first palette for most UI surfaces
- Black/white contrast for primary actions
- Red reserved for destructive/error semantics
- Minimal color accents in product views

| Token | CSS variable | Intent |
| --- | --- | --- |
| Background | `--background` | App/page canvas |
| Foreground | `--foreground` | Primary text |
| Card | `--card` | Elevated surfaces |
| Card Foreground | `--card-foreground` | Text on cards |
| Popover | `--popover` | Floating overlays |
| Popover Foreground | `--popover-foreground` | Text in overlays |
| Primary | `--primary` | Primary call-to-action |
| Primary Foreground | `--primary-foreground` | Text/icons on primary |
| Secondary | `--secondary` | Secondary controls/surfaces |
| Secondary Foreground | `--secondary-foreground` | Text/icons on secondary |
| Muted | `--muted` | Subtle backgrounds |
| Muted Foreground | `--muted-foreground` | Secondary text |
| Accent | `--accent` | Highlighted/support actions |
| Accent Foreground | `--accent-foreground` | Text/icons on accent |
| Destructive | `--destructive` | Errors and destructive actions |
| Destructive Foreground | `--destructive-foreground` | Text/icons on destructive |
| Border | `--border` | Borders/dividers |
| Input | `--input` | Input chrome |
| Ring | `--ring` | Focus ring |

### 2.2 Radius

- Base radius token: `--radius`
- Direction: compact rounded corners (no pill-heavy UI)
- Tailwind radius aliases:
  - `rounded-lg` -> `var(--radius)`
  - `rounded-md` -> `calc(var(--radius) - 2px)`
  - `rounded-sm` -> `calc(var(--radius) - 4px)`

### 2.3 Typography

- Primary sans stack: `var(--font-sans)` (Geist-style sans fallback chain)
- Monospace stack: `var(--font-mono)` for code/raw payload blocks
- Subtle negative tracking on headings and body for a tighter editorial feel
- Use semantic scale from utilities:
  - `text-xs` for labels/meta
  - `text-sm` for body/supporting copy
  - `text-lg` and above for section/page titles

### 2.4 Elevation and effects

- Prefer borders over heavy shadows.
- Use small shadows only on cards that need separation.
- Avoid glassmorphism/backdrop blur and high-opacity overlays.

### 2.5 Theme modes

- Supported modes: `light`, `dark`, and `system`.
- `system` follows OS preference using `prefers-color-scheme`.
- Theme preference is persisted in local storage (`athanor-theme-mode`).
- Dark mode should remain Geist-like: near-black canvas, low-noise borders, and restrained contrast ramps.

## 3. Component system

UI primitives are generated and maintained via `shadcn-vue` into `components/ui`.

Current baseline components:

- `button`
- `input`
- `textarea`
- `card`
- `badge`
- `alert`
- `separator`
- `tabs`

Rules:

- Prefer composition of these primitives over ad-hoc custom controls.
- Keep component variants semantic (`default`, `secondary`, `outline`, `destructive`).
- Preserve accessible defaults (focus ring, disabled state, role semantics).
- Keep motion subtle and purposeful (state changes over decorative animation).

## 4. Layout patterns

- Page shell: neutral canvas with centered max-width product container.
- Workbench content: two-column grid on desktop, stacked on mobile.
- Data-heavy panels use `Card` with clear `CardHeader`/`CardContent` separation.
- Navigation and control panels should be visually stable: low-noise borders, muted backgrounds.

## 5. Interaction patterns

- Primary flow actions:
  - Create draft (primary button)
  - Save new version (secondary button)
  - Validate latest (outline button)
- Runtime status is always visible and paired with API environment context.
- Validation outcomes are separated into:
  - `errors` (destructive styling)
  - `warnings` (amber styling)

## 6. Accessibility requirements

- Visible keyboard focus on all interactive controls (`ring` token).
- Ensure text contrast for all token combinations used in components.
- Do not encode state using color only; pair with text labels/icons.
- Keep tap targets and input heights consistent (`h-9` baseline for dense controls).

## 7. Extending the system

When adding new UI:

1. Add/generate the primitive with `shadcn-vue` when possible.
2. Use existing semantic tokens first; introduce new tokens only for new semantic meaning.
3. Preserve the Geist-inspired direction (neutral-first, concise, low ornament).
4. Update this document when adding tokens, variants, or global layout/interaction rules.
