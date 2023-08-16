package to.rtc.cli.migrate.util;

import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;

public class GitignoreRemoverUtil {
    /**
     * Remove folders that should've been deleted but weren't because there was a
     * .gitignore file in there.
     * <p>
     * When .jazzignore files are created, we automatically create .gitignore files.
     * When .jazzignore files are deletes along with the folder they're in, we can
     * fail to notice because RTC won't delete the .gitignore file and hence won't
     * delete the folder it's in.
     * </p>
     * Given a set of folders to check, if the folder contains a
     * <code>.gitignore</code> file and nothing else, the folder (and its contents)
     * are deleted.
     * 
     * @param foldersToCheck List of pathnames of folders that we think should've
     *                       been deleted.
     * @param rootDirectory  The base folder that <code>foldersToCheck</code> are
     *                       relative to.
     * @param output         What to log to if we do anything.
     * @return true if we removed anything, false if not.
     */
    public boolean removeFoldersKeptDueToLingeringGitignoreFiles(Collection<String> foldersToCheck, File rootDirectory,
            IChangeLogOutput output) {
        final Set<String> deletedDirs = new TreeSet<String>(DEPTH_FIRST);
        deletedDirs.addAll(foldersToCheck);
        final String rootDirPath = rootDirectory.getPath();
        boolean haveDeletedSomething = false;
        for (final String deletedDir : deletedDirs) {
            final File f = new File(rootDirectory, deletedDir);
            if (removeFolderIfItOnlyContainsAGitignoreFile(f, rootDirPath, output)) {
                haveDeletedSomething = true;
            }
        }
        return haveDeletedSomething;
    }

    private boolean removeFolderIfItOnlyContainsAGitignoreFile(final File dir, final String rootDirPath,
            final IChangeLogOutput output) {
        final String[] dirContents = listDirContents(dir);
        final String filename = ".gitignore";
        if (dirContents != null && dirContents.length == 1 && filename.equals(dirContents[0])) {
            // if folder is empty aside from the .gitignore file
            final File c = new File(dir, filename);
            final boolean cDeleted = deleteFile(c);
            final boolean dirDeleted = deleteFile(dir);
            final String dirPath = dir.getPath();
            final String dirRelativePath = dirPath.substring(rootDirPath.length());
            if (cDeleted && dirDeleted) {
                output.writeLine("INFO: Removed " + filename + " file from " + dirRelativePath
                        + " and deleted folder as changeset required.");
            } else {
                output.writeLine("WARN: Unable to remove " + filename + " file from " + dirRelativePath
                        + " and delete folder as changeset required.");
            }
            return dirDeleted;
        }
        return false;
    }

    protected String[] listDirContents(final File f) {
        return f.list();
    }

    protected boolean deleteFile(File f) {
        return f.delete();
    }

    private static final Comparator<String> DEPTH_FIRST = new DepthFirst();

    private static class DepthFirst implements Comparator<String> {
        @Override
        public int compare(String first, String second) {
            // Returns a negative integer, zero, or a positive integer as the first argument
            // is less than, equal to, or greater than the second.
            if (first.startsWith(second)) {
                // first is a subset of the second, so it's probably a parent dir, so it must
                // come later
                return -1;
            }
            if (second.startsWith(first)) {
                // second is a subset of the first, so it's probably a parent dir, so it must
                // come later
                return 1;
            }
            return first.compareTo(second);
        }
    }
}
