/**
 *
 */

package to.rtc.cli.migrate.command;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.ibm.team.filesystem.cli.client.internal.subcommands.AcceptCmd;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.rtc.cli.infrastructure.internal.core.CLIClientException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ClientConfiguration;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;

@SuppressWarnings("restriction")
public class AcceptCommandDelegate extends RtcCommandDelegate {

  public void runAcceptChangeSet(IScmClientConfiguration config, String targetWorkspace, String changeSetUuid) throws CLIClientException {
    runAcceptChangeSet(config, targetWorkspace, changeSetUuid, false);
  }

  public void runAcceptBaseline(IScmClientConfiguration config, String targetWorkspace, String changeSetUuid) throws CLIClientException {
    runAcceptChangeSet(config, targetWorkspace, changeSetUuid, true);
  }

  void runAcceptChangeSet(IScmClientConfiguration config, String targetWorkspace, String changeSetUuid, boolean baseline)
      throws CLIClientException {
    setSubCommandLine(config, targetWorkspace, changeSetUuid, baseline);
    System.out.println("Accepting changeset [" + changeSetUuid + "] to workspace [" + targetWorkspace + "]");
    long start = System.currentTimeMillis();
    new AcceptCmd().run(config);
    System.out.println("Accpeted. Accept took [" + (System.currentTimeMillis() - start) + " ms]");
  }

  void setSubCommandLine(IScmClientConfiguration config, String targetWorkspace, String changeSetUuid, boolean isBaseline) {
    Class<?> c = ClientConfiguration.class;
    try {
      Field subargs = c.getDeclaredField("subargs");
      subargs.setAccessible(true);
      String uri = getSubCommandOption(config, CommonOptions.OPT_URI);
      String username = getSubCommandOption(config, CommonOptions.OPT_USERNAME);
      String password = getSubCommandOption(config, CommonOptions.OPT_PASSWORD);
      subargs.set(config, generateCommandLine(uri, username, password, targetWorkspace, changeSetUuid, isBaseline));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private ICommandLine generateCommandLine(String uri, String username, String password, String rtcWorkspace, String changeSetUuid,
      boolean isBaseline) {
    List<String> args = new ArrayList<String>();
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
    return generateCommandLine(args);
  }
}
