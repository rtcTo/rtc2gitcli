package to.rtc.cli.migrate.command;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IStatus;

import com.ibm.team.filesystem.cli.client.internal.subcommands.ConflictsCmd;
import com.ibm.team.filesystem.cli.client.internal.subcommands.ConflictsCmdOpts;
import com.ibm.team.filesystem.cli.core.AbstractSubcommand;
import com.ibm.team.filesystem.cli.core.Constants;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.rtc.cli.infrastructure.internal.core.CLIClientException;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;

public class ConflictsCommandDelegate extends RtcCommandDelegate {

	public ConflictsCommandDelegate(IScmClientConfiguration config, IChangeLogOutput output, String uri, String username,
			String password, File sandboxDirOrNull, boolean json, boolean local) {
		super(config, output, mkCommandLineString("show conflicts", mkCommandLineArgs("...", "...", "...", sandboxDirOrNull, json, local)));
		setSubCommandLine(config, generateCommandLine(mkCommandLineArgs(uri, username, password, sandboxDirOrNull, json, local)));
	}

	@Override
	public int run() throws CLIClientException {
		try {
			return super.run();
		} catch (CLIClientException e) {
			// This command throws exceptions under non-exceptional circumstances.
			// e.g. if there are conflicts to be shown, it throws a CLIClientException.
			// We want to suppress exceptions where it's not an error.
			IStatus status = e.getStatus();
			if (status != null) {
				switch (status.getCode()) {
				case Constants.STATUS_GAP:
					return Constants.STATUS_GAP;
				case Constants.STATUS_CONFLICT:
					return Constants.STATUS_CONFLICT;
				case Constants.STATUS_NWAY_CONFLICT:
					return Constants.STATUS_NWAY_CONFLICT;
				}
			}
			throw e;
		}
	}

	@Override
	AbstractSubcommand getCommand() {
		return new ConflictsCmd();
	}

	@Override
	Options getOptions() throws ConflictingOptionException {
		return new ConflictsCmdOpts().getOptions();
	}

	private static List<String> mkCommandLineArgs(String uri, String username, String password, File sandboxDirOrNull, boolean json,
			boolean local) {
		List<String> args = new ArrayList<String>();
		if (json) {
			args.add("--json");
		}
		args.add("-r");
		args.add(uri);
		args.add("-u");
		args.add(username);
		args.add("-P");
		args.add(password);
		if (sandboxDirOrNull != null) {
			args.add("-d");
			args.add(sandboxDirOrNull.getPath());
		}
		if (local) {
			args.add("--local");
		}
		return args;
	}
}
