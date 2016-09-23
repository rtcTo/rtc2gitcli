package to.rtc.cli.migrate;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class RtcTag implements Tag {

	/**
    *
    */
	private static final long TIME_DIFFERENCE_PLUS_MINUS_MILLISECONDS = TimeUnit.SECONDS.toMillis(90);
	private static final RtcChangeSet EARLYEST_CHANGESET = new RtcChangeSet("").setCreationDate(Long.MAX_VALUE);
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
			int changeSetIndex = index.get();
			if (changeSetIndex < changeSets.size()) {
				RtcChangeSet changeSet = changeSets.get(changeSetIndex);
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
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
			return originalName + "_" + dateFormat.format(new Date(creationDate));
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
			long objCreationDate = tag.getCreationDate();
			if (getOriginalName().equals(tag.getOriginalName())) {
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
		return (new StringBuilder(getName())).append('@').append(new Date(creationDate)).toString();
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
