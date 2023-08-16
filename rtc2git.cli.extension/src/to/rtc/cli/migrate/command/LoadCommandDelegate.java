package to.rtc.cli.migrate.command;

import java.util.ArrayList;
import java.util.List;

import com.ibm.team.filesystem.cli.client.internal.subcommands.LoadCmdLauncher;
import com.ibm.team.filesystem.cli.client.internal.subcommands.LoadCmdOptions;
import com.ibm.team.filesystem.cli.core.AbstractSubcommand;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;

public class LoadCommandDelegate extends RtcCommandDelegate {

	LoadCommandDelegate(IScmClientConfiguration config, IChangeLogOutput output, String uri, String username,
			String password, boolean allComponents, boolean force, boolean includeRoot, String workspace,
			String component) {
		super(config, output, mkCommandLineString("load",
				mkCommandLineArgs("...", "...", "...", allComponents, force, includeRoot, workspace, component)));
		setSubCommandLine(config, generateCommandLine(
				mkCommandLineArgs(uri, username, password, allComponents, force, includeRoot, workspace, component)));
	}

	/**
	 * Used to load a new component into an existing sandbox.
	 * 
	 * @param config      The {@link IScmClientConfiguration}
	 * @param output      What to log to.
	 * @param uri         RTC server URI
	 * @param username    Username to log in with
	 * @param password    Password for username.
	 * @param includeRoot Whether or not to load components into a subdirectory
	 *                    matching their own name. Necessary when a workspace has
	 *                    more than one component containing a files with the same
	 *                    path/name.
	 * @param workspace   The workspace containing the component.
	 * @param component   The component to be added to the sandbox (at whatever
	 *                    version the workspace says).
	 * @return A scm load command.
	 */
	public static LoadCommandDelegate addComponentToSandbox(IScmClientConfiguration config, IChangeLogOutput output,
			String uri, String username, String password, boolean includeRoot, String workspace, String component) {
		return new LoadCommandDelegate(config, output, uri, username, password, false, false, includeRoot, workspace,
				component);
	}

	/**
	 * Used to reload a workspace within the sandbox. Required if RTC has made a
	 * mess of things.
	 * 
	 * @param config      The {@link IScmClientConfiguration}
	 * @param output      What to log to.
	 * @param uri         RTC server URI
	 * @param username    Username to log in with
	 * @param password    Password for username.
	 * @param includeRoot Whether or not to load components into a subdirectory
	 *                    matching their own name. Necessary when a workspace has
	 *                    more than one component containing a files with the same
	 *                    path/name.
	 * @param workspace   The workspace containing the component.
	 * @return A scm (re)load command.
	 */
	public static LoadCommandDelegate reloadEverything(IScmClientConfiguration config, IChangeLogOutput output,
			String uri, String username, String password, boolean includeRoot, String workspace) {
		return new LoadCommandDelegate(config, output, uri, username, password, true, true, includeRoot, workspace,
				null);
	}

	@Override
	AbstractSubcommand getCommand() {
		return new LoadCmdLauncher();
	}

	@Override
	Options getOptions() throws ConflictingOptionException {
		return new LoadCmdOptions().getOptions();
	}

	private static List<String> mkCommandLineArgs(String uri, String username, String password, boolean allComponents,
			boolean force, boolean includeRoot, String workspace, String component) {
		final List<String> args = new ArrayList<String>();
		args.add("-r");
		args.add(uri);
		args.add("-u");
		args.add(username);
		args.add("-P");
		args.add(password);
		if (allComponents) {
			args.add("--all");
		}
		if (force) {
			args.add("--force");
		}
		args.add("--resync");
		if (includeRoot) {
			args.add("--include-root");
		}
		args.add(workspace);
		if (component != null) {
			args.add(component);
		}
		return args;
	}
}
