/**
 *
 */

package to.rtc.cli.migrate;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.internal.snapshot.FlowType;
import com.ibm.team.filesystem.client.internal.snapshot.SnapshotId;
import com.ibm.team.filesystem.client.internal.snapshot.SnapshotSyncReport;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.common.changemodel.IPathResolver;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogEntryDTO;
import com.ibm.team.filesystem.rcp.core.internal.changelog.ChangeLogCustomizer;
import com.ibm.team.filesystem.rcp.core.internal.changelog.ChangeLogStreamOutput;
import com.ibm.team.filesystem.rcp.core.internal.changelog.GenerateChangeLogOperation;
import com.ibm.team.filesystem.rcp.core.internal.changes.model.CopyFileAreaPathResolver;
import com.ibm.team.filesystem.rcp.core.internal.changes.model.FallbackPathResolver;
import com.ibm.team.filesystem.rcp.core.internal.changes.model.SnapshotPathResolver;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IItemHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ISubcommand;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.scm.common.IWorkspace;

/**
 * @author florian.buehlmann
 *
 */
@SuppressWarnings("restriction")
public abstract class MigrateTo extends AbstractSubcommand implements ISubcommand {

  /**
   * @return
   */
  private IProgressMonitor getMonitor() {
    return new NullProgressMonitor();
  }

  public abstract Migrator getMigrator();

  @Override
  public void run() throws FileSystemException {
    long start = System.currentTimeMillis();

    // Consume the command-line
    ICommandLine subargs = config.getSubcommandCommandLine();

    final ScmCommandLineArgument sourceWsOption = ScmCommandLineArgument
        .create(subargs.getOptionValue(MigrateToOptions.OPT_SRC_WS), config);
    SubcommandUtil.validateArgument(sourceWsOption, ItemType.WORKSPACE);
    final ScmCommandLineArgument destinationWsOption = ScmCommandLineArgument.create(subargs.getOptionValue(MigrateToOptions.OPT_DEST_WS),
        config);
    SubcommandUtil.validateArgument(destinationWsOption, ItemType.WORKSPACE);

    // Initialize connection to RTC
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, destinationWsOption);
    repo.setConnectionTimeout(3600);

    // compare workspaces
    IWorkspace sourceWs = RepoUtil.getWorkspace(sourceWsOption.getItemSelector(), true, false, repo, config);
    IItemHandle sourceWsHandle = sourceWs.getItemHandle();
    IWorkspace destinationWs = RepoUtil.getWorkspace(destinationWsOption.getItemSelector(), true, false, repo, config);
    IItemHandle destinationWsHandle = destinationWs.getItemHandle();

    SnapshotId sourceSnapshotId = SnapshotId.getSnapshotId(sourceWsHandle);
    SnapshotId destinationSnapshotId = SnapshotId.getSnapshotId(destinationWsHandle);
    try {
      SnapshotSyncReport syncReport = SnapshotSyncReport.compare(destinationSnapshotId.getSnapshot(null),
          sourceSnapshotId.getSnapshot(null), null, null);
      GenerateChangeLogOperation clOp = new GenerateChangeLogOperation();
      ChangeLogCustomizer customizer = new ChangeLogCustomizer();

      customizer.setFlowsToInclude(FlowType.Incoming);

      List<IPathResolver> pathResolvers = new ArrayList<IPathResolver>();
      pathResolvers.add(CopyFileAreaPathResolver.create());
      pathResolvers.add(SnapshotPathResolver.create(sourceSnapshotId));
      pathResolvers.add(SnapshotPathResolver.create(destinationSnapshotId));
      IPathResolver pathResolver = new FallbackPathResolver(pathResolvers, true);
      clOp.setChangeLogRequest(repo, syncReport, pathResolver, customizer);
      ChangeLogEntryDTO changelog;
      changelog = clOp.run(getMonitor());

      Migrator migrator = getMigrator();
      try {
        ChangeLogEntryVisitor visitor = new ChangeLogEntryVisitor(new ChangeLogStreamOutput(config.getContext().stdout()), config,
            destinationWs.getName(), migrator);
        visitor.init();
        visitor.acceptInto(changelog);
      } finally {
        migrator.close();
      }
    } catch (TeamRepositoryException e) {
      e.printStackTrace();
    }
    System.out.println("Migration took [" + (System.currentTimeMillis() - start) + " ms]");
  }
}
