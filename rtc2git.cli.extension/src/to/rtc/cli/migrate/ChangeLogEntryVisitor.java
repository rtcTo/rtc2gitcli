
package to.rtc.cli.migrate;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import to.rtc.cli.migrate.command.AcceptCommandDelegate;
import to.rtc.cli.migrate.command.LoadCommandDelegate;

import com.ibm.team.filesystem.cli.core.Constants;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogBaselineEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogChangeSetEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogComponentEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogDirectionEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogOslcLinkEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogVersionableEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogWorkItemEntryDTO;
import com.ibm.team.filesystem.rcp.core.internal.changelog.BaseChangeLogEntryVisitor;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.rtc.cli.infrastructure.internal.core.CLIClientException;

public class ChangeLogEntryVisitor extends BaseChangeLogEntryVisitor {

  private IScmClientConfiguration config;
  private String workspace;
  private boolean initialLoadDone;
  private final Migrator migrator;
  private Map<String, Tag> tagMap;

  public ChangeLogEntryVisitor(IChangeLogOutput out, IScmClientConfiguration config, String workspace, Migrator migrator,
      Map<String, Tag> tagMap) {
    initialLoadDone = false;
    this.config = config;
    this.workspace = workspace;
    this.migrator = migrator;
    this.tagMap = tagMap;
    setOutput(out);
  }

  public void init() {
    handleInitialLoad();
    initialLoadDone = false;
  }

  private void handleInitialLoad() {
    if (!initialLoadDone) {
      try {
        new LoadCommandDelegate(config, workspace, true).run();
        initialLoadDone = true;
      } catch (CLIClientException e) {// ignore
        throw new RuntimeException("Not a valid sandbox. Please run [scm load " + workspace + "] before [scm migrate-to-git] command");
      }
    }
  }

  void acceptInto(ChangeLogEntryDTO root) {
    if (!enter(root)) {
      return;
    }
    for (Iterator<?> iterator = root.getChildEntries().iterator(); iterator.hasNext();) {
      ChangeLogEntryDTO child = (ChangeLogEntryDTO)iterator.next();
      visitChild(root, child);
      acceptInto(child);
    }

    exit(root);
  }

  @Override
  protected void visitChangeSet(ChangeLogEntryDTO parent, ChangeLogChangeSetEntryDTO dto) {
    String workItemText = "";
    List<?> workItems = dto.getWorkItems();
    if (workItems != null && !workItems.isEmpty()) {
      final ChangeLogWorkItemEntryDTO workItem = (ChangeLogWorkItemEntryDTO)workItems.get(0);
      workItemText = workItem.getWorkItemNumber() + ": " + workItem.getEntryName();
      if (workItemText.length() > 10) {
        workItemText = workItemText.substring(0, 10);
      }
    }
    final String changeSetUuid = dto.getItemId();
    try {
      acceptAndLoadChangeSet(changeSetUuid);
      ChangeSet changeSet = (new ChangeSet(changeSetUuid)).setWorkItem(workItemText).setText(dto.getEntryName())
          .setCreatorName(dto.getCreator().getFullName()).setCreatorEMail(dto.getCreator().getEmailAddress())
          .setCreationDate(dto.getCreationDate());
      migrator.commitChanges(changeSet);
      handleBaselineChange(dto);
    } catch (CLIClientException e) {
      throw new RuntimeException(e);
    }
  }

  private PrintStream stdout() {
    return config.getContext().stdout();
  }

  private void handleBaselineChange(ChangeLogEntryDTO parent) throws CLIClientException {
    Tag tag = tagMap.get(parent.getItemId());
    if (tag != null) {
      migrator.createTag(tag);
      acceptAndLoadBaseline(tag.getUuid());
    }
  }

  private void acceptAndLoadBaseline(String baselineItemId) {
    // Baselines could not be successfully accepted by rtc cli, therefore do not do it
    // new AcceptCommandDelegate(config, workspace, baselineItemId, true).run();
    handleInitialLoad();
  }

  private void acceptAndLoadChangeSet(String changeSetUuid) throws CLIClientException {
    int result = new AcceptCommandDelegate(config, workspace, changeSetUuid, false, false).run();
    if (Constants.STATUS_GAP == result) {
      stdout().println("Retry accepting with --accept-missing-changesets");
      result = new AcceptCommandDelegate(config, workspace, changeSetUuid, false, true).run();
      if (Constants.STATUS_GAP == result) {
        throw new CLIClientException("There was a GAP in accepting that we cannot resolve.");
      }

    }
    handleInitialLoad();
  }

  @Override
  protected void visitBaseline(ChangeLogEntryDTO parent, ChangeLogBaselineEntryDTO dto) {
  }

  @Override
  protected void visitComponent(ChangeLogEntryDTO parent, ChangeLogComponentEntryDTO dto) {
    stdout().println("------------------------------------------------------------------");
    stdout().println("------------------------------------------------------------------");
    stdout().println("------------------------------------------------------------------");
    stdout().println("---------------------" + dto.getEntryName() + "----------------------------");
    stdout().println("------------------------------------------------------------------");
    stdout().println("------------------------------------------------------------------");
    stdout().println("------------------------------------------------------------------");
  }

  @Override
  protected void visitDirection(ChangeLogEntryDTO parent, ChangeLogDirectionEntryDTO dto) {
  }

  @Override
  protected void visitOslcLink(ChangeLogEntryDTO parent, ChangeLogOslcLinkEntryDTO dto) {
  }

  @Override
  protected void visitVersionable(ChangeLogEntryDTO parent, ChangeLogVersionableEntryDTO dto) {
  }

  @Override
  protected void visitWorkItem(ChangeLogEntryDTO parent, ChangeLogWorkItemEntryDTO dto, boolean inChangeSet) {
  }

}
