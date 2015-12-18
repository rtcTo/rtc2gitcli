package to.rtc.cli.migrate;

import to.rtc.cli.migrate.ChangeSet.WorkItem;

/**
 * @author patrick.reinhart
 */
final class RtcWorkItem implements WorkItem {
	private long number;
	private String text;

	RtcWorkItem(long number, String text) {
		this.number = number;
		this.text = text;
	}

	@Override
	public long getNumber() {
		return number;
	}

	@Override
	public String getText() {
		return text;
	}
}