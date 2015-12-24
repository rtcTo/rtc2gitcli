package to.rtc.cli.migrate.command;

import java.util.ArrayList;
import java.util.List;

import com.ibm.team.filesystem.cli.client.internal.subcommands.LoadCmdLauncher;
import com.ibm.team.filesystem.cli.client.internal.subcommands.LoadCmdOptions;
import com.ibm.team.filesystem.cli.core.AbstractSubcommand;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;

@SuppressWarnings("restriction")
public class LoadCommandDelegate extends RtcCommandDelegate {

	public LoadCommandDelegate(IScmClientConfiguration config, IChangeLogOutput output, String workspace,
			String component, boolean force) {
		super(config, output, "load " + workspace + " force[" + force + "]");
		setSubCommandLineByReflection(config, workspace, component, force);
	}

	@Override
	AbstractSubcommand getCommand() {
		return new LoadCmdLauncher();
	}

	@Override
	Options getOptions() throws ConflictingOptionException {
		return new LoadCmdOptions().getOptions();
	}

	private void setSubCommandLineByReflection(IScmClientConfiguration config, String workspace, String component,
			boolean force) {
		String uri = getSubCommandOption(config, CommonOptions.OPT_URI);
		String username = getSubCommandOption(config, CommonOptions.OPT_USERNAME);
		String password;
		if (config.getSubcommandCommandLine().hasOption(CommonOptions.OPT_PASSWORD)) {
			password = getSubCommandOption(config, CommonOptions.OPT_PASSWORD);
		} else {
			try {
				password = config.getConnectionInfo().getPassword();
			} catch (FileSystemException e) {
				throw new RuntimeException("Unable to get password", e);
			}
		}
		setSubCommandLine(config, generateCommandLine(uri, username, password, workspace, component, force));
	}

	private ICommandLine generateCommandLine(String uri, String username, String password, String workspace,
			String component, boolean force) {
		List<String> args = new ArrayList<String>();
		args.add("-r");
		args.add(uri);
		args.add("-u");
		args.add(username);
		args.add("-P");
		args.add(password);

		if (force) {
			args.add("--force");
		}

		args.add(workspace);
		if (component != null) {
			args.add(component);
		}

		return generateCommandLine(args);
	}
}
