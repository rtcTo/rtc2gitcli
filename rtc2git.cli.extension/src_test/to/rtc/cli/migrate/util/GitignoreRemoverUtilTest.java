package to.rtc.cli.migrate.util;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;

public class GitignoreRemoverUtilTest {
    private static final IChangeLogOutput STUB_ICLO = new IChangeLogOutput() {
        @Override
        public void setIndent(int arg0) {
        }

        @Override
        public void writeLine(String arg0) {
            System.out.println(arg0);
        }
    };

    @Test
    public void removeFoldersKeptDueToLingeringGitignoreFilesGivenSeveralFoldersToCheckThenDeletesOphanedGitignoresAndTheirParentFolders() {
        // Given
        // foo/
        // foo/.gitignore
        // foo/.jazzignore
        // bar/
        // bar/subdir/
        // bar/subdir/.gitignore
        // bar/empty/
        final File rootDirectory = new File(".");
        final File foo = new File(rootDirectory, "foo");
        final File fooGitignore = new File(foo, ".gitignore");
        final File fooJazzignore = new File(foo, ".jazzignore");
        final File bar = new File(rootDirectory, "bar");
        final File barSubdir = new File(bar, "subdir");
        final File barEmpty = new File(bar, "empty");
        final File barSubdirGitignore = new File(barSubdir, ".gitignore");
        final TestInstance instance = new TestInstance();
        instance.stubDirContents(rootDirectory, foo, bar);
        instance.stubDirContents(foo, fooGitignore, fooJazzignore);
        instance.stubDirContents(bar, barSubdir);
        instance.stubDirContents(barSubdir, barSubdirGitignore);
        instance.stubEmptyDir(barEmpty);
        final List<String> foldersToCheck = Arrays.asList("foo", "bar", "bar/subdir", "bar/empty", "missing");
        final List<String> expectedDeletions = Arrays.asList(fileToString(barSubdirGitignore), fileToString(barSubdir));
        final boolean expected = true;

        // When
        final boolean actual = instance.removeFoldersKeptDueToLingeringGitignoreFiles(foldersToCheck, rootDirectory, STUB_ICLO);

        // Then
        final List<String> actualDeletions = instance.getDeletions();
        assertThat(actualDeletions, equalTo(expectedDeletions));
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void removeFoldersKeptDueToLingeringGitignoreFilesGivenFoldersThatShouldNotBeDeletedThenReturnsFalse() {
        // Given
        // foo/
        // foo/.gitignore
        // foo/.jazzignore
        // bar/
        // bar/empty/
        final File rootDirectory = new File(".");
        final File foo = new File(rootDirectory, "foo");
        final File fooGitignore = new File(foo, ".gitignore");
        final File fooJazzignore = new File(foo, ".jazzignore");
        final File bar = new File(rootDirectory, "bar");
        final File barEmpty = new File(bar, "empty");
        final TestInstance instance = new TestInstance();
        instance.stubDirContents(rootDirectory, foo, bar);
        instance.stubDirContents(foo, fooGitignore, fooJazzignore);
        instance.stubEmptyDir(barEmpty);
        final List<String> foldersToCheck = Arrays.asList("foo", "bar", "bar/empty", "missing");
        final List<String> expectedDeletions = Arrays.asList();
        final boolean expected = false;

        // When
        final boolean actual = instance.removeFoldersKeptDueToLingeringGitignoreFiles(foldersToCheck, rootDirectory, STUB_ICLO);

        // Then
        final List<String> actualDeletions = instance.getDeletions();
        assertThat(actualDeletions, equalTo(expectedDeletions));
        assertThat(actual, equalTo(expected));
    }

    private static class TestInstance extends GitignoreRemoverUtil {
        private final Map<String, String[]> dirContents = new HashMap<String, String[]>();
        private final List<String> deletedFiles = new ArrayList<String>();

        void stubDirContents(String dirName, String... contents) {
            dirContents.put(dirName, contents);
        }

        void stubDirContents(File dir, File... contentFiles) {
            final String dirName = fileToString(dir);
            final String[] contents = new String[contentFiles.length];
            for (int i = 0; i < contents.length; i++) {
                contents[i] = contentFiles[i].getName();
            }
            stubDirContents(dirName, contents);
        }

        void stubEmptyDir(String dirName) {
            dirContents.put(dirName, new String[0]);
        }

        void stubEmptyDir(File dir) {
            final String dirName = fileToString(dir);
            stubEmptyDir(dirName);
        }

        List<String> getDeletions() {
            return deletedFiles;
        }

        @Override
        protected String[] listDirContents(File f) {
            return dirContents.get(fileToString(f));
        }

        @Override
        protected boolean deleteFile(File f) {
            return deletedFiles.add(fileToString(f));
        }
    }

    static String fileToString(File f) {
        return f.getPath();
    }
}
