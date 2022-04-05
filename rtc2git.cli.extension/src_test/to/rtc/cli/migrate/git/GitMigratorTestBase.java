package to.rtc.cli.migrate.git;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import to.rtc.cli.migrate.ChangeSet;
import to.rtc.cli.migrate.ChangeSet.WorkItem;
import to.rtc.cli.migrate.ILineLogger;
import to.rtc.cli.migrate.Tag;
import to.rtc.cli.migrate.util.Files;

/**
 * Helper code to test the {@link GitMigrator} implementation.
 *
 * @author patrick.reinhart
 */
public class GitMigratorTestBase {
	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	protected Charset cs;
	protected GitMigrator migrator;
	protected Git git;
	protected Properties props;
	protected File basedir;
	protected ILineLogger logger;

	@SuppressWarnings("serial")
	@Before
	public void setUp() {
		cs = Charset.forName("UTF-8");
		props = new Properties() {
			@Override
			public synchronized Object setProperty(String key, String value) {
				super.setProperty(key+".untrimmed", value);
				return super.setProperty(key, value.trim());
			}
		};
		logger = new ILineLogger() {
			@Override
			public void writeLineToLog(String lineOfText) {
				System.out.println(lineOfText);
			}};
		migrator = new GitMigrator(logger, props, null, null);
		basedir = tempFolder.getRoot();
	}

	@After
	public void tearDown() {
		migrator.close();
		if (git != null) {
			git.close();
		}
	}

	protected static List<TestScenarioStage> ts(TestScenarioStage... stages) {
		return Arrays.asList(stages);
	}

	protected static TestScenarioStage tsInit(TestScenarioFile... files) {
		return new TestScenarioStage(null, files);
	}

	protected static TestScenarioStage tsCs1(TestScenarioFile... files) {
		return new TestScenarioStage(TestChangeSet.INSTANCE, files);
	}

	protected static TestScenarioFile tsDeleteFile(String path) {
		return new TestScenarioFile(path, null, true, false, null);
	}

	protected static TestScenarioFile tsMkCommittedFolder(String path) {
		return new TestScenarioFile(path.endsWith("/")?path:(path+"/"), Arrays.<String>asList(), true, false, true);
	}

	protected static TestScenarioFile tsMkIgnoredFolder(String path) {
		return new TestScenarioFile(path.endsWith("/")?path:(path+"/"), Arrays.<String>asList(), true, false, false);
	}

	protected static TestScenarioFile tsMkCommittedFile(String path, String... content) {
		return new TestScenarioFile(path, (content==null || content.length==0)?Arrays.asList("expectedInGit"):Arrays.asList(content), true, false, true);
	}

	protected static TestScenarioFile tsMkIgnoredFile(String path, String... content) {
		return new TestScenarioFile(path, (content==null || content.length==0)?Arrays.asList("expectedInGit"):Arrays.asList(content), true, false, false);
	}

	protected static TestScenarioFile tsAssertFileContent(String path, String... content) {
		return new TestScenarioFile(path, Arrays.asList(content), false, true, true);
	}

	protected static TestScenarioFile tsAssertFileContentNotInGit(String path, String... content) {
		return new TestScenarioFile(path, Arrays.asList(content), false, true, false);
	}

	protected static TestScenarioFile tsAssertFileAbsentAndNotInGit(String path) {
		return new TestScenarioFile(path, null, false, true, false);
	}

	protected static TestScenarioFile tsAssertFileAbsentButIsInGit(String path) {
		return new TestScenarioFile(path, null, false, true, true);
	}

	protected static TestScenarioFile tsAssertFileDeletedFromGit(String path) {
		return new TestScenarioFile(path, null, false, true, true);
	}

	protected static class TestScenarioStage {
		private final TestChangeSet changesetOrNull;
		private final String username;
		private final String useremail;
		private final String comment;
		private final List<TestScenarioFile> files;
		protected TestScenarioStage(TestChangeSet changesetOrNull, List<TestScenarioFile> files) {
			this.changesetOrNull = changesetOrNull;
			if ( changesetOrNull==null ) {
				username = GitMigrator.DEFAULT_INITIAL_COMMIT_USERNAME;
				useremail = GitMigrator.DEFAULT_INITIAL_COMMIT_USEREMAIL;
				comment = GitMigrator.DEFAULT_INITIAL_COMMIT_COMMENT;
			} else {
				username = changesetOrNull.getCreatorName();
				useremail = changesetOrNull.getEmailAddress();
				comment = changesetOrNull.getWorkItems().get(0).getNumber()+" "+changesetOrNull.getComment();
			}
			this.files = files;
		}
		protected TestScenarioStage(TestChangeSet changesetOrNull, TestScenarioFile... files) {
			this(changesetOrNull, Arrays.asList(files));
		}
		@Override
		public String toString() {
			return "TestScenarioStage("+(changesetOrNull==null?"init":changesetOrNull.name())+")";
		}
	}

	protected static class TestScenarioFile {
		private final String path;
		private final List<String> contentsOrNull;
		private final boolean toBeWritten;
		private final boolean contentsToBeVerified;
		private final Boolean expectedToBeInGitCommit;
		protected TestScenarioFile(String path, List<String> contentsOrNull, boolean toBeWritten,
				boolean contentsToBeVerified, Boolean expectedToBeInGitCommit) {
			this.path = path;
			this.contentsOrNull = contentsOrNull;
			this.toBeWritten = toBeWritten;
			this.contentsToBeVerified = contentsToBeVerified;
			this.expectedToBeInGitCommit = expectedToBeInGitCommit;
		}
		@Override
		public String toString() {
			return "TestScenarioFile [path=" + path + ", toBeWritten=" + toBeWritten + ", expectedToBeInGitCommit="
					+ expectedToBeInGitCommit + ", contentsToBeVerified=" + contentsToBeVerified + ", contentsOrNull="
					+ contentsOrNull + "]";
		}
	}

	protected void testScenario(final boolean trustJazzignoreFilesProperty,
			final String globalGitignoreEntriesProperty, final String ignoreFileExtensionsProperty,
			final List<TestScenarioStage> stages) throws Exception, IOException {
		// set up initial conditions
		props.setProperty("git.allow.empty.commits", "true");
		props.setProperty("global.gitignore.entries", globalGitignoreEntriesProperty);
		props.setProperty("ignore.file.extensions", ignoreFileExtensionsProperty);
		props.setProperty("trust.jazzignore.files", Boolean.valueOf(trustJazzignoreFilesProperty).toString());
		migrator.initialize(props);
		final List<TestScenarioStage> stagesWithInit;
		if ( stages.get(0).changesetOrNull!=null ) {
			stagesWithInit = new ArrayList<GitMigratorTestBase.TestScenarioStage>(stages);
			stagesWithInit.add(0, tsInit());
		} else {
			stagesWithInit = stages;
		}
		for( final TestScenarioStage stage :  stagesWithInit) {
			final String testStepN = (stagesWithInit.indexOf(stage)+1) + " of " + stagesWithInit.size();
			System.out.println("UnitTest: step " + testStepN);
			// create/modify/delete files
			for( final TestScenarioFile f : stage.files) {
				if( f.toBeWritten ) {
					if ( f.contentsOrNull==null ) {
						System.out.println("UnitTest: deleting " + f.path);
						new File(basedir, f.path).delete();
					} else {
						mkFile(f.path, f.contentsOrNull);
					}
				}
			}
			// commit
			if ( stage.changesetOrNull==null ) {
				System.out.println("UnitTest: initialising migration");
				migrator.init(basedir, false);
			} else {
				System.out.println("UnitTest: committing changeset " + stage.changesetOrNull.name());
				migrator.commitChanges(stage.changesetOrNull);
			}
			// test
			final List<String> expectedPathsPresent = new ArrayList<String>();
			final List<String> expectedPathsNotInGit = new ArrayList<String>();
			for( final TestScenarioFile f : stage.files) {
				if( f.contentsToBeVerified ) {
					final File file = new File(basedir, f.path);
					if ( f.contentsOrNull==null ) {
						assertFalse("Test stage " + testStepN + ": "+f.path + " must NOT exist", file.exists());
					} else {
						checkExactLinesIgnoringComments("Test stage " + testStepN + ": ", file, f.contentsOrNull);
					}
				}
				if( f.expectedToBeInGitCommit!=null ) {
					if( f.expectedToBeInGitCommit.booleanValue() ) {
						expectedPathsPresent.add(f.path);
					} else {
						expectedPathsNotInGit.add(f.path);
					}
				}
			}
			checkGit("Test stage " + testStepN + ": ", 0, stage.username, stage.useremail, stage.comment, expectedPathsPresent, expectedPathsNotInGit);
		}
	}

	protected void mkFile(final String path, final List<String> lines) throws IOException {
		File f = pathToFile(path);
		if ( path.endsWith("/")) {
			System.out.println("UnitTest: mkdir " + path);
			f.mkdir();
		} else {
			System.out.println("UnitTest: writing to " + path);
			Files.writeLines(f, lines, cs, false);
		}
	}

	protected File pathToFile(final String path) {
		File f = new File(basedir, path);
		File parentDir = f.getParentFile();
		if ( !parentDir.exists() ) {
			parentDir.mkdirs();
		}
		return f;
	}

	/*
	 * Git mandates restrictions about reference names, which limits tag names.
	 */
	protected static void assertIsValidTag(String gitTag) {
		final String gitRef = "refs/tags/"+gitTag;
		/*
		 * 1. They can include slash / for hierarchical (directory) grouping, but no
		 *    slash-separated component can begin with a dot . or end with the sequence .lock
		 */
		final String r1 = "reference can include slash / for hierarchical (directory) grouping, but no slash-separated component can begin with a dot . or end with the sequence .lock";
		// 1a. must not start with a dot
		assertThat(r1, gitRef, not(startsWith(".")));
		// 1b. must not contain /.
		assertThat(r1, gitRef, not(containsString("/.")));
		// 1c. must not end .lock
		assertThat(r1, gitRef, not(endsWith(".lock")));
		// 1d. must not contain .lock/
		assertThat(r1, gitRef, not(containsString(".lock/")));
		/*
		 * 2. They must contain at least one /. This enforces the presence of a category
		 *    like heads/, tags/ etc. but the actual names are not restricted.
		 */
		// 2 is automatically met as tags are refs/tags/tagName
		/*
		 * 3. They cannot have two consecutive dots .. anywhere
		 */
		final String r3 = "reference cannot have two consecutive dots .. anywhere";
		assertThat(r3, gitRef, not(containsString("..")));
		/*
		 * 4. They cannot have ASCII control characters (i.e. bytes whose values are lower
		 *    than \040, or \177 DEL), space, tilde ~, caret ^, or colon : anywhere
		 */
		final String r4 = "reference cannot have ASCII control characters (i.e. bytes whose values are lower than \\040, or \\177 DEL), space, tilde ~, caret ^, or colon : anywhere";
		// 4a. no bytes whose values are lower than \\040
		for ( int charValue = 0 ; charValue < 32 ; charValue++ ) {
			final char c = (char)charValue;
			assertThat(r4, gitRef, not(containsString(""+c)));
		}
		// 4b. no \\177 DEL
		assertThat(r4, gitRef, not(containsString(""+((char)127))));
		// 4c. no spaces
		assertThat(r4, gitRef, not(containsString(" ")));
		// 4d. no tilde
		assertThat(r4, gitRef, not(containsString("~")));
		// 4e. no caret
		assertThat(r4, gitRef, not(containsString("^")));
		// 4f. no colon
		assertThat(r4, gitRef, not(containsString(":")));
		/*
		 * 5. They cannot have question-mark ?, asterisk *, or open bracket [ anywhere.
		 */
		final String r5 = "reference cannot have question-mark ?, asterisk *, or open bracket [ anywhere.";
		// 5a. no question-mark
		assertThat(r5, gitRef, not(containsString("?")));
		// 5b. no asterisk
		assertThat(r5, gitRef, not(containsString("*")));
		// 5c. no open bracket
		assertThat(r5, gitRef, not(containsString("[")));
		/*
		 * 6. They cannot begin or end with a slash / or contain multiple consecutive slashes
		 *    (see the --normalize option below for an exception to this rule)
		 */
		final String r6 = "reference cannot begin or end with a slash / or contain multiple consecutive slashes (see the --normalize option below for an exception to this rule)";
		// 6a. must not end with /
		assertThat(r6, gitRef, not(endsWith("/")));
		// 6b. must not contain //
		assertThat(r6, gitRef, not(containsString("//")));
		/*
		 * 7. They cannot end with a dot .
		 */
		final String r7 = "reference cannot end with a dot .";
		assertThat(r7, gitRef, not(endsWith(".")));
		/*
		 * 8. They cannot contain a sequence @{
		 */
		final String r8 = "reference cannot contain a sequence @{";
		assertThat(r8, gitRef, not(containsString("@{")));
		/*
		 * 9. They cannot be the single character @
		 */
		// 9 is automatically met as tags are refs/tags/tagName
		/*
		 * 10. They cannot contain a \
		 */
		final String r10 = "reference cannot contain a \\";
		assertThat(r10, gitRef, not(containsString("\\")));
	}

	//
	// helper stuff
	//

	protected void create(File file) throws Exception {
		file.getParentFile().mkdirs();
		file.createNewFile();
	}

	protected void checkExactLines(File fileName, List<String> expected) throws Exception {
		final String baseDirName = basedir.getPath();
		final String filePath = fileName.getPath();
		final String checkText;
		if ( filePath.startsWith(baseDirName)) {
			checkText = "Contents of file " + filePath.substring(baseDirName.length());
		} else {
			checkText = "Contents of file " + filePath;
		}
		final List<String> actual = Files.readLines(fileName, cs);
		assertEquals(checkText, expected, actual);
	}

	protected void checkExactLinesIgnoringComments(File fileName, List<String> expected) throws Exception {
		checkExactLinesIgnoringComments("", fileName, expected, "#");
	}

	protected void checkExactLinesIgnoringComments(String testPrefixText, File fileName, List<String> expected) throws Exception {
		checkExactLinesIgnoringComments(testPrefixText, fileName, expected, "#");
	}

	protected void checkExactLinesIgnoringComments(String testPrefixText, File fileName, List<String> expected, String prefixIndicatingComment) throws Exception {
		final String relativeFilePath = Files.relativePath(basedir, fileName);
		String checkText = testPrefixText + "Contents of file " + relativeFilePath;
		final List<String> actual = Files.readLines(fileName, cs);
		final List<String> expectedWithoutComments = withoutComments(prefixIndicatingComment, expected);
		if ( expectedWithoutComments.size()!=expected.size() ) {
			// Our expected text contains comments, so we must not ignore comments when comparing
			checkText += " is exactly ";
			assertEquals(checkText, expected, actual);
		} else {
			// Our expected text contains no comments so we ignore them in the actual text
			checkText += " is " + actual + ", but ignoring comment lines we have";
			final List<String> actualWithoutComments = withoutComments(prefixIndicatingComment, actual);
			assertEquals(checkText, expected, actualWithoutComments);
		}
	}

	protected static List<String> withoutComments(String prefixIndicatingComment, final List<String> original) {
		final List<String> actualWithoutComments = new ArrayList<String>(original.size());
		for( final String actualLine : original ) {
			if( !actualLine.startsWith(prefixIndicatingComment)) {
				actualWithoutComments.add(actualLine);
			}
		}
		return actualWithoutComments;
	}

	protected void checkGit(String userName, String userEmail, String comment) throws Exception {
		checkGit(0, userName, userEmail, comment);
	}

	protected void checkGit(int commitsToSkip, String userName, String userEmail, String comment) throws Exception {
		checkGit(commitsToSkip, userName, userEmail, comment, null);
	}

	protected void checkGit(int commitsToSkip, String userName, String userEmail, String comment, Iterable<String> pathsExpected) throws Exception {
		checkGit("", commitsToSkip, userName, userEmail, comment, pathsExpected, null);
	}

	protected void ensureGitIsOpen() throws IOException {
		if ( git==null ) {
			git = Git.open(basedir);
		}
	}

	protected void checkGit(String testPrefixText, int commitsToSkip, String userName, String userEmail, String comment, Iterable<String> pathsExpected, Iterable<String> pathsForbidden) throws Exception {
		ensureGitIsOpen();
		Status status = git.status().call();
		migrator.logGitStatus(status);
		if( Boolean.valueOf(props.getProperty("purge.gitignore.files.between.commits")) ) {
			// git status might not be clean - it might have deleted gitignore files but ONLY those.
			assertThat(testPrefixText + "Git status Added", status.getAdded(),equalTo(Collections.EMPTY_SET));
			assertThat(testPrefixText + "Git status Changed", status.getChanged(),equalTo(Collections.EMPTY_SET));
			assertThat(testPrefixText + "Git status IgnoredNotInIndex", status.getIgnoredNotInIndex(),equalTo(Collections.EMPTY_SET));
			assertThat(testPrefixText + "Git status Modified", status.getModified(),equalTo(Collections.EMPTY_SET));
			assertThat(testPrefixText + "Git status Removed", status.getRemoved(),equalTo(Collections.EMPTY_SET));
			assertThat(testPrefixText + "Git status Untracked", status.getUntracked(),equalTo(Collections.EMPTY_SET));
			if( Boolean.valueOf(props.getProperty("preserve.empty.folders")) ) {
				assertThat(testPrefixText + "Git status UntrackedFolders", status.getUntrackedFolders(),equalTo(Collections.EMPTY_SET));
			}
			final Set<String> uncommittedChanges = new TreeSet<String>(status.getUncommittedChanges());
			GitMigrator.removeAllGitIgnorePathsFromSet(uncommittedChanges);
			assertThat(testPrefixText + "Git status UncommittedChanges aside from gitignores", uncommittedChanges,equalTo(Collections.EMPTY_SET));
			final Set<String> missing = new TreeSet<String>(status.getMissing());
			GitMigrator.removeAllGitIgnorePathsFromSet(missing);
			assertThat(testPrefixText + "Git status Missing aside from gitignores", missing,equalTo(Collections.EMPTY_SET));
		} else {
			assertTrue(testPrefixText + "git status must be clean", status.isClean());
		}
		Iterator<RevCommit> log;
		try {
			log = git.log().call().iterator();
		} catch( NoHeadException ex ) {
			log = Collections.emptyListIterator();
		}
		for( int i=0 ; i<commitsToSkip ; i++ ) {
			log.next();
		}
		RevCommit revCommit = log.next();
		if ( userEmail!=null ) {
			assertEquals(userEmail, revCommit.getAuthorIdent().getEmailAddress());
		}
		if ( userName!=null ) {
			assertEquals(userName, revCommit.getAuthorIdent().getName());
		}
		if ( comment!=null ) {
			assertEquals(comment, revCommit.getFullMessage());
		}
		final String commitHash = revCommit.getName();
		final String commitName = (commitsToSkip==0?"latest":commitsToSkip+"-from-latest") + " commit (" + commitHash +")";
		if ( pathsExpected!=null ) {
			for( final String expectedPath : pathsExpected ) {
				final List<RevCommit> history = getGitFileHistory(expectedPath);
				assertTrue(testPrefixText + "File " + expectedPath + " is expected to have Git history, but does not", !history.isEmpty());
				boolean historyIncludesThisCommit = false;
				for( RevCommit pathCommit : history ) {
					historyIncludesThisCommit |= commitHash.equals(pathCommit.getName());
				}
				assertTrue(testPrefixText + "File " + expectedPath + " is expected to have Git history from " + commitName, historyIncludesThisCommit);
			}
		}
		if ( pathsForbidden!=null ) {
			for( final String forbiddenPath : pathsForbidden ) {
				final List<RevCommit> history = getGitFileHistory(forbiddenPath);
				boolean historyIncludesThisCommit = false;
				for( RevCommit pathCommit : history ) {
					historyIncludesThisCommit |= commitHash.equals(pathCommit.getName());
				}
				assertTrue(testPrefixText + "File " + forbiddenPath + " is expected to have NO Git history from " + commitName, !historyIncludesThisCommit);
			}
		}
	}

	protected List<RevCommit> getGitFileHistory(String filePath) throws Exception {
		ensureGitIsOpen();
		final Iterable<RevCommit> log = git.log().addPath(filePath).call();
		final List<RevCommit> result = new ArrayList<RevCommit>();
		for( RevCommit rc : log ) {
			result.add(rc);
		}
		return result;
	}

	protected enum TestChangeSet implements ChangeSet {
		INSTANCE, NO_WORKITEM_INSTANCE {
			@Override
			public List<WorkItem> getWorkItems() {
				return Collections.emptyList();
			}
		};

		@Override
		public String getServerURI() {
			return "https://someserver.domain.com/ccm/";
		}

		@Override
		public String getStreamID() {
			return "someStreamUUID";
		}

		@Override
		public String getChangesetID() {
			return "someChangesetUUID";
		}

		@Override
		public String getComment() {
			return "the checkin comment";
		}

		@Override
		public String getCreatorName() {
			return "Heiri Mueller";
		}

		@Override
		public String getEmailAddress() {
			return "heiri.mueller@irgendwo.ch";
		}

		@Override
		public long getCreationDate() {
			return 0;
		}

		@Override
		public List<WorkItem> getWorkItems() {
			List<WorkItem> items = new ArrayList<WorkItem>();
			items.add(TestWorkItem.INSTANCE1);
			return items;
		}

		@Override
		public String getAddedToStreamName() {
			return "Heiri Mueller";
		}

		@Override
		public String getAddedToStreamEmailAddress() {
			return "heiri.mueller@irgendwo.ch";
		}

		@Override
		public long getAddedToStreamDate() {
			return 0;
		}
	}

	protected enum TestWorkItem implements WorkItem {
		INSTANCE1 {
			@Override
			public long getNumber() {
				return 4711;
			}

			@Override
			public String getText() {
				return "The one and only";
			}
		},
		INSTANCE2 {
			@Override
			public long getNumber() {
				return 4712;
			}

			@Override
			public String getText() {
				return "The even more and only";
			}
		};
	}

	protected enum TestTag implements Tag {
		INSTANCE;

		@Override
		public String getName() {
			return "myTag";
		}

		@Override
		public long getCreationDate() {
			return 0;
		}
	}
}
