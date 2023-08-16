package to.rtc.cli.migrate.command;

import java.io.File;
import java.util.List;

import org.junit.Test;

import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;

public class AcceptCommandDelegateTest extends CommandDelegateTestBase {
	@Test
	public void commandMatches() throws Exception {
		testCommandMatchesClassName(mkInstance());
	}

	private AcceptCommandDelegate mkInstance() {
		final IScmClientConfiguration config = null;
		final IChangeLogOutput output = null;
		final String uri = "TestUri";
		final String username = "TestUsername";
		final String password = "TestPassword";
		final String targetWorkspace = "TestTargetWorkspace";
		final File sandboxDir = new File(".");
		final String changeSetUuid = "TestChangeSetUuid";
		final boolean baseline = true;
		final boolean acceptMissingChangesets = false;
		return new AcceptCommandDelegate(config, output, uri, username, password, targetWorkspace, sandboxDir, changeSetUuid,
				baseline, acceptMissingChangesets) {
			protected ICommandLine generateCommandLine(List<String> args) {
				return null;
			}

			protected void setSubCommandLine(IScmClientConfiguration config, ICommandLine commandLine) {
			}
		};
	}
}
