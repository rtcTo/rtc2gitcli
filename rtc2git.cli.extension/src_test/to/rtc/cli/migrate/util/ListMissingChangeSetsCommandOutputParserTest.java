package to.rtc.cli.migrate.util;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class ListMissingChangeSetsCommandOutputParserTest {
	private static String JSON1 = "{\n" + 
			"    \"changes\": [\n" + 
			"    ]\n" + 
			"}\n" + 
			"";
	private static final List<String> PARSED1 = Arrays.asList();

	private static String JSON2 = "{\n" + 
			"    \"changes\": [\n" + 
			"        {\n" + 
			"            \"author\": \"Doe, John (John)\",\n" + 
			"            \"comment\": \"Share\",\n" + 
			"            \"modified\": \"22-Mar-2018 08:48 AM\",\n" + 
			"            \"state\": {\n" + 
			"                \"active\": false,\n" + 
			"                \"complete\": true,\n" + 
			"                \"conflict\": false,\n" + 
			"                \"current\": false,\n" + 
			"                \"current_merge_target\": false,\n" + 
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
			"            \"comment\": \"main - test1.txt - line2\",\n" + 
			"            \"modified\": \"22-Mar-2018 09:06 AM\",\n" + 
			"            \"state\": {\n" + 
			"                \"active\": false,\n" + 
			"                \"complete\": true,\n" + 
			"                \"conflict\": false,\n" + 
			"                \"current\": false,\n" + 
			"                \"current_merge_target\": false,\n" + 
			"                \"has_source\": false,\n" + 
			"                \"is_linked\": false,\n" + 
			"                \"is_source\": false,\n" + 
			"                \"potential_conflict\": false\n" + 
			"            },\n" + 
			"            \"url\": \"https:\\/\\/rtc.mydomain.com:9443\\/ccm\\/\",\n" + 
			"            \"uuid\": \"_4qJdQC2nEeihzYB7AIhDkw\"\n" + 
			"        }\n" + 
			"    ]\n" + 
			"}\n" + 
			"";
	private static final List<String> PARSED2 = Arrays.asList("_aZvrwC2lEeihzYB7AIhDkw", "_4qJdQC2nEeihzYB7AIhDkw");

	@Test
	public void parseNothingMissing() throws Exception {
		// Given
		final List<String> toBeParsed = Arrays.asList(JSON1.split("\n"));
		final List<String> expected = PARSED1;

		// When
		final ListMissingChangeSetsCommandOutputParser instance = new ListMissingChangeSetsCommandOutputParser(toBeParsed);
		final List<String> actual = instance.getMissingChangeSetUuids();

		// Then
		assertEquals(actual, expected);
	}

	@Test
	public void parseTwoMissing() throws Exception {
		// Given
		final List<String> toBeParsed = Arrays.asList(JSON2.split("\n"));
		final List<String> expected = PARSED2;

		// When
		final ListMissingChangeSetsCommandOutputParser instance = new ListMissingChangeSetsCommandOutputParser(toBeParsed);
		final List<String> actual = instance.getMissingChangeSetUuids();

		// Then
		assertEquals(actual, expected);
	}
}
