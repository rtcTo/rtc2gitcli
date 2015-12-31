package to.rtc.cli.migrate.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author christoph.erni
 */
public class JazzignoreTranslator {

	private static final Pattern EXCLUSION = Pattern.compile("\\{(.*?)\\}");

	/**
	 * Translates a .jazzignore file to .gitignore
	 * 
	 * @param jazzignore
	 *            the input .jazzignore file
	 * 
	 * @return the translation, as a list of lines
	 */
	public static List<String> toGitignore(File jazzignore) {
		List<String> gitignoreLines = new ArrayList<String>();
		try {
			// default charset should be ok here
			List<String> jazzignoreLines = Files.readLines(jazzignore, Charset.defaultCharset());
			String lineForRegex = "";
			boolean needToTransform = false;
			for (String line : jazzignoreLines) {
				line = line.trim();
				if (!line.startsWith("#")) {
					needToTransform = true;
					lineForRegex += line;
					if (!line.endsWith("\\")) {
						addGroupsToList(lineForRegex, gitignoreLines);
						lineForRegex = "";
						needToTransform = false;
					}
				}
			}
			if (needToTransform) {
				gitignoreLines = addGroupsToList(lineForRegex, gitignoreLines);
			}
		} catch (IOException ioe) {
			throw new RuntimeException("unable to read .jazzignore file " + jazzignore.getAbsolutePath(), ioe);
		}
		return gitignoreLines;
	}

	private static List<String> addGroupsToList(String lineToMatch, List<String> ignoreLines) {
		Matcher matcher = EXCLUSION.matcher(lineToMatch);
		while (matcher.find()) {
			String group = matcher.group();
			boolean recursive = lineToMatch.startsWith("core.ignore.recursive");
			ignoreLines.add((!recursive ? "/" : "") + group.substring(1, group.length() - 1));
		}
		return ignoreLines;
	}

}
