
package to.rtc.cli.migrate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

final class RtcTag implements Tag {

	private static final RtcChangeSet EARLYEST_CHANGESET = new RtcChangeSet("").setCreationDate(Long.MAX_VALUE);
	private final String uuid;
	private String name;
	private long creationDate;
	private final Map<String, List<RtcChangeSet>> components;
	private long totalChangeSetCount;
	private final Map<String, AtomicInteger> changeSetOrderIndex;

	RtcTag(String uuid) {
		this.uuid = uuid;
		components = new HashMap<String, List<RtcChangeSet>>();
		totalChangeSetCount = 0;
		changeSetOrderIndex = new HashMap<String, AtomicInteger>();
	}

	RtcTag setCreationDate(long creationDate) {
		this.creationDate = creationDate;
		return this;
	}

	RtcTag setName(String name) {
		this.name = name;
		return this;
	}

	String getUuid() {
		return uuid;
	}

	void add(RtcChangeSet changeSet) {
		List<RtcChangeSet> changesets = null;
		String component = changeSet.getComponent();
		if (components.containsKey(component)) {
			changesets = components.get(component);
		} else {
			changesets = new ArrayList<RtcChangeSet>();
			components.put(component, changesets);
			changeSetOrderIndex.put(component, new AtomicInteger(0));
		}
		changesets.add(changeSet);
		totalChangeSetCount++;
	}

	Map<String, List<RtcChangeSet>> getComponentsChangeSets() {
		return components;
	}

	List<RtcChangeSet> getOrderedChangeSets() {
		List<RtcChangeSet> changeSets = new ArrayList<RtcChangeSet>();

		while (changeSets.size() < totalChangeSetCount) {
			changeSets.add(getLatestChangeSet());
		}
		return changeSets;
	}

	private RtcChangeSet getLatestChangeSet() {
		RtcChangeSet earlyestChangeSet = EARLYEST_CHANGESET;
		for (Entry<String, List<RtcChangeSet>> entry : components.entrySet()) {
			AtomicInteger index = changeSetOrderIndex.get(entry.getKey());
			List<RtcChangeSet> changeSets = entry.getValue();
			if (index.get() < changeSets.size()) {
				RtcChangeSet changeSet = changeSets.get(index.get());
				if (earlyestChangeSet.getCreationDate() > changeSet.getCreationDate()) {
					earlyestChangeSet = changeSet;
				}
			}
		}
		changeSetOrderIndex.get(earlyestChangeSet.getComponent()).incrementAndGet();
		return earlyestChangeSet;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public long getCreationDate() {
		return creationDate;
	}

	public boolean isEmpty() {
		return totalChangeSetCount <= 0;
	}
}
