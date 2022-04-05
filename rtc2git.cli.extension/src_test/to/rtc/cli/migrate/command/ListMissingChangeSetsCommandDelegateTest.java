package to.rtc.cli.migrate.command;

import java.util.List;

import org.junit.Test;

import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;

public class ListMissingChangeSetsCommandDelegateTest extends CommandDelegateTestBase {
	@Test
	public void commandMatches() throws Exception {
		testCommandMatchesClassName(mkInstance());
	}

	private ListMissingChangeSetsCommandDelegate mkInstance() {
		final IScmClientConfiguration config = null;
		final IChangeLogOutput output = null;
		final String uri = "TestUri";
		final String username = "TestUsername";
		final String password = "TestPassword";
		final String targetWorkspace = "TestTargetWorkspace";
		final String changeSetUuid = "TestChangeSetUuid";
		final boolean summarise = true;
		final boolean json = false;
		return new ListMissingChangeSetsCommandDelegate(config, output, uri, username, password, targetWorkspace, changeSetUuid,
				summarise, json) {
			protected ICommandLine generateCommandLine(List<String> args) {
				return null;
			}

			protected void setSubCommandLine(IScmClientConfiguration config, ICommandLine commandLine) {
			}
		};
	}
}
