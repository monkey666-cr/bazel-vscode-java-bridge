import * as path from 'path';
import * as fs from 'fs';
import * as Mocha from 'mocha';

export function run(testsRoot: string): Promise<void> {
    const suiteDir = path.dirname(testsRoot);

    return new Promise((resolve, reject) => {
        const mocha = new Mocha.default({
            ui: 'tdd',
            timeout: 120_000,
            color: true,
        });

        fs.readdir(suiteDir, (err, files) => {
            if (err) {
                return reject(err);
            }

            files
                .filter((f) => f.endsWith('.test.js'))
                .forEach((f) => mocha.addFile(path.resolve(suiteDir, f)));

            try {
                mocha.run((failures: number) => {
                    if (failures > 0) {
                        reject(new Error(`${failures} tests failed.`));
                    } else {
                        resolve();
                    }
                });
            } catch (err) {
                reject(err);
            }
        });
    });
}
