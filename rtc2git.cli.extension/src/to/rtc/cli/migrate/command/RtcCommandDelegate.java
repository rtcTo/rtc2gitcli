
package to.rtc.cli.migrate.command;

import java.lang.reflect.Field;
import java.util.List;

import com.ibm.team.filesystem.cli.client.internal.subcommands.AcceptCmdOptions;
import com.ibm.team.filesystem.cli.core.AbstractSubcommand;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.rtc.cli.infrastructure.internal.core.CLIClientException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ClientConfiguration;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.CLIParser;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;

public abstract class RtcCommandDelegate {

  protected final IScmClientConfiguration config;

  protected RtcCommandDelegate(IScmClientConfiguration config) {
    this.config = config;
  }

  public final int run() throws CLIClientException {
    long start = System.currentTimeMillis();
    AbstractSubcommand command = getCommand();
    try {
      return command.run(config);
    } finally {
      System.out.println("DelegateCommand [" + command + "] finished in [" + (System.currentTimeMillis() - start) + " ms]");
    }
  }

  abstract AbstractSubcommand getCommand();

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

  protected void setSubCommandLine(IScmClientConfiguration config, ICommandLine commandLine) {
    Class<?> c = ClientConfiguration.class;
    Field subargs;
    try {
      subargs = c.getDeclaredField("subargs");
      subargs.setAccessible(true);
      subargs.set(config, commandLine);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

}
