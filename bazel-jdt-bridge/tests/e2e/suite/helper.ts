import * as vscode from 'vscode';
import * as assert from 'assert';

export const JDTLS_INDEX_DELAY_MS = 5_000;

export async function openJavaFile(relativePath: string): Promise<vscode.TextDocument> {
    const workspaceRoot = vscode.workspace.workspaceFolders![0].uri.fsPath;
    const filePath = vscode.Uri.file(vscode.Uri.joinPath(vscode.Uri.file(workspaceRoot), relativePath).fsPath);
    const doc = await vscode.workspace.openTextDocument(filePath);
    await vscode.window.showTextDocument(doc);
    await new Promise(r => setTimeout(r, JDTLS_INDEX_DELAY_MS));
    return doc;
}

export async function getCompletionAt(
    doc: vscode.TextDocument,
    line: number,
    character: number
): Promise<vscode.CompletionList> {
    const position = new vscode.Position(line, character);
    const result = await vscode.commands.executeCommand<vscode.CompletionList>(
        'vscode.executeCompletionItemProvider',
        doc.uri,
        position
    );
    return result;
}

export function completionLabels(completions: vscode.CompletionList): string[] {
    return completions.items.map(item => {
        if (typeof item.label === 'string') {
            return item.label;
        }
        return item.label.label;
    });
}

export function findPositionOf(text: string, searchString: string): vscode.Position {
    const idx = text.indexOf(searchString);
    assert.ok(idx >= 0, `String "${searchString}" not found in document text`);
    const before = text.substring(0, idx);
    const line = before.split('\n').length - 1;
    const lastNewline = before.lastIndexOf('\n');
    const character = idx - lastNewline - 1;
    return new vscode.Position(line, character);
}
