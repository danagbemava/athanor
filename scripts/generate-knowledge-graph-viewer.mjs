import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, "..");
const dotPath = path.join(repoRoot, "docs/architecture/knowledge-graph.dot");
const jsonPath = path.join(repoRoot, "docs/architecture/knowledge-graph-data.json");
const htmlPath = path.join(repoRoot, "docs/architecture/knowledge-graph.html");

function parseAttributes(attributeText) {
  const attributes = {};
  if (!attributeText) return attributes;

  for (const segment of attributeText
    .split(/,(?=(?:[^"]*"[^"]*")*[^"]*$)/)
    .map((part) => part.trim())
    .filter(Boolean)) {
    const separatorIndex = segment.indexOf("=");
    if (separatorIndex === -1) continue;

    const key = segment.slice(0, separatorIndex).trim();
    let value = segment.slice(separatorIndex + 1).trim();
    if (value.startsWith('"') && value.endsWith('"')) {
      value = value.slice(1, -1);
    }

    attributes[key] = value;
  }

  return attributes;
}

function extractClusterBlocks(dot) {
  const clusters = [];
  const clusterPattern = /subgraph\s+cluster_([A-Za-z0-9_]+)\s*\{/g;

  for (let match = clusterPattern.exec(dot); match; match = clusterPattern.exec(dot)) {
    const clusterName = match[1];
    let index = clusterPattern.lastIndex;
    let depth = 1;
    let inQuote = false;
    let escaped = false;

    while (index < dot.length && depth > 0) {
      const char = dot[index];
      index += 1;

      if (escaped) {
        escaped = false;
        continue;
      }

      if (char === "\\") {
        escaped = true;
        continue;
      }

      if (char === '"') {
        inQuote = !inQuote;
        continue;
      }

      if (inQuote) continue;
      if (char === "{") depth += 1;
      if (char === "}") depth -= 1;
    }

    clusters.push({
      name: clusterName,
      body: dot.slice(clusterPattern.lastIndex, index - 1),
    });
  }

  return clusters;
}

function extractClusterLabel(body, fallbackLabel) {
  const labelMatch = body.match(/^\s*label\s*=\s*"([^"]+)"\s*;?\s*$/m);
  return labelMatch ? labelMatch[1] : fallbackLabel;
}

function serializeEmbeddedData(embeddedData) {
  const json =
    typeof embeddedData === "string"
      ? embeddedData
      : JSON.stringify(embeddedData, null, 2);

  return json.replace(/[<>&]/g, (character) => {
    switch (character) {
      case "<":
        return "\\u003c";
      case ">":
        return "\\u003e";
      case "&":
        return "\\u0026";
      default:
        return character;
    }
  });
}

export function parseKnowledgeGraphDot(dot) {
  const clusters = [];
  const nodes = [];
  const edges = [];

  for (const { name, body } of extractClusterBlocks(dot)) {
    const label = extractClusterLabel(body, name);
    clusters.push({ id: name, label });

    for (const nodeMatch of body.matchAll(
      /^\s*([A-Za-z0-9_]+)\s+\[(.*?)\];\s*$/gm,
    )) {
      const [, id, attributeText] = nodeMatch;
      const attributes = parseAttributes(attributeText);
      if (!attributes.label) continue;
      nodes.push({
        id,
        label: attributes.label,
        cluster: label,
      });
    }
  }

  for (const edgeMatch of dot.matchAll(
    /^\s*([A-Za-z0-9_]+)\s*->\s*([A-Za-z0-9_]+)\s+\[(.*?)\];\s*$/gm,
  )) {
    const [, source, target, attributeText] = edgeMatch;
    const attributes = parseAttributes(attributeText);
    edges.push({
      source,
      target,
      label: attributes.label ?? "",
      style: attributes.style ?? null,
    });
  }

  return { clusters, nodes, edges };
}

export function deriveTags({ label, edgeLabels }) {
  const haystack = `${label} ${edgeLabels.join(" ")}`.toLowerCase();
  const tags = new Set();

  if (label.startsWith("UI route:")) tags.add("route");
  if (haystack.includes("redis") || haystack.includes("dispatch stream")) {
    tags.add("redis");
  }
  if (
    haystack.includes("optimize") ||
    haystack.includes("optimization") ||
    haystack.includes("candidate")
  ) {
    tags.add("optimization");
  }
  if (haystack.includes("telemetry") || haystack.includes("analytics")) {
    tags.add("telemetry");
  }
  if (haystack.includes("validate")) tags.add("validation");
  if (
    haystack.includes("bundle") ||
    haystack.includes("minio") ||
    haystack.includes("object store")
  ) {
    tags.add("bundle");
  }

  return [...tags];
}

function inferKind(label) {
  if (label.startsWith("UI route:")) return "route";
  if (label.includes("Controller")) return "controller";
  if (label.includes("Service")) return "service";
  if (label.includes("Store")) return "store";
  if (label.includes("stream")) return "stream";
  if (label.startsWith("UI:")) return "ui-surface";
  return "node";
}

function extractRoute(label) {
  if (!label.startsWith("UI route:")) return null;
  return label.slice("UI route:".length).trim();
}

export function buildGraphModel(dot, { sourcePath = "docs/architecture/knowledge-graph.dot" } = {}) {
  const parsed = parseKnowledgeGraphDot(dot);
  const edgesByNode = new Map();

  for (const node of parsed.nodes) {
    edgesByNode.set(node.id, { incoming: [], outgoing: [] });
  }

  for (const edge of parsed.edges) {
    edgesByNode.get(edge.source)?.outgoing.push(edge);
    edgesByNode.get(edge.target)?.incoming.push(edge);
  }

  const nodes = parsed.nodes.map((node) => {
    const related = edgesByNode.get(node.id) ?? { incoming: [], outgoing: [] };
    const edgeLabels = [...related.incoming, ...related.outgoing].map(
      (edge) => edge.label,
    );

    return {
      id: node.id,
      label: node.label,
      cluster: node.cluster,
      kind: inferKind(node.label),
      route: extractRoute(node.label),
      tags: deriveTags({ label: node.label, edgeLabels }),
      incoming: related.incoming.map((edge) => ({
        source: edge.source,
        label: edge.label,
      })),
      outgoing: related.outgoing.map((edge) => ({
        target: edge.target,
        label: edge.label,
      })),
    };
  });

  const edges = parsed.edges.map((edge) => ({
    ...edge,
    tags: deriveTags({ label: edge.label, edgeLabels: [edge.label] }),
  }));

  return {
    source: sourcePath,
    generatedAt: new Date().toISOString(),
    clusters: parsed.clusters,
    nodes,
    edges,
  };
}

export function renderHtml({
  embeddedData = null,
  dataUrl = "./knowledge-graph-data.json",
} = {}) {
  const embeddedDataMarkup =
    embeddedData === null
      ? ""
      : `    <script id="knowledge-graph-data" type="application/json">\n${serializeEmbeddedData(embeddedData)}\n    </script>\n`;

  return `<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width,initial-scale=1" />
    <title>Athanor Searchable Knowledge Graph</title>
    <script src="https://unpkg.com/cytoscape@3.29.2/dist/cytoscape.min.js"></script>
    <style>
      :root {
        color-scheme: light;
        --bg: #f6f7f9;
        --panel: #ffffff;
        --border: #d8dde6;
        --text: #172033;
        --muted: #5f6b85;
        --accent: #0f766e;
        --accent-soft: #d7f3ef;
      }
      * { box-sizing: border-box; }
      body { margin: 0; font: 14px/1.4 system-ui, sans-serif; color: var(--text); background: var(--bg); }
      .layout { display: grid; grid-template-columns: 340px 1fr; min-height: 100vh; }
      .sidebar { border-right: 1px solid var(--border); background: var(--panel); padding: 16px; overflow: auto; }
      .canvas { position: relative; min-height: 100vh; }
      #graph { position: absolute; inset: 0; }
      h1,h2,h3 { margin: 0 0 12px; }
      .group { margin-bottom: 18px; }
      .muted { color: var(--muted); }
      .count { display: inline-block; margin-left: 8px; padding: 2px 8px; border-radius: 999px; background: #ecf2f7; color: var(--muted); font-size: 12px; }
      input[type="search"] { width: 100%; padding: 10px 12px; border: 1px solid var(--border); border-radius: 10px; }
      .chips { display: flex; flex-wrap: wrap; gap: 8px; }
      .chip { border: 1px solid var(--border); background: #fff; border-radius: 999px; padding: 6px 10px; cursor: pointer; }
      .chip.active { border-color: var(--accent); background: var(--accent-soft); color: var(--accent); }
      .detail-list { padding-left: 18px; margin: 8px 0 0; }
      .detail-grid { display: grid; gap: 4px; }
      .summary { font-size: 13px; color: var(--muted); }
      .code { font-family: ui-monospace, SFMono-Regular, monospace; }
      .pill { display: inline-block; margin: 2px 6px 0 0; padding: 2px 8px; border-radius: 999px; background: #eef6ff; color: #244266; font-size: 12px; }
    </style>
  </head>
  <body>
    <div class="layout">
      <aside class="sidebar">
        <div class="group">
          <h1>Athanor Knowledge Graph</h1>
          <p class="muted">Search exact nodes and richer categories, then inspect relationships in the graph.</p>
        </div>
        <div class="group">
          <input id="search" type="search" placeholder="Search nodes, routes, tags..." />
        </div>
        <div class="group">
          <h2>Clusters</h2>
          <div id="cluster-filters" class="chips"></div>
        </div>
        <div class="group">
          <h2>Tags</h2>
          <div id="tag-filters" class="chips"></div>
        </div>
        <div class="group">
          <h2>Routes</h2>
          <div id="route-filters" class="chips"></div>
        </div>
        <div class="group">
          <h2>Summary</h2>
          <div id="summary" class="summary"></div>
        </div>
        <div class="group">
          <h2>Selected Node</h2>
          <div id="details" class="muted">Select a node to inspect inbound and outbound relationships.</div>
        </div>
      </aside>
      <main class="canvas">
        <div id="graph"></div>
      </main>
    </div>
${embeddedDataMarkup}    <script type="module">
      let graphData = null;
      const embeddedDataElement = document.getElementById("knowledge-graph-data");
      const embeddedData = embeddedDataElement?.textContent?.trim();

      if (embeddedData) {
        try {
          graphData = JSON.parse(embeddedData);
        } catch (error) {
          console.warn("Failed to parse embedded knowledge graph data; falling back to fetch.", error);
        }
      }

      if (!graphData) {
        const response = await fetch(${JSON.stringify(dataUrl)});
        graphData = await response.json();
      }

      const state = {
        search: "",
        clusters: new Set(),
        tags: new Set(),
        routes: new Set(),
      };

      const clusterRoot = document.getElementById("cluster-filters");
      const tagRoot = document.getElementById("tag-filters");
      const routeRoot = document.getElementById("route-filters");
      const detailsRoot = document.getElementById("details");
      const summaryRoot = document.getElementById("summary");
      const searchInput = document.getElementById("search");

      function allTags() {
        return [...new Set(graphData.nodes.flatMap((node) => node.tags))].sort();
      }

      function allRoutes() {
        return [...new Set(graphData.nodes.filter((node) => node.route).map((node) => node.route))].sort();
      }

      function visibleRoutes() {
        if (state.routes.size === 0) return null;
        const routeNodes = graphData.nodes.filter((node) => state.routes.has(node.route));
        const connected = new Set(routeNodes.map((node) => node.id));

        for (const node of routeNodes) {
          for (const edge of node.incoming) connected.add(edge.source);
          for (const edge of node.outgoing) connected.add(edge.target);
        }

        return connected;
      }

      function passesFilters(node, routeVisible) {
        const haystack = [node.label, node.cluster, node.kind, node.route ?? "", ...node.tags]
          .join(" ")
          .toLowerCase();
        const searchOk = state.search === "" || haystack.includes(state.search);
        const clusterOk = state.clusters.size === 0 || state.clusters.has(node.cluster);
        const tagOk = state.tags.size === 0 || [...state.tags].every((tag) => node.tags.includes(tag));
        const routeOk =
          routeVisible === null || routeVisible.has(node.id) || state.routes.has(node.route);
        return searchOk && clusterOk && tagOk && routeOk;
      }

      const cy = cytoscape({
        container: document.getElementById("graph"),
        elements: [
          ...graphData.nodes.map((node) => ({
            data: {
              id: node.id,
              label: node.label,
              cluster: node.cluster,
              tags: node.tags,
              kind: node.kind,
            },
          })),
          ...graphData.edges.map((edge, index) => ({
            data: {
              id: \`edge-\${index}\`,
              source: edge.source,
              target: edge.target,
              label: edge.label,
              style: edge.style ?? "solid",
            },
          })),
        ],
        style: [
          {
            selector: "node",
            style: {
              "background-color": "#0f766e",
              label: "data(label)",
              color: "#172033",
              "font-size": 10,
              "text-valign": "bottom",
              "text-margin-y": 6,
              width: 18,
              height: 18,
            },
          },
          {
            selector: "edge",
            style: {
              width: 2,
              "line-color": "#8fa1b8",
              "target-arrow-color": "#8fa1b8",
              "target-arrow-shape": "triangle",
              "curve-style": "bezier",
              label: "data(label)",
              "font-size": 8,
              color: "#4b5870",
              "text-background-color": "#ffffff",
              "text-background-opacity": 0.85,
              "text-background-padding": 2,
            },
          },
          { selector: 'edge[style = "dashed"]', style: { "line-style": "dashed" } },
          { selector: ".dimmed", style: { opacity: 0.16 } },
          {
            selector: ".highlight",
            style: {
              "background-color": "#c2410c",
              "line-color": "#c2410c",
              "target-arrow-color": "#c2410c",
              opacity: 1,
            },
          },
        ],
        layout: { name: "cose", fit: true, padding: 30 },
      });

      function renderChips(root, values, selectedSet) {
        root.innerHTML = "";
        for (const value of values) {
          const button = document.createElement("button");
          button.className = "chip" + (selectedSet.has(value) ? " active" : "");
          button.textContent = value;
          button.type = "button";
          button.onclick = () => {
            if (selectedSet.has(value)) selectedSet.delete(value);
            else selectedSet.add(value);
            renderAll();
          };
          root.appendChild(button);
        }
      }

      function renderDetails(nodeId = null) {
        if (!nodeId) {
          detailsRoot.innerHTML =
            '<div class="muted">Select a node to inspect inbound and outbound relationships.</div>';
          return;
        }
        const node = graphData.nodes.find((entry) => entry.id === nodeId);
        if (!node) return;
        const incomingHtml = node.incoming
          .map((edge) => \`<li><span class="code">\${edge.source}</span> via \${edge.label}</li>\`)
          .join("");
        const outgoingHtml = node.outgoing
          .map((edge) => \`<li><span class="code">\${edge.target}</span> via \${edge.label}</li>\`)
          .join("");
        detailsRoot.innerHTML = \`
          <div><strong>\${node.label}</strong></div>
          <div class="summary">Cluster: \${node.cluster}</div>
          <div class="summary">Kind: \${node.kind}</div>
          <div class="summary">Route: \${node.route ?? "none"}</div>
          <div class="summary">Tags: \${node.tags.join(", ") || "none"}</div>
          <div style="margin-top:8px;">
            \${node.tags.map((tag) => \`<span class="pill">\${tag}</span>\`).join("")}
          </div>
          <h3 style="margin-top:12px;">Incoming</h3>
          <ul class="detail-list">\${incomingHtml || "<li>None</li>"}</ul>
          <h3 style="margin-top:12px;">Outgoing</h3>
          <ul class="detail-list">\${outgoingHtml || "<li>None</li>"}</ul>
        \`;
      }

      function renderAll() {
        renderChips(clusterRoot, graphData.clusters.map((cluster) => cluster.label), state.clusters);
        renderChips(tagRoot, allTags(), state.tags);
        renderChips(routeRoot, allRoutes(), state.routes);

        const routeVisible = visibleRoutes();
        const visibleNodeIds = new Set(
          graphData.nodes.filter((node) => passesFilters(node, routeVisible)).map((node) => node.id),
        );
        cy.nodes().forEach((node) => {
          node.toggleClass("dimmed", !visibleNodeIds.has(node.id()));
          node.removeClass("highlight");
        });
        cy.edges().forEach((edge) => {
          const visible =
            visibleNodeIds.has(edge.source().id()) && visibleNodeIds.has(edge.target().id());
          edge.toggleClass("dimmed", !visible);
          edge.removeClass("highlight");
        });

        const visibleEdges = graphData.edges.filter(
          (edge) => visibleNodeIds.has(edge.source) && visibleNodeIds.has(edge.target),
        ).length;
        summaryRoot.textContent = \`\${visibleNodeIds.size} nodes visible, \${visibleEdges} edges visible, generated \${new Date(graphData.generatedAt).toLocaleString()}\`;
      }

      searchInput.addEventListener("input", (event) => {
        state.search = event.target.value.trim().toLowerCase();
        renderAll();
      });

      cy.on("tap", "node", (event) => {
        const node = event.target;
        cy.elements().removeClass("highlight");
        node.addClass("highlight");
        node.incomers("edge").addClass("highlight");
        node.outgoers("edge").addClass("highlight");
        node.connectedEdges().connectedNodes().addClass("highlight");
        renderDetails(node.id());
      });

      cy.on("tap", (event) => {
        if (event.target === cy) {
          cy.elements().removeClass("highlight");
          renderDetails(null);
        }
      });

      renderAll();
    </script>
  </body>
</html>`;
}

export async function generateKnowledgeGraphArtifacts({
  dotFilePath = dotPath,
  jsonFilePath = jsonPath,
  htmlFilePath = htmlPath,
} = {}) {
  const dot = await fs.readFile(dotFilePath, "utf8");
  const sourcePath = path.relative(repoRoot, dotFilePath).replaceAll(path.sep, "/");
  const dataUrl = path
    .relative(path.dirname(htmlFilePath), jsonFilePath)
    .replaceAll(path.sep, "/");
  const model = buildGraphModel(dot, { sourcePath });
  await fs.writeFile(jsonFilePath, `${JSON.stringify(model, null, 2)}\n`, "utf8");
  await fs.writeFile(
    htmlFilePath,
    renderHtml({ embeddedData: model, dataUrl }),
    "utf8",
  );
  return model;
}

async function main() {
  await generateKnowledgeGraphArtifacts();
}

export function shouldRunAsMainModule(argv1 = process.argv[1]) {
  return typeof argv1 === "string" && import.meta.url === pathToFileURL(argv1).href;
}

if (shouldRunAsMainModule()) {
  main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
}
