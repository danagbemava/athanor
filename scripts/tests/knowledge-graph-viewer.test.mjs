import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";

import {
  generateKnowledgeGraphArtifacts,
  deriveTags,
  renderHtml,
  parseKnowledgeGraphDot,
  shouldRunAsMainModule,
} from "../generate-knowledge-graph-viewer.mjs";

test("parseKnowledgeGraphDot extracts clusters, nodes, and edges", () => {
  const dot = `
digraph athanor_knowledge_graph {
  subgraph cluster_ui {
    label="UI";
    builder [label="UI: builder.vue"];
  }
  subgraph cluster_api {
    label="API";
    service [label="API: JobService"];
  }
  builder -> service [label="HTTP /simulate"];
}
`;

  const graph = parseKnowledgeGraphDot(dot);

  assert.equal(graph.clusters.length, 2);
  assert.deepEqual(
    graph.clusters.map((cluster) => cluster.label),
    ["UI", "API"],
  );
  assert.deepEqual(
    graph.nodes.map((node) => [node.id, node.cluster, node.label]),
    [
      ["builder", "UI", "UI: builder.vue"],
      ["service", "API", "API: JobService"],
    ],
  );
  assert.deepEqual(graph.edges, [
    {
      source: "builder",
      target: "service",
      label: "HTTP /simulate",
      style: null,
    },
  ]);
});

test("parseKnowledgeGraphDot resolves cluster labels regardless of statement order", () => {
  const dot = `
digraph athanor_knowledge_graph {
  subgraph cluster_ui {
    builder [label="UI: builder.vue"];
    label="UI";
  }
  subgraph cluster_api {
    service [label="API: JobService"];
    label="API";
  }
}
`;

  const graph = parseKnowledgeGraphDot(dot);

  assert.deepEqual(
    graph.clusters.map((cluster) => cluster.label),
    ["UI", "API"],
  );
  assert.deepEqual(
    graph.nodes.map((node) => [node.id, node.cluster]),
    [
      ["builder", "UI"],
      ["service", "API"],
    ],
  );
});

test("deriveTags assigns route, redis, optimization, telemetry, validation, and bundle tags", () => {
  assert.deepEqual(
    deriveTags({
      label: "UI route: /optimize/{jobId}",
      edgeLabels: ["GET /optimize/{jobId}", "Redis dispatch stream", "validate graph"],
    }).sort(),
    ["optimization", "redis", "route", "validation"],
  );

  assert.deepEqual(
    deriveTags({
      label: "API: TelemetryService",
      edgeLabels: ["analytics snapshot", "object store", "bundle shape"],
    }).sort(),
    ["bundle", "telemetry"],
  );
});

test("parseKnowledgeGraphDot preserves dashed style metadata for edges", () => {
  const dot = `
digraph athanor_knowledge_graph {
  subgraph cluster_ui {
    label="UI";
    a [label="UI: useSimulation"];
  }
  subgraph cluster_api {
    label="API";
    b [label="API: SimulationController"];
  }
  a -> b [style=dashed, label="HTTP /scenarios/{id}/simulate"];
}
`;

  const graph = parseKnowledgeGraphDot(dot);

  assert.deepEqual(graph.edges, [
    {
      source: "a",
      target: "b",
      label: "HTTP /scenarios/{id}/simulate",
      style: "dashed",
    },
  ]);
});

test("renderHtml prefers embedded data and keeps the file fetch fallback", () => {
  const html = renderHtml({
    embeddedData: {
      generatedAt: "2026-04-07T00:00:00.000Z",
      clusters: [],
      nodes: [],
      edges: [],
    },
  });

  const embeddedIndex = html.indexOf('id="knowledge-graph-data"');
  const fetchIndex = html.indexOf('fetch("./knowledge-graph-data.json")');

  assert.ok(embeddedIndex !== -1);
  assert.ok(fetchIndex !== -1);
  assert.ok(embeddedIndex < fetchIndex);
  assert.match(html, /JSON\.parse\(embeddedData\)/);
});

test("generateKnowledgeGraphArtifacts writes json and standalone html outputs", async () => {
  const tempDir = await fs.mkdtemp(path.join(os.tmpdir(), "knowledge-graph-viewer-"));
  const dotPath = path.join(tempDir, "knowledge-graph.dot");
  const jsonDir = path.join(tempDir, "data");
  const htmlDir = path.join(tempDir, "viewer");
  const jsonPath = path.join(jsonDir, "custom-graph-data.json");
  const htmlPath = path.join(htmlDir, "custom-graph-viewer.html");

  await fs.mkdir(jsonDir, { recursive: true });
  await fs.mkdir(htmlDir, { recursive: true });

  await fs.writeFile(
    dotPath,
    `
digraph athanor_knowledge_graph {
  subgraph cluster_ui {
    label="UI";
    runs_route [label="UI route: /runs/{runId}"];
    builder [label="UI: builder.vue"];
  }
  subgraph cluster_api {
    label="API";
    job_service [label="API: JobService"];
  }
  builder -> runs_route [label="poll /runs/{runId}"];
  runs_route -> job_service [label="GET /runs/{runId}"];
}
`,
    "utf8",
  );

  const model = await generateKnowledgeGraphArtifacts({
    dotFilePath: dotPath,
    jsonFilePath: jsonPath,
    htmlFilePath: htmlPath,
  });

  const jsonText = await fs.readFile(jsonPath, "utf8");
  const htmlText = await fs.readFile(htmlPath, "utf8");
  const parsedJson = JSON.parse(jsonText);

  assert.deepEqual(parsedJson, model);
  assert.equal(parsedJson.source, path.relative(process.cwd(), dotPath).replaceAll(path.sep, "/"));
  assert.equal(parsedJson.clusters.length, 2);
  assert.deepEqual(
    parsedJson.nodes.map((node) => [node.id, node.cluster, node.route ?? null]),
    [
      ["runs_route", "UI", "/runs/{runId}"],
      ["builder", "UI", null],
      ["job_service", "API", null],
    ],
  );
  assert.equal(parsedJson.nodes.find((node) => node.id === "runs_route").kind, "route");
  assert.match(htmlText, /id="knowledge-graph-data"/);
  assert.match(htmlText, /Athanor Knowledge Graph/);
  assert.match(htmlText, /cluster-filters/);
  assert.match(htmlText, /tag-filters/);
  assert.match(htmlText, /route-filters/);
  assert.match(htmlText, /Selected Node/);
  assert.match(htmlText, /fetch\("\.\.\/data\/custom-graph-data\.json"\)/);
});

test("shouldRunAsMainModule is safe when argv1 is missing", () => {
  assert.equal(shouldRunAsMainModule(undefined), false);
});
