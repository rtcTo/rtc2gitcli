
package to.rtc.cli.migrate;

import java.util.Iterator;
import java.util.List;

import to.rtc.cli.migrate.command.AcceptCommandDelegate;
import to.rtc.cli.migrate.command.LoadCommandDelegate;

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

  private static final String CLENTRY_BASELINE_NAME = "clentry_baseline";
  private ChangeLogEntryDTO oldBaseline;
  private IScmClientConfiguration config;
  private String workspace;
  private boolean initialLoadDone;
  private final Migrator migrator;

  public ChangeLogEntryVisitor(IChangeLogOutput out, IScmClientConfiguration config, String workspace, Migrator migrator) {
    initialLoadDone = false;
    this.config = config;
    this.workspace = workspace;
    this.migrator = migrator;
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
    config
        .getContext()
        .stdout()
        .println(
            handleBaselineChange(parent) + " [" + changeSetUuid + "], Story [" + workItemText + "] Comment [" + dto.getEntryName()
                + "] User [" + dto.getCreator().getFullName() + "]");
    try {
      acceptAndLoadChangeSet(changeSetUuid);
      ChangeSet changeSet = new ChangeSet(changeSetUuid).setWorkItem(workItemText).setText(dto.getEntryName())
          .setCreatorName(dto.getCreator().getFullName()).setCreatorEMail(dto.getCreator().getEmailAddress())
          .setCreationDate(dto.getCreationDate());
      migrator.commitChanges(changeSet);
    } catch (CLIClientException e) {
      throw new RuntimeException(e);
    }
  }

  private void acceptAndLoadChangeSet(String changeSetUuid) throws CLIClientException {
    new AcceptCommandDelegate(config, workspace, changeSetUuid, false).run();
    handleInitialLoad();
  }

  private String handleBaselineChange(ChangeLogEntryDTO parent) {
    if (oldBaseline != null && !parent.getItemId().equals(oldBaseline.getItemId())) {
      if (CLENTRY_BASELINE_NAME.equals(oldBaseline.getEntryType())) {
        ChangeLogBaselineEntryDTO baseline = (ChangeLogBaselineEntryDTO)oldBaseline;
        // Accept baseline to target workspace
        try {
          Tag tag = new Tag(baseline.getItemId()).setName(baseline.getEntryName()).setCreationDate(baseline.getCreationDate());
          migrator.createTag(tag);
          acceptAndLoadBaseline(baseline.getItemId());
        } catch (CLIClientException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
      oldBaseline = parent;
    }

    if (CLENTRY_BASELINE_NAME.equals(parent.getEntryType())) {
      ChangeLogBaselineEntryDTO baseline = (ChangeLogBaselineEntryDTO)parent;
      oldBaseline = baseline;
      return baseline.getBaselineId() + ":" + baseline.getEntryName() + " --> ";
    } else {
      return " NO BASELINE --> ";
    }
  }

  private void acceptAndLoadBaseline(String baselineItemId) throws CLIClientException {
    new AcceptCommandDelegate(config, workspace, baselineItemId, true).run();
    handleInitialLoad();
  }

  @Override
  protected void visitBaseline(ChangeLogEntryDTO parent, ChangeLogBaselineEntryDTO dto) {
  }

  @Override
  protected void visitComponent(ChangeLogEntryDTO parent, ChangeLogComponentEntryDTO dto) {
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
