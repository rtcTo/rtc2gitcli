package to.rtc.cli.migrate.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.eclipse.e4.core.services.util.JSONObject;

import to.rtc.cli.migrate.RtcChangeSet;

public class ListChangesCommandOutputParser {

	private final List<RtcChangeSet> changeSets;

	public ListChangesCommandOutputParser(final List<String> toBeParsed) {
		try {
			this.changeSets = calcChangeSets(toBeParsed);
		} catch (ParseException e) {
			throw new IllegalArgumentException("Data " + toBeParsed + " was invalid", e);
		}
	}

	// RTC reported date-times as e.g. "22-Mar-2018 09:06 AM"
	private static final SimpleDateFormat DATEFORMAT = new SimpleDateFormat("dd-MMM-yyyy hh:mm aa");
	private static List<RtcChangeSet> calcChangeSets(final List<String> toBeParsed) throws ParseException {
		final List<RtcChangeSet> result = new ArrayList<RtcChangeSet>();
		final JSONObject json = JSONParser.parseJSON(toBeParsed);
		final JSONObject[] changes = json.getObjects("changes");
		for( JSONObject change : changes ) {
			final String author = change.getString("author");
			final String comment = change.getString("comment");
			final JSONObject componentSection = change.getObject("component");
			final String componentName = componentSection.getString("name");
			final String modified = change.getString("modified");
			final String url = change.getString("url");
			final String uuid = change.getString("uuid");
			final Date parsedDate = DATEFORMAT.parse(modified);
			final long creationDate = parsedDate.getTime();
			final String streamUuid = null; // ignored, we hope
			final String emailAddress = author; // hopefully not validated
			final RtcChangeSet changeSet = mkCS(uuid, componentName, creationDate , emailAddress , author, url, streamUuid , comment);
			final JSONObject[] workitemsSection = JSONParser.getObjectsOrEmpty(change, "workitems");
			for( final JSONObject workItem : workitemsSection) {
				final String workitemLabel = workItem.getString("workitem-label");
				final Map<String, Object> workItemMap = JSONParser.getMap(workItem);
				final long workitemNumber = JSONParser.getLong(workItemMap, "workitem-number");
				changeSet.addWorkItem(workitemNumber , workitemLabel);
			}
			result.add(changeSet);
		}
		return result;
	}

	private static RtcChangeSet mkCS(String changeSetUuid, String component, long creationDate, String emailAddress,
			String creatorName, String serverUri, String streamUuid, String entryName) {
		final RtcChangeSet cs = new RtcChangeSet(changeSetUuid);
		cs.setComponent(component);
		cs.setCreationDate(creationDate);
		cs.setCreatorEMail(emailAddress);
		cs.setCreatorName(creatorName);
		cs.setServerURI(serverUri);
		cs.setStreamId(streamUuid);
		cs.setText(entryName);
		return cs;
	}

	// Expecting JSON output of the form
	// {
	//     "changes": [
	//         {
	//             "author": "Doe, John (John)",
	//             "changes": [
	//                 {
	//                     "before-state": "_adduJC2lEeihzYB7AIhDkw",
	//                     "inaccessible-change": false,
	//                     "merges": [
	//                     ],
	//                     "path": "\\<unresolved>\\test1.txt",
	//                     "state": {
	//                         "add": false,
	//                         "conflict": false,
	//                         "content_change": true,
	//                         "delete": false,
	//                         "move": false,
	//                         "potential_conflict": false,
	//                         "property_change": false
	//                     },
	//                     "state-id": "_4sMrtC2nEeihzYB7AIhDkw",
	//                     "uuid": "_a5TVkC2lEei5SqrKaOjVUA"
	//                 }
	//             ],
	//             "comment": "main - test1.txt - line2",
	//             "component": {
	//                 "name": "Test_CM Main Component",
	//                 "url": "https:\/\/rtc.mydomain.com:9443\/ccm\/",
	//                 "uuid": "_HXHk0C2kEeihzYB7AIhDkw"
	//             },
	//             "modified": "22-Mar-2018 09:06 AM",
	//             "state": {
	//                 "active": false,
	//                 "complete": true,
	//                 "conflict": false,
	//                 "has_source": false,
	//                 "is_linked": false,
	//                 "is_source": false,
	//                 "potential_conflict": false
	//             },
	//             "url": "https:\/\/rtc.mydomain.ibm.com:9443\/ccm\/",
	//             "uuid": "_4qJdQC2nEeihzYB7AIhDkw",
	//             "workitems": [
	//                 {
	//                     "id": 8020,
	//                     "uuid": "_NLGmUHYmEeSL0Yi_JkggMQ",
	//                     "workitem-label": "Define permissions",
	//                     "workitem-number": 8020
	//                 }
	//             ]
	//         }
	//     ]
	// }

	public List<RtcChangeSet> getChangeSets() {
		return changeSets;
	}
}
