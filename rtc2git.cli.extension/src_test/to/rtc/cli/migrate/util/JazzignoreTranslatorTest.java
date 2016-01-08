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
		Files.writeLines(jazzignore, getJazzignoreLines(), Charset.defaultCharset(), false);
		List<String> gitignoreLines = JazzignoreTranslator.toGitignore(jazzignore);
		assertEquals(getExpectedGitignoreLines(), gitignoreLines);
	}

	private List<String> getJazzignoreLines() {
		List<String> lines = new ArrayList<String>();
		// leading white space is intentional
		lines.add("### Jazz Ignore 0");
		lines.add("   #    The pattern list may be split across lines");
		lines.add("  #    e.g: core.ignore.recursive = {*.sh} {\\.*}");
		lines.add(" #    some text");
		lines.add("#    some text");
		lines.add("");
		lines.add("   core.ignore.recursive = {*.class} {*.so} \\");
		lines.add("       {*.pyc}");
		lines.add("");
		lines.add(" core.ignore = {*.suo} {.classpath} \\");
		lines.add("  {.idea} \\");
		lines.add("  {.project} \\");
		lines.add("  {.settings} \\");
		lines.add("  {${buildDirectory\\}} \\"); // this line must be ignored
		lines.add("  {bin} \\");
		lines.add("  {dist} \\");
		lines.add("  {a?c} \\"); // no next line after line continuation
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
