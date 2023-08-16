package to.rtc.cli.migrate.util;

import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;

public final class NoOpChangeLogOutput implements IChangeLogOutput {
	public static final IChangeLogOutput INSTANCE = new NoOpChangeLogOutput();

	@Override
	public void setIndent(int arg0) {
		// do nothing
	}

	@Override
	public void writeLine(String arg0) {
		// do nothing
	}
}
