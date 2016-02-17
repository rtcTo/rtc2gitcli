package to.rtc.cli.migrate;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogBaselineEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogChangeSetEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogComponentEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogWorkItemEntryDTO;
import com.ibm.team.filesystem.rcp.core.internal.changelog.BaseChangeLogEntryVisitor;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;

public class HistoryEntryVisitor extends BaseChangeLogEntryVisitor {

	private final RtcTagList tags;
	private String component;
	private final Map<String, String> lastChangeSets;
	private boolean lastChangeSetReached;

	public HistoryEntryVisitor(RtcTagList tagList, Map<String, String> lastChangeSets, IChangeLogOutput out) {
		this.tags = tagList;
		setOutput(out);
		this.lastChangeSets = lastChangeSets;
		this.lastChangeSetReached = false;
	}

	public void acceptInto(ChangeLogEntryDTO root) {
		if (!enter(root)) {
			return;
		}
		for (Iterator<?> iterator = root.getChildEntries().iterator(); iterator.hasNext();) {
			ChangeLogEntryDTO child = (ChangeLogEntryDTO) iterator.next();
			visitChild(root, child);
			acceptInto(child);
		}

		exit(root);
	}

	@Override
	protected void visitChangeSet(ChangeLogEntryDTO parent, ChangeLogChangeSetEntryDTO changeSetDto) {
		if (lastChangeSetReached) {
			return;
		}
		String changeSetUuid = changeSetDto.getItemId();
		RtcChangeSet changeSet = new RtcChangeSet(changeSetUuid).setText(changeSetDto.getEntryName())
				.setCreatorName(changeSetDto.getCreator().getFullName())
				.setCreatorEMail(changeSetDto.getCreator().getEmailAddress())
				.setCreationDate(changeSetDto.getCreationDate()).setComponent(component);
		@SuppressWarnings("unchecked")
		List<ChangeLogWorkItemEntryDTO> workItems = changeSetDto.getWorkItems();
		if (workItems != null && !workItems.isEmpty()) {
			for (ChangeLogWorkItemEntryDTO workItem : workItems) {
				changeSet.addWorkItem(workItem.getWorkItemNumber(), workItem.getEntryName());
			}
		}
		RtcTag actualTag = getActualTag(parent);
		actualTag.add(changeSet);
		if (lastChangeSets.get(component).equals(changeSetUuid)) {
			lastChangeSetReached = true;
			actualTag.setContainLastChangeset(true);
		}
	}

	private RtcTag getActualTag(ChangeLogEntryDTO parent) {
		if (parent instanceof ChangeLogBaselineEntryDTO) {
			final ChangeLogBaselineEntryDTO dto = (ChangeLogBaselineEntryDTO) parent;
			return tags.getTag(dto.getItemId(), dto.getEntryName(), dto.getCreationDate());
		} else {
			return tags.getHeadTag();
		}
	}

	@Override
	protected void visitComponent(ChangeLogEntryDTO parent, ChangeLogComponentEntryDTO dto) {
		component = dto.getEntryName();
		lastChangeSetReached = false;
	}
}
