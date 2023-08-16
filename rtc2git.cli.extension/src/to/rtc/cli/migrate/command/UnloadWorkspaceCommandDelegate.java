package to.rtc.cli.migrate.command;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.ibm.team.filesystem.cli.client.internal.workspace.UnloadWorkspaceCmd;
import com.ibm.team.filesystem.cli.core.AbstractSubcommand;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;

public class UnloadWorkspaceCommandDelegate extends RtcCommandDelegate {

	public UnloadWorkspaceCommandDelegate(IScmClientConfiguration config, IChangeLogOutput output, String uri, String username,
			String password, File sandboxDirOrNull, boolean noLocalRefresh, boolean overwriteUncommitted, boolean deleteLocalFilesFromSandbox, String workspaceOrNull) {
		super(config, output, mkCommandLineString("unload",
				mkCommandLineArgs("...", "...", "...", sandboxDirOrNull, noLocalRefresh, overwriteUncommitted, deleteLocalFilesFromSandbox, workspaceOrNull)));
		setSubCommandLine(config,
				generateCommandLine(mkCommandLineArgs(uri, username, password, sandboxDirOrNull, noLocalRefresh, overwriteUncommitted, deleteLocalFilesFromSandbox, workspaceOrNull)));
	}

	@Override
	AbstractSubcommand getCommand() {
		return new UnloadWorkspaceCmd();
	}

	@Override
	Options getOptions() throws ConflictingOptionException {
		return new UnloadWorkspaceCmd().getOptions();
	}

	private static List<String> mkCommandLineArgs(String uri, String username, String password, File sandboxDirOrNull,
			boolean noLocalRefresh, boolean overwriteUncommitted, boolean deleteLocalFilesFromSandbox, String workspaceOrNull) {
		List<String> args = new ArrayList<String>();
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
		if (noLocalRefresh) {
		args.add("-N");
		}
		if (overwriteUncommitted) {
			args.add("-o");
		}
		if (deleteLocalFilesFromSandbox) {
			args.add("-D");
		}
		if (workspaceOrNull != null && !workspaceOrNull.isEmpty()) {
			args.add("-w");
			args.add(workspaceOrNull);
		}
		return args;
	}
}
