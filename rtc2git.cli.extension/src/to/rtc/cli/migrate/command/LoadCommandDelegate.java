
package to.rtc.cli.migrate.command;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.ibm.team.filesystem.cli.client.internal.subcommands.LoadCmdLauncher;
import com.ibm.team.filesystem.cli.client.internal.subcommands.LoadCmdOptions;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.rtc.cli.infrastructure.internal.core.CLIClientException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ClientConfiguration;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.CLIParser;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;

@SuppressWarnings("restriction")
public class LoadCommandDelegate {

  public static void runLoad(IScmClientConfiguration config, String workspace, boolean force) throws CLIClientException {
    setSubCommandLineByReflection(config, workspace, force);
    System.out.println("Loading [" + workspace + "]");
    long start = System.currentTimeMillis();
    new LoadCmdLauncher().run(config);
    System.out.println("Loaded. Loaded took [" + (System.currentTimeMillis() - start) + " ms]");
  }

  private static void setSubCommandLineByReflection(IScmClientConfiguration config, String workspace, boolean force) {
    Class<?> c = ClientConfiguration.class;
    try {
      Field subargs = c.getDeclaredField("subargs");
      subargs.setAccessible(true);
      ICommandLine subCommandLine = config.getSubcommandCommandLine();
      String uri = subCommandLine.getOption(CommonOptions.OPT_URI);
      String username = subCommandLine.getOption(CommonOptions.OPT_USERNAME);
      String password = subCommandLine.getOption(CommonOptions.OPT_PASSWORD);
      subargs.set(config, generateCommandLine(uri, username, password, workspace, force));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static Object generateCommandLine(String uri, String username, String password, String workspace, boolean force) {
    List<String> args = new ArrayList<String>();
    args.add("-r");
    args.add(uri);
    args.add("-u");
    args.add(username);
    args.add("-P");
    args.add(password);
    args.add(workspace);

    if (force) {
      args.add("--force");
    }
    try {
      Options options = new LoadCmdOptions().getOptions();
      CLIParser parser = new CLIParser(options, args);
      return parser.parse();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

}
