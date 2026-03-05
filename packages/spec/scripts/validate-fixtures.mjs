import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import Ajv2020 from 'ajv/dist/2020.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const specRoot = path.resolve(__dirname, '..');
const schemasDir = path.join(specRoot, 'schemas');
const fixturesDir = path.join(specRoot, 'fixtures');

const ajv = new Ajv2020({ allErrors: true, strict: false });
const schemaFiles = (await fs.readdir(schemasDir)).filter((f) => f.endsWith('.schema.json'));

let failures = 0;
for (const schemaFile of schemaFiles) {
  const schemaName = schemaFile.replace('.schema.json', '');
  const schemaPath = path.join(schemasDir, schemaFile);
  const fixtureDir = path.join(fixturesDir, schemaName);
  const validPath = path.join(fixtureDir, 'valid.json');
  const invalidPath = path.join(fixtureDir, 'invalid.json');

  const [schema, validFixture, invalidFixture] = await Promise.all([
    fs.readFile(schemaPath, 'utf8').then(JSON.parse),
    fs.readFile(validPath, 'utf8').then(JSON.parse),
    fs.readFile(invalidPath, 'utf8').then(JSON.parse)
  ]);

  const validate = ajv.compile(schema);

  const validOk = validate(validFixture);
  if (!validOk) {
    failures += 1;
    console.error(`FAIL valid fixture for ${schemaName}`);
    console.error(validate.errors);
  }

  const invalidOk = validate(invalidFixture);
  if (invalidOk) {
    failures += 1;
    console.error(`FAIL invalid fixture unexpectedly passed for ${schemaName}`);
  }
}

if (failures > 0) {
  process.exit(1);
}

console.log(`Schema fixture validation passed for ${schemaFiles.length} schemas.`);
