package com.bazel.jdt;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BazelProjectViewTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File writeBazelproject(String content) throws IOException {
        File file = new File(tempFolder.getRoot(), ".bazelproject");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        return file;
    }

    @Test
    public void inlineDirectiveAfterDirectoriesSection() throws IOException {
        writeBazelproject(
            "directories:\n" +
            "  app\n" +
            "  service\n" +
            "derive_targets_from_directories: True\n"
        );

        BazelProjectView view = BazelProjectView.parse(tempFolder.getRoot());
        assertNotNull(view);

        List<String> dirs = view.getDirectories();
        assertEquals(2, dirs.size());
        assertTrue(dirs.contains("app"));
        assertTrue(dirs.contains("service"));
        assertFalse(dirs.contains("derive_targets_from_directories: True"));

        assertTrue(view.isDeriveTargetsFromDirectories());
    }

    @Test
    public void inlineDirectiveFalseValue() throws IOException {
        writeBazelproject(
            "directories:\n" +
            "  app\n" +
            "derive_targets_from_directories: False\n"
        );

        BazelProjectView view = BazelProjectView.parse(tempFolder.getRoot());
        assertNotNull(view);
        assertFalse(view.isDeriveTargetsFromDirectories());
    }

    @Test
    public void sectionHeaderFormatStillWorks() throws IOException {
        writeBazelproject(
            "directories:\n" +
            "  app\n" +
            "derive_targets_from_directories:\n" +
            "  True\n"
        );

        BazelProjectView view = BazelProjectView.parse(tempFolder.getRoot());
        assertNotNull(view);
        assertEquals(1, view.getDirectories().size());
        assertTrue(view.isDeriveTargetsFromDirectories());
    }

    @Test
    public void scopePatternsExcludeDirectiveString() throws IOException {
        writeBazelproject(
            "directories:\n" +
            "  app\n" +
            "  utils\n" +
            "derive_targets_from_directories: True\n"
        );

        BazelProjectView view = BazelProjectView.parse(tempFolder.getRoot());
        List<String> patterns = view.getScopePatterns();

        assertEquals(2, patterns.size());
        assertEquals("//app/...:*", patterns.get(0));
        assertEquals("//utils/...:*", patterns.get(1));
        for (String p : patterns) {
            assertFalse("Pattern should not contain directive name: " + p,
                p.contains("derive_targets_from_directories"));
        }
    }

    @Test
    public void parseBuildFlags() throws IOException {
        writeBazelproject(
            "directories:\n" +
            "  app\n" +
            "build_flags:\n" +
            "  --java_language_version=17\n" +
            "  --config=dev\n"
        );
        BazelProjectView view = BazelProjectView.parse(tempFolder.getRoot());
        assertNotNull(view);
        assertEquals(2, view.getBuildFlags().size());
        assertTrue(view.getBuildFlags().contains("--java_language_version=17"));
        assertTrue(view.getBuildFlags().contains("--config=dev"));
    }

    @Test
    public void parseTestSources() throws IOException {
        writeBazelproject(
            "directories:\n" +
            "  app\n" +
            "test_sources:\n" +
            "  src/test/java/**\n" +
            "  javatests/**\n"
        );
        BazelProjectView view = BazelProjectView.parse(tempFolder.getRoot());
        assertNotNull(view);
        assertEquals(2, view.getTestSourcePatterns().size());
        assertTrue(view.getTestSourcePatterns().contains("src/test/java/**"));
        assertTrue(view.getTestSourcePatterns().contains("javatests/**"));
    }

    @Test
    public void parseSyncFlags() throws IOException {
        writeBazelproject(
            "directories:\n" +
            "  app\n" +
            "sync_flags:\n" +
            "  --keep_going\n"
        );
        BazelProjectView view = BazelProjectView.parse(tempFolder.getRoot());
        assertNotNull(view);
        assertEquals(1, view.getSyncFlags().size());
        assertTrue(view.getSyncFlags().contains("--keep_going"));
    }

    @Test
    public void parseExcludeTarget() throws IOException {
        writeBazelproject(
            "directories:\n" +
            "  app\n" +
            "exclude_target:\n" +
            "  //third_party:expensive_test\n"
        );
        BazelProjectView view = BazelProjectView.parse(tempFolder.getRoot());
        assertNotNull(view);
        assertEquals(1, view.getExcludeTargets().size());
        assertTrue(view.getExcludeTargets().contains("//third_party:expensive_test"));
    }

    @Test
    public void parseMultipleNewSections() throws IOException {
        writeBazelproject(
            "directories:\n" +
            "  app\n" +
            "build_flags:\n" +
            "  --java_language_version=17\n" +
            "test_sources:\n" +
            "  src/test/java/**\n" +
            "sync_flags:\n" +
            "  --keep_going\n"
        );
        BazelProjectView view = BazelProjectView.parse(tempFolder.getRoot());
        assertNotNull(view);
        assertEquals(1, view.getBuildFlags().size());
        assertEquals(1, view.getTestSourcePatterns().size());
        assertEquals(1, view.getSyncFlags().size());
    }
}
