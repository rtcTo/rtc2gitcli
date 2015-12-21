/**
 *
 */
package to.rtc.cli.migrate.git;

import to.rtc.cli.migrate.MigrateToOptions;

import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;

/**
 * @author florian.buehlmann
 *
 */
public class MigrateToGitOptions extends MigrateToOptions {

	@Override
	public Options getOptions() throws ConflictingOptionException {
		Options options = super.getOptions();

		// rtc2git migration properties
		options.addOption(new PositionalOptionDefinition(OPT_MIGRATION_PROPERTIES, "migration.properties", 1, 1), //$NON-NLS-1$
				"path location of the migration.properties file.");
		return options;
	}

}
