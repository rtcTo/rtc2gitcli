/**
 *
 */

package to.rtc.cli.migrate;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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

/**
 * @author florian.buehlmann
 *
 */
public class TagLogEntryVisitor extends BaseChangeLogEntryVisitor {

  private ChangeLogEntryDTO oldBaseline;
  private ChangeLogChangeSetEntryDTO oldChangeset;
  private final Map<String, Tag> tagMap;

  public TagLogEntryVisitor(IChangeLogOutput out) {
    tagMap = new HashMap<String, Tag>();
    setOutput(out);
  }

  public Map<String, Tag> acceptInto(ChangeLogEntryDTO root) {
    if (!enter(root)) return tagMap;
    for (Iterator<?> iterator = root.getChildEntries().iterator(); iterator.hasNext();) {
      ChangeLogEntryDTO child = (ChangeLogEntryDTO)iterator.next();
      visitChild(root, child);
      acceptInto(child);
    }

    exit(root);
    return tagMap;
  }

  @Override
  protected void visitChangeSet(ChangeLogEntryDTO parent, ChangeLogChangeSetEntryDTO dto) {
    if (oldBaseline == null) {
      oldBaseline = parent;
      oldChangeset = dto;
    } else if (!parent.getItemId().equals(oldBaseline.getItemId())) {
      if ("clentry_baseline".equals(oldBaseline.getEntryType())) {
        ChangeLogBaselineEntryDTO baseline = (ChangeLogBaselineEntryDTO)oldBaseline;
        Tag tag = new Tag(baseline.getItemId()).setName(baseline.getEntryName()).setCreationDate(baseline.getCreationDate());
        tagMap.put(oldChangeset.getItemId(), tag);
      }
      oldBaseline = parent;
    }
    oldChangeset = dto;
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
