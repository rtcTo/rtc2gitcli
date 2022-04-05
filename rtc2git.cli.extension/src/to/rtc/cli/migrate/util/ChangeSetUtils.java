package to.rtc.cli.migrate.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ibm.team.filesystem.client.internal.rest.CommonUtil;
import com.ibm.team.links.common.ILink;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.IContributorHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.scm.client.IChangeHistory;
import com.ibm.team.scm.client.IChangeHistoryDescriptor;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.client.IWorkspaceManager;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.common.IChangeHistoryEntryChange;
import com.ibm.team.scm.common.IChangeSet;
import com.ibm.team.scm.common.IChangeSetHandle;
import com.ibm.team.scm.common.IComponentHandle;
import com.ibm.team.scm.common.IWorkspaceHandle;
import com.ibm.team.scm.common.dto.IGapFillingChangeSetsReport;
import com.ibm.team.scm.common.links.ChangeSetLinks;
import com.ibm.team.scm.common.providers.ProviderFactory;
import com.ibm.team.workitem.common.model.IWorkItem;
import com.ibm.team.workitem.common.model.IWorkItemHandle;

import to.rtc.cli.migrate.RtcChangeSet;
import to.rtc.cli.migrate.RtcTag;
import to.rtc.cli.migrate.RtcTagList;

public class ChangeSetUtils {
	/**
	 * Figures out if there are any pre-requisite changesets we need to accept
	 * before we can accept the one specified.
	 * <p>
	 * <b>WARNING:</b> This has been found to produce spurious results, e.g.
	 * identifying changesets that form no part of the history of the stream which,
	 * if accepted, cause a storm of merge conflicts and ultimately break the entire
	 * migration.
	 * 
	 * @param iTeamRepository      The {@link ITeamRepository} to use when talking
	 *                             to RTC.
	 * @param iWorkspace           The {@link IWorkspaceHandle} of the workspace
	 *                             we're using.
	 * @param desiredChangeSetUuid The changeset we want to accept.
	 * 
	 * @return A list (which may be empty) of changeset UUIDs that we need to accept
	 *         first.
	 */
	public static List<String> identifyChangeSetsWeNeedToAcceptBeforeThisOne(ITeamRepository iTeamRepository,
			IWorkspaceHandle iWorkspace, String desiredChangeSetUuid) {
		final IProgressMonitor monitor = null;
		final IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(iTeamRepository);
		try {
			final IWorkspaceConnection sourceWsConnection = workspaceManager.getWorkspaceConnection(iWorkspace,
					monitor);
			final IChangeSetHandle desiredChangeSetHandle = CommonUtil.createChangeSetHandle(iTeamRepository,
					desiredChangeSetUuid);
			final IChangeSetHandle[] desiredChangeSetHandles = new IChangeSetHandle[] { desiredChangeSetHandle };
			final List<IGapFillingChangeSetsReport> reports = sourceWsConnection
					.findChangeSetsToAcceptToFillGap(desiredChangeSetHandles, monitor).getReports();
			final List<String> result = new ArrayList<String>();
			for (final IGapFillingChangeSetsReport report : reports) {
				final List<IChangeSetHandle> changeSetHandles = report.getChangeSets();
				for (final IChangeSetHandle changeSetHandle : changeSetHandles) {
					final String changeSetUuid = changeSetHandle.getItemId().getUuidValue();
					result.add(changeSetUuid);
				}
			}
			result.remove(desiredChangeSetUuid);
			return result;
		} catch (TeamRepositoryException e) {
			throw new RuntimeException(
					"Failed to identifyChangeSetsWeNeedToAcceptBeforeThisOne(" + desiredChangeSetUuid + ")", e);
		}
	}

	/**
	 * Looks up the fine details of zero or more changesets given only their UUIDs.
	 * 
	 * @param iTeamRepository The {@link ITeamRepository} to use when talking to
	 *                        RTC.
	 * @param iWorkspace      The {@link IWorkspaceHandle} of the workspace we're
	 *                        using.
	 * @param changeSetUuids  The UUIDs of the changesets to be looked up.
	 * @return A {@link Map} of the fully-populated {@link RtcChangeSet}s requested,
	 *         indexed by the requested UUIDs.
	 */
	@SuppressWarnings("deprecation") // ChangeSetLinks.findLinks
	public static Map<String, RtcChangeSet> getChangeSetDetails(ITeamRepository iTeamRepository,
			IWorkspaceHandle iWorkspace, Iterable<String> changeSetUuids) {
		final IProgressMonitor monitor = null;
		final String serverUri = iTeamRepository.getRepositoryURI();
		final IItemManager itemManager = iTeamRepository.itemManager();
		// IWorkspaceConnection workspaceConnection = null;
		final String workspace = iWorkspace.getItemId().getUuidValue();
		final Map<String, RtcChangeSet> result = new HashMap<String, RtcChangeSet>();
		for (final String changeSetUuid : changeSetUuids) {
			try {
				final IChangeSetHandle changeSetHandle = CommonUtil.createChangeSetHandle(iTeamRepository,
						changeSetUuid);
				final IChangeSet changeSetDetails = (IChangeSet) itemManager.fetchCompleteItem(changeSetHandle,
						IItemManager.DEFAULT, monitor);
				final String comment = changeSetDetails.getComment();
				final IComponentHandle componentHandle = changeSetDetails.getComponent();
				final IContributorHandle authorHandle = changeSetDetails.getAuthor();
				final IContributor author = (IContributor) itemManager.fetchCompleteItem(authorHandle,
						IItemManager.DEFAULT, monitor);
				final String authorEmailAddress = author.getEmailAddress();
				final String authorName = author.getName();
				final Date lastChangeDate = changeSetDetails.getLastChangeDate();
				final String changeSetUuidValue = changeSetDetails.getItemId().getUuidValue();
				final RtcChangeSet ourCs = new RtcChangeSet(changeSetUuidValue);
				ourCs.setComponent(componentHandle.getItemId().getUuidValue());
				ourCs.setCreationDate(lastChangeDate.getTime());
				ourCs.setCreatorEMail(authorEmailAddress);
				ourCs.setCreatorName(authorName);
				ourCs.setServerURI(serverUri);
				ourCs.setStreamId(workspace);
				ourCs.setText(comment);
				final ProviderFactory providerFactory = (ProviderFactory) iTeamRepository
						.getClientLibrary(ProviderFactory.class);
				final List<ILink> linksToChangeSet = ChangeSetLinks.findLinks(providerFactory, changeSetHandle,
						monitor);
				for (final ILink linkToChangeSet : linksToChangeSet) {
					final Object linkTarget = linkToChangeSet.getTargetRef().resolve();
					if (linkTarget instanceof IWorkItemHandle) {
						final IWorkItemHandle workItemHandle = (IWorkItemHandle) linkTarget;
						final IWorkItem workItem = (IWorkItem) itemManager.fetchCompleteItem(workItemHandle,
								IItemManager.DEFAULT, monitor);
						final long workItemNumber = workItem.getId();
						final String workItemText = workItem.getHTMLSummary().getPlainText();
						ourCs.addWorkItem(workItemNumber, workItemText);
					}
				}
				result.put(changeSetUuid, ourCs);
			} catch (TeamRepositoryException e) {
				throw new RuntimeException("Failed to getChangeSetDetails for " + changeSetUuid, e);
			}
		}
		return result;
	}

	private static RtcChangeSet getChangeSetDetails(ITeamRepository iTeamRepository, IWorkspaceHandle iWorkspace,
			String changeSetUuid) {
		final ArrayList<String> l = new ArrayList<String>(1);
		l.add(changeSetUuid);
		final Map<String, RtcChangeSet> changeSetDetails = getChangeSetDetails(iTeamRepository, iWorkspace, l);
		return changeSetDetails.values().iterator().next();
	}

	/**
	 * Replaces all the {@link RtcChangeSet}s within a {@link RtcTag} with
	 * normalised ones.
	 * 
	 * @param tag             The tag whose contents are to be populated.
	 * @param iTeamRepository The {@link ITeamRepository} to use when talking to
	 *                        RTC.
	 * @param iWorkspace      The {@link IWorkspaceHandle} of the workspace we're
	 *                        using.
	 */
	private static void normaliseChangeSetsInTag(RtcTag tag, ITeamRepository iTeamRepository,
			IWorkspaceHandle iWorkspace) {
		final Map<String, List<RtcChangeSet>> componentsChangeSets = tag.getComponentsChangeSets();
		final Map<String, List<RtcChangeSet>> componentUuidToNormalisedChangeSets = new HashMap<String, List<RtcChangeSet>>();
		for (Map.Entry<String, List<RtcChangeSet>> e : componentsChangeSets.entrySet()) {
			final List<RtcChangeSet> changeSetList = e.getValue();
			final Map<RtcChangeSet, String> originalCsToUuid = new HashMap<RtcChangeSet, String>();
			for (final RtcChangeSet originalCs : changeSetList) {
				originalCsToUuid.put(originalCs, originalCs.getChangesetID());
			}
			final Map<String, RtcChangeSet> uuidToNewCs = getChangeSetDetails(iTeamRepository, iWorkspace,
					originalCsToUuid.values());
			final ListIterator<RtcChangeSet> li = changeSetList.listIterator();
			while (li.hasNext()) {
				final RtcChangeSet originalCs = li.next();
				final String id = originalCsToUuid.get(originalCs);
				final RtcChangeSet fullyPopulatedCs = uuidToNewCs.get(id);
				if (fullyPopulatedCs == null) {
					throw new IllegalStateException(originalCs + " was not found in fully-populated dataset");
				}
				fullyPopulatedCs.setHistoryOrderIndex(originalCs.getHistoryOrderIndex());
				final String componentUuid = fullyPopulatedCs.getComponent();
				final List<RtcChangeSet> normalisedChangeSets;
				if (componentUuidToNormalisedChangeSets.containsKey(componentUuid)) {
					normalisedChangeSets = componentUuidToNormalisedChangeSets.get(componentUuid);
				} else {
					normalisedChangeSets = new ArrayList<RtcChangeSet>();
					componentUuidToNormalisedChangeSets.put(componentUuid, normalisedChangeSets);
				}
				normalisedChangeSets.add(fullyPopulatedCs);
			}
		}
		componentsChangeSets.clear();
		componentsChangeSets.putAll(componentUuidToNormalisedChangeSets);
	}

	/**
	 * Replaces all the {@link RtcChangeSet}s within a {@link RtcTagList} with
	 * fully-populated normalised ones.
	 * <p>
	 * <b>NOTE:</b> This might take a while and may be resource-intensive. It has
	 * to parse the entire stream's history, for each component within the stream,
	 * because RTC doesn't give us any means of asking about just the changesets
	 * we're interested in.
	 * 
	 * @param tagList         The list whose contents are to be populated.
	 * @param iTeamRepository The {@link ITeamRepository} to use when talking to
	 *                        RTC.
	 * @param iWorkspace      The {@link IWorkspaceHandle} of the workspace we're
	 *                        using.
	 */
	public static void fullyPopulateChangeSetsInTagList(RtcTagList tagList, ITeamRepository iTeamRepository,
			IWorkspaceHandle iWorkspace) {
		final Map<String, Map<String, RtcChangeSet>> compUuidToCSUuidToCS = new HashMap<String, Map<String, RtcChangeSet>>();
		final Iterator<RtcTag> iter = tagList.iterator();
		while (iter.hasNext()) {
			final RtcTag tag = iter.next();
			normaliseChangeSetsInTag(tag, iTeamRepository, iWorkspace);
			for (final Map.Entry<String, List<RtcChangeSet>> e : tag.getComponentsChangeSets().entrySet()) {
				final String componentId = e.getKey();
				final List<RtcChangeSet> changeSets = e.getValue();
				final Map<String, RtcChangeSet> changeSetUuidToChangeSet;
				if (!compUuidToCSUuidToCS.containsKey(componentId)) {
					changeSetUuidToChangeSet = new HashMap<String, RtcChangeSet>();
					compUuidToCSUuidToCS.put(componentId, changeSetUuidToChangeSet);
				} else {
					changeSetUuidToChangeSet = compUuidToCSUuidToCS.get(componentId);
				}
				for (final RtcChangeSet changeSet : changeSets) {
					changeSetUuidToChangeSet.put(changeSet.getChangesetID(), changeSet);
				}
			}
		}
		final Map<String, String> committerUuidToCommitterName = new HashMap<String, String>();
		final Map<String, String> committerUuidToCommitterEmail = new HashMap<String, String>();
		try {
			final IProgressMonitor monitor = null;
			final IItemManager itemManager = iTeamRepository.itemManager();
			final IWorkspaceConnection wsConnection = SCMPlatform.getWorkspaceManager(iTeamRepository)
					.getWorkspaceConnection(iWorkspace, monitor);
			int componentNumber = 0;
			for (final Map.Entry<String, Map<String, RtcChangeSet>> e : compUuidToCSUuidToCS.entrySet()) {
				final String componentId = e.getKey();
				final Map<String, RtcChangeSet> changeSetUuidToChangeSet = e.getValue();
				final IComponentHandle componentHandle = CommonUtil.createComponentHandle(iTeamRepository, componentId);
				final List<RtcChangeSet> componentChangesetsInOrderFromBeginningOfTime = new ArrayList<RtcChangeSet>();
				IChangeHistory ch = wsConnection.changeHistory(componentHandle);
				while (ch != null) {
					final IChangeHistoryDescriptor chDescriptor = ch.getHistoryDescriptor(false, monitor);
					final List<IChangeHistoryEntryChange> partialChangeHistory = chDescriptor.recent();
					final List<RtcChangeSet> componentChangesetsThisEraInDesiredOrder = new ArrayList<RtcChangeSet>();
					for (final IChangeHistoryEntryChange chec : partialChangeHistory) {
						final String changeSetUuid = chec.changeSet().getItemId().getUuidValue();
						final RtcChangeSet rtcChangeSet;
						if (changeSetUuidToChangeSet.containsKey(changeSetUuid)) {
							rtcChangeSet = changeSetUuidToChangeSet.get(changeSetUuid);
						} else {
							rtcChangeSet = getChangeSetDetails(iTeamRepository, iWorkspace, changeSetUuid);
						}
						final IContributorHandle addedToStreamBy = chec.createdBy();
						final long addedToStreamOn = chec.creationDate().getTime();
						final String addedToStreamByUuid = addedToStreamBy.getItemId().getUuidValue();
						final String addedToStreamByName;
						final String addedToStreamByEmail;
						if (committerUuidToCommitterName.containsKey(addedToStreamByUuid)) {
							addedToStreamByName = committerUuidToCommitterName.get(addedToStreamByUuid);
							addedToStreamByEmail = committerUuidToCommitterEmail.get(addedToStreamByUuid);
						} else {
							final IContributor committer = (IContributor) itemManager
									.fetchCompleteItem(addedToStreamBy, IItemManager.DEFAULT, monitor);
							addedToStreamByEmail = committer.getEmailAddress();
							addedToStreamByName = committer.getName();
							committerUuidToCommitterName.put(addedToStreamByUuid, addedToStreamByName);
							committerUuidToCommitterEmail.put(addedToStreamByUuid, addedToStreamByEmail);
						}
						rtcChangeSet.setAddedToStreamDate(addedToStreamOn);
						rtcChangeSet.setAddedToStreamEMail(addedToStreamByEmail);
						rtcChangeSet.setAddedToStreamName(addedToStreamByName);
						componentChangesetsThisEraInDesiredOrder.add(rtcChangeSet);
					}
					componentChangesetsInOrderFromBeginningOfTime.addAll(0, componentChangesetsThisEraInDesiredOrder);
					ch = chDescriptor.previousHistory();
				}
				int historyOrderIndex = componentNumber;
				for (final RtcChangeSet rtcChangeSet : componentChangesetsInOrderFromBeginningOfTime) {
					rtcChangeSet.setHistoryOrderIndex(historyOrderIndex);
					rtcChangeSet.setFullyPopulated(true);
					rtcChangeSet.setAddedToStreamName(mungeUserName(rtcChangeSet.getAddedToStreamName()));
					rtcChangeSet.setCreatorName(mungeUserName(rtcChangeSet.getCreatorName()));
					rtcChangeSet.setAddedToStreamEMail(mungeEmailAddress(rtcChangeSet.getAddedToStreamEmailAddress()));
					rtcChangeSet.setCreatorEMail(mungeEmailAddress(rtcChangeSet.getEmailAddress()));
					historyOrderIndex += 10;
				}
				componentNumber++;
			}
		} catch (TeamRepositoryException e) {
			throw new RuntimeException("Failed to fullyPopulateChangeSetsInTag for " + tagList, e);
		}
	}

	private static String mungeUserName(String rtcUserName) {
		if (rtcUserName.contains(" *CONTRACTOR*")) {
			return rtcUserName.replace(" *CONTRACTOR*", "");
		}
		return rtcUserName;
	}

	private static String mungeEmailAddress(String rtcEmail) {
		return rtcEmail.replaceAll("[0-9]@.*\\.ibm\\.com", "@i2group.com");
	}
}
