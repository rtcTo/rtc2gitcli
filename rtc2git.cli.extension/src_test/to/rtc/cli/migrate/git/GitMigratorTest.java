package to.rtc.cli.migrate.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import to.rtc.cli.migrate.ChangeSet;
import to.rtc.cli.migrate.ChangeSet.WorkItem;
import to.rtc.cli.migrate.util.Files;

/**
 * Tests the {@link GitMigrator} implementation.
 *
 * @author patrick.reinhart
 */
public class GitMigratorTest {
	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private GitMigrator migrator;
	private Git git;
	private Properties props;

	@Before
	public void setUo() {
		migrator = new GitMigrator();
		props = new Properties();
	}

	@After
	public void tearDown() {
		migrator.close();
		if (git != null) {
			git.close();
		}
	}

	@Test
	public void testClose() {
		migrator.close();
	}

	@Test
	public void testInit_noGitRepoAvailableNoGitIgnore() throws Exception {
		props.setProperty("user.email", "john.doe@somewhere.com");
		props.setProperty("user.name", "John Doe");

		migrator.init(tempFolder.getRoot(), props);

		checkGit("John Doe", "john.doe@somewhere.com", "Initial commit",
				new File(tempFolder.getRoot(), ".gitignore"),
				GitMigrator.ROOT_IGNORED_ENTRIES);
	}

	@Test
	public void testInit_noGitRepoAvailableWithGitIgnore() throws Exception {
		Files.writeLines(new File(tempFolder.getRoot(), ".gitignore"),
				Arrays.asList("/.jazz5", "/bin/", "/.jazzShed"),
				Charset.forName("UTF-8"), false);

		migrator.init(tempFolder.getRoot(), props);

		checkGit("RTC 2 git", "rtc2git@rtc.to", "Initial commit", new File(
				tempFolder.getRoot(), ".gitignore"), Arrays.asList("/.jazz5",
				"/bin/", "/.jazzShed", "/.metadata"));
	}

	@Test
	public void testInit_GitRepoAvailable() throws Exception {
		git = Git.init().setDirectory(tempFolder.getRoot()).call();

		migrator.init(tempFolder.getRoot(), props);

		checkGit("RTC 2 git", "rtc2git@rtc.to", "Initial commit", new File(
				tempFolder.getRoot(), ".gitignore"),
				GitMigrator.ROOT_IGNORED_ENTRIES);
	}

	@Test
	public void testGetCommitMessage() {
		migrator.init(tempFolder.getRoot(), props);

		assertEquals("gugus gaga", migrator.getCommitMessage("gugus", "gaga"));
	}

	@Test
	public void testGetCommitMessage_withCustomFormat() {
		props.setProperty("commit.message.format", "%1s%n%n%2s");
		migrator.init(tempFolder.getRoot(), props);
		String lf = System.getProperty("line.separator");

		assertEquals("gug%us" + lf + lf + "ga%ga",
				migrator.getCommitMessage("gug%us", "ga%ga"));
	}

	@Test
	public void testGetWorkItemNumbers_noWorkItems() {
		assertEquals("",
				migrator.getWorkItemNumbers(Collections.<WorkItem> emptyList()));
	}

	@Test
	public void testGetWorkItemNumbers_singleWorkItem() {
		migrator.init(tempFolder.getRoot(), props);
		List<WorkItem> items = new ArrayList<WorkItem>();
		items.add(TestWorkItem.INSTANCE1);
		assertEquals("4711", migrator.getWorkItemNumbers(items));
	}

	@Test
	public void testGetWorkItemNumbers_singleWorkItem_customFormat() {
		props.setProperty("rtc.workitem.number.format", "RTC-%s");
		migrator.init(tempFolder.getRoot(), props);

		List<WorkItem> items = new ArrayList<WorkItem>();
		items.add(TestWorkItem.INSTANCE1);
		assertEquals("RTC-4711", migrator.getWorkItemNumbers(items));
	}

	@Test
	public void testGetWorkItemNumbers_multipleWorkItems() {
		props.setProperty("rtc.workitem.number.format", "RTC-%s");
		migrator.init(tempFolder.getRoot(), props);

		List<WorkItem> items = new ArrayList<WorkItem>();
		items.add(TestWorkItem.INSTANCE1);
		items.add(TestWorkItem.INSTANCE2);
		assertEquals("RTC-4711 RTC-4712", migrator.getWorkItemNumbers(items));
	}

	@Test
	public void testCommitChanges() throws Exception {
		migrator.init(tempFolder.getRoot(), props);

		File testFile = new File(tempFolder.getRoot(), "somefile");
		Files.writeLines(testFile, Collections.singletonList("somevalue"),
				Charset.forName("UTF-8"), false);

		migrator.commitChanges(TestChangeSet.INSTANCE);

		checkGit("Heiri Mueller", "heiri.mueller@irgendwo.ch",
				"4711 the checkin comment", testFile,
				Collections.singletonList("somevalue"));
	}

	@Test
	public void testCommitChanges_noWorkItem() throws Exception {
		migrator.init(tempFolder.getRoot(), props);

		File testFile = new File(tempFolder.getRoot(), "somefile");
		Files.writeLines(testFile, Collections.singletonList("somevalue"),
				Charset.forName("UTF-8"), false);

		migrator.commitChanges(TestChangeSet.NO_WORKITEM_INSTANCE);

		checkGit("Heiri Mueller", "heiri.mueller@irgendwo.ch",
				"the checkin comment", testFile,
				Collections.singletonList("somevalue"));
	}

	private void checkGit(String userName, String userEmail, String comment,
			File checkedFile, List<String> checkedContent) throws Exception {
		assertEquals(checkedContent,
				Files.readLines(checkedFile, Charset.forName("UTF-8")));
		git = Git.open(tempFolder.getRoot());
		Status status = git.status().call();
		assertTrue(status.getUncommittedChanges().isEmpty());
		assertTrue(status.getUntracked().isEmpty());
		Iterator<RevCommit> log = git.log().call().iterator();
		RevCommit revCommit = log.next();
		assertEquals(userEmail, revCommit.getAuthorIdent().getEmailAddress());
		assertEquals(userName, revCommit.getAuthorIdent().getName());
		assertEquals(comment, revCommit.getFullMessage());
	}

	private enum TestChangeSet implements ChangeSet {
		INSTANCE, NO_WORKITEM_INSTANCE {
			@Override
			public List<WorkItem> getWorkItems() {
				return Collections.emptyList();
			}
		};

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
	}

	private enum TestWorkItem implements WorkItem {
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
}