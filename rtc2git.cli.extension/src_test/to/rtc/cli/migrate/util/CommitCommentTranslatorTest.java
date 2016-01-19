/**
 * File Name: CommitCommentTranslatorTest.java
 * 
 * Copyright (c) 2016 BISON Schweiz AG, All Rights Reserved.
 */

package to.rtc.cli.migrate.util;

import static org.junit.Assert.assertEquals;

import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests the {@link CommitCommentTranslator} implementation.
 *
 * @author Patrick Reinhart
 */
public class CommitCommentTranslatorTest {
	private Properties props;
	private CommitCommentTranslator translator;

	@Before
	public void setUp() {
		props = new Properties();
		props.setProperty("commit.message.regex.1", "Revision [0-9]+: (.+)");
		props.setProperty("commit.message.replacement.1", "$1");

		props.setProperty("commit.message.regex.2", "^(b|B|#)0*([1-9][0-9]+) *[:-]? +(.*)");
		props.setProperty("commit.message.replacement.2", "BUG-$2 $3");

		props.setProperty("commit.message.regex.3", "^(b|B|#)0* *[:-]? +(.*)");
		props.setProperty("commit.message.replacement.3", "$2");

		props.setProperty("commit.message.regex.4", "^(US?|D)0*([1-9][0-9]+) *[:-]? +(.*)");
		props.setProperty("commit.message.replacement.4", "RTC-$2 $3");

		translator = new CommitCommentTranslator(props);
	}

	@Test
	public void testTranslate() throws Exception {
		assertEquals("BUG-33589 Added JUnit for renamed RecordListener implementation",
				translator.translate("Revision 49988: B33589 - Added JUnit for renamed RecordListener implementation"));
	}

	@Test
	public void testTranslate_removeTailingDashAndColumn() throws Exception {
		assertEquals("BUG-33589 Added JUnit for renamed RecordListener implementation",
				translator.translate("Revision 49988: B33589- Added JUnit for renamed RecordListener implementation"));
		assertEquals("BUG-33589 Added JUnit for renamed RecordListener implementation",
				translator.translate("Revision 49988: B33589: Added JUnit for renamed RecordListener implementation"));
	}

	@Test
	public void testTranslateLeadingZerosRemoved() throws Exception {
		assertEquals("BUG-33589 Added JUnit for renamed RecordListener implementation", translator
				.translate("Revision 49988: B0033589 - Added JUnit for renamed RecordListener implementation"));
	}

	@Test
	public void testTranslateLeadingDummyValuesRemoved() throws Exception {
		assertEquals("Some changes", translator.translate("B00000 - Some changes"));
		assertEquals("Some changes", translator.translate("#0: Some changes"));
		assertEquals("Some changes", translator.translate("#00000: Some changes"));
	}

	@Test
	public void testTranslateRtcSpecificComments() throws Exception {
		assertEquals("RTC-1234 Some changes", translator.translate("D01234 - Some changes"));
		assertEquals("RTC-1234 Some changes", translator.translate("U01234: Some changes"));
		assertEquals("RTC-1234 Some changes", translator.translate("US01234: Some changes"));
		assertEquals("RTC-1234 Some changes", translator.translate("US01234: Some changes"));
	}
}
