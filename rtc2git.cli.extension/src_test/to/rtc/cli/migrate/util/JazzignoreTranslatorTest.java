package to.rtc.cli.migrate.util;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JazzignoreTranslatorTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	@Test
	public void testToGitignore() throws IOException {
		File jazzignore = new File(tempFolder.getRoot(), ".jazzignore");
		Files.writeLines(jazzignore, getJazzignoreLines(),
				Charset.defaultCharset(), false);
		List<String> gitignoreLines = JazzignoreTranslator
				.toGitignore(jazzignore);
		assertEquals(getExpectedGitignoreLines(), gitignoreLines);
	}

	private List<String> getJazzignoreLines() {
		List<String> lines = new ArrayList<String>();
		lines.add("### Jazz Ignore 0");
		lines.add("# Ignored files and folders will not be committed, but may be modified during");
		lines.add("# accept or update.");
		lines.add("# - Ignore properties should contain a space separated list of filename patterns.");
		lines.add("# - Each pattern is case sensitive and surrounded by braces ('{' and '}').");
		lines.add("# - \"*\" matches zero or more characters.");
		lines.add("# - \"?\" matches a single character.");
		lines.add("   # - The pattern list may be split across lines by ending the line with a");
		lines.add("#     backslash and starting the next line with a tab.");
		lines.add("# - Patterns in core.ignore prevent matching resources in the same");
		lines.add("#     directory from being committed.");
		lines.add("# - Patterns in core.ignore.recursive matching resources in the current");
		lines.add("#     directory and all subdirectories from being committed.");
		lines.add("# - The default value of core.ignore.recursive is *.class");
		lines.add("# - The default value for core.ignore is bin");
		lines.add("#");
		lines.add("# To ignore shell scripts and hidden files in this subtree:");
		lines.add("  #     e.g: core.ignore.recursive = {*.sh} {\\.*}");
		lines.add("#");
		lines.add("# To ignore resources named 'bin' in the current directory (but allow");
		lines.add("#  them in any sub directorybelow):");
		lines.add("#     e.g: core.ignore.recursive = {*.sh} {\\.*}");
		lines.add("#");
		lines.add("# NOTE: modifying ignore files will not change the ignore status of");
		lines.add("#     Eclipse derived resources.");
		lines.add("");
		lines.add("   core.ignore.recursive = {*.class} {*.so} \\");
		lines.add("       {*.pyc}");
		lines.add("");
		lines.add(" core.ignore = {*.suo} {.classpath} \\");
		lines.add("  {.idea} \\");
		lines.add("  {.project} \\");
		lines.add("  {.settings} \\");
		lines.add("  {bin} \\");
		lines.add("  {dist} \\");
		lines.add("  {a?c} \\");
		return lines;
	}

	private List<String> getExpectedGitignoreLines() {
		List<String> lines = new ArrayList<String>();
		lines.add("*.class");
		lines.add("*.so");
		lines.add("*.pyc");
		lines.add("/*.suo");
		lines.add("/.classpath");
		lines.add("/.idea");
		lines.add("/.project");
		lines.add("/.settings");
		lines.add("/bin");
		lines.add("/dist");
		lines.add("/a?c");
		return lines;
	}
}
