package to.rtc.cli.migrate.util;

import java.util.ArrayList;
import java.util.List;

import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;

public class CapturingWrapper {
    private final StringBuilder currentLine = new StringBuilder();
    private final List<String> completeLines = new ArrayList<String>();

    /**
     * Creates a new instance that wraps (and delegates to) another
     * {@link IChangeLogOutput}.
     * 
     * @param scmClientConfiguration The config we also need to intercept.
     * 
     * @param delegate               The {@link IChangeLogOutput} that we'll
     *                               delegate to.
     */
    public CapturingWrapper() {
    }

/*
    private final Map<IChangeLogOutput, CapturingChangeLogOutput> changeLogOutputs = new IdentityHashMap<IChangeLogOutput, CapturingChangeLogOutput>();
    private final Map<IScmClientConfiguration, CapturingScmClientConfiguration> scmClientConfigurations = new IdentityHashMap<IScmClientConfiguration, CapturingScmClientConfiguration>();

    public CapturingChangeLogOutput getChangeLogOutput(final IChangeLogOutput toBeWrapped) {
        if (toBeWrapped == null) {
            return null;
        }
        if (toBeWrapped instanceof CapturingChangeLogOutput) {
            return (CapturingChangeLogOutput) toBeWrapped;
        }
        CapturingChangeLogOutput wrapped = changeLogOutputs.get(toBeWrapped);
        if (wrapped == null) {
            wrapped = new CapturingChangeLogOutput(toBeWrapped, this);
            System.err.println("CapturingWrapper.getChangeLogOutput(" + toBeWrapped
                    + ") created new CapturingChangeLogOutput(" + toBeWrapped + ", " + this + ")");
            changeLogOutputs.put(toBeWrapped, wrapped);
        }
        return wrapped;
    }

    public CapturingScmClientConfiguration getScmClientConfiguration(final IScmClientConfiguration toBeWrapped) {
        if (toBeWrapped == null) {
            return null;
        }
        if (toBeWrapped instanceof CapturingScmClientConfiguration) {
            return (CapturingScmClientConfiguration) toBeWrapped;
        }
        CapturingScmClientConfiguration wrapped = scmClientConfigurations.get(toBeWrapped);
        if (wrapped == null) {
            wrapped = new CapturingScmClientConfiguration(toBeWrapped, this);
            System.err.println("CapturingWrapper.getScmClientConfiguration(" + toBeWrapped
                    + ") created new CapturingScmClientConfiguration(" + toBeWrapped + ", " + this + ")");
            scmClientConfigurations.put(toBeWrapped, wrapped);
        }
        return wrapped;
    }
*/

    /**
     * Wipes the captured log clean.
     */
    public void clear() {
        completeLines.clear();
        currentLine.setLength(0);
    }

    /**
     * Returns the underlying {@link List} where we've recorded every complete line.
     * The first line will typically say what DelegateCommand is running.
     * The last line will typically say what ran and what the result was.
     * Everything inbetween should be the command's output. 
     * 
     * @return A non-null list.
     */
    public List<String> getCompleteLines() {
        checkForCompletedLines();
        return completeLines;
    }

    StringBuilder getIncompleteLine() {
        checkForCompletedLines();
        return currentLine;
    }

    void completeIncompleteLine() {
        checkForCompletedLines();
        completeLines.add(currentLine.toString());
        currentLine.setLength(0);
    }

    private void checkForCompletedLines() {
        while (addCompleteLine()) {
            // just the check is enough
        }
    }

    private boolean addCompleteLine() {
        return addCompleteLine("\r\n") || addCompleteLine("\n");
    }

    private boolean addCompleteLine(String eol) {
        final int indexOfCrLf = currentLine.indexOf(eol);
        if (indexOfCrLf < 0) {
            return false;
        }
        final String line = currentLine.substring(0, indexOfCrLf);
        currentLine.delete(0, indexOfCrLf + eol.length());
        completeLines.add(line);
        return true;
    }
}
