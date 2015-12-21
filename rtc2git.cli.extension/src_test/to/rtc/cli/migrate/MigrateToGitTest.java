/**
 * File Name: MigrateToTest.java
 *
 * Copyright (c) 2015 BISON Schweiz AG, All Rights Reserved.
 */

package to.rtc.cli.migrate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import to.rtc.cli.migrate.git.MigrateToGit;
import to.rtc.cli.migrate.git.MigrateToGitOptions;
import to.rtc.cli.migrate.util.Files;

import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;

public class MigrateToGitTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	@Test
	public void testTrimProperties() throws Exception {
		List<String> lines = new ArrayList<String>();
		lines.add("a.b.c =  d ");
		lines.add("p2 =  my value ; ");
		Properties untrimmed = prepareUntrimmedProperties(lines);
		Properties trimmed = new TestMigrateTo().trimProperties(untrimmed);
		assertNotNull(trimmed);
		assertEquals(2, trimmed.entrySet().size());
		assertEquals("d", trimmed.getProperty("a.b.c"));
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

	private static final class TestMigrateTo extends MigrateToGit {
		@Override
		public Migrator getMigrator() {
			return null;
		}

		private Properties readProperties(ICommandLine subargs) {
			final Properties props = new Properties();
			try {
				FileInputStream in = new FileInputStream(
						subargs.getOption(MigrateToGitOptions.OPT_MIGRATION_PROPERTIES));
				try {
					props.load(in);
				} finally {
					in.close();
				}
			} catch (IOException e) {
				throw new RuntimeException("Unable to read migration properties", e);
			}
			return trimProperties(props);
		}

		@Override
		protected Properties trimProperties(Properties props) {
			Set<Object> keyset = props.keySet();
			for (Object keyObject : keyset) {
				String key = (String) keyObject;
				props.setProperty(key, props.getProperty(key).trim());
			}
			return props;
		}
	}
}
