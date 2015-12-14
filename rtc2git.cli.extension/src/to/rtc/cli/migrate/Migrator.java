/**
 *
 */

package to.rtc.cli.migrate;

import java.nio.file.Path;
import java.util.Properties;

/**
 * @author florian.buehlmann
 * @author patrick.reinhart
 */
public interface Migrator extends AutoCloseable {

	/**
	 * Initializes the migration implementation with the given
	 * <code>sandboxRootDirectory</code>.
	 * 
	 * @param sandboxRootDirectory
	 *            the sand box root directory
	 * @param properties
	 *            the migration properties
	 */
	void init(Path sandboxRootDirectory, Properties properties);

	/**
	 * Releases all resources
	 */
	@Override
	void close();

	void createTag(Tag tag);

	void commitChanges(ChangeSet changeSet);

}
