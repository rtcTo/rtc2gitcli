package to.rtc.cli.migrate.util;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;

public class AcceptCommandOutputParserTest {
    private static final List<String> O1 = asList(new String[] { "Accepting changes:",
            "  Repository: https://my.rtc.server.com/ccm/",
            "    Workspace: (_W0eNMJ5XEem4Kc8uqGvXiQ) \"migrateRTC2Git__scUr0KVgEeSArceWqOJbww_10787_t\"",
            "      Component: (_sqhf4KVgEeSArceWqOJbww) \"MyComponentName\"", "        Change sets:",
            "          (_ce3uoaV2EeSArceWqOJbww) ----$ Surname, Firstname I (Firstname) \"Some changeset description.\" 26-Jan-2015 05:14 PM",
            "            Changes:", "              ---c- /DSLJobsDefinition.groovy",
            "              --a-- /FlavaDefinition.groovy", "              --d-- /Helles/",
            "              --d-- /Helles/Helles_JenkinsDSL.groovy",
            "              --mc- /Mainline_jenkinsDSL.groovy (Renamed from /Gonzo_jenkinsDSL.groovy)",
            "              ---c- /RtcTestDefinition.groovy",
            "              --m-- /libs/main.jar.from.job-dsl-core-1.27-SNAPSHOT-standalone.jar (Renamed from /libs/main.jar)",
            "            Work items:",
            "              (_7gXUEKGBEeSKROhauN_a2A) 16103 \"Some work item tile\"",
            "Following workspaces still have conflicts after accept:",
            "  migrateRTC2Git__mjvOMOnEEeSY6Lhbn4A0Xg_332_t",
            "Run 'scm resolve conflict -l' or 'scm show conflicts -l' or 'scm show status' for help in resolving the conflicts."
            });
    private static final List<String> O1_ADDED = asList(new String[] { "FlavaDefinition.groovy" });
    private static final List<String> O1_DELETED = asList(
            new String[] { "Helles/", "Helles/Helles_JenkinsDSL.groovy" });
    private static final List<String> O1_CHANGED = asList(
            new String[] { "DSLJobsDefinition.groovy", "Mainline_jenkinsDSL.groovy", "RtcTestDefinition.groovy" });
    private static final List<String> O1_CONFLICT = asList(new String[] {});
    private static final Map<String, String> O1_MOVED = new TreeMap<String, String>();
    static {
        O1_MOVED.put("Gonzo_jenkinsDSL.groovy", "Mainline_jenkinsDSL.groovy");
        O1_MOVED.put("libs/main.jar", "libs/main.jar.from.job-dsl-core-1.27-SNAPSHOT-standalone.jar");
    }
    private static final List<String> O2 = asList(new String[] { "Repository: https://my.rtc.server.com/ccm/",
            "    Workspace: (_W0eNMJ5XEem4Kc8uqGvXiQ) \"migrateRTC2Git__scUr0KVgEeSArceWqOJbww_10787_t\"",
            "      Component: (_sqhf4KVgEeSArceWqOJbww) \"MyComponentName\"",
            "        Change sets:",
            "          (_ce3uoaV2EeSArceWqOJbww) ----$ Surname, Firstname I (Firstname) \"Some changeset description.\" 26-Jan-2015 05:14 PM" });
    /** Trimmed down real command result from a <code>--accept-missing-changesets</code> accept. */
    private static final List<String> O3 = asList(new String[] { "Accepting changes:",
            "  Repository: https://rtc.mycompany.com:9443/ccm/",
            "  Workspace: (_yx4JgFWaEeu23Y-r8BATdQ) \"migrateRTC2Git__XkQo8JhIEeSmgfdjgmzAQg_7810_t\"",
            "    Component: (_Y-XPcJhIEeSmgfdjgmzAQg) \"Web_UI\"",
            "      Change sets:",
            "        (_E74UUDBxEeWAUMpfke2jpA) ---#$ Foo, Bar \"Merges\" 23-Jul-2015 08:48 AM",
            "          Changes:",
            "            -#-c- /Subfolder/Common.csproj",
            "            ---c- /Subfolder/ModelTests.cs",
            "            -#-c- /Subfolder/CommonControls.csproj",
            "            ---c- /Subfolder/ViewModel.cs",
            "          Work items:",
            "            (_pwQ3YDBLEeWAUMpfke2jpA) 35017 \"First work item\"",
            "        (_HHf6YAYLEeWbpoCWX5ZJ1g) ---#$ Doe, Jane \"Boring comment.\" 05-Jun-2015 01:18 PM",
            "          Changes:",
            "            ---c- /Subfolder/DemoDesktopConsoleApp/Program.cs",
            "            -#-c- /Subfolder/Common.csproj",
            "            --d-- /Subfolder/ViewModel.cs",
            "            --a-- /Subfolder/DataModel.cs",
            "            ---c- /Subfolder/CommonControls.csproj",
            "          Work items:",
            "            (_lgwK0At8EeWeQtNfc7bFlw) 33581 \"Second work item\"",
            "        (_y-k3EQ6LEeW2ULYg_cKmUw) ---#$ Smith, John \"Some comment\" 09-Jun-2015 10:44 AM",
            "          Changes:",
            "            -#-c- /Subfolder/Common.csproj",
            "            --a-- /Subfolder/MoreFiles.cs",
            "          Work items:",
            "            (_ZQDaAAhiEeWxpqD_ypLnsA) 33465 \"Third work item\"" });
    private static final List<String> O3_ADDED = asList(new String[] { "Subfolder/DataModel.cs", "Subfolder/MoreFiles.cs" });
    private static final List<String> O3_DELETED = asList(new String[] { "Subfolder/ViewModel.cs" });
    private static final List<String> O3_CHANGED = asList(
            new String[] { "Subfolder/Common.csproj", "Subfolder/CommonControls.csproj",
                    "Subfolder/DemoDesktopConsoleApp/Program.cs", "Subfolder/ModelTests.cs", "Subfolder/ViewModel.cs" });
    private static final List<String> O3_CONFLICT = asList(new String[] {"Subfolder/Common.csproj", "Subfolder/CommonControls.csproj"});

    @Test
    public void getChangesFoundGivenOutputWithChangesThenReturnsTrue() {
        // Given
        final AcceptCommandOutputParser instance = new AcceptCommandOutputParser(O1);
        final boolean expected = true;

        // When
        final boolean actual = instance.getChangesFound();

        // Then
        assertEquals(expected, actual);
    }

    @Test
    public void getChangesFoundGivenOutputWithNoChangesThenReturnsFalse() {
        // Given
        final AcceptCommandOutputParser instance = new AcceptCommandOutputParser(O2);
        final boolean expected = false;

        // When
        final boolean actual = instance.getChangesFound();

        // Then
        assertEquals(expected, actual);
    }

    @Test
    public void getMovedGivenOutputThenReturnsMapOfOldToNew() {
        // Given
        final AcceptCommandOutputParser instance = new AcceptCommandOutputParser(O1);
        final Map<String, String> expected = new TreeMap<String, String>(O1_MOVED);

        // When
        final Map<String, String> actual = instance.getMoved();

        // Then
        assertEquals(expected, new TreeMap<String, String>(actual));
    }

    @Test
    public void getChangedGivenOutputThenReturnsList() {
        // Given
        final AcceptCommandOutputParser instance = new AcceptCommandOutputParser(O1);
        final List<String> expected = new ArrayList<String>(O1_CHANGED);

        // When
        final List<String> actual = instance.getChanged();

        // Then
        assertEquals(expected, new ArrayList<String>(actual));
    }

    @Test
    public void getPropertiesGivenOutputThenReturnsList() {
        // Given
        final AcceptCommandOutputParser instance = new AcceptCommandOutputParser(O1);
        final List<String> expected = new ArrayList<String>();

        // When
        final List<String> actual = instance.getProperties();

        // Then
        assertEquals(expected, new ArrayList<String>(actual));
    }

    @Test
    public void getAddedGivenOutputThenReturnsList() {
        // Given
        final AcceptCommandOutputParser instance = new AcceptCommandOutputParser(O1);
        final List<String> expected = new ArrayList<String>(O1_ADDED);

        // When
        final List<String> actual = instance.getAdded();

        // Then
        assertEquals(expected, new ArrayList<String>(actual));
    }

    @Test
    public void getAddedGivenMultiChangesetOutputThenReturnsAllAddedFiles() {
        // Given
        final AcceptCommandOutputParser instance = new AcceptCommandOutputParser(O3);
        final List<String> expected = new ArrayList<String>(O3_ADDED);

        // When
        final List<String> actual = instance.getAdded();

        // Then
        assertEquals(expected, new ArrayList<String>(actual));
    }

    @Test
    public void getAddedGivenMultiChangesetOutputThenReturnsAllChangedFiles() {
        // Given
        final AcceptCommandOutputParser instance = new AcceptCommandOutputParser(O3);
        final List<String> expected = new ArrayList<String>(O3_CHANGED);

        // When
        final List<String> actual = instance.getChanged();

        // Then
        assertEquals(expected, new ArrayList<String>(actual));
    }

    @Test
    public void getAddedGivenMultiChangesetOutputThenReturnsAllDeletedFiles() {
        // Given
        final AcceptCommandOutputParser instance = new AcceptCommandOutputParser(O3);
        final List<String> expected = new ArrayList<String>(O3_DELETED);

        // When
        final List<String> actual = instance.getDeleted();

        // Then
        assertEquals(expected, new ArrayList<String>(actual));
    }

    @Test
    public void getAddedGivenMultiChangesetOutputThenReturnsAllConflictFiles() {
        // Given
        final AcceptCommandOutputParser instance = new AcceptCommandOutputParser(O3);
        final List<String> expected = new ArrayList<String>(O3_CONFLICT);

        // When
        final List<String> actual = instance.getConflicts();

        // Then
        assertEquals(expected, new ArrayList<String>(actual));
    }

    @Test
    public void getDeletedGivenOutputThenReturnsList() {
        // Given
        final AcceptCommandOutputParser instance = new AcceptCommandOutputParser(O1);
        final List<String> expected = new ArrayList<String>(O1_DELETED);

        // When
        final List<String> actual = instance.getDeleted();

        // Then
        assertEquals(expected, new ArrayList<String>(actual));
    }

    @Test
    public void getConflictsGivenOutputThenReturnsList() {
        // Given
        final AcceptCommandOutputParser instance = new AcceptCommandOutputParser(O1);
        final List<String> expected = new ArrayList<String>(O1_CONFLICT);

        // When
        final List<String> actual = instance.getConflicts();

        // Then
        assertEquals(expected, new ArrayList<String>(actual));
    }

    @Test
    public void getOnlyFilesGivenListThenReturnsJustFiles() {
        // Given
        final String file1 = File.separator + "foo";
        final String file2 = File.separator + "bar";
        final String dir1 = File.separator + "mydir" + File.separator;
        final String[] input = { file1, file2, dir1 };
        final String[] expected = { file1, file2 };

        // When
        final List<String> actual = AcceptCommandOutputParser.getOnlyFiles(asList(input));

        // Then
        assertThat(actual, equalTo(asList(expected)));
    }

    @Test
    public void getOnlyDirectoriesGivenListThenReturnsJustDirectories() {
        // Given
        final String file1 = File.separator + "foo";
        final String file2 = File.separator + "bar";
        final String dir1 = File.separator + "mydir" + File.separator;
        final String[] input = { file1, file2, dir1 };
        final String[] expected = { dir1 };

        // When
        final List<String> actual = AcceptCommandOutputParser.getOnlyDirectories(asList(input));

        // Then
        assertThat(actual, equalTo(asList(expected)));
    }

    @Test
    public void isOrderOfChangesImportantGivenIndependentChangesThenReturnsFalse() {
        // Given
        final boolean expected = false;
        final List<String> conflicts = Arrays.asList("conflict");
        final List<String> added = Arrays.asList("added");
        final List<String> deleted = Arrays.asList("deleted");
        final Map<String, String> moved = new LinkedHashMap<String, String>();
        moved.put("oldname", "newname");
        final List<String> changed = Arrays.asList("edited");
        final List<String> properties = Arrays.asList("tweaked");
        final TestInstance instance = new TestInstance(conflicts, added, deleted, moved, changed, properties);

        // When
        final boolean actual = instance.isOrderOfChangesImportant();

        // Then
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void isOrderOfChangesImportantGivenOverlappingMovesThenReturnsTrue() {
        // Given
        final boolean expected = true;
        final List<String> conflicts = Arrays.asList();
        final List<String> added = Arrays.asList();
        final List<String> deleted = Arrays.asList();
        final Map<String, String> moved = new LinkedHashMap<String, String>();
        moved.put("oldname", "newname");
        moved.put("newname", "oldname");
        final List<String> changed = Arrays.asList();
        final List<String> properties = Arrays.asList();
        final TestInstance instance = new TestInstance(conflicts, added, deleted, moved, changed, properties);

        // When
        final boolean actual = instance.isOrderOfChangesImportant();

        // Then
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void isOrderOfChangesImportantGivenMoveAndEditThenReturnsFalse() {
        // Given
        final boolean expected = false;
        final List<String> conflicts = Arrays.asList();
        final List<String> added = Arrays.asList();
        final List<String> deleted = Arrays.asList();
        final Map<String, String> moved = new LinkedHashMap<String, String>();
        moved.put("oldname", "newname");
        final List<String> changed = Arrays.asList("newname");
        final List<String> properties = Arrays.asList();
        final TestInstance instance = new TestInstance(conflicts, added, deleted, moved, changed, properties);

        // When
        final boolean actual = instance.isOrderOfChangesImportant();

        // Then
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void isOrderOfChangesImportantGivenMoveAndNonClashingDeleteThenReturnsFalse() {
        // Given
        final boolean expected = false;
        final List<String> conflicts = Arrays.asList();
        final List<String> added = Arrays.asList();
        final List<String> deleted = Arrays.asList("someOtherDir/");
        final Map<String, String> moved = new LinkedHashMap<String, String>();
        moved.put("dir/oldname", "newname");
        final List<String> changed = Arrays.asList();
        final List<String> properties = Arrays.asList();
        final TestInstance instance = new TestInstance(conflicts, added, deleted, moved, changed, properties);

        // When
        final boolean actual = instance.isOrderOfChangesImportant();

        // Then
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void isOrderOfChangesImportantGivenMoveAndClashingDeleteThenReturnsTrue() {
        // Given
        final boolean expected = true;
        final List<String> conflicts = Arrays.asList();
        final List<String> added = Arrays.asList();
        final List<String> deleted = Arrays.asList("dir/");
        final Map<String, String> moved = new LinkedHashMap<String, String>();
        moved.put("dir/oldname", "newname");
        final List<String> changed = Arrays.asList();
        final List<String> properties = Arrays.asList();
        final TestInstance instance = new TestInstance(conflicts, added, deleted, moved, changed, properties);

        // When
        final boolean actual = instance.isOrderOfChangesImportant();

        // Then
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void isOrderOfChangesImportantGivenComplexButUnorderedChangesetThenReturnsFalse() {
        // Given
        final boolean expected = false;
        final List<String> changeset = asList(new String[] { "Repository: https://my.rtc.server.com/ccm/",
                "Workspace: (_1ophgM8qEemtOeS-oAttuw) \"migrateRTC2Git__ObtokOq9EeWzQqr9bzLxSQ_13856_t\"",
                "  Component: (_OdiNg-q9EeWzQqr9bzLxSQ) \"MyComponentName\"", "    Change sets:",
                "      (_TUGFQD3qEea5ift7pZTTZQ) ----$ Surname, Firstname I (Firstname) \"Some changeset description\" 29-Jun-2016 01:12 PM",
                "        Changes:",
                "          --m-- /shell/destroy_docker_space.sh (Renamed from /destroy_docker_space.sh)",
                "          --m-- /shell/docker-host-garbage-collection.sh (Renamed from /docker-host-garbage-collection.sh)",
                "          --m-- /init/docker-host-lvm-cleanup.conf (Renamed from /docker-host-lvm-cleanup.conf)",
                "          --m-- /shell/docker-host-lvm-cleanup.sh (Renamed from /docker-host-lvm-cleanup.sh)",
                "          --m-- /shell/docker-idle-slave-cleanup.sh (Renamed from /docker-idle-slave-cleanup.sh)",
                "          --m-- /shell/docker-ui.sh (Renamed from /docker-ui.sh)", "          --a-- /init/",
                "          --m-- /shell/install-nginx.sh (Renamed from /install-nginx.sh)",
                "          --m-- /init/docker-host-redhat-cleanup.conf (Renamed from /python/docker-host-redhat-cleanup.conf)",
                "          ---c- /python/docker-host-redhat-cleanup.py",
                "          --m-- /shell/recreate_docker_registry.sh (Renamed from /recreate_docker_registry.sh)",
                "          --a-- /shell/",
                "          --m-- /shell/upgradeDockerClient.sh (Renamed from /upgradeDockerClient.sh)",
                "        Work items:", "          (_cWdsEC4wEeaura3zzH_bWQ) 44761 \"some title\"", });
        final AcceptCommandOutputParser instance = new AcceptCommandOutputParser(changeset);

        // When
        final boolean actual = instance.isOrderOfChangesImportant();

        // Then
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void getConflictsFoundInWorkspaceGivenOutputWithConflictsThenReturnsTrue() {
        // Given
        final AcceptCommandOutputParser instance = new AcceptCommandOutputParser(O1);
        final Boolean expected = true;

        // When
        final Boolean actual = instance.getConflictsFoundInWorkspace();

        // Then
        assertEquals(expected, actual);
    }

    @Test
    public void getConflictsFoundInWorkspaceGivenBelievableOutputWithNoConflictsThenReturnsFalse() {
        // Given
        final AcceptCommandOutputParser instance = new AcceptCommandOutputParser(O3);
        final Boolean expected = false;

        // When
        final Boolean actual = instance.getConflictsFoundInWorkspace();

        // Then
        assertEquals(expected, actual);
    }

    @Test
    public void getConflictsFoundInWorkspaceGivenAmbiguousOutputThenReturnsNull() {
        // Given
        final AcceptCommandOutputParser instance = new AcceptCommandOutputParser(asList("Error:", "Something went wrong"));
        final Boolean expected = null;

        // When
        final Boolean actual = instance.getConflictsFoundInWorkspace();

        // Then
        assertEquals(expected, actual);
    }

    private static class TestInstance extends AcceptCommandOutputParser {
        private final List<String> conflicts;
        private final List<String> added;
        private final List<String> deleted;
        private final Map<String, String> moved;
        private final List<String> changed;
        private final List<String> properties;

        public TestInstance(List<String> conflicts, List<String> added, List<String> deleted, Map<String, String> moved,
                List<String> changed, List<String> properties) {
            super(Collections.<String>emptyList());
            this.conflicts = conflicts;
            this.added = added;
            this.deleted = deleted;
            this.moved = moved;
            this.changed = changed;
            this.properties = properties;
        }

        public List<String> getConflicts() {
            return conflicts;
        }

        public List<String> getAdded() {
            return added;
        }

        public List<String> getDeleted() {
            return deleted;
        }

        public Map<String, String> getMoved() {
            return moved;
        }

        public List<String> getChanged() {
            return changed;
        }

        public List<String> getProperties() {
            return properties;
        }
    }
}
