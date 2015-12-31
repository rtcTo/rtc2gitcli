package to.rtc.cli.migrate.git;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RmCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * @author patrick.reinhart
 * @see <a href="https://github.com/centic9/jgit-cookbook">https://github.com/ centic9/jgit-cookbook</a>
 */
public class GitPlainTest {
	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	static final Pattern gitIgnorePattern = Pattern.compile("^.*(/|)\\.gitignore$");
	static final Pattern jazzIgnorePattern = Pattern.compile("^.*(/|)\\.jazzignore$");

	@Test
	public void plainJGit() throws Exception {
		File gitDir = tempFolder.getRoot();
		Git git = init(gitDir);
		try {
			// sample add of a boolean configuration entry
			StoredConfig config = git.getRepository().getConfig();
			config.setBoolean("core", null, "ignoreCase", false);
			config.save();

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
				System.out.println("-: " + removed);
				// adds a modified entry to the index
				if (gitIgnorePattern.matcher(removed).matches()) {
					// restore .gitignore files that where deleted
					toRestore.add(removed);
				} else {
					toRemove.add(removed);
				}
			}

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
				PersonIdent ident = new PersonIdent("Robocop", "john.doe@somewhere.com", System.currentTimeMillis(),
						(int) TimeUnit.HOURS.toMillis(1));
				git.commit().setMessage("auto commit").setAuthor(ident).setCommitter(ident).call();
			}
		} finally {
			git.close();
		}
	}

	private Git init(File gitDir) throws Exception {
		if (new File(gitDir, ".git").exists()) {
			return Git.open(gitDir);
		} else {
			return Git.init().setDirectory(gitDir).call();
		}
	}
}