package to.rtc.cli.migrate.command;

import java.util.ArrayList;
import java.util.List;

import com.ibm.team.filesystem.cli.client.internal.listcommand.ListMissingChangeSetsCmd;
import com.ibm.team.filesystem.cli.core.AbstractSubcommand;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;

public class ListMissingChangeSetsCommandDelegate extends RtcCommandDelegate {

	public ListMissingChangeSetsCommandDelegate(IScmClientConfiguration config, IChangeLogOutput output, String uri, String username,
			String password, String workspace, String changeSetUuid, boolean summarize, boolean json) {
		super(config, output, mkCommandLineString("list missing-changesets", mkCommandLineArgs("...", "...", "...", workspace,
				changeSetUuid, summarize, json)));
		setSubCommandLine(config, generateCommandLine(mkCommandLineArgs(uri, username, password, workspace,
				changeSetUuid, summarize, json)));
	}

	@Override
	AbstractSubcommand getCommand() {
		return new ListMissingChangeSetsCmd();
	}

	@Override
	Options getOptions() throws ConflictingOptionException {
		return new ListMissingChangeSetsCmd().getOptions();
	}

	private static List<String> mkCommandLineArgs(String uri, String username, String password, String workspace,
			String changeSetUuid, boolean summarize, boolean json) {
		List<String> args = new ArrayList<String>();
		args.add("-w");
		args.add(workspace);
		if (summarize) {
			args.add("--summarize-changes");
		}
		args.add("-r");
		args.add(uri);
		args.add("-u");
		args.add(username);
		args.add("-P");
		args.add(password);
		if (json) {
			args.add("--json");
		}
		args.add(changeSetUuid);
		return args;
	}
}
