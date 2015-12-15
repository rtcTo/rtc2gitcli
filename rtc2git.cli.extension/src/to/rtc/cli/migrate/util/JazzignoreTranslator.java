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
	static List<String> toGitignore(File jazzignore) {
		List<String> gitignoreLines = new ArrayList<String>();
		try {
			List<String> jazzignoreLines = Files.readLines(jazzignore,
					Charset.defaultCharset()); // default should be ok here
			String lineForRegex = "";
			for (String line : jazzignoreLines) {
				if (!line.startsWith("#")) {
					lineForRegex += line;
					if (!line.endsWith("\\")) {
						boolean leadingSlash = true;
						if (lineForRegex.startsWith("core.ignore.recursive")) {
							leadingSlash = false;
						}
						Matcher matcher = EXCLUSION.matcher(lineForRegex);
						while (matcher.find()) {
							String group = matcher.group();
							gitignoreLines.add((leadingSlash ? "/" : "")
									+ group.substring(1, group.length() - 1));
						}
						lineForRegex = "";
					}
				}
			}
		} catch (IOException ioe) {
			throw new RuntimeException("unable to read .jazzignore file "
					+ jazzignore.getAbsolutePath(), ioe);
		}
		return gitignoreLines;
	}
}
