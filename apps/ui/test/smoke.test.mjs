import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'

const dashboardPagePath = path.resolve(process.cwd(), 'pages/dashboard.vue')

test('dashboard shell exists with heading', async () => {
  const contents = await readFile(dashboardPagePath, 'utf8')
  assert.match(contents, /Athanor Scenario Studio/)
})
