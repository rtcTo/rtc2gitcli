package to.rtc.cli.migrate.command;

import java.lang.reflect.Field;
import java.util.List;

import com.ibm.team.filesystem.cli.core.AbstractSubcommand;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.rtc.cli.infrastructure.internal.core.CLIClientException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ClientConfiguration;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.CLIParser;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;

public abstract class RtcCommandDelegate {

	protected final IScmClientConfiguration config;

	private final String commandLine;

	protected final IChangeLogOutput output;

	protected RtcCommandDelegate(IScmClientConfiguration config, IChangeLogOutput output, String commandLine) {
		this.config = config;
		this.output = output;
		this.commandLine = commandLine;
	}

	public int run() throws CLIClientException {
		long start = System.currentTimeMillis();
		AbstractSubcommand command = getCommand();
		try {
			return command.run(config);
		} finally {
			output.writeLine(
					"DelegateCommand [" + this + "] finished in [" + (System.currentTimeMillis() - start) + "]ms");
		}
	}

	abstract AbstractSubcommand getCommand();

	abstract Options getOptions() throws ConflictingOptionException;

	protected ICommandLine generateCommandLine(List<String> args) {
		try {
			CLIParser parser = new CLIParser(getOptions(), args);
			return parser.parse();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected static String getSubCommandOption(IScmClientConfiguration config, IOptionKey key) {
		return config.getSubcommandCommandLine().getOption(key);
	}

	protected void setSubCommandLine(IScmClientConfiguration config, ICommandLine commandLine) {
		Class<?> c = ClientConfiguration.class;
		Field subargs;
		try {
			subargs = c.getDeclaredField("subargs");
			subargs.setAccessible(true);
			subargs.set(config, commandLine);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String toString() {
		if (commandLine == null) {
			return super.toString();
		}
		return commandLine;
	}

}
