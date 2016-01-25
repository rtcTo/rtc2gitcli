package to.rtc.cli.migrate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

final class RtcTag implements Tag {

	/**
    *
    */
	private static final int TIME_DIFFERENCE_PLUS_MINUS_SECONDS = 5;
	private static final RtcChangeSet EARLYEST_CHANGESET = new RtcChangeSet("").setCreationDate(Long.MAX_VALUE);
	private final String uuid;
	private String originalName;
	private boolean makeNameUnique;
	private long creationDate;
	private final Map<String, List<RtcChangeSet>> components;
	private long totalChangeSetCount;

	RtcTag(String uuid) {
		this.uuid = uuid;
		components = new HashMap<String, List<RtcChangeSet>>();
		totalChangeSetCount = 0;
		makeNameUnique = false;
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

	void add(RtcChangeSet changeSet) {
		List<RtcChangeSet> changesets = null;
		String component = changeSet.getComponent();
		if (components.containsKey(component)) {
			changesets = components.get(component);
		} else {
			changesets = new ArrayList<RtcChangeSet>();
			components.put(component, changesets);
		}
		changesets.add(changeSet);
		totalChangeSetCount++;
	}

	Map<String, List<RtcChangeSet>> getComponentsChangeSets() {
		return components;
	}

	List<RtcChangeSet> getOrderedChangeSets() {
		List<RtcChangeSet> changeSets = new ArrayList<RtcChangeSet>();
		Map<String, AtomicInteger> changeSetOrderIndex = new HashMap<String, AtomicInteger>();

		for (String component : components.keySet()) {
			changeSetOrderIndex.put(component, new AtomicInteger(0));
		}

		while (changeSets.size() < totalChangeSetCount) {
			changeSets.add(getLatestChangeSet(changeSetOrderIndex));
		}
		return changeSets;
	}

	private RtcChangeSet getLatestChangeSet(Map<String, AtomicInteger> changeSetOrderIndex) {
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
		if (makeNameUnique) {
			return originalName + "_" + creationDate / 1000;
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

	public void makeNameUnique() {
		makeNameUnique = true;
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
			long objCreationDate = tag.getCreationDate();
			if ((getOriginalName().equals(tag.getOriginalName()))
					&& ((objCreationDate <= this.creationDate + (TIME_DIFFERENCE_PLUS_MINUS_SECONDS - 1)) || (objCreationDate >= this.creationDate
							- (TIME_DIFFERENCE_PLUS_MINUS_SECONDS - 1)))) {
				return true;
			}
		}
		return false;
	}

	String getOriginalName() {
		return originalName;
	}
}
