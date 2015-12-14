
package to.rtc.cli.migrate.git;

import static java.nio.file.Files.exists;
import static java.nio.file.Files.readAllLines;
import static java.nio.file.Files.write;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Arrays;
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
import to.rtc.cli.migrate.Migrator;
import to.rtc.cli.migrate.Tag;

/**
 * Git implementation of a {@link Migrator}.
 *
 * @author patrick.reinhart
 */
public final class GitMigrator implements Migrator {
  static final List<String> ROOT_IGNORED_ENTRIES = Arrays.asList("/.jazz5", "/.jazzShed", "/.metadata");
  static final Pattern GITIGNORE_PATTERN = Pattern.compile("^.*(/|)\\.gitignore$");
  static final Pattern JAZZIGNORE_PATTERN = Pattern.compile("^.*(/|)\\.jazzignore$");

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

  private void initRootGitIgnore(Path sandboxRootDirectory) throws IOException {
    Path rootGitIgnore = sandboxRootDirectory.resolve(".gitignore");
    List<String> ignored;
    if (exists(rootGitIgnore)) {
      ignored = readAllLines(rootGitIgnore, getCharset());
      addMissing(ignored, ROOT_IGNORED_ENTRIES);
    } else {
      ignored = ROOT_IGNORED_ENTRIES;
    }
    write(rootGitIgnore, ignored, getCharset());
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
        git.commit().setMessage(comment).setAuthor(ident).setCommitter(ident).call();
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void init(Path sandboxRootDirectory, Properties props) {
    this.properties = props;
    try {
      Path bareGitDirectory = sandboxRootDirectory.resolve(".git");
      if (exists(bareGitDirectory)) {
        git = Git.open(sandboxRootDirectory.toFile());
      } else if (exists(sandboxRootDirectory)) {
        git = Git.init().setDirectory(sandboxRootDirectory.toFile()).call();
      } else {
        throw new RuntimeException(bareGitDirectory + " does not exist");
      }
      defaultIdent = new PersonIdent(props.getProperty("user.name", "RTC 2 git"), props.getProperty("user.email", "rtc2git@rtc.to"));
      initRootGitIgnore(sandboxRootDirectory);
      gitCommit(new PersonIdent(defaultIdent, System.currentTimeMillis(), 0), "initial commit");
    } catch (IOException | GitAPIException e) {
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
    gitCommit(new PersonIdent(changeset.getCreatorName(), changeset.getEmailAddress(), changeset.getCreationDate(), 0),
        changeset.getComment());
  }

  @Override
  public void createTag(Tag tag) {
    System.out.println("GIT: create tag [" + tag.getName() + "].");
  }
}