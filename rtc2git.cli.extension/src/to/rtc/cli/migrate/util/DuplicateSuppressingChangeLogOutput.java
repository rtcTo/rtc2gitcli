package to.rtc.cli.migrate.util;

import java.util.HashSet;
import java.util.Set;

import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;

public class DuplicateSuppressingChangeLogOutput extends DelegatingChangeLogOutput {
    private final Set<String> knownOutput;

    public DuplicateSuppressingChangeLogOutput(IChangeLogOutput delegate) {
        super(delegate);
        this.knownOutput = new HashSet<String>();
    }

    /**
     * As {@link IChangeLogOutput#writeLine(String)}, but suppresses the output of
     * lines that we've already logged.
     * 
     * @param line The line to be written.
     */
    @Override
    public void writeLine(String line) {
        if (knownOutput.add(line)) {
            getDelegate().writeLine(line);
        }
    }
}