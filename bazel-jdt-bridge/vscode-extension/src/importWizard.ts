import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import { parseBazelprojectFile, resolveScopePatterns } from './bazelproject';
import { getConfig } from './config';

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
    const config = getConfig();
    const dirs = collectDirectories(workspaceRoot, config.importScanDirs);

    if (dirs.length === 0) {
        vscode.window.showWarningMessage('No directories with BUILD files found in workspace.');
        return undefined;
    }

    const picks = await vscode.window.showQuickPick(
        dirs.map((d) => ({
            label: d.name,
            description: d.parentPath,
            detail: d.relative,
        })),
        {
            placeHolder: 'Select directories to import (multi-select)',
            canPickMany: true,
        }
    );

    if (!picks || picks.length === 0) {
        return undefined;
    }

    const patterns = picks.map((p) => `//${p.detail}/...:*`);

    const saveChoice = await vscode.window.showQuickPick(
        [
            { label: 'Yes, save to .bazelproject', description: 'Create .bazelproject for future imports' },
            { label: 'No, import only this time', description: 'Skip saving .bazelproject' },
        ],
        { placeHolder: 'Save selection to .bazelproject?' }
    );

    if (saveChoice && saveChoice.label.startsWith('Yes')) {
        const bazelprojectPath = path.join(workspaceRoot, '.bazelproject');
        const testSources = detectTestSourceDirs(workspaceRoot, picks.map((p) => p.detail));
        const content = generateBazelprojectContent({
            directories: picks.map((p) => p.detail),
            testSources,
            buildFlags: config.buildFlags,
            bazelBinary: config.bazelPath,
            javaLanguageLevel: config.javaLanguageLevel,
        });
        vscode.commands.executeCommand('_bazel-jdt.setWizardActive', true);
        fs.writeFileSync(bazelprojectPath, content, 'utf-8');
        setTimeout(() => {
            vscode.commands.executeCommand('_bazel-jdt.setWizardActive', false).then(undefined, () => {});
        }, 2000);
        return { strategy: 'manual', patterns, bazelprojectPath };
    }

    return { strategy: 'manual', patterns };
}

interface DirectoryEntry {
    name: string;
    parentPath: string;
    relative: string;
}

function hasBuildFile(dirPath: string): boolean {
    return fs.existsSync(path.join(dirPath, 'BUILD')) ||
           fs.existsSync(path.join(dirPath, 'BUILD.bazel'));
}

const SKIP_DIRS = new Set(['.', 'bazel-out', 'bazel-bin', 'bazel-testlogs', 'bazel-genfiles']);

function collectDirectories(workspaceRoot: string, scanDirs: string[]): DirectoryEntry[] {
    const results: DirectoryEntry[] = [];
    const seen = new Set<string>();

    function addEntry(relativePath: string) {
        if (seen.has(relativePath)) {
            return;
        }
        seen.add(relativePath);
        const parts = relativePath.split('/');
        const name = parts[parts.length - 1];
        const parentParts = parts.slice(0, -1);
        const parentPath = parentParts.length > 0 ? parentParts.join('/') + '/' : '';
        results.push({ name, parentPath, relative: relativePath });
    }

    try {
        const entries = fs.readdirSync(workspaceRoot, { withFileTypes: true });
        for (const entry of entries) {
            if (!entry.isDirectory() || entry.name.startsWith('.') || SKIP_DIRS.has(entry.name)) {
                continue;
            }
            const fullPath = path.join(workspaceRoot, entry.name);
            if (hasBuildFile(fullPath)) {
                addEntry(entry.name);
            }
        }
    } catch {
    }

    for (const scanDir of scanDirs) {
        const scanPath = path.join(workspaceRoot, scanDir);
        try {
            const entries = fs.readdirSync(scanPath, { withFileTypes: true });
            for (const entry of entries) {
                if (!entry.isDirectory() || entry.name.startsWith('.') || SKIP_DIRS.has(entry.name)) {
                    continue;
                }
                const childPath = path.join(scanPath, entry.name);
                if (hasBuildFile(childPath)) {
                    const relative = scanDir + '/' + entry.name;
                    addEntry(relative);
                }
            }
        } catch {
        }
    }

    results.sort((a, b) => a.relative.localeCompare(b.relative));
    return results;
}

function detectTestSourceDirs(workspaceRoot: string, directories: string[]): string[] {
    const testPatterns: string[] = [];
    const testSubdirs = ['src/test/java', 'test', 'javatests'];

    for (const dir of directories) {
        for (const sub of testSubdirs) {
            const fullPath = path.join(workspaceRoot, dir, sub);
            if (fs.existsSync(fullPath) && fs.statSync(fullPath).isDirectory()) {
                testPatterns.push(`${dir}/${sub}/**`);
            }
        }
    }

    return testPatterns;
}

function generateBazelprojectContent(options: {
    directories: string[];
    testSources: string[];
    buildFlags: string[];
    bazelBinary: string;
    javaLanguageLevel: string;
}): string {
    const lines = ['# Generated by Bazel JDT Bridge', 'directories:'];
    for (const dir of options.directories) {
        lines.push(`  ${dir}`);
    }
    lines.push('derive_targets_from_directories:');
    lines.push('  True');

    if (options.testSources.length > 0) {
        lines.push('test_sources:');
        for (const ts of options.testSources) {
            lines.push(`  ${ts}`);
        }
    }

    if (options.buildFlags.length > 0) {
        lines.push('build_flags:');
        for (const flag of options.buildFlags) {
            lines.push(`  ${flag}`);
        }
    }

    if (options.bazelBinary && options.bazelBinary !== 'bazel') {
        lines.push(`bazel_binary: ${options.bazelBinary}`);
    }

    if (options.javaLanguageLevel) {
        lines.push(`java_language_level: ${options.javaLanguageLevel}`);
    }

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
