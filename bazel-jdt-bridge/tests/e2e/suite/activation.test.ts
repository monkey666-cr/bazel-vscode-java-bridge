import * as vscode from 'vscode';
import * as assert from 'assert';

suite('Extension Activation', () => {
    test('extension is active in Bazel workspace', async function () {
        this.timeout(120_000);
        const ext = vscode.extensions.getExtension('bazel-jdt.bazel-jdt-bridge');
        assert.ok(ext, 'Extension bazel-jdt.bazel-jdt-bridge should be found');
        if (!ext!.isActive) {
            await ext!.activate();
        }
        assert.strictEqual(ext!.isActive, true, 'Extension should be active');
    });

    test('workspace contains WORKSPACE file', async function () {
        this.timeout(30_000);
        const workspaceFiles = await vscode.workspace.findFiles('{WORKSPACE,WORKSPACE.bazel}');
        assert.ok(workspaceFiles.length > 0, 'Workspace should contain WORKSPACE or WORKSPACE.bazel');
    });

    test('extension commands are registered', async function () {
        this.timeout(30_000);
        const commands = await vscode.commands.getCommands(true);
        assert.ok(commands.includes('bazel-jdt.importProject'), 'importProject command should be registered');
        assert.ok(commands.includes('bazel-jdt.syncProject'), 'syncProject command should be registered');
        assert.ok(commands.includes('bazel-jdt.cleanCache'), 'cleanCache command should be registered');
    });
});
