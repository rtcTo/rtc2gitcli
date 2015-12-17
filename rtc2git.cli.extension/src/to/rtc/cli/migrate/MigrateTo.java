package to.rtc.cli.migrate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

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
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ISubcommand;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.IWorkspaceHandle;

/**
 * @author florian.buehlmann
 */
@SuppressWarnings("restriction")
public abstract class MigrateTo extends AbstractSubcommand implements
		ISubcommand {

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
				.create(subargs.getOptionValue(MigrateToOptions.OPT_SRC_WS),
						config);
		SubcommandUtil.validateArgument(sourceWsOption, ItemType.WORKSPACE);
		final ScmCommandLineArgument destinationWsOption = ScmCommandLineArgument
				.create(subargs.getOptionValue(MigrateToOptions.OPT_DEST_WS),
						config);
		SubcommandUtil
				.validateArgument(destinationWsOption, ItemType.WORKSPACE);

		// Initialize connection to RTC
		IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
		ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client,
				destinationWsOption);
		repo.setConnectionTimeout(3600);

		IWorkspace sourceWs = RepoUtil.getWorkspace(
				sourceWsOption.getItemSelector(), true, false, repo, config);
		IWorkspace destinationWs = RepoUtil.getWorkspace(
				destinationWsOption.getItemSelector(), true, false, repo,
				config);

		// compare destination workspace with stream of source workspace to get
		// tagging information
		Map<String, RtcTag> tagMap = createTagMap(repo, sourceWs, destinationWs);

		// compare workspaces
		ChangeLogEntryDTO changelog = compareWorkspaces(repo, sourceWs,
				destinationWs);

		File sandboxDirectory;
		if (subargs.hasOption(MigrateToOptions.OPT_DIRECTORY)) {
			sandboxDirectory = new File(
					subargs.getOption(MigrateToOptions.OPT_DIRECTORY));
		} else {
			sandboxDirectory = new File(System.getProperty("user.dir"));
		}
		Migrator migrator = getMigrator();
		migrator.init(sandboxDirectory, readProperties(subargs));
		ChangeLogEntryVisitor visitor = new ChangeLogEntryVisitor(
				new ChangeLogStreamOutput(config.getContext().stdout()),
				config, destinationWs.getName(), migrator, tagMap);
		visitor.init();
		visitor.acceptInto(changelog);
		config.getContext()
				.stdout()
				.println(
						"Migration took ["
								+ (System.currentTimeMillis() - start) + " ms]");
	}

	private Properties readProperties(ICommandLine subargs) {
		final Properties props = new Properties();
		try {
			FileInputStream in = new FileInputStream(
					subargs.getOption(MigrateToOptions.OPT_MIGRATION_PROPERTIES));
			try {
				props.load(in);
			} finally {
				in.close();
			}
		} catch (IOException e) {
			throw new RuntimeException("Unable to read migration properties", e);
		}
		return trimProperties(props);
	}

	Properties trimProperties(Properties props) {
		Set<Object> keyset = props.keySet();
		for (Object keyObject : keyset) {
			String key = (String) keyObject;
			props.setProperty(key, props.getProperty(key).trim());
		}
		return props;
	}

	private Map<String, RtcTag> createTagMap(ITeamRepository repo,
			IWorkspace sourceWs, IWorkspace destinationWs) {
		SnapshotSyncReport syncReport;
		Map<String, RtcTag> tagMap = new HashMap<String, RtcTag>();
		try {
			IWorkspaceConnection sourceWsConnection = SCMPlatform
					.getWorkspaceManager(repo).getWorkspaceConnection(sourceWs,
							getMonitor());

			IWorkspaceHandle sourceStreamHandle = (IWorkspaceHandle) (sourceWsConnection
					.getFlowTable().getCurrentAcceptFlow().getFlowNode());
			SnapshotId sourceSnapshotId = SnapshotId
					.getSnapshotId(sourceStreamHandle);
			SnapshotId destinationSnapshotId = SnapshotId
					.getSnapshotId(destinationWs.getItemHandle());

			syncReport = SnapshotSyncReport.compare(
					destinationSnapshotId.getSnapshot(null),
					sourceSnapshotId.getSnapshot(null), null, null);
			GenerateChangeLogOperation clOp = new GenerateChangeLogOperation();
			ChangeLogCustomizer customizer = new ChangeLogCustomizer();

			customizer.setFlowsToInclude(FlowType.Incoming);
			customizer.setIncludeBaselines(true);

			List<IPathResolver> pathResolvers = new ArrayList<IPathResolver>();
			pathResolvers.add(CopyFileAreaPathResolver.create());
			pathResolvers.add(SnapshotPathResolver.create(sourceSnapshotId));
			pathResolvers.add(SnapshotPathResolver
					.create(destinationSnapshotId));
			IPathResolver pathResolver = new FallbackPathResolver(
					pathResolvers, true);
			clOp.setChangeLogRequest(repo, syncReport, pathResolver, customizer);
			ChangeLogEntryDTO changelog = clOp.run(getMonitor());
			TagLogEntryVisitor visitor = new TagLogEntryVisitor(
					new ChangeLogStreamOutput(config.getContext().stdout()));

			tagMap = visitor.acceptInto(changelog);

		} catch (TeamRepositoryException e) {
			e.printStackTrace();
		}
		return tagMap;
	}

	private ChangeLogEntryDTO compareWorkspaces(ITeamRepository repo,
			IWorkspace sourceWs, IWorkspace destinationWs) {
		SnapshotSyncReport syncReport;
		try {
			SnapshotId sourceSnapshotId = SnapshotId.getSnapshotId(sourceWs
					.getItemHandle());
			SnapshotId destinationSnapshotId = SnapshotId
					.getSnapshotId(destinationWs.getItemHandle());
			syncReport = SnapshotSyncReport.compare(
					destinationSnapshotId.getSnapshot(null),
					sourceSnapshotId.getSnapshot(null), null, null);
			GenerateChangeLogOperation clOp = new GenerateChangeLogOperation();
			ChangeLogCustomizer customizer = new ChangeLogCustomizer();

			customizer.setFlowsToInclude(FlowType.Incoming);
			customizer.setIncludeBaselines(true);

			List<IPathResolver> pathResolvers = new ArrayList<IPathResolver>();
			pathResolvers.add(CopyFileAreaPathResolver.create());
			pathResolvers.add(SnapshotPathResolver.create(sourceSnapshotId));
			pathResolvers.add(SnapshotPathResolver
					.create(destinationSnapshotId));
			IPathResolver pathResolver = new FallbackPathResolver(
					pathResolvers, true);
			clOp.setChangeLogRequest(repo, syncReport, pathResolver, customizer);
			return clOp.run(getMonitor());
		} catch (TeamRepositoryException e) {
			e.printStackTrace();
		}
		return null;
	}
}
