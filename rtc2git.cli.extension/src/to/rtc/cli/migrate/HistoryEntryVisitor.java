package to.rtc.cli.migrate;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogBaselineEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogChangeSetEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogComponentEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogWorkItemEntryDTO;
import com.ibm.team.filesystem.rcp.core.internal.changelog.BaseChangeLogEntryVisitor;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;

import to.rtc.cli.migrate.util.DuplicateSuppressingChangeLogOutput;

public class HistoryEntryVisitor extends BaseChangeLogEntryVisitor {

	private final RtcTagList tags;
	private String component;
	private final Map<String, String> lastChangeSets;
	private boolean lastChangeSetReached;
	private final String rtcServerUrl;
	private final String rtcStreamId;
	private final AtomicInteger changeSetIndex = new AtomicInteger();

	public HistoryEntryVisitor(String rtcServerUrl, String rtcStreamId, RtcTagList tagList, Map<String, String> lastChangeSets, IChangeLogOutput out) {
		this.tags = tagList;
		setOutput(new DuplicateSuppressingChangeLogOutput(out));
		this.lastChangeSets = lastChangeSets;
		this.lastChangeSetReached = false;
		if( rtcServerUrl==null || rtcServerUrl.isEmpty() ) {
			this.rtcServerUrl = null;
		} else {
			if ( rtcServerUrl.endsWith("/")) {
				this.rtcServerUrl = rtcServerUrl;
			} else {
				this.rtcServerUrl = rtcServerUrl + "/";
			}
		}
		this.rtcStreamId = rtcStreamId;
	}

	public void acceptInto(ChangeLogEntryDTO root) {
		//debugLog("HEV.acceptInto(" + dtoToString(root) + ")");
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
		debugLog("HEV.visitChangeSet(" + dtoToString(parent) +", " + dtoToString(changeSetDto) + ")");
		if (lastChangeSetReached) {
			debugLog(2,"lastChangeSetReached - ignoring");
			return;
		}
		debugLog(2,"CreationDate="+RtcChangeSet.getDateAsString(changeSetDto.getCreationDate()));
		debugLog(2,"Creator.FullName="+(changeSetDto.getCreator()!=null?changeSetDto.getCreator().getFullName():null));
		debugLog(2,"EntryName="+changeSetDto.getEntryName());
		debugLog(2,"EntryType="+changeSetDto.getEntryType());
		debugLog(2,"OslcLinks="+changeSetDto.getOslcLinks());
		debugLog(2,"WorkItems="+changeSetDto.getWorkItems());
		String changeSetUuid = changeSetDto.getItemId();
		RtcChangeSet changeSet = new RtcChangeSet(changeSetUuid).setText(changeSetDto.getEntryName())
				.setServerURI(rtcServerUrl).setStreamId(rtcStreamId)
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
		changeSet.setHistoryOrderIndex(changeSetIndex.incrementAndGet());
		final RtcTag actualTag = getActualTag(parent);
		final boolean wasAdded = actualTag.add(changeSet);
		debugLog(2,(wasAdded?"Added":"FAILED to add") + " changeSet " + changeSetUuid + " to tag " + actualTag);
		if (lastChangeSets.get(component).equals(changeSetUuid)) {
			lastChangeSetReached = true;
			actualTag.setContainLastChangeset(true);
			debugLog(2,"This was the last changeset in tag "+actualTag);
		}
	}

	@Override
	protected void visitBaseline(ChangeLogEntryDTO parent, ChangeLogBaselineEntryDTO dto) {
		debugLog("HEV.visitBaseline(" + dtoToString(parent) +", " + dtoToString(dto) + ")");
		debugLog(2,"BaselineId="+dto.getBaselineId());
		debugLog(2,"CreationDate="+RtcChangeSet.getDateAsString(dto.getCreationDate()));
		debugLog(2,"Creator.FullName="+(dto.getCreator()!=null?dto.getCreator().getFullName():null));
		debugLog(2,"EntryName="+dto.getEntryName());
		debugLog(2,"EntryType="+dto.getEntryType());
		super.visitBaseline(parent, dto);
	}

	//@Override
	//protected void visitDirection(ChangeLogEntryDTO parent, ChangeLogDirectionEntryDTO dto) {
	//	debugLog("HEV.visitDirection(" + dtoToString(parent) +", " + dtoToString(dto) + ")");
	//	debugLog(2,"EntryName="+dto.getEntryName());
	//	debugLog(2,"EntryType="+dto.getEntryType());
	//	debugLog(2,"FlowDirection="+dto.getFlowDirection());
	//	super.visitDirection(parent, dto);
	//}

	//@Override
	//protected void visitOslcLink(ChangeLogEntryDTO parent, ChangeLogOslcLinkEntryDTO dto, boolean inChangeSet) {
	//	debugLog("HEV.visitOslcLink(" + dtoToString(parent) +", " + dtoToString(dto) + ", " + inChangeSet + ")");
	//	super.visitOslcLink(parent, dto, inChangeSet);
	//}

	//@Override
	//protected void visitVersionable(ChangeLogEntryDTO parent, ChangeLogVersionableEntryDTO dto) {
	//	debugLog("HEV.visitVersionable(" + dtoToString(parent) +", " + dtoToString(dto) + ")");
	//	debugLog(2,"EntryName="+dto.getEntryName());
	//	debugLog(2,"EntryType="+dto.getEntryType());
	//	debugLog(2,"Segments="+dto.getSegments());
	//	super.visitVersionable(parent, dto);
	//}

	//@Override
	//protected void visitWorkItem(ChangeLogEntryDTO parent, ChangeLogWorkItemEntryDTO dto, boolean inChangeSet) {
	//	debugLog("HEV.visitWorkItem(" + dtoToString(parent) +", " + dtoToString(dto) + ", " + inChangeSet + ")");
	//	debugLog(2,"EntryName="+dto.getEntryName());
	//	debugLog(2,"EntryType="+dto.getEntryType());
	//	debugLog(2,"Resolver.FullName="+(dto.getResolver()!=null?dto.getResolver().getFullName():null));
	//	debugLog(2,"WorkItemNumber="+dto.getWorkItemNumber());
	//	super.visitWorkItem(parent, dto, inChangeSet);
	//}

	//private int indent = 0;
	//@Override
	//public boolean enter(ChangeLogEntryDTO item) {
	//	debugLog("HEV.enter(" + dtoToString(item) + ")");
	//	indent++;
	//	return super.enter(item);
	//}

	//@Override
	//public void exit(ChangeLogEntryDTO item) {
	//	indent--;
	//	debugLog("HEV.exit(" + dtoToString(item) + ")");
	//	super.exit(item);
	//}

	private void debugLog(String msg) {
		debugLog(0,msg);
	}

	private void debugLog(int extraIndent, String msg) {
		//for(int i=0 ; i<indent ; i++ ) {
		//	System.out.print("    ");
		//}
		//for(int i=0 ; i<extraIndent ; i++ ) {
		//	System.out.print(" ");
		//}
		//System.out.println(msg);;
	}

	private String dtoToString(ChangeLogEntryDTO item) {
		final String dtoString = item.getClass().getSimpleName() + '(' + item.getItemId() + ')';
		return dtoString;
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
		debugLog("HEV.visitComponent(" + dtoToString(parent) +", " + dtoToString(dto) + ")");
		debugLog(2,"ChangeType="+dto.getChangeType());
		debugLog(2,"ContextWhereNotPresent="+dto.getContextWhereNotPresent());
		debugLog(2,"ContextWherePresent="+dto.getContextWherePresent());
		debugLog(2,"EntryName="+dto.getEntryName());
		debugLog(2,"EntryType="+dto.getEntryType());
		// dto.getEntryName() gives us the human-friendly component name BUT this is not
		// guaranteed to be unique.
		// dto.getItemId() should give us the component id, which is guaranteed to be
		// unique
		final String id = dto.getItemId();
		component = id;
		lastChangeSetReached = false;
	}
}
