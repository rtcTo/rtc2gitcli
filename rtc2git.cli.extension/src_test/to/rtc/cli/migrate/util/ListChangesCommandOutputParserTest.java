package to.rtc.cli.migrate.util;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.junit.Test;

import to.rtc.cli.migrate.ChangeSet.WorkItem;
import to.rtc.cli.migrate.RtcChangeSet;

public class ListChangesCommandOutputParserTest {
	private static final RtcChangeSet CS1;
	static {
		CS1 = new RtcChangeSet("_4qJdQC2nEeihzYB7AIhDkw");
		CS1.setComponent("Test_CM Main Component");
		final Calendar d = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		d.setTimeInMillis(0);
		d.set(2018, 2, 22, 9, 6, 0);
		CS1.setCreationDate(d.getTimeInMillis());
		CS1.setCreatorEMail("Doe, John (John)");
		CS1.setCreatorName("Doe, John (John)");
		CS1.setServerURI("https://rtc.mydomain.com:9443/ccm/");
		CS1.setStreamId(null);
		CS1.setText("main - test1.txt - line2");
		CS1.addWorkItem(8020, "Define permissions");
	}
	private static final RtcChangeSet CS2;
	static {
		CS2 = new RtcChangeSet("_aZvrwC2lEeihzYB7AIhDkw");
		CS2.setComponent("Test_CM Main Component");
		final Calendar d = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		d.setTimeInMillis(0);
		d.set(2018, 2, 22, 8, 48, 0);
		CS2.setCreationDate(d.getTimeInMillis());
		CS2.setCreatorEMail("Doe, John (John)");
		CS2.setCreatorName("Doe, John (John)");
		CS2.setServerURI("https://rtc.mydomain.com:9443/ccm/");
		CS2.setStreamId(null);
		CS2.setText("Share");
	}
	private static String JSON0 = "{\n" + 
			"    \"changes\": [\n" + 
			"    ]\n" + 
			"}\n" + 
			"";
	private static final List<RtcChangeSet> PARSED0 = Arrays.asList();
	private static String JSON1 = "{\n" + 
			"    \"changes\": [\n" + 
			"        {\n" + 
			"            \"author\": \"Doe, John (John)\",\n" + 
			"            \"changes\": [\n" + 
			"                {\n" + 
			"                    \"before-state\": \"_adduJC2lEeihzYB7AIhDkw\",\n" + 
			"                    \"inaccessible-change\": false,\n" + 
			"                    \"merges\": [\n" + 
			"                    ],\n" + 
			"                    \"path\": \"\\\\<unresolved>\\\\test1.txt\",\n" + 
			"                    \"state\": {\n" + 
			"                        \"add\": false,\n" + 
			"                        \"conflict\": false,\n" + 
			"                        \"content_change\": true,\n" + 
			"                        \"delete\": false,\n" + 
			"                        \"move\": false,\n" + 
			"                        \"potential_conflict\": false,\n" + 
			"                        \"property_change\": false\n" + 
			"                    },\n" + 
			"                    \"state-id\": \"_4sMrtC2nEeihzYB7AIhDkw\",\n" + 
			"                    \"uuid\": \"_a5TVkC2lEei5SqrKaOjVUA\"\n" + 
			"                }\n" + 
			"            ],\n" + 
			"            \"comment\": \"main - test1.txt - line2\",\n" + 
			"            \"component\": {\n" + 
			"                \"name\": \"Test_CM Main Component\",\n" + 
			"                \"url\": \"https:\\/\\/rtc.mydomain.com:9443\\/ccm\\/\",\n" + 
			"                \"uuid\": \"_HXHk0C2kEeihzYB7AIhDkw\"\n" + 
			"            },\n" + 
			"            \"modified\": \"22-Mar-2018 09:06 AM\",\n" + 
			"            \"state\": {\n" + 
			"                \"active\": false,\n" + 
			"                \"complete\": true,\n" + 
			"                \"conflict\": false,\n" + 
			"                \"has_source\": false,\n" + 
			"                \"is_linked\": false,\n" + 
			"                \"is_source\": false,\n" + 
			"                \"potential_conflict\": false\n" + 
			"            },\n" + 
			"            \"url\": \"https:\\/\\/rtc.mydomain.com:9443\\/ccm\\/\",\n" + 
			"            \"uuid\": \"_4qJdQC2nEeihzYB7AIhDkw\",\n" + 
			"            \"workitems\": [\n" + 
			"                {\n" + 
			"                    \"id\": 8020,\n" + 
			"                    \"uuid\": \"_NLGmUHYmEeSL0Yi_JkggMQ\",\n" + 
			"                    \"workitem-label\": \"Define permissions\",\n" + 
			"                    \"workitem-number\": 8020\n" + 
			"                }\n" + 
			"            ]\n" + 
			"        }\n" + 
			"    ]\n" + 
			"}\n" + 
			"";
	private static final List<RtcChangeSet> PARSED1 = Arrays.asList(CS1);
	private static String JSON2 = "{\n" + 
			"    \"changes\": [\n" + 
			"        {\n" + 
			"            \"author\": \"Doe, John (John)\",\n" + 
			"            \"changes\": [\n" + 
			"                {\n" + 
			"                    \"inaccessible-change\": false,\n" + 
			"                    \"merges\": [\n" + 
			"                    ],\n" + 
			"                    \"path\": \"\\\\Test\\\\.project\",\n" + 
			"                    \"state\": {\n" + 
			"                        \"add\": true,\n" + 
			"                        \"conflict\": false,\n" + 
			"                        \"content_change\": false,\n" + 
			"                        \"delete\": false,\n" + 
			"                        \"move\": false,\n" + 
			"                        \"potential_conflict\": false,\n" + 
			"                        \"property_change\": false\n" + 
			"                    },\n" + 
			"                    \"state-id\": \"_adduLi2lEeihzYB7AIhDkw\",\n" + 
			"                    \"uuid\": \"_a5PrMC2lEei5SqrKaOjVUA\"\n" + 
			"                },\n" + 
			"                {\n" + 
			"                    \"inaccessible-change\": false,\n" + 
			"                    \"merges\": [\n" + 
			"                    ],\n" + 
			"                    \"path\": \"\\\\Test\\\\folder1\\\\test1.txt\",\n" + 
			"                    \"state\": {\n" + 
			"                        \"add\": true,\n" + 
			"                        \"conflict\": false,\n" + 
			"                        \"content_change\": false,\n" + 
			"                        \"delete\": false,\n" + 
			"                        \"move\": false,\n" + 
			"                        \"potential_conflict\": false,\n" + 
			"                        \"property_change\": false\n" + 
			"                    },\n" + 
			"                    \"state-id\": \"_adduJC2lEeihzYB7AIhDkw\",\n" + 
			"                    \"uuid\": \"_a5TVkC2lEei5SqrKaOjVUA\"\n" + 
			"                }\n" + 
			"            ],\n" + 
			"            \"comment\": \"Share\",\n" + 
			"            \"component\": {\n" + 
			"                \"name\": \"Test_CM Main Component\",\n" + 
			"                \"url\": \"https:\\/\\/rtc.mydomain.com:9443\\/ccm\\/\",\n" + 
			"                \"uuid\": \"_HXHk0C2kEeihzYB7AIhDkw\"\n" + 
			"            },\n" + 
			"            \"modified\": \"22-Mar-2018 08:48 AM\",\n" + 
			"            \"state\": {\n" + 
			"                \"active\": false,\n" + 
			"                \"complete\": true,\n" + 
			"                \"conflict\": false,\n" + 
			"                \"has_source\": false,\n" + 
			"                \"is_linked\": false,\n" + 
			"                \"is_source\": false,\n" + 
			"                \"potential_conflict\": false\n" + 
			"            },\n" + 
			"            \"url\": \"https:\\/\\/rtc.mydomain.com:9443\\/ccm\\/\",\n" + 
			"            \"uuid\": \"_aZvrwC2lEeihzYB7AIhDkw\"\n" + 
			"        },\n" + 
			"        {\n" + 
			"            \"author\": \"Doe, John (John)\",\n" + 
			"            \"changes\": [\n" + 
			"                {\n" + 
			"                    \"before-state\": \"_adduJC2lEeihzYB7AIhDkw\",\n" + 
			"                    \"inaccessible-change\": false,\n" + 
			"                    \"merges\": [\n" + 
			"                    ],\n" + 
			"                    \"path\": \"\\\\<unresolved>\\\\test1.txt\",\n" + 
			"                    \"state\": {\n" + 
			"                        \"add\": false,\n" + 
			"                        \"conflict\": false,\n" + 
			"                        \"content_change\": true,\n" + 
			"                        \"delete\": false,\n" + 
			"                        \"move\": false,\n" + 
			"                        \"potential_conflict\": false,\n" + 
			"                        \"property_change\": false\n" + 
			"                    },\n" + 
			"                    \"state-id\": \"_4sMrtC2nEeihzYB7AIhDkw\",\n" + 
			"                    \"uuid\": \"_a5TVkC2lEei5SqrKaOjVUA\"\n" + 
			"                }\n" + 
			"            ],\n" + 
			"            \"comment\": \"main - test1.txt - line2\",\n" + 
			"            \"component\": {\n" + 
			"                \"name\": \"Test_CM Main Component\",\n" + 
			"                \"url\": \"https:\\/\\/rtc.mydomain.com:9443\\/ccm\\/\",\n" + 
			"                \"uuid\": \"_HXHk0C2kEeihzYB7AIhDkw\"\n" + 
			"            },\n" + 
			"            \"modified\": \"22-Mar-2018 09:06 AM\",\n" + 
			"            \"state\": {\n" + 
			"                \"active\": false,\n" + 
			"                \"complete\": true,\n" + 
			"                \"conflict\": false,\n" + 
			"                \"has_source\": false,\n" + 
			"                \"is_linked\": false,\n" + 
			"                \"is_source\": false,\n" + 
			"                \"potential_conflict\": false\n" + 
			"            },\n" + 
			"            \"url\": \"https:\\/\\/rtc.mydomain.com:9443\\/ccm\\/\",\n" + 
			"            \"uuid\": \"_4qJdQC2nEeihzYB7AIhDkw\",\n" + 
			"            \"workitems\": [\n" + 
			"                {\n" + 
			"                    \"id\": 8020,\n" + 
			"                    \"uuid\": \"_NLGmUHYmEeSL0Yi_JkggMQ\",\n" + 
			"                    \"workitem-label\": \"Define permissions\",\n" + 
			"                    \"workitem-number\": 8020\n" + 
			"                }\n" + 
			"            ]\n" + 
			"        }\n" + 
			"    ]\n" + 
			"}\n" + 
			"";
	private static final List<RtcChangeSet> PARSED2 = Arrays.asList(CS2, CS1);

	@Test
	public void parseNothing() throws Exception {
		// Given
		final List<String> toBeParsed = Arrays.asList(JSON0.split("\n"));
		final List<RtcChangeSet> expected = PARSED0;

		// When
		final ListChangesCommandOutputParser instance = new ListChangesCommandOutputParser(toBeParsed);
		final List<RtcChangeSet> actual = instance.getChangeSets();

		// Then
		testEqual(actual, expected);
	}

	@Test
	public void parseCSWithOneWorkItem() throws Exception {
		// Given
		final List<String> toBeParsed = Arrays.asList(JSON1.split("\n"));
		final List<RtcChangeSet> expected = PARSED1;

		// When
		final ListChangesCommandOutputParser instance = new ListChangesCommandOutputParser(toBeParsed);
		final List<RtcChangeSet> actual = instance.getChangeSets();

		// Then
		testEqual(actual, expected);
	}

	@Test
	public void parseTwoCSs() throws Exception {
		// Given
		final List<String> toBeParsed = Arrays.asList(JSON2.split("\n"));
		final List<RtcChangeSet> expected = PARSED2;

		// When
		final ListChangesCommandOutputParser instance = new ListChangesCommandOutputParser(toBeParsed);
		final List<RtcChangeSet> actual = instance.getChangeSets();

		// Then
		testEqual(actual, expected);
	}

	private static void testEqual(List<RtcChangeSet> actual, List<RtcChangeSet> expected) {
		assertEquals("ChangeSet list length", expected.size(), actual.size());
		for (int i = 0; i < expected.size(); i++) {
			final RtcChangeSet a = actual.get(i);
			final RtcChangeSet e = expected.get(i);
			assertEquals("ChangeSet UUID", e.getChangesetID(), a.getChangesetID());
			assertEquals("ChangeSet Component Name", e.getComponent(), a.getComponent());
			final long ed = e.getCreationDate();
			final long ad = a.getCreationDate();
			assertEquals("ChangeSet Modified Date (expected "+new Date(ed)+", actual " + new Date(ad)+")", ed, ad);
			assertEquals("ChangeSet Author Email", e.getEmailAddress(), a.getEmailAddress());
			assertEquals("ChangeSet Author Name", e.getCreatorName(), a.getCreatorName());
			assertEquals("ChangeSet Server URL", e.getServerURI(), a.getServerURI());
			final List<WorkItem> ew = e.getWorkItems();
			final List<WorkItem> aw = a.getWorkItems();
			testWorkItemsEqual(aw, ew);
		}
	}

	private static void testWorkItemsEqual(List<WorkItem> actual, List<WorkItem> expected) {
		assertEquals("WorkItems list length", expected.size(), actual.size());
		for (int i = 0; i < expected.size(); i++) {
			final WorkItem a = actual.get(i);
			final WorkItem e = expected.get(i);
			assertEquals("WorkItems Number", e.getNumber(), a.getNumber());
			assertEquals("WorkItems Text", e.getText(), a.getText());
		}
	}
}
