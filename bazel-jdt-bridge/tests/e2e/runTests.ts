import * as path from 'path';
import * as cp from 'child_process';
import { runTests, downloadAndUnzipVSCode, resolveCliArgsFromVSCodeExecutablePath } from '@vscode/test-electron';

async function main() {
    const projectRoot = path.resolve(__dirname, '..', '..', '..');
    const repoRoot = path.resolve(projectRoot, '..');
    const extensionDevelopmentPath = path.resolve(projectRoot, 'vscode-extension');
    const extensionTestsPath = path.resolve(__dirname, 'suite', 'index.js');
    const testWorkspace = process.env.TEST_WORKSPACE || 'simple-java-project';
    const testWorkspacePath = path.resolve(repoRoot, 'examples', testWorkspace);

    console.log(`\n═══ E2E Test Configuration ═══`);
    console.log(`  Extension: ${extensionDevelopmentPath}`);
    console.log(`  Test suite: ${extensionTestsPath}`);
    console.log(`  Workspace: ${testWorkspacePath} (${testWorkspace})`);
    console.log(`═══════════════════════════════\n`);

    try {
        const vscodeExecutablePath = await downloadAndUnzipVSCode('stable');
        const [cliPath, ...cliArgs] = resolveCliArgsFromVSCodeExecutablePath(vscodeExecutablePath);

        console.log('Installing redhat.java extension...');
        cp.spawnSync(cliPath, [...cliArgs, '--install-extension', 'redhat.java'], {
            encoding: 'utf-8',
            stdio: 'inherit',
        });

        await runTests({
            vscodeExecutablePath,
            extensionDevelopmentPath,
            extensionTestsPath,
            launchArgs: [
                testWorkspacePath,
            ],
        });
    } catch (err) {
        console.error('Failed to run E2E tests:', err);
        process.exit(1);
    }
}

main();
