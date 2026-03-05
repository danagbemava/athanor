import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'

const appVuePath = path.resolve(process.cwd(), 'app.vue')

test('app shell exists with heading', async () => {
  const contents = await readFile(appVuePath, 'utf8')
  assert.match(contents, /Athanor Scenario Studio/)
})
