package to.rtc.cli.migrate.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ShowSandboxStructureCommandOutputParser {
	private final boolean filesystemIsOutOfSync;
	private final String whatWeTookADislikeTo;
	private final Set<String> expectedRootContent;

	public ShowSandboxStructureCommandOutputParser(final List<String> toBeParsed) {
		final List<String> linesWeDidntLike = new ArrayList<String>();
		this.expectedRootContent = new TreeSet<String>();
		final String expectedSandboxLineStart = "Sandbox: ";
		String expectedLocalLineStart = null;
		String rootContentNameWithoutTrailingSlash = null;
		for (final String line : toBeParsed) {
			final String trimmedLine = line.trim();
			if (trimmedLine.startsWith("DelegateCommand [")) {
				continue;
			}
			// What we should see is "Sandbox: /somepath"
			// followed by zero or more " Local: /somepath/something" and " Remote:
			// workspaceName/somepath" lines.
			if (trimmedLine.isEmpty()) {
				continue;
			}
			if (expectedLocalLineStart == null && trimmedLine.startsWith(expectedSandboxLineStart)) {
				// We can work out where the sandbox is within our local filesystem from this
				// line.
				// That'll tell us what we need to remove from the "Local:" lines to get paths
				// relative to the sandbox root.
				// So we remove the prefix and that's our path.
				final String parsedSandboxRootPath = trimmedLine.substring(expectedSandboxLineStart.length());
				expectedLocalLineStart = "Local: " + parsedSandboxRootPath;
				continue;
			}
			if (expectedLocalLineStart != null && trimmedLine.startsWith(expectedLocalLineStart)) {
				// We can work out the name of something we expect to find in our sandbox from
				// this
				// It'll be of the form Local: /pathToWorkspace/expectedContent
				// ... but there will be no trailing / even if it's a directory, so we can't add
				// it
				// to our list of expected content yet.
				final String slashPath = trimmedLine.substring(expectedLocalLineStart.length());
				if (slashPath.startsWith("/") || slashPath.startsWith("\\")) {
					rootContentNameWithoutTrailingSlash = slashPath.substring(1);
					continue;
				}
			}
			if (rootContentNameWithoutTrailingSlash != null && trimmedLine.startsWith("Remote: ")) {
				// We can work out whether the previous "Local" entry was referring to a file
				// or a folder from here.
				final String rootContentNameWithSlashIfDirectory;
				if (trimmedLine.endsWith("/")) {
					rootContentNameWithSlashIfDirectory = rootContentNameWithoutTrailingSlash + "/";
				} else {
					rootContentNameWithSlashIfDirectory = rootContentNameWithoutTrailingSlash;
				}
				expectedRootContent.add(rootContentNameWithSlashIfDirectory);
				rootContentNameWithoutTrailingSlash = null;
				continue;
			}
			// anything else is a problem
			// e.g. "No projects were found in the sandbox." means RTC doesn't think it's
			// got anything there.
			linesWeDidntLike.add(line);
		}
		if (linesWeDidntLike.isEmpty()) {
			this.whatWeTookADislikeTo = null;
		} else {
			if (linesWeDidntLike.size() == 1) {
				this.whatWeTookADislikeTo = linesWeDidntLike.get(0);
			} else {
				this.whatWeTookADislikeTo = linesWeDidntLike.toString();
			}
		}
		this.filesystemIsOutOfSync = this.whatWeTookADislikeTo != null;
	}

	public boolean isFilesystemOutOfSync() {
		return filesystemIsOutOfSync;
	}

	public String whatWeTookADislikeTo() {
		return whatWeTookADislikeTo;
	}

	public Set<String> getExpectedRootContent() {
		return Collections.unmodifiableSet(expectedRootContent);
	}
}
