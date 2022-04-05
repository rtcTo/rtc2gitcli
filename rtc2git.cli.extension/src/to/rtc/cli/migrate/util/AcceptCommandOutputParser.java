package to.rtc.cli.migrate.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the text output from the <code>scm accept</code> command.
 * <p>
 * <b>NOTE:</b> This code can return incomplete results because RTC lies by
 * omission. Just because this code says we expected to see file Foo.txt added
 * doesn't mean it isn't OK to also see Bar.txt added as well. It would seem
 * that, when <code>scm accept</code> reports the contents of the changeset
 * being accepted, it can omit parts of the changeset's contents in the
 * description. This then has a knock-on effect that this code may also omit
 * critical details. <br/>
 * Callers can trust that anything reported is valid, but they cannot trust the
 * inverse (or anything relying on the inverse).
 */
public class AcceptCommandOutputParser {
    private static final String CHANGES_HEADER_REGEX = "^ *Changes:$";
    private static final Pattern CHANGES_HEADER_PATTERN = Pattern.compile(CHANGES_HEADER_REGEX);
    /**
     * Syntax seems to be:
     * <ol>
     * <li>! to indicate an incoming conflict (which should never happen), or - to
     * mean clear. Note that this is guessed from the docs.</li>
     * <li># to indicate an accepted conflict (which is caused by RTC malfunctions),
     * or - to mean clear.</li>
     * <li>a, d or m to mean file added, deleted or moved, or - to mean none of
     * those.</li>
     * <li>c to mean contents changed, or - to mean no change.</li>
     * <li>p probably means properties changed but it's not clear, or -
     * normally.</li>
     * </ol>
     * Note: # means that there's been a conflict accepting the changes in the order
     * that RTC said they had to be accepted. RTC will then prompt "Following
     * workspaces still have conflicts after accept:", then the workspace name, then
     * "Run 'scm resolve conflict' or 'scm show conflicts' or 'scm show status' for
     * help in resolving the conflicts.".
     */
    private static final String CHANGE_REGEX = "^ *([-!][-#][-adm][-c][-p]) ([/\\\\].+)$";
    /**
     * First group is the change attributes, second is the file or directory name.
     */
    private static final Pattern CHANGE_PATTERN = Pattern.compile(CHANGE_REGEX);
    /**
     * When RTC shows a file that's been moved, it lists it as e.g "--m--
     * /Trunk_EIA/ (Renamed from /Hellas/)". This regex is applied to the bit
     * following "--m-- ".
     */
    private static final String MOVE_REGEX = "^([/\\\\].+?) \\(Renamed from ([/\\\\].+)\\)$";
    private static final Pattern MOVE_PATTERN = Pattern.compile(MOVE_REGEX);

    private boolean changesFound;
    private List<String> conflicts;
    private List<String> added;
    private List<String> deleted;
    private Map<String, String> moved;
    private List<String> changed;
    private List<String> properties;
    private Boolean conflictsFoundInWorkspace;

    public AcceptCommandOutputParser(final List<String> toBeParsed) {
        calculateResult(toBeParsed);
        conflictsFoundInWorkspace = calculateConflictsFound(toBeParsed);
    }

    private Boolean calculateConflictsFound(final List<String> toBeParsed) {
        // If we have a conflict, scm accept will report something like this:
        //
        // Following workspaces still have conflicts after accept:
        //  migrateRTC2Git__mjvOMOnEEeSY6Lhbn4A0Xg_332_t
        // Run 'scm resolve conflict -l' or 'scm show conflicts -l' or 'scm show status' for help in resolving the conflicts.
        //
        // ...at the end of its output, just before it completes.
        if ( toBeParsed.contains("Following workspaces still have conflicts after accept:") ) {
            return Boolean.TRUE;
        }
        // If scm accept failed to output anything then we can't be sure, but
        // if it starts by saying "Accepting changes:" then we can be sure that
        // it'll tell us about conflicts if there are some.
        if ( toBeParsed.contains("Accepting changes:")) {
            return Boolean.FALSE;
        }
        // otherwise we're unsure what we're dealing with.
        return null;
    }

    private List<Matcher> calculateChangeLines(final List<String> toBeParsed) {
        final List<Matcher> result = new ArrayList<Matcher>(toBeParsed.size());
        final Iterator<String> i = toBeParsed.iterator();
        while (i.hasNext() ) {
            // First, we skip to the start of the changes
            while (i.hasNext()) {
                final String line = i.next();
                final Matcher matcher = CHANGES_HEADER_PATTERN.matcher(line);
                if (matcher.matches()) {
                    // This is a line denoting the start of the list of file changes.
                    // We stop looping here and drop into the next loop
                    break;
                }
            }
            // If we're here, we've EITHER run out of lines, OR we've got file changes next.
            while (i.hasNext()) {
                final String line = i.next();
                final Matcher matcher = CHANGE_PATTERN.matcher(line);
                if (!matcher.matches()) {
                    break;
                }
                result.add(matcher);
            }
            // we've run out of file change lines, so we're either out of lines or there's another changeset to follow.
        }
        return result;
    }

    private void calculateResult(final List<String> toBeParsed) {
        final List<Matcher> changes = calculateChangeLines(toBeParsed);
        if (changes.isEmpty()) {
            changesFound = false;
            return;
        }
        changesFound = true;
        final Set<String> con = new TreeSet<String>();
        final Set<String> a = new TreeSet<String>();
        final Set<String> d = new TreeSet<String>();
        final Map<String, String> m = new TreeMap<String, String>();
        final Set<String> c = new TreeSet<String>();
        final Set<String> p = new TreeSet<String>();
        for (final Matcher change : changes) {
            final String flags = change.group(1);
            final String fileText = change.group(2);
            final String oldFilename;
            final String newFilename;
            if (flags.contains("m")) {
                final Matcher mm = MOVE_PATTERN.matcher(fileText);
                final String msg = "Error parsing line [" + change.group() + "]: Flags (" + flags
                        + ") contains an 'm' so we're expecting to parse the change line [" + fileText
                        + "] using the regex [" + MOVE_PATTERN.pattern() + "] but it did not match.";
                if (!mm.matches()) {
                    throw new IllegalStateException(msg);
                }
                try {
                    newFilename = trimLeadingSlash(mm.group(1));
                    oldFilename = trimLeadingSlash(mm.group(2));
                } catch (IllegalStateException ex) {
                    throw new IllegalStateException(msg, ex);
                }
                m.put(oldFilename, newFilename);
            } else {
                oldFilename = newFilename = trimLeadingSlash(fileText);
            }
            if (flags.contains("!") || flags.contains("#")) {
                con.add(newFilename);
            }
            if (flags.contains("a")) {
                a.add(newFilename);
            }
            if (flags.contains("d")) {
                d.add(newFilename);
            }
            if (flags.contains("c")) {
                c.add(newFilename);
            }
            if (flags.contains("p")) {
                p.add(newFilename);
            }
        }
        conflicts = Collections.unmodifiableList(new ArrayList<String>(con));
        added = Collections.unmodifiableList(new ArrayList<String>(a));
        deleted = Collections.unmodifiableList(new ArrayList<String>(d));
        moved = Collections.unmodifiableMap(m);
        changed = Collections.unmodifiableList(new ArrayList<String>(c));
        properties = Collections.unmodifiableList(new ArrayList<String>(p));
    }

    public boolean getChangesFound() {
        return changesFound;
    }

    /**
     * Returns the files reported as having conflicts.
     * <p>
     * <b>NOTE:</b> This list may not include all the files it should, because the
     * accept command does not always report everything it should.
     * 
     * @return A (possibly incomplete) list, which may be empty but will not be null.
     */
    public List<String> getConflicts() {
        return conflicts;
    }

    /**
     * Returns the files reported as having been added.
     * <p>
     * <b>NOTE:</b> This list may not include all the files it should, because the
     * accept command does not always report everything it should.
     * 
     * @return A (possibly incomplete) list, which may be empty but will not be null.
     */
    public List<String> getAdded() {
        return added;
    }

    /**
     * Returns the files reported as having been deleted.
     * <p>
     * <b>NOTE:</b> This list may not include all the files it should, because the
     * accept command does not always report everything it should.
     * 
     * @return A (possibly incomplete) list, which may be empty but will not be null.
     */
    public List<String> getDeleted() {
        return deleted;
    }

    /**
     * Returns the files reported as having been renamed.
     * <p>
     * <b>NOTE:</b> This list may not include all the files it should, because the
     * accept command does not always report everything it should.
     * 
     * @return A (possibly incomplete) map of old name to new name.
     */
    public Map<String, String> getMoved() {
        return moved;
    }

    /**
     * Returns the files reported as having been modified.
     * <p>
     * <b>NOTE:</b> This list may not include all the files it should, because the
     * accept command does not always report everything it should.
     * 
     * @return A (possibly incomplete) list, which may be empty but will not be null.
     */
    public List<String> getChanged() {
        return changed;
    }

    /**
     * Returns the files reported as having had their properties changed.
     * <p>
     * <b>NOTE:</b> This list may not include all the files it should, because the
     * accept command does not always report everything it should.
     * 
     * @return A (possibly incomplete) list, which may be empty but will not be null.
     */
    public List<String> getProperties() {
        return properties;
    }

    /**
     * Says if the accept command reported that the workspace contains conflicts.
     * 
     * @return true if conflicts have been reported, false if we are sure that there are no conflicts, null if we are unsure.
     */
    public Boolean getConflictsFoundInWorkspace() {
        return conflictsFoundInWorkspace;
    }

    /**
     * Determines if we can trust the RTC CLI "accept" command to process this
     * changeset correctly or not. The "accept" command doesn't always process
     * changes in the order that they should be applied in, e.g. moving a file
     * /a/b.txt to /b.txt and then deleting /a/ has to be done in that order, but
     * there's an RTC bug where this can't be relied upon, so you can end up with no
     * /b.txt if the delete happened first.
     * <p>
     * <b>NOTE:</b> This might return false when the true answer should be true,
     * because the accept command does not always report everything it should.
     * 
     * @return true if the changes overlap and thus the order they are applied is
     *         important, false if not.
     */
    public boolean isOrderOfChangesImportant() {
        // If our move operations overlap then that's order-critical
        for (final Map.Entry<String, String> move : getMoved().entrySet()) {
            final Map<String, String> otherMoves = new HashMap<String, String>(getMoved());
            final String moveFrom = move.getKey();
            final String moveTo = move.getValue();
            otherMoves.remove(moveFrom);
            final Collection<String> otherMovesTo = otherMoves.values();
            final Collection<String> otherMovesFrom = otherMoves.keySet();
            if (changesMightOverlap(moveFrom, otherMovesFrom)) {
                return true;
            }
            if (changesMightOverlap(moveFrom, otherMovesTo)) {
                return true;
            }
            if (changesMightOverlap(moveTo, otherMovesFrom)) {
                return true;
            }
            if (changesMightOverlap(moveTo, otherMovesTo)) {
                return true;
            }
        }
        final Collection<String> movedTo = getMoved().values();
        final Collection<String> movedFrom = getMoved().keySet();
        final Set<String> moved = new HashSet<String>(movedTo);
        moved.addAll(movedFrom);
        final Set<String> addedOrChangedButNotMoved = new HashSet<String>();
        addedOrChangedButNotMoved.addAll(getOnlyFiles(getAdded()));
        addedOrChangedButNotMoved.addAll(getChanged());
        addedOrChangedButNotMoved.addAll(getProperties());
        final Set<String> addedChangedOrMoved = new HashSet<String>(addedOrChangedButNotMoved);
        addedChangedOrMoved.addAll(moved);
        addedOrChangedButNotMoved.removeAll(movedTo);
        if (changesMightOverlap(getDeleted(), addedChangedOrMoved)) {
            // If we delete anything that overlaps with any other operation then the order
            // is important
            return true;
        }
        final Set<String> addedChangedOrDeleted = new HashSet<String>(addedOrChangedButNotMoved);
        addedChangedOrDeleted.addAll(getDeleted());
        if (changesMightOverlap(moved, addedChangedOrDeleted)) {
            // If we moved anything that overlaps with any other operation then the order is
            // important
            return true;
        }
        return false;
    }

    /**
     * Sometimes "scm accept" fails but declares success. If the changeset
     * added/moved/changed files that aren't on the filesystem, this returns those
     * files and their parent folders (if any). It does not check that deleted files
     * have been deleted.
     * <p>
     * <b>NOTE:</b> This list may not include all the files it should, because the
     * accept command does not always report everything it should.
     * 
     * @param baseDir The sandbox root
     * @return empty if everything we expect to be present is present, otherwise it
     *         returns a (possibly incomplete) set of things that were expected to
     *         exist but are missing.
     */
    public Set<String> findMostOfTheMissingFiles(File baseDir) {
        final Set<String> result = new TreeSet<String>();
        final Set<String> mostOfTheFilesExpectedToBePresent = getFilesExpectedToBePresent();
        findMissing(result, baseDir, mostOfTheFilesExpectedToBePresent);
        return result;
    }

    /**
     * Sometimes "scm accept" fails but declares success. If the changeset was meant
     * to delete files and/or directories that are still on the filesystem, this
     * returns those files & directories. Note: unlike
     * {@link #findMostOfTheMissingFiles(File)}, this does not list parent directories, as
     * the deletion of a file in a folder does not imply the loss of the folder
     * itself. <br>
     * Note: if a folder should be deleted then we don't list any contents that
     * should also have been removed.
     * <p>
     * <b>NOTE:</b> This list may not include all the files it should, because the
     * accept command does not always report everything it should. Worse, this may
     * report things as unwanted if RTC reported its removal but failed to report
     * its replacement. i.e. you can't trust this data to be correct.
     * 
     * @param baseDir The sandbox root
     * @return empty if everything we expect to be present is present, otherwise it
     *         returns a set of things that were expected to exist but are missing.
     */
    public Set<String> detectPotentiallyUnwantedFiles(File baseDir) {
        final Iterable<String> filesExpectedToHaveBeenRemovedExcludingThoseImplitlyRemovedByParentFolder = getFilesExpectedToHaveBeenRemoved();
        final Set<String> result = new TreeSet<String>();
        findPresent(result, baseDir, filesExpectedToHaveBeenRemovedExcludingThoseImplitlyRemovedByParentFolder);
        return result;
    }

    /**
     * Calculates all the files (and directories) that we expect to have present in
     * the sandbox after this changeset has been applied. This includes all the
     * parent directories of files & directories that get a mention as well. For
     * non-trivial changesets, it may include things that were <em>also</em>
     * deleted/moved before being replaced.
     * <p>
     * <b>NOTE:</b> Beware that RTC does not always report all files that it has
     * added - what it says it did and what it actually did can differ.
     * 
     * @return A (possibly incomplete) set of stuff we expect to find in our sandbox area.
     */
    public Set<String> getFilesExpectedToBePresent() {
        final Set<String> result = new TreeSet<String>();
        addFilesAndTheirParentDirectories(result, getChanged());
        addFilesAndTheirParentDirectories(result, getProperties());
        addFilesAndTheirParentDirectories(result, getAdded());
        addFilesAndTheirParentDirectories(result, getMoved().values());
        return result;
    }

    /**
     * Calculates all the files (and/or possibly directories) that we expected to no
     * longer be present in the sandbox after this changeset has been applied. This
     * does not individually mention the contents of folders that were deleted even
     * if the changeset changes did. For non-trivial changesets, it will not include
     * anything that may have been replaced after it was deleted/moved.
     * <p>
     * <b>NOTE:</b> This list may not include all the files it should, because the
     * accept command does not always report everything it should. Worse, this may
     * report things as should-be-removed if RTC reported its removal but failed to
     * report its replacement. i.e. you can't trust this data to be correct.
     * 
     * @return A set of stuff we should not have in our sandbox area anymore.
     */
    private Set<String> getFilesExpectedToHaveBeenRemoved() {
        final Set<String> allFilesWeExpectToHaveBeenRemoved;
        {
            allFilesWeExpectToHaveBeenRemoved = new TreeSet<String>();
            allFilesWeExpectToHaveBeenRemoved.addAll(getDeleted());
            allFilesWeExpectToHaveBeenRemoved.addAll(getMoved().keySet());
            final Set<String> filesExpectedToBePresent = getFilesExpectedToBePresent();
            allFilesWeExpectToHaveBeenRemoved.removeAll(filesExpectedToBePresent);
        }
        final Set<String> directoriesWeExpectToHaveBeenRemoved = new HashSet<String>(
                getOnlyDirectories(allFilesWeExpectToHaveBeenRemoved));
        final Set<String> result = new TreeSet<String>();
        for (final String filename : allFilesWeExpectToHaveBeenRemoved) {
            final boolean filesParentDirHasAlreadyBeenRemoved = isInOneOfTheseDirectories(filename,
                    directoriesWeExpectToHaveBeenRemoved);
            if (!filesParentDirHasAlreadyBeenRemoved) {
                result.add(filename);
            }
        }
        return result;
    }

    private boolean isInOneOfTheseDirectories(final String filename, final Iterable<String> directoriesItMightBeIn) {
        boolean fileIsInADirectoryThatWeHaveRemoved = false;
        for (final String dirWeRemoved : directoriesItMightBeIn) {
            if (filename.startsWith(dirWeRemoved)) {
                fileIsInADirectoryThatWeHaveRemoved = true;
                break;
            }
        }
        return fileIsInADirectoryThatWeHaveRemoved;
    }

    private static void addFilesAndTheirParentDirectories(Set<String> toBeAddedTo, Iterable<String> filenamesToAdd) {
        for (final String filename : filenamesToAdd) {
            addFileAndParentDirectories(toBeAddedTo, filename);
        }
    }

    private static void addFileAndParentDirectories(Set<String> toBeAddedTo, String filenameToAdd) {
        int index = indexOfSlash(filenameToAdd, 0);
        while (index >= 0) {
            final int indexPlusOne = index + 1;
            final String dirname = filenameToAdd.substring(0, indexPlusOne);
            toBeAddedTo.add(dirname);
            index = indexOfSlash(filenameToAdd, indexPlusOne);
        }
        toBeAddedTo.add(filenameToAdd);
    }

    /**
     * Checks zero or more files and adds any that <em>don't</em> exist to the
     * results.
     * 
     * @param results     Will receive any missing files.
     * @param baseDir     The base folder.
     * @param whatToCheck The files (relative to base folder) to be checked.
     */
    private static void findMissing(Collection<String> results, File baseDir, Iterable<String> whatToCheck) {
        findWhereExistsIs(false, results, baseDir, whatToCheck);
    }

    /**
     * Checks zero or more files and adds any that <em>do</em> exist to the results.
     * 
     * @param results     Will receive any missing files.
     * @param baseDir     The base folder.
     * @param whatToCheck The files (relative to base folder) to be checked.
     */
    private static void findPresent(Collection<String> results, File baseDir, Iterable<String> whatToCheck) {
        findWhereExistsIs(true, results, baseDir, whatToCheck);
    }

    private static void findWhereExistsIs(boolean recordWhatExists, Collection<String> results, File baseDir,
            Iterable<String> whatToCheck) {
        for (final String name : whatToCheck) {
            final File f = new File(baseDir, name);
            if (recordWhatExists == f.exists()) {
                results.add(name);
            }
        }
    }

    public static List<String> getOnlyFiles(Iterable<String> filesAndDirectories) {
        return getOnlyFilesOrDirectories(false, filesAndDirectories);
    }

    public static List<String> getOnlyDirectories(Iterable<String> filesAndDirectories) {
        return getOnlyFilesOrDirectories(true, filesAndDirectories);
    }

    private static List<String> getOnlyFilesOrDirectories(final boolean wantDirectoriesNotFiles, Iterable<String> l) {
        final List<String> r = new ArrayList<String>();
        for (final String e : l) {
            if (wantDirectoriesNotFiles == looksLikeADirectory(e)) {
                r.add(e);
            }
        }
        return r;
    }

    private static String trimLeadingSlash(String slashFilename) {
        if (slashFilename.startsWith("/") || slashFilename.startsWith("\\")) {
            return slashFilename.substring(1);
        }
        return slashFilename;
    }

    public static boolean looksLikeADirectory(final String e) {
        return e.endsWith("/") || e.endsWith("\\");
    }

    private static int indexOfSlash(String filename, int fromIndex) {
        int result = filename.indexOf('/', fromIndex);
        if (result < 0) {
            result = filename.indexOf('\\', fromIndex);
        }
        return result;
    }

    private static boolean changesMightOverlap(String pathA, String pathB) {
        if (looksLikeADirectory(pathA) && pathB.startsWith(pathA)) {
            return true;
        }
        if (looksLikeADirectory(pathB) && pathA.startsWith(pathB)) {
            return true;
        }
        return pathA.equals(pathB);
    }

    private static boolean changesMightOverlap(String pathA, Iterable<String> pathBs) {
        for (final String pathB : pathBs) {
            if (changesMightOverlap(pathA, pathB)) {
                return true;
            }
        }
        return false;
    }

    private static boolean changesMightOverlap(Iterable<String> pathAs, Iterable<String> pathBs) {
        for (final String pathA : pathAs) {
            if (changesMightOverlap(pathA, pathBs)) {
                return true;
            }
        }
        return false;
    }
}
