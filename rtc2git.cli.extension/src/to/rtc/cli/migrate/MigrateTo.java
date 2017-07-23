package to.rtc.cli.migrate;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.internal.PathLocation;
import com.ibm.team.filesystem.client.internal.snapshot.FlowType;
import com.ibm.team.filesystem.client.internal.snapshot.SnapshotId;
import com.ibm.team.filesystem.client.internal.snapshot.SnapshotSyncReport;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsGetBaselines;
import com.ibm.team.filesystem.common.changemodel.IPathResolver;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.BaselineDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.BaselineHistoryEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.GetBaselinesDTO;
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
import com.ibm.team.rtc.cli.infrastructure.internal.core.LocalContext;
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

	private StreamOutput output;
	private boolean listTagsOnly = false;

	private IProgressMonitor getMonitor() {
		return new LogTaskMonitor(new StreamOutput(config.getContext().stdout()));
	}

	public abstract Migrator getMigrator();

	public abstract Pattern getBaselineIncludePattern();

	@Override
	public void run() throws FileSystemException {
		boolean isUpdateMigration = false;
		long start = System.currentTimeMillis();
		setStdOut();
		output = new StreamOutput(config.getContext().stdout());

		try {
			// Consume the command-line
			ICommandLine subargs = config.getSubcommandCommandLine();

			int timeout = 900;
			if (subargs.hasOption(MigrateToOptions.OPT_RTC_CONNECTION_TIMEOUT)) {
				String timeoutOptionValue = subargs.getOptionValue(MigrateToOptions.OPT_RTC_CONNECTION_TIMEOUT)
						.getValue();
				timeout = Integer.parseInt(timeoutOptionValue);
			}

			if (subargs.hasOption(MigrateToOptions.OPT_RTC_LIST_TAGS_ONLY)) {
				listTagsOnly = true;
				output.writeLine("***** LIST ONLY THE TAGS *****");
			}

			if (subargs.hasOption(MigrateToOptions.OPT_RTC_IS_UPDATE_MIGRATION)) {
				isUpdateMigration = true;
				output.writeLine("***** IS UPDATE MIGRATION *****");
			}

			final ScmCommandLineArgument sourceWsOption = ScmCommandLineArgument.create(
					subargs.getOptionValue(MigrateToOptions.OPT_SRC_WS), config);
			SubcommandUtil.validateArgument(sourceWsOption, ItemType.WORKSPACE);
			final ScmCommandLineArgument destinationWsOption = ScmCommandLineArgument.create(
					subargs.getOptionValue(MigrateToOptions.OPT_DEST_WS), config);
			SubcommandUtil.validateArgument(destinationWsOption, ItemType.WORKSPACE);

			// Initialize connection to RTC
			output.writeLine("Initialize RTC connection with connection timeout of " + timeout + "s");
			IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
			ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, destinationWsOption);
			repo.setConnectionTimeout(timeout);

			IWorkspace sourceWs = RepoUtil.getWorkspace(sourceWsOption.getItemSelector(), true, false, repo, config);
			IWorkspace destinationWs = RepoUtil.getWorkspace(destinationWsOption.getItemSelector(), true, false, repo,
					config);

			output.writeLine("Get full history information from RTC. This could take a large amount of time.");
			output.writeLine("Create the list of baselines");
			RtcTagList tagList = createTagListFromBaselines(client, repo, sourceWs);

			output.writeLine("Get changeset information for all baselines");
			addChangeSetInfo(tagList, repo, sourceWs, destinationWs);

			tagList.printTagList(listTagsOnly);

			output.writeLine("Filter included baselines...");

			// Sorting is required berore pruning if migration from multiple components should be done. Otherwise tags
			// of some code could be wrong.
			tagList.sortByCreationDate();
			tagList.pruneInactiveTags();
			tagList.pruneExcludedTags(getBaselineIncludePattern());

			tagList.printTagList(true);

			if (listTagsOnly) {
				// Stop here before migration of any data
				return;
			}

			final File sandboxDirectory;
			output.writeLine("Start migration of tags.");
			if (subargs.hasOption(CommonOptions.OPT_DIRECTORY)) {
				sandboxDirectory = new File(subargs.getOption(CommonOptions.OPT_DIRECTORY));
			} else {
				sandboxDirectory = new File(System.getProperty("user.dir"));
			}
			Migrator migrator = getMigrator();
			migrator.init(sandboxDirectory);

			Map<String, String> destinationWsComponents = RepoUtil.getComponentsInSandbox(
					destinationWs.getItemId().getUuidValue(), new PathLocation(sandboxDirectory.getAbsolutePath()),
					client, config);

			RtcMigrator rtcMigrator = new RtcMigrator(output, config, destinationWsOption.getStringValue(), migrator,
					sandboxDirectory, destinationWsComponents.values(), isUpdateMigration);
			boolean isFirstTag = true;
			int numberOfTags = tagList.size();
			int tagCounter = 0;
			for (RtcTag tag : tagList) {
				if (isUpdateMigration && isFirstTag && tag.isEmpty()) {
					output.writeLine("Ignore migration of tag [" + tag.toString() + "] because it is empty.");
					tagCounter++;
					continue;
				}
				isFirstTag = false;
				final long startTag = System.currentTimeMillis();
				output.writeLine("Start migration of Tag [" + tag.getName() + "] [" + (tagCounter + 1) + "/"
						+ numberOfTags + "]");
				try {
					rtcMigrator.migrateTag(tag);
					tagCounter++;
				} catch (CLIClientException e) {
					e.printStackTrace(output.getOutputStream());
					throw new RuntimeException(e);
				}
				output.writeLine("Migration of tag [" + tag.getName() + "] [" + (tagCounter) + "/" + numberOfTags
						+ "] took [" + (System.currentTimeMillis() - startTag) / 1000 + "] s");
			}
		} catch (Throwable t) {
			t.printStackTrace(output.getOutputStream());
			throw new RuntimeException(t);
		} finally {
			output.writeLine("Migration took [" + (System.currentTimeMillis() - start) / 1000 + "] s");
		}
	}

	private void setStdOut() {
		Class<?> c = LocalContext.class;
		Field subargs;
		try {
			subargs = c.getDeclaredField("stdout");
			subargs.setAccessible(true);
			subargs.set(config.getContext(), new LoggingPrintStream(config.getContext().stdout()));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private Map<String, String> getLastChangeSetUuids(ITeamRepository repo, IWorkspace sourceWs) {
		IWorkspaceConnection sourceWsConnection;
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		IItemManager itemManager = repo.itemManager();
		Map<String, String> lastChangeSets = new HashMap<String, String>();
		try {
			IProgressMonitor monitor = getMonitor();
			sourceWsConnection = workspaceManager.getWorkspaceConnection(sourceWs, monitor);
			@SuppressWarnings("unchecked")
			List<IComponentHandle> componentHandles = sourceWsConnection.getComponents();
			@SuppressWarnings("unchecked")
			List<IComponent> components = itemManager.fetchCompleteItems(componentHandles, componentHandles.size(),
					monitor);
			for (IComponent component : components) {
				@SuppressWarnings("unchecked")
				List<ClientChangeSetEntry> changeSets = sourceWsConnection.changeHistory(component).recent(monitor);
				// select first change set if there are any
				if (!changeSets.isEmpty()) {
					IChangeSetHandle changeSetHandle = changeSets.get(changeSets.size() - 1).changeSet();
					lastChangeSets.put(component.getName(), changeSetHandle.getItemId().getUuidValue());
				}
			}
		} catch (TeamRepositoryException e) {
			e.printStackTrace(output.getOutputStream());
		}
		return lastChangeSets;
	}

	private RtcTagList createTagListFromBaselines(IFilesystemRestClient client, ITeamRepository repo,
			IWorkspace sourceWs) {
		RtcTagList tagList = new RtcTagList(output);
		try {
			IWorkspaceConnection sourceWsConnection = SCMPlatform.getWorkspaceManager(repo).getWorkspaceConnection(
					sourceWs, getMonitor());

			IWorkspaceHandle sourceStreamHandle = (IWorkspaceHandle) (sourceWsConnection.getFlowTable()
					.getCurrentAcceptFlow().getFlowNode());

			@SuppressWarnings("unchecked")
			List<IComponentHandle> componentHandles = sourceWsConnection.getComponents();

			ParmsGetBaselines parms = new ParmsGetBaselines();
			parms.workspaceItemId = sourceStreamHandle.getItemId().getUuidValue();
			parms.repositoryUrl = repo.getRepositoryURI();
			parms.max = 1000000;

			GetBaselinesDTO result = null;
			for (IComponentHandle component : componentHandles) {
				parms.componentItemId = component.getItemId().getUuidValue();
				result = client.getBaselines(parms, getMonitor());
				for (Object obj : result.getBaselineHistoryEntriesInWorkspace()) {
					BaselineHistoryEntryDTO baselineEntry = (BaselineHistoryEntryDTO) obj;
					BaselineDTO baseline = baselineEntry.getBaseline();
					long creationDate = baseline.getCreationDate();
					RtcTag tag = new RtcTag(baseline.getItemId()).setCreationDate(creationDate).setOriginalName(
							baseline.getName());
					tag = tagList.add(tag);
				}
			}
			// add default tag
			tagList.getHeadTag();
		} catch (TeamRepositoryException e) {
			e.printStackTrace(output.getOutputStream());
		}
		return tagList;
	}

	private void addChangeSetInfo(RtcTagList tagList, ITeamRepository repo, IWorkspace sourceWs,
			IWorkspace destinationWs) {

		SnapshotSyncReport syncReport;
		try {
			IWorkspaceConnection sourceWsConnection = SCMPlatform.getWorkspaceManager(repo).getWorkspaceConnection(
					sourceWs, getMonitor());

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
			customizer.setIncludeComponents(true);
			customizer.setIncludeBaselines(true);
			customizer.setIncludeChangeSets(true);
			customizer.setIncludeWorkItems(true);
			customizer.setPruneEmptyDirections(false);
			customizer.setPruneUnchangedComponents(false);

			List<IPathResolver> pathResolvers = new ArrayList<IPathResolver>();
			pathResolvers.add(CopyFileAreaPathResolver.create());
			pathResolvers.add(SnapshotPathResolver.create(destinationSnapshotId));
			pathResolvers.add(SnapshotPathResolver.create(sourceSnapshotId));
			IPathResolver pathResolver = new FallbackPathResolver(pathResolvers, true);
			clOp.setChangeLogRequest(repo, syncReport, pathResolver, customizer);
			output.writeLine("Get list of baselines and changesets form RTC.");
			long startTime = System.currentTimeMillis();
			ChangeLogEntryDTO changelog = clOp.run(getMonitor());
			output.writeLine("Get list of baselines and changesets form RTC took ["
					+ (System.currentTimeMillis() - startTime) / 1000 + "]s.");
			output.writeLine("Parse the list of baselines and changesets.");
			HistoryEntryVisitor visitor = new HistoryEntryVisitor(tagList, getLastChangeSetUuids(repo, sourceWs),
					new ChangeLogStreamOutput(config.getContext().stdout()));

			startTime = System.currentTimeMillis();
			visitor.acceptInto(changelog);
			output.writeLine("Parse the list of baselines and changesets took ["
					+ (System.currentTimeMillis() - startTime) / 1000 + "]s.");

		} catch (TeamRepositoryException e) {
			e.printStackTrace(output.getOutputStream());
		}
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

		private int getPercent() {
			if (total <= 0) {
				return -1;
			}
			return done * 100 / total;
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
