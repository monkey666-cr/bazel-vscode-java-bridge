import * as vscode from 'vscode';
import * as assert from 'assert';
import * as path from 'path';
import * as fs from 'fs';

suite('Incremental Sync', () => {
    test('BUILD.bazel file is present in workspace', () => {
        const workspaceRoot = vscode.workspace.workspaceFolders![0].uri.fsPath;
        const buildFilePath = path.join(workspaceRoot, 'BUILD.bazel');
        assert.ok(fs.existsSync(buildFilePath), 'BUILD.bazel should exist in workspace');
    });

    test('BUILD file can be opened and edited', async function () {
        this.timeout(30_000);
        const workspaceRoot = vscode.workspace.workspaceFolders![0].uri.fsPath;
        const buildFilePath = path.join(workspaceRoot, 'BUILD.bazel');
        const originalContent = fs.readFileSync(buildFilePath, 'utf-8');

        try {
            const doc = await vscode.workspace.openTextDocument(buildFilePath);
            await vscode.window.showTextDocument(doc);
            const edit = new vscode.WorkspaceEdit();
            const lastLine = doc.lineAt(doc.lineCount - 1);
            edit.insert(doc.uri, lastLine.range.end, '\n# e2e-test-marker\n');
            await vscode.workspace.applyEdit(edit);
            await doc.save();

            const savedContent = fs.readFileSync(buildFilePath, 'utf-8');
            assert.ok(savedContent.includes('# e2e-test-marker'), 'Edit should be persisted');
        } finally {
            fs.writeFileSync(buildFilePath, originalContent, 'utf-8');
        }
    });
});
