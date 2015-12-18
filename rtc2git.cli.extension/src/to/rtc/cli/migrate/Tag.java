package to.rtc.cli.migrate;

/**
 * Represents a tag.
 *
 * @author florian.buehlmann
 * @author patrick.reinhart
 */
public interface Tag {
	/**
	 * Returns the actual tag name.
	 * 
	 * @return the name of the tag
	 */
	public String getName();

	/**
	 * Returns the tag set creation time stamp.
	 * 
	 * @return the creation date time stamp
	 */
	public long getCreationDate();
}