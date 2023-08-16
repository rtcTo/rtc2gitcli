package to.rtc.cli.migrate;

import java.util.List;

/**
 * Represents a change set
 *
 * @author florian.buehlmann
 * @author patrick.reinhart
 */
public interface ChangeSet {
	/** Can be returned by our get...Date methods to indicate that the date is not known. */
	public static final long DATE_UNKNOWN = Long.MIN_VALUE;

	/**
	 * Returns the URI of the RTC server that owns the change set.
	 * 
	 * @return the URI of the server containing the changeset.
	 */
	public String getServerURI();

	/**
	 * Returns the ItemID of the stream that the changeset came from.
	 * 
	 * @return the stream ID
	 */
	public String getStreamID();

	/**
	 * Returns the ItemID of the change set. When combined with
	 * {@link #getStreamID()} it's possible to work out what point in RTC's history
	 * this changeset was introduced.
	 * 
	 * @return the changeset ID
	 */
	public String getChangesetID();

	/**
	 * Returns the comment of the change set.
	 * 
	 * @return the changeset comment
	 */
	public String getComment();

	/**
	 * Returns the change set creator name.
	 * 
	 * @return the creator of the change set
	 */
	public String getCreatorName();

	/**
	 * Returns the email address of the change set creator.
	 * 
	 * @return the creator email address
	 */
	public String getEmailAddress();

	/**
	 * Returns the change set creation time stamp.
	 * 
	 * @return the creation date time stamp
	 */
	public long getCreationDate();

	/**
	 * Returns the list of all work items connected to that change set.
	 * 
	 * @return the referenced work items
	 */
	public List<WorkItem> getWorkItems();

	/**
	 * Returns the name of whoever added this change set to the stream.
	 * 
	 * @return the added-to-stream name
	 */
	public String getAddedToStreamName();

	/**
	 * Returns the email address of whoever added this change set to the stream.
	 * 
	 * @return the added-to-stream email address
	 */
	public String getAddedToStreamEmailAddress();

	/**
	 * Returns the date that the change set was added to the stream.
	 * 
	 * @return the added-to-stream date time stamp
	 */
	public long getAddedToStreamDate();

	/**
	 * Represents a work item reference
	 */
	public interface WorkItem {
		/**
		 * Returns the unique number of the work item.
		 * 
		 * @return the work item number
		 */
		public long getNumber();

		/**
		 * Returns the work item description.
		 * 
		 * @return the work description
		 */
		public String getText();
	}
}
