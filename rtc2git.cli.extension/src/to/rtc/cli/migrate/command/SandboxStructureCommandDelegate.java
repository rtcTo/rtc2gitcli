package to.rtc.cli.migrate.command;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.ibm.team.filesystem.cli.client.internal.subcommands.SandboxStructureCmd;
import com.ibm.team.filesystem.cli.client.internal.subcommands.SandboxStructureCmdOpts;
import com.ibm.team.filesystem.cli.core.AbstractSubcommand;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;

public class SandboxStructureCommandDelegate extends RtcCommandDelegate {

	public SandboxStructureCommandDelegate(IScmClientConfiguration config, IChangeLogOutput output, String username,
			String password, File sandboxDirOrNull) {
		super(config, output, mkCommandLineString("show sandbox-structure",
				mkCommandLineArgs("...", "...", sandboxDirOrNull)));
		setSubCommandLine(config,
				generateCommandLine(mkCommandLineArgs(username, password, sandboxDirOrNull)));
	}

	@Override
	AbstractSubcommand getCommand() {
		return new SandboxStructureCmd();
	}

	@Override
	Options getOptions() throws ConflictingOptionException {
		return new SandboxStructureCmdOpts().getOptions();
	}

	private static List<String> mkCommandLineArgs(String username, String password, File sandboxDirOrNull) {
		List<String> args = new ArrayList<String>();
		args.add("-u");
		args.add(username);
		args.add("-P");
		args.add(password);
		if (sandboxDirOrNull != null) {
			args.add("-d");
			args.add(sandboxDirOrNull.getPath());
		}
		return args;
	}
}
