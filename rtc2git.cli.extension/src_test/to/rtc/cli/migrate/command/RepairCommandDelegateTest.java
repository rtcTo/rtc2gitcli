package to.rtc.cli.migrate.command;

import java.io.File;
import java.util.List;

import org.junit.Test;

import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;

public class RepairCommandDelegateTest extends CommandDelegateTestBase {
	@Test
	public void commandMatches() throws Exception {
		testCommandMatchesClassName(mkInstance());
	}

	private RepairCommandDelegate mkInstance() {
		final IScmClientConfiguration config = null;
		final IChangeLogOutput output = null;
		final String uri = "TestUri";
		final String username = "TestUsername";
		final String password = "TestPassword";
		final File sandboxDirOrNull = new File("TestSandboxDir");
		return new RepairCommandDelegate(config, output, uri, username, password, sandboxDirOrNull) {
			protected ICommandLine generateCommandLine(List<String> args) {
				return null;
			}

			protected void setSubCommandLine(IScmClientConfiguration config, ICommandLine commandLine) {
			}
		};
	}
}
