/**
 * File Name: CommitCommentTranslatorTest.java
 *
 * Copyright (c) 2016 BISON Schweiz AG, All Rights Reserved.
 */

package to.rtc.cli.migrate;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Date;
import java.util.Iterator;
import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Tests the {@link RtcTagList} implementation.
 *
 * @author Florian Bühlmann
 */
public class RtcTagListTest {

	private RtcTagList tagList;

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Before
	public void setup() {
		tagList = new RtcTagList(new StreamOutput(System.out));
	}

	private long TODAY = (new Date()).getTime();
	private long YESTERDAY = TODAY - 86400000;
	private long TOMORROW = TODAY + 86400000;

	@Test
	public void testAddAndGetUniqueTag() {
		RtcTag rtcTag = new RtcTag("uuid").setOriginalName("TagNameUnique").setCreationDate(Long.MAX_VALUE);
		tagList.add(rtcTag);

		assertThat("get returns the added tag", tagList.getTag("uuit", "TagNameUnique", Long.MAX_VALUE),
				equalTo(rtcTag));
	}

	@Test
	public void testAddMakeTagsUniqueIfCreatedateDifferToMuch() {
		tagList.add(new RtcTag("uuid").setOriginalName("TagNameUnique").setCreationDate(YESTERDAY));
		tagList.add(new RtcTag("uuid").setOriginalName("TagNameUnique").setCreationDate(TODAY));

		assertThat("We have two tags in the list", tagList.size(), equalTo(2));

		Iterator<RtcTag> tagListIterator = tagList.iterator();
		RtcTag tag1 = tagListIterator.next();
		RtcTag tag2 = tagListIterator.next();

		assertThat("We have two tags with unique name", tag1.getName(), not(equalTo(tag2.getName())));
		assertThat("We have two tags with the same original name", tag1.getOriginalName(),
				equalTo(tag2.getOriginalName()));
		assertThat("The secondly added tag has a unique name", tag2.getName(), startsWith("TagNameUnique_20"));
	}

	@Test
	public void testTagsKeepUniqueAfterPrune() {
		tagList.add(new RtcTag("uuid").setOriginalName("v20110202").setCreationDate(YESTERDAY));
		tagList.add(new RtcTag("uuid").setOriginalName("rc11-01").setCreationDate(TODAY));
		tagList.add(new RtcTag("uuid").setOriginalName("v20100202").setCreationDate(TOMORROW));
		tagList.add(new RtcTag("uuid").setOriginalName("rc11-01").setCreationDate(TOMORROW));
		assertThat("We have two tags in the list", tagList.size(), equalTo(4));

		tagList.pruneExcludedTags(Pattern.compile("^rc.*$"));

		Iterator<RtcTag> tagListIterator = tagList.iterator();
		RtcTag tag1 = tagListIterator.next();
		RtcTag tag2 = tagListIterator.next();

		assertThat("We have two tags with unique name", tag1.getName(), not(equalTo(tag2.getName())));
		assertThat("We have two tags with the same original name", tag1.getOriginalName(),
				equalTo(tag2.getOriginalName()));
		assertThat("The secondly added tag has a unique name", tag2.getName(), startsWith("rc11-01_20"));
	}

	@Test
	public void testAddMakeTagsUniqueIfCreatedateDifferInDefinedRange() {
		tagList.add(new RtcTag("uuid").setOriginalName("TagNameUnique").setCreationDate(TODAY - 10000));
		tagList.add(new RtcTag("uuid").setOriginalName("TagNameUnique").setCreationDate(TODAY - 29999));
		tagList.add(new RtcTag("uuid").setOriginalName("TagNameUnique").setCreationDate(TODAY - 10000));

		assertThat("We have only one tag in the list", tagList.size(), equalTo(1));

	}

	@Test
	public void testPruneExcludedTags() {
		tagList.add(new RtcTag("uuid").setOriginalName("includedTag").setCreationDate(Long.MAX_VALUE - 10000));
		tagList.add(new RtcTag("uuid").setOriginalName("includedSecondTag").setCreationDate(Long.MAX_VALUE - 10000));
		tagList.add(new RtcTag("uuid").setOriginalName("excludedTag").setCreationDate(Long.MAX_VALUE - 10000));
		tagList.add(new RtcTag("uuid").setOriginalName("notIncludedTag").setCreationDate(Long.MAX_VALUE - 10000));
		tagList.getHeadTag();

		assertThat("All added tags are in the list", tagList.size(), equalTo(5));

		tagList.pruneExcludedTags(Pattern.compile("^(included.*|HEAD)$"));

		assertThat(
				"Expect tag with name 'includedTag' is in the list",
				tagList.contains(new RtcTag("uuid").setOriginalName("includedTag").setCreationDate(
						Long.MAX_VALUE - 10000)), equalTo(true));
		assertThat(
				"Expect tag with name 'includedSecondTag' is in the list",
				tagList.contains(new RtcTag("uuid").setOriginalName("includedSecondTag").setCreationDate(
						Long.MAX_VALUE - 10000)), equalTo(true));
		assertThat("Expect tag with name 'HEAD' is in the list",
				tagList.contains(new RtcTag("uuid").setOriginalName("HEAD").setCreationDate(Long.MAX_VALUE)),
				equalTo(true));
		assertThat(
				"Expect tag with name 'excludedTag' is not in the list",
				tagList.contains(new RtcTag("uuid").setOriginalName("excludedTag").setCreationDate(
						Long.MAX_VALUE - 10000)), equalTo(false));
	}

	@Test
	public void testRuntimeExceptionIfTagNotFound() {
		thrown.expect(RuntimeException.class);
		tagList.getTag("itemId", "tagName", 0);
	}

	@Test
	public void testHeadTagWillNotBeTagged() {
		RtcTag headTag = tagList.getHeadTag();

		assertThat("HEAD tag will never be tagged", headTag.doCreateTag(), equalTo(false));

		assertThat("HEAD tag will never be tagged", tagList.getHeadTag().doCreateTag(), equalTo(false));
	}

	@Test
	public void testHeadTagOnlyExistOnce() {
		tagList.getHeadTag();

		assertThat("Only one tag is in the list", tagList.size(), equalTo(1));

		tagList.getHeadTag();

		assertThat("Only one tag is in the list", tagList.size(), equalTo(1));
	}
}
