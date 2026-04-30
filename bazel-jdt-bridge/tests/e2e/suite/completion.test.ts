import * as vscode from 'vscode';
import * as assert from 'assert';
import { openJavaFile, getCompletionAt, completionLabels, findPositionOf } from './helper';

suite('Code Completion', () => {
    test('Java file opens and completion API responds', async function () {
        this.timeout(30_000);
        const doc = await openJavaFile('app/src/main/java/com/example/app/Main.java');
        const text = doc.getText();
        assert.ok(text.includes('Main'), 'Opened document should contain Main class');
        assert.ok(text.includes('Greeter'), 'Main.java should reference Greeter class');
    });

    test('completion API returns results for Java file', async function () {
        this.timeout(30_000);
        const doc = await openJavaFile('app/src/main/java/com/example/app/Main.java');
        const completions = await getCompletionAt(doc, 0, 0);
        assert.ok(completions !== undefined, 'Completion API should return a result');
        assert.ok(typeof completions.items !== 'undefined', 'Result should have items');
    });

    test('workspace Java files are discoverable', async function () {
        this.timeout(30_000);
        const javaFiles = await vscode.workspace.findFiles('**/*.java');
        assert.ok(javaFiles.length > 0, `Expected Java files in workspace, found ${javaFiles.length}`);
        const paths = javaFiles.map(u => u.fsPath);
        assert.ok(
            paths.some(p => p.includes('Main.java')),
            'Expected Main.java in workspace'
        );
        assert.ok(
            paths.some(p => p.includes('Greeter.java')),
            'Expected Greeter.java in workspace'
        );
    });
});
