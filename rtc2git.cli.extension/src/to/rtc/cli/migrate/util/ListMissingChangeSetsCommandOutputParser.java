package to.rtc.cli.migrate.util;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.e4.core.services.util.JSONObject;

public class ListMissingChangeSetsCommandOutputParser {

	private final List<String> missingChangeSetUuids;

	public ListMissingChangeSetsCommandOutputParser(final List<String> toBeParsed) {
		this.missingChangeSetUuids = calcMissingChangeSets(toBeParsed);
	}

	private static List<String> calcMissingChangeSets(final List<String> toBeParsed) {
		final List<String> result = new ArrayList<String>();
		final JSONObject json = JSONParser.parseJSON(toBeParsed);
		final JSONObject[] changes = json.getObjects("changes");
		for( final JSONObject change : changes) {
			final String uuid = change.getString("uuid");
			result.add(uuid);
		}
		return result;
	}

	// Expecting JSON output of the form
	// {
	//     "changes": [
	//     ]
	// }
	// or of the form:
	// {
	//     "changes": [
	//         {
	//             "author": "McAuley, Martin (Martin)",
	//             "comment": "Share",
	//             "modified": "22-Mar-2018 08:48 AM",
	//             "state": {
	//                 "active": false,
	//                 "complete": true,
	//                 "conflict": false,
	//                 "current": false,
	//                 "current_merge_target": false,
	//                 "has_source": false,
	//                 "is_linked": false,
	//                 "is_source": false,
	//                 "potential_conflict": false
	//             },
	//             "url": "https:\/\/jazzc04.hursley.ibm.com:9443\/ccm\/",
	//             "uuid": "_aZvrwC2lEeihzYB7AIhDkw"
	//         },
	// ...
	//         {
	//             "author": "McAuley, Martin (Martin)",
	//             "comment": "main - test1.txt - line2",
	//             "modified": "22-Mar-2018 09:06 AM",
	//             "state": {
	//                 "active": false,
	//                 "complete": true,
	//                 "conflict": false,
	//                 "current": false,
	//                 "current_merge_target": false,
	//                 "has_source": false,
	//                 "is_linked": false,
	//                 "is_source": false,
	//                 "potential_conflict": false
	//             },
	//             "url": "https:\/\/jazzc04.hursley.ibm.com:9443\/ccm\/",
	//             "uuid": "_4qJdQC2nEeihzYB7AIhDkw"
	//         }
	//     ]
	// }

	public List<String> getMissingChangeSetUuids() {
		return missingChangeSetUuids;
	}
}
