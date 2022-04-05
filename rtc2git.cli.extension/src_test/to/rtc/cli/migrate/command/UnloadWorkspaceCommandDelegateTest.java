package to.rtc.cli.migrate.command;

import java.io.File;
import java.util.List;

import org.junit.Test;

import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;

public class UnloadWorkspaceCommandDelegateTest extends CommandDelegateTestBase {
	@Test
	public void commandMatches() throws Exception {
		testCommandMatchesClassName(mkInstance());
	}

	private UnloadWorkspaceCommandDelegate mkInstance() {
		final IScmClientConfiguration config = null;
		final IChangeLogOutput output = null;
		final String uri = "TestUri";
		final String username = "TestUsername";
		final String password = "TestPassword";
		final File sandboxDirOrNull = new File("TestSandboxDir");
		final boolean noLocalRefresh = false;
		final boolean overwriteUncommitted = true;
		final boolean deleteLocalFilesFromSandbox = false;
		final String workspaceOrNull = "TestWorkspace";
		return new UnloadWorkspaceCommandDelegate(config, output, uri, username, password, sandboxDirOrNull, noLocalRefresh,
				overwriteUncommitted, deleteLocalFilesFromSandbox, workspaceOrNull) {
			protected ICommandLine generateCommandLine(List<String> args) {
				return null;
			}

			protected void setSubCommandLine(IScmClientConfiguration config, ICommandLine commandLine) {
			}
		};
	}
}
