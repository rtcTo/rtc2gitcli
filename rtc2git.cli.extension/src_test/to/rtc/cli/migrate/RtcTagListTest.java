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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
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
		final RtcTag inputT1 = new RtcTag("t1uuid").setOriginalName("includedTag1").setCreationDate(Long.MAX_VALUE - 40000);
		final RtcChangeSet t1cs = new RtcChangeSet("t1cs").setComponent("component");
		inputT1.add(t1cs);
		tagList.add(inputT1);
		final RtcTag inputT2 = new RtcTag("t2uuid").setOriginalName("excludedTag2").setCreationDate(Long.MAX_VALUE - 30000);
		final RtcChangeSet t2cs = new RtcChangeSet("t2cs").setComponent("component");
		inputT2.add(t2cs);
		tagList.add(inputT2);
		final RtcTag inputT3 = new RtcTag("t3uuid").setOriginalName("includedSecondTag3").setCreationDate(Long.MAX_VALUE - 20000);
		final RtcChangeSet t3cs1 = new RtcChangeSet("t3cs1").setComponent("component");
		final RtcChangeSet t3cs2 = new RtcChangeSet("t3cs2").setComponent("component");
		inputT3.add(t3cs1);
		inputT3.add(t3cs2);
		tagList.add(inputT3);
		final RtcTag inputT4 = new RtcTag("t4uuid").setOriginalName("notIncludedTag4").setCreationDate(Long.MAX_VALUE - 10000);
		final RtcChangeSet t4cs = new RtcChangeSet("t4cs").setComponent("component");
		inputT4.add(t4cs);
		tagList.add(inputT4);
		tagList.getHeadTag();
		final RtcTag expectedT1 = new RtcTag("t1uuid").setOriginalName("includedTag1").setCreationDate(Long.MAX_VALUE - 40000);
		final RtcTag expectedT2 = new RtcTag("t2uuid").setOriginalName("excludedTag2").setCreationDate(Long.MAX_VALUE - 30000);
		final RtcTag expectedT3 = new RtcTag("t3uuid").setOriginalName("includedSecondTag3").setCreationDate(Long.MAX_VALUE - 20000);
		final RtcTag expectedT4 = new RtcTag("t4uuid").setOriginalName("notIncludedTag4").setCreationDate(Long.MAX_VALUE - 10000);
		final RtcTag expectedHead = new RtcTag("ignored").setOriginalName("HEAD").setCreationDate(Long.MAX_VALUE);

		assertThat("All added tags are in the list", tagList.size(), equalTo(5));

		tagList.pruneExcludedTags(Pattern.compile("^(included.*|HEAD)$"));

		testTagList(expectedT1, true, t1cs);
		testTagList(expectedT3, true, t2cs, t3cs1, t3cs2);
		testTagList(expectedHead, true, t4cs);
		testTagList(expectedT2, false);
		testTagList(expectedT4, false);
	}

	private void testTagList(RtcTag t, boolean isExpectedToBeInTheList, RtcChangeSet... expectedChangeSets) {
		final String msg = "Expect tag with name '"+t.getOriginalName()+"' is "+(isExpectedToBeInTheList?"":"not ")+"in the list";
		final Boolean actuallyIsInList = tagList.contains(t);
		assertThat(msg, actuallyIsInList, equalTo(isExpectedToBeInTheList));
		if( isExpectedToBeInTheList ) {
			final RtcTag actualTagInList = tagList.getTag(t.getUuid(), t.getOriginalName(), t.getCreationDate());
			final List<RtcChangeSet> expectedChangeSetsAsList = new ArrayList<RtcChangeSet>(Arrays.asList(expectedChangeSets));
			final List<RtcChangeSet> actualChangeSetsAsList = new ArrayList<RtcChangeSet>();
			for( List<RtcChangeSet> actualCss : actualTagInList.getComponentsChangeSets().values()) {
				actualChangeSetsAsList.addAll(actualCss);
			}
			assertThat("Expecting tag with name '"+t.getOriginalName()+"' ChangeSets to contain", actualChangeSetsAsList, equalTo(expectedChangeSetsAsList));
		}
	}

	@Test
	public void testRuntimeExceptionIfTagNotFound() {
		thrown.expect(RuntimeException.class);
		tagList.getTag("itemId", "tagName", 0);
	}

	@Test
	public void testHeadTagWillNotBeTagged() {
		RtcTag headTag = tagList.getHeadTag();
		tagList.pruneInactiveTags();

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

    @Test
    public void testSortByCreationDateGivenTagsOfOneComponentThenSortsByChangesetOrder() {
        final String component = "component";
        final RtcTag inputT1 = new RtcTag("zt1uuid").setOriginalName("zt1").setCreationDate(1000);
        final RtcChangeSet inputCS1 = new RtcChangeSet("zt1cs").setComponent(component).setHistoryOrderIndex(1L);
        inputT1.add(inputCS1);
        final RtcTag inputT2 = new RtcTag("xt2uuid").setOriginalName("x2").setCreationDate(12000);
        final RtcChangeSet inputCS2 = new RtcChangeSet("xt2cs").setComponent(component).setHistoryOrderIndex(2L);
        inputT2.add(inputCS2);
        final RtcTag inputT3 = new RtcTag("y3uuid").setOriginalName("y3").setCreationDate(3000);
        final RtcChangeSet inputCS3 = new RtcChangeSet("y3cs").setComponent(component).setHistoryOrderIndex(3L);
        inputT3.add(inputCS3);
        tagList.add(inputT1);
        tagList.add(inputT3);
        tagList.add(inputT2); // put them in out of order
        final List<RtcTag> expected = toList(inputT1, inputT2, inputT3);

        tagList.sortByCreationDate();
        final List<RtcTag> actual = toList(tagList.iterator());

        assertThat("Tags", actual, equalTo(expected));
    }

    @Test
    public void testSortByCreationDateGivenTagsWithNoComponentInCommonThenSortsByDateOrder() {
        final String component1 = "component1";
        final String component2 = "componentTwo";
        final String component3 = "component3";
        final RtcTag inputT1 = new RtcTag("zt1uuid").setOriginalName("zt1").setCreationDate(1000);
        final RtcChangeSet inputCS1 = new RtcChangeSet("zt1cs").setComponent(component1).setHistoryOrderIndex(1L);
        inputT1.add(inputCS1);
        final RtcTag inputT2 = new RtcTag("xt2uuid").setOriginalName("x2").setCreationDate(2000);
        final RtcChangeSet inputCS2 = new RtcChangeSet("xt2cs").setComponent(component2).setHistoryOrderIndex(3L);
        inputT2.add(inputCS2);
        final RtcTag inputT3 = new RtcTag("y3uuid").setOriginalName("y3").setCreationDate(3000);
        final RtcChangeSet inputCS3 = new RtcChangeSet("y3cs").setComponent(component3).setHistoryOrderIndex(2L);
        inputT3.add(inputCS3);
        tagList.add(inputT1);
        tagList.add(inputT3);
        tagList.add(inputT2); // put them in out of order
        final List<RtcTag> expected = toList(inputT1, inputT2, inputT3);

        tagList.sortByCreationDate();
        final List<RtcTag> actual = toList(tagList.iterator());

        assertThat("Tags", actual, equalTo(expected));
    }

    @Test
    public void testSortByCreationDateGivenMixedScenarioThenSortsByChangesetAndDateOrder() {
        final String component1 = "component1";
        final String component2 = "component2";
        // 6 tags
        final RtcTag inputT1 = new RtcTag("uuid1").setOriginalName("1t").setCreationDate(200);
        final RtcTag inputT2 = new RtcTag("uuid2").setOriginalName("t2").setCreationDate(600);
        final RtcTag inputT3 = new RtcTag("u3uid").setOriginalName("t3").setCreationDate(200);
        final RtcTag inputT4 = new RtcTag("uu4id").setOriginalName("t4").setCreationDate(500);
        final RtcTag inputT5 = new RtcTag("5uuid").setOriginalName("t5").setCreationDate(400);
        final RtcTag inputT6 = new RtcTag("uuid6").setOriginalName("6t").setCreationDate(900);
        // tag1 has nothing
        // tag2 has both components
        inputT2.add(new RtcChangeSet("c1cs1").setComponent(component1).setHistoryOrderIndex(101L));
        inputT2.add(new RtcChangeSet("c1cs2").setComponent(component1).setHistoryOrderIndex(102L));
        inputT2.add(new RtcChangeSet("c2cs1").setComponent(component2).setHistoryOrderIndex(201L));
        inputT2.add(new RtcChangeSet("c2cs2").setComponent(component2).setHistoryOrderIndex(202L));
        // tag3 uses one
        inputT3.add(new RtcChangeSet("c1cs3").setComponent(component1).setHistoryOrderIndex(103L));
        inputT3.add(new RtcChangeSet("c1cs4").setComponent(component1).setHistoryOrderIndex(104L));
        // tag4 uses both
        inputT4.add(new RtcChangeSet("c1cs5").setComponent(component1).setHistoryOrderIndex(105L));
        inputT4.add(new RtcChangeSet("c1cs6").setComponent(component1).setHistoryOrderIndex(106L));
        inputT4.add(new RtcChangeSet("c2cs3").setComponent(component2).setHistoryOrderIndex(203L));
        inputT4.add(new RtcChangeSet("c2cs4").setComponent(component2).setHistoryOrderIndex(204L));
        // tag5 uses the other
        inputT5.add(new RtcChangeSet("c2cs5").setComponent(component2).setHistoryOrderIndex(205L));
        inputT5.add(new RtcChangeSet("c2cs6").setComponent(component2).setHistoryOrderIndex(206L));
        // tag6 has nothing
        final List<RtcTag> expected = toList(inputT1, inputT2, inputT3, inputT4, inputT5, inputT6);
        // add in wrong order to prevent false-positive
        tagList.add(inputT5);
        tagList.add(inputT4);
        tagList.add(inputT3);
        tagList.add(inputT2);
        tagList.add(inputT1);
        tagList.add(inputT6);

        tagList.sortByCreationDate();
        final List<RtcTag> actual = toList(tagList.iterator());

        assertThat("Tags", actual, equalTo(expected));
    }

    @Test
    public void testSortByCreationDateGivenTagsWithInconsistentTagHistoryThenThrows() {
        final String component = "component";
        final RtcChangeSet inputCS1 = new RtcChangeSet("cs1").setComponent(component).setHistoryOrderIndex(1L);
        final RtcChangeSet inputCS2 = new RtcChangeSet("cs2").setComponent(component).setHistoryOrderIndex(2L);
        final RtcChangeSet inputCS3 = new RtcChangeSet("cs3").setComponent(component).setHistoryOrderIndex(3L);
        final RtcTag inputT1 = new RtcTag("t1uuid").setOriginalName("t1").setCreationDate(1000);
        final RtcTag inputT2 = new RtcTag("t2uuid").setOriginalName("t2").setCreationDate(12000);
        inputT1.add(inputCS1);
        inputT1.add(inputCS2);
        inputT1.add(inputCS3);
        inputT2.add(inputCS2);
        tagList.add(inputT1);
        tagList.add(inputT2);
        thrown.expect(IllegalStateException.class);

        tagList.sortByCreationDate();
    }

    private static final <T> List<T> toList(Iterator<? extends T> i) {
        final List<T> result = new ArrayList<T>();
        while (i.hasNext()) {
            result.add(i.next());
        }
        return result;
    }

    @SafeVarargs
    private static final <T> List<T> toList(T... elements) {
        final List<T> result = new ArrayList<T>();
        for (final T e : elements) {
            result.add(e);
        }
        return result;
    }
}
