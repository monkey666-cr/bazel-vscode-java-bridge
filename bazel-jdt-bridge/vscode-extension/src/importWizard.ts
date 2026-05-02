import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import { parseBazelprojectFile, resolveScopePatterns } from './bazelproject';

export interface ImportWizardResult {
    strategy: 'existing' | 'manual' | 'everything';
    patterns: string[];
    bazelprojectPath?: string;
}

export async function runImportWizard(
    workspaceRoot: string
): Promise<ImportWizardResult | undefined> {
    const existingFile = findBazelprojectFile(workspaceRoot);

    if (existingFile) {
        const useExisting = await vscode.window.showQuickPick(
            [
                {
                    label: 'Use existing .bazelproject',
                    description: path.basename(existingFile),
                    detail: existingFile,
                },
                {
                    label: 'Choose directories manually',
                    description: 'Override with manual selection',
                },
                {
                    label: 'Import everything',
                    description: 'Import all Java targets (//...:*)',
                },
            ],
            { placeHolder: 'Found .bazelproject file. How to proceed?' }
        );

        if (!useExisting) {
            return undefined;
        }

        if (useExisting.label.startsWith('Use existing')) {
            const config = parseBazelprojectFile(existingFile);
            const patterns = config ? resolveScopePatterns(config) : [];
            return {
                strategy: 'existing',
                patterns,
                bazelprojectPath: existingFile,
            };
        }

        if (useExisting.label.startsWith('Import everything')) {
            return { strategy: 'everything', patterns: [] };
        }
    } else {
        const strategy = await vscode.window.showQuickPick(
            [
                {
                    label: '$(file-directory) Select directories to import',
                    description: 'Choose workspace directories',
                },
                {
                    label: '$(globe) Import everything',
                    description: 'Import all Java targets (//...:*)',
                },
            ],
            { placeHolder: 'No .bazelproject found. How to import?' }
        );

        if (!strategy) {
            return undefined;
        }

        if (strategy.label.includes('Import everything')) {
            return { strategy: 'everything', patterns: [] };
        }
    }

    return runDirectoryPicker(workspaceRoot);
}

async function runDirectoryPicker(
    workspaceRoot: string
): Promise<ImportWizardResult | undefined> {
    const dirs = collectDirectories(workspaceRoot);

    if (dirs.length === 0) {
        vscode.window.showWarningMessage('No directories found in workspace.');
        return undefined;
    }

    const picks = await vscode.window.showQuickPick(
        dirs.map((d) => ({
            label: d.relative,
            description: d.hasBuildFile ? '$(gear) has BUILD' : undefined,
            detail: d.absolute,
        })),
        {
            placeHolder: 'Select directories to import (multi-select)',
            canPickMany: true,
        }
    );

    if (!picks || picks.length === 0) {
        return undefined;
    }

    const patterns = picks.map((p) => `//${p.label}/...:*`);

    const saveChoice = await vscode.window.showQuickPick(
        [
            { label: 'Yes, save to .bazelproject', description: 'Create .bazelproject for future imports' },
            { label: 'No, import only this time', description: 'Skip saving .bazelproject' },
        ],
        { placeHolder: 'Save selection to .bazelproject?' }
    );

    if (saveChoice && saveChoice.label.startsWith('Yes')) {
        const bazelprojectPath = path.join(workspaceRoot, '.bazelproject');
        const content = generateBazelprojectContent(
            picks.map((p) => p.label)
        );
        vscode.commands.executeCommand('_bazel-jdt.setWizardActive', true);
        fs.writeFileSync(bazelprojectPath, content, 'utf-8');
        setTimeout(() => {
            vscode.commands.executeCommand('_bazel-jdt.setWizardActive', false).catch(() => {});
        }, 2000);
        return { strategy: 'manual', patterns, bazelprojectPath };
    }

    return { strategy: 'manual', patterns };
}

interface DirectoryEntry {
    relative: string;
    absolute: string;
    hasBuildFile: boolean;
}

function collectDirectories(workspaceRoot: string): DirectoryEntry[] {
    const results: DirectoryEntry[] = [];
    const maxDepth = 3;

    function walk(dir: string, depth: number) {
        if (depth > maxDepth) {
            return;
        }
        try {
            const entries = fs.readdirSync(dir, { withFileTypes: true });
            for (const entry of entries) {
                if (entry.name.startsWith('.') || entry.name === 'bazel-out' || entry.name === 'bazel-bin') {
                    continue;
                }
                if (entry.isDirectory()) {
                    const fullPath = path.join(dir, entry.name);
                    const relative = path.relative(workspaceRoot, fullPath);
                    const hasBuild =
                        fs.existsSync(path.join(fullPath, 'BUILD')) ||
                        fs.existsSync(path.join(fullPath, 'BUILD.bazel'));
                    results.push({ relative, absolute: fullPath, hasBuildFile: hasBuild });
                    walk(fullPath, depth + 1);
                }
            }
        } catch {
        }
    }

    walk(workspaceRoot, 0);
    results.sort((a, b) => a.relative.localeCompare(b.relative));
    return results;
}

function generateBazelprojectContent(directories: string[]): string {
    const lines = ['# Generated by Bazel JDT Bridge', 'directories:'];
    for (const dir of directories) {
        lines.push(`  ${dir}`);
    }
    lines.push('derive_targets_from_directories: True');
    return lines.join('\n') + '\n';
}

function findBazelprojectFile(workspaceRoot: string): string | undefined {
    const candidates = ['.bazelproject'];
    for (const name of candidates) {
        const fullPath = path.join(workspaceRoot, name);
        if (fs.existsSync(fullPath)) {
            return fullPath;
        }
    }
    return undefined;
}
