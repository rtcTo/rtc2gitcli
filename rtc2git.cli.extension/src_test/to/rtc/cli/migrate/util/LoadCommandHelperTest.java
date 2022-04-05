package to.rtc.cli.migrate.util;

import static org.hamcrest.CoreMatchers.any;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;

public class LoadCommandHelperTest {
    File sandboxDirectory;
    CapturingLogger output;

    @Before
    public void setup() throws IOException {
        sandboxDirectory = java.nio.file.Files.createTempDirectory(getClass().getSimpleName()).toFile();
        output = new CapturingLogger();
    }

    @After
    public void teardown() {
        if (sandboxDirectory != null) {
            Files.delete(sandboxDirectory);
        }
        sandboxDirectory = null;
        output = null;
    }

    @Test
    public void removeAnyFilesErroneouslyCreatedByScmLoadCommandWhenNoJazzFolderThenDoesNothing() throws IOException {
        // Given

        // When
        LoadCommandHelper.removeAnyFilesErroneouslyCreatedByScmLoadCommand(output, sandboxDirectory);

        // Then
        assertThat(output.linesWritten, not(hasItem(any(String.class))));
    }

    @Test
    public void removeAnyFilesErroneouslyCreatedByScmLoadCommandWhenBackupFolderExistsThenDeletes() throws IOException {
        // Given
        final File jazzFolder = new File(sandboxDirectory, ".jazzShed");
        final File backupFolder = new File(jazzFolder, "b22-03-03_08.46.03.337");
        jazzFolder.mkdir();
        backupFolder.mkdir();

        // When
        LoadCommandHelper.removeAnyFilesErroneouslyCreatedByScmLoadCommand(output, sandboxDirectory);

        // Then
        assertThat(output.linesWritten, hasItem(containsString(backupFolder.getPath())));
    }

    @Test
    public void removeAnyFilesErroneouslyCreatedByScmLoadCommandWhenOtherFolderExistThenDeletesOnlyBackupFolder()
            throws IOException {
        // Given
        final File jazzFolder = new File(sandboxDirectory, ".jazzShed");
        final File backupFolder = new File(jazzFolder, "b22-03-03_08.46.03.337");
        final File notABackupFolder = new File(jazzFolder, "someOtherFolder");
        jazzFolder.mkdir();
        backupFolder.mkdir();
        notABackupFolder.mkdir();

        // When
        LoadCommandHelper.removeAnyFilesErroneouslyCreatedByScmLoadCommand(output, sandboxDirectory);

        // Then
        assertThat(output.linesWritten, hasItem(containsString(backupFolder.getPath())));
        assertThat(output.linesWritten, not(hasItem(containsString(notABackupFolder.getPath()))));
    }

    private static class CapturingLogger implements IChangeLogOutput {
        final List<String> linesWritten = new ArrayList<String>();

        @Override
        public void writeLine(String line) {
            linesWritten.add(line);
            System.out.println(line);
        }

        @Override
        public void setIndent(int arg0) {
            // ignore
        }
    }
}
