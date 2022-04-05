package to.rtc.cli.migrate.util;

import java.io.PrintStream;

import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogBaselineEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogChangeSetEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogComponentEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogDirectionEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogOslcLinkEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogRootEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogVersionableEntry2DTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogVersionableEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogWorkItemEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ContributorDTO;

public class ChangeLogPrintUtil {
    private final PrintStream out;

    public ChangeLogPrintUtil(PrintStream out) {
        this.out = out;
    }

    public void writeLn(final Object o) {
        writeLn(o, "");
    }

    public void writeLn(final Object o, final String linePrefix) {
        if (o == null) {
            println("null,");
            return;
        }
        final Class<? extends Object> oc = o.getClass();
        if (oc.isPrimitive() || oc.getPackage().getName().startsWith("java.lang")) {
            if (o instanceof String) {
                println("\"" + o.toString() + "\",");
            } else {
                println(o.toString() + ",");
            }
            return;
        }
        print("\"");
        print(oc.getSimpleName());
        println("\": {");
        final String moreIndented = linePrefix + "  ";
        if (o instanceof ChangeLogEntryDTO) {
            writeContentsChangeLogEntryDTO((ChangeLogEntryDTO) o, moreIndented);
        }
        if (o instanceof ChangeLogBaselineEntryDTO) {
            writeContentsChangeLogBaselineEntryDTO((ChangeLogBaselineEntryDTO) o, moreIndented);
        }
        if (o instanceof ChangeLogChangeSetEntryDTO) {
            writeContentsChangeLogChangeSetEntryDTO((ChangeLogChangeSetEntryDTO) o, moreIndented);
        }
        if (o instanceof ChangeLogComponentEntryDTO) {
            writeContentsChangeLogComponentEntryDTO((ChangeLogComponentEntryDTO) o, moreIndented);
        }
        if (o instanceof ChangeLogDirectionEntryDTO) {
            writeContentsChangeLogDirectionEntryDTO((ChangeLogDirectionEntryDTO) o, moreIndented);
        }
        if (o instanceof ChangeLogOslcLinkEntryDTO) {
            writeContentsChangeLogOslcLinkEntryDTO((ChangeLogOslcLinkEntryDTO) o, moreIndented);
        }
        if (o instanceof ChangeLogRootEntryDTO) {
            writeContentsChangeLogRootEntryDTO((ChangeLogRootEntryDTO) o, moreIndented);
        }
        if (o instanceof ChangeLogVersionableEntryDTO) {
            writeContentsChangeLogVersionableEntryDTO((ChangeLogVersionableEntryDTO) o, moreIndented);
        }
        if (o instanceof ChangeLogVersionableEntry2DTO) {
            writeContentsChangeLogVersionableEntry2DTO((ChangeLogVersionableEntry2DTO) o, moreIndented);
        }
        if (o instanceof ChangeLogWorkItemEntryDTO) {
            writeContentsChangeLogWorkItemEntryDTO((ChangeLogWorkItemEntryDTO) o, moreIndented);
        }
        if (o instanceof ContributorDTO) {
            writeContentsContributorDTO((ContributorDTO) o, moreIndented);
        }
        if (o instanceof ChangeLogEntryDTO) {
            writeContents2ChangeLogEntryDTO((ChangeLogEntryDTO) o, moreIndented);
        }
        println(linePrefix + "},");
    }

    private void writeContentsChangeLogEntryDTO(final ChangeLogEntryDTO o, final String linePrefix) {
        print(linePrefix);
        writeLnField(linePrefix, "EntryType", o.isSetEntryType(), o.getEntryType());
        print(linePrefix);
        writeLnField(linePrefix, "EntryName", o.isSetEntryName(), o.getEntryName());
        print(linePrefix);
        writeLnField(linePrefix, "ItemId", o.isSetItemId(), o.getItemId());
    }

    private void writeContents2ChangeLogEntryDTO(final ChangeLogEntryDTO o, final String linePrefix) {
        print(linePrefix);
        writeLnField(linePrefix, "ChildEntries", o.isSetChildEntries(), o.getChildEntries());
    }

    private void writeContentsChangeLogBaselineEntryDTO(final ChangeLogBaselineEntryDTO o, final String linePrefix) {
        print(linePrefix);
        writeLnField(linePrefix, "BaselineId", o.isSetBaselineId(), o.getBaselineId());
        print(linePrefix);
        writeLnField(linePrefix, "CreationDate", o.isSetCreationDate(), o.getCreationDate());
        print(linePrefix);
        writeLnField(linePrefix, "Creator", o.isSetCreator(), o.getCreator());
    }

    private void writeContentsChangeLogChangeSetEntryDTO(final ChangeLogChangeSetEntryDTO o, final String linePrefix) {
        print(linePrefix);
        writeLnField(linePrefix, "CreationDate", o.isSetCreationDate(), o.getCreationDate());
        print(linePrefix);
        writeLnField(linePrefix, "Creator", o.isSetCreator(), o.getCreator());
        print(linePrefix);
        writeLnField(linePrefix, "WorkItems", o.isSetWorkItems(), o.getWorkItems());
        print(linePrefix);
        writeLnField(linePrefix, "OslcLinks", o.isSetOslcLinks(), o.getOslcLinks());
    }

    private void writeContentsChangeLogComponentEntryDTO(final ChangeLogComponentEntryDTO o, final String linePrefix) {
        print(linePrefix);
        writeLnField(linePrefix, "ChangeType", o.isSetChangeType(), o.getChangeType());
    }

    private void writeContentsChangeLogDirectionEntryDTO(final ChangeLogDirectionEntryDTO o, final String linePrefix) {
        print(linePrefix);
        writeLnField(linePrefix, "FlowDirection", o.isSetFlowDirection(), o.getFlowDirection());
    }

    private void writeContentsChangeLogOslcLinkEntryDTO(final ChangeLogOslcLinkEntryDTO o, final String linePrefix) {
        print(linePrefix);
        writeLnField(linePrefix, "TargetUri", o.isSetTargetUri(), o.getTargetUri());
    }

    private void writeContentsChangeLogRootEntryDTO(final ChangeLogRootEntryDTO o, final String linePrefix) {
        print(linePrefix);
        writeLnField(linePrefix, "RepositoryUri", o.isSetRepositoryUri(), o.getRepositoryUri());
        print(linePrefix);
        writeLnField(linePrefix, "RepositoryId", o.isSetRepositoryId(), o.getRepositoryId());
    }

    private void writeContentsChangeLogVersionableEntryDTO(final ChangeLogVersionableEntryDTO o,
            final String linePrefix) {
        print(linePrefix);
        writeLnField(linePrefix, "Resolved", o.isSetResolved(), o.isResolved());
        print(linePrefix);
        writeLnField(linePrefix, "Segments", o.isSetSegments(), o.getSegments());
    }

    private void writeContentsChangeLogVersionableEntry2DTO(final ChangeLogVersionableEntry2DTO o,
            final String linePrefix) {
        print(linePrefix);
        writeLnField(linePrefix, "ShortVersionId", o.isSetShortVersionId(), o.getShortVersionId());
        print(linePrefix);
        writeLnField(linePrefix, "LongVersionId", o.isSetLongVersionId(), o.getLongVersionId());
    }

    private void writeContentsChangeLogWorkItemEntryDTO(final ChangeLogWorkItemEntryDTO o, final String linePrefix) {
        print(linePrefix);
        writeLnField(linePrefix, "WorkItemNumber", o.isSetWorkItemNumber(), o.getWorkItemNumber());
        print(linePrefix);
        writeLnField(linePrefix, "Resolver", o.isSetResolver(), o.getResolver());
    }

    private void writeContentsContributorDTO(final ContributorDTO o, final String linePrefix) {
        print(linePrefix);
        writeLnField(linePrefix, "UserId", o.isSetUserId(), o.getUserId());
        print(linePrefix);
        writeLnField(linePrefix, "FullName", o.isSetFullName(), o.getFullName());
        print(linePrefix);
        writeLnField(linePrefix, "EmailAddress", o.isSetEmailAddress(), o.getEmailAddress());
        print(linePrefix);
        writeLnField(linePrefix, "ContributorItemId", o.isSetContributorItemId(), o.getContributorItemId());
    }

    private void writeLnField(final String linePrefix, final String fieldName, final boolean isSet,
            final Object value) {
        print("\"" + fieldName + "\": ");
        if (!isSet) {
            println("notset,");
            return;
        }
        final String moreIndented = linePrefix + "  ";
        if (value instanceof Iterable) {
            final Iterable<?> i = (Iterable<?>) value;
            if (!i.iterator().hasNext()) {
                println("[],");
                return;
            }
            println("[");
            for (final Object o : i) {
                print(moreIndented);
                writeLn(o, moreIndented);
            }
            println(linePrefix + "],");
            return;
        } else {
            writeLn(value, moreIndented);
        }
    }

    private void println(String string) {
        out.println(string);
    }

    private void print(String string) {
        out.print(string);
    }
}
