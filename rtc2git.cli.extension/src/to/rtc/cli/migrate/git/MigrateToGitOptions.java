/**
 *
 */
package to.rtc.cli.migrate.git;

import to.rtc.cli.migrate.MigrateToOptions;

import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;

/**
 * @author florian.buehlmann
 *
 */
public class MigrateToGitOptions extends MigrateToOptions {
	public static final IOptionKey OPT_MIGRATION_PROPERTIES = new OptionKey("migrationProperties"); //$NON-NLS-1$
	public static final IOptionKey OPT_INITIAL_COMMIT_DATE = new OptionKey("initialCommitDatestamp"); //$NON-NLS-1$
	public static final IOptionKey OPT_GIT_CORE_AUTOCRLF = new OptionKey("autocrlf"); //$NON-NLS-1$

	@Override
	public Options getOptions() throws ConflictingOptionException {
		Options options = super.getOptions();

		// rtc2git migration properties
		options.addOption(new NamedOptionDefinition(OPT_MIGRATION_PROPERTIES, "m", "migrationProperties", 1),
				"File with migration properties.");
		options.addOption(new NamedOptionDefinition(OPT_INITIAL_COMMIT_DATE, "D", "initialCommitDatestamp", 1),
				"The timestamp to set for the initial commit, in xsd:datetime format (YYYY-MM-DDThh:mm:ss).  Defaults to 'now' (i.e. likely to be after the subsequent migrated changesets, which may cause confusion).");
		options.addOption(new NamedOptionDefinition(OPT_GIT_CORE_AUTOCRLF, "a", "autocrlf", 1),
				"The setting to use for git's core.autocrlf setting. If not set then defaults to 'input' on unix-like OSs and 'true' for Windows. Set this to 'false' in order to ensure that git persists the exact contents provided by RTC.");
		return options;
	}
}
