package to.rtc.cli.migrate.command;

import java.io.File;
import java.util.List;

import org.junit.Test;

import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;

public class RefreshCommandDelegateTest extends CommandDelegateTestBase {
	@Test
	public void commandMatches() throws Exception {
		testCommandMatchesClassName(mkInstance());
	}

	private RefreshCommandDelegate mkInstance() {
		final IScmClientConfiguration config = null;
		final IChangeLogOutput output = null;
		final File sandbox = new File("TestSandboxFile");
		return new RefreshCommandDelegate(config, output, sandbox) {
			protected ICommandLine generateCommandLine(List<String> args) {
				return null;
			}

			protected void setSubCommandLine(IScmClientConfiguration config, ICommandLine commandLine) {
			}
		};
	}
}
