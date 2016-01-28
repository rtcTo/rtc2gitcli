/**
 *
 */
package to.rtc.cli.migrate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RtcTagList implements Iterable<RtcTag> {

	private List<RtcTag> rtcTags;

	public RtcTagList() {
		rtcTags = new ArrayList<RtcTag>();
	}

	public RtcTag add(RtcTag tag) {
		long creationDate = tag.getCreationDate();
		if (rtcTags.contains(tag)) {
			tag = rtcTags.get(rtcTags.indexOf(tag));
			if (tag.getCreationDate() > creationDate) {
				tag.setCreationDate(creationDate / 2 + tag.getCreationDate() / 2);
			}
		} else {
			for (RtcTag tagToCheck : rtcTags) {
				if (tagToCheck.getOriginalName().equals(tag.getOriginalName())) {
					tag.makeNameUnique();
					break;
				}
			}
			rtcTags.add(tag);
		}
		return tag;
	}

	public void printTagList(StreamOutput output) {
		output.writeLine("********** BASELINE INFOS **********");
		int totalChangeSets = 0;
		for (RtcTag tag : rtcTags) {
			int totalChangeSetsByBaseline = tag.getOrderedChangeSets().size();
			totalChangeSets += totalChangeSetsByBaseline;
			output.writeLine("  Baseline [" + tag.getName() + "] with original name [" + tag.getOriginalName()
					+ "] created at [" + (new Date(tag.getCreationDate())) + "] total number of changesets ["
					+ totalChangeSetsByBaseline + "] will be tagged [" + tag.doCreateTag() + "]");
			for (Entry<String, List<RtcChangeSet>> entry : tag.getComponentsChangeSets().entrySet()) {
				output.writeLine("      number of changesets  for component [" + entry.getKey() + "] is ["
						+ entry.getValue().size() + "]");
			}
		}
		output.writeLine("TOTAL NUMBER OF CHANGESETS [" + totalChangeSets + "]");
		output.writeLine("********** BASELINE INFOS **********");
	}

	public void pruneExcludedTags(Pattern includePattern) {
		List<RtcTag> prunedList = new ArrayList<RtcTag>();
		RtcTag tmpTag = null;

		for (RtcTag currentTag : rtcTags) {
			if (tmpTag == null) {
				tmpTag = currentTag;
			} else {
				tmpTag.setUuid(currentTag.getUuid()).setOriginalName(currentTag.getOriginalName())
						.setCreationDate(currentTag.getCreationDate());
				tmpTag.addAll(currentTag.getComponentsChangeSets());
			}

			Matcher matcher = includePattern.matcher(tmpTag.getOriginalName());
			if (matcher.matches()) {
				prunedList.add(tmpTag);
				tmpTag = null;
			}
		}

		if (tmpTag != null) {
			prunedList.add(tmpTag);
		}
		rtcTags = prunedList;
	}

	public void sortByCreationDate() {
		Collections.sort(rtcTags, new TagCreationDateComparator());
	}

	public int size() {
		return rtcTags.size();
	}

	@Override
	public Iterator<RtcTag> iterator() {
		return rtcTags.iterator();
	}

	public RtcTag getTag(String itemId, String tagName, long creationDate) {
		RtcTag tag = new RtcTag(itemId).setOriginalName(tagName).setCreationDate(creationDate);
		if (rtcTags.contains(tag)) {
			tag = rtcTags.get(rtcTags.indexOf(tag));
		} else {
			throw new RuntimeException("Tag not found");
		}
		return tag;
	}

	public RtcTag getHeadTag() {
		RtcTag tag = new RtcTag(null).setDoCreateTag(false).setOriginalName("HEAD").setCreationDate(Long.MAX_VALUE);
		if (!rtcTags.contains(tag)) {
			rtcTags.add(tag);
		}
		return tag;
	}

	public Boolean contains(RtcTag tag) {
		return rtcTags.contains(tag);
	}
}
