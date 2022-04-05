package to.rtc.cli.migrate.git;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RmCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.ignore.FastIgnoreRule;
import org.eclipse.jgit.ignore.IgnoreNode;
import org.eclipse.jgit.ignore.IgnoreNode.MatchResult;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.WindowCacheConfig;

import to.rtc.cli.migrate.ChangeSet;
import to.rtc.cli.migrate.ChangeSet.WorkItem;
import to.rtc.cli.migrate.ILineLogger;
import to.rtc.cli.migrate.Migrator;
import to.rtc.cli.migrate.Tag;
import to.rtc.cli.migrate.util.CommentTranslator;
import to.rtc.cli.migrate.util.CommitCommentTranslator;
import to.rtc.cli.migrate.util.CommitNoteTranslator;
import to.rtc.cli.migrate.util.DirectoryUtils;
import to.rtc.cli.migrate.util.Files;
import to.rtc.cli.migrate.util.JazzignoreTranslator;

/**
 * Git implementation of a {@link Migrator}.
 *
 * @author otmar.humbel
 * @author patrick.reinhart
 */
public final class GitMigrator implements Migrator {
	private static final String GIT_ALLOW_EMPTY_COMMITS = "git.allow.empty.commits";
	static final String DEFAULT_INITIAL_COMMIT_USERNAME = "RTC 2 git";
	static final String DEFAULT_INITIAL_COMMIT_USEREMAIL = "rtc2git@rtc.to";
	private static final String IGNORED_FILE_EXTENSIONS_PROPERTY_NAME = "ignore.file.extensions";
	private static final String IGNORED_ROOT_ENTRIES_PROPERTY_NAME = "global.gitignore.entries";
	private static final String PRESERVE_EMPTY_FOLDERS_PROPERTY_NAME = "preserve.empty.folders";
	private static final String PURGE_GITIGNORE_FILES_BETWEEN_COMMITS_PROPERTY_NAME = "purge.gitignore.files.between.commits";
	private static final String GIT_CONFIG_PREFIX = "git.config.";
	/** Root stuff we must always ignore, no matter what we're told to do. */
	static final List<String> ROOT_ALWAYS_IGNORED_ENTRIES = Arrays.asList("/.jazz5", "/.jazzShed", "/.metadata");
	private static final Pattern VALUE_PATTERN = Pattern.compile("^([0-9]+) *(m|mb|k|kb|)$", Pattern.CASE_INSENSITIVE);

	private final ILineLogger logger;
	private final Charset defaultCharset;
	/** File extensions we have been told we MUST auto-ignore regardless of .jazzignore settings */
	private final Set<String> ignoredFileExtensions;
	/** Root files we have been told we MUST ignore regardless of .jazzignore settings */
	private final Set<String> ignoredRootEntries;
	private final WindowCacheConfig WindowCacheConfig;

	private int commitsAfterClean;
	private Git git;
	private Properties properties;
	private PersonIdent defaultIdent;
	static final String DEFAULT_INITIAL_COMMIT_COMMENT = "Initial commit";
	private String initialCommitComment = DEFAULT_INITIAL_COMMIT_COMMENT;
	private File rootDir;
	private CommitCommentTranslator commentTranslator;
	private CommitNoteTranslator noteTranslator;
	private final Long timestampOfInitialCommit;
	private final String gitAutocrlfOrNullForDefault;
	private boolean allowEmptyCommits;
	private boolean trustJazzignoreFiles;
	private boolean preserveEmptyFolders;
	private boolean purgeGitignoreFilesBetweenCommits;
	/** Files that we're going to ignore regardless of what .jazzignore files might say */
	private IgnoreNode ignoredFilePatterns;

	public GitMigrator(ILineLogger logger, Properties properties, Date initialCommitTimestampOrNull, String gitCoreAutocrlfOrNullForDefault) {
		this.logger = logger;
		defaultCharset = Charset.forName("UTF-8");
		ignoredFileExtensions = new LinkedHashSet<String>();
		ignoredRootEntries = new LinkedHashSet<String>();
		WindowCacheConfig = new WindowCacheConfig();
		commitsAfterClean = 0;
		initialize(properties);
		timestampOfInitialCommit = initialCommitTimestampOrNull == null ? null : initialCommitTimestampOrNull.getTime();
		this.gitAutocrlfOrNullForDefault = gitCoreAutocrlfOrNullForDefault;
	}

	private Charset getCharset() {
		return defaultCharset;
	}

	private Collection<String> getGitInfoExcludedEntries() throws IOException {
		Charset charset = getCharset();
		File rootGitInfoExcludeFile = new File(rootDir, ".git/info/exclude");
		List<String> gitInfoExcludedEntries = Files.readLines(rootGitInfoExcludeFile, charset);
		return gitInfoExcludedEntries;
	}

	private void addGitInfoExcludedEntries(Collection<String> ignoreEntriesToAddIfNotAlreadyThere) throws IOException {
		if ( ignoreEntriesToAddIfNotAlreadyThere!=null && !ignoreEntriesToAddIfNotAlreadyThere.isEmpty()) {
			File rootGitFolder = new File(rootDir, ".git");
			File rootGitInfoFolder = new File(rootGitFolder, "info");
			rootGitInfoFolder.mkdir();
			File rootGitInfoExcludeFile = new File(rootGitInfoFolder, "exclude");
			addLinesToTextFileUnlessAlreadyPresent(rootGitInfoExcludeFile, ignoreEntriesToAddIfNotAlreadyThere);
		}
	}

	private static final FileFilter FILTER_NONIGNORED_ROOT_FOLDERS_ONLY = new FilterNonignoredRootFoldersOnly();
	private static class FilterNonignoredRootFoldersOnly implements FileFilter {
		@Override
		public boolean accept(File pathname) {
			if (!pathname.isDirectory()) {
				return false;
			}
			final String name = pathname.getName();
			if (name.equals(".git")) {
				return false;
			}
			final String slashName = "/" + name;
			for (final String toBeIgnored : ROOT_ALWAYS_IGNORED_ENTRIES) {
				if (slashName.equals(toBeIgnored)) {
					return false;
				}
			}
			return true;
		}
	};

	private static final FileFilter FILTER_DIRECTORIES_ONLY = new FilterDirectoriesOnly();
	private static class FilterDirectoriesOnly implements FileFilter {
		@Override
		public boolean accept(File pathname) {
			return pathname.isDirectory();
		}
	};

	private Set<File> findAllFoldersToConsiderForGitIgnores() throws IOException {
		final Set<File> result = new TreeSet<File>();
		result.add(rootDir);
		final File subfolders[] = rootDir.listFiles(FILTER_NONIGNORED_ROOT_FOLDERS_ONLY);
		for (final File subfolder : subfolders) {
			findSubfoldersToConsiderForGitIgnores(result, subfolder);
		}
		return result;
	}

	private void findSubfoldersToConsiderForGitIgnores(Collection<File> result, File folder) throws IOException {
		result.add(folder);
		final File subfolders[] = folder.listFiles(FILTER_DIRECTORIES_ONLY);
		for (final File subfolder : subfolders) {
			findSubfoldersToConsiderForGitIgnores(result, subfolder);
		}
	}

	/**
	 * A {@link Map} of <code>.gitignore</code> file content, indexed by their
	 * parent folder's {@link File}, representing what we committed last time and
	 * used by {@link #ensureGitignoreIsCorrectEverywhere()}. This cache enables us
	 * to avoid having to run numerous git-status commands (which are slow), albeit
	 * at the cost of increased memory usage. It is only used if we're not trusting
	 * <code>.jazzignore</code> files (if we are trusting, we don't need to do
	 * costly special handling of gitignore files in the first place).
	 */
	private Map<File, Set<String>> cachedLastCommittedGitIgnoreState = null;

	private void ensureGitignoreIsCorrectEverywhere() throws IOException, GitAPIException {
		final Set<File> allFolders = findAllFoldersToConsiderForGitIgnores();
		if (trustJazzignoreFiles) {
			for (final File folder : allFolders) {
				ensureGitignoreIsCorrectForFolder(folder, true);
			}
		} else {
			final Map<File, Set<String>> allGitIgnoresAsTheyShouldBeNow = new HashMap<File, Set<String>>();
			for (final File folder : allFolders) {
				final Set<String> correctGitIgnoreContents = ensureGitignoreIsCorrectForFolder(folder, true);
				allGitIgnoresAsTheyShouldBeNow.put(folder, correctGitIgnoreContents);
			}
			// if we can't trust jazz ignore files not to ignore things we mustn't ignore
			// then we're going to have to handle the git-add/git-rm for those HERE
			// and then delete/modify them into a trusted form so they don't mess up
			// everything else
			// However, this involves a lot of git calls, which are slow, so we do them
			// in batches, one batch per level of depth of folders (as sibling folders
			// can't interfere with one another).
			// So, first we order our folders by depth...
			final Map<Integer, Set<File>> allFoldersByDepth = new TreeMap<Integer, Set<File>>();
			final int rootDepth = calculateDepth(rootDir);
			for (final File folder : allFolders) {
				final int folderDepth = calculateDepth(folder);
				final Integer depthRelativeToRoot = Integer.valueOf(folderDepth - rootDepth);
				Set<File> foldersAtThisDepth = allFoldersByDepth.get(depthRelativeToRoot);
				if (foldersAtThisDepth == null) {
					foldersAtThisDepth = new TreeSet<File>();
					allFoldersByDepth.put(depthRelativeToRoot, foldersAtThisDepth);
				}
				foldersAtThisDepth.add(folder);
			}
			// then handle the folders one depth at a time
			// As it's a tree-set, it'll be in order of increasing depth, which is what we
			// need.
			for (final Map.Entry<Integer, Set<File>> depthAndFolders : allFoldersByDepth.entrySet()) {
				// for each layer of depth, we git-status then git-add and/or git-rm the
				// gitignore files (as needed)...
				final Set<String> filesToAdd = new TreeSet<String>();
				final Set<String> filesToRemove = new TreeSet<String>();
				if ( cachedLastCommittedGitIgnoreState==null ) {
					// we have to do this the slow way
					final Status gitStatus = git.status().call();
					for (final File folder : depthAndFolders.getValue()) {
						commitGitIgnoreForFolder(gitStatus, filesToAdd, filesToRemove, folder);
					}
				} else {
					for (final File folder : depthAndFolders.getValue()) {
						commitGitIgnoreForFolder(cachedLastCommittedGitIgnoreState, allGitIgnoresAsTheyShouldBeNow, filesToAdd, filesToRemove, folder);
					}
				}
				if (!filesToAdd.isEmpty()) {
					final AddCommand gitAdd = git.add();
					for( final String path : filesToAdd ) {
						gitAdd.addFilepattern(path);
					}
					gitAdd.call();
				}
				if (!filesToRemove.isEmpty()) {
					final RmCommand gitRm = git.rm();
					for( final String path : filesToRemove ) {
						gitRm.addFilepattern(path);
					}
					gitRm.call();
				}
				// ... before resetting them to a safe state (usually deleting them) as only
				// once
				// the gitignores have been neutralised will it be safe to process subfolders
				for (final File folder : depthAndFolders.getValue()) {
					ensureGitignoreIsCorrectForFolder(folder, false);
				}
			}
			cachedLastCommittedGitIgnoreState = allGitIgnoresAsTheyShouldBeNow;
		}
	}

	private static int calculateDepth(final File f) {
		if (f == null) {
			return 0;
		}
		return 1 + calculateDepth(f.getParentFile());
	}

	private void removeGitignoreEverywhere() throws IOException {
		final Set<File> allFolders = findAllFoldersToConsiderForGitIgnores();
		for (final File folder : allFolders) {
			removeGitignoreForFolder(folder);
		}
	}

	private void removeGitignoreForFolder(File folder) throws IOException {
		final File gitIgnoreFile = new File(folder, ".gitignore");
		final boolean gitignoreExists = gitIgnoreFile.exists();
		if (gitignoreExists) {
			Files.delete(gitIgnoreFile);
		}
	}

	private Set<String> ensureGitignoreIsCorrectForFolder(File folder, boolean includeJazzIgnoreContent) throws IOException, GitAPIException {
		final File gitIgnoreFile = new File(folder, ".gitignore");
		final File jazzIgnoreFile = new File(folder, ".jazzignore");
		final boolean jazzignoreExists = includeJazzIgnoreContent && jazzIgnoreFile.exists();
		final boolean gitignoreExists = gitIgnoreFile.exists();
		final boolean isRootDir = folder.equals(rootDir);
		final Set<String> ignoreEntries = new LinkedHashSet<String>();
		final Set<String> permanentRootGitIgnoreContents;
		if (isRootDir) {
			permanentRootGitIgnoreContents = getGloballyConfiguredGitIgnoreLines();
			ignoreEntries.addAll(permanentRootGitIgnoreContents);
		} else {
			permanentRootGitIgnoreContents = null;
		}
		if (jazzignoreExists) {
			final List<String> jazzIgnoreContent = JazzignoreTranslator.toGitignore(jazzIgnoreFile);
			ignoreEntries.addAll(jazzIgnoreContent);
			// some ignore files ignore themselves, so lets prevent that
			ignoreEntries.add("!/.gitignore");
			ignoreEntries.add("!/.jazzignore");
		}
		if (preserveEmptyFolders && !isRootDir && !jazzignoreExists && ignoreEntries.isEmpty()) {
			final String[] folderContents = folder.list();
			final boolean isEmptyNonRootFolder = folderContents != null & ( folderContents.length == 0 || (folderContents.length==1 && ".gitignore".equals(folderContents[0])) );
			if (isEmptyNonRootFolder) {
				ignoreEntries.add("# Generated because property " + PRESERVE_EMPTY_FOLDERS_PROPERTY_NAME + " is true");
			}
		}
		final boolean shouldHaveGitIgnore = jazzignoreExists || !ignoreEntries.isEmpty();
		final Charset charset = getCharset();
		if (shouldHaveGitIgnore) {
			if (gitignoreExists) {
				// must only change the file if necessary
				final List<String> existingGitIgnoreContent = Files.readLines(gitIgnoreFile, charset);
				final List<String> desiredGitIgnoreContent = new ArrayList<String>(ignoreEntries);
				if (!existingGitIgnoreContent.equals(desiredGitIgnoreContent)) {
					Files.writeLines(gitIgnoreFile, ignoreEntries, charset, false);
				}
			} else {
				// file needs to be created
				Files.writeLines(gitIgnoreFile, ignoreEntries, charset, false);
			}
			return ignoreEntries;
		} else {
			if (gitignoreExists) {
				// gitignore exists but should not
				Files.delete(gitIgnoreFile);
			}
			return null;
		}
	}

	private void commitGitIgnoreForFolder(final Status gitStatus, Set<String> filesToAdd, Set<String> filesToRm, File folder) throws IOException, GitAPIException {
		final File gitIgnoreFile = new File(folder, ".gitignore");
		final String gitignorePath = Files.relativePath(rootDir, gitIgnoreFile);
		if (gitStatus.getModified().contains(gitignorePath) || gitStatus.getUntracked().contains(gitignorePath)) {
			filesToAdd.add(gitignorePath);
		} else
		if ( gitStatus.getMissing().contains(gitignorePath) ) {
			filesToRm.add(gitignorePath);
		}
	}

	private void commitGitIgnoreForFolder(final Map<File, Set<String>> lastCommittedGitIgnoreStates, final Map<File, Set<String>> mostRecentlyCalculatedGitIgnoreStates, Set<String> filesToAdd, Set<String> filesToRm, File folder) throws IOException, GitAPIException {
		final Set<String> lastCommitedContents = lastCommittedGitIgnoreStates.get(folder);
		final Set<String> mostRecentlyCalculatedContents = mostRecentlyCalculatedGitIgnoreStates.get(folder);
		if( lastCommitedContents==null ) {
			// last time we committed, there was no gitignore file here
			if ( mostRecentlyCalculatedContents==null ) {
				// we still don't want a file here
				return; // no changes required
			} else {
				final File gitIgnoreFile = new File(folder, ".gitignore");
				final String gitignorePath = Files.relativePath(rootDir, gitIgnoreFile);
				filesToAdd.add(gitignorePath); // need to add it
			}
		} else {
			// last time we committed, there was a gitignore file here
			if ( mostRecentlyCalculatedContents==null ) {
				// we don't want one anymore
				final File gitIgnoreFile = new File(folder, ".gitignore");
				final String gitignorePath = Files.relativePath(rootDir, gitIgnoreFile);
				filesToRm.add(gitignorePath); // need to remove it
			} else {
				// we still want a file here, but has it changed?
				if ( !mostRecentlyCalculatedContents.equals(lastCommitedContents)) {
					// file has changed so need to add it to the commit
					final File gitIgnoreFile = new File(folder, ".gitignore");
					final String gitignorePath = Files.relativePath(rootDir, gitIgnoreFile);
					filesToAdd.add(gitignorePath); // need to add it
				}
				// else it hasn't changed
			}
		}
	}

	private Set<String> getGloballyConfiguredGitIgnoreLines() throws IOException {
		final Set<String> ignoreEntries = new LinkedHashSet<String>();
		// If we have a .git/info/exclude file, we may not need so much in our
		// .gitignore file.
		final Collection<String> gitInfoExcludedEntries = getGitInfoExcludedEntries();
		// Don't need to .gitignore anything this repo's .git/info/exclude filter
		// ignores
		ignoreEntries.addAll(ROOT_ALWAYS_IGNORED_ENTRIES);
		ignoreEntries.removeAll(gitInfoExcludedEntries);
		// Now add in anything the migration properties says to add
		if ( !ignoredRootEntries.isEmpty() ) {
			ignoreEntries.add("# Generated from property " + IGNORED_ROOT_ENTRIES_PROPERTY_NAME);
			ignoreEntries.addAll(ignoredRootEntries);
		}
		if ( !ignoredFileExtensions.isEmpty() ) {
			ignoreEntries.add("# Generated from property " + IGNORED_FILE_EXTENSIONS_PROPERTY_NAME);
			for (final String ignoredFileExtension : ignoredFileExtensions) {
				ignoreEntries.add("*" + ignoredFileExtension);
			}
		}
		return ignoreEntries;
	}

	private void initRootGitattributes(File sandboxRootDirectory) throws IOException {
		addLinesToTextFileUnlessAlreadyPresent(new File(sandboxRootDirectory, ".gitattributes"), getGitattributeLines());
	}

	private List<String> addLinesToTextFileUnlessAlreadyPresent(File rootFile, Collection<String> linesToAdd) throws IOException {
		final Charset charset = getCharset();
		final List<String> existingLines = Files.readLines(rootFile, charset);
		final List<String> existingPlusLinesToAdd = new ArrayList<String>(existingLines);
		final boolean haveAddedSomething = addMissing(existingPlusLinesToAdd, linesToAdd);
		if (haveAddedSomething && !existingPlusLinesToAdd.isEmpty()) {
			Files.writeLines(rootFile, existingPlusLinesToAdd, charset, false);
			return existingLines;
		}
		return null;
	}

	/**
	 * Given a string of ';'-separated elements, adds them to a list.
	 * @param elementString A string if ';'-separated elements.
	 * @param consumer The string to be added to.
	 */
	private void parseElements(String elementString, Collection<String> consumer) {
		if (elementString == null || elementString.isEmpty()) {
			return;
		}
		String[] splitted = elementString.split(";");
		int splittedLength = splitted.length;
		for (int i = 0; i < splittedLength; i++) {
			consumer.add(splitted[i].trim());
		}
	}

	boolean addMissing(Collection<String> existing, Collection<String> adding) {
		boolean haveAddedSomething = false;
		for (String entry : adding) {
			if (!existing.contains(entry)) {
				existing.add(entry);
				haveAddedSomething = true;
			}
		}
		return haveAddedSomething;
	}

	String getCommitMessage(String workItemNumbers, String comment, String workItemTexts, String serverUrl, String streamId, String changeSetId) {
		return formatMessage("commit.message.format", "%1s %2s", properties, commentTranslator, workItemNumbers, comment, workItemTexts, serverUrl, streamId, changeSetId);
	}

	String getCommitNote(String workItemNumbers, String comment, String workItemTexts, String serverUrl, String streamId, String changeSetId) {
		return formatMessage("commit.note.format", "", properties, noteTranslator, workItemNumbers, comment, workItemTexts, serverUrl, streamId, changeSetId);
	}

	private static String formatMessage(String formatPropertyName, String defaultFormat, Properties properties,
			CommentTranslator translator, String workItemNumbers, String comment, String workItemTexts,
			String serverUrl, String streamId, String changeSetId) {
		String actualFormat = properties.getProperty(formatPropertyName, defaultFormat);
		String arg1 = workItemNumbers;
		String arg2 = translator.translate(comment);
		String arg3 = workItemTexts;
		String arg4 = serverUrl;
		String arg5 = streamId;
		String arg6 = changeSetId;
		return String.format(actualFormat, arg1, arg2, arg3, arg4, arg5, arg6).trim();
	}

	List<String> getGitattributeLines() {
		List<String> lines = new ArrayList<String>();
		parseElements(properties.getProperty("gitattributes", ""), lines);
		return lines;
	}

	Set<String> getIgnoredFileExtensions() {
		return ignoredFileExtensions;
	}

	WindowCacheConfig getWindowCacheConfig() {
		return WindowCacheConfig;
	}

	String getCommentText(ChangeSet changeSet) {
		String comment = changeSet.getComment();
		String workItemText = changeSet.getWorkItems().isEmpty() ? "" : changeSet.getWorkItems().get(0).getText();
		final boolean substituteWorkItemText = Boolean
				.valueOf(properties.getProperty("rtc.changeset.comment.substitute", Boolean.FALSE.toString()));
		if (comment.isEmpty() && substituteWorkItemText)
			return workItemText;
		final String format = properties.getProperty("rtc.changeset.comment.format", "%1s");
		return String.format(format, comment, workItemText);
	}

	private String formatWorkItems(String rtcServerURI, List<? extends WorkItem> workItems, String propertyPrefix, String defaultPrefix, String defaultFormat, String defaultDelimiter, String defaultSuffix) {
		if (workItems.isEmpty()) {
			return "";
		}
		final String prefix = properties.getProperty(propertyPrefix + ".prefix.untrimmed", defaultPrefix);
		final String format = properties.getProperty(propertyPrefix + ".format", defaultFormat);
		final String delimiter = properties.getProperty(propertyPrefix + ".delimiter.untrimmed", defaultDelimiter);
		final String suffix = properties.getProperty(propertyPrefix + ".suffix.untrimmed", defaultSuffix);
		final StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		Formatter formatter = new Formatter(sb);
		try {
			for (WorkItem workItem : workItems) {
				Long workItemNumber = Long.valueOf(workItem.getNumber());
				String workItemText = workItem.getText();
				if (isFirst) {
					formatter.format(prefix, rtcServerURI);
					isFirst = false;
				} else {
					formatter.format(delimiter, rtcServerURI);
				}
				formatter.format(format, workItemNumber, workItemText, rtcServerURI);
			}
			formatter.format(suffix, rtcServerURI);
		} finally {
			formatter.close();
		}
		return sb.toString();
	}

	String getWorkItemNumbers(String rtcServerURI, List<WorkItem> workItems) {
		return formatWorkItems(rtcServerURI, workItems, "rtc.workitem.number", "", "%1s", " ", "");
	}

	String getWorkItemTexts(String rtcServerURI, List<WorkItem> workItems) {
		return formatWorkItems(rtcServerURI, workItems, "rtc.workitem.text", "", "%1s %2s", "%n", "");
	}

	private void gitCommit(PersonIdent author, PersonIdent commiter, String comment, String notes) {
		try {
			ensureGitignoreIsCorrectEverywhere();
			Status status = git.status().call();
			logger.writeLineToLog("Git status shows:");
			logGitStatus(status);

			Set<String> toAdd = handleNewAndModified(status);
			Set<String> toRemove = handleRemoved(status);
			logger.writeLineToLog("Processed status shows:");
			logIfNotEmpty("  toGitAdd=====", toAdd);
			logIfNotEmpty("  toGitRemove==", toRemove);
			final boolean commitIsEmpty = toAdd.isEmpty() && toRemove.isEmpty() && status.getAdded().isEmpty() && status.getChanged().isEmpty() && status.getRemoved().isEmpty();
			if (commitIsEmpty) {
				logger.writeLineToLog("  no changes for git to process");
			}

			// execute the git index commands if needed
			final int maxFilesPerBatchToAvoidOverloadingJGit = 10;
			/*
			 * Bug workaround: When adding a huge amount of files, we sometimes get
			 * an java.io.EOFException from JGit saying
			 * "Input did not match supplied length. <N> bytes are missing."
			 * This bug was logged (and allegedly fixed) ages ago but the workaround
			 * mentioned was to add files in smaller batches.
			 * 
			 * ...but this was more likely caused by inconsistent git-lfs data where
			 * files should've been pointers but weren't.
			 */
			if (!toAdd.isEmpty()) {
				AddCommand add = null;
				int numberOfFiles = 0;
				for (String filepattern : toAdd) {
					if (add == null) {
						add = git.add();
					}
					add.addFilepattern(filepattern);
					numberOfFiles++;
					if( numberOfFiles >= maxFilesPerBatchToAvoidOverloadingJGit) {
						add.call();
						add = null;
						numberOfFiles = 0;
					}
				}
				if (add != null) {
					add.call();
				}
			}
			if (!toRemove.isEmpty()) {
				RmCommand rm = null;
				int numberOfFiles = 0;
				for (String filepattern : toRemove) {
					if (rm == null) {
						rm = git.rm();
					}
					rm.addFilepattern(filepattern);
					numberOfFiles++;
					if (numberOfFiles >= maxFilesPerBatchToAvoidOverloadingJGit) {
						rm.call();
						rm = null;
						numberOfFiles = 0;
					}
				}
				if (rm != null) {
					rm.call();
				}
			}

			if (allowEmptyCommits || !commitIsEmpty) {
				RevCommit commit = git.commit().setMessage(comment).setAuthor(author).setCommitter(commiter).setAllowEmpty(allowEmptyCommits).call();
				if ( notes!=null && !notes.isEmpty()) {
					git.notesAdd().setObjectId(commit).setMessage(notes).call();
				}
			} else {
				logger.writeLineToLog("Ignoring changeset \"" + comment + "\"/\"" + notes + "\" by \"" + author
						+ "\" as there are no changes (to non-ignored files) and property " + GIT_ALLOW_EMPTY_COMMITS
						+ " is set to " + allowEmptyCommits + ".");
			}
			// RTC can get confused and upset if we have .gitignore files within the
			// workspace when it tries to accept a new changeset, so we can remove
			// them once we've committed in order to avoid this scenario.
			// In particular, if we've got a gitignore file in an otherwise-empty
			// folder that's then deleted, RTC can fail to delete the folder.
			if (purgeGitignoreFilesBetweenCommits) {
				removeGitignoreEverywhere();
			} else {
				if (!trustJazzignoreFiles) {
					// We deleted (and/or edited) many of the gitignore files already.
					// Put them back.
					final Set<String> toRestore = new TreeSet<String>(status.getMissing());
					toRestore.addAll(status.getModified());
					removeAllNonGitIgnorePathsFromSet(toRestore);
					if( !toRestore.isEmpty() ) {
						final CheckoutCommand checkout = git.checkout().setStartPoint(Constants.HEAD);
						for( final String gitIgnoreWeNeedToRestore : toRestore) {
							checkout.addPath(gitIgnoreWeNeedToRestore);
						}
						checkout.call();
					}
				}
			}

			++commitsAfterClean;
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("Unable to commit changes", e);
		}
	}

	@Override
	public boolean needsIntermediateCleanup() {
		return commitsAfterClean >= 1000;
	}

	@Override
	public void intermediateCleanup() {
		runGitGc();
		commitsAfterClean = 0;
	}

	void logGitStatus(Status status) {
		logIfNotEmpty("  Added=================", new TreeSet<String>(status.getAdded()));
		logIfNotEmpty("  Changed===============", new TreeSet<String>(status.getChanged()));
		logIfNotEmpty("  Conflicting===========", new TreeSet<String>(status.getConflicting()));
		logIfNotEmpty("  ConflictingStageState=", status.getConflictingStageState());
		logIfNotEmpty("  IgnoredNotInIndex=====", new TreeSet<String>(status.getIgnoredNotInIndex()));
		logIfNotEmpty("  Removed===============", new TreeSet<String>(status.getRemoved()));
		logIfNotEmpty("  UncommittedChanges====", new TreeSet<String>(status.getUncommittedChanges()));
		logFileList("  Untracked======== ", "\n                  = ", false, status.getUntracked());
		logFileList("  UntrackedFolders= ", "\n                  = ", false, status.getUntrackedFolders());
		logFileList("  Modified========= ", "\n                  = ", false, status.getModified());
		logFileList("  Missing========== ", "\n                  = ", false, status.getMissing());
	}

	private void logIfNotEmpty(String line, Iterable<?> whatToLog) {
		if( whatToLog.iterator().hasNext()) {
			logger.writeLineToLog(line + whatToLog);
		}
	}

	private void logIfNotEmpty(String line, Map<?,?> whatToLog) {
		if( !whatToLog.isEmpty()) {
			logger.writeLineToLog(line + whatToLog);
		}
	}

	private void logFileList(final String prefix, final String lineSep, final boolean recurse, final Iterable<String> files) {
		final String multiLineString = new DirectoryUtils().logFileList(rootDir, prefix, lineSep, recurse, files);
		final String[] multiLines = multiLineString.split("\n");
		for (final String line : multiLines) {
			logger.writeLineToLog(line);
		}
	}

	/**
	 * Finds all files that need to be removed in this commit.
	 * 
	 * @param status    State of git repo that tells us what's changed.
	 * @return List of files that are really being removed.
	 */
	private Set<String> handleRemoved(Status status) {
		final Set<String> toRemove = new TreeSet<String>(status.getMissing());
		if ( !trustJazzignoreFiles ) {
			// if we're not trusting jazzignore files then we will have already git-added
			// (or git-removed) the .gitignore files before deleting them from the local
			// filesystem (as we can't trust their contents not to screw things up)
			// This means that we expect to have a lot of "missing" gitignore files that
			// we mustn't git-rm ... but some of those will need removing as their folder
			// might've been deleted.
			// So, we need to remove all gitignores from the toRemove list whose folder
			// still exists, but make sure any gitignores that must really be removed
			// stay in the toRemote list.
			final Set<String> missingGitIgnores = new TreeSet<String>(toRemove);
			removeAllNonGitIgnorePathsFromSet(missingGitIgnores);
			for( final String gitignorePath : missingGitIgnores ) {
				final File gitignoreFolder = new File(rootDir, gitignorePath).getParentFile();
				if( gitignoreFolder.isDirectory() ) {
					toRemove.remove(gitignorePath);
				}
			}
		}
		return toRemove;
	}

	/**
	 * Finds all files that need to be added to a commit, excluding files we're told to ignore.
	 * 
	 * @param status State of git repo that tells us what's changed.
	 * @return List of files that need to be included in the commit.
	 * @throws IOException if we can't edit the ignore files when we need to
	 */
	private Set<String> handleNewAndModified(Status status) throws IOException {
		final Set<String> toAdd = new TreeSet<String>();
		// go over untracked files
		toAdd.addAll(status.getUntracked());
		// go over modified files
		toAdd.addAll(status.getModified());
		if ( !trustJazzignoreFiles ) {
			// if we're not trusting jazzignore files then we will have already git-added
			// (or git-removed) the .gitignore files before deleting them from the local
			// filesystem (as we can't trust their contents not to screw things up)
			removeAllGitIgnorePathsFromSet(toAdd);
		}
		return toAdd;
	}

	private void initConfig() throws IOException {
		StoredConfig config = git.getRepository().getConfig();
		config.setBoolean("core", null, "ignoreCase", false);
		final String defaultAutocrlf = File.separatorChar == '/' ? "input" : "true";
		final String autocrlf = gitAutocrlfOrNullForDefault == null ? defaultAutocrlf : gitAutocrlfOrNullForDefault;
		config.setString("core", null, "autocrlf", autocrlf);
		config.setBoolean("http", null, "sslverify", false);
		config.setString("push", null, "default", "simple");
		fillConfigFromProperties(config);
		config.save();
	}

	private int getFactor(String sign) {
		if (!sign.isEmpty()) {
			switch (sign.charAt(0)) {
			case 'k':
			case 'K':
				return org.eclipse.jgit.storage.file.WindowCacheConfig.KB;
			case 'm':
			case 'M':
				return org.eclipse.jgit.storage.file.WindowCacheConfig.MB;
			}
		}
		return 1;
	}

	long parseConfigValue(String value, long defaultValue) {
		if (value != null) {
			Matcher matcher = VALUE_PATTERN.matcher(value.trim());
			if (matcher.matches()) {
				return getFactor(matcher.group(2)) * Long.parseLong(matcher.group(1));
			}
		}
		return defaultValue;
	}

	void initialize(Properties props) {
		properties = props;
		commentTranslator = new CommitCommentTranslator(props);
		noteTranslator = new CommitNoteTranslator(props);
		defaultIdent = new PersonIdent(props.getProperty("user.name", DEFAULT_INITIAL_COMMIT_USERNAME),
				props.getProperty("user.email", DEFAULT_INITIAL_COMMIT_USEREMAIL));
		initialCommitComment = props.getProperty("initial.comment", DEFAULT_INITIAL_COMMIT_COMMENT);
		parseElements(props.getProperty(IGNORED_FILE_EXTENSIONS_PROPERTY_NAME, ""), ignoredFileExtensions);
		parseElements(props.getProperty(IGNORED_ROOT_ENTRIES_PROPERTY_NAME, ""), ignoredRootEntries);
		preserveEmptyFolders = Boolean.parseBoolean(props.getProperty(PRESERVE_EMPTY_FOLDERS_PROPERTY_NAME, "false"));
		purgeGitignoreFilesBetweenCommits = Boolean.parseBoolean(props.getProperty(PURGE_GITIGNORE_FILES_BETWEEN_COMMITS_PROPERTY_NAME, "false"));
		allowEmptyCommits = Boolean.parseBoolean(props.getProperty(GIT_ALLOW_EMPTY_COMMITS, "false"));
		trustJazzignoreFiles = Boolean.parseBoolean(props.getProperty("trust.jazzignore.files", "true"));
		// update window cache config
		WindowCacheConfig cfg = getWindowCacheConfig();
		cfg.setPackedGitOpenFiles(
				(int) parseConfigValue(props.getProperty("packedgitopenfiles"), cfg.getPackedGitOpenFiles()));
		cfg.setPackedGitLimit(parseConfigValue(props.getProperty("packedgitlimit"), cfg.getPackedGitLimit()));
		cfg.setPackedGitWindowSize(
				(int) parseConfigValue(props.getProperty("packedgitwindowsize"), cfg.getPackedGitWindowSize()));
		cfg.setPackedGitMMAP(Boolean.parseBoolean(props.getProperty("packedgitmmap")));
		cfg.setDeltaBaseCacheLimit(
				(int) parseConfigValue(props.getProperty("deltabasecachelimit"), cfg.getDeltaBaseCacheLimit()));
		long sft = parseConfigValue(props.getProperty("streamfilethreshold"), cfg.getStreamFileThreshold());
		cfg.setStreamFileThreshold(getMaxFileThresholdValue(sft, Runtime.getRuntime().maxMemory()));
		final List<FastIgnoreRule> rootRules = new ArrayList<FastIgnoreRule>();
		for( final String ignoreRule : ROOT_ALWAYS_IGNORED_ENTRIES ) {
			final FastIgnoreRule r = new FastIgnoreRule(ignoreRule);
			rootRules.add(r);
		}
		for( final String ignoreRule : ignoredRootEntries ) {
			final FastIgnoreRule r = new FastIgnoreRule(ignoreRule);
			rootRules.add(r);
		}
		for( final String extensionToIgnore : ignoredFileExtensions ) {
			final FastIgnoreRule r = new FastIgnoreRule("*"+extensionToIgnore);
			rootRules.add(r);
		}
		ignoredFilePatterns = new IgnoreNode(rootRules);
	}

	/**
	 * Indicates if we should ignore this file according to what we've been told via properties.
	 * @param path The path under consideration.
	 * @return true if we should ignore the file, false if we should include it in our git repo contents.
	 */
	boolean mustBeIgnored(final String path, final boolean isDirectory) {
		final MatchResult ignored = ignoredFilePatterns.isIgnored(path, isDirectory);
		if ( ignored == MatchResult.IGNORED ) {
			return true;
		}
		return false;
	}

	int getMaxFileThresholdValue(long configThreshold, final long maxMem) {
		// don't use more than 1/4 of the heap
		configThreshold = Math.min(configThreshold, maxMem / 4);
		// cannot exceed array length
		configThreshold = Math.min(configThreshold, Integer.MAX_VALUE);
		return (int) configThreshold;
	}

	/**
	 * Removes all paths from the set except those of gitignore files. This is the
	 * opposite of {@link #removeAllGitIgnorePathsFromSet(Set)}
	 * 
	 * @param paths The set to be modified.
	 */
	static void removeAllNonGitIgnorePathsFromSet(final Set<String> paths) {
		final Iterator<String> i = paths.iterator();
		while (i.hasNext()) {
			final String path = i.next();
			if (!(path.endsWith("/.gitignore") || path.equals(".gitignore"))) {
				i.remove();
			}
		}
	}

	/**
	 * Removes any paths from the set which are for gitignore files. This is the
	 * opposite of {@link #removeAllNonGitIgnorePathsFromSet(Set)}
	 * 
	 * @param paths The set to be modified.
	 */
	static void removeAllGitIgnorePathsFromSet(final Set<String> paths) {
		final Iterator<String> i = paths.iterator();
		while (i.hasNext()) {
			final String path = i.next();
			if (path.endsWith("/.gitignore") || path.equals(".gitignore")) {
				i.remove();
			}
		}
	}

	/**
	* Git imposes the following rules on how references are named:
	* <ol><li>
	* They can include slash / for hierarchical (directory) grouping, but no slash-separated component can begin with a dot . or end with the sequence .lock
	* </li><li>
	* They must contain at least one /. This enforces the presence of a category like heads/, tags/ etc. but the actual names are not restricted. If the --allow-onelevel option is used, this rule is waived
	* </li><li>
	* They cannot have two consecutive dots .. anywhere
	* </li><li>
	* They cannot have ASCII control characters (i.e. bytes whose values are lower than \040, or \177 DEL), space, tilde ~, caret ^, or colon : anywhere
	* </li><li>
	* They cannot have question-mark ?, asterisk *, or open bracket [ anywhere. See the --refspec-pattern option below for an exception to this rule
	* </li><li>
	* They cannot begin or end with a slash / or contain multiple consecutive slashes (see the --normalize option below for an exception to this rule)
	* </li><li>
	* They cannot end with a dot .
	* </li><li>
	* They cannot contain a sequence @{
	* </li><li>
	* They cannot be the single character @
	* </li><li>
	* They cannot contain a \
	* </li></ol>
	* In addition, git-lfs's command line assumes that it's OK to use a comma as a separator,
	* so if we have a comma we'll make life very difficult for anyone using git-lfs, so we avoid that too.
	* <br>
	* RTC imposes no such restrictions on basenames; not even uniqueness.
	*/
	static String createTagName(final String rtcName) {
		final StringBuilder sb = new StringBuilder(rtcName);
		// 1. can't begin with a dot .
		replaceAll(sb, "/.", "/_");
		// 1. can't end with the sequence .lock
		replaceAll(sb, ".lock/", "_lock/");
		// 3. cannot have two consecutive dots .. anywhere
		replaceAll(sb, "..", "__");
		// 4. cannot have ASCII control characters (i.e. bytes whose values are lower than \040, or \177 DEL)
		for( int i=0; i<32 ; i++ ) {
			final char c = (char)i;
			replaceAll(sb, c, '_');
		}
		replaceAll(sb, (char)127, '_');
		// 4. ... space, tilde ~, caret ^, or colon :
		replaceAll(sb, ' ', '_');
		replaceAll(sb, '~', '_');
		replaceAll(sb, '^', '_');
		replaceAll(sb, ':', '_');
		// 5. cannot have question-mark ?, asterisk *, or open bracket [
		replaceAll(sb, '?', '_');
		replaceAll(sb, '*', '_');
		replaceAll(sb, '[', '_');
		// 6. cannot contain multiple consecutive slashes
		// 6. cannot begin or end with a slash /
		// While we could permit single slashes, that could lead to problems (it'd mean
		// we couldn't have a basename of "a/b" and "a" because git can't make a tag "a"
		// as "a" is a folder in the tag hierarchy), so we force things to stay in a
		// single-layer hierarchy.
		replaceAll(sb, '/', '_');
		// 8. cannot contain a sequence @{
		replaceAll(sb, "@{", "@_");
		// 10. cannot contain a \
		replaceAll(sb, '\\', '_');
		// git-lfs doesn't play well with commas in names
		replaceAll(sb, ',', '_');
		final String gitTagName = sb.toString()
				// 1. can't begin with a dot .
				.replaceAll("^\\.", "_")
				// 1. can't end with the sequence .lock
				.replaceAll("\\.lock$", "_lock")
				// 7. cannot end with a dot .
				.replaceAll("\\.$", "_")
				;
		// Note: Points 2 and 9 are satisfied because a git tag is always refs/tags/gitTagName
		return gitTagName;
	}

	private static void replaceAll(StringBuilder sb, final char find, final char replace) {
		final int len = sb.length();
		for( int i=0 ; i<len ; i++) {
			if( sb.charAt(i)==find) {
				sb.setCharAt(i, replace);
			}
		}
	}

	private static void replaceAll(StringBuilder sb, final String find, final String replace) {
		final int findLen = find.length();
		for( int i=sb.indexOf(find) ; i>=0 ; i=sb.indexOf(find, i)) {
			sb.replace(i, i + findLen, replace);
		}
	}

	@Override
	public void init(File sandboxRootDirectory, boolean isUpdate) {
		rootDir = sandboxRootDirectory;
		try {
			File bareGitDirectory = new File(sandboxRootDirectory, ".git");
			if (bareGitDirectory.exists()) {
				git = Git.open(sandboxRootDirectory);
			} else if (sandboxRootDirectory.exists() && !isUpdate) {
				git = Git.init().setDirectory(sandboxRootDirectory).call();
			} else {
				throw new RuntimeException(bareGitDirectory + " does not exist");
			}
			getWindowCacheConfig().install();
			if ( !isUpdate ) {
				initRootGitattributes(sandboxRootDirectory);
				initConfig();
				addGitInfoExcludedEntries(ROOT_ALWAYS_IGNORED_ENTRIES);
				long when = timestampOfInitialCommit == null ? System.currentTimeMillis()
						: timestampOfInitialCommit.longValue();
				final PersonIdent pi = new PersonIdent(defaultIdent, when, 0);
				gitCommit(pi, pi, initialCommitComment, null);
			}
			Status status = git.status().call();
			logger.writeLineToLog("Initial repository contents before migration:");
			logFileList("  All= ", "\n     = ", false, new DirectoryUtils().getRecursiveDirListing(sandboxRootDirectory,
					DirectoryUtils.PATTERNS_TO_IGNORE));
			logGitStatus(status);
		} catch (IOException e) {
			throw new RuntimeException("Unable to initialize GIT repository", e);
		} catch (GitAPIException e) {
			throw new RuntimeException("Unable to initialize GIT repository", e);
		}
	}

	@Override
	public void close() {
		cachedLastCommittedGitIgnoreState = null;
		if (git != null) {
			logger.writeLineToLog("Closing down JGit instance.");
			git.close();
			git = null;
		}
	}

	private void runGitGc() {
		final long gcStartMilliseconds = System.currentTimeMillis();
		logger.writeLineToLog("Running git gc...");
		try {
			git.gc().call();
			final long gcEndMilliseconds = System.currentTimeMillis();
			final long gcDuration = gcEndMilliseconds - gcStartMilliseconds;
			logger.writeLineToLog("Completed git gc in " + gcDuration + "ms.");
		} catch (GitAPIException e) {
			final long gcEndMilliseconds = System.currentTimeMillis();
			final long gcDuration = gcEndMilliseconds - gcStartMilliseconds;
			throw new IllegalStateException("Failed to do Git garbage-collection; crashed after " + gcDuration + "ms.", e);
		}
	}

	@Override
	public void commitChanges(ChangeSet changeset) {
		PersonIdent author = new PersonIdent(changeset.getCreatorName(), changeset.getEmailAddress(), changeset.getCreationDate(),
				0);
		PersonIdent committer;
		if ( changeset.getAddedToStreamDate()!=ChangeSet.DATE_UNKNOWN ) {
			committer = new PersonIdent(changeset.getAddedToStreamName(), changeset.getAddedToStreamEmailAddress(), changeset.getAddedToStreamDate(),
					0);
		} else {
			committer = author;
		}
		String uri = changeset.getServerURI();
		String workItemTexts = getWorkItemTexts(uri, changeset.getWorkItems());
		String workItemNumbers = getWorkItemNumbers(uri, changeset.getWorkItems());
		String commentText = getCommentText(changeset);
		String streamId = changeset.getStreamID();
		String changesetId = changeset.getChangesetID();
		String commitMessage = getCommitMessage(workItemNumbers, commentText, workItemTexts, uri, streamId, changesetId);
		String notes = getCommitNote(workItemNumbers, commentText, workItemTexts, uri, streamId, changesetId);
		gitCommit(author, committer, commitMessage, notes);
	}

	@Override
	public void createTag(Tag tag) {
		String tagName = tag.getName();
		if (tagName != null && !tagName.isEmpty()) {
			final String gitTagName = createTagName(tagName);
			try {
				git.tag().setForceUpdate(true).setTagger(defaultIdent).setName(gitTagName).call();
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				throw new RuntimeException("Unable make tag '"+gitTagName+"': "+e.toString(), e);
			}
		}
	}

	private void fillConfigFromProperties(Config config) {
		for (Entry<Object, Object> entry : properties.entrySet()) {
			if (entry.getKey() instanceof String && (((String) entry.getKey()).startsWith(GIT_CONFIG_PREFIX))) {

				String key = ((String) entry.getKey()).substring(GIT_CONFIG_PREFIX.length());
				int dot = key.indexOf('.');
				int dot1 = key.lastIndexOf('.');
				// so far supporting section/key entries, no subsections
				if (dot < 1 || dot == key.length() - 1 || dot1 != dot) {
					// invalid config key entry
					continue;
				}
				String section = key.substring(0, dot);
				String name = key.substring(dot + 1);
				config.setString(section, null, name, entry.getValue().toString());
			}

		}
	}
}
