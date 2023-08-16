package to.rtc.cli.migrate.command;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.ibm.team.filesystem.cli.client.internal.subcommands.StatusCmd;
import com.ibm.team.filesystem.cli.client.internal.subcommands.StatusCmdOpts;
import com.ibm.team.filesystem.cli.core.AbstractSubcommand;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;

public class StatusCommandDelegate extends RtcCommandDelegate {

	public StatusCommandDelegate(IScmClientConfiguration config, IChangeLogOutput output, String username,
			String password, File sandboxDirOrNull, boolean json, boolean verbose, boolean all, boolean excludeIncoming, boolean xchangeset, boolean locateConflicts) {
		super(config, output, mkCommandLineString("show status", mkCommandLineArgs("...", "...", sandboxDirOrNull, json, verbose, all, excludeIncoming, xchangeset, locateConflicts)));
		setSubCommandLine(config, generateCommandLine(mkCommandLineArgs(username, password, sandboxDirOrNull, json, verbose, all, excludeIncoming, xchangeset, locateConflicts)));
	}

	@Override
	AbstractSubcommand getCommand() {
		return new StatusCmd();
	}

	@Override
	Options getOptions() throws ConflictingOptionException {
		return new StatusCmdOpts().getOptions();
	}

	private static List<String> mkCommandLineArgs(String username, String password, File sandboxDirOrNull, boolean json,
			boolean verbose, boolean all, boolean excludeIncoming, boolean xchangeset, boolean locateConflicts) {
		List<String> args = new ArrayList<String>();
		args.add("-u");
		args.add(username);
		args.add("-P");
		args.add(password);
		if (json) {
			args.add("--json");
		}
		if (verbose) {
			args.add("--verbose");
		}
		if (all) {
			args.add("--all");
		}
		if (excludeIncoming) {
			args.add("--exclude");
			args.add("in:cbC");
		}
		if (sandboxDirOrNull != null) {
			args.add("-d");
			args.add(sandboxDirOrNull.getPath());
		}
		if (locateConflicts) {
			args.add("--locate-conflicts");
		}
		if (xchangeset) {
			args.add("--xchangeset");
		}
		return args;
	}
}
