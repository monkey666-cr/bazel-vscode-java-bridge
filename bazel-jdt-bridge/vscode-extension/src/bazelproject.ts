import * as fs from 'fs';

export interface BazelProjectViewConfig {
    directories: string[];
    targets: string[];
    deriveTargetsFromDirectories: boolean;
    buildFlags: string[];
    testSources: string[];
    imports: string[];
    syncFlags: string[];
    excludeTarget: string[];
    bazelBinary: string;
    javaLanguageLevel: string;
}

const DEFAULT_CONFIG: BazelProjectViewConfig = {
    directories: [],
    targets: [],
    deriveTargetsFromDirectories: true,
    buildFlags: [],
    testSources: [],
    imports: [],
    syncFlags: [],
    excludeTarget: [],
    bazelBinary: '',
    javaLanguageLevel: '',
};

export function parseBazelprojectFile(filePath: string): BazelProjectViewConfig | null {
    if (!fs.existsSync(filePath)) {
        return null;
    }

    const content = fs.readFileSync(filePath, 'utf-8');
    return parseBazelprojectContent(content);
}

export function parseBazelprojectContent(content: string): BazelProjectViewConfig {
    const config: BazelProjectViewConfig = {
        ...DEFAULT_CONFIG,
        directories: [],
        targets: [],
        buildFlags: [],
        testSources: [],
        imports: [],
        syncFlags: [],
        excludeTarget: [],
    };
    let currentSection: string | null = null;

    for (const rawLine of content.split('\n')) {
        const line = rawLine.trim();
        if (line.length === 0 || line.startsWith('#')) {
            continue;
        }

        if (line.endsWith(':')) {
            currentSection = line.slice(0, -1).trim().toLowerCase();
            continue;
        }

        const directiveMatch = line.match(/^([a-z_]+):\s*(.+)$/i);
        if (directiveMatch) {
            const key = directiveMatch[1].toLowerCase();
            const value = directiveMatch[2].trim();
            if (key === 'derive_targets_from_directories') {
                config.deriveTargetsFromDirectories = value.toLowerCase() === 'true';
                currentSection = null;
                continue;
            }
            if (key === 'bazel_binary') {
                config.bazelBinary = value;
                currentSection = null;
                continue;
            }
            if (key === 'java_language_level') {
                config.javaLanguageLevel = value;
                currentSection = null;
                continue;
            }
        }

        if (currentSection === null) {
            continue;
        }

        switch (currentSection) {
            case 'directories':
                config.directories.push(line);
                break;
            case 'derive_targets_from_directories':
                config.deriveTargetsFromDirectories = line.toLowerCase() === 'true';
                break;
            case 'targets':
                config.targets.push(line);
                break;
            case 'build_flags':
                config.buildFlags.push(line);
                break;
            case 'test_sources':
                config.testSources.push(line);
                break;
            case 'import':
            case 'try_import':
                config.imports.push(line);
                break;
            case 'sync_flags':
                config.syncFlags.push(line);
                break;
            case 'exclude_target':
                config.excludeTarget.push(line);
                break;
        }
    }

    return config;
}

export function resolveScopePatterns(config: BazelProjectViewConfig): string[] {
    const patterns: string[] = [];

    if (config.deriveTargetsFromDirectories) {
        for (const dir of config.directories) {
            if (dir.startsWith('-')) {
                const p = dir.slice(1);
                patterns.push(`-//${p === '.' ? '...' : p + '/...'}:*`);
            } else {
                patterns.push(`//${dir === '.' ? '...' : dir + '/...'}:*`);
            }
        }
    }

    patterns.push(...config.targets);

    for (const target of config.excludeTarget) {
        const prefix = target.startsWith('-') ? target : `-${target}`;
        patterns.push(prefix);
    }

    return patterns;
}
