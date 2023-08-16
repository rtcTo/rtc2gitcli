package to.rtc.cli.migrate.git;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.junit.Test;

import to.rtc.cli.migrate.ChangeSet.WorkItem;
import to.rtc.cli.migrate.util.Files;

/**
 * Tests the {@link GitMigrator} implementation.
 *
 * @author patrick.reinhart
 */
public class GitMigratorTest extends GitMigratorTestBase {

	@Test
	public void testInit_noGitRepoAvailableNoGitIgnore() throws Exception {
		props.setProperty("user.email", "john.doe@somewhere.com");
		props.setProperty("user.name", "John Doe");
		props.setProperty("git.allow.empty.commits", "true");

		assertFalse(new File(basedir, ".gitattributes").exists());

		migrator.initialize(props);
		migrator.init(basedir, false);

		checkGit("John Doe", "john.doe@somewhere.com", "Initial commit");
		assertFalse(new File(basedir, ".gitignore").exists());
		assertFalse(new File(basedir, ".gitattributes").exists());
		checkExactLines(new File(basedir, ".git/info/exclude"), GitMigrator.ROOT_ALWAYS_IGNORED_ENTRIES);
	}

	@Test
	public void testInit_noGitRepoAvailableWithGitIgnore() throws Exception {
		Files.writeLines(new File(basedir, ".gitignore"), Arrays.asList("willBeOverwritten", "andIgnored"), cs, false);
		props.setProperty("global.gitignore.entries", "/projectX/WebContent/node_modules; *.ignored");

		migrator.initialize(props);
		migrator.init(basedir, false);

		checkGit(GitMigrator.DEFAULT_INITIAL_COMMIT_USERNAME, GitMigrator.DEFAULT_INITIAL_COMMIT_USEREMAIL, "Initial commit");
		checkExactLinesIgnoringComments(new File(basedir, ".gitignore"), Arrays.asList(
				"/projectX/WebContent/node_modules", "*.ignored"));
		checkExactLines(new File(basedir, ".git/info/exclude"), GitMigrator.ROOT_ALWAYS_IGNORED_ENTRIES);
	}

	@Test
	public void testInit_Gitattributes() throws IOException {
		props.setProperty("gitattributes", "* text=auto");

		migrator.init(basedir, false);

		File gitattributes = new File(basedir, ".gitattributes");
		assertTrue(gitattributes.exists() && gitattributes.isFile());
		List<String> expectedLines = new ArrayList<String>();
		expectedLines.add("* text=auto");
		assertEquals(expectedLines, Files.readLines(gitattributes, cs));
	}

	@Test
	public void testInit_GitRepoAvailable() throws Exception {
		props.setProperty("git.allow.empty.commits", "true");
		migrator.initialize(props);
		git = Git.init().setDirectory(basedir).call();

		migrator.init(basedir, false);

		checkGit(GitMigrator.DEFAULT_INITIAL_COMMIT_USERNAME, GitMigrator.DEFAULT_INITIAL_COMMIT_USEREMAIL, "Initial commit");
		assertEquals(GitMigrator.ROOT_ALWAYS_IGNORED_ENTRIES, Files.readLines(new File(basedir, ".git/info/exclude"), cs));
	}

	@Test
	public void testInit_GitConfig() throws Exception {
		git = Git.init().setDirectory(basedir).call();
		StoredConfig config = git.getRepository().getConfig();

		migrator.init(basedir, false);

		config.load();
		assertFalse(config.getBoolean("core", null, "ignorecase", true));
		assertEquals(File.separatorChar == '/' ? "input" : "true", config.getString("core", null, "autocrlf"));
		assertEquals("simple", config.getString("push", null, "default"));
		assertFalse(config.getBoolean("http", null, "sslverify", true));
	}

	@Test
	public void testGetCommitMessage() {
		props.setProperty("commit.message.regex.1", "^B([0-9]+): (.+)$");
		props.setProperty("commit.message.replacement.1", "BUG-$1 $2");
		migrator.initialize(props);

		assertEquals("gugus BUG-1234 gaga", migrator.getCommitMessage("gugus", "B1234: gaga", "", "uri", "streamId", "changeid"));
	}

	@Test
	public void testGetCommitMessage_withCustomFormat() {
		props.setProperty("commit.message.format", "%1s%n%n%2s");
		migrator.init(basedir, false);
		String lf = System.getProperty("line.separator");

		assertEquals("gug%us" + lf + lf + "ga%ga", migrator.getCommitMessage("gug%us", "ga%ga", "", "uri", "streamId", "changeid"));
	}

	@Test
	public void testGetCommitMessage_withCustomFormat1() {
		props.setProperty("commit.message.format", "%1s%n%n%2s%n%n%3s");
		migrator.init(basedir, false);
		String lf = System.getProperty("line.separator");

		assertEquals("gug%us" + lf + lf + "ga%ga" + lf + lf + "gi%gl",
				migrator.getCommitMessage("gug%us", "ga%ga", "gi%gl", "uri", "streamId", "changeid"));
	}

	@Test
	public void testGetWorkItemNumbers_noWorkItems() {
		assertEquals("", migrator.getWorkItemNumbers("MyRtcUrl", Collections.<WorkItem> emptyList()));
	}

	@Test
	public void testGetWorkItemNumbers_singleWorkItem() {
		migrator.init(basedir, false);
		List<WorkItem> items = new ArrayList<WorkItem>();
		items.add(TestWorkItem.INSTANCE1);
		assertEquals("4711", migrator.getWorkItemNumbers("MyRtcUrl", items));
	}

	@Test
	public void testGetWorkItemNumbers_singleWorkItem_customFormat() {
		props.setProperty("rtc.workitem.number.format", "RTC-%s");
		migrator.init(basedir, false);

		List<WorkItem> items = new ArrayList<WorkItem>();
		items.add(TestWorkItem.INSTANCE1);
		assertEquals("RTC-4711", migrator.getWorkItemNumbers("MyRtcUrl", items));
	}

	@Test
	public void testGetWorkItemNumbers_multipleWorkItems() {
		props.setProperty("rtc.workitem.number.format", "RTC-%s");
		migrator.init(basedir, false);

		List<WorkItem> items = new ArrayList<WorkItem>();
		items.add(TestWorkItem.INSTANCE1);
		items.add(TestWorkItem.INSTANCE2);
		assertEquals("RTC-4711 RTC-4712", migrator.getWorkItemNumbers("MyRtcUrl", items));
	}

	@Test
	public void testGetWorkItemNumbers_multipleWorkItemsWithPrefixAndSuffix() {
		props.setProperty("rtc.workitem.number.prefix", "[RTC-");
		props.setProperty("rtc.workitem.number.format", "%1$s");
		props.setProperty("rtc.workitem.number.delimiter", ",");
		props.setProperty("rtc.workitem.number.suffix", "]: ");
		migrator.init(basedir, false);

		List<WorkItem> items = new ArrayList<WorkItem>();
		items.add(TestWorkItem.INSTANCE1);
		items.add(TestWorkItem.INSTANCE2);
		assertEquals("[RTC-4711,4712]: ", migrator.getWorkItemNumbers("MyRtcUrl", items));
	}

	@Test
	public void testGetWorkItemTexts_noWorkItems() {
		props.setProperty("rtc.workitem.text.prefix", "foo");
		migrator.init(basedir, false);
		final String expected = "";

		List<WorkItem> items = new ArrayList<WorkItem>();
		final String actual = migrator.getWorkItemTexts("https://myRtcServer/ccm/", items);
		assertEquals(expected, actual);
	}

	@Test
	public void testGetWorkItemTexts_workItemWithPrefixAndSuffix() {
		props.setProperty("rtc.workitem.text.prefix", "%n%n");
		props.setProperty("rtc.workitem.text.format", "%3$sweb#action=com.ibm.team.workitem.viewWorkItem&id=%1$s%n%2$s");
		props.setProperty("rtc.workitem.text.delimiter", "%n%n");
		props.setProperty("rtc.workitem.text.suffix", "");
		migrator.init(basedir, false);
		final String eol = System.getProperty("line.separator");
		final String expected = eol + eol + "https://myRtcServer/ccm/web#action=com.ibm.team.workitem.viewWorkItem&id=4711"+eol+"The one and only";

		List<WorkItem> items = new ArrayList<WorkItem>();
		items.add(TestWorkItem.INSTANCE1);
		final String actual = migrator.getWorkItemTexts("https://myRtcServer/ccm/", items);
		assertEquals(expected, actual);
	}

	@Test
	public void testGetWorkItemTexts_workItems() {
		migrator.init(basedir, false);
		final String eol = System.getProperty("line.separator");
		final String expected = TestWorkItem.INSTANCE1.getNumber() + " " + TestWorkItem.INSTANCE1.getText() + eol
				+ TestWorkItem.INSTANCE2.getNumber() + " " + TestWorkItem.INSTANCE2.getText();

		List<WorkItem> items = new ArrayList<WorkItem>();
		items.add(TestWorkItem.INSTANCE1);
		items.add(TestWorkItem.INSTANCE2);
		final String actual = migrator.getWorkItemTexts("https://myRtcServer/ccm/", items);
		assertEquals(expected, actual);
	}

	@Test
	public void testCommitChanges() throws Exception {
		migrator.init(basedir, false);

		File testFile = new File(basedir, "somefile");
		Files.writeLines(testFile, Collections.singletonList("somevalue"), cs, false);

		migrator.commitChanges(TestChangeSet.INSTANCE);

		checkGit(TestChangeSet.INSTANCE.getCreatorName(), TestChangeSet.INSTANCE.getEmailAddress(), TestWorkItem.INSTANCE1.getNumber()+" "+TestChangeSet.INSTANCE.getComment());
		checkExactLines(testFile, Collections.singletonList("somevalue"));
	}

	@Test
	public void testCommitChanges_noWorkItem() throws Exception {
		migrator.init(basedir, false);

		File testFile = new File(basedir, "somefile");
		Files.writeLines(testFile, Collections.singletonList("somevalue"), cs, false);

		migrator.commitChanges(TestChangeSet.NO_WORKITEM_INSTANCE);

		checkGit(TestChangeSet.NO_WORKITEM_INSTANCE.getCreatorName(), TestChangeSet.NO_WORKITEM_INSTANCE.getEmailAddress(), TestChangeSet.NO_WORKITEM_INSTANCE.getComment());
		checkExactLines(testFile, Collections.singletonList("somevalue"));
	}

	@Test
	public void testGetGitattributeLines() throws Exception {
		props.setProperty("gitattributes", " # handle text files; * text=auto; *.sql text");
		migrator.initialize(props);

		List<String> lines = migrator.getGitattributeLines();
		assertNotNull(lines);
		assertEquals(3, lines.size());
		assertEquals("# handle text files", lines.get(0));
		assertEquals("* text=auto", lines.get(1));
		assertEquals("*.sql text", lines.get(2));
	}

	@Test
	public void testGetIgnoredFileExtensions() throws Exception {
		props.setProperty("ignore.file.extensions", ".zip; .jar; .exe; .dll");
		migrator.initialize(props);

		Set<String> ignoredExtensions = migrator.getIgnoredFileExtensions();
		HashSet<String> expected = new HashSet<String>(Arrays.asList(".zip", ".jar", ".exe", ".dll"));

		assertEquals(expected, ignoredExtensions);
	}

	@Test
	public void testAddMissing() {
		List<String> existing = new ArrayList<String>();
		existing.add("0");
		existing.add("3");
		List<String> adding = new ArrayList<String>();
		adding.add("0");
		adding.add("1");
		adding.add("2");
		adding.add("4");
		List<String> expectedLines = new ArrayList<String>();
		expectedLines.add("0");
		expectedLines.add("3");
		expectedLines.add("1");
		expectedLines.add("2");
		expectedLines.add("4");
		migrator.addMissing(existing, adding);
		assertEquals(expectedLines, existing);
	}

	@Test
	public void testAddUpdateGitignoreIfJazzignoreAddedOrChanged() throws Exception {
		testScenario(true, "", "", ts(
				tsCs1(tsMkCommittedFile(".jazzignore", "core.ignore.recursive = {*.class}"),
						tsAssertFileContent(".gitignore", "*.class", "!/.gitignore", "!/.jazzignore")),
				tsCs1(tsMkCommittedFile(".jazzignore", "core.ignore = {*.suo}"),
						tsAssertFileContent(".gitignore", "/*.suo", "!/.gitignore", "!/.jazzignore")),
				tsCs1(tsMkCommittedFile(".jazzignore", "core.ignore = {*.suo}", "core.ignore.recursive = {*.class}"),
						tsAssertFileContent(".gitignore", "/*.suo", "*.class", "!/.gitignore", "!/.jazzignore")),
				tsCs1(tsMkCommittedFile(".jazzignore", "core.ignore = {*.foo} {*.bar}", "core.ignore.recursive = {*.class}"),
						tsAssertFileContent(".gitignore", "/*.foo", "/*.bar", "*.class", "!/.gitignore", "!/.jazzignore"))
						));
	}

	@Test
	public void testRootGitIgnoreIsAbsentIfNotNeeded() throws Exception {
		testScenario(true, "", "", ts(
				tsInit(tsAssertFileAbsentAndNotInGit(".gitignore"))));
	}

	@Test
	public void testRootGitIgnoreIsIgnoredIfWeSaySo() throws Exception {
		testScenario(false, "", "", ts(
				tsCs1(tsMkCommittedFile(".jazzignore", "core.ignore = {*.foo}\ncore.ignore.recursive = {*.bar}"),
						tsAssertFileContent(".gitignore", "/*.foo", "*.bar", "!/.gitignore", "!/.jazzignore"),
						tsMkCommittedFile("someFile"), tsMkCommittedFile("someFolder/anotherFile"),
						tsMkCommittedFile("jazzignored.foo"), tsMkCommittedFile("someFolder/jazzignored.bar"))));
	}

	@Test
	public void testRootGitIgnoreIsDeletedOnceNotNeeded() throws Exception {
		testScenario(true, "", "", ts(
				tsInit(tsAssertFileAbsentAndNotInGit(".gitignore")),
				tsCs1(tsMkCommittedFile(".jazzignore", "core.ignore.recursive = {*.bar}"),
						tsAssertFileContent(".gitignore", "*.bar", "!/.gitignore", "!/.jazzignore")),
				tsCs1(tsDeleteFile(".jazzignore"),
						tsAssertFileDeletedFromGit(".gitignore"))));
	}

	@Test
	public void testSubfolderGitIgnoreDoesNotIncludeRootEntriesButStillIgnoresWhatItSaysItShould() throws Exception {
		testScenario(true, "", ".zip", ts(
				tsCs1(tsMkCommittedFile("subdir/.jazzignore", "core.ignore = {*.bar}"),
						tsMkCommittedFile("subdir/shouldBeCommitted.txt"),
						tsMkIgnoredFile("subdir/shouldBeIgnoredByGlobalConfigRule.zip"),
						tsMkIgnoredFile("subdir/shouldBeIgnored.bar"),
						tsAssertFileContent("subdir/.gitignore", "/*.bar", "!/.gitignore", "!/.jazzignore"))));
	}

	@Test
	public void testSubfolderGitIgnoreDoesNotIncludeRootEntriesButIsIgnoredIfWeDontTrustIt() throws Exception {
		testScenario(false, "", ".zip", ts(
				tsCs1(tsMkCommittedFile("subdir/.jazzignore", "core.ignore = {*.bar}"),
						tsMkCommittedFile("subdir/shouldBeCommitted.txt"),
						tsMkIgnoredFile("subdir/shouldBeIgnoredByGlobalConfigRule.zip"),
						tsMkCommittedFile("subdir/wouldBeIgnoredIfWeTrustedJazzignoresButWeDont.bar"),
						tsAssertFileContent("subdir/.gitignore", "/*.bar", "!/.gitignore", "!/.jazzignore"))));
	}

	@Test
	public void testJazzIgnoreCanIgnoreManyThings() throws Exception {
		testScenario(true, "", "",
				ts(tsCs1(
						tsMkCommittedFile(".jazzignore", "core.ignore = {*.foo}\ncore.ignore.recursive = {bin} {ignored} {*.txt}"),
						tsMkIgnoredFile("shouldBeIgnoredByRecursiveTxtRule.txt"),
						tsMkIgnoredFile("bin/shouldBeIgnoredByRecursiveBinRule.text"),
						tsMkIgnoredFile("notignored/bin/shouldBeIgnoredByRecursiveBinRule.text"),
						tsMkIgnoredFile("notignored/ignored/shouldBeIgnoredByRecursiveIgnoreRule.text"),
						tsAssertFileContent(".gitignore", "/*.foo", "bin", "ignored", "*.txt", "!/.gitignore", "!/.jazzignore"))));
	}

	@Test
	public void testGitIgnoreIsIgnoredIfWeDontTrustItEvenInDeeplyNestedFolder() throws Exception {
		testScenario(false, "", "",
				ts(tsCs1(
						tsMkCommittedFile(".jazzignore", "core.ignore = {*.foo}\ncore.ignore.recursive = {bin} {ignored} {*.txt}"),
						tsAssertFileContent(".gitignore", "/*.foo", "bin", "ignored", "*.txt", "!/.gitignore", "!/.jazzignore")),
					tsCs1(tsMkCommittedFile("bin/foo/ignored/wouldBeIgnoredByRecursiveRuleDueToPathAndName.txt"),
						tsMkCommittedFile("wouldBeIgnoredByRecursiveRuleDueToFileExtenstion.txt"),
						tsAssertFileContentNotInGit(".gitignore", "/*.foo", "bin", "ignored", "*.txt", "!/.gitignore", "!/.jazzignore"))));
	}

	@Test
	public void testGitIgnoreIsIgnoredIfWeDontTrustItEvenWhenIgnoredForMultipleReasons() throws Exception {
		// This is a real-world failure scenario
		// .jazzignore set to ignore loads of things recursively, including bin & Release
		// a new file dev/utilities/TranslationFileGenerator/bin/Release/TranslationFileGenerator.vshost.exe was added
		// git ignored it despite code that should've fixed that.
		final List<String> ji = new ArrayList<String>();
		ji.add("### Jazz Ignore 0");
		ji.add("# Ignored files and folders will not be committed, but may be modified during ");
		ji.add("# accept or update.  ");
		ji.add("# - Ignore properties should contain a space separated list of filename patterns.  ");
		ji.add("# - Each pattern is case sensitive and surrounded by braces ('{' and '}').  ");
		ji.add("# - \"*\" matches zero or more characters.  ");
		ji.add("# - \"?\" matches a single character.  ");
		ji.add("# - The pattern list may be split across lines by ending the line with a ");
		ji.add("#     backslash and starting the next line with a tab.  ");
		ji.add("# - Patterns in core.ignore prevent matching resources in the same ");
		ji.add("#     directory from being committed.  ");
		ji.add("# - Patterns in core.ignore.recursive matching resources in the current ");
		ji.add("#     directory and all subdirectories from being committed.  ");
		ji.add("# - The default value of core.ignore.recursive is *.class ");
		ji.add("# - The default value for core.ignore is bin ");
		ji.add("# ");
		ji.add("# To ignore shell scripts and hidden files in this subtree: ");
		ji.add("#     e.g: core.ignore.recursive = {*.sh} {\\.*} ");
		ji.add("# ");
		ji.add("# To ignore resources named 'bin' in the current directory (but allow ");
		ji.add("#  them in any sub directorybelow): ");
		ji.add("#     e.g: core.ignore.recursive = {*.sh} {\\.*} ");
		ji.add("# ");
		ji.add("# NOTE: modifying ignore files will not change the ignore status of ");
		ji.add("#     Eclipse derived resources.");
		ji.add("");
		ji.add("core.ignore.recursive= \\");
		ji.add(" {*.ncb} \\");
		ji.add(" {*.suo} \\");
		ji.add(" {*.sdf} \\");
		ji.add(" {*.pdb} \\");
		ji.add(" {*.tlh} \\");
		ji.add(" {*.tlb} \\");
		ji.add(" {*_i.c} \\");
		ji.add(" {*_p.c} \\");
		ji.add(" {*_h.h} \\");
		ji.add(" {*.i} \\");
		ji.add(" {*.pch} \\");
		ji.add(" {*.tli} \\");
		ji.add(" {*.htm} \\");
		ji.add(" {*.idb} \\");
		ji.add(" {*.dll} \\");
		ji.add(" {*.cache} \\");
		ji.add(" {*.user} \\");
		ji.add("    {*.chm} \\");
		ji.add(" {*.embed.manifest} \\");
		ji.add(" {*.intermediate.manifest} \\");
		ji.add(" {*.embed.manifest.res} \\");
		ji.add(" {*.res} \\");
		ji.add(" {*.dep } \\");
		ji.add(" {*.opensdf } \\");
		ji.add(" {Debug} \\");
		ji.add(" {Release} \\");
		ji.add(" {ReadOnlyDebug} \\");
		ji.add(" {ReadOnlyRelease} \\");
		ji.add(" {obj} \\");
		ji.add(" {bin} \\");
		ji.add(" {*FileListAbsolute.txt} \\");
		ji.add(" {libraries} \\");
		ji.add(" {tests} \\");
		ji.add(" {ipch} \\");
		ji.add(" {My Project} \\");
		ji.add("        {*.ipch}");
		ji.add("core.ignore= ");
		final List<String> gi = new ArrayList<String>();
		gi.add("# Generated from .jazzignore file");
		gi.add("#   ");
		gi.add("#   core.ignore.recursive= \\{*.ncb} \\{*.suo} \\{*.sdf} \\{*.pdb} \\{*.tlh} \\{*.tlb} \\{*_i.c} \\{*_p.c} \\{*_h.h} \\{*.i} \\{*.pch} \\{*.tli} \\{*.htm} \\{*.idb} \\{*.dll} \\{*.cache} \\{*.user} \\{*.chm} \\{*.embed.manifest} \\{*.intermediate.manifest} \\{*.embed.manifest.res} \\{*.res} \\{*.dep } \\{*.opensdf } \\{Debug} \\{Release} \\{ReadOnlyDebug} \\{ReadOnlyRelease} \\{obj} \\{bin} \\{*FileListAbsolute.txt} \\{libraries} \\{tests} \\{ipch} \\{My Project} \\{*.ipch}");
		gi.add("*.ncb");
		gi.add("*.suo");
		gi.add("*.sdf");
		gi.add("*.pdb");
		gi.add("*.tlh");
		gi.add("*.tlb");
		gi.add("*_i.c");
		gi.add("*_p.c");
		gi.add("*_h.h");
		gi.add("*.i");
		gi.add("*.pch");
		gi.add("*.tli");
		gi.add("*.htm");
		gi.add("*.idb");
		gi.add("*.dll");
		gi.add("*.cache");
		gi.add("*.user");
		gi.add("*.chm");
		gi.add("*.embed.manifest");
		gi.add("*.intermediate.manifest");
		gi.add("*.embed.manifest.res");
		gi.add("*.res");
		gi.add("*.dep ");
		gi.add("*.opensdf ");
		gi.add("Debug");
		gi.add("Release");
		gi.add("ReadOnlyDebug");
		gi.add("ReadOnlyRelease");
		gi.add("obj");
		gi.add("bin");
		gi.add("*FileListAbsolute.txt");
		gi.add("libraries");
		gi.add("tests");
		gi.add("ipch");
		gi.add("My Project");
		gi.add("*.ipch");
		gi.add("#   core.ignore=");
		gi.add("!/.gitignore");
		gi.add("!/.jazzignore");
		testScenario(false, "", "", ts(
				tsCs1(tsMkCommittedFile("dev/.jazzignore", ji.toArray(new String[0])),
					  tsAssertFileContent("dev/.gitignore", gi.toArray(new String[0]))),
				tsCs1(tsMkCommittedFile("dev/utilities/TranslationFileGenerator/bin/Release/TranslationFileGenerator.exe","OriginalContent"),
					  tsMkCommittedFile("dev/utilities/TranslationFileGenerator/Input/IgnoreExceptions.txt","OriginalContent"),
					  tsMkCommittedFile("dev/utilities/TranslationFileGenerator/TranslationFileGenerator.cs","OriginalContent")),
				tsCs1(tsMkCommittedFile("dev/utilities/TranslationFileGenerator/bin/Release/TranslationFileGenerator.exe","ModifiedContent"),
					  tsMkCommittedFile("dev/utilities/TranslationFileGenerator/bin/Release/TranslationFileGenerator.vshost.exe"),
					  tsMkCommittedFile("dev/utilities/TranslationFileGenerator/bin/Release/TranslationFileGenerator.vshost.exe.manifest"),
					  tsMkCommittedFile("dev/utilities/TranslationFileGenerator/Input/IgnoreExceptions.txt","ModifiedContent"),
					  tsMkCommittedFile("dev/utilities/TranslationFileGenerator/TranslationFileGenerator.cs","ModifiedContent"))));
	}

	@Test
	public void testTrustedJazzignoreThatIgnoresEverythingDoesNotIgnoreGitignore() throws Exception {
		testScenario(true, "", "", ts(
				tsCs1(tsMkCommittedFile(".jazzignore", "core.ignore.recursive = {*}"),
						tsAssertFileContent(".gitignore", "*", "!/.gitignore", "!/.jazzignore"))));
	}

	@Test
	public void testUntrustedJazzignoreThatIgnoresEverythingDoesNotIgnoreGitignore() throws Exception {
		testScenario(false, "", "", ts(
				tsCs1(tsMkCommittedFile(".jazzignore", "core.ignore.recursive = {*}"),
						tsAssertFileContent(".gitignore", "*", "!/.gitignore", "!/.jazzignore"))));
	}

	@Test
	public void testRootGitIgnoreContainsJazzignoreContentsAndGlobalGitIgnoreEntries() throws Exception {
		testScenario(true, "*.ignored; /ignoreDir", ".zip", ts(
				tsInit(tsAssertFileContent(".gitignore", "*.ignored", "/ignoreDir", "*.zip")),
				tsCs1(tsMkCommittedFile(".jazzignore", "core.ignore = {*.foo}\ncore.ignore.recursive = {*.bar}"),
						tsAssertFileContent(".gitignore", "*.ignored", "/ignoreDir", "*.zip", "/*.foo", "*.bar", "!/.gitignore", "!/.jazzignore"),
						tsMkIgnoredFile("ignoreDir/text"), tsMkIgnoredFile("text.ignored"),
						tsMkIgnoredFile("ignored.foo"), tsMkIgnoredFile("ignored.bar"),
						tsMkCommittedFile("someDir/ignored.foo"), tsMkIgnoredFile("someDir/ignored.zip"))));
	}

	@Test
	public void testPreserveEmptyFoldersCreatesGitIgnoreFileForFoldersWeArentIgnoring() throws Exception {
		props.setProperty("preserve.empty.folders", "true");
		testScenario(true, "", "",
				ts(tsInit(tsMkCommittedFile(".jazzignore", "core.ignore = {ignoreDir}"),
						tsAssertFileContent(".gitignore", "/ignoreDir", "!/.gitignore", "!/.jazzignore")),
						tsCs1(tsMkIgnoredFolder("ignoreDir/"),
								tsMkCommittedFolder("folderThatIsEmpty/"),
								tsAssertFileContent("folderThatIsEmpty/.gitignore"))));
	}

	@Test
	public void testPreserveEmptyFoldersCreatesGitIgnoreFileForFoldersWhenWeAreIgnoringJazzignoreSettings() throws Exception {
		props.setProperty("preserve.empty.folders", "true");
		props.setProperty("purge.gitignore.files.between.commits", "false"); // we need to see what's going on
		testScenario(false, "", "",
				ts(tsInit(tsMkCommittedFile(".jazzignore", "core.ignore = {ignoreDir}"),
						tsAssertFileContent(".gitignore", "/ignoreDir", "!/.gitignore", "!/.jazzignore")),
					tsCs1(tsMkCommittedFolder("ignoreDir/"),
						tsMkCommittedFolder("folderThatIsEmpty/"),
						tsAssertFileContent("ignoreDir/.gitignore"),
						tsAssertFileContent("folderThatIsEmpty/.gitignore"))));
	}

	@Test
	public void testPreserveEmptyFoldersCreatesGitIgnoreFileForFoldersWhenWeAreIgnoringJazzignoreSettingsEvenIfWeAreRemovingGitIgnoredBetweenCommits() throws Exception {
		props.setProperty("preserve.empty.folders", "true");
		props.setProperty("purge.gitignore.files.between.commits", "true");
		testScenario(false, "", "",
				ts(tsInit(tsMkCommittedFile(".jazzignore", "core.ignore = {ignoreDir}"),
						tsAssertFileAbsentButIsInGit(".gitignore")),
					tsCs1(tsMkCommittedFolder("ignoreDir/"),
						tsMkCommittedFolder("folderThatIsEmpty/"),
						tsAssertFileAbsentButIsInGit("ignoreDir/.gitignore"),
						tsAssertFileAbsentButIsInGit("folderThatIsEmpty/.gitignore"))));
	}

	@Test
	public void testPreserveEmptyFoldersDeletesGitIgnoreFileForRemovedFoldersWhenWeAreIgnoringJazzignoreSettingsEvenIfWeAreRemovingGitIgnoredBetweenCommits() throws Exception {
		props.setProperty("preserve.empty.folders", "true");
		props.setProperty("purge.gitignore.files.between.commits", "true");
		testScenario(false, "", "",
				ts(tsCs1(tsMkCommittedFolder("folderThatIsEmpty/"),
						tsAssertFileAbsentButIsInGit("folderThatIsEmpty/.gitignore")),
				tsCs1(tsDeleteFile("folderThatIsEmpty/"),
						tsAssertFileAbsentButIsInGit("folderThatIsEmpty/.gitignore")),
				tsCs1(tsMkCommittedFile("somethingElse"),
						tsAssertFileAbsentAndNotInGit("folderThatIsEmpty/.gitignore"))));
	}

	@Test
	public void testPreserveEmptyFoldersWhenTrustingJazzignoreRemovesGitIgnoreFileWhenUnnecessary() throws Exception {
		props.setProperty("preserve.empty.folders", "true");
		testScenario(true, "", "",
				ts(tsInit(tsMkCommittedFile(".jazzignore", "core.ignore = {ignoreDir}"),
						tsAssertFileContent(".gitignore", "/ignoreDir", "!/.gitignore", "!/.jazzignore")),
				tsCs1(tsMkIgnoredFolder("ignoreDir/"),
						tsMkCommittedFolder("folderThatIsEmpty/"),
						tsAssertFileContent("folderThatIsEmpty/.gitignore")),
				tsCs1(tsMkCommittedFile("folderThatIsEmpty/somethingElse.txt"),
						tsAssertFileDeletedFromGit("folderThatIsEmpty/.gitignore"))));
	}

	@Test
	public void testPreserveEmptyFoldersWhenNotTrustingJazzignoreRemovesGitIgnoreFileWhenUnnecessary() throws Exception {
		props.setProperty("preserve.empty.folders", "true");
		testScenario(false, "", "",
				ts(tsInit(tsMkCommittedFile(".jazzignore", "core.ignore = {ignoreDir}"),
						tsAssertFileContent(".gitignore", "/ignoreDir", "!/.gitignore", "!/.jazzignore")),
				tsCs1(tsMkCommittedFolder("ignoreDir/"),
						tsMkCommittedFolder("folderThatIsEmpty/"),
						tsAssertFileContent("folderThatIsEmpty/.gitignore")),
				tsCs1(tsMkCommittedFile("folderThatIsEmpty/somethingElse.txt"),
						tsAssertFileDeletedFromGit("folderThatIsEmpty/.gitignore"))));
	}

	@Test
	public void testAddUpdateGitignoreIfJazzignoreAddedOrChangedInSubdirectory() throws Exception {
		migrator.init(basedir, false);
		File subdir = tempFolder.newFolder("subdir");
		File jazzignore = new File(subdir, ".jazzignore");

		Files.writeLines(jazzignore, Arrays.asList("core.ignore = {*.suo}", "core.ignore.recursive = {*.class}"), cs,
				false);

		migrator.commitChanges(TestChangeSet.INSTANCE);

		checkGit(TestChangeSet.INSTANCE.getCreatorName(), TestChangeSet.INSTANCE.getEmailAddress(), TestWorkItem.INSTANCE1.getNumber()+" "+TestChangeSet.INSTANCE.getComment());
		checkExactLinesIgnoringComments(new File(subdir, ".gitignore"), Arrays.asList("/*.suo", "*.class", "!/.gitignore", "!/.jazzignore"));
	}

	@Test
	public void testRemovedGitignoreIfJazzignoreRemoved() throws Exception {
		migrator.init(basedir, false);
		File jazzignore = new File(basedir, ".jazzignore");
		File gitignore = new File(basedir, ".gitignore");

		Files.writeLines(jazzignore, Arrays.asList("core.ignore = {*.suo}", "core.ignore.recursive = {*.class}"), cs,
				false);
		migrator.commitChanges(TestChangeSet.INSTANCE);

		assertTrue(jazzignore.delete());

		migrator.commitChanges(TestChangeSet.INSTANCE);

		assertFalse(gitignore.exists());
	}

	@Test
	public void testRestoreGitignoreIfJazzignoreNotRemoved() throws Exception {
		migrator.init(basedir, false);
		File jazzignore = new File(basedir, ".jazzignore");
		File gitignore = new File(basedir, ".gitignore");

		Files.writeLines(jazzignore, Arrays.asList("core.ignore = {*.suo}", "core.ignore.recursive = {*.class}"), cs,
				false);
		migrator.commitChanges(TestChangeSet.INSTANCE);

		assertTrue(gitignore.delete());

		migrator.commitChanges(TestChangeSet.INSTANCE);

		assertTrue(gitignore.exists());
	}

	@Test
	public void testRestoreGitignoreIfJazzignoreNotRemovedInSubdirectory() throws Exception {
		migrator.init(basedir, false);
		File subdir = tempFolder.newFolder("subdir");
		File jazzignore = new File(subdir, ".jazzignore");
		File gitignore = new File(subdir, ".gitignore");

		Files.writeLines(jazzignore, Arrays.asList("core.ignore = {*.suo}", "core.ignore.recursive = {*.class}"), cs,
				false);
		migrator.commitChanges(TestChangeSet.INSTANCE);

		assertTrue(gitignore.delete());

		migrator.commitChanges(TestChangeSet.INSTANCE);

		assertTrue(gitignore.exists());
	}

	@Test
	public void testGlobalIgnoredFilesAddedToRootGitIgnore() throws Exception {
		props.setProperty("ignore.file.extensions", ".zip; .jar; .exe; .dLL");
		migrator.initialize(props);
		migrator.init(basedir, false);

		create(new File(basedir, "some.zip"));
		create(new File(basedir, "subdir/some.jar"));
		create(new File(basedir, "subdir/subsub/some.dLL"));
		create(new File(basedir, "notIgnored"));

		migrator.commitChanges(TestChangeSet.INSTANCE);

		checkGit(1, GitMigrator.DEFAULT_INITIAL_COMMIT_USERNAME, GitMigrator.DEFAULT_INITIAL_COMMIT_USEREMAIL, "Initial commit");
		checkGit(0, TestChangeSet.INSTANCE.getCreatorName(), TestChangeSet.INSTANCE.getEmailAddress(), TestWorkItem.INSTANCE1.getNumber()+" "+TestChangeSet.INSTANCE.getComment());
		checkExactLinesIgnoringComments(new File(basedir, ".gitignore"), Arrays.asList("*.zip", "*.jar", "*.exe", "*.dLL"));
	}

	@Test
	public void testCreateTagName() {
		final String[][] testCases = { //
				{ "foo]", "foo]" }, //
				{ "This_is_actually_a_valid_tag-name!", "This_is_actually_a_valid_tag-name!" }, //
				{ "tag/.lock", "tag__lock" }, //
				{ "tag..dotdot", "tag__dotdot" }, //
				{ "tag\000nul\001one\002two\rcr\nlf\ttab\bbksp\fff\177del", "tag_nul_one_two_cr_lf_tab_bksp_ff_del" }, //
				{ "tag with whitespaces", "tag_with_whitespaces" }, //
				{ "tag~tilde", "tag_tilde" }, //
				{ "tag^caret", "tag_caret" }, //
				{ "tag:colon", "tag_colon" }, //
				{ "tag?questionmark", "tag_questionmark" }, //
				{ "tag*asterisk", "tag_asterisk" }, //
				{ "tag[openbracket", "tag_openbracket" }, //
				{ "/startswithslash", "_startswithslash" }, //
				{ "endwithslash/", "endwithslash_" }, //
				{ "contains/slash", "contains_slash" }, //
				{ "multiple//slashes", "multiple__slashes" }, //
				{ "multiple///slashes", "multiple___slashes" }, //
				{ "endwithdot.", "endwithdot_" }, //
				{ "tag@{name", "tag@_name" }, //
				{ "tag\\backslash", "tag_backslash" }, //
				{ "tag1,2,3", "tag1_2_3" }, //
		};
		for (final String[] testCase : testCases) {
			final String rtcName = testCase[0];
			final String expectedTagName = testCase[1];
			final String actualTagName = GitMigrator.createTagName(rtcName);
			assertIsValidTag(actualTagName);
			assertThat(actualTagName, equalTo(expectedTagName));
		}
	}

	@Test
	public void testCreateTag() throws Exception {
		props.setProperty("git.allow.empty.commits", "true");
		migrator.initialize(props);
		migrator.init(basedir, false);

		migrator.createTag(TestTag.INSTANCE);

		ensureGitIsOpen();
		List<Ref> tags = git.tagList().call();
		assertEquals(1, tags.size());
		Ref ref = tags.get(0);
		assertEquals("refs/tags/myTag", ref.getName());
	}

	@Test
	public void testParseConfigValue() {
		assertEquals(1, migrator.parseConfigValue(null, 1));
		assertEquals(1, migrator.parseConfigValue("", 1));
		assertEquals(1, migrator.parseConfigValue(" ", 1));
		assertEquals(2, migrator.parseConfigValue("2", 1));
		assertEquals(1024, migrator.parseConfigValue("1k", 1));
		assertEquals(1024, migrator.parseConfigValue("1K", 1));
		assertEquals(1024, migrator.parseConfigValue("1 kB", 1));
		assertEquals(1024, migrator.parseConfigValue("1  KB", 1));
		assertEquals(2097152, migrator.parseConfigValue("2 m", 1));
		assertEquals(2097152, migrator.parseConfigValue("2  M", 1));
		assertEquals(2097152, migrator.parseConfigValue("2mb", 1));
		assertEquals(2097152, migrator.parseConfigValue("2MB", 1));
	}

	@Test
	public void testGetGitCacheConfig_defaults() throws Exception {
		// check values
		WindowCacheConfig cfg = migrator.getWindowCacheConfig();
		assertEquals(128, cfg.getPackedGitOpenFiles());
		assertEquals(10 * WindowCacheConfig.MB, cfg.getPackedGitLimit());
		assertEquals(8 * WindowCacheConfig.KB, cfg.getPackedGitWindowSize());
		assertFalse(cfg.isPackedGitMMAP());
		assertEquals(10 * WindowCacheConfig.MB, cfg.getDeltaBaseCacheLimit());
		assertEquals(50 * WindowCacheConfig.MB, cfg.getStreamFileThreshold());
	}

	@Test
	public void testGetGitCacheConfig() throws Exception {
		props.setProperty("packedgitopenfiles", "129");
		props.setProperty("packedgitlimit", "11m");
		props.setProperty("packedgitwindowsize", "9k");
		props.setProperty("packedgitmmap", "true");
		props.setProperty("deltabasecachelimit", "11m");
		props.setProperty("streamfilethreshold", "51m");
		migrator.initialize(props);
		// check values
		WindowCacheConfig cfg = migrator.getWindowCacheConfig();
		assertEquals(129, cfg.getPackedGitOpenFiles());
		assertEquals(11 * WindowCacheConfig.MB, cfg.getPackedGitLimit());
		assertEquals(9 * WindowCacheConfig.KB, cfg.getPackedGitWindowSize());
		assertTrue(cfg.isPackedGitMMAP());
		assertEquals(11 * WindowCacheConfig.MB, cfg.getDeltaBaseCacheLimit());
		assertEquals(51 * WindowCacheConfig.MB, cfg.getStreamFileThreshold());
	}

	@Test
	public void testGetMaxFileThresholdValue_maxOneFirthOfHeap() {
		assertEquals(10000, migrator.getMaxFileThresholdValue(12000, 40000));
	}

	@Test
	public void testGetMaxFileThresholdValue_lessOrEqulalMaxArraySize() {
		assertEquals(Integer.MAX_VALUE, migrator.getMaxFileThresholdValue(Integer.MAX_VALUE, Long.MAX_VALUE));
	}
}
