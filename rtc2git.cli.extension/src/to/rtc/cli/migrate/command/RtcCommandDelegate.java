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
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;

import to.rtc.cli.migrate.util.ExceptionHelper;

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
		final long start = System.currentTimeMillis();
		final AbstractSubcommand command = getCommand();
		String result = "";
		output.writeLine("DelegateCommand [" + this + "] running...");
		try {
			final int returnCode = command.run(config);
			result = ", returned " + returnCode;
			return returnCode;
		} catch( RuntimeException ex) {
			result = ", threw " + ExceptionHelper.exceptionToString(ex);
			throw ex;
		} catch( CLIClientException ex ) {
			result = ", threw " + ExceptionHelper.exceptionToString(ex);
			throw ex;
		} catch( Error ex) {
			result = ", threw " + ExceptionHelper.exceptionToString(ex);
			throw ex;
		} finally {
			final long finish = System.currentTimeMillis();
			final long duration = finish - start;
			output.writeLine(
					"DelegateCommand [" + this + "] finished in [" + duration + "]ms" + result);
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

	protected void setSubCommandLine(IScmClientConfiguration config, ICommandLine commandLine) {
		String fieldName = "subargs";
		Class<?> c = ClientConfiguration.class;
		Field subargs;
		try {
			subargs = c.getDeclaredField(fieldName);
			subargs.setAccessible(true);
			subargs.set(config, commandLine);
		} catch (Exception e) {
			throw new RuntimeException("Failed to set field '" + fieldName + "' to '" + commandLine + "' in instance " + config, e);
		}
	}

	@Override
	public String toString() {
		if (commandLine == null) {
			return super.toString();
		}
		return commandLine;
	}

	protected static String mkCommandLineString(String prefix, Iterable<String> args) {
		final StringBuilder s = new StringBuilder(prefix);
		for (final String arg : args) {
			if (s.length() > 0) {
				s.append(' ');
			}
			if (arg.contains(" ") || arg.isEmpty()) {
				s.append('\'').append(arg).append('\'');
			} else {
				s.append(arg);
			}
		}
		return s.toString();
	}
}
