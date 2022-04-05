/**
 * File Name: CommitCommentTranslator.java
 */

package to.rtc.cli.migrate.util;

import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Translates a commit comment based on a optional set of <code>commit.note.regex.x</code>/
 * <code>commit.note.replacement.x</code> definition applied in their <code>x</code> numerical order.
 *
 * @author Peter Darton
 */
public class CommitNoteTranslator extends CommentTranslator {
	private static final String REPLACEMENT_FORMAT = "commit.note.replacement.%s";
	private static final Pattern SEARCH_PATTERN = Pattern.compile("^commit\\.note\\.regex\\.([0-9]+)$");

	/**
	 * @param props
	 */
	public CommitNoteTranslator(Properties props) {
		super(SEARCH_PATTERN, REPLACEMENT_FORMAT, props);
	}
}
