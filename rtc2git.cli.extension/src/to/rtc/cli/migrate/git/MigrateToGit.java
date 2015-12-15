package to.rtc.cli.migrate.git;

import to.rtc.cli.migrate.MigrateTo;
import to.rtc.cli.migrate.Migrator;

import com.ibm.team.filesystem.client.FileSystemException;

public class MigrateToGit extends MigrateTo {
	private GitMigrator migratorImplementation;

	@Override
	public void run() throws FileSystemException {
		migratorImplementation = new GitMigrator();
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
}
