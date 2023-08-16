package to.rtc.cli.migrate.git;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;
import java.util.regex.Pattern;

import javax.xml.bind.DatatypeConverter;

import to.rtc.cli.migrate.MigrateTo;
import to.rtc.cli.migrate.Migrator;

import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;

public class MigrateToGit extends MigrateTo {
	private Migrator migratorImplementation;
	private Pattern baselineIncludeRegexPattern;

	@Override
	public void run() throws FileSystemException {
		Properties migrationProperties = readProperties(config.getSubcommandCommandLine());
		baselineIncludeRegexPattern = Pattern.compile(migrationProperties.getProperty("rtc.baseline.include", ""));
		Date initialCommitDate = getInitialCommitDate(config.getSubcommandCommandLine());
		String gitCoreAutocrlf = getGitCoreAutocrlf(config.getSubcommandCommandLine());
		migratorImplementation = new GitMigrator(this, migrationProperties, initialCommitDate, gitCoreAutocrlf);
		try {
			super.run();
		} finally {
			migratorImplementation.close();
		}
	}

	@Override
	public Migrator getMigrator() {
		return migratorImplementation;
	}

	@Override
	public Pattern getBaselineIncludePattern() {
		return baselineIncludeRegexPattern;
	}

	private Properties readProperties(ICommandLine subargs) {
		final Properties props = new Properties();
		if (subargs.hasOption(MigrateToGitOptions.OPT_MIGRATION_PROPERTIES)) {
			try {
				FileInputStream in = new FileInputStream(
						subargs.getOption(MigrateToGitOptions.OPT_MIGRATION_PROPERTIES));
				try {
					props.load(in);
				} finally {
					in.close();
				}
			} catch (IOException e) {
				throw new RuntimeException("Unable to read migration properties", e);
			}
		}
		return trimProperties(props);
	}

	private Date getInitialCommitDate(ICommandLine subargs) {
		if ( !subargs.hasOption(MigrateToGitOptions.OPT_INITIAL_COMMIT_DATE)) {
			return null;
		}
		String argValue = subargs.getOption(MigrateToGitOptions.OPT_INITIAL_COMMIT_DATE);
		Calendar timestamp;
		try {
			timestamp = DatatypeConverter.parseDateTime(argValue);
		} catch (IllegalArgumentException e) {
			String msg = "Invalid argument " + MigrateToGitOptions.OPT_INITIAL_COMMIT_DATE.getName()
					+ " argument value '" + argValue
					+ "': This must be a parsable date-time, e.g. '2001-12-23T12:23:34Z'";
			throw new IllegalArgumentException(msg, e);
		}
		return timestamp.getTime();
	}

	private String getGitCoreAutocrlf(ICommandLine subargs) {
		if (!subargs.hasOption(MigrateToGitOptions.OPT_GIT_CORE_AUTOCRLF)) {
			return null;
		}
		final String argValue = subargs.getOption(MigrateToGitOptions.OPT_GIT_CORE_AUTOCRLF);
		if ("true".equals(argValue) || "false".equals(argValue) || "input".equals(argValue)) {
			return argValue;
		}
		throw new IllegalArgumentException("Invalid argument " + MigrateToGitOptions.OPT_GIT_CORE_AUTOCRLF.getName()
				+ " argument value '" + argValue + "': This must be 'true', 'false' or 'input'.");
	}

	protected static Properties trimProperties(Properties props) {
		Iterable<Object> keyset = new ArrayList<Object>(props.keySet());
		for (Object keyObject : keyset) {
			String key = (String) keyObject;
			String value = props.getProperty(key);
			props.setProperty(key, value.trim());
			props.setProperty(key + ".untrimmed", value);
		}
		return props;
	}
}
