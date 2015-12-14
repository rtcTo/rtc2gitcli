package to.rtc.cli.migrate;

import java.io.File;
import java.util.Properties;

/**
 * @author florian.buehlmann
 * @author patrick.reinhart
 */
public interface Migrator {

	/**
	 * Initializes the migration implementation with the given
	 * <code>sandboxRootDirectory</code>.
	 * 
	 * @param sandboxRootDirectory
	 *            the sand box root directory
	 * @param properties
	 *            the migration properties
	 */
	void init(File sandboxRootDirectory, Properties properties);

	/**
	 * Releases all resources
	 */
	void close();

	void createTag(Tag tag);

	void commitChanges(ChangeSet changeSet);

}
