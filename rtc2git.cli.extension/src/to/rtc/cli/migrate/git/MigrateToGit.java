package to.rtc.cli.migrate.git;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Set;

import to.rtc.cli.migrate.MigrateTo;
import to.rtc.cli.migrate.Migrator;

import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;

public class MigrateToGit extends MigrateTo {
	private Migrator migratorImplementation;

	@Override
	public void run() throws FileSystemException {
		migratorImplementation = new GitMigrator(readProperties(config.getSubcommandCommandLine()));
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

	protected Properties trimProperties(Properties props) {
		Set<Object> keyset = props.keySet();
		for (Object keyObject : keyset) {
			String key = (String) keyObject;
			props.setProperty(key, props.getProperty(key).trim());
		}
		return props;
	}
}
