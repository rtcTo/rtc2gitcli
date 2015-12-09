/**
 *
 */

package to.rtc.cli.migrate;

/**
 * @author florian.buehlmann
 *
 */
public interface Migrator {
  void createTag(Tag tag);

  void commitChanges(ChangeSet changeSet);

}
