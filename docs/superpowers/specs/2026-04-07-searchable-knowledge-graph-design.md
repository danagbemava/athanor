# Searchable Knowledge Graph Design

## Goal

Add a standalone, searchable knowledge-graph artifact that is generated from the existing Athanor graph sources and committed to the repository.

The new artifact should make it easy to:

- search for exact nodes like `JobService`, `TelemetryController`, or `/simulate`
- filter by broader concerns like `redis`, `optimization`, `telemetry`, `worker`, or `ui`
- inspect inbound and outbound relationships for a selected node
- keep the interactive graph aligned with the authored DOT graph through a manual generation script

## Outputs

The implementation will add these repository artifacts:

- `docs/architecture/knowledge-graph.html`
- `docs/architecture/knowledge-graph-data.json`

The implementation will also add a repo script that regenerates them from the DOT source:

- `scripts/generate-knowledge-graph-viewer.mjs`

Optional supporting documentation updates may be made to:

- `docs/architecture/knowledge-graph.md`

## Source Of Truth

The authored graph source remains:

- `docs/architecture/knowledge-graph.dot`

The interactive viewer is a generated derivative. It should not become a second hand-maintained graph definition.

## Recommended Approach

Use a generator script that parses the DOT graph into a normalized JSON model and writes a standalone HTML viewer that loads the generated JSON.

This is preferred over trying to search a rendered SVG because:

- richer filtering depends on explicit node and edge metadata
- DOT labels can be normalized into searchable tags
- the viewer can remain standalone while still being deterministic
- the generation step becomes the single mechanism for keeping the viewer current

## Scope

### Include

- Standalone HTML artifact under `docs/architecture/`
- Split layout with sidebar plus graph canvas
- Text search across node labels, route labels, and derived tags
- Filter controls for clusters and derived tags
- Detail panel for selected nodes and their relationships
- Manual generator script that refreshes the HTML and JSON artifacts

### Exclude

- Automatic pre-push or CI hook execution
- Live repository introspection beyond the authored DOT graph
- Full natural-language semantic search
- Editing the graph from the viewer
- Making the UI app host or own the viewer

## Artifact Model

### 1. DOT Input

The generator consumes `docs/architecture/knowledge-graph.dot`.

It should parse:

- node identifiers
- display labels
- cluster membership
- edge endpoints
- edge labels
- edge styling hints where relevant

### 2. Normalized JSON Output

The generator writes `docs/architecture/knowledge-graph-data.json`.

Each node record should include:

- stable ID from the DOT node name
- display label
- cluster
- optional kind inferred from label, such as `route`, `service`, `controller`, `store`, `stream`, `ui-surface`
- derived tags
- incoming edge references
- outgoing edge references

Each edge record should include:

- source node ID
- target node ID
- display label
- optional style metadata
- derived tags

Top-level JSON should also include:

- cluster metadata
- generation timestamp
- source file path

### 3. Standalone HTML Output

The generator writes `docs/architecture/knowledge-graph.html`.

The HTML should:

- be a repo artifact, not a framework route
- use a split layout
- load graph data from the generated JSON file
- use CDN-hosted browser libraries where useful
- remain usable when opened directly in a browser from the filesystem or a simple local server

## Viewer Design

### Layout

Use a split layout:

- left sidebar for search, filters, and selected-node details
- right canvas for the interactive graph

### Sidebar

The sidebar should include:

- free-text search input
- cluster filters, such as `UI`, `API`, `Worker`, `Shared Contracts`, `Infrastructure`
- tag filters, such as `redis`, `optimization`, `telemetry`, `simulation`, `validation`, `bundle`, `route`
- node count and visible edge count summary
- selected-node details

### Canvas

The canvas should support:

- zoom and pan
- click selection
- highlighting of matched nodes
- dimming of non-matching nodes
- highlighting inbound and outbound neighbors for the selected node

### Selected Node Panel

When a node is selected, show:

- node label
- cluster
- tags
- incoming edges
- outgoing edges
- directly connected node labels

## Derived Search Model

Because the DOT graph does not explicitly store rich tags, the generator should derive them from labels and edge text.

Examples:

- labels containing `Redis` or `dispatch stream` -> tag `redis`
- labels containing `optimize`, `optimization`, or `candidate` -> tag `optimization`
- labels containing `telemetry`, `analytics` -> tag `telemetry`
- labels containing `simulate`, `runs`, `trace` -> tag `simulation`
- labels containing `validate` -> tag `validation`
- labels containing `bundle`, `MinIO`, `object store` -> tag `bundle`
- labels beginning with `UI route:` -> tag `route`

The derived-tag rules should be deterministic and local to the generator script.

## Technology Choice

Use a lightweight browser graph library loaded from a CDN.

The exact library can be chosen during implementation, but it should support:

- client-side graph rendering
- node and edge metadata
- programmatic filtering/highlighting
- pan and zoom

Suitable examples would be Cytoscape.js or Vis Network. The implementation should choose the simplest option that cleanly supports the required sidebar-driven filtering.

## Generation Workflow

Manual workflow:

1. Update `docs/architecture/knowledge-graph.dot` as needed.
2. Run the generator script from the repo root.
3. Commit the refreshed HTML and JSON artifacts.

The script should be designed so it can later be called from other workflows, but manual execution is the only requirement for now.

## Script Behavior

The generator script should:

1. Read `docs/architecture/knowledge-graph.dot`
2. Parse clusters, nodes, and edges
3. Derive tags and node kinds
4. Write `docs/architecture/knowledge-graph-data.json`
5. Write `docs/architecture/knowledge-graph.html`
6. Exit non-zero if parsing or writing fails

The script should be deterministic so repeated runs with unchanged DOT input produce stable output.

## Documentation Updates

The Markdown file should briefly explain:

- that a searchable standalone viewer exists
- where the generated HTML and JSON live
- which script regenerates them
- how to open the HTML artifact

This should remain short; the main operational detail belongs in the generated viewer and generator usage block.

## Verification

Implementation is correct if:

- the generator script runs successfully from the repo root
- the JSON artifact contains nodes, edges, clusters, and tags derived from the DOT source
- the HTML artifact opens as a standalone page and loads the generated JSON
- users can search exact labels and broader categories like `redis` or `optimization`
- selecting a node reveals its immediate relationships in the sidebar
- the generated artifacts change when the DOT graph changes

## Risks

- DOT parsing can be brittle if implemented too loosely. Mitigation: support the current repository graph shape precisely rather than trying to implement a full DOT parser.
- A CDN-based viewer can break without network access. Mitigation: accept CDN dependency as part of scope and document it clearly.
- The HTML may become stale if the generator is not rerun. Mitigation: keep the generated artifact obviously derived from the DOT source and document the refresh script near the top of the Markdown file.

## Implementation Notes

- This is a documentation/tooling change only.
- The interactive artifact should remain outside the Nuxt app.
- The generator should not inspect live git status or infer architecture changes from the repository beyond the authored DOT graph.
