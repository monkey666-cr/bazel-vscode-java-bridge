import * as esbuild from 'esbuild';
import * as path from 'path';
import * as fs from 'fs';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const extensionDir = path.resolve(__dirname, '..');
const projectRoot = path.resolve(extensionDir, '..');
const extNodeModules = path.resolve(extensionDir, 'node_modules');

const suiteDir = path.resolve(projectRoot, 'tests', 'e2e', 'suite');
const outDir = path.resolve(projectRoot, 'tests', 'e2e', 'out');
const outSuiteDir = path.resolve(outDir, 'suite');

fs.mkdirSync(outSuiteDir, { recursive: true });

const testFiles = fs.readdirSync(suiteDir)
    .filter((f) => f.endsWith('.test.ts'))
    .map((f) => ({
        in: path.resolve(suiteDir, f),
        out: path.resolve(outSuiteDir, f.replace('.ts', '.js')),
    }));

await Promise.all([
    esbuild.build({
        entryPoints: [path.resolve(suiteDir, 'index.ts')],
        bundle: true,
        outfile: path.resolve(outSuiteDir, 'index.js'),
        external: ['vscode'],
        format: 'cjs',
        platform: 'node',
        target: 'node18',
        sourcemap: true,
        resolveExtensions: ['.ts', '.js'],
        alias: {
            'mocha': path.resolve(extNodeModules, 'mocha', 'lib', 'mocha.js'),
        },
    }),
    ...testFiles.map(({ in: inFile, out: outFile }) =>
        esbuild.build({
            entryPoints: [inFile],
            bundle: true,
            outfile: outFile,
            external: ['vscode'],
            format: 'cjs',
            platform: 'node',
            target: 'node18',
            sourcemap: true,
            resolveExtensions: ['.ts', '.js'],
        })
    ),
    esbuild.build({
        entryPoints: [path.resolve(projectRoot, 'tests', 'e2e', 'runTests.ts')],
        bundle: true,
        outfile: path.resolve(outDir, 'runTests.js'),
        external: ['vscode', '@vscode/test-electron', 'child_process'],
        format: 'cjs',
        platform: 'node',
        target: 'node18',
        sourcemap: true,
    }),
]);

console.log('Test bundles built successfully.');
