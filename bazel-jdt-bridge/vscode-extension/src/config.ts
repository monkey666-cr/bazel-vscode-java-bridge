import * as vscode from 'vscode';

export interface BazelConfig {
    bazelPath: string;
    syncOnSave: boolean;
    cacheDir: string;
    importScanDirs: string[];
    buildFlags: string[];
    javaLanguageLevel: string;
}

export function getConfig(): BazelConfig {
    const config = vscode.workspace.getConfiguration('bazel-jdt');
    return {
        bazelPath: config.get<string>('bazelPath', 'bazel'),
        syncOnSave: config.get<boolean>('syncOnSave', true),
        cacheDir: config.get<string>('cacheDir', ''),
        importScanDirs: config.get<string[]>('importScanDirs', []),
        buildFlags: config.get<string[]>('buildFlags', []),
        javaLanguageLevel: config.get<string>('javaLanguageLevel', '17'),
    };
}
