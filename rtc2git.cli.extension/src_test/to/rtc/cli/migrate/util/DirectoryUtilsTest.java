package to.rtc.cli.migrate.util;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.Test;

public class DirectoryUtilsTest {
    @Test
    public void getRecursiveDirListingGivenNoExclusionsThenListsEverything() {
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
        instance.stubDirContents(bar, barSubdir, barEmpty);
        instance.stubDirContents(barSubdir, barSubdirGitignore);
        instance.stubEmptyDir(barEmpty);
        final List<String> expected = Arrays.asList("bar/", "bar/empty/", "bar/subdir/", "bar/subdir/.gitignore",
                "foo/", "foo/.gitignore", "foo/.jazzignore");

        // When
        final Set<String> actual = instance.getRecursiveDirListing(rootDirectory);

        // Then
        assertThat(new ArrayList<String>(actual), equalTo(new ArrayList<String>(expected)));
    }

    @Test
    public void getRecursiveDirListingGivenExclusionsThenListsEverythingThatWasntExcluded() {
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
        instance.stubDirContents(bar, barSubdir, barEmpty);
        instance.stubDirContents(barSubdir, barSubdirGitignore);
        instance.stubEmptyDir(barEmpty);
        final Pattern excludeFoo = Pattern.compile("^foo/$");
        final Pattern excludeGitignores = Pattern.compile("^.*\\.gitignore$");
        final List<String> expected = Arrays.asList("bar/", "bar/empty/", "bar/subdir/");

        // When
        final Set<String> actual = instance.getRecursiveDirListing(rootDirectory, excludeFoo, excludeGitignores);

        // Then
        assertThat(new ArrayList<String>(actual), equalTo(new ArrayList<String>(expected)));
    }

    @Test
    public void calcNewlyAddedFilesGivenNothingAddedThenReturnsNothing() {
        // Given
        final Set<String> oldListing = new HashSet<String>(Arrays.asList("bar/", "bar/empty/", "bar/subdir/",
                "bar/subdir/.gitignore", "foo/", "foo/.gitignore", "foo/.jazzignore"));
        final Set<String> currentListing = new HashSet<String>(oldListing);
        final Set<String> expected = new HashSet<String>();
        final TestInstance instance = new TestInstance();

        // When
        final Set<String> actual = instance.calcNewlyAddedFiles(oldListing, currentListing);

        // Then
        assertThat(new TreeSet<String>(actual), equalTo(new TreeSet<String>(expected)));
    }

    @Test
    public void calcNewlyAddedFilesGivenNewFolderWithContentsThenReturnsJustTheFolder() {
        // Given
        final Set<String> oldListing = new HashSet<String>(Arrays.asList("foo/", "foo/.gitignore", "foo/.jazzignore"));
        final Set<String> currentListing = new HashSet<String>(Arrays.asList("bar/", "bar/empty/", "bar/subdir/",
                "bar/subdir/.gitignore", "foo/", "foo/.gitignore", "foo/.jazzignore"));
        final Set<String> expected = new HashSet<String>(Arrays.asList("bar/"));
        final TestInstance instance = new TestInstance();

        // When
        final Set<String> actual = instance.calcNewlyAddedFiles(oldListing, currentListing);

        // Then
        assertThat(new TreeSet<String>(actual), equalTo(new TreeSet<String>(expected)));
    }

    @Test
    public void calcNewlyAddedFilesGivenNewSubfoldersWithContentsThenReturnsJustTheSubfolders() {
        // Given
        final Set<String> oldListing = new HashSet<String>(
                Arrays.asList("bar/", "bar/somefile.txt", "foo/", "foo/.gitignore", "foo/.jazzignore"));
        final Set<String> currentListing = new HashSet<String>(Arrays.asList("bar/", "bar/empty/", "bar/subdir/",
                "bar/subdir/.gitignore", "foo/", "foo/.gitignore", "foo/.jazzignore"));
        final Set<String> expected = new HashSet<String>(Arrays.asList("bar/empty/", "bar/subdir/"));
        final TestInstance instance = new TestInstance();

        // When
        final Set<String> actual = instance.calcNewlyAddedFiles(oldListing, currentListing);

        // Then
        assertThat(new TreeSet<String>(actual), equalTo(new TreeSet<String>(expected)));
    }

    @Test
    public void calcNewlyAddedFilesGivenNewFilesThenReturnsNewFiles() {
        // Given
        final Set<String> oldListing = new HashSet<String>(
                Arrays.asList("bar/", "bar/subdir/", "bar/subdir/.gitignore", "foo/"));
        final Set<String> currentListing = new HashSet<String>(Arrays.asList("bar/", "bar/empty/", "bar/subdir/",
                "bar/subdir/.gitignore", "foo/", "foo/.gitignore", "foo/.jazzignore"));
        final Set<String> expected = new HashSet<String>(
                Arrays.asList("bar/empty/", "foo/.gitignore", "foo/.jazzignore"));
        final TestInstance instance = new TestInstance();

        // When
        final Set<String> actual = instance.calcNewlyAddedFiles(oldListing, currentListing);

        // Then
        assertThat(new TreeSet<String>(actual), equalTo(new TreeSet<String>(expected)));
    }

    @Test
    public void calcUnexpectedContentGivenNothingExpectedThenReturnsEverything() {
        // Given
        final Collection<String> anticipatedContent = Arrays.asList("ExpectedButNotPresent");
        final Collection<String> anticipatedFoldersThatMightContainAnything = Arrays.asList("Expected/But/Not/Present/");
        final Collection<String> actualContent = Arrays.asList("Foo", "Bar/File.txt");
        final Collection<String> expected = Arrays.asList("Foo", "Bar/File.txt");
        final TestInstance instance = new TestInstance();

        // When
        final Collection<String> actual = instance.calcUnexpectedContent(anticipatedContent,
                anticipatedFoldersThatMightContainAnything, actualContent);

        // Then
        assertThat(new TreeSet<String>(actual), equalTo(new TreeSet<String>(expected)));
    }

    @Test
    public void calcUnexpectedContentGivenExpectedFilesThenReturnsEverythingElse() {
        // Given
        final Collection<String> anticipatedContent = Arrays.asList("Foo", "ExpectedButNotPresent");
        final Collection<String> anticipatedFoldersThatMightContainAnything = Arrays.asList("Expected/But/Not/Present/");
        final Collection<String> actualContent = Arrays.asList("Foo", "Bar/File.txt");
        final Collection<String> expected = Arrays.asList("Bar/File.txt");
        final TestInstance instance = new TestInstance();

        // When
        final Collection<String> actual = instance.calcUnexpectedContent(anticipatedContent,
                anticipatedFoldersThatMightContainAnything, actualContent);

        // Then
        assertThat(new TreeSet<String>(actual), equalTo(new TreeSet<String>(expected)));
    }

    @Test
    public void calcUnexpectedContentGivenExpectedFoldersThenReturnsEverythingElse() {
        // Given
        final Collection<String> anticipatedContent = Arrays.asList("ExpectedButNotPresent");
        final Collection<String> anticipatedFoldersThatMightContainAnything = Arrays.asList("Bar/", "Expected/But/Not/Present/");
        final Collection<String> actualContent = Arrays.asList("Foo", "Bar/File.txt");
        final Collection<String> expected = Arrays.asList("Foo");
        final TestInstance instance = new TestInstance();

        // When
        final Collection<String> actual = instance.calcUnexpectedContent(anticipatedContent,
                anticipatedFoldersThatMightContainAnything, actualContent);

        // Then
        assertThat(new TreeSet<String>(actual), equalTo(new TreeSet<String>(expected)));
    }

    private static class TestInstance extends DirectoryUtils {
        private final Map<String, String[]> dirContents = new HashMap<String, String[]>();

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

        @Override
        protected File[] listDirContents(File dir) {
            final String[] names = dirContents.get(fileToString(dir));
            if (names == null)
                return NO_FILES;
            final int n = names.length;
            final File[] files = new File[n];
            for (int i = 0; i < n; i++) {
                files[i] = new File(dir, names[i]);
            }
            return files;
        }

        @Override
        protected boolean isDirectory(File f) {
            return dirContents.containsKey(fileToString(f));
        }
    }

    static String fileToString(File f) {
        return f.getPath();
    }
}
