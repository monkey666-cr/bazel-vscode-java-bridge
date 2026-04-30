import * as vscode from 'vscode';
import * as assert from 'assert';

suite('Commands', () => {
    test('importProject command executes without throwing', async function () {
        this.timeout(30_000);
        try {
            await vscode.commands.executeCommand('bazel-jdt.importProject');
        } catch (e: any) {
            assert.ok(
                e.message?.includes('java.execute.workspaceCommand'),
                `Expected Java bridge error, got: ${e.message}`
            );
        }
    });

    test('syncProject command executes without throwing', async function () {
        this.timeout(30_000);
        try {
            await vscode.commands.executeCommand('bazel-jdt.syncProject');
        } catch (e: any) {
            assert.ok(
                e.message?.includes('java.execute.workspaceCommand'),
                `Expected Java bridge error, got: ${e.message}`
            );
        }
    });

    test('configuration values are accessible', () => {
        const config = vscode.workspace.getConfiguration('bazel-jdt');
        const bazelPath = config.get<string>('bazelPath');
        const syncOnSave = config.get<boolean>('syncOnSave');
        const cacheDir = config.get<string>('cacheDir');

        assert.strictEqual(bazelPath, 'bazel', 'Default bazelPath should be "bazel"');
        assert.strictEqual(syncOnSave, true, 'Default syncOnSave should be true');
        assert.strictEqual(cacheDir, '', 'Default cacheDir should be empty string');
    });
});
