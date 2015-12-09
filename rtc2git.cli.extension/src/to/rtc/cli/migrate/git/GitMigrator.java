
package to.rtc.cli.migrate.git;

import to.rtc.cli.migrate.ChangeSet;
import to.rtc.cli.migrate.Migrator;
import to.rtc.cli.migrate.Tag;

public class GitMigrator implements Migrator {

  @Override
  public void createTag(Tag tag) {
    System.out.println("GIT: create tag [" + tag.getName() + "].");

  }

  @Override
  public void commitChanges(ChangeSet changeset) {
    System.out.println("GIT: commit changes [" + changeset.getComment() + "].");
  }

}
