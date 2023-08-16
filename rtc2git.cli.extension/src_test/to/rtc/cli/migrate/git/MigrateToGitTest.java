package to.rtc.cli.migrate.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import to.rtc.cli.migrate.util.Files;

public class MigrateToGitTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	@Test
	public void testTrimProperties() throws Exception {
		// Given
		List<String> lines = new ArrayList<String>();
		lines.add("a.b.c =  d ");
		lines.add("p2 =  my value ; ");
		Properties untrimmed = prepareUntrimmedProperties(lines);
		assertEquals(2, untrimmed.entrySet().size());

		// When
		Properties trimmed = MigrateToGit.trimProperties(untrimmed);

		// Then
		assertNotNull(trimmed);
		assertEquals(4, trimmed.entrySet().size());
		assertEquals("d", trimmed.getProperty("a.b.c"));
		assertEquals("d ", trimmed.getProperty("a.b.c.untrimmed"));
		assertEquals("my value ;", trimmed.getProperty("p2"));
	}

	private Charset getPropertyCharset() {
		return Charset.forName("ISO-8859-1");
	}

	private Properties prepareUntrimmedProperties(List<String> lines) throws Exception {
		File file = new File(tempFolder.getRoot(), "untrimmed.properties");
		Files.writeLines(file, lines, getPropertyCharset(), false);
		Properties untrimmed = new Properties();
		FileInputStream in = new FileInputStream(file);
		try {
			untrimmed.load(in);
		} finally {
			in.close();
		}
		return untrimmed;
	}
}
