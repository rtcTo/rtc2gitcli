package to.rtc.cli.migrate;

import java.io.File;

/**
 * @author florian.buehlmann
 * @author patrick.reinhart
 */
public interface Migrator {

	/**
	 * Initializes the migration implementation with the given <code>sandboxRootDirectory</code>.
	 *
	 * @param sandboxRootDirectory
	 *            the sand box root directory
	 * @param isUpdate
	 *            if true, this migration is continuing on from a previous migration.
	 */
	void init(File sandboxRootDirectory, boolean isUpdate);

	/**
	 * Releases all resources
	 */
	void close();

	void createTag(Tag tag);

	void commitChanges(ChangeSet changeSet);

	void intermediateCleanup();

	boolean needsIntermediateCleanup();

}
