package to.rtc.cli.migrate;

import java.io.PrintStream;
import java.util.Date;

import com.ibm.team.filesystem.rcp.core.internal.changelog.ChangeLogStreamOutput;

public class DateTimeStreamOutput extends ChangeLogStreamOutput {
	private int indent;

	public DateTimeStreamOutput(PrintStream out) {
		super(out);
		indent = 0;
	}

	@Override
	public void writeLine(String message) {
		super.writeLine(formatMessage(message));
	}

	@Override
	public void writeLn(String message) {
		super.writeLn(formatMessage(message));
	}

	@Override
	public void setIndent(int indent) {
		this.indent = indent;
	}

	private String formatMessage(String message) {
		if (indent > 0 && message != null) {
			return String.format("[%1$tY-%1$tm-%1$td %1$tT] %2$" + (indent + message.length()) + "s", new Date(),
					message);
		}
		return String.format("[%1$tY-%1$tm-%1$td %1$tT] %2$s", new Date(), message);
	}


	public PrintStream getOutputStream() {
		return System.out;
	}
}
