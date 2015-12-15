/**
 *
 */

package to.rtc.cli.migrate;

/**
 * @author florian.buehlmann
 *
 */
public class Tag {

	private final String uuid;
	private String name;
	private long creationDate;

	Tag(String uuid) {
		this.uuid = uuid;
	}

	Tag setCreationDate(long creationDate) {
		this.creationDate = creationDate;
		return this;
	}

	Tag setName(String name) {
		this.name = name;
		return this;
	}

	String getUuid() {
		return uuid;
	}

	public String getName() {
		return name;
	}

	public long getCreationDate() {
		return creationDate;
	}

}
