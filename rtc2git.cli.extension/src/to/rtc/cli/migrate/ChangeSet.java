package to.rtc.cli.migrate;

import java.util.List;

/**
 * Represents a change set
 *
 * @author florian.buehlmann
 * @author patrick.reinhart
 */
public interface ChangeSet {
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
