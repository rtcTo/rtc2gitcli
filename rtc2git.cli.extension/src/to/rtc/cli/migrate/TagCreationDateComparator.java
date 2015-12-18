
package to.rtc.cli.migrate;

import java.util.Comparator;

public class TagCreationDateComparator implements Comparator<RtcTag> {

	@Override
	public int compare(RtcTag o1, RtcTag o2) {
		if (o1.getCreationDate() > o2.getCreationDate()) {
			return 1;
		}
		if (o1.getCreationDate() < o2.getCreationDate()) {
			return -1;
		}
		return 0;
	}

}
