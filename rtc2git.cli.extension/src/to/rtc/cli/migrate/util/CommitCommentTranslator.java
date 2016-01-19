/**
 * File Name: CommitCommentTranslator.java
 * 
 * Copyright (c) 2016 BISON Schweiz AG, All Rights Reserved.
 */

package to.rtc.cli.migrate.util;

import java.util.Map.Entry;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Translates a commit comment based on a optional set of <code>commit.message.regex.x</code>/
 * <code>commit.message.replacement.x</code> definition applied in their <code>x</code> numerical order.
 *
 * @author Patrick Reinhart
 */
public class CommitCommentTranslator {
	private static final Logger LOGGER = LoggerFactory.getLogger(CommitCommentTranslator.class);
	private static final String REPLACEMENT_FORMAT = "commit.message.replacement.%s";
	private static final Pattern SEARCH_PATTERN = Pattern.compile("^commit\\.message\\.regex\\.([0-9]+)$");

	private final SortedMap<Integer, SearchEntry> searches = new TreeMap<Integer, SearchEntry>();

	/**
	 * @param props
	 */
	public CommitCommentTranslator(Properties props) {
		for (Entry<Object, Object> entry : props.entrySet()) {
			String key = entry.getKey().toString();
			Matcher matcher = SEARCH_PATTERN.matcher(key);
			if (matcher.matches()) {
				String order = matcher.group(1);
				String replacement = props.getProperty(String.format(REPLACEMENT_FORMAT, order));
				if (replacement == null) {
					LOGGER.warn("Search definition {} without any replacement definition.", key);
				} else {
					String pattern = entry.getValue().toString();
					try {
						Pattern searchPattern = Pattern.compile(pattern);
						if (searches.put(Integer.valueOf(order), new SearchEntry(searchPattern, replacement)) != null) {
							LOGGER.warn("Search definition {} already processed.", key);
						}
					} catch (NumberFormatException e) {
						LOGGER.error("Unable to parse order '{}' pattern '{}'", order, pattern);
					} catch (Exception e) {
						LOGGER.error("Unable to compile '{}' pattern '{}'", key, pattern);
					}
				}
			}
		}
	}

	/**
	 * Translates the given <code>input</code> and returns the resulting string.
	 * 
	 * @param input
	 *            the comment to be translated based on the defined Regex/replacement pairs
	 * @return the resulting string with all replacements applied.
	 */
	public String translate(String input) {
		String result = input;
		for (SearchEntry entry : searches.values()) {
			result = entry.apply(result);
		}
		return result;
	}

	static final class SearchEntry {
		private Pattern pattern;
		private String replacement;

		SearchEntry(Pattern pattern, String replacement) {
			this.pattern = pattern;
			this.replacement = replacement;
		}

		String apply(String input) {
			return pattern.matcher(input).replaceAll(replacement);
		}
	}
}
