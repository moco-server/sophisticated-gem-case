import { existsSync } from 'node:fs';
import { join } from 'node:path';

// Windowsの.cmdをshellで再解釈せず、pnpm/CorepackのJS本体へ同じ引数を渡す。
export function pnpmCommand(args, { platform = process.platform, path = process.env.PATH ?? process.env.Path ?? '', execPath = process.execPath } = {}) {
  if (platform !== 'win32') return { file: 'pnpm', args };
  for (const directory of path.split(';').filter(Boolean)) {
    const base = directory.replace(/^"|"$/g, '');
    if (existsSync(join(base, 'pnpm.exe'))) return { file: join(base, 'pnpm.exe'), args };
    if (!existsSync(join(base, 'pnpm.cmd'))) continue;
    for (const script of [
      join(base, 'node_modules/pnpm/bin/pnpm.mjs'),
      join(base, 'node_modules/pnpm/bin/pnpm.cjs'),
      join(base, '../pnpm/bin/pnpm.mjs'),
      join(base, '../pnpm/bin/pnpm.cjs'),
      join(base, 'node_modules/corepack/dist/pnpm.js'),
    ]) {
      if (existsSync(script)) return { file: execPath, args: [script, ...args] };
    }
    throw new Error('pnpm.cmd has no supported JS entry point; use npm-installed pnpm 12.8.1 or Corepack');
  }
  throw new Error('pnpm is not available on PATH');
}
