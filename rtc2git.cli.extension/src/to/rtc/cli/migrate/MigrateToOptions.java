package to.rtc.cli.migrate;

import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;

public class MigrateToOptions implements IOptionSource {
	public static final IOptionKey OPT_SRC_WS = new OptionKey("source-workspace-name"); //$NON-NLS-1$
	public static final IOptionKey OPT_DEST_WS = new OptionKey("destination-workspace-name"); //$NON-NLS-1$

	public static final IOptionKey OPT_RTC_CONNECTION_TIMEOUT = new OptionKey("timeout");
	public static final IOptionKey OPT_RTC_LIST_TAGS_ONLY = new OptionKey("listTagsOnly");
	public static final IOptionKey OPT_RTC_IS_UPDATE_MIGRATION = new OptionKey("updateMigration");
	public static final IOptionKey OPT_RTC_DESIGNATED_SOURCE = new OptionKey("sourceStream");
	public static final IOptionKey OPT_MAX_CHANGESETS = new OptionKey("maxChangesets");
	public static final IOptionKey OPT_MAX_TAGS = new OptionKey("maxTags");
	public static final IOptionKey OPT_INCLUDE_ROOT = new OptionKey("includeRoot");
	public static final IOptionKey OPT_IDENTIFY_CHANGESETS_TO_FILL_GAP = new OptionKey("fillGap");

	@Override
	public Options getOptions() throws ConflictingOptionException {
		Options options = new Options(false);

		SubcommandUtil.addRepoLocationToOptions(options);
		options.setLongHelp("Step-by-step migrates RTC changesets into git commits, preserving history.");
		options.addOption(new PositionalOptionDefinition(OPT_SRC_WS, "source-workspace-name", 1, 1), //$NON-NLS-1$
				"name of the pre configured source RTC workspace that could follow the stream to migrate.");
		options.addOption(new PositionalOptionDefinition(OPT_DEST_WS, "destination-workspace-name", 1, 1), //$NON-NLS-1$
				"name of the pre configured RTC workspace that holds the current state of the migration and that follows the source-workspace-name.");

		options.addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP);
		options.addOption(new NamedOptionDefinition(OPT_RTC_CONNECTION_TIMEOUT, "t", "timeout", 1),
				"Timeout in seconds, default is 900");
		options.addOption(new NamedOptionDefinition(OPT_RTC_LIST_TAGS_ONLY, "L", "list-tags-only", 0),
				"List only all tags that would be migrated but do not migrate them.");
		options.addOption(new NamedOptionDefinition(OPT_RTC_IS_UPDATE_MIGRATION, "U", "update", 0),
				"Update the content of an already migrated workspace.");
		options.addOption(new NamedOptionDefinition(OPT_INCLUDE_ROOT, "i", "include-root", 0),
				"Loads component roots as directories in the file system. This may be necessary if streams contain more than one component that claim to provide the same content, causing a collision.");
		options.addOption(new NamedOptionDefinition(OPT_RTC_DESIGNATED_SOURCE, "S", "sourceStream", 1),
				"Workspace/Stream to name in git commit comments as being the source.  Default is the source-workspace-name.");
		options.addOption(new NamedOptionDefinition(OPT_MAX_CHANGESETS, "x", "maxChangesets", 1),
				"Sets an upper limit on the size of the migration that will be processed in this call by counting changesets.  Defaults to no limit.");
		options.addOption(new NamedOptionDefinition(OPT_MAX_TAGS, "X", "maxTags", 1),
				"Sets an upper limit on the size of the migration that will be processed in this call by counting tags.  Defaults to no limit.");
		options.addOption(new NamedOptionDefinition(OPT_IDENTIFY_CHANGESETS_TO_FILL_GAP, "F", "fill-gap", 0),
				"Enables functionality that calls scm list missing-changesets before accepting any planned changeset. WARNING: While this can help in some circumstances, the command can falsely identify incompatible changesets in other situations. Best to only enable this feature if you need it.");
		return options;
	}
}
