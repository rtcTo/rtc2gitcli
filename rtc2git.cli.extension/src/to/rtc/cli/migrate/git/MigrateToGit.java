package to.rtc.cli.migrate.git;

import to.rtc.cli.migrate.MigrateTo;
import to.rtc.cli.migrate.Migrator;

public class MigrateToGit extends MigrateTo {

	@Override
	public Migrator getMigrator() {
		return new GitMigrator();
	}

}
