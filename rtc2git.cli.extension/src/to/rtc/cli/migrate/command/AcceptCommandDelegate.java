package to.rtc.cli.migrate.command;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IStatus;

import com.ibm.team.filesystem.cli.client.internal.subcommands.AcceptCmd;
import com.ibm.team.filesystem.cli.client.internal.subcommands.AcceptCmdOptions;
import com.ibm.team.filesystem.cli.core.AbstractSubcommand;
import com.ibm.team.filesystem.cli.core.Constants;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.rtc.cli.infrastructure.internal.core.CLIClientException;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;

import to.rtc.cli.migrate.util.ExceptionHelper;

public class AcceptCommandDelegate extends RtcCommandDelegate {

	public AcceptCommandDelegate(IScmClientConfiguration config, IChangeLogOutput output, String uri, String username,
			String password, String targetWorkspace, File sandboxDirOrNull, String changeSetUuid, boolean baseline,
			boolean acceptMissingChangesets) {
		super(config, output, mkCommandLineString("accept", mkCommandLineArgs("...", "...", "...", targetWorkspace,
				sandboxDirOrNull, changeSetUuid, baseline, acceptMissingChangesets)));
		setSubCommandLine(config, generateCommandLine(mkCommandLineArgs(uri, username, password, targetWorkspace,
				sandboxDirOrNull, changeSetUuid, baseline, acceptMissingChangesets)));
	}

	@Override
	public int run() throws CLIClientException {
		try {
			return super.run();
		} catch (CLIClientException e) {
			IStatus status = e.getStatus();
			if (status != null) {
				switch (status.getCode()) {
				case Constants.STATUS_FAILURE:
					printExceptionMessage("FAILURE", status);
					return Constants.STATUS_FAILURE;
				case Constants.STATUS_INTERNAL_ERROR:
					printExceptionMessage("INTERNAL_ERROR", status);
					return Constants.STATUS_INTERNAL_ERROR;
				case Constants.STATUS_GAP:
					printExceptionMessage("GAP", status);
					return Constants.STATUS_GAP;
				case Constants.STATUS_CONFLICT:
					printExceptionMessage("CONFLICT", status);
					return Constants.STATUS_CONFLICT;
				case Constants.STATUS_NWAY_CONFLICT:
					printExceptionMessage("NWAY_CONFLICT", status);
					return Constants.STATUS_NWAY_CONFLICT;
				case Constants.STATUS_WORKSPACE_UNCHANGED:
					printExceptionMessage("WORKSPACE_UNCHANGED", status);
					return Constants.STATUS_WORKSPACE_UNCHANGED;
				case Constants.STATUS_OUT_OF_SYNC:
					printExceptionMessage("WORKSPACE_OUT_OF_SYNC", status);
					return Constants.STATUS_OUT_OF_SYNC;
				default:
					output.writeLine("There was an unexpected exception with state [" + status.getCode() + "]("
							+ status.getMessage() + ")");
					break;
				}
			}
			throw e;
		}
	}

	void printExceptionMessage(String codeText, IStatus status) {
		output.writeLine("There was a [" + codeText + "](" + ExceptionHelper.istatusToString(status)
				+ "). We ignore that, because the following accepts should fix that");
	}

	@Override
	AbstractSubcommand getCommand() {
		return new AcceptCmd();
	}

	@Override
	Options getOptions() throws ConflictingOptionException {
		return new AcceptCmdOptions().getOptions();
	}

	private static List<String> mkCommandLineArgs(String uri, String username, String password, String rtcWorkspace,
			File sandboxDirOrNull, String changeSetUuid, boolean isBaseline, boolean acceptMissingChangesets) {
		List<String> args = new ArrayList<String>();
		args.add("-o");
		args.add("-v");
		if (sandboxDirOrNull!=null ) {
			args.add("-d");
			args.add(sandboxDirOrNull.getPath());
		}
		args.add("--no-merge");
		if (acceptMissingChangesets) {
			args.add("--accept-missing-changesets");
		}
		//args.add("--no-local-refresh");
		args.add("-r");
		args.add(uri);
		args.add("-t");
		args.add(rtcWorkspace);
		args.add("-u");
		args.add(username);
		args.add("-P");
		args.add(password);
		if (isBaseline) {
			args.add("--baseline");
		} else {
			args.add("--changes");
		}
		args.add(changeSetUuid);
		return args;
	}
}
