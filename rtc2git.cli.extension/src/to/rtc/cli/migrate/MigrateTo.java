package to.rtc.cli.migrate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.filesystem.rcp.core.internal.changes.model.CopyFileAreaPathResolver;
import com.ibm.team.filesystem.rcp.core.internal.changes.model.FallbackPathResolver;
import com.ibm.team.filesystem.rcp.core.internal.changes.model.SnapshotPathResolver;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.CLIClientException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ISubcommand;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.client.IWorkspaceManager;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.client.internal.ClientChangeSetEntry;
import com.ibm.team.scm.common.IChangeSetHandle;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IComponentHandle;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.IWorkspaceHandle;

@SuppressWarnings("restriction")
public abstract class MigrateTo extends AbstractSubcommand implements ISubcommand {

	private IProgressMonitor getMonitor() {
		return new LogTaskMonitor(new DateTimeStreamOutput(config.getContext().stdout()));
	}

	public abstract Migrator getMigrator();

	@Override
	public void run() throws FileSystemException {
		long start = System.currentTimeMillis();

		// Consume the command-line
		ICommandLine subargs = config.getSubcommandCommandLine();
		DateTimeStreamOutput output = new DateTimeStreamOutput(config.getContext().stdout());

		final ScmCommandLineArgument sourceWsOption = ScmCommandLineArgument
				.create(subargs.getOptionValue(MigrateToOptions.OPT_SRC_WS), config);
		SubcommandUtil.validateArgument(sourceWsOption, ItemType.WORKSPACE);
		final ScmCommandLineArgument destinationWsOption = ScmCommandLineArgument
				.create(subargs.getOptionValue(MigrateToOptions.OPT_DEST_WS), config);
		SubcommandUtil.validateArgument(destinationWsOption, ItemType.WORKSPACE);

		// Initialize connection to RTC
		IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
		ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, destinationWsOption);
		repo.setConnectionTimeout(3600);

		IWorkspace sourceWs = RepoUtil.getWorkspace(sourceWsOption.getItemSelector(), true, false, repo, config);
		IWorkspace destinationWs = RepoUtil.getWorkspace(destinationWsOption.getItemSelector(), true, false, repo,
				config);

		// compare destination workspace with stream of source workspace to get
		// tagging information
		List<RtcTag> tags = createTagMap(repo, sourceWs, destinationWs);
		Collections.sort(tags, new TagCreationDateComparator());
		final File sandboxDirectory;
		if (subargs.hasOption(MigrateToOptions.OPT_DIRECTORY))

		{
			sandboxDirectory = new File(subargs.getOption(MigrateToOptions.OPT_DIRECTORY));
		} else

		{
			sandboxDirectory = new File(System.getProperty("user.dir"));
		}

		Migrator migrator = getMigrator();
		migrator.init(sandboxDirectory,

				readProperties(subargs));
		RtcMigrator rtcMigrator = new RtcMigrator(output, config, destinationWsOption.getStringValue(), migrator);
		boolean isFirstTag = true;
		for (RtcTag tag : tags) {
			if (isFirstTag && tag.isEmpty()) {
				output.writeLine("Ignore first empty tag, as we cannot accept baselines");
				break;
			}
			isFirstTag = false;
			final long startTag = System.currentTimeMillis();
			output.writeLine("Start migration of Tag [" + tag.getName() + "]");
			try {
				rtcMigrator.migrateTag(tag);
			} catch (CLIClientException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			output.writeLine("Migration of tag [" + tag.getName() + "] took ["
					+ (System.currentTimeMillis() - startTag) / 1000 + "] s");
		}
		output.writeLine("Migration took [" + (System.currentTimeMillis() - start) / 1000 + "] s");
	}

	private Map<String, String> getLastChangeSetUuids(ITeamRepository repo, IWorkspace sourceWs) {
		IWorkspaceConnection sourceWsConnection;
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		IItemManager itemManager = repo.itemManager();
		Map<String, String> lastChangeSets = new HashMap<String, String>();
		try {
			sourceWsConnection = workspaceManager.getWorkspaceConnection(sourceWs, getMonitor());
			@SuppressWarnings("unchecked")
			List<IComponentHandle> componentHandles = sourceWsConnection.getComponents();
			@SuppressWarnings("unchecked")
			List<IComponent> components = itemManager.fetchCompleteItems(componentHandles, componentHandles.size(),
					getMonitor());
			for (IComponent component : components) {
				@SuppressWarnings("unchecked")
				List<ClientChangeSetEntry> changeSets = sourceWsConnection.changeHistory(component)
						.recent(getMonitor());
				IChangeSetHandle changeSetHandle = changeSets.get(changeSets.size() - 1).changeSet();
				lastChangeSets.put(component.getName(), changeSetHandle.getItemId().getUuidValue());
			}
		} catch (TeamRepositoryException e) {
			e.printStackTrace();
		}
		return lastChangeSets;
	}

	private Properties readProperties(ICommandLine subargs) {
		final Properties props = new Properties();
		try {
			FileInputStream in = new FileInputStream(subargs.getOption(MigrateToOptions.OPT_MIGRATION_PROPERTIES));
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

	private List<RtcTag> createTagMap(ITeamRepository repo, IWorkspace sourceWs, IWorkspace destinationWs) {

		SnapshotSyncReport syncReport;
		List<RtcTag> tagMap = new ArrayList<RtcTag>();
		try {
			IWorkspaceConnection sourceWsConnection = SCMPlatform.getWorkspaceManager(repo)
					.getWorkspaceConnection(sourceWs, getMonitor());

			IWorkspaceHandle sourceStreamHandle = (IWorkspaceHandle) (sourceWsConnection.getFlowTable()
					.getCurrentAcceptFlow().getFlowNode());
			SnapshotId sourceSnapshotId = SnapshotId.getSnapshotId(sourceStreamHandle);
			SnapshotId destinationSnapshotId = SnapshotId.getSnapshotId(destinationWs.getItemHandle());

			@SuppressWarnings("unchecked")
			List<IComponentHandle> componentHandles = sourceWsConnection.getComponents();
			syncReport = SnapshotSyncReport.compare(destinationSnapshotId.getSnapshot(null),
					sourceSnapshotId.getSnapshot(null), componentHandles, getMonitor());
			GenerateChangeLogOperation clOp = new GenerateChangeLogOperation();
			ChangeLogCustomizer customizer = new ChangeLogCustomizer();

			customizer.setFlowsToInclude(FlowType.Incoming);
			customizer.setIncludeBaselines(true);

			List<IPathResolver> pathResolvers = new ArrayList<IPathResolver>();
			pathResolvers.add(CopyFileAreaPathResolver.create());
			pathResolvers.add(SnapshotPathResolver.create(sourceSnapshotId));
			pathResolvers.add(SnapshotPathResolver.create(destinationSnapshotId));
			IPathResolver pathResolver = new FallbackPathResolver(pathResolvers, true);
			clOp.setChangeLogRequest(repo, syncReport, pathResolver, customizer);
			ChangeLogEntryDTO changelog = clOp.run(getMonitor());
			HistoryEntryVisitor visitor = new HistoryEntryVisitor(
					new ChangeLogStreamOutput(config.getContext().stdout()), getLastChangeSetUuids(repo, sourceWs));

			tagMap = visitor.acceptInto(changelog);

		} catch (TeamRepositoryException e) {
			e.printStackTrace();
		}
		return tagMap;
	}

	static class LogTaskMonitor extends NullProgressMonitor {
		private String taskName;
		private int total = -1;
		private int done = 0;
		private final IChangeLogOutput output;

		LogTaskMonitor(IChangeLogOutput output) {
			this.output = output;
		}

		@Override
		public void beginTask(String task, int totalWork) {
			if (task != null && !task.isEmpty()) {
				taskName = task;
				output.writeLine(taskName + " start");
			}
			total = totalWork;
		}

		@Override
		public void subTask(String subTask) {
			output.setIndent(2);
			output.writeLine(subTask + " [" + getPercent() + "%]");
		}

		private String getPercent() {
			if (total <= 0) {
				return "n/a";
			}
			return String.valueOf(done * 100 / total);
		}

		@Override
		public void worked(int workDone) {
			done += workDone;
		}

		@Override
		public void done() {
			taskName = null;
		}
	}
}
