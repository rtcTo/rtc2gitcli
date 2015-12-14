package to.rtc.cli.migrate.git;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RmCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;

import to.rtc.cli.migrate.ChangeSet;
import to.rtc.cli.migrate.ChangeSet.WorkItem;
import to.rtc.cli.migrate.Migrator;
import to.rtc.cli.migrate.Tag;
import to.rtc.cli.migrate.util.Files;

/**
 * Git implementation of a {@link Migrator}.
 *
 * @author patrick.reinhart
 */
public final class GitMigrator implements Migrator {
	static final List<String> ROOT_IGNORED_ENTRIES = Arrays.asList("/.jazz5",
			"/.jazzShed", "/.metadata");
	static final Pattern GITIGNORE_PATTERN = Pattern
			.compile("^.*(/|)\\.gitignore$");
	static final Pattern JAZZIGNORE_PATTERN = Pattern
			.compile("^.*(/|)\\.jazzignore$");

	private Git git;
	private Properties properties;
	private PersonIdent defaultIdent;

	private Charset getCharset() {
		return Charset.forName("UTF-8");
	}

	private void addMissing(List<String> values, List<String> entries) {
		for (String entry : entries) {
			if (!values.contains(entry)) {
				values.add(entry);
			}
		}
	}

	private void initRootGitIgnore(File sandboxRootDirectory)
			throws IOException {
		File rootGitIgnore = new File(sandboxRootDirectory, ".gitignore");
		List<String> ignored = Files.readLines(rootGitIgnore, getCharset());
		addMissing(ignored, ROOT_IGNORED_ENTRIES);
		Files.writeLines(rootGitIgnore, ignored, getCharset(), false);
	}

	String getCommitMessage(String workItemText, String comment) {
		return String.format(
				properties.getProperty("commit.message.format", "%1s %2s"),
				workItemText, comment).trim();
	}

	String getWorkItemNumbers(List<WorkItem> workItems) {
		if (workItems.isEmpty()) {
			return "";
		}
		final String format = properties.getProperty(
				"rtc.workitem.number.format", "%s");
		final String delimiter = properties.getProperty(
				"rtc.workitem.number.delimiter", " ");
		final StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		Formatter formatter = new Formatter(sb);
		try {
			for (WorkItem workItem : workItems) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(delimiter);
				}
				formatter.format(format, String.valueOf(workItem.getNumber()));
			}

		} finally {
			formatter.close();
		}
		return sb.toString();
	}

	private void gitCommit(PersonIdent ident, String comment) {
		try {
			// add all untracked files
			Status status = git.status().call();
			Set<String> toAdd = new HashSet<String>();
			Set<String> toRemove = new HashSet<String>();
			Set<String> toRestore = new HashSet<String>();

			// go over untracked files
			for (String untracked : status.getUntracked()) {
				// add it to the index
				toAdd.add(untracked);
			}
			// go over modified files
			for (String modified : status.getModified()) {
				// adds a modified entry to the index
				toAdd.add(modified);
			}

			// go over all deleted files
			for (String removed : status.getMissing()) {
				// adds a modified entry to the index
				if (GITIGNORE_PATTERN.matcher(removed).matches()) {
					// restore .gitignore files that where deleted
					toRestore.add(removed);
				} else {
					toRemove.add(removed);
				}
			}

			// write(gitDir.resolve("testfile"), "abc".getBytes());
			// toAdd.add("testfile");

			// delete(gitDir.resolve("testfile"));
			// toRemove.add("testfile");

			// execute the git index commands if needed
			if (!toAdd.isEmpty()) {
				AddCommand add = git.add();
				for (String filepattern : toAdd) {
					add.addFilepattern(filepattern);
				}
				add.call();
			}
			if (!toRemove.isEmpty()) {
				RmCommand rm = git.rm();
				for (String filepattern : toRemove) {
					rm.addFilepattern(filepattern);
				}
				rm.call();
			}
			if (!toRestore.isEmpty()) {
				CheckoutCommand checkout = git.checkout();
				for (String filepattern : toRestore) {
					checkout.addPath(filepattern);
				}
				checkout.call();
			}

			// execute commit if something has changed
			if (!toAdd.isEmpty() || !toRemove.isEmpty()) {
				git.commit().setMessage(comment).setAuthor(ident)
						.setCommitter(ident).call();
			}
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void init(File sandboxRootDirectory, Properties props) {
		this.properties = props;
		try {
			File bareGitDirectory = new File(sandboxRootDirectory, ".git");
			if (bareGitDirectory.exists()) {
				git = Git.open(sandboxRootDirectory);
			} else if (sandboxRootDirectory.exists()) {
				git = Git.init().setDirectory(sandboxRootDirectory).call();
			} else {
				throw new RuntimeException(bareGitDirectory + " does not exist");
			}
			defaultIdent = new PersonIdent(props.getProperty("user.name",
					"RTC 2 git"), props.getProperty("user.email",
					"rtc2git@rtc.to"));
			initRootGitIgnore(sandboxRootDirectory);
			gitCommit(new PersonIdent(defaultIdent, System.currentTimeMillis(),
					0), "Initial commit");
		} catch (IOException e) {
			throw new RuntimeException("Unable to initialize GIT repository", e);
		} catch (GitAPIException e) {
			throw new RuntimeException("Unable to initialize GIT repository", e);
		}
	}

	@Override
	public void close() {
		if (git != null) {
			git.close();
		}
	}

	@Override
	public void commitChanges(ChangeSet changeset) {
		gitCommit(
				new PersonIdent(changeset.getCreatorName(),
						changeset.getEmailAddress(),
						changeset.getCreationDate(), 0),
				getCommitMessage(getWorkItemNumbers(changeset.getWorkItems()),
						changeset.getComment()));
	}

	@Override
	public void createTag(Tag tag) {
		// not yet implemented
	}
}