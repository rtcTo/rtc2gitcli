/**
 *
 */

package to.rtc.cli.migrate;

/**
 * @author florian.buehlmann
 *
 */
public class ChangeSet {

  private final String uuid;
  private String workItemText;
  private String entryName;
  private String creatorName;
  private String emailAddress;
  private long creationDate;

  ChangeSet(String changeSetUuid) {
    uuid = changeSetUuid;
  }

  ChangeSet setWorkItem(String workItemText) {
    this.workItemText = workItemText;
    return this;
  }

  ChangeSet setText(String entryName) {
    this.entryName = entryName;
    return this;
  }

  ChangeSet setCreatorName(String creatorName) {
    this.creatorName = creatorName;
    return this;
  }

  ChangeSet setCreatorEMail(String emailAddress) {
    this.emailAddress = emailAddress;
    return this;
  }

  ChangeSet setCreationDate(long creationDate) {
    this.creationDate = creationDate;
    return this;
  }

  String getUuid() {
    return uuid;
  }

  public String getWorkItemText() {
    return workItemText;
  }

  public String getComment() {
    return entryName;
  }

  public String getCreatorName() {
    return creatorName;
  }

  public String getEmailAddress() {
    return emailAddress;
  }

  public long getCreationDate() {
    return creationDate;
  }

}
