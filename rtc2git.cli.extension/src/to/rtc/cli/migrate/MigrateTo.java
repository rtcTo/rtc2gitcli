package to.rtc.cli.migrate;

import java.io.File;
import java.io.PrintStream;
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
import com.ibm.team.filesystem.rcp.core.internal.changelog.GenerateChangeLogOperation;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.filesystem.rcp.core.internal.changes.model.CopyFileAreaPathResolver;
import com.ibm.team.filesystem.rcp.core.internal.changes.model.FallbackPathResolver;
import com.ibm.team.filesystem.rcp.core.internal.changes.model.SnapshotPathResolver;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
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

import to.rtc.cli.migrate.util.ExceptionHelper;
import to.rtc.cli.migrate.util.NoOpChangeLogOutput;
import to.rtc.cli.migrate.util.CapturingPrintStream;
import to.rtc.cli.migrate.util.CapturingWrapper;
//import to.rtc.cli.migrate.util.ChangeLogPrintUtil;
import to.rtc.cli.migrate.util.ChangeSetUtils;

public abstract class MigrateTo extends AbstractSubcommand implements ISubcommand, ILineLogger {

	private StreamOutput output;
	private boolean listTagsOnly = false;
	private final CapturingWrapper capturingWrapper = new CapturingWrapper();

	private IProgressMonitor getMonitor() {
		return new LogTaskMonitor(new StreamOutput(config.getContext().stdout()));
	}

	public abstract Migrator getMigrator();

	public abstract Pattern getBaselineIncludePattern();

	/**
	 * Logs a line of text, typically with a timestamp prepended.
	 * 
	 * @param lineOfText The human-readable line of text to be logged.
	 */
	public synchronized void writeLineToLog(String lineOfText) {
		if (output == null) {
			setStdOut();
			output = new StreamOutput(config.getContext().stdout());
		}
		output.writeLine(lineOfText);
	}

	@Override
	public void run() throws FileSystemException {
		boolean isUpdateMigration = false;
		long start = System.currentTimeMillis();
		Boolean migrationWasStoppedEarly = false;

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
				writeLineToLog("***** LIST ONLY THE TAGS *****");
			}

			if (subargs.hasOption(MigrateToOptions.OPT_RTC_IS_UPDATE_MIGRATION)) {
				isUpdateMigration = true;
				writeLineToLog("***** IS UPDATE MIGRATION *****");
			}

			final ScmCommandLineArgument sourceWsOption = ScmCommandLineArgument.create(
					subargs.getOptionValue(MigrateToOptions.OPT_SRC_WS), config);
			SubcommandUtil.validateArgument(sourceWsOption, ItemType.WORKSPACE);
			final ScmCommandLineArgument destinationWsOption = ScmCommandLineArgument.create(
					subargs.getOptionValue(MigrateToOptions.OPT_DEST_WS), config);
			SubcommandUtil.validateArgument(destinationWsOption, ItemType.WORKSPACE);
			final ScmCommandLineArgument designatedSourceOption;
			if ( subargs.hasOption(MigrateToOptions.OPT_RTC_DESIGNATED_SOURCE)) {
				designatedSourceOption = ScmCommandLineArgument.create(
						subargs.getOptionValue(MigrateToOptions.OPT_RTC_DESIGNATED_SOURCE), config);
				SubcommandUtil.validateArgument(designatedSourceOption, ItemType.WORKSPACE);
			} else {
				designatedSourceOption = null;
			}
			final long maxTagsToProcess;
			if (subargs.hasOption(MigrateToOptions.OPT_MAX_TAGS)) {
				String value = subargs.getOptionValue(MigrateToOptions.OPT_MAX_TAGS)
						.getValue();
				maxTagsToProcess = Long.valueOf(value).longValue();
				if ( maxTagsToProcess<1L ) {
					throw new IllegalArgumentException(
							MigrateToOptions.OPT_MAX_TAGS.getName() + " argument value must be >= 1.");
				}
			} else {
				maxTagsToProcess = Long.MAX_VALUE;
			}
			final long maxChangesetsToProcess;
			if (subargs.hasOption(MigrateToOptions.OPT_MAX_CHANGESETS)) {
				String value = subargs.getOptionValue(MigrateToOptions.OPT_MAX_CHANGESETS)
						.getValue();
				maxChangesetsToProcess = Long.valueOf(value).longValue();
				if ( maxChangesetsToProcess<1L ) {
					throw new IllegalArgumentException(
							MigrateToOptions.OPT_MAX_CHANGESETS.getName() + " argument value must be >= 1.");
				}
			} else {
				maxChangesetsToProcess = Long.MAX_VALUE;
			}

			// Initialize connection to RTC
			writeLineToLog("Initialize RTC connection with connection timeout of " + timeout + "s");
			IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
			ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, destinationWsOption);
			repo.setConnectionTimeout(timeout);

			IWorkspace sourceWs = RepoUtil.getWorkspace(sourceWsOption.getItemSelector(), true, false, repo, config);
			IWorkspace destinationWs = RepoUtil.getWorkspace(destinationWsOption.getItemSelector(), true, false, repo,
					config);
			writeLineToLog("RTC source workspace = " + sourceWs.getItemId().getUuidValue());
			final String destinationWsUuid = destinationWs.getItemId().getUuidValue();
			writeLineToLog("RTC destination workspace = " + destinationWsUuid);
			String designatedSourceId;
			IWorkspace designatedSourceWs;
			if ( designatedSourceOption==null ) {
				designatedSourceId = sourceWs.getItemId().getUuidValue();
				designatedSourceWs = sourceWs;
			} else {
				designatedSourceWs = RepoUtil.getWorkspace(designatedSourceOption.getItemSelector(), true, true, repo,
						config);
				designatedSourceId = designatedSourceWs.getItemId().getUuidValue();
				writeLineToLog("RTC designated source = " + designatedSourceId);
			}

			writeLineToLog("Get full history information from RTC. This could take a large amount of time.");
			writeLineToLog("Create the list of baselines");
			RtcTagList tagList = createTagListFromBaselines(client, repo, sourceWs);

			writeLineToLog("Get changeset information for all baselines");
			addChangeSetInfo(tagList, repo, sourceWs, destinationWs, designatedSourceId);
			writeLineToLog("Fully populate changeset information");
			ChangeSetUtils.fullyPopulateChangeSetsInTagList(tagList, repo, designatedSourceWs);
			writeLineToLog("ChangeSet and Baseline summary");
			tagList.printTagList(false, maxTagsToProcess, maxChangesetsToProcess);
			writeLineToLog("Unfiltered ChangeSet and Baseline details:");
			tagList.printTagList(true, maxTagsToProcess, maxChangesetsToProcess);

			writeLineToLog("Filter included baselines using pattern '"+getBaselineIncludePattern()+"'...");

			// Sorting is required before pruning if migration from multiple components should be done. Otherwise tags
			// of some code could be wrong.
			tagList.sortByCreationDate();
			tagList.pruneInactiveTags();
			tagList.pruneExcludedTags(getBaselineIncludePattern());

			writeLineToLog("Filtered ChangeSet and Baseline details:");
			tagList.printTagList(true, maxTagsToProcess, maxChangesetsToProcess);

			if (listTagsOnly) {
				// Stop here before migration of any data
				return;
			}

			final File sandboxDirectory;
			writeLineToLog("Start migration of tags.");
			if (subargs.hasOption(CommonOptions.OPT_DIRECTORY)) {
				sandboxDirectory = new File(subargs.getOption(CommonOptions.OPT_DIRECTORY));
			} else {
				sandboxDirectory = new File(System.getProperty("user.dir"));
			}
			final Migrator migrator = getMigrator();
			migrator.init(sandboxDirectory, isUpdateMigration);

			Map<String, String> destinationWsComponents = RepoUtil.getComponentsInSandbox(
					destinationWsUuid, new PathLocation(sandboxDirectory.getAbsolutePath()),
					client, config);
			final List<String> destinationWsComponentNamesAndIds = new ArrayList<String>(destinationWsComponents.values());
			destinationWsComponentNamesAndIds.addAll(destinationWsComponents.keySet());

			final RtcMigrator rtcMigrator = new RtcMigrator(output, config, repo, destinationWs, migrator,
					sandboxDirectory, destinationWsComponentNamesAndIds, isUpdateMigration, capturingWrapper);
			boolean isFirstTag = true;
			final int numberOfTags = tagList.size();
			int tagCounter = 0;
			long changesetCounter = 0;
			for (RtcTag tag : tagList) {
				if (tagCounter >= maxTagsToProcess) {
					int tagsRemaining = numberOfTags - tagCounter;
					writeLineToLog("Aborting migration after " + tagCounter + " tags as requested by "
							+ MigrateToOptions.OPT_MAX_TAGS.getName() + " argument.");
					writeLineToLog(
							"There are " + tagsRemaining + " tag" + (tagsRemaining == 1 ? "" : "s") + " remaining.");
					migrationWasStoppedEarly = true;
					break;
				}
				if (isUpdateMigration && isFirstTag && tag.isEmpty()) {
					writeLineToLog("Ignore migration of tag [" + tag.toString() + "] because it is empty.");
					tagCounter++;
					continue;
				}
				isFirstTag = false;
				final long startTag = System.currentTimeMillis();
				writeLineToLog("Start migration of Tag [" + tag.getName() + "] [" + (tagCounter + 1) + "/"
						+ numberOfTags + "]");
				final int maxChangesetsToProcessInThisBatch = (int) Math.min(Integer.MAX_VALUE,
						maxChangesetsToProcess - changesetCounter);
				final int changesetsInThisBatch = rtcMigrator.migrateTag(tag, maxChangesetsToProcessInThisBatch, tagList);
				final int changesetsProcessedInThisBatch = Math.min(maxChangesetsToProcessInThisBatch, changesetsInThisBatch);
				tagCounter++;
				changesetCounter += changesetsProcessedInThisBatch;
				if (changesetsInThisBatch > changesetsProcessedInThisBatch) {
					final int tagsRemaining = numberOfTags - tagCounter;
					final int changesetsRemainingInThisTag = changesetsInThisBatch - changesetsProcessedInThisBatch;
					writeLineToLog("Aborting migration after " + changesetCounter + " changesets as requested by "
							+ MigrateToOptions.OPT_MAX_CHANGESETS.getName() + " argument.");
					writeLineToLog("There are " + changesetsRemainingInThisTag + " changeset"
							+ (changesetsRemainingInThisTag == 1 ? "" : "s") + " remaining in this tag, and " + tagsRemaining
							+ " other tag" + (tagsRemaining == 1 ? "" : "s") + " remaining.");
					migrationWasStoppedEarly = true;
					break;
				}
				writeLineToLog("Migration of tag [" + tag.getName() + "] [" + (tagCounter) + "/" + numberOfTags
						+ "] took [" + (System.currentTimeMillis() - startTag) / 1000 + "] s");
			}
		} catch (Throwable t) {
			t.printStackTrace(output.getOutputStream());
			migrationWasStoppedEarly = null;
			if( t instanceof RuntimeException ) { throw (RuntimeException)t; }
			if( t instanceof Error ) { throw (Error)t; }
			if( t instanceof FileSystemException ) { throw (FileSystemException)t; }
			throw new RuntimeException("Unexpected error during migration: " + ExceptionHelper.exceptionToString(t), t);
		} finally {
			String migrationString = migrationWasStoppedEarly==null ? "Failed migration" : (migrationWasStoppedEarly ? "Incomplete migration" : "Migration");
			long timeTakenInSeconds = (System.currentTimeMillis() - start) / 1000;
			writeLineToLog(migrationString + " took [" + timeTakenInSeconds + "] s");
		}
	}

	private void setStdOut() {
		final PrintStream originalStdout = config.getContext().stdout();
		final PrintStream timestampedStdout = new LoggingPrintStream(originalStdout);
		final PrintStream capturingStdout = new CapturingPrintStream(timestampedStdout, capturingWrapper);
		Class<?> c = LocalContext.class;
		Field subargs;
		try {
			subargs = c.getDeclaredField("stdout");
			subargs.setAccessible(true);
			subargs.set(config.getContext(), capturingStdout);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Finds out what the most-recent changeset UUID is for each component.
	 * @param repo The repo
	 * @param sourceWs The workspace
	 * @return A Map of component-ID to changeset-ID
	 */
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
					lastChangeSets.put(component.getItemId().getUuidValue(), changeSetHandle.getItemId().getUuidValue());
				}
			}
		} catch (TeamRepositoryException e) {
			e.printStackTrace(output.getOutputStream());
			throw new RuntimeException("Failed to getLastChangeSetUuids", e);
		}
		return lastChangeSets;
	}

	@SuppressWarnings("deprecation")
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
					if (baseline == null) {
						// a "<Component removed>" history entry has no baseline.
						continue;
					}
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
			throw new RuntimeException("Failed to createTagListFromBaselines", e);
		}
		return tagList;
	}

	private void addChangeSetInfo(RtcTagList tagList, ITeamRepository repo, IWorkspace sourceWs,
			IWorkspace destinationWs, String designatedSourceId) {

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
			writeLineToLog("Get list of baselines and changesets form RTC.");
			long startTime = System.currentTimeMillis();
			ChangeLogEntryDTO changelog = clOp.run(getMonitor());
			writeLineToLog("Get list of baselines and changesets form RTC took ["
					+ (System.currentTimeMillis() - startTime) / 1000 + "]s.");
			writeLineToLog("Parse the list of baselines and changesets.");
			HistoryEntryVisitor visitor = new HistoryEntryVisitor(repo.getRepositoryURI(),
					designatedSourceId, tagList,
					getLastChangeSetUuids(repo, sourceWs),
					NoOpChangeLogOutput.INSTANCE);
			startTime = System.currentTimeMillis();
			visitor.acceptInto(changelog);
			writeLineToLog("Parse the list of baselines and changesets took ["
					+ (System.currentTimeMillis() - startTime) / 1000 + "]s.");
			// new ChangeLogPrintUtil(output.getOutputStream()).writeLn(changelog);

		} catch (TeamRepositoryException e) {
			e.printStackTrace(output.getOutputStream());
			throw new RuntimeException("Failed to addChangeSetInfo", e);
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
