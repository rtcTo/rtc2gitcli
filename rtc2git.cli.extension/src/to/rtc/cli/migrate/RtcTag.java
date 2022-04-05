package to.rtc.cli.migrate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

public final class RtcTag implements Tag {

	private static final long TIME_DIFFERENCE_PLUS_MINUS_MILLISECONDS = TimeUnit.SECONDS.toMillis(90);
	private String uuid;
	private String originalName;
	private boolean makeNameUnique;
	private long creationDate;
	private final Map<String, List<RtcChangeSet>> components;
	private long totalChangeSetCount;
	private boolean doCreateTag;
	private boolean containLastChangeset;

	RtcTag(String uuid) {
		this.uuid = uuid;
		components = new HashMap<String, List<RtcChangeSet>>();
		totalChangeSetCount = 0;
		makeNameUnique = false;
		doCreateTag = true;
		containLastChangeset = false;
	}

	RtcTag setCreationDate(long creationDate) {
		this.creationDate = creationDate;
		return this;
	}

	RtcTag setOriginalName(String originalName) {
		this.originalName = originalName;
		return this;
	}

	String getUuid() {
		return uuid;
	}

	RtcTag setUuid(String uuid) {
		this.uuid = uuid;
		return this;
	}

	boolean add(RtcChangeSet changeSet) {
		List<RtcChangeSet> changesets = null;
		String component = changeSet.getComponent();
		if (components.containsKey(component)) {
			changesets = components.get(component);
		} else {
			changesets = new ArrayList<RtcChangeSet>();
			components.put(component, changesets);
		}
		final boolean result = changesets.add(changeSet);
		totalChangeSetCount++;
		return result;
	}

	/**
	 * Locates a {@link RtcChangeSet} given only its UUID.
	 * 
	 * @param changeSetUuid The UUID to search for.
	 * @return An {@link Entry} where {@link Entry#getKey()} is the component it
	 *         belongs to and {@link Entry#getValue()} is the {@link RtcChangeSet}
	 *         itself. Or null if no such ChangeSet was found.
	 */
	Entry<String, RtcChangeSet> findChangeSetByUuid(String changeSetUuid) {
		for (Entry<String, List<RtcChangeSet>> entry : components.entrySet()) {
			final String component = entry.getKey();
			final List<RtcChangeSet> changeSets = entry.getValue();
			for (final RtcChangeSet cs : changeSets) {
				final String thisUuid = cs.getUuid();
				if (thisUuid.equals(changeSetUuid)) {
					return new Entry<String, RtcChangeSet>() {
						@Override
						public String getKey() {
							return component;
						}

						@Override
						public RtcChangeSet getValue() {
							return cs;
						}

						@Override
						public RtcChangeSet setValue(RtcChangeSet value) {
							throw new UnsupportedOperationException("Read-only");
						}
					};
				}
			}
		}
		return null;
	}

	/**
	 * Removes a {@link RtcChangeSet} from this tag.
	 * 
	 * @param componentTheChangeSetBelongsTo The component it belongs (to as
	 *                                       specified by e.g.
	 *                                       {@link #findChangeSetByUuid(String)}).
	 * @param changeSetToRemove              The {@link RtcChangeSet} to be removed.
	 * @return true if the {@link RtcChangeSet} was removed, false if it wasn't
	 *         found.
	 */
	boolean removeChangeSet(String componentTheChangeSetBelongsTo, RtcChangeSet changeSetToRemove) {
		final List<RtcChangeSet> list = components.get(componentTheChangeSetBelongsTo);
		if (list == null) {
			return false;
		}
		return list.remove(changeSetToRemove);
	}

	public Map<String, List<RtcChangeSet>> getComponentsChangeSets() {
		return components;
	}

	private static class RtcChangeSetHistoryIndexComparator implements Comparator<RtcChangeSet> {
		/**
		 * Returns 1 (or more) if a is later than b, -1 (or less) if b is later than a, or 0 if they are the same.
		 */
		@Override
		public int compare(RtcChangeSet a, RtcChangeSet b) {
			final long cmpHistoryOrderIndex = a.getHistoryOrderIndex() - b.getHistoryOrderIndex();
			if ( cmpHistoryOrderIndex!=0 ) {
				return cmpHistoryOrderIndex > 0L ? 1 : -1;
			}
			final int cmpUuids = a.getUuid().compareTo(b.getUuid());
			return cmpUuids;
		}
	}
	/**
	 * Compares {@link RtcChangeSet}s by the order in which they appear in the RTC history.
	 * This should only be used to compare {@link RtcChangeSet}s affecting the same RTC component.
	 */
	static final Comparator<RtcChangeSet> RTC_CHANGESET_HISTORYINDEX_COMPARATOR = new RtcChangeSetHistoryIndexComparator();

	/**
	 * Compares {@link RtcChangeSet}s by date, with later "added to stream" or "modified" date meaning later in the sort order.
	 */
	private static class RtcChangeSetDateComparator implements Comparator<RtcChangeSet> {
		/**
		 * Returns 1 (or more) if a is later than b, -1 (or less) if b is later than a, or 0 if they are the same.
		 */
		@Override
		public int compare(RtcChangeSet a, RtcChangeSet b) {
			final long aAddedToStream = a.getAddedToStreamDate();
			final long bAddedToStream = b.getAddedToStreamDate();
			final long cmpAddedToStream = (aAddedToStream==ChangeSet.DATE_UNKNOWN || bAddedToStream==ChangeSet.DATE_UNKNOWN) ? 0L : aAddedToStream - bAddedToStream;
			if( cmpAddedToStream!=0L) {
				return cmpAddedToStream > 0L ? 1 : -1;
			}
			final long aModified = a.getCreationDate();
			final long bModified = b.getCreationDate();
			final long cmpModified = (aModified==ChangeSet.DATE_UNKNOWN || bModified==ChangeSet.DATE_UNKNOWN) ? 0L : aModified - bModified;
			if( cmpModified!=0L) {
				return cmpModified > 0L ? 1 : -1;
			}
			final int cmpComponents = a.getComponent().compareTo(b.getComponent());
			if ( cmpComponents!=0 ) {
				return cmpComponents;
			}
			final int cmpUuids = a.getUuid().compareTo(b.getUuid());
			return cmpUuids;
		}
	}
	private static final Comparator<RtcChangeSet> RTC_CHANGESET_DATE_COMPARATOR = new RtcChangeSetDateComparator();

	/**
	 * Calculates and returns the changesets in the order they should be migrated, i.e. index 0 is the first.
	 * @return A non-null list of all changesets in this tag.
	 */
	List<RtcChangeSet> getOrderedChangeSets() {
		// Change history is not RTC's strongest feature.
		// You can scan the history of a component within a stream and find out who added each changeset
		// and when ... except that sometimes the "when" is a lie (it can claim things were added to the
		// stream well before they were really added) and other times the "when" is the same value as for
		// other changesets, so you can't use "when" to decide the order.
		// It seems that the only indication of order is the order in which RTC returns the data, but
		// this only tells you about an individual component, leaving the decision as to the order of
		// changesets affecting different components as our problem.
		//
		// That's the problem this method is here to solve.
		//
		// We assume the following:
		// 1) We know the changeset order for each component individually.
		// 2) That the "when changeset was added to stream" date is a good indicator
		// 3) ...unless the date goes backwards, in which case that indicates a merge event where we
		//	  should handle all such changesets until the date is "forwards" again.
		//
		// So, first we gather the changesets into one queue per component, where each queue is
		// correctly ordered.
		final Map<String, Queue<RtcChangeSet>> componentIdToChangeSets = new TreeMap<String, Queue<RtcChangeSet>>();
		int expectedSize = 0;
		for (Map.Entry<String, List<RtcChangeSet>> e : components.entrySet()) {
			final String componentId = e.getKey();
			final Set<RtcChangeSet> sortedChangeSets = new TreeSet<RtcChangeSet>(RTC_CHANGESET_HISTORYINDEX_COMPARATOR);
			sortedChangeSets.addAll(e.getValue());
			final LinkedList<RtcChangeSet> orderedChangeSets = new LinkedList<RtcChangeSet>(sortedChangeSets);
			if( !orderedChangeSets.isEmpty() ) {
				componentIdToChangeSets.put(componentId, orderedChangeSets);
				expectedSize += orderedChangeSets.size();
			}
		}
		final List<RtcChangeSet> result = new ArrayList<RtcChangeSet>(expectedSize);
		// Now we iterate over our queues, examining the changeset at the head of each,
		// and taking the "earliest" of those on offer.
		// The idea here is that changesets from different components will interleave based
		// on their datestamps but, within a component, changesets will always be
		// in the correct order.
		while ( !componentIdToChangeSets.isEmpty() ) {
			RtcChangeSet earliestCS = null;
			Queue<RtcChangeSet> earliestCSs = null;
			String earliestCId = null;
			for (final Map.Entry<String, Queue<RtcChangeSet>> e : componentIdToChangeSets.entrySet()) {
				final String componentId = e.getKey();
				final Queue<RtcChangeSet> css = e.getValue();
				final RtcChangeSet cs = css.peek();
				if( earliestCS==null || RTC_CHANGESET_DATE_COMPARATOR.compare(earliestCS, cs)>0 ) {
					earliestCSs = css;
					earliestCS = cs;
					earliestCId = componentId;
				}
			}
			if ( earliestCS!=null ) {
				earliestCSs.poll(); // remove CS from queue
				result.add(earliestCS); /// add it to the results
				if( earliestCSs.isEmpty() ) {
					// if a queue becomes empty, we forget about it.
					// that way our Map will become empty once we're out of data.
					componentIdToChangeSets.remove(earliestCId);
				}
			}
		}
		return result;
	}

	@Override
	public String getName() {
		if (makeNameUnique) {
			return originalName + "_" + RtcChangeSet.getDateAsString(creationDate);
		} else {
			return originalName;
		}
	}

	@Override
	public long getCreationDate() {
		return creationDate;
	}

	public boolean isEmpty() {
		return totalChangeSetCount <= 0;
	}

	RtcTag setMakeNameUnique(boolean makeNameUnique) {
		this.makeNameUnique = makeNameUnique;
		return this;
	}

	boolean isMakeNameUnique() {
		return makeNameUnique;
	}

	boolean isContainingLastChangeset() {
		return containLastChangeset;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((components == null) ? 0 : components.hashCode());
		result = prime * result + (int) (creationDate ^ (creationDate >>> 32));
		result = prime * result + (makeNameUnique ? 1231 : 1237);
		result = prime * result + ((originalName == null) ? 0 : originalName.hashCode());
		result = prime * result + (int) (totalChangeSetCount ^ (totalChangeSetCount >>> 32));
		result = prime * result + ((uuid == null) ? 0 : uuid.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (obj.getClass() == getClass()) {
			RtcTag tag = (RtcTag) obj;
			if (getOriginalName().equals(tag.getOriginalName())) {
				long objCreationDate = tag.getCreationDate();
				if ((objCreationDate == this.creationDate)
						|| ((objCreationDate <= this.creationDate + TIME_DIFFERENCE_PLUS_MINUS_MILLISECONDS) && (objCreationDate >= this.creationDate
								- TIME_DIFFERENCE_PLUS_MINUS_MILLISECONDS))) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public String toString() {
		return (new StringBuilder(getName())).append('@').append(RtcChangeSet.getDateAsString(creationDate)).toString();
	}

	String getOriginalName() {
		return originalName;
	}

	public void addAll(Map<String, List<RtcChangeSet>> componentsChangeSets) {
		for (Entry<String, List<RtcChangeSet>> changesetList : componentsChangeSets.entrySet()) {
			for (RtcChangeSet changeset : changesetList.getValue()) {
				add(changeset);
			}
		}
	}

	RtcTag setDoCreateTag(boolean doCreateTag) {
		this.doCreateTag = doCreateTag;
		return this;
	}

	boolean doCreateTag() {
		return doCreateTag;
	}

	RtcTag setContainLastChangeset(boolean containLastChangeset) {
		this.containLastChangeset = containLastChangeset;
		return this;
	}
}
