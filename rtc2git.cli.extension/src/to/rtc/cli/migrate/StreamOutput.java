package to.rtc.cli.migrate;

import java.io.PrintStream;

import com.ibm.team.filesystem.rcp.core.internal.changelog.ChangeLogStreamOutput;

public class StreamOutput extends ChangeLogStreamOutput {

	private PrintStream stream;

	StreamOutput(PrintStream out) {
		super(out);
		stream = out;
	}

	PrintStream getOutputStream() {
		return stream;
	}
}
