
package to.rtc.cli.migrate.git;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RmCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;

import to.rtc.cli.migrate.ChangeSet;
import to.rtc.cli.migrate.Migrator;
import to.rtc.cli.migrate.Tag;
import to.rtc.cli.migrate.util.Files;

import com.ibm.team.repository.common.TeamRepositoryException;

public class GitMigrator implements Migrator {
  static final Pattern gitIgnorePattern = Pattern.compile("^.*(/|)\\.gitignore$");
  static final Pattern jazzIgnorePattern = Pattern.compile("^.*(/|)\\.jazzignore$");

  private Git git;

  private Charset getCharset() {
    return Charset.forName("UTF-8");
  }

  private void addMissing(List<String> values, String... entries) {
    for (String entry : entries) {
      if (!values.contains(entry)) {
        values.add(entry);
      }
    }
  }

  private void initRootGitIgnore(File sandboxRootDirectory) throws IOException {
    File rootGitIgnore = new File(sandboxRootDirectory, ".gitignore");
    List<String> ignored = Files.readLines(rootGitIgnore, getCharset());
    addMissing(ignored, "/.jazz5", "/.jazzShed", "/.metadata");
    Files.writeLines(rootGitIgnore, ignored, getCharset(), false);

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
        System.out.println("-: " + removed);
        // adds a modified entry to the index
        if (gitIgnorePattern.matcher(removed).matches()) {
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
        git.commit().setMessage(comment).setAuthor(ident).setCommitter(ident).call();
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void init(File sandboxRootDirectory) throws TeamRepositoryException {
    try {
      File bareGitDirectory = new File(sandboxRootDirectory, ".git");
      if (bareGitDirectory.exists()) {
        RepositoryBuilder repoBuilder = new RepositoryBuilder();
        repoBuilder.setGitDir(bareGitDirectory);
        repoBuilder.readEnvironment();
        repoBuilder.findGitDir();
        Repository repo = repoBuilder.build();
        git = new Git(repo);
      } else if (sandboxRootDirectory.exists()) {
        git = Git.init().setDirectory(sandboxRootDirectory).call();
      } else {
        throw new IOException(bareGitDirectory.getAbsolutePath() + " does not exist");
      }
      initRootGitIgnore(sandboxRootDirectory);
      gitCommit(new PersonIdent("Robocop", "john.doe@somewhere.com", System.currentTimeMillis(), 0), "initial commit");
    } catch (Exception e) {
      throw new TeamRepositoryException("Unable to initialize GIT repository", e);
    }
  }

  @Override
  public void close() {
    git.close();
  }

  @Override
  public void commitChanges(ChangeSet changeset) {
    gitCommit(new PersonIdent(changeset.getCreatorName(), changeset.getEmailAddress(), changeset.getCreationDate(), 0),
        changeset.getComment());
  }

  @Override
  public void createTag(Tag tag) {
    System.out.println("GIT: create tag [" + tag.getName() + "].");
  }
}