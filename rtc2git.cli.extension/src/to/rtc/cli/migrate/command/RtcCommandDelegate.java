
package to.rtc.cli.migrate.command;

import java.util.List;

import com.ibm.team.filesystem.cli.client.internal.subcommands.AcceptCmdOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.CLIParser;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;

public abstract class RtcCommandDelegate {

  protected static ICommandLine generateCommandLine(List<String> args) {
    AcceptCmdOptions acceptCmdOptions = new AcceptCmdOptions();
    Options options;
    try {
      options = acceptCmdOptions.getOptions();
      CLIParser parser = new CLIParser(options, args);
      return parser.parse();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected static String getSubCommandOption(IScmClientConfiguration config, IOptionKey key) {
    return config.getSubcommandCommandLine().getOption(key);
  }

}
