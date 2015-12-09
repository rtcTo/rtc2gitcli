
package to.rtc.cli.migrate.command;

import java.util.ArrayList;
import java.util.List;

import com.ibm.team.filesystem.cli.client.internal.subcommands.LoadCmdLauncher;
import com.ibm.team.filesystem.cli.client.internal.subcommands.LoadCmdOptions;
import com.ibm.team.filesystem.cli.core.AbstractSubcommand;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;

@SuppressWarnings("restriction")
public class LoadCommandDelegate extends RtcCommandDelegate {

  public LoadCommandDelegate(IScmClientConfiguration config, String workspace, boolean force) {
    super(config);
    setSubCommandLineByReflection(config, workspace, force);
  }

  @Override
  AbstractSubcommand getCommand() {
    return new LoadCmdLauncher();
  }

  @Override
  Options getOptions() throws ConflictingOptionException {
    return new LoadCmdOptions().getOptions();
  }

  private void setSubCommandLineByReflection(IScmClientConfiguration config, String workspace, boolean force) {
    String uri = getSubCommandOption(config, CommonOptions.OPT_URI);
    String username = getSubCommandOption(config, CommonOptions.OPT_USERNAME);
    String password = getSubCommandOption(config, CommonOptions.OPT_PASSWORD);
    setSubCommandLine(config, generateCommandLine(uri, username, password, workspace, force));
  }

  private ICommandLine generateCommandLine(String uri, String username, String password, String workspace, boolean force) {
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
    return generateCommandLine(args);
  }
}
