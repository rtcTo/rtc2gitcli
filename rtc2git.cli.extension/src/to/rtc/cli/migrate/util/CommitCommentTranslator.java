/**
 * File Name: CommitCommentTranslator.java
 * 
 * Copyright (c) 2016 BISON Schweiz AG, All Rights Reserved.
 */

package to.rtc.cli.migrate.util;

import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Translates a commit comment based on a optional set of <code>commit.message.regex.x</code>/
 * <code>commit.message.replacement.x</code> definition applied in their <code>x</code> numerical order.
 *
 * @author Patrick Reinhart
 */
public class CommitCommentTranslator extends CommentTranslator {
	private static final String REPLACEMENT_FORMAT = "commit.message.replacement.%s";
	private static final Pattern SEARCH_PATTERN = Pattern.compile("^commit\\.message\\.regex\\.([0-9]+)$");

	/**
	 * @param props
	 */
	public CommitCommentTranslator(Properties props) {
		super(SEARCH_PATTERN, REPLACEMENT_FORMAT, props);
	}
}
