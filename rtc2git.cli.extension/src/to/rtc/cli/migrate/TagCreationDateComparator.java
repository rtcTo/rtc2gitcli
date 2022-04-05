
package to.rtc.cli.migrate;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class TagCreationDateComparator implements Comparator<RtcTag> {
    public static final Comparator<RtcTag> INSTANCE = new TagCreationDateComparator();

	@Override
	public int compare(RtcTag o1, RtcTag o2) {
		if (o1 == o2) {
			return 0;
		}
		final int csComparisonResult = compareByChangeSetOrder(o1, o2);
		if (csComparisonResult != 0) {
			return csComparisonResult;
		}
		if (o1.getCreationDate() > o2.getCreationDate()) {
			return 1;
		}
		if (o1.getCreationDate() < o2.getCreationDate()) {
			return -1;
		}
		return 0;
	}

	/**
	 * If our two tags affect the same component then we have to order them
	 * according to the order that their {@link RtcChangeSet}s must be applied.
	 * 
	 * @param t1 First tag to be compared.
	 * @param t2 Second tag to be compared.
	 * @return 0 if we cannot distinguish based on {@link RtcChangeSet}s.
	 */
	private int compareByChangeSetOrder(RtcTag t1, RtcTag t2) {
		final Map<String, List<RtcChangeSet>> ccs1 = t1.getComponentsChangeSets();
		final Map<String, List<RtcChangeSet>> ccs2 = t2.getComponentsChangeSets();
		final Set<String> componentsInCommon = inBoth(ccs1.keySet(), ccs2.keySet());
		for (final String componentInCommon : componentsInCommon) {
			final List<RtcChangeSet> lcss1 = ccs1.get(componentInCommon);
			final List<RtcChangeSet> lcss2 = ccs2.get(componentInCommon);
			final int compareByChangeSetsResult = compareByChangeSets(lcss1, lcss2);
			if (compareByChangeSetsResult != 0) {
				return compareByChangeSetsResult;
			}
		}
		return 0;
	}

	/**
	 * Compares two sets of {@link RtcChangeSet}s. Note: Both sets must refer to the
	 * same component.
	 * 
	 * @param cs1 First set of {@link RtcChangeSet}s.
	 * @param cs2 Second set of {@link RtcChangeSet}s.
	 * @return 0 if we cannot distinguish based on changesets.
	 */
	private int compareByChangeSets(Collection<? extends RtcChangeSet> cs1, Collection<? extends RtcChangeSet> cs2) {
		if (cs1 == cs2 || cs1.isEmpty() || cs2.isEmpty()) {
			return 0;
		}
		final Set<RtcChangeSet> sorted1 = new TreeSet<RtcChangeSet>(RtcTag.RTC_CHANGESET_HISTORYINDEX_COMPARATOR);
		sorted1.addAll(cs1);
		final Set<RtcChangeSet> sorted2 = new TreeSet<RtcChangeSet>(RtcTag.RTC_CHANGESET_HISTORYINDEX_COMPARATOR);
		sorted2.addAll(cs2);
		final RtcChangeSet first1 = first(sorted1);
		final RtcChangeSet last1 = last(sorted1);
		final RtcChangeSet first2 = first(sorted2);
		final RtcChangeSet last2 = last(sorted2);
		final int cmpFirst = RtcTag.RTC_CHANGESET_HISTORYINDEX_COMPARATOR.compare(first1, first2);
		final int cmpLast = RtcTag.RTC_CHANGESET_HISTORYINDEX_COMPARATOR.compare(last1, last2);
		// The lists should be the same, or one 100% before the other
		if (cmpFirst == 0 && cmpLast == 0) {
			return 0;
		}
		if (cmpFirst > 0 && cmpLast > 0) {
			return 1;
		}
		if (cmpFirst < 0 && cmpLast < 0) {
			return -1;
		}
		// but if they overlap then we complain.
		// Note: This should not happen in practise - it's an internal error if it does.
		final String detail1 = first1.toDetailedString() + (last1 != first1 ? "..." + last1.toDetailedString() : "");
		final String detail2 = first2.toDetailedString() + (last2 != first2 ? "..." + last2.toDetailedString() : "");
		throw new IllegalStateException(
				"Unable to determine correct order of two lists of changesets because they appear to overlap. List1["
						+ cs1.size() + "] = {" + detail1 + "}, List2[" + cs2.size() + "] = {" + detail2 + "}.");
	}

	private static <K> Set<K> inBoth(final Collection<? extends K> keys1, final Collection<? extends K> keys2) {
		final Set<K> inBoth = new HashSet<K>();
		for (final K key : keys1) {
			if (keys2.contains(key)) {
				inBoth.add(key);
			}
		}
		return inBoth;
	}

	private static <X> X first(Iterable<? extends X> set) {
		final Iterator<? extends X> i = set.iterator();
		if (i.hasNext()) {
			return i.next();
		}
		return null;
	}

	private static <X> X last(Iterable<? extends X> set) {
		final Iterator<? extends X> i = set.iterator();
		X result = null;
		while (i.hasNext()) {
			result = i.next();
		}
		return result;
	}
}
