package to.rtc.cli.migrate;

import java.util.ArrayList;
import java.util.List;

/**
 * @author florian.buehlmann
 */
final class RtcChangeSet implements ChangeSet {
	private final String uuid;
	private final List<WorkItem> workItems;

	private long creationDate;
	private String entryName;
	private String creatorName;
	private String emailAddress;
	private String component;

	RtcChangeSet(String changeSetUuid) {
		uuid = changeSetUuid;
		workItems = new ArrayList<WorkItem>();
	}

	RtcChangeSet addWorkItem(long workItem, String workItemText) {
		workItems.add(new RtcWorkItem(workItem, workItemText));
		return this;
	}

	RtcChangeSet setText(String entryName) {
		this.entryName = entryName;
		return this;
	}

	RtcChangeSet setCreatorName(String creatorName) {
		this.creatorName = creatorName;
		return this;
	}

	RtcChangeSet setCreatorEMail(String emailAddress) {
		this.emailAddress = emailAddress;
		return this;
	}

	RtcChangeSet setCreationDate(long creationDate) {
		this.creationDate = creationDate;
		return this;
	}

	RtcChangeSet setComponent(String component) {
		this.component = component;
		return this;
	}

	String getUuid() {
		return uuid;
	}

	String getComponent() {
		return component;
	}

	@Override
	public String getComment() {
		return entryName;
	}

	@Override
	public String getCreatorName() {
		return creatorName;
	}

	@Override
	public String getEmailAddress() {
		return emailAddress;
	}

	@Override
	public long getCreationDate() {
		return creationDate;
	}

	@Override
	public List<WorkItem> getWorkItems() {
		return workItems;
	}
}
