/**
 *
 */
package to.rtc.cli.migrate;

import java.util.ArrayList;
import java.util.Collections;
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

	/**
	 * Adds the given {@link RtcTag} to our list if it does not already exist. If it
	 * does already exist, merge the given tag with the existing tag.
	 * 
	 * @param tag The tag to be added
	 * @return The tag that's in our list, which may be the one given, or may be a
	 *         tag that's a merger of the existing tag and the new one.
	 */
	public RtcTag add(RtcTag tag) {
		int tagIndex = rtcTags.indexOf(tag);
		if (tagIndex < 0) {
			final String nameOfNewlyAddedTag = tag.getOriginalName();
			for (RtcTag tagToCheck : rtcTags) {
				if (tagToCheck.getOriginalName().equals(nameOfNewlyAddedTag)) {
					tag.setMakeNameUnique(true);
					break;
				}
			}
			rtcTags.add(tag);
		} else {
			long newlyAddedCreationDate = tag.getCreationDate();
			tag = rtcTags.get(tagIndex);
			tag.setCreationDate(newlyAddedCreationDate / 2 + tag.getCreationDate() / 2);
		}
		return tag;
	}

	public void printTagList(boolean printChangesetDetails, final long maxTagsToPrint, final long maxChangesetsToPrint) {
		output.writeLine("********** BASELINE INFOS **********");
		int tagsPrinted = 0;
		int changesetsPrinted = 0;
		int totalChangeSets = 0;
		boolean haveToldUserListIsTruncated = false;
		for (RtcTag tag : rtcTags) {
			List<RtcChangeSet> orderedChangeSets = tag.getOrderedChangeSets();
			int totalChangeSetsByBaseline = orderedChangeSets.size();
			totalChangeSets += totalChangeSetsByBaseline;
			if (tagsPrinted < maxTagsToPrint && changesetsPrinted < maxChangesetsToPrint) {
				final String tagName = tag.getName();
				final String tagOriginalName = tag.getOriginalName();
				output.writeLine("  Baseline [" + tagName
						+ (tagName.equals(tagOriginalName) ? "" : ("] with original name [" + tagOriginalName))
						+ "] created at [" + RtcChangeSet.getDateAsString(tag.getCreationDate()) + "] total number of changesets ["
						+ totalChangeSetsByBaseline + "] will be tagged [" + tag.doCreateTag() + "]");
				for (Entry<String, List<RtcChangeSet>> entry : tag.getComponentsChangeSets().entrySet()) {
					output.writeLine("      number of changesets for component [" + entry.getKey() + "] is ["
							+ entry.getValue().size() + "]");
				}
				for (RtcChangeSet changeSet : orderedChangeSets) {
					if (printChangesetDetails) {
						if (tagsPrinted < maxTagsToPrint && changesetsPrinted < maxChangesetsToPrint) {
							output.writeLine("        -- " + changeSet.toDetailedString());
							changesetsPrinted++;
						} else if (!haveToldUserListIsTruncated) {
							haveToldUserListIsTruncated = true;
							output.writeLine("        -- ...");
						}
					}
				}
				tagsPrinted++;
			} else if (!haveToldUserListIsTruncated) {
				haveToldUserListIsTruncated = true;
				output.writeLine("  ...");
			}
		}
		output.writeLine("TOTAL NUMBER OF CHANGESETS [" + totalChangeSets + "]"
				+ ((totalChangeSets == changesetsPrinted || !printChangesetDetails) ? "" : (" but only [" + changesetsPrinted + "] listed")));
		output.writeLine("********** BASELINE INFOS **********");
	}

	/** Selectively unsets {@link RtcTag#doCreateTag()}. Does NOT remove anything from the tag list. */
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
		Collections.sort(rtcTags, TagCreationDateComparator.INSTANCE);
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
					+ RtcChangeSet.getDateAsString(creationDate) + "]");
			sortByCreationDate();
			printTagList(false, Long.MAX_VALUE, Long.MAX_VALUE);
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
			tag = add(tag);
		} else {
			tag = rtcTags.get(tagIndex);
		}
		return tag;
	}

	public Boolean contains(RtcTag tag) {
		return Boolean.valueOf(rtcTags.contains(tag));
	}

	/**
	 * Finds a {@link RtcChangeSet} given only its UUID.
	 * 
	 * @param rtcChangeSetUuidToSearchFor The UUID of the {@link RtcChangeSet} to be
	 *                                    searched for.
	 * @return A {@link Entry} where {@link Entry#getKey()} is the {@link RtcTag}
	 *         that contains the {@link RtcChangeSet} and {@link Entry#getValue()}
	 *         is the result from {@link RtcTag#findChangeSetByUuid(String)} where
	 *         it was found ... or null if the changeset was not found.
	 */
	Entry<RtcTag, Entry<String, RtcChangeSet>> findChangeSetByUuid(final String rtcChangeSetUuidToSearchFor) {
		for (final RtcTag rtcTag : rtcTags) {
			final Entry<String, RtcChangeSet> result = rtcTag.findChangeSetByUuid(rtcChangeSetUuidToSearchFor);
			if (result != null) {
				return new Entry<RtcTag, Entry<String, RtcChangeSet>>() {
					@Override
					public RtcTag getKey() {
						return rtcTag;
					}

					@Override
					public Entry<String, RtcChangeSet> getValue() {
						return result;
					}

					@Override
					public Entry<String, RtcChangeSet> setValue(Entry<String, RtcChangeSet> value) {
						throw new UnsupportedOperationException("Read-only");
					}
				};
			}
		}
		return null;
	}

	/**
	 * Removes a {@link RtcChangeSet} from the specified {@link RtcTag}.
	 * 
	 * @param tagTheChangeSetBelongsTo       The {@link RtcTag} it belongs (to as
	 *                                       specified by e.g.
	 *                                       {@link #findChangeSetByUuid(String)}).
	 * @param componentTheChangeSetBelongsTo The component it belongs (to as
	 *                                       specified by e.g.
	 *                                       {@link #findChangeSetByUuid(String)}).
	 * @param changeSetToRemove              The {@link RtcChangeSet} to be removed.
	 * @return true if the {@link RtcChangeSet} was removed, false if it wasn't
	 *         found.
	 */
	boolean removeChangeSet(RtcTag tagTheChangeSetBelongsTo, String componentTheChangeSetBelongsTo, RtcChangeSet changeSetToRemove) {
		return tagTheChangeSetBelongsTo.removeChangeSet(componentTheChangeSetBelongsTo, changeSetToRemove);
	}
}
