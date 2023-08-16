package to.rtc.cli.migrate.command;

import java.util.List;

import org.junit.Test;

import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;

public class LoadCommandDelegateTest extends CommandDelegateTestBase {
	@Test
	public void commandMatches() throws Exception {
		// The load command's RTC-internal class is named ...CmdLauncher whereas normally they're all named ...Cmd
		testCommandMatchesClassName(mkInstance(), "CmdLauncher");
	}

	private LoadCommandDelegate mkInstance() {
		final IScmClientConfiguration config = null;
		final IChangeLogOutput output = null;
		final String uri = "TestUri";
		final String username = "TestUsername";
		final String password = "TestPassword";
		final boolean allComponents = true;
		final boolean force = false;
		final boolean includeRoot = true;
		final String workspace = "TestWorkspace";
		final String component = "TestComponent";
		return new LoadCommandDelegate(config, output, uri, username, password, allComponents, force, includeRoot, workspace, component) {
			protected ICommandLine generateCommandLine(List<String> args) {
				return null;
			}

			protected void setSubCommandLine(IScmClientConfiguration config, ICommandLine commandLine) {
			}
		};
	}
}
