package to.rtc.cli.migrate;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author florian.buehlmann
 */
public final class RtcChangeSet implements ChangeSet {
	private final String uuid;
	private final List<WorkItem> workItems;

	private long creationDate = DATE_UNKNOWN;
	private String entryName;
	private String creatorName;
	private String emailAddress;
	private long addedToStreamDate = DATE_UNKNOWN;
	private String addedToStreamName;
	private String addedToStreamEmailAddress;
	private String component;
	private String serverUri;
	private String streamUuid;
	private long historyOrderIndex;
	private boolean fullyPopulated;

	public RtcChangeSet(String changeSetUuid) {
		uuid = changeSetUuid;
		workItems = new ArrayList<WorkItem>();
	}

	public RtcChangeSet setServerURI(String serverUri) {
		this.serverUri = serverUri;
		return this;
	}

	public RtcChangeSet setStreamId(String streamUuid) {
		this.streamUuid = streamUuid;
		return this;
	}

	public RtcChangeSet addWorkItem(long workItem, String workItemText) {
		workItems.add(new RtcWorkItem(workItem, workItemText));
		return this;
	}

	public RtcChangeSet setText(String entryName) {
		this.entryName = entryName;
		return this;
	}

	public RtcChangeSet setCreatorName(String creatorName) {
		this.creatorName = creatorName;
		return this;
	}

	public RtcChangeSet setCreatorEMail(String emailAddress) {
		this.emailAddress = emailAddress;
		return this;
	}

	public RtcChangeSet setCreationDate(long creationDate) {
		this.creationDate = creationDate;
		return this;
	}

	public RtcChangeSet setAddedToStreamDate(long addedToStreamDate) {
		this.addedToStreamDate = addedToStreamDate;
		return this;
	}

	public RtcChangeSet setAddedToStreamName(String addedToStreamName) {
		this.addedToStreamName = addedToStreamName;
		return this;
	}

	public RtcChangeSet setAddedToStreamEMail(String addedToStreamEmailAddress) {
		this.addedToStreamEmailAddress = addedToStreamEmailAddress;
		return this;
	}

	public RtcChangeSet setComponent(String component) {
		this.component = component;
		return this;
	}

	String getUuid() {
		return uuid;
	}

	@Override
	public String getServerURI() {
		return serverUri;
	}

	@Override
	public String getStreamID() {
		return streamUuid;
	}

	@Override
	public String getChangesetID() {
		return uuid;
	}

	public String getComponent() {
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

	public String getCreationDateAsString() {
		return getDateAsString(creationDate);
	}

	public long getAddedToStreamDate() {
		return addedToStreamDate;
	}

	public String getAddedToStreamDateAsString() {
		return getDateAsString(addedToStreamDate);
	}

	public String getAddedToStreamName() {
		return addedToStreamName;
	}

	public String getAddedToStreamEmailAddress() {
		return addedToStreamEmailAddress;
	}

	private static int digitsRequiredForHistoryOrderIndex = 0;
	private static String formatRequiredForPaddedHistoryOrderIndex = "";

	public RtcChangeSet setHistoryOrderIndex(long historyOrderIndex) {
		this.historyOrderIndex = historyOrderIndex;
		final int newDigits = Long.toString(historyOrderIndex).length();
		if (newDigits > digitsRequiredForHistoryOrderIndex) {
			digitsRequiredForHistoryOrderIndex = newDigits;
			formatRequiredForPaddedHistoryOrderIndex = " [%0" + newDigits + "d]";
		}
		return this;
	}

	public long getHistoryOrderIndex() {
		return historyOrderIndex;
	}

	public String getHistoryOrderIndexAsPaddedString() {
		return historyOrderIndex < 1 ? "" : String.format(formatRequiredForPaddedHistoryOrderIndex, historyOrderIndex);
	}

	public boolean isFullyPopulated() {
		return fullyPopulated;
	}

	public void setFullyPopulated(boolean fullyPopulated) {
		this.fullyPopulated = fullyPopulated;
	}

	@Override
	public List<WorkItem> getWorkItems() {
		return workItems;
	}

	@Override
	public int hashCode() {
		return Objects.hash(uuid);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RtcChangeSet other = (RtcChangeSet) obj;
		return Objects.equals(uuid, other.uuid);
	}

	@Override
	public String toString() {
		return "RtcChangeSet(" + uuid + ")";
	}

	public String toDetailedString() {
		return getUuid()
				+ getHistoryOrderIndexAsPaddedString()
				+ " " + getCreationDateAsString()
				+ " [" + getCreatorName() + "] "
				+ (isFullyPopulated()?"":"[partial data] ")
				+ (getAddedToStreamDate() == ChangeSet.DATE_UNKNOWN ? "" : (RtcChangeSet.getDateAsString(getAddedToStreamDate()) + " "))
				+ (getAddedToStreamName() == null ? "" : ("[" + getAddedToStreamName() + "] "))
				+ getComment()
				;
	}

	private static final SimpleDateFormat DATEFORMAT = new SimpleDateFormat("yyyy'/'MM'/'dd-HH':'mm':'ss.SSS");
	public static String getDateAsString(long date) {
		if ( date == Long.MAX_VALUE ) {
			return "99999999999999EndOfTime";
		}
		if ( date == Long.MIN_VALUE ) {
			return "000000000000StartOfTime";
		}
		return DATEFORMAT.format(new java.util.Date(date));
	}
}
