
package to.rtc.cli.migrate;

import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;

public class MigrateToOptions implements IOptionSource {
  public static final IOptionKey OPT_SRC_WS = new OptionKey("source-workspace-name"); //$NON-NLS-1$
  public static final IOptionKey OPT_DEST_WS = new OptionKey("destination-workspace-name"); //$NON-NLS-1$

  @Override
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false);

    SubcommandUtil.addRepoLocationToOptions(options);
    options
        .addOption(
            new PositionalOptionDefinition(OPT_SRC_WS, "source-workspace-name", 1, 1), "name of the pre configured source RTC workspace that could follow the stream to migrate."); //$NON-NLS-1$
    options
        .addOption(
            new PositionalOptionDefinition(OPT_DEST_WS, "destination-workspace-name", 1, 1), "name of the pre configured RTC workspace that holds the current state of the migration and that follows the source-workspace-name."); //$NON-NLS-1$
    return options;
  }
}
