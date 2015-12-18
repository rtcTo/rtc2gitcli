package to.rtc.cli.migrate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ibm.team.filesystem.cli.core.Constants;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.rtc.cli.infrastructure.internal.core.CLIClientException;

import to.rtc.cli.migrate.command.AcceptCommandDelegate;
import to.rtc.cli.migrate.command.LoadCommandDelegate;

public class RtcMigrator {

	protected final IChangeLogOutput output;
	private final IScmClientConfiguration config;
	private final String workspace;
	private final Migrator migrator;
	private static final Set<String> initiallyLoadedComponents = new HashSet<String>();

	public RtcMigrator(IChangeLogOutput output, IScmClientConfiguration config,
			String workspace, Migrator migrator) {
		this.output = output;
		this.config = config;
		this.workspace = workspace;
		this.migrator = migrator;
	}

	public void migrateTag(RtcTag tag) throws CLIClientException {
		List<RtcChangeSet> changeSets = tag.getOrderedChangeSets();
		for (RtcChangeSet changeSet : changeSets) {
			acceptAndLoadChangeSet(changeSet);
			handleInitialLoad(changeSet);
			migrator.commitChanges(changeSet);
		}
		if (!"HEAD".equals(tag.getName())) {
			migrator.createTag(tag);
		}
	}

	private void acceptAndLoadChangeSet(RtcChangeSet changeSet)
			throws CLIClientException {
		int result = new AcceptCommandDelegate(config, output, workspace,
				changeSet.getUuid(), false, true).run();
		switch (result) {
		case Constants.STATUS_GAP:
			output.writeLine(
					"Retry accepting with --accept-missing-changesets");
			result = new AcceptCommandDelegate(config, output, workspace,
					changeSet.getUuid(), false, true).run();
			if (Constants.STATUS_GAP == result) {
				throw new CLIClientException(
						"There was a PROBLEM in accepting that we cannot solve.");
			}
			break;
		case Constants.STATUS_FAILURE:
			output.writeLine(
					"Got a failure during load action. Reload all components with force option.");
			new LoadCommandDelegate(config, output, workspace, null, true)
					.run();
			output.writeLine("Retry accepting");
			result = new AcceptCommandDelegate(config, output, workspace,
					changeSet.getUuid(), false, false).run();
			break;
		default:
			break;
		}
	}

	private void handleInitialLoad(RtcChangeSet changeSet) {
		if (!initiallyLoadedComponents.contains(changeSet.getComponent())) {
			try {
				new LoadCommandDelegate(config, output, workspace,
						changeSet.getComponent(), true).run();
				initiallyLoadedComponents.add(changeSet.getComponent());
			} catch (CLIClientException e) {// ignore
				throw new RuntimeException(
						"Not a valid sandbox. Please run [scm load " + workspace
								+ "] before [scm migrate-to-git] command");
			}
		}
	}

}
