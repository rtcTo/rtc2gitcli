package to.rtc.cli.migrate.command;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.ibm.team.filesystem.cli.client.internal.subcommands.RefreshCmd;
import com.ibm.team.filesystem.cli.client.internal.subcommands.RefreshCmdOpts;
import com.ibm.team.filesystem.cli.core.AbstractSubcommand;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;

public class RefreshCommandDelegate extends RtcCommandDelegate {

	public RefreshCommandDelegate(IScmClientConfiguration config, IChangeLogOutput output, File sandboxDirOrNull) {
		super(config, output, mkCommandLineString("refresh", mkCommandLineArgs(sandboxDirOrNull)));
		setSubCommandLineByReflection(config, sandboxDirOrNull);
	}

	@Override
	AbstractSubcommand getCommand() {
		return new RefreshCmd();
	}

	@Override
	Options getOptions() throws ConflictingOptionException {
		return new RefreshCmdOpts().getOptions();
	}

	private void setSubCommandLineByReflection(IScmClientConfiguration config, File sandboxDirOrNull) {
		List<String> args = mkCommandLineArgs(sandboxDirOrNull);
		setSubCommandLine(config, generateCommandLine(args));
	}

	private static List<String> mkCommandLineArgs(File sandboxDirOrNull) {
		List<String> args = new ArrayList<String>();
		if ( sandboxDirOrNull!=null ) {
			args.add("-d");
			args.add(sandboxDirOrNull.getPath());
		}
		return args;
	}
}
