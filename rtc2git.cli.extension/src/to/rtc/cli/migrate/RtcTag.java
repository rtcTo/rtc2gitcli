package to.rtc.cli.migrate;

/**
 * @author florian.buehlmann
 */
final class RtcTag implements Tag {
	private final String uuid;
	private String name;
	private long creationDate;

	RtcTag(String uuid) {
		this.uuid = uuid;
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

	@Override
	public String getName() {
		return name;
	}

	@Override
	public long getCreationDate() {
		return creationDate;
	}
}