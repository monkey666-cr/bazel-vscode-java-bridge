import * as vscode from 'vscode';
import { getConfig } from './config';
import { runImportWizard } from './importWizard';

export function registerCommands(context: vscode.ExtensionContext) {
    const workspaceRoot = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath || '';

    context.subscriptions.push(
        vscode.commands.registerCommand('bazel-jdt.importProject', async () => {
            try {
                const config = getConfig();
                await vscode.window.withProgress(
                    { location: vscode.ProgressLocation.Window, title: 'Importing Bazel project...' },
                    async (progress) => {
                        progress.report({ message: 'Setting up import...' });

                        const wizardResult = await runImportWizard(workspaceRoot);
                        const scopePatterns = wizardResult?.patterns || [];

                        progress.report({ message: 'Discovering Java targets...' });
                        await vscode.commands.executeCommand('java.execute.workspaceCommand',
                            'bazel-jdt.importProject', workspaceRoot, config.bazelPath, config.cacheDir,
                            scopePatterns);
                    }
                );
                vscode.window.showInformationMessage('Bazel project imported successfully');
            } catch (error) {
                vscode.window.showErrorMessage(`Bazel import failed: ${error}`);
            }
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('bazel-jdt.syncProject', async () => {
            try {
                await vscode.commands.executeCommand('java.execute.workspaceCommand', 'bazel-jdt.syncProject');
            } catch (error) {
                vscode.window.showErrorMessage(`Bazel sync failed: ${error}`);
            }
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('bazel-jdt.cleanCache', async () => {
            const confirm = await vscode.window.showWarningMessage(
                'Clear Bazel cache? This will trigger a full re-sync.',
                { modal: true },
                'Clear Cache'
            );
            if (confirm === 'Clear Cache') {
                await vscode.commands.executeCommand('java.execute.workspaceCommand', 'bazel-jdt.cleanCache');
                vscode.window.showInformationMessage('Bazel cache cleared');
            }
        })
    );
}
