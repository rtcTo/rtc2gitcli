/**
 *
 */

package to.rtc.cli.migrate.command;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.ibm.team.filesystem.cli.client.internal.subcommands.AcceptCmd;
import com.ibm.team.filesystem.cli.client.internal.subcommands.AcceptCmdOptions;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.rtc.cli.infrastructure.internal.core.CLIClientException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ClientConfiguration;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.CLIParser;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;

@SuppressWarnings("restriction")
public class AcceptCommandDelegate {

  public static void runAcceptChangeSet(IScmClientConfiguration config, String targetWorkspace, String changeSetUuid)
      throws CLIClientException {
    runAcceptChangeSet(config, targetWorkspace, changeSetUuid, false);
  }

  public static void runAcceptBaseline(IScmClientConfiguration config, String targetWorkspace, String changeSetUuid)
      throws CLIClientException {
    runAcceptChangeSet(config, targetWorkspace, changeSetUuid, true);
  }

  private static void runAcceptChangeSet(IScmClientConfiguration config, String targetWorkspace, String changeSetUuid, boolean baseline)
      throws CLIClientException {
    setSubCommandLineByReflection(config, targetWorkspace, changeSetUuid, baseline);
    System.out.println("Accepting changeset [" + changeSetUuid + "] to workspace [" + targetWorkspace + "]");
    long start = System.currentTimeMillis();
    new AcceptCmd().run(config);
    System.out.println("Accpeted. Accept took [" + (System.currentTimeMillis() - start) + " ms]");
  }

  private static void setSubCommandLineByReflection(IScmClientConfiguration config, String targetWorkspace, String changeSetUuid,
      boolean isBaseline) {
    Class<?> c = ClientConfiguration.class;
    try {
      Field subargs = c.getDeclaredField("subargs");
      subargs.setAccessible(true);
      ICommandLine subCommandLine = config.getSubcommandCommandLine();
      String uri = subCommandLine.getOption(CommonOptions.OPT_URI);
      String username = subCommandLine.getOption(CommonOptions.OPT_USERNAME);
      String password = subCommandLine.getOption(CommonOptions.OPT_PASSWORD);
      // System.out.println("-->" + uri);
      // System.out.println("-->" + username);
      // System.out.println("-->" + password);
      // System.out.println("-->" + targetWorkspace + " => " + targetDirectory);
      // System.out.println("-->" + changeSetUuid);
      // here I was stuck
      // String rtcWorkspace = subCommandLine.getOption(new NamedOptionDefinition("t", "target",
      // 1));
      subargs.set(config, generateCommandLine(uri, username, password, targetWorkspace, changeSetUuid, isBaseline));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static ICommandLine generateCommandLine(String uri, String username, String password, String rtcWorkspace, String changeSetUuid,
      boolean isBaseline) {
    List<String> args = new ArrayList<String>();
    // args.add("-v");
    args.add("-o");
    args.add("--no-merge");
    args.add("-r");
    args.add(uri);
    args.add("-t");
    args.add(rtcWorkspace);
    args.add("-u");
    args.add(username);
    args.add("-P");
    args.add(password);

    if (isBaseline) {
      args.add("--baseline");
    } else {
      args.add("--changes");
    }
    args.add(changeSetUuid);
    AcceptCmdOptions acceptCmdOptions = new AcceptCmdOptions();
    Options options;
    try {
      options = acceptCmdOptions.getOptions();
      CLIParser parser = new CLIParser(options, args);
      ICommandLine commandline = parser.parse();
      return commandline;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
