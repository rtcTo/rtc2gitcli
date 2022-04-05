package to.rtc.cli.migrate.util;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.ignore.internal.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author christoph.erni
 */
public class JazzignoreTranslator {
	private static final Logger LOGGER = LoggerFactory.getLogger(JazzignoreTranslator.class);
	private static final Pattern EXCLUSION = Pattern.compile("\\{(.*?)\\}");
	private static final Method CONVERT_GLOB_METHOD = initConvertGlobMethod();

	/**
	 * Translates a .jazzignore file to gitignore format.
	 * 
	 * @param jazzignore
	 *            the input .jazzignore file
	 * 
	 * @return the translation, as a list of lines in gitignore format.
	 */
	public static List<String> toGitignore(File jazzignore) {
		List<String> gitignoreLines = new ArrayList<String>();
		gitignoreLines.add("# Generated from .jazzignore file");
		try {
			// default charset should be ok here
			List<String> jazzignoreLines = Files.readLines(jazzignore, Charset.defaultCharset());
			final StringBuilder lineForRegex = new StringBuilder();
			boolean needToTransform = false;
			for (String line : jazzignoreLines) {
				line = line.trim();
				if (!line.startsWith("#")) {
					needToTransform = true;
					lineForRegex.append(line);
					if (!line.endsWith("\\")) {
						addGroupsToList(lineForRegex.toString(), gitignoreLines);
						lineForRegex.setLength(0);
						needToTransform = false;
					}
				}
			}
			if (needToTransform) {
				addGroupsToList(lineForRegex.toString(), gitignoreLines);
			}
		} catch (IOException ioe) {
			throw new RuntimeException("unable to read .jazzignore file " + jazzignore.getAbsolutePath(), ioe);
		}
		return gitignoreLines;
	}

	private static Method initConvertGlobMethod() {
		try {
			Method method = Strings.class.getDeclaredMethod("convertGlob", String.class);
			method.setAccessible(true);
			return method;
		} catch (Exception e) {
			LOGGER.error("Unable to access needed method", e);
			return null;
		}
	}

	private static void addGroupsToList(String lineToMatch, List<String> listToAddTo) {
		boolean recursive = lineToMatch.startsWith("core.ignore.recursive");
		Matcher matcher = EXCLUSION.matcher(lineToMatch);
		listToAddTo.add("#   " + lineToMatch);
		while (matcher.find()) {
			String pattern = (!recursive ? "/" : "").concat(matcher.group(1));
			if (checkPattern(pattern)) {
				listToAddTo.add(pattern);
			} else {
			    listToAddTo.add("#   Invalid pattern: "+ pattern);
			}
		}
	}

	private static boolean checkPattern(String pattern) {
		if (CONVERT_GLOB_METHOD != null) {
			try {
				CONVERT_GLOB_METHOD.invoke(null, pattern);
			} catch (Exception e) {
				LOGGER.warn("Ignoring uncompilable pattern: {}", pattern);
				return false;
			}
		}
		return true;
	}
}
