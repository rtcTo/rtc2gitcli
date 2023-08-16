package to.rtc.cli.migrate.command;

import java.io.File;
import java.util.List;

import org.junit.Test;

import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;

public class StatusCommandDelegateTest extends CommandDelegateTestBase {
	@Test
	public void commandMatches() throws Exception {
		testCommandMatchesClassName(mkInstance());
	}

	private StatusCommandDelegate mkInstance() {
		final IScmClientConfiguration config = null;
		final IChangeLogOutput output = null;
		final String username = "TestUsername";
		final String password = "TestPassword";
		final File sandboxDirOrNull = new File(".");
		final boolean json = false;
		final boolean verbose = false;
		final boolean all = false;
		final boolean excludeIncoming = true;
		final boolean xchangeset = false;
		final boolean locateConflicts = false;
		return new StatusCommandDelegate(config, output, username, password, sandboxDirOrNull, json, verbose, all, excludeIncoming, xchangeset, locateConflicts) {
			protected ICommandLine generateCommandLine(List<String> args) {
				return null;
			}

			protected void setSubCommandLine(IScmClientConfiguration config, ICommandLine commandLine) {
			}
		};
	}
}
