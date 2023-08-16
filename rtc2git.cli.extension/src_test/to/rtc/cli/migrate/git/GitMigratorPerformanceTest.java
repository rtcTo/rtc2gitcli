package to.rtc.cli.migrate.git;

import java.io.IOException;
import java.util.ArrayList;

import org.junit.Test;

/**
 * Tests the performance of the {@link GitMigrator} implementation.
 *
 * @author peter.darton
 */
public class GitMigratorPerformanceTest extends GitMigratorTestBase {
	/*
	 * We want to see how performance varies depending on settings.
	 * To make it practicable, we don't test all combinations.
	 * We test a "main path" and a "one step from the main path".
	 * Our idea of the "main path" is a size of 3, churn of 10, purging (gitignores between commits), preserving (empty folders), and not trusting (jazzignore files).
	 * We also test a size of 1 and 5, a churn of 1 and 30, not purging, not preserving, and trusting.
	 */
	private static final int MAIN_SIZE = 3;
	private static final int SMALL_SIZE = 1;
	private static final int BIG_SIZE = 5;
	private static final int MAIN_CHURN = 10;
	private static final int SMALL_CHURN = 1;
	private static final int BIG_CHURN = 30;
	private static final boolean MAIN_PURGING = true;
	private static final boolean MAIN_PRESERVING = true;
	private static final boolean MAIN_TRUSTING = false;

	@Test
	public void mainPath() throws Exception {
		final int dataSize = MAIN_SIZE;
		final int minorChanges = MAIN_CHURN;
		final boolean purgingGitignoreFilesBetweenCommits = MAIN_PURGING;
		final boolean preserveEmptyfolders = MAIN_PRESERVING;
		final boolean trustJazzIgnoresFiles = MAIN_TRUSTING;
		runPerformanceTest(purgingGitignoreFilesBetweenCommits, preserveEmptyfolders, trustJazzIgnoresFiles, dataSize,
				minorChanges);
	}

	@Test
	public void trusting() throws Exception {
		final int dataSize = MAIN_SIZE;
		final int minorChanges = MAIN_CHURN;
		final boolean purgingGitignoreFilesBetweenCommits = MAIN_PURGING;
		final boolean preserveEmptyfolders = MAIN_PRESERVING;
		final boolean trustJazzIgnoresFiles = !MAIN_TRUSTING;
		runPerformanceTest(purgingGitignoreFilesBetweenCommits, preserveEmptyfolders, trustJazzIgnoresFiles, dataSize,
				minorChanges);
	}

	@Test
	public void preserving() throws Exception {
		final int dataSize = MAIN_SIZE;
		final int minorChanges = MAIN_CHURN;
		final boolean purgingGitignoreFilesBetweenCommits = MAIN_PURGING;
		final boolean preserveEmptyfolders = !MAIN_PRESERVING;
		final boolean trustJazzIgnoresFiles = MAIN_TRUSTING;
		runPerformanceTest(purgingGitignoreFilesBetweenCommits, preserveEmptyfolders, trustJazzIgnoresFiles, dataSize,
				minorChanges);
	}

	@Test
	public void purging() throws Exception {
		final int dataSize = MAIN_SIZE;
		final int minorChanges = MAIN_CHURN;
		final boolean purgingGitignoreFilesBetweenCommits = !MAIN_PURGING;
		final boolean preserveEmptyfolders = MAIN_PRESERVING;
		final boolean trustJazzIgnoresFiles = MAIN_TRUSTING;
		runPerformanceTest(purgingGitignoreFilesBetweenCommits, preserveEmptyfolders, trustJazzIgnoresFiles, dataSize,
				minorChanges);
	}

	@Test
	public void smallChurn() throws Exception {
		final int dataSize = MAIN_SIZE;
		final int minorChanges = SMALL_CHURN;
		final boolean purgingGitignoreFilesBetweenCommits = MAIN_PURGING;
		final boolean preserveEmptyfolders = MAIN_PRESERVING;
		final boolean trustJazzIgnoresFiles = MAIN_TRUSTING;
		runPerformanceTest(purgingGitignoreFilesBetweenCommits, preserveEmptyfolders, trustJazzIgnoresFiles, dataSize,
				minorChanges);
	}

	@Test
	public void bigChurn() throws Exception {
		final int dataSize = MAIN_SIZE;
		final int minorChanges = BIG_CHURN;
		final boolean purgingGitignoreFilesBetweenCommits = MAIN_PURGING;
		final boolean preserveEmptyfolders = MAIN_PRESERVING;
		final boolean trustJazzIgnoresFiles = MAIN_TRUSTING;
		runPerformanceTest(purgingGitignoreFilesBetweenCommits, preserveEmptyfolders, trustJazzIgnoresFiles, dataSize,
				minorChanges);
	}

	@Test
	public void smallSize() throws Exception {
		final int dataSize = SMALL_SIZE;
		final int minorChanges = MAIN_CHURN;
		final boolean purgingGitignoreFilesBetweenCommits = MAIN_PURGING;
		final boolean preserveEmptyfolders = MAIN_PRESERVING;
		final boolean trustJazzIgnoresFiles = MAIN_TRUSTING;
		runPerformanceTest(purgingGitignoreFilesBetweenCommits, preserveEmptyfolders, trustJazzIgnoresFiles, dataSize,
				minorChanges);
	}

	@Test
	public void bigSize() throws Exception {
		final int dataSize = BIG_SIZE;
		final int minorChanges = MAIN_CHURN;
		final boolean purgingGitignoreFilesBetweenCommits = MAIN_PURGING;
		final boolean preserveEmptyfolders = MAIN_PRESERVING;
		final boolean trustJazzIgnoresFiles = MAIN_TRUSTING;
		runPerformanceTest(purgingGitignoreFilesBetweenCommits, preserveEmptyfolders, trustJazzIgnoresFiles, dataSize,
				minorChanges);
	}

	@Test
	public void bigSizeAndPurging() throws Exception {
		final int dataSize = BIG_SIZE;
		final int minorChanges = MAIN_CHURN;
		final boolean purgingGitignoreFilesBetweenCommits = !MAIN_PURGING;
		final boolean preserveEmptyfolders = MAIN_PRESERVING;
		final boolean trustJazzIgnoresFiles = MAIN_TRUSTING;
		runPerformanceTest(purgingGitignoreFilesBetweenCommits, preserveEmptyfolders, trustJazzIgnoresFiles, dataSize,
				minorChanges);
	}

	@Test
	public void bigSizeAndPreserving() throws Exception {
		final int dataSize = BIG_SIZE;
		final int minorChanges = MAIN_CHURN;
		final boolean purgingGitignoreFilesBetweenCommits = MAIN_PURGING;
		final boolean preserveEmptyfolders = !MAIN_PRESERVING;
		final boolean trustJazzIgnoresFiles = MAIN_TRUSTING;
		runPerformanceTest(purgingGitignoreFilesBetweenCommits, preserveEmptyfolders, trustJazzIgnoresFiles, dataSize,
				minorChanges);
	}

	@Test
	public void bigSizeAndTrusting() throws Exception {
		final int dataSize = BIG_SIZE;
		final int minorChanges = MAIN_CHURN;
		final boolean purgingGitignoreFilesBetweenCommits = MAIN_PURGING;
		final boolean preserveEmptyfolders = MAIN_PRESERVING;
		final boolean trustJazzIgnoresFiles = !MAIN_TRUSTING;
		runPerformanceTest(purgingGitignoreFilesBetweenCommits, preserveEmptyfolders, trustJazzIgnoresFiles, dataSize,
				minorChanges);
	}

	@Test
	public void bigSizeAndPurgingPreservingTrusting() throws Exception {
		final int dataSize = BIG_SIZE;
		final int minorChanges = MAIN_CHURN;
		final boolean purgingGitignoreFilesBetweenCommits = !MAIN_PURGING;
		final boolean preserveEmptyfolders = !MAIN_PRESERVING;
		final boolean trustJazzIgnoresFiles = !MAIN_TRUSTING;
		runPerformanceTest(purgingGitignoreFilesBetweenCommits, preserveEmptyfolders, trustJazzIgnoresFiles, dataSize,
				minorChanges);
	}

	private void runPerformanceTest(final boolean purgingGitignoreFilesBetweenCommits,
			final boolean preserveEmptyfolders, final boolean trustJazzIgnoresFiles, final int dataSize,
			final int minorChanges) throws Exception, IOException {
		final char[] letters = new char[dataSize];
		for( int i=0 ; i<dataSize ; i++ ) {
			letters[i] = (char)('a' + i);
		}
		System.out.println("Test started with datasize=" + dataSize + ", churn=" + minorChanges + ", purge="
				+ purgingGitignoreFilesBetweenCommits + ", preserve=" + preserveEmptyfolders + ", trust="
				+ trustJazzIgnoresFiles);
		props.setProperty("preserve.empty.folders", Boolean.valueOf(preserveEmptyfolders).toString());
		props.setProperty("purge.gitignore.files.between.commits", Boolean.valueOf(purgingGitignoreFilesBetweenCommits).toString());
		final ArrayList<TestScenarioFile> creations = new ArrayList<TestScenarioFile>();
		final ArrayList<TestScenarioFile> jazzignores = new ArrayList<TestScenarioFile>();
		final ArrayList<TestScenarioFile> modifications = new ArrayList<TestScenarioFile>();
		for( final char one : letters ) {
			for( final char two : letters ) {
				final String path2 = one + "/" + two;
				for( final char three : letters ) {
					final String path3 = path2 + "/" + three;
					for( final char four : letters ) {
						final String path4 = path3 + "/" + four;
						final String textfilePath = path4 + ".txt";
						final String initialContent = "File " + one + two + three + four;
						final String newContent = "Modified " + one + two + three + four;
						creations.add(tsMkCommittedFile(textfilePath, initialContent));
						modifications.add(tsMkCommittedFile(textfilePath, newContent));
					}
					final String jazzignorepath = path3 + "/.jazzignore";
					final String gitignorepath = path3 + "/.gitignore";
					jazzignores.add(tsMkCommittedFile(jazzignorepath, "core.ignore = {x}"));
					if ( purgingGitignoreFilesBetweenCommits ) {
						jazzignores.add(tsAssertFileAbsentButIsInGit(gitignorepath));
					} else {
						jazzignores.add(tsAssertFileContent(gitignorepath, "/x", "!/.gitignore", "!/.jazzignore"));
					}
					final String unchangingTextFilePath = path2 + "/Unchanging_" + three + ".txt";
					final String unchangingTextFileContent = "File " + one + two + three;
					creations.add(tsMkCommittedFile(unchangingTextFilePath, unchangingTextFileContent));
					final String initiallyEmptyThenFilledFolderPath = path3 + "/dirInitiallyEmpty/";
					if ( preserveEmptyfolders ) {
						creations.add(tsMkCommittedFolder(initiallyEmptyThenFilledFolderPath));
					} else {
						creations.add(tsMkIgnoredFolder(initiallyEmptyThenFilledFolderPath));
					}
					modifications.add(tsMkCommittedFile(initiallyEmptyThenFilledFolderPath+"notEmptyAnymore.file"));
					final String initiallyEmptyThenRmedFolderPath = path3 + "/emptyDirLaterDeleted/";
					if ( preserveEmptyfolders ) {
						creations.add(tsMkCommittedFolder(initiallyEmptyThenRmedFolderPath));
					} else {
						creations.add(tsMkIgnoredFolder(initiallyEmptyThenRmedFolderPath));
					}
					modifications.add(tsDeleteFile(initiallyEmptyThenRmedFolderPath));
				}
			}
		}
		final ArrayList<TestScenarioStage> stages = new ArrayList<GitMigratorPerformanceTest.TestScenarioStage>();
		stages.add(tsInit(tsMkCommittedFile(".jazzignore", "core.ignore.recursive = {x}"),
				purgingGitignoreFilesBetweenCommits?tsAssertFileAbsentButIsInGit(".gitignore"):
				tsAssertFileContent(".gitignore", "x", "!/.gitignore", "!/.jazzignore")));
		stages.add(tsCs1(creations.toArray(new TestScenarioFile[0])));
		stages.add(tsCs1(modifications.get(0)));
		stages.add(tsCs1(creations.get(0)));
		stages.add(tsCs1(jazzignores.toArray(new TestScenarioFile[0])));
		stages.add(tsCs1(modifications.get(0)));
		stages.add(tsCs1(creations.get(0)));
		stages.add(tsCs1(modifications.toArray(new TestScenarioFile[0])));
		stages.add(tsCs1(creations.get(0)));
		stages.add(tsCs1(modifications.get(0)));
		for( int i=1 ; i<=minorChanges ; i++ ) {
			stages.add(tsCs1(tsMkCommittedFile("a/a/a/changedfile", "Some content #" + i)));
		}
		testScenario(trustJazzIgnoresFiles, "", "",
				ts(stages.toArray(new TestScenarioStage[0])));
		System.out.println("Test passed");
	}
}
