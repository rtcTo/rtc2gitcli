/**
 * Class is explicit in this package to get access to the out memeber. The access is required to print out stack traces to the same log file as all other output.
 */
package com.ibm.team.filesystem.rcp.core.internal.changelog;

import java.io.PrintStream;

public class AbstractDateTimeStreamOutput extends ChangeLogStreamOutput {
	public AbstractDateTimeStreamOutput(PrintStream out) {
		super(out);
	}

	public PrintStream getOutputStream() {
		return out;
	}
}
