package to.rtc.cli.migrate.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

public class DirectoryUtils {
    /**
     * Files/directories that we usually want to ignore. e.g. git and rtc meta-data
     * folders, plus .gitattributes and .gitignore files.
     */
    public static final Pattern[] PATTERNS_TO_IGNORE = new Pattern[] { Pattern.compile("^\\.git/$"),
            Pattern.compile("^\\.gitattributes$"), Pattern.compile("^\\.gitignore$"), Pattern.compile("/\\.gitignore$"),
            Pattern.compile("^\\.jazz5/$"), Pattern.compile("^\\.jazzShed/$"), Pattern.compile("^\\.metadata/$") };

    private static final char ourSeparatorChar = '/';
    private static final String ourSeparator = "" + ourSeparatorChar;

    /**
     * Given a starting directory and (optionally) a set of exclusion filters, lists
     * everything.
     * 
     * @param basedir    Starting point.
     * @param exclusions If any match the path (relative to the basedir) then the
     *                   file/folder (and any folder content) will be excluded from
     *                   the list. Note that the string these exclusions will be
     *                   matched against has no '.' or '/' prefix for the basedir
     *                   but will have a suffix of '/' for directories.
     * @return Sorted set of paths (relative to the base dir).
     */
    public Set<String> getRecursiveDirListing(File basedir, Pattern... exclusions) {
        final Map<String, String> result = getRecursiveDirListingWithHash(basedir, NULL_FILE_HASHER, exclusions);
        return result.keySet();
    }

    /**
     * Given a starting directory and (optionally) a set of exclusion filters, lists
     * everything and calculates a hash indicative of the file content (that's likely
     * to be different only if the file has been changed).
     * 
     * @param basedir    Starting point.
     * @param exclusions If any match the path (relative to the basedir) then the
     *                   file/folder (and any folder content) will be excluded from
     *                   the list. Note that the string these exclusions will be
     *                   matched against has no '.' or '/' prefix for the basedir
     *                   but will have a suffix of '/' for directories.
     * @return Sorted Map of paths (relative to the base dir) to some hash of the file content.
     */
    public Map<String, Object> getRecursiveDirContentsHash(File basedir, Pattern... exclusions) {
        return getRecursiveDirListingWithHash(basedir, SIZE_AND_MODIFICATION_FILE_HASHER, exclusions);
    }

    /**
     * Works out what's been added to oldListing.
     * 
     * @param oldListing     The original list of files & directories.
     * @param currentListing The current list of files & directories.
     * @return A trimmed set of what's in currentListing but not oldListing,
     *         truncated so we don't mention folder contents if the folder is new.
     */
    public Set<String> calcNewlyAddedFiles(Set<String> oldListing, Set<String> currentListing) {
        final Set<String> allNewEntries = new TreeSet<String>(currentListing);
        allNewEntries.removeAll(oldListing);
        final Set<String> results = new TreeSet<String>(allNewEntries);
        for (final String newEntry : allNewEntries) {
            if (newEntry.endsWith(ourSeparator)) {
                // it's a new directory, so we don't need to mention the new contents
                final Iterator<String> ri = results.iterator();
                while (ri.hasNext()) {
                    final String r = ri.next();
                    if (r != newEntry && r.startsWith(newEntry)) {
                        ri.remove();
                    }
                }
            }
        }
        return results;
    }

    /**
     * Determines if what we've got matches what we expected. Given a list of
     * newly-added content (e.g. from {@link #calcNewlyAddedFiles(Set, Set)}), and
     * details of what we expected to happen, works out what (if any) of the
     * newly-added content couldn't possibly have got there legitimately.
     * 
     * @param anticipatedContent                         All the files and folders
     *                                                   we were told to expect to
     *                                                   be created.
     * @param anticipatedFoldersThatMightContainAnything The new name of any folders
     *                                                   that were renamed (and
     *                                                   therefore might
     *                                                   legitimately contain stuff
     *                                                   we weren't fully aware of).
     * @param actualContent                              Everything that we know was
     *                                                   added for real.
     * @return A subset of the <code>actualNewlyAddedContent</code> containing only
     *         entries which weren't in the <code>expectedNewlyAddedContent</code>
     *         or any folders in
     *         <code>expectedNewlyMovedFoldersThatMightContainAnything</code>.
     */
    public Collection<String> calcUnexpectedContent(final Collection<String> anticipatedContent,
            final Collection<String> anticipatedFoldersThatMightContainAnything,
            final Collection<String> actualContent) {
        final Set<String> unexpectedContent = new TreeSet<String>(actualContent);
        unexpectedContent.removeAll(anticipatedContent);
        // Any files found inside a folder that was moved get a free pass.
        // While we could tell if a file/subfolder was in the folder before it was
        // moved, we can't be sure that any files we find in a moved folder's new
        // location aren't valid because we don't know if anything was put there
        // just before it was moved (it depends on the order of operations and only
        // RTC knows that).
        // So, any file detected in a path that starts with "where a folder was
        // moved to" might be legitimate, so we don't complain about it.
        for (final String newNameOfMovedFolder : anticipatedFoldersThatMightContainAnything) {
            final Iterator<String> i = unexpectedContent.iterator();
            while (i.hasNext()) {
                final String unexpectedAddedFileOrFolder = i.next();
                if (unexpectedAddedFileOrFolder.startsWith(newNameOfMovedFolder)) {
                    i.remove();
                }
            }
        }
        return unexpectedContent;
    }

    /**
     * Creates a multi-line string that lists the state of the specified files
     * within the specified folder.
     * 
     * @param basedir The folder on the filesystem that everything is relative to.
     * @param prefix  What to put at the start of the output as a one-off.
     * @param lineSep What to write in between file entries.
     * @param recurse Whether or not to recurse into directories.
     * @param files   Names of files to show.
     * @return A multi-line string.
     */
    public String logFileList(final File basedir, final String prefix, final String lineSep, final boolean recurse,
            final Iterable<String> files) {
        final StringBuilder out = new StringBuilder(prefix);
        listFiles(basedir, out, lineSep, recurse, files);
        final String multiLineString = out.toString();
        return multiLineString;
    }

    private void listFiles(final File basedir, final StringBuilder out, final String lineSeperator,
            final boolean recurse, final Iterable<String> filenames) {
        final Iterable<String> sortedList = recurse ? toFullyRecursiveSortedList(basedir, filenames)
                : toSortedList(basedir, filenames);
        final Iterator<String> iterator = sortedList.iterator();
        while (iterator.hasNext()) {
            final String filename = iterator.next();
            final File file = new File(basedir, filename);
            final boolean isDir = file.isDirectory();
            final boolean isFile = file.isFile();
            final boolean x = file.canExecute();
            final boolean r = file.canRead();
            final boolean w = file.canWrite();
            out.append(isDir ? 'd' : '-');
            out.append(r ? 'r' : '-');
            out.append(w ? 'w' : '-');
            out.append(x ? 'x' : '-');
            out.append(' ');
            final Long size;
            if (isFile) {
                size = Long.valueOf(file.length());
            } else if (isDir) {
                final String[] dirList = file.list();
                size = dirList == null ? null : Long.valueOf(dirList.length);
            } else {
                size = null;
            }
            if (size != null) {
                final long bytes = size.longValue();
                final String number;
                final String units;
                // 999999 as-is
                // 99999k
                // 99999m
                if (bytes < 1000000L) {
                    number = Long.toString(bytes);
                    units = "";
                } else if (bytes < 102400000L) {
                    number = Long.toString(bytes / 1024L);
                    units = "k";
                } else {
                    number = Long.toString(bytes / 1024L / 1024L);
                    units = "m";
                }
                final int desiredLength = 6;
                final int paddingNeeded = desiredLength - number.length() - units.length();
                for (int i = 0; i < paddingNeeded; i++) {
                    out.append(' ');
                }
                out.append(number).append(units);
            } else {
                out.append("......");
            }
            out.append(' ');
            out.append(filename);
            if (isDir && !filename.endsWith(File.separator)) {
                out.append(File.separatorChar);
            }
            if (iterator.hasNext()) {
                out.append(lineSeperator);
            }
        }
    }

    private Iterable<String> toFullyRecursiveSortedList(final File basedir, final Iterable<String> filenames) {
        final TreeSet<String> result = new TreeSet<String>();
        for (final String filename : filenames) {
            addFolderContents(basedir, true, result, filename);
        }
        return result;
    }

    private Iterable<String> toSortedList(final File basedir, final Iterable<String> filenames) {
        final TreeSet<String> result = new TreeSet<String>();
        for (final String filename : filenames) {
            addFolderContents(basedir, false, result, filename);
        }
        return result;
    }

    private void addFolderContents(final File basedir, final boolean recurse, final Collection<String> toBeAddedTo,
            final String... filenames) {
        for (final String filename : filenames) {
            boolean wasntAlreadyThere = toBeAddedTo.add(filename);
            if (wasntAlreadyThere && recurse) {
                final File potentialDir = new File(basedir, filename);
                final String[] dirContents = potentialDir.list();
                if (dirContents != null) {
                    for (int i = 0; i < dirContents.length; i++) {
                        dirContents[i] = filename + File.separatorChar + dirContents[i];
                    }
                    addFolderContents(basedir, recurse, toBeAddedTo, dirContents);
                }
            }
        }
    }

    private <T> Map<String, T> getRecursiveDirListingWithHash(File basedir, IFileHasher<T> hashFunction, Pattern... exclusions) {
        final String basedirPath = toPath(basedir);
        final Map<String, T> result = new TreeMap<String,T>();
        appendRecursiveDirListing(result, hashFunction, basedirPath.length(), basedir, exclusions);
        result.remove("");
        return result;
    }

    /**
     * @return true if the result was appended, false if it was excluded or already
     *         present.
     */
    private <T> boolean appendRecursiveDirListing(Map<String, T> result, IFileHasher<T> hashFunction, int lengthOfBasedirPath, File dir,
            Pattern[] exclusions) {
        if (!appendEntry(result, hashFunction, lengthOfBasedirPath, dir, exclusions)) {
            return false; // excluded or already present, so we stop here
        }
        final File[] dirEntries = listDirContents(dir);
        boolean somethingAppended = false;
        for (final File entry : dirEntries) {
            somethingAppended |= appendRecursiveDirListing(result, hashFunction, lengthOfBasedirPath, entry, exclusions);
        }
        return somethingAppended;
    }

    /**
     * @return true if the result was appended, false if it was excluded or already
     *         present.
     */
    private <T> boolean appendEntry(Map<String, T> result, IFileHasher<T> hashFunction, int lengthOfBasedirPath, File toBeAppended, Pattern[] exclusions) {
        final String path = toPath(toBeAppended);
        final String subPath = path.substring(lengthOfBasedirPath);
        for (Pattern p : exclusions) {
            if (p.matcher(subPath).matches()) {
                return false;
            }
        }
        final T fileHash;
        try {
            fileHash = hashFunction.getHash(toBeAppended);
        } catch (IOException ex) {
            throw new IllegalStateException("Error invoking " + hashFunction + ".getHash(" + toBeAppended + ").", ex);
        }
        final T oldFileHash = result.put(subPath, fileHash);
        return oldFileHash==null || !oldFileHash.equals(fileHash);
    }

    protected static final File[] NO_FILES = new File[0];

    /**
     * Overridable for test purposes. Lists dir contents.
     * 
     * @param dir Dir whose contents is to be listed.
     * @return Never null.
     */
    protected File[] listDirContents(File dir) {
        final File[] result = dir.listFiles();
        return result == null ? NO_FILES : result;
    }

    /**
     * Overridable for test purposes
     * 
     * @param f The file-or-directory in question
     * @return true if given {@link File#isDirectory()} is true.
     */
    protected boolean isDirectory(File f) {
        return f.isDirectory();
    }

    /**
     * Gets a platform-independent path that always uses / for path separators, and
     * appends a / after directory names.
     * 
     * @param f File whose path we need
     * @return Path to the file/dir using unix-style path separators.
     */
    private String toPath(File f) {
        final String nativePath = f.getPath();
        final String ourPath = File.separatorChar != ourSeparatorChar
                ? nativePath.replace(File.separatorChar, ourSeparatorChar)
                : nativePath;
        if (isDirectory(f)) {
            return ourPath + ourSeparatorChar;
        }
        return ourPath;
    }

    /**
     * Means of turning a {@link File} into some form of checksum/hash/etc that can
     * be used to determine if the file has changed or not later.
     * @param <T> The type that stores a hash.
     */
    private interface IFileHasher<T> {
        /**
         * Called by {@link DirectoryUtils#getRecursiveDirListing(File, Pattern...)} in
         * order to snapshot the state of files we encounter.
         * 
         * @param fileToBeHashed A file we've found.
         * @return An Object that should only change if the file contents changes (and
         *         will not change if the file content hasn't changed). This MUST NOT be
         *         null.
         */
        T getHash(File fileToBeHashed) throws IOException;
    }

    private static class NullFileHasher implements IFileHasher<String> {
        @Override
        public String getHash(File fileToBeHashed) {
            return "";
        }
    }

    private static class SizeAndModificationTimeFileHasher implements IFileHasher<Object> {
        @Override
        public String getHash(File fileToBeHashed) throws IOException {
            final long fileLength = fileToBeHashed.length();
            Path path = fileToBeHashed.toPath();
            final FileTime lastModifiedTime = java.nio.file.Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS);
            return fileLength + "," + lastModifiedTime.toMillis();
        }
    }

    private static final IFileHasher<String> NULL_FILE_HASHER = new NullFileHasher();
    private static final IFileHasher<Object> SIZE_AND_MODIFICATION_FILE_HASHER = new SizeAndModificationTimeFileHasher();
}
