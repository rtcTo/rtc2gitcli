/**
 *
 */
package to.rtc.cli.migrate;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RtcTagList implements Iterable<RtcTag> {

	private final StreamOutput output;
	private List<RtcTag> rtcTags;

	public RtcTagList(StreamOutput output) {
		this.output = output;
		rtcTags = new ArrayList<RtcTag>();
	}

	public RtcTag add(RtcTag tag) {
		long creationDate = tag.getCreationDate();
		int tagIndex = rtcTags.indexOf(tag);
		if (tagIndex < 0) {
			for (RtcTag tagToCheck : rtcTags) {
				if (tagToCheck.getOriginalName().equals(tag.getOriginalName())) {
					tag.setMakeNameUnique(true);
					break;
				}
			}
			rtcTags.add(tag);
		} else {
			tag = rtcTags.get(tagIndex);
			tag.setCreationDate(creationDate / 2 + tag.getCreationDate() / 2);
		}
		return tag;
	}

	public void printTagList(boolean printChangesetDetails) {
		output.writeLine("********** BASELINE INFOS **********");
		int totalChangeSets = 0;
		for (RtcTag tag : rtcTags) {
			List<RtcChangeSet> orderedChangeSets = tag.getOrderedChangeSets();
			int totalChangeSetsByBaseline = orderedChangeSets.size();
			totalChangeSets += totalChangeSetsByBaseline;
			output.writeLine("  Baseline [" + tag.getName() + "] with original name [" + tag.getOriginalName()
					+ "] created at [" + (new Date(tag.getCreationDate())) + "] total number of changesets ["
					+ totalChangeSetsByBaseline + "] will be tagged [" + tag.doCreateTag() + "]");
			for (Entry<String, List<RtcChangeSet>> entry : tag.getComponentsChangeSets().entrySet()) {
				output.writeLine("      number of changesets  for component [" + entry.getKey() + "] is ["
						+ entry.getValue().size() + "]");
			}
			if (printChangesetDetails) {
				for (RtcChangeSet changeSet : orderedChangeSets) {
					output.writeLine("        -- " + new Date(changeSet.getCreationDate()) + " : ["
							+ changeSet.getCreatorName() + "] " + changeSet.getComment());
				}
			}
		}
		output.writeLine("TOTAL NUMBER OF CHANGESETS [" + totalChangeSets + "]");
		output.writeLine("********** BASELINE INFOS **********");
	}

	public void pruneInactiveTags() {
		RtcTag lastTagThatRequiresTagging = null;
		for (RtcTag tag : rtcTags) {
			if (tag.isContainingLastChangeset()) {
				lastTagThatRequiresTagging = tag;
			}

		}
		boolean lastTagReached = false;
		for (RtcTag tag : rtcTags) {
			tag.setDoCreateTag(!lastTagReached && tag.doCreateTag());
			if (tag.equals(lastTagThatRequiresTagging)) {
				lastTagReached = true;
			}
		}
	}

	public void pruneExcludedTags(Pattern includePattern) {
		List<RtcTag> prunedList = new ArrayList<RtcTag>();
		RtcTag tmpTag = null;

		for (RtcTag currentTag : rtcTags) {
			if (tmpTag == null) {
				tmpTag = currentTag;
			} else {
				tmpTag.setUuid(currentTag.getUuid()).setOriginalName(currentTag.getOriginalName())
						.setCreationDate(currentTag.getCreationDate()).setMakeNameUnique(currentTag.isMakeNameUnique())
						.setDoCreateTag(currentTag.doCreateTag());
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
		int tagIndex = rtcTags.indexOf(tag);
		if (tagIndex < 0) {
			output.writeLine("Error: Tag could not be found in Stream");
			output.writeLine("Searching for Tag: [" + tagName + "] ["
					+ (new SimpleDateFormat("yyyyMMdd_HHmmss")).format(new Date(creationDate)) + "]");
			sortByCreationDate();
			printTagList(false);
			throw new RuntimeException("Tag not found");
		} else {
			tag = rtcTags.get(tagIndex);
		}
		return tag;
	}

	public RtcTag getHeadTag() {
		RtcTag tag = new RtcTag(null).setDoCreateTag(false).setOriginalName("HEAD").setCreationDate(Long.MAX_VALUE);
		int tagIndex = rtcTags.indexOf(tag);
		if (tagIndex < 0) {
			add(tag);
		} else {
			tag = rtcTags.get(tagIndex);
		}
		return tag;
	}

	public Boolean contains(RtcTag tag) {
		return Boolean.valueOf(rtcTags.contains(tag));
	}
}
