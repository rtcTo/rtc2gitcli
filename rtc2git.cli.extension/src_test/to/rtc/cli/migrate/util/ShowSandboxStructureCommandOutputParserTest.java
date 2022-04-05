package to.rtc.cli.migrate.util;

import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ShowSandboxStructureCommandOutputParserTest {
	@Parameterized.Parameters(name = "{index}: {0}")
	public static Iterable<Object[]> data() {
		final List<Object[]> data = new ArrayList<Object[]>();
		// This is how things should be
		data.add(new Object[] { "Ok", null,
				new String[] { "AFolderName/", ".project" },
				new String[] {
						"DelegateCommand [show sandbox-structure -u ... -P ... -d /tmp/sandbox] running...",
						"Sandbox: /tmp/sandbox",
						"  Local: /tmp/sandbox/AFolderName", 
						"    Remote: myworkspace/MyComponent/AFolderName/", 
						"  Local: /tmp/sandbox/.project", 
						"    Remote: myworkspace/MyComponent/.project",
						"DelegateCommand [show sandbox-structure -u ... -P ... -d /tmp/sandbox] finished in [123]ms, returned 0"
						} });
		// This is a longer example of an OK scenario
		data.add(new Object[] { "Longer OK", null,
				new String[] { "Windowed Components/", "Task Bar/", "No Test Data/", "Manifests/", "BootStrap.cmd", "AutomationPlugin/", "ANB.UIAutomation.sln", "ANB.UIAutomation.Tests/", ".jazzignore" },
				new String[] {
						"DelegateCommand [show sandbox-structure -u ... -P ... -d /home/jenkins/workspace/testJob/rtc] running...",
						"Sandbox: /home/jenkins/workspace/testJob/rtc",
						"  Local: /home/jenkins/workspace/testJob/rtc/Windowed Components",
						"    Remote: myWkspce_t/Test_Data_Component/Windowed Components/",
						"  Local: /home/jenkins/workspace/testJob/rtc/Task Bar",
						"    Remote: myWkspce_t/Test_Data_Component/Task Bar/",
						"  Local: /home/jenkins/workspace/testJob/rtc/No Test Data",
						"    Remote: myWkspce_t/Test_Data_Component/No Test Data/",
						"  Local: /home/jenkins/workspace/testJob/rtc/Manifests",
						"    Remote: myWkspce_t/Tests_Run_Component/Manifests/",
						"  Local: /home/jenkins/workspace/testJob/rtc/BootStrap.cmd",
						"    Remote: myWkspce_t/Test_Source_Component/BootStrap.cmd",
						"  Local: /home/jenkins/workspace/testJob/rtc/AutomationPlugin",
						"    Remote: myWkspce_t/Test_Source_Component/AutomationPlugin/",
						"  Local: /home/jenkins/workspace/testJob/rtc/ANB.UIAutomation.sln",
						"    Remote: myWkspce_t/Test_Source_Component/ANB.UIAutomation.sln",
						"  Local: /home/jenkins/workspace/testJob/rtc/ANB.UIAutomation.Tests",
						"    Remote: myWkspce_t/Test_Source_Component/ANB.UIAutomation.Tests/",
						"  Local: /home/jenkins/workspace/testJob/rtc/.jazzignore",
						"    Remote: myWkspce_t/Test_Source_Component/.jazzignore",
						"DelegateCommand [show sandbox-structure -u ... -P ... -d /home/jenkins/workspace/testJob/rtc] finished in [2305]ms, returned 0"
						} });
		// but often an scm accept will fail to update things correctly
		// and leave the sandbox in a mess.
		data.add(new Object[] { "OutOfSync", "Local file system is out of sync. Run 'scm load' with --force or --resync options to reload the workspace.",
				new String[] { "AFolderName/", ".project" },
				new String[] {
						"DelegateCommand [show sandbox-structure -u ... -P ... -d /tmp/sandbox] running...",
						"Sandbox: /tmp/sandbox",
						"  Local: /tmp/sandbox/AFolderName", 
						"    Remote: myworkspace/MyComponent/AFolderName/", 
						"  Local: /tmp/sandbox/.project", 
						"    Remote: myworkspace/MyComponent/.project",
						"Local file system is out of sync. Run 'scm load' with --force or --resync options to reload the workspace.",
						"DelegateCommand [show sandbox-structure -u ... -P ... -d /tmp/sandbox] finished in [123]ms, returned 0"
						} });
		// ...and, or corrupt things so badly RTC doesn't know what's going on at all
		data.add(new Object[] { "Filesystem_Corrupt", "Local file system is corrupt. Run repair command to make the workspace consistent.",
				new String[] { "AFolderName/", ".project" },
				new String[] {
						"DelegateCommand [show sandbox-structure -u ... -P ... -d /tmp/sandbox] running...",
						"Local file system is corrupt. Run repair command to make the workspace consistent.",
						"Sandbox: /tmp/sandbox",
						"  Local: /tmp/sandbox/AFolderName", 
						"    Remote: myworkspace/MyComponent/AFolderName/", 
						"  Local: /tmp/sandbox/.project", 
						"    Remote: myworkspace/MyComponent/.project",
						"DelegateCommand [show sandbox-structure -u ... -P ... -d /tmp/sandbox] finished in [123]ms, returned 0"
						} });
		final String someRandomText = UUID.randomUUID().toString();
		data.add(new Object[] { "Unexpected output at start of line", someRandomText,
				new String[] { ".project" },
				new String[] {
						"DelegateCommand [show sandbox-structure -u ... -P ... -d /tmp/sandbox] running...",
						"Sandbox: /tmp/sandbox",
						"  Local: /tmp/sandbox/AFolderName", 
						someRandomText, 
						"  Local: /tmp/sandbox/.project", 
						"    Remote: myworkspace/MyComponent/.project",
						"DelegateCommand [show sandbox-structure -u ... -P ... -d /tmp/sandbox] finished in [123]ms, returned 0"
						} });
		data.add(new Object[] { "Unexpected output indented a bit", "    " + someRandomText,
				new String[] { ".project" },
				new String[] {
						"DelegateCommand [show sandbox-structure -u ... -P ... -d /tmp/sandbox] running...",
						"Sandbox: /tmp/sandbox",
						"  Local: /tmp/sandbox/AFolderName", 
						"    " + someRandomText, 
						"  Local: /tmp/sandbox/.project", 
						"    Remote: myworkspace/MyComponent/.project",
						"DelegateCommand [show sandbox-structure -u ... -P ... -d /tmp/sandbox] finished in [123]ms, returned 0"
						} });
		data.add(new Object[] { "Unexpected output indented a lot", "      " + someRandomText,
				new String[] { ".project" },
				new String[] {
						"DelegateCommand [show sandbox-structure -u ... -P ... -d /tmp/sandbox] running...",
						"Sandbox: /tmp/sandbox",
						"  Local: /tmp/sandbox/AFolderName", 
						"      " + someRandomText, 
						"  Local: /tmp/sandbox/.project", 
						"    Remote: myworkspace/MyComponent/.project",
						"DelegateCommand [show sandbox-structure -u ... -P ... -d /tmp/sandbox] finished in [123]ms, returned 0"
						} });
		data.add(new Object[] { "No projects", "  No projects were found in the sandbox.",
				new String[0],
				new String[] {
						"DelegateCommand [show sandbox-structure -u ... -P ... -d /tmp/sandbox] running...",
						"  No projects were found in the sandbox.",
						"DelegateCommand [show sandbox-structure -u ... -P ... -d /tmp/sandbox] finished in [123]ms, returned 0"
						} });
		return data;
	}

	private final String testName;
	private final boolean expectedFilesystemOutOfSync;
	private final String expectedWhatWeDidntLike;
	private final Set<String> expectedRootPaths;
	private final List<String> textToBeParsed;

	public ShowSandboxStructureCommandOutputParserTest(String testName, String expectedWhatWeDidntLike, String[] expectedRootPaths,
			String[] textToBeParsed) {
		this.testName = testName;
		this.expectedWhatWeDidntLike = expectedWhatWeDidntLike;
		this.expectedFilesystemOutOfSync = expectedWhatWeDidntLike != null;
		this.expectedRootPaths = new TreeSet<String>(Arrays.asList(expectedRootPaths));
		this.textToBeParsed = Arrays.asList(textToBeParsed);
	}

	@Test
	public void testFilesystemOutOfSync() {
		// Given
		final ShowSandboxStructureCommandOutputParser instance = new ShowSandboxStructureCommandOutputParser(textToBeParsed);

		// When
		final boolean actual = instance.isFilesystemOutOfSync();

		// Then
		assertThat(testName + ": isFilesystemOutOfSync", actual, equalTo(expectedFilesystemOutOfSync));
	}

	@Test
	public void testWhatWeTookADislikeTo() {
		// Given
		final ShowSandboxStructureCommandOutputParser instance = new ShowSandboxStructureCommandOutputParser(textToBeParsed);

		// When
		final String actual = instance.whatWeTookADislikeTo();

		// Then
		assertThat(testName + ": whatWeTookADislikeTo", actual, equalTo(expectedWhatWeDidntLike));
	}

	@Test
	public void testExpectedRootContent() {
		// Given
		final ShowSandboxStructureCommandOutputParser instance = new ShowSandboxStructureCommandOutputParser(textToBeParsed);

		// When
		final Set<String> actual = instance.getExpectedRootContent();

		// Then
		assertThat(testName + ": ExpectedRootContent", new TreeSet<String>(actual), equalTo(expectedRootPaths));
	}
}
