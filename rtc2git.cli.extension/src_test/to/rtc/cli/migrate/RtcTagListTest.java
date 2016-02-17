/**
 * File Name: CommitCommentTranslatorTest.java
 *
 * Copyright (c) 2016 BISON Schweiz AG, All Rights Reserved.
 */

package to.rtc.cli.migrate;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
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
	public void testKeepOnlyActiveTags() {
		RtcTag tag1 = new RtcTag("uuid").setOriginalName("TagName_1").setCreationDate(TODAY - 7000);
		RtcTag tag2 = new RtcTag("uuid").setOriginalName("TagName_2").setCreationDate(TODAY - 6000);
		RtcTag tag3 = new RtcTag("uuid").setOriginalName("TagName_3").setCreationDate(TODAY - 5000)
				.setContainLastChangeset(true);
		RtcTag tag4 = new RtcTag("uuid").setOriginalName("TagName_4").setCreationDate(TODAY - 4000);
		RtcTag tag5 = new RtcTag("uuid").setOriginalName("TagName_5").setCreationDate(TODAY - 3000)
				.setContainLastChangeset(true);
		RtcTag tag6 = new RtcTag("uuid").setOriginalName("TagName_6").setCreationDate(TODAY - 2000);
		RtcTag tag7 = new RtcTag("uuid").setOriginalName("TagName_7").setCreationDate(TODAY - 1000);
		tagList.add(tag1);
		tagList.add(tag2);
		tagList.add(tag3);
		tagList.add(tag4);
		tagList.add(tag5);
		tagList.add(tag6);
		tagList.add(tag7);

		tagList.sortByCreationDate();
		tagList.pruneInactiveTags();

		assertThat("We tag only active tags in the list", tagList.size(), equalTo(7));
		assertThat("Tag 1 will be tagged",
				tagList.getTag(tag1.getName(), tag1.getOriginalName(), tag1.getCreationDate()).doCreateTag(), is(true));
		assertThat("Tag 2 will be tagged",
				tagList.getTag(tag2.getName(), tag2.getOriginalName(), tag2.getCreationDate()).doCreateTag(), is(true));
		assertThat("Tag 3 will be tagged",
				tagList.getTag(tag3.getName(), tag3.getOriginalName(), tag3.getCreationDate()).doCreateTag(), is(true));
		assertThat("Tag 4 will be tagged",
				tagList.getTag(tag4.getName(), tag4.getOriginalName(), tag4.getCreationDate()).doCreateTag(), is(true));
		assertThat("Tag 5 will be tagged",
				tagList.getTag(tag5.getName(), tag5.getOriginalName(), tag5.getCreationDate()).doCreateTag(), is(true));
		assertThat("Tag 6 will NOT be tagged",
				tagList.getTag(tag6.getName(), tag6.getOriginalName(), tag6.getCreationDate()).doCreateTag(), is(false));
		assertThat("Tag 7 will NOT be tagged",
				tagList.getTag(tag7.getName(), tag7.getOriginalName(), tag7.getCreationDate()).doCreateTag(), is(false));

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
