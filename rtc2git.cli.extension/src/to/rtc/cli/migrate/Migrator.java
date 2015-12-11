/**
 *
 */

package to.rtc.cli.migrate;

/**
 * @author florian.buehlmann
 *
 */
public interface Migrator {

  /**
   * Releases all resources
   */
  void close();

  void createTag(Tag tag);

  void commitChanges(ChangeSet changeSet);

}
