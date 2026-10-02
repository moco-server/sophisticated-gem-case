import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export function checkGameTestResult(text) {
  const passed = text.match(/All (\d+) required tests passed/);
  if (!passed || Number(passed[1]) <= 0 || /required tests failed/.test(text)) throw new Error('No positive passing required GameTest result');
  return Number(passed[1]);
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { console.log(`${checkGameTestResult(readFileSync(new URL('../.local/gametest/logs/latest.log',import.meta.url),'utf8'))} required GameTests passed`); }
  catch (error) { console.error(error.message); process.exitCode=1; }
}
