package to.rtc.cli.migrate.util;

import java.io.File;
import java.io.FileFilter;
import java.util.regex.Pattern;

import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;

/**
 * We can't actually parse the output from <code>scm load</code>.
 * <p>
 * Unfortunately the scm load command has a bug whereby its output somehow
 * manages to bypass the normal output stream and instead goes direct to stdout.
 * This means we can't see what it is, so we can't parse it.
 * <p>
 * So, instead of seeing what the scm load command said and reacting to it, we
 * have to work blind instead.
 */
public class LoadCommandHelper {
    /**
     * The <code>scm load</code> can tell the user something like:<br>
     * <code>The following resources had uncommitted changes and were overwritten or merged with remote content:</code><br>
     * <code>  A copy of each file is available in "/pathToSandbox/.jazzShed/b22-03-03_08.46.03.337".</code><br>
     * So this pattern lets us find those folders.
     */
    private static final Pattern BACKUP_FOLDER_REGEX = Pattern
            .compile("^b[0-9][0-9]-[0-9][0-9]-[0-9][0-9]_[0-9][0-9]\\.[0-9][0-9]\\.[0-9][0-9]\\.[0-9][0-9][0-9]$");

    private static final class BackupFolderFileFilter implements FileFilter {
        @Override
        public boolean accept(File pathname) {
            if (!pathname.isDirectory()) {
                return false;
            }
            final String filename = pathname.getName();
            return BACKUP_FOLDER_REGEX.matcher(filename).matches();
        }
    }

    private static final BackupFolderFileFilter BACKUP_FOLDER_FILE_FILTER = new BackupFolderFileFilter();

    /**
     * Sadly, if RTC fails to update the filesystem correctly during an "accept",
     * when we later do a "load" it then claims that all the incorrect content it
     * left are "our" changes and it then stores them as a backup copy in the
     * .jazzShed folder so we need to remove those otherwise they'll fill up the
     * disk.
     * 
     * @param output           Where to log to.
     * @param sandboxDirectory Where the RTC sandbox folder is.
     */
    public static void removeAnyFilesErroneouslyCreatedByScmLoadCommand(final IChangeLogOutput output,
            final File sandboxDirectory) {
        final File jazzShedDir = new File(sandboxDirectory, ".jazzShed");
        final File[] backupFolders = jazzShedDir.listFiles(BACKUP_FOLDER_FILE_FILTER);
        if (backupFolders == null) {
            return;
        }
        for (final File d : backupFolders) {
            Files.delete(d);
            if (d.exists()) {
                output.writeLine("WARNING: Unable to delete data in '" + d + "'.");
            } else {
                output.writeLine("INFO: Deleted data in '" + d + "'.");
            }
        }
    }
}
