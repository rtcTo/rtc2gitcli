package to.rtc.cli.migrate;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.ibm.team.filesystem.rcp.core.internal.changelog.ChangeLogStreamOutput;

public class DateTimeStreamOutput extends ChangeLogStreamOutput {
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss");

	public DateTimeStreamOutput(PrintStream out) {
		super(out);
	}

	@Override
	public void writeLine(String message) {
		super.writeLine(formatMessage(message));
	}

	@Override
	public void writeLn(String message) {
		super.writeLn(formatMessage(message));
	}

	private String formatMessage(String message) {
		return DATE_FORMAT.format(new Date()) + ": " + message;
	}
}
