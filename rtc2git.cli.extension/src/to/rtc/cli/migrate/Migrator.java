/**
 *
 */

package to.rtc.cli.migrate;

import java.io.File;

import com.ibm.team.repository.common.TeamRepositoryException;

/**
 * @author florian.buehlmann
 * @author patrick.reinhart
 */
public interface Migrator extends AutoCloseable {

  /**
   * Initializes the migration implementation with the given <code>sandboxRootDirectory</code>.
   * 
   * @param sandboxRootDirectory the sand box root directory
   */
  void init(File sandboxRootDirectory) throws TeamRepositoryException;

  /**
   * Releases all resources
   */
  @Override
  void close();

  void createTag(Tag tag);

  void commitChanges(ChangeSet changeSet);

}
