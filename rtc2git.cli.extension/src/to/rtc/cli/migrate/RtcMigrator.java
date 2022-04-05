package to.rtc.cli.migrate;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import com.ibm.team.filesystem.cli.core.Constants;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.rtc.cli.infrastructure.internal.core.CLIClientException;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.scm.common.IWorkspace;

import to.rtc.cli.migrate.command.AcceptCommandDelegate;
import to.rtc.cli.migrate.command.ConflictsCommandDelegate;
import to.rtc.cli.migrate.command.LoadCommandDelegate;
import to.rtc.cli.migrate.command.RefreshCommandDelegate;
import to.rtc.cli.migrate.command.RepairCommandDelegate;
import to.rtc.cli.migrate.command.SandboxStructureCommandDelegate;
import to.rtc.cli.migrate.command.StatusCommandDelegate;
import to.rtc.cli.migrate.command.UnloadWorkspaceCommandDelegate;
import to.rtc.cli.migrate.util.AcceptCommandOutputParser;
import to.rtc.cli.migrate.util.CapturingWrapper;
import to.rtc.cli.migrate.util.ChangeSetUtils;
import to.rtc.cli.migrate.util.DirectoryUtils;
import to.rtc.cli.migrate.util.ExceptionHelper;
import to.rtc.cli.migrate.util.Files;
import to.rtc.cli.migrate.util.GitignoreRemoverUtil;
import to.rtc.cli.migrate.util.LoadCommandHelper;
import to.rtc.cli.migrate.util.ShowSandboxStructureCommandOutputParser;

public class RtcMigrator {

	private static final int ACCEPTS_BEFORE_LOCAL_HISTORY_CLEAN = 1000;
	protected final IChangeLogOutput output;
	private final IScmClientConfiguration config;
	private final String workspace;
	private final Migrator migrator;
	private final Set<String> initiallyLoadedComponents;
	private File sandboxDirectory;
	private final CapturingWrapper capturingWrapper;
	private final GitignoreRemoverUtil gitignoreRemoverUtil;
	private final DirectoryUtils dirUtils;
	private final String rtcUri;
	private final String rtcUsername;
	private final String rtcPassword;
	private final boolean includeRoot;
	private boolean identifyAndAcceptMissingChangeSets;
	private ITeamRepository iTeamRepository;
	private IWorkspace iWorkspace;

	public RtcMigrator(IChangeLogOutput output, IScmClientConfiguration config, ITeamRepository teamRepository,
			IWorkspace workspace, Migrator migrator, File sandboxDirectory,
			Collection<String> initiallyLoadedComponents, boolean isUpdateMigration,
			CapturingWrapper capturingWrapper) {
		this.output = output;
		this.config = config;
		this.iTeamRepository = teamRepository;
		this.iWorkspace = workspace;
		this.workspace = workspace.getItemId().getUuidValue();
		this.migrator = migrator;
		this.sandboxDirectory = sandboxDirectory;
		this.initiallyLoadedComponents = new HashSet<String>(initiallyLoadedComponents);
		this.capturingWrapper = capturingWrapper;
		this.gitignoreRemoverUtil = new GitignoreRemoverUtil();
		this.dirUtils = new DirectoryUtils();
		final ICommandLine ourCmdLine = config.getSubcommandCommandLine();
		this.rtcUri = ourCmdLine.getOption(CommonOptions.OPT_URI);
		this.rtcUsername = ourCmdLine.getOption(CommonOptions.OPT_USERNAME);
		if (ourCmdLine.hasOption(CommonOptions.OPT_PASSWORD)) {
			this.rtcPassword = ourCmdLine.getOption(CommonOptions.OPT_PASSWORD);
		} else {
			try {
				this.rtcPassword = config.getConnectionInfo().getPassword();
			} catch (FileSystemException e) {
				throw new RuntimeException("Unable to get password", e);
			}
		}
		this.includeRoot = ourCmdLine.hasOption(MigrateToOptions.OPT_INCLUDE_ROOT);
		this.identifyAndAcceptMissingChangeSets = ourCmdLine.hasOption(MigrateToOptions.OPT_IDENTIFY_CHANGESETS_TO_FILL_GAP);
	}

	/**
	 * Accepts and commits a batch of changesets. If the tag's
	 * {@link RtcTag#doCreateTag()} flag is set and we migrate all the changesets
	 * within the tag then the tag will be committed too.
	 * 
	 * @param tag           Contains the changesets to be migrated.
	 * @param maxChangeSets An upper limit on the number of changesets to process
	 *                      before returning.
	 * @return The number of changesets that were present in this batch. This may be
	 *         more than we processed if maxChangeSets is a smaller number.
	 * @throws CLIClientException if anything went wrong.
	 */
	public int migrateTag(RtcTag tag, int maxChangeSets, RtcTagList allChangeSetsWeKnowAbout)
			throws CLIClientException {
		final List<RtcChangeSet> allOrderedChangeSetsInTag = tag.getOrderedChangeSets();
		final int numberOfChangesetsInTag = allOrderedChangeSetsInTag.size();
		int changeSetCounter = 0;
		while (!allOrderedChangeSetsInTag.isEmpty()) {
			final RtcChangeSet nextDesiredChangeset = allOrderedChangeSetsInTag.get(0);
			final int nextDesiredChangesetIndexNumber = numberOfChangesetsInTag - allOrderedChangeSetsInTag.size() + 1;
			final String nextDesiredChangesetIndexString = "[" + nextDesiredChangesetIndexNumber + "]/["
					+ numberOfChangesetsInTag + "]";
			if (identifyAndAcceptMissingChangeSets) {
				final List<RtcChangeSet> unexpectedChangeSets = getOtherChangesetsWeWillNeedBeforeAcceptingThisOne(
						nextDesiredChangeset, allOrderedChangeSetsInTag, allChangeSetsWeKnowAbout, iTeamRepository,
						iWorkspace);
				int unexpectedCount = 0;
				final int numberOfUnexpectedChangeSets = unexpectedChangeSets.size();
				for (final RtcChangeSet unexpectedChangeSet : unexpectedChangeSets) {
					unexpectedCount++;
					final String unexpectedChangesetIndexString = nextDesiredChangesetIndexString + ".["
							+ unexpectedCount + "/" + numberOfUnexpectedChangeSets + "]";
					migrateChangeSet(unexpectedChangeSet, tag, unexpectedChangesetIndexString);
					changeSetCounter++;
					considerCleaningLocalHistoryAsWeveJustDoneAnAccept();
					// Note: We don't stop looping here even if we've done too many changesets
					// as otherwise we could leave things in a mess.
				}
			}
			allOrderedChangeSetsInTag.remove(nextDesiredChangeset);
			final Boolean isOkToStopHere = migrateChangeSet(nextDesiredChangeset, tag, nextDesiredChangesetIndexString);
			changeSetCounter++;
			considerCleaningLocalHistoryAsWeveJustDoneAnAccept();
			if (changeSetCounter >= maxChangeSets) {
				if (Boolean.TRUE.equals(isOkToStopHere)) {
					break; // stop looping as we've done enough
				} else {
					if( !allOrderedChangeSetsInTag.isEmpty()) {
						output.writeLine("INFO: We have migrated more changesets (" + changeSetCounter
								+ ") than requested (" + maxChangeSets
								+ ") but we can't stop here as we're at a bad point in history.");
					}
				}
			}
		}
		cleanLocalHistory();
		if (allOrderedChangeSetsInTag.isEmpty() && tag.doCreateTag()) {
			migrator.createTag(tag);
		}
		return numberOfChangesetsInTag;
	}

	/**
	 * Sometimes RTC needs us to accept additional changesets before we can accept
	 * the one we want to accept. This method detects if this is true and finds out
	 * what extra changesets we need.
	 * 
	 * @param changeSet                The changeset we want to accept
	 * @param changeSetsInCurrentBatch All other changesets we're expecting to do in
	 *                                 the current migration batch
	 * @param allChangeSetsWeKnowAbout All other changesets we know about regardless
	 *                                 of batch.
	 * @param repo
	 * @param sourceWs
	 * @return A list of changesets we need to accept, in the order we need to
	 *         accept them, before we can accept the one we want.
	 * @throws CLIClientException
	 */
	private List<RtcChangeSet> getOtherChangesetsWeWillNeedBeforeAcceptingThisOne(final RtcChangeSet changeSet,
			List<RtcChangeSet> changeSetsInCurrentBatch, RtcTagList allChangeSetsWeKnowAbout, ITeamRepository repo,
			IWorkspace sourceWs) throws CLIClientException {
		final String changeSetUuid = changeSet.getUuid();
		final List<String> missingChangeSetUuids = ChangeSetUtils
				.identifyChangeSetsWeNeedToAcceptBeforeThisOne(iTeamRepository, iWorkspace, changeSetUuid);
		if (missingChangeSetUuids.isEmpty()) {
			return new ArrayList<RtcChangeSet>();
		}
		final int numberOfMissingChangeSets = missingChangeSetUuids.size();
		output.writeLine("INFO: Before we accept changeset " + changeSet.getUuid() + " we first need to accept " + numberOfMissingChangeSets + " more: " + missingChangeSetUuids);
		final List<String> unknownChangeSetUuids = new ArrayList<String>();
		final Map<String, RtcChangeSet> populatedChangeSets = new HashMap<String, RtcChangeSet>(
				numberOfMissingChangeSets);
		int i = 0;
		for (final String missingChangeSetUuid : missingChangeSetUuids) {
			i++;
			final Entry<RtcTag, Entry<String, RtcChangeSet>> found = allChangeSetsWeKnowAbout
					.findChangeSetByUuid(missingChangeSetUuid);
			if (found != null) {
				final RtcChangeSet foundChangeSet = found.getValue().getValue();
				final String componentTheChangeSetBelongsTo = found.getValue().getKey();
				final RtcTag tagTheChangeSetBelongsTo = found.getKey();
				allChangeSetsWeKnowAbout.removeChangeSet(tagTheChangeSetBelongsTo, componentTheChangeSetBelongsTo,
						foundChangeSet);
				final boolean wasInThisBatch = changeSetsInCurrentBatch.remove(foundChangeSet);
				populatedChangeSets.put(missingChangeSetUuid, foundChangeSet);
				if (wasInThisBatch) {
					output.writeLine("INFO: Changeset[" + i + "/" + numberOfMissingChangeSets + "] was later in this batch - all it needed was bringing forwards.");
				} else {
					output.writeLine("INFO: Changeset[" + i + "/" + numberOfMissingChangeSets + "] is part of tag '" + tagTheChangeSetBelongsTo.getName() + "' (" + tagTheChangeSetBelongsTo.getUuid() + ") - bringing it into this one.");
				}
			} else {
				output.writeLine("INFO: Changeset[" + i + "/" + numberOfMissingChangeSets + "] is not in the known history - need to obtain full details.");
				unknownChangeSetUuids.add(missingChangeSetUuid);
			}
		}
		if (!unknownChangeSetUuids.isEmpty()) {
			final Map<String, RtcChangeSet> changeSetDetails = ChangeSetUtils.getChangeSetDetails(iTeamRepository,
					iWorkspace, unknownChangeSetUuids);
			for (final RtcChangeSet cs : changeSetDetails.values()) {
				final String uuid = cs.getUuid();
				populatedChangeSets.put(uuid, cs);
			}
		}
		final List<RtcChangeSet> result = new ArrayList<RtcChangeSet>(numberOfMissingChangeSets);
		for (final String uuid : missingChangeSetUuids) {
			final RtcChangeSet cs = populatedChangeSets.get(uuid);
			if (cs != null) {
				result.add(cs);
			}
		}
		return result;
	}

	private Boolean migrateChangeSet(final RtcChangeSet changeSet, RtcTag tag,
			final String humanReadableChangesetIndexWithinTag) throws CLIClientException {
		try {
			final long acceptStartTime = System.currentTimeMillis();
			final Boolean isOkToStopHere = accept(changeSet);
			final long acceptEndTimeAndCommitStartTime = System.currentTimeMillis();
			commit(changeSet);
			final long commitEndTime = System.currentTimeMillis();
			final long acceptDuration = acceptEndTimeAndCommitStartTime - acceptStartTime;
			final long commitDuration = commitEndTime - acceptEndTimeAndCommitStartTime;
			output.writeLine("Migrated [" + tag.getName() + "] " + humanReadableChangesetIndexWithinTag
					+ " changesets. Accept took " + acceptDuration + "ms commit took " + commitDuration + "ms");
			if (migrator.needsIntermediateCleanup()) {
				intermediateCleanup();
			}
			return isOkToStopHere;
		} catch (CLIClientException clie) {
			output.writeLine(ExceptionHelper.cliClientExceptionToString(clie) + " while processing changeset:");
			output.writeLine("  Tag original name       : " + tag.getOriginalName());
			output.writeLine("  Tag creation date       : " + new Date(tag.getCreationDate()));
			output.writeLine("  Changeset comment       : " + changeSet.getComment());
			output.writeLine("  Changeset creator       : " + changeSet.getCreatorName());
			output.writeLine("  Changeset creation date : " + new Date(changeSet.getCreationDate()));
			output.writeLine("  Changeset component     : " + changeSet.getComponent());
			output.writeLine("  Changeset UUID          : " + changeSet.getUuid());
			throw clie;
		}
	}

	void intermediateCleanup() {
		long startCleanup = System.currentTimeMillis();
		migrator.intermediateCleanup();
		output.writeLine("Intermediate cleanup had ["
				+ (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startCleanup)) + "]sec");
	}

	void commit(RtcChangeSet changeSet) {
		migrator.commitChanges(changeSet);
	}

	/**
	 * Accepts a changeset such that the filesystem will reflect the correct state
	 * at that point in history.
	 * 
	 * @param changeSet The changeset we need to accept and put onto the filesystem.
	 * @return true if this seems to be a self-consistent state (i.e. no conflicts),
	 *         false if we shouldn't stop here and need to keep going (i.e. we've
	 *         got merge/conflict issues that require more changesets to resolve),
	 *         null if we're not sure.
	 * @throws CLIClientException if anything goes badly wrong.
	 */
	Boolean accept(RtcChangeSet changeSet) throws CLIClientException {
		final Boolean isOkToStopHere = acceptAndLoadChangeSet(changeSet);
		handleInitialLoad(changeSet);
		return isOkToStopHere;
	}

	private int acceptsBeforeWeNeedToCleanLocalHistory = ACCEPTS_BEFORE_LOCAL_HISTORY_CLEAN;

	private void considerCleaningLocalHistoryAsWeveJustDoneAnAccept() {
		acceptsBeforeWeNeedToCleanLocalHistory--;
		if (acceptsBeforeWeNeedToCleanLocalHistory < 0) {
			cleanLocalHistory();
		}
	}

	private void cleanLocalHistory() {
		acceptsBeforeWeNeedToCleanLocalHistory = ACCEPTS_BEFORE_LOCAL_HISTORY_CLEAN;
		File localHistoryDirectory = new File(sandboxDirectory,
				".metadata/.plugins/org.eclipse.core.resources/.history");
		if (localHistoryDirectory.exists() && localHistoryDirectory.isDirectory()) {
			long start = System.currentTimeMillis();
			Files.delete(localHistoryDirectory);
			output.writeLine("Cleanup of local history had ["
					+ (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start)) + "]sec");
		}
	}

	/**
	 * Pulls in a single changeset into the {@link #sandboxDirectory}, taking
	 * account of <em>most</em> of the ways that RTC can go wrong.
	 * <p>
	 * This is non-trivial because "scm accept" is an error-prone operation:
	 * <ol>
	 * <li>It can fail for no good reason at all, e.g. claiming that the workspace
	 * overlaps with itself (https://www.ibm.com/mysupport/5000z00001Lg5UAAAZ and
	 * https://jazz.net/jazz/web/projects/Rational%20Team%20Concert#action=com.ibm.team.workitem.viewWorkItem&id=518806),
	 * but that doesn't mean it actually failed.</li>
	 * <li>It can fail to apply changes because there are "conflicts with local
	 * changes", which is typically caused by RTC listing the changesets in an order
	 * it can't accept ... but these ought to be resolved once later changesets have
	 * also been accepted.</li>
	 * <li>It can fail because RTC missed out important changesets it now thinks are
	 * required, so we have to use the --accept-missing-changesets option
	 * there.</li>
	 * <li>It can fail to apply the changes correctly, e.g. in a changeset that
	 * "moves a file out of a folder then deletes the folder" it can process the
	 * folder deletes before it's moved files out of the folder, resulting in files
	 * going missing.</li>
	 * <li>It can fail to apply all the changes, e.g. in a changeset that deletes
	 * a file, it can leave the file present ... and it can fail to add a file
	 * that a changeset added.</li>
	 * <li>It can lie about what it did, e.g. by omitting any mention of property
	 * changes or simply omitting any mention of some parts of the changeset.</li>
	 * <li>It can (because the bloaty RTC code contains a complete Eclipse IDE) run
	 * an Eclipse "build all" over any/all Eclipse .projects that are defined,
	 * littering the sandbox with compiled/copied files (Rational have fixed this
	 * but while they back-ported the fix to 6.0.6 and provided that fix to me, they
	 * didn't release the fix to anyone else so you'll have to beg for it).</li>
	 * <li>It can (re)create long-since-deleted <code>.project</code> files, which
	 * can also exacerbate the previous issue as well (another symptom of the
	 * previous bug).</li>
	 * </ol>
	 * To make matters worse, while "in theory" we should be able to just do a "scm
	 * load" to re-load the workspace, overwriting its contents with what should be
	 * there, this can (and often does) also fail (or, at least, it can report that
	 * it failed which, due to bugs in the code, doesn't actually mean that it
	 * failed at all), so we even have to be careful when trying to recover from
	 * these issues.
	 * </p>
	 * <b>TL;DR:</b> The RTC client cannot be trusted. So:
	 * <ul>
	 * <li>if there's a "status gap" we repeat telling it to fill in the gap, and
	 * give up if we can't fix it.</li>
	 * <li>if anything else went wrong, we can try to detect (and ignore)
	 * false-negatives and repair the mess if necessary.</li>
	 * <li>if we don't detect anything going wrong then we compare what files we now
	 * have with what we originally had so we can detect if we've mysteriously
	 * gained rogue .project files or Eclipse build results and delete anything we
	 * shouldn't have.</li>
	 * <li>if we're at all unsure/untrusting of the results, we reload the workspace
	 * (which takes time but is usually effective).
	 * </ol>
	 * i.e. We try to achieve what the "scm accept" command should do, and would do
	 * if it wasn't so bug-ridden.
	 * 
	 * @param changeSet The changeset we need to accept and put onto the filesystem.
	 * @return true if this seems to be a self-consistent state (i.e. no conflicts),
	 *         false if we shouldn't stop here and need to keep going (i.e. we've
	 *         got merge/conflict issues that require more changesets to resolve),
	 *         null if we're not sure.
	 * @throws CLIClientException if anything goes badly wrong.
	 */
	private Boolean acceptAndLoadChangeSet(RtcChangeSet changeSet) throws CLIClientException {
		output.setIndent(2);
		final String changeSetUuid = changeSet.getUuid();
		capturingWrapper.clear();
		final Map<String, Object> sandboxContentBeforeChangeset = getSandboxContentHash();
		boolean acceptMissingChangesetsToClearTheStatusGap = false;
		int result = new AcceptCommandDelegate(config, output, rtcUri, rtcUsername, rtcPassword, workspace, null, changeSetUuid, false, acceptMissingChangesetsToClearTheStatusGap).run();
		if (result == Constants.STATUS_GAP) {
			output.writeLine("INFO: Retry accepting with --accept-missing-changesets");
			capturingWrapper.clear();
			acceptMissingChangesetsToClearTheStatusGap = true;
			result = new AcceptCommandDelegate(config, output, rtcUri, rtcUsername, rtcPassword, workspace, null, changeSetUuid, false, acceptMissingChangesetsToClearTheStatusGap).run();
			// TODO: Work out what changesets were also accepted
			// and include their details in the commit comment.
		}
		final Collection<String> reasonsToReloadWorkspace = new ArrayList<String>();
		final Collection<String> reasonsToShowStatus = new ArrayList<String>();
		boolean scmAcceptWorkedOK = false;
		Boolean weAreAtAGoodPlaceToStop = null;
		switch (result) {
		case Constants.STATUS_CONFLICT:
		case Constants.STATUS_NWAY_CONFLICT:
			// Conflicts can happen because RTC tells us to accept changesets in an order
			// that it can't accept until earlier (but provided later) changesets have been
			// accepted.
			// This sort of problem will go away once the earlier (but provided later in the
			// sequence) changeset has been accepted ... albeit at the cost of changes not
			// showing up at all until that point.
			// However, what we can do is run "scm show conflicts" and "scm show status"
			// so we can see what's going on.
			reasonsToShowStatus.add("Conflict detected");
			weAreAtAGoodPlaceToStop = Boolean.FALSE;
			break;
		case Constants.STATUS_OUT_OF_SYNC:
			output.writeLine("INFO: Accept result indicates reloading the workspace again with force option may allow recovery.");
			reasonsToShowStatus.add("Accept result says OUT_OF_SYNC");
			reasonsToReloadWorkspace.add("Accept result says OUT_OF_SYNC");
			break;
		case Constants.STATUS_GAP:
			throw new CLIClientException("There was a PROBLEM in accepting that we cannot solve.");
		default:
			output.writeLine("ERROR: Accept result was " + result + ", which we have no explicit coding for.");
			reasonsToShowStatus.add("Accept result was " + result);
			reasonsToReloadWorkspace.add("Accept result was " + result);
			break;
		case Constants.STATUS_FAILURE: // it does this sometimes; doesn't mean it failed
			// but it may mean it's corrupted the local filesystem and the local client's idea of
			// whatever the local filesystem should've contained ... because sometimes it
			// really does mean it failed (for no good reason).
			reasonsToShowStatus.add("Accept result was FAILURE");
			reasonsToReloadWorkspace.add("Accept result was FAILURE");
			break;
		case 0: // success
			scmAcceptWorkedOK = true;
			break;
		}
		final List<String> capturedAcceptOutput = new ArrayList<String>(capturingWrapper.getCompleteLines());
		final AcceptCommandOutputParser parsedAcceptOutput = new AcceptCommandOutputParser(capturedAcceptOutput);
		capturingWrapper.clear();
		if (weAreAtAGoodPlaceToStop == null || weAreAtAGoodPlaceToStop.booleanValue() == true) {
			final Boolean weHaveConflicts = parsedAcceptOutput.getConflictsFoundInWorkspace();
			if (weHaveConflicts!=null) {
				weAreAtAGoodPlaceToStop = Boolean.valueOf(!weHaveConflicts.booleanValue());
			}
		}
		// We don't always find changes, and this isn't always an error in the
		// migration.
		// Sometimes users make mistakes, deliver empty changes to RTC, resulting in
		// empty changesets in the history.
		// Sometimes RTC silently omits (even with -v) changes from the report (e.g.
		// changes to file properties) so, if this bug strikes and all the changes in
		// the changeset were omitted then we won't find any changes.
		// Sometimes RTC crashes and outputs an error message instead of saying what it
		// did ... although that's usually a false-negative that we have to ignore.
		//
		// TL;DR: We have to cope when we don't know what to expect.
		if (parsedAcceptOutput.getChangesFound()) {
			// If we can, we avoid doing a "scm load" because it's slow and tends to crash,
			// complaining that our workspace overlaps with itself - it doesn't seem to be
			// able to (reliably & consistently) tell that *the* workspace is the *only*
			// workspace loaded there.
			if( !ourSandboxContainsWhatWeThinkWeShouldHave(sandboxContentBeforeChangeset, parsedAcceptOutput) ) {
				reasonsToReloadWorkspace.add("filesystem inconsistencies detected - scm accept didn't do what it said it'd do");
			}
			if (parsedAcceptOutput.isOrderOfChangesImportant()) {
				reasonsToReloadWorkspace.add("non-trivial changes detected that scm accept cannot be trusted with");
			}
			final List<String> dirsToCheckForOphanedGitignoreFiles = AcceptCommandOutputParser.getOnlyDirectories(parsedAcceptOutput.getDeleted());
			dirsToCheckForOphanedGitignoreFiles.removeAll(parsedAcceptOutput.getAdded());
			if (gitignoreRemoverUtil.removeFoldersKeptDueToLingeringGitignoreFiles(dirsToCheckForOphanedGitignoreFiles, sandboxDirectory, output)) {
				// because we can't tell 100% if a folder was re-added in a changeset, we might
				// end up removing folders that we need to keep, so we have to reload "just in
				// case".
				reasonsToReloadWorkspace.add("deleted some folders that scm accept should probably have deleted itself");
			}
		} else {
			output.writeLine("WARN: No changes detected when parsing accept command output for changeset " + changeSetUuid);
			reasonsToReloadWorkspace.add("scm accept did not show any changes, and we don't trust that to be true");
		}
		capturedAcceptOutput.clear();
		if (reasonsToReloadWorkspace.isEmpty()) {
			// Sadly, when RTC does an "accept" it sometimes doesn't update the filesystem
			// correctly (or at all), or report an error; it just silently fails to do
			// what it claimed to have done.
			// This can be detected by doing a "scm show sandbox-structure" command and
			// seeing if it says "Local file system is out of sync. Run 'scm load' with
			// --force or --resync options to reload the workspace."
			// That command can also tell us that the accept completely borked the sandbox
			// saying "Local file system is corrupt. Run repair command to make the
			// workspace consistent."
			// Either way, we have to trigger our repair code.
			capturingWrapper.clear();
			final List<String> showSandboxStructureLines = new ArrayList<String>();
			try {
				result = new SandboxStructureCommandDelegate(config, output, rtcUsername, rtcPassword, sandboxDirectory).run();
			} catch (CLIClientException ex) {
				// ...however, "show sandbox-structure" can be unreliable
				// So we have to suppress most exceptions from here.
				result = ex.getStatus() == null ? -1 : ex.getStatus().getCode();
				showSandboxStructureLines.add(ExceptionHelper.cliClientExceptionToString(ex));
			}
			switch (result) {
			default:
				showSandboxStructureLines.addAll(capturingWrapper.getCompleteLines());
				final ShowSandboxStructureCommandOutputParser parsedShowSandbox = new ShowSandboxStructureCommandOutputParser(showSandboxStructureLines);
				if (parsedShowSandbox.isFilesystemOutOfSync()) {
					output.writeLine("INFO: Previous accept corrupted the sandbox so we need to reload it (scm show sandbox-structure said \"" + parsedShowSandbox.whatWeTookADislikeTo() + "\").");
					reasonsToReloadWorkspace.add("sandbox is corrupted");
				} else {
					output.writeLine("INFO: scm show sandbox-structure does not indicate a need to reload.");
				}
				break;
			case Constants.STATUS_ARGUMENT_SYNTAX_ERROR:
				// If we have a valid sandbox in which our workspace is loaded but where we
				// don't yet have any component(s) loaded, RTC can return a syntax error
				// complaining that we didn't give it the workspace argument and saying
				// "No workspace loaded." ... when the workspace is loaded and
				// the argument it's saying we must provide is actually optional.
				// In this situation, we simply need to ignore the error because we'll load the
				// component immediately following this operation anyway.
				break;
			}
		}
		if (!reasonsToShowStatus.isEmpty()) {
			output.writeLine("WARN: showing status because " + reasonsToShowStatus);
			// It won't affect the migration work, but it'll be useful to any human who
			// later reviews the logs.
			// Note: We exclude "incoming" details because that drowns us in text.
			try {
				final boolean json = false;
				final boolean verbose = false;
				final boolean all = false;
				final boolean excludeIncoming = true;
				final boolean xchangeset = false;
				final boolean locateConflicts = false;
				new StatusCommandDelegate(config, output, rtcUsername, rtcPassword, sandboxDirectory, json, verbose, all, excludeIncoming, xchangeset, locateConflicts).run();
			} catch (CLIClientException ex) {
				output.writeLine("WARN: scm show status failed."); // the exception will've been logged already
			}
			try {
				final boolean json = false;
				final boolean local = false;
				new ConflictsCommandDelegate(config, output, rtcUri, rtcUsername, rtcPassword, sandboxDirectory, json, local).run();
			} catch (CLIClientException ex) {
				// Note: scm show conflicts usually crashes out with an error and fails to show anything about the conflicts.
				// As this will've been logged, and we're only doing this to log extra information, no further action
				// is required.
			}
		}
		boolean repairedOk = true;
		if (!reasonsToReloadWorkspace.isEmpty()) {
			// If we're here, it's because we know (or suspect) that RTC failed to correctly
			// update the local filesystem with the changes we requested.
			// To resolve this, we do a "repair", then a "refresh", then we unload the workspace
			// and (re)load the workspace.
			// That should then result in us having the desired data on the local filesystem.
			output.writeLine("WARN: reloading workspace because " + reasonsToReloadWorkspace);
			// We can get into a state where show sandbox-status reports that "Local file
			// system is corrupt. Run repair command to make the workspace consistent."
			// and no matter how many times we reload the workspace it stays corrupt.
			// The show sandbox-status command claims that a "repair" will fix that, so
			// we'll try that.
			capturingWrapper.clear();
			try {
				result = new RepairCommandDelegate(config, output, rtcUri, rtcUsername, rtcPassword, sandboxDirectory).run();
				if (result != 0) {
					output.writeLine("INFO: repair command failed (code " + result
							+ "); ignoring as fatal errors will be caught during subsequent load command.");
				}
			} catch (CLIClientException ex) { // the call to .run() will have logged the exception already
				output.writeLine("WARN: repair command crashed; ignoring as fatal errors will be caught during subsequent load command.");
			}
			// Sadly, the "scm refresh" command is failure-prone,
			// e.g. crashing with "Error loading map info".
			// with other logs showing
			// com.ibm.team.internal.repository.rcp.dbhm.DBHMException:
			// com.ibm.team.internal.repository.rcp.dbhm.BadHeapException:
			// Not a heap file: pathToMySandbox/.jazz5/someFolderInRtc/.iteminfo.dat
			capturingWrapper.clear();
			try {
				result = new RefreshCommandDelegate(config, output, sandboxDirectory).run();
				if (result != 0) {
					output.writeLine("INFO: refresh command failed (code " + result
							+ "); ignoring as fatal errors will be caught during subsequent load command.");
				}
			} catch (CLIClientException ex) { // the call to .run() will have logged the exception already
				output.writeLine("WARN: refresh command crashed; ignoring as fatal errors will be caught during subsequent load command.");
			}
			capturingWrapper.clear();
			try {
				new UnloadWorkspaceCommandDelegate(config, output, rtcUri, rtcUsername, rtcPassword, sandboxDirectory, true, true, false, workspace).run();
			} catch (CLIClientException ex) {
				output.writeLine("INFO: Unload#1 failed."); // the call to .run() will have logged the exception already
			}
			capturingWrapper.clear();
			try {
				new UnloadWorkspaceCommandDelegate(config, output, rtcUri, rtcUsername, rtcPassword, null, true, true, false, workspace).run();
			} catch (CLIClientException ex) {
				if (ex.getStatus() == null || ex.getStatus().getCode() != Constants.STATUS_ITEM_NOT_FOUND) {
					// We expect the second unload to return "not found", so that's unremarkable
					output.writeLine("INFO: Unload#2 failed."); // the call to .run() will have logged the exception already
				}
			}
			if (!scmAcceptWorkedOK) {
				// Avoid doing a second scm-accept if we're pretty certain that the original one worked fine.
				// It may be that unnecessary duplicated accepts cause RTC to have problems later.
				capturingWrapper.clear();
				result = new AcceptCommandDelegate(config, output, rtcUri, rtcUsername, rtcPassword, workspace, null, changeSetUuid, false, acceptMissingChangesetsToClearTheStatusGap).run();
				switch (result) {
				case Constants.STATUS_WORKSPACE_UNCHANGED:
					// This means that the changeset was previously accepted
					// So a scm load should give us what we wanted.
					output.writeLine("INFO: Original Accept must've worked ok");
					break;
				case 0:
					output.writeLine("INFO: While the original accept failed, the retried Accept worked OK.");
					break;
				case Constants.STATUS_CONFLICT:
				case Constants.STATUS_NWAY_CONFLICT:
					// These are also "success" return codes.
					output.writeLine("INFO: The retried Accept worked as well as we expected.");
					break;
				default:
					output.writeLine("WARN: The retry of scm accept failed, code " + result +".");
					repairedOk = false;
					break;
				}
			}
			capturingWrapper.clear();
			try {
				result = LoadCommandDelegate.reloadEverything(config, output, rtcUri, rtcUsername, rtcPassword, includeRoot, workspace).run();
			} catch (CLIClientException ex) {
				result = ex.getStatus() == null ? -1 : ex.getStatus().getCode();
			}
			LoadCommandHelper.removeAnyFilesErroneouslyCreatedByScmLoadCommand(output, sandboxDirectory);
			if (result == Constants.STATUS_CFA_COLLISION) {
				// RTC has a couple of reasons why this can happen.
				// 1)
				// RTC can get itself in a muddle and complain that our workspace is loaded in
				// more than one place. If that happens, we can try telling it to unload the
				// workspace and then reload it.
				// ...but we've already unloaded to prevent that, so it must be...
				// 2)
				// There's nothing to stop a workspace having two different files of the same
				// path/name in different components that (obviously) cannot co-exist in the
				// same place on the filesystem.
				//
				// The former we can work around by unloading/reloading the workspace (which
				// we did).
				// The latter is a fatal error and can only be dealt with by changing the way
				// the workspace is loaded in the sandbox so that we have the
				// "--include-roots" flag set ... but this will likely break all code that
				// "knows" where the components are loaded relative to each other, so this
				// isn't something we do automatically - if we find we need this, we just
				// give up.
				output.writeLine("ERROR: Reload failed due to collision (with itself); RTC contains multiple files with the same name/path but in different components.");
				throw new CLIClientException("Workspace " + workspace + " at changeset " + changeSetUuid + " contains components with non-unique path/filenames - you'll need to load using the --include-root flag.");
			}
			if (result != 0) {
				repairedOk = false;
			}
			output.writeLine("INFO: Running scm show status after the repair/refresh/unload/accept/reload.");
			try {
				final boolean json = false;
				final boolean verbose = false;
				final boolean all = false;
				final boolean excludeIncoming = true;
				final boolean xchangeset = false;
				final boolean locateConflicts = false;
				new StatusCommandDelegate(config, output, rtcUsername, rtcPassword, sandboxDirectory, json, verbose, all, excludeIncoming, xchangeset, locateConflicts).run();
			} catch (CLIClientException ex) {
				output.writeLine("WARN: scm show status failed."); // the exception will've been logged already
			}
		}
		if (!repairedOk) {
			output.writeLine("ERROR: Repair failed for changeset \"" + changeSetUuid + "\".  Data corruption is likely.");
		}
		return weAreAtAGoodPlaceToStop;
	}

	/**
	 * Does a check of what we've got and, if it looks OK, we declare success and avoid a full reload.
	 * If it's not OK, we delete anything we think shouldn't be there and then report that things are not OK
	 * so that a reload will be required.
	 * <p>
	 * <b>NOTE:</b> Because RTC lies by omission, this can give a false assurance of success ... or a false report of problems.
	 * @param sandboxContentBeforeChangeset 
	 * @param parsedOutput What we were told to expect.
	 * @return true if all seems to be OK, false if there's doubt.
	 */
	private boolean ourSandboxContainsWhatWeThinkWeShouldHave(Map<String, Object> sandboxContentBeforeChangeset, final AcceptCommandOutputParser parsedOutput) {
		final Set<String> mostOfTheMissingFiles = parsedOutput.findMostOfTheMissingFiles(sandboxDirectory);
		final Set<String> potentiallyUnwantedFiles = parsedOutput.detectPotentiallyUnwantedFiles(sandboxDirectory);
		final Map<String, Object> sandboxContentAfterChangeset = getSandboxContentHash();
		final Map<String, String> sandboxChanges = compareSandboxContents(sandboxContentBeforeChangeset,
				sandboxContentAfterChangeset, "changed", "added", "deleted");
		int numberOfChangesThatAreMissing = 0;
		for (final String path : parsedOutput.getChanged()) {
			final String sandboxChange = sandboxChanges.get(path);
			if (!"changed".equals(sandboxChange)) {
				numberOfChangesThatAreMissing++;
				output.writeLine(
						"INFO: Expecting file " + path + " to have been modified by changeset" + (sandboxChange == null
								? "."
								: (" but was " + sandboxChange)) + ".");
			}
		}
		if (mostOfTheMissingFiles.isEmpty() && potentiallyUnwantedFiles.isEmpty() && numberOfChangesThatAreMissing==0) {
			output.writeLine("INFO: All the files that we expected to be added/removed/changed have been.");
			return true;
		}
		output.writeLine("WARN: We don't have the files we expect so we're going to need to reload"
				+ (mostOfTheMissingFiles.isEmpty() ? ""
						: ("; files we need " + mostOfTheMissingFiles + " aren't on the filesystem"))
				+ (potentiallyUnwantedFiles.isEmpty() ? ""
						: ("; files we expected to be deleted " + mostOfTheMissingFiles + " are still present"))
				+ (numberOfChangesThatAreMissing==0 ? ""
						: ("; " + numberOfChangesThatAreMissing + " file" + (numberOfChangesThatAreMissing == 1 ? " was" : "s were")
								+ " not modified"))
				+ ".");
		for (final String potentiallyUnwantedFile : potentiallyUnwantedFiles) {
			output.writeLine("INFO: deleting potentially-unwanted entry " + potentiallyUnwantedFile);
			deleteFileOrDir(output, sandboxDirectory, potentiallyUnwantedFile);
		}
		return false;
	}

	/**
	 * Given a "before" and "after" map of "thing to content", this returns a map of
	 * "thing to change" where the "change" is added/deleted/changed if the before
	 * and after contents differ and omitting entries where the content did not
	 * change.
	 * 
	 * @param sandboxContentBefore The situation "before"
	 * @param sandboxContentAfter  The situation "after"
	 * @param fileHasBeenChanged   Denotes a change where the "thing" content has
	 *                             changed.
	 * @param fileHasBeenAdded     Denotes a change where the "thing" content exists
	 *                             "after" but not "before".
	 * @param fileHasBeenDeleted   Denotes a change where the "thing" content exists
	 *                             "before" but not "after".
	 * @return A non-null map. This will be empty if there are no changes.
	 */
	private Map<String, String> compareSandboxContents(Map<String, Object> sandboxContentBefore,
			final Map<String, Object> sandboxContentAfter, final String fileHasBeenChanged,
			final String fileHasBeenAdded, final String fileHasBeenDeleted) {
		final Set<String> supersetOfAllPaths = new TreeSet<String>();
		supersetOfAllPaths.addAll(sandboxContentBefore.keySet());
		supersetOfAllPaths.addAll(sandboxContentAfter.keySet());
		final Map<String, String> result = new TreeMap<String, String>();
		for (final String path : supersetOfAllPaths) {
			final Object before = sandboxContentBefore.get(path);
			final Object after = sandboxContentAfter.get(path);
			if (before != null) {
				if (after != null) {
					if (!before.equals(after)) {
						result.put(path, fileHasBeenChanged);
					}
					// else unmodified
				} else {
					result.put(path, fileHasBeenDeleted);
				}
			} else {
				if (after != null) {
					result.put(path, fileHasBeenAdded);
				}
			}
		}
		return result;
	}

	private Map<String, Object> getSandboxContentHash() {
		return dirUtils.getRecursiveDirContentsHash(sandboxDirectory, DirectoryUtils.PATTERNS_TO_IGNORE);
	}

	private static void deleteFileOrDir(final IChangeLogOutput output, final File baseDirectory,
			final String fileOrDirName) {
		final File f = new File(baseDirectory, fileOrDirName);
		deleteFileOrDir(output, f);
	}

	private static void deleteFileOrDir(final IChangeLogOutput output, final File f) {
		if (!f.exists()) {
			return;
		}
		if (f.isDirectory()) {
			deleteDirectoryContents(output, f);
		}
		f.delete();
		if (f.exists()) {
			output.writeLine("WARN: unable to delete " + f);
		}
	}

	private static void deleteDirectoryContents(final IChangeLogOutput output, final File dirToDelete) {
		final String[] dirContents = dirToDelete.list();
		if (dirContents == null) {
			return;
		}
		for (final String dirEntry : dirContents) {
			final File f = new File(dirToDelete, dirEntry);
			deleteFileOrDir(output, f);
		}
	}

	private void handleInitialLoad(RtcChangeSet changeSet) {
		final String componentUuid = changeSet.getComponent();
		if (!initiallyLoadedComponents.contains(componentUuid)) {
			try {
				output.writeLine("[INFO] This is the first time we've encountered component '" + componentUuid + "' so we need to load it into the workspace.");
				LoadCommandDelegate.addComponentToSandbox(config, output, rtcUri, rtcUsername, rtcPassword, includeRoot, workspace, componentUuid).run();
				initiallyLoadedComponents.add(componentUuid);
			} catch (CLIClientException e) {
				throw new RuntimeException("Not a valid sandbox. Please run [scm load " + workspace
						+ "] before [scm migrate-to-git] command", e);
			}
		}
	}
}
