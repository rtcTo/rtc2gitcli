#!/bin/bash
# Script to assist with adding in snapshots/tags to a migrated Git repo that
# are the equivalent of selected RTC snapshots.
# RTC snapshots are not necessarily *on* the main history, they can be some
# changesets removed, so to add them to Git we need to identify where, in Git's
# history and in RTC's history, the snapshot parted from the main branch.
#
# Note: This can read & write files to the current folder.
# It should be run from an empty folder.

# Ensure we fail if we reference a variable we've not set
set -u
# Ensure we fail if we execute a command that returns a non-zero exit code
set -e
# Ensure we fail if we execute a command that has a failure in any step of a pipeline
set -o pipefail

SCRIPT_NAME=`basename ${0}`
SCRIPT_DIR=`dirname ${0}`
TABCHARACTER="$(echo -e '\t')"

# Outputs CLI usage help text to stdout.
doHelp() {
  cat <<ENDHELP
Syntax:
  ${SCRIPT_NAME} --gitRepository ... [--gitUsername ...] [--gitPassword ...] [--rtcUri ...] [--rtcUsername ...] [--rtcPassword ...] --rtcSource ... [--rtcProjectArea ... --rtcTeamName ...] [--skip ...] [--maxChangesUniqueToSnapshot ...] [--fieldSeparator ...] [--commentPrefix ...] [--outFile ...] uuid [...]
Where
  --gitRepository "URL"
    Specifies the git repository that the RTC changes will be pushed to.
    If this already exists then its history will be appended to.
  --gitUsername "username"
  --gitPassword "password"
    Specifies the credentials to be used when logging into git to clone and push.
    Note: For security reasons, it is better to use the --gitPasswordEnvName form (see below).
    Defaults to using environment variables GIT_USERNAME and GIT_PASSWORD.
  --rtcUri "rtcBaseUrl" (Optional)
    Specifies the RTC server to be used.
    Defaults to https://jazzc04.hursley.ibm.com:9443/ccm/
  --rtcUsername "username"
  --rtcPassword "password"
    Specifies the credentials to be used when logging into RTC.
    Defaults to using environment variables RTC_USERNAME and RTC_PASSWORD.
  --rtcSource "streamOrWorkspaceName"
    Specifies the stream (or workspace) to be migrated.
  --rtcProjectArea "projectAreaName" (Optional)
  --rtcTeamName "teamNameOrEmpty" (Optional)
    Specifies the project area and team name where the rtcSource can be found.
    If either is specified, both must be specified; if the source is not in a team, give an empty team name.
    Note: These are only required if the rtcSource name is not unique.
  --skip [none|matching|existing]
    Must be one of the following options:
      "none" - we output results for all specified snapshots.
      "matching" - we skip over any snapshots where there's an existing git tag of the same name AND it points at the same commit as identified branchpoint.
      "existing" - we skip over any snapshots where there's an existing git tag of the same name regardless of what commit it points at.
    If commentPrefix is set then skipped entries will be represented by a comment in the output.
    If not, a note will be made to stdout but not the final output.
    Defaults to "existing".
  --maxChangesUniqueToSnapshot "number|all" (Optional)
    Sets the maximum distance (in changesets, from the snapshot's head, going backwards in time) in which to search for a changeset in common in the main branch before giving up.
    Can either be set to a positive integer or to the string "all" (which may not be wise if the snapshot has an especially lengthy history).
    Defaults to 10.
  --fieldSeparator "string" (Optional)
    Specifies how to separate the different columns that we output.
    Defaults to a tab.
  --outFile "filename" (Optional)
    Specifies a file where the output should be written to in addition to stdout.
    Defaults to "", meaning no output other than the console.
  --commentPrefix "linePrefix" (Optional)
    Specifies a series of characters that can prefix a header comment in the output, e.g. '# '.
    If this is empty then no header will be output.
    Defaults to "# ".
  --xxxEnvName "environmentVariableName"
    For any/all of the above arguments, specifies that the value for argument 'xxx' is to be read from
    the specified environment variable.
  uuid [...]
    The RTC UUID(s) of the RTC snapshots to be investigated.
    There must be at least one.
    There can be more than one.
    Each will be processed in turn.
Description:
  For each RTC snapshot UUID in turn:
   - Identifies the name of the snapshot.
   - Identifies the ID of the last RTC changeset that went into the snapshot that is also present in the Git history.
   - Identifies the commit within the Git history of that point.
   - Identifies the creation timestamp of the snapshot in the form YYYY-MM-DDThh:mm (as RTC doesn't tell us about seconds).
   - Outputs a line of the form:
     <RTC-Snapshot-Timestamp><fieldSeparator><RTC-Snapshot-UUID><fieldSeparator><RTC-changeset-UUID><fieldSeparator><Git-commit-ID><fieldSeparator>"<RTC-Snapshot-Name>"<fieldSeparator>"<RTC-Snapshot-Comment>"
  Note that no attempt is made to escape quotes within RTC snapshot names or comments.
ENDHELP
}

# Deletes a folder and its contents, even if Microsoft are involved.
# If we are running in an environment where our working folder is a Microsoft
# Windows filesystem then the filesystem is fundamentally unreliable and thus
# "rm -rf" can fail for no _good_ reason.  So we retry :-(
# FYI the reason is usually "file in use" because Windows OSs have a lot of
# malign processes which lock files "open" - File Explorer, Windows Search and
# most anti-virus software are common culprits and "difficult to remove".
# Another possible reason is that files can be set "read only" in a way that
# the -f argument (to rm) doesn't handle, so we chmod -R a+w just in case too.
robustlyDelete() {
  for attempt in 1 2 3 4 5
  do
    if rm -rf "${@}" >/dev/null 2>&1
    then
      return 0
    else
      echo "Warning: Unable to delete $@ - retrying"
      chmod -R a+w "${@}" >/dev/null 2>&1 || true
      sleep 1
    fi
  done
  rm -rf "${@}"
}

# $1 = --foo
# $2 = value for arg_foo
handleArgAndValue() {
  local argName="$(expr ${1} : '--\(.*\)$')"
  if [ "${2-}" = "" -a "${2-X}" = "X"  ]
  then
    echo "Error: Missing value for argument ${argName}." >&2
    return 1
  fi
  local argValue="${2}"
  local -n varRef="arg_${argName}"
  varRef="${argValue}"
}

# $1 = --fooEnvName
# $2 = name of env var that contains value for arg_foo
handleArgAndEnvName() {
  local argName="$(expr ${1} : '--\(.*\)EnvName$')"
  local envName="${2}"
  if [ -z "${envName}" ]
  then
    echo "Error: Missing environment variable name for argument ${argName}." >&2
    return 1
  fi
  local -n envVarRef="${envName}"
  local argValueOrEmpty="${envVarRef-}"
  local argValueOrX="${envVarRef-X}"
  if [ "${argValueOrEmpty}" = "" -a "${argValueOrX}" = "X"  ]
  then
    echo "Error: Environment variable ${envName}, specified for argument ${argName}, is not set." >&2
    return 1
  fi
  local -n varRef="arg_${argName}"
  varRef="${argValueOrEmpty}"
}

# Creates a git credentials helper script.
# $1 = name of the script to create
# $2 = git repository url
# $3 = username to give to git
# $4 = passcode to give to git
createGitCredentialsHelper() {
  local scriptName="${1}"
  local scriptDir=$(dirname "${scriptName}")
  local gitUrl="${2}"
  local username="${3}"
  local passcode="${4}"
  mkdir -p "${scriptDir}"
  robustlyDelete "${scriptName}"
  (
    echo "#!/bin/sh"
    echo "echo 'url=${gitUrl}'"
    echo "echo 'username=${username}'"
    echo "echo 'password=${passcode}'"
  ) >"${scriptName}"
  chmod 700 "${scriptName}"
}

# Tries to grab existing source from git, and leaves us with a full clone.
# Note: If the remote repo doesn't exist, or we're not allowed to access it, then we'll fail.
# $1 = remote git repo
# $2 = where to put it
# $3 = credentials helper script
# returns 0 if we have a repository that's now ready for use.
# returns 1 if the remote repository does not exist, or does exist and we're not allowed to use it.
gitReadAllOfRemoteRepositry() {
  echo git clone                                   --no-checkout "${1}" "${2}"
  if   git clone --config "credential.helper=${3}" --no-checkout "${1}" "${2}"
  then
    true
  else
    robustlyDelete "${2}"
    return 1
  fi
  if [ -d "${2}/.git" ]
  then
    (
      cd "${2}"
      git config --add "credential.helper" "${3}"
    )
  else
    return 1
  fi
  (
    cd "${2}"
    echo git fetch --tags origin
         git fetch --tags origin
    # If we are searching data in git notes then we need to ensure that
    # we've fetched the notes too, as they're not fetched by default.
    echo git fetch origin 'refs/notes/commits:refs/notes/commits'
         git fetch origin 'refs/notes/commits:refs/notes/commits' || true
    echo git fetch origin 'refs/notes/*'
         git fetch origin 'refs/notes/*' || true
  )
  (
    # Git can store "orphan" commits that aren't part of any branch, tag, reference etc.
    # e.g. ones that have been deleted recently, for example by git-lfs rewriting history.
    # If we're searching, we don't want to get confused by deleted results, so we get rid
    # of all of them from our local copy before we use it as a basis for decision making.
    cd "${2}"
    echo git reflog expire --expire=now --all
         git reflog expire --expire=now --all
    echo git gc --prune=now
         git gc --prune=now
  )
}

# Scans a git repository for a commit that mentions the given ID in either its commit comment or its git note.
# $1 = where the git repo can be found
# $2 = the ID we are searching for
# stdout = the git commit ID of the commit matching the given ID, or nothing if none match
# returns code 0 if there is no match or if there is one match.
# returns code 1 if there are multiple matches.
getUniqueGitCommitFromHistoryOrEmpty() {
  local gitRepoDir="${1}"
  local idToSearchFor="${2}"
  (
    cd "${gitRepoDir}" && git log --grep="${idToSearchFor}" --fixed-strings --all '--format=%H'
  ) | (
    firstAnswer=''
    otherAnswers=''
    if read -r firstAnswer
    then
      while read -r anotherAnswer
      do
        otherAnswers="${otherAnswers} ${anotherAnswer}"
      done
    fi
    if [ -z "${otherAnswers}" ]
    then
      if [ -n "${firstAnswer}" ]
      then
        echo "${firstAnswer}"
      fi
      true
    else
      echo "ERROR: Git repo in ${gitRepoDir} found multiple commits containing ${idToSearchFor}: ${firstAnswer}${otherAnswers}." >&2
      exit 1
    fi
  )
}

# Sees if there's a git tag of the specified name that already exists ... and optionally check to see if it points at the specified commit.
# $1 = where the git repo is
# $2 = git tag name
# $3 = git commit id, or empty
# If $3 is specified then exit 0 if the tag exists and points to the specified commit
# If $3 is empty then exit 0 if the tag exists, regardless of what it points to
isGitTagAlreadySet() {
  local repoDir="$1"
  local tagName="$2"
  local commitId="${3-}"
  (
    cd "${repoDir}"
    if [ -z "${commitId}" ]
    then
      git tag -l
    else
      git tag --points-at "${commitId}"
    fi
  ) \
  | isGitTagListedInStdin "${tagName}"
}
# $1 = git tag name we're looking for
# stdin = a list of git tags, e.g. from 'git tag ...'
# exit 0 if we see $1, exit 1 if not
isGitTagListedInStdin() {
  local tagName="$1"
  local thisTag
  local exitCode=1
  while read -r thisTag
  do
    if [ "${thisTag}" = "${tagName}" ]
    then
      cat >/dev/null # consume remaining input
      exitCode=0
    fi
  done
  return ${exitCode}
}

# Deletes any workspaces of the given name.
# $1 = the name of the workspaces to delete.
deleteRTCWorkspaces() {
  # Note: If you ask RTC to list all workspaces called "foo", it will also
  # include workspaces _not_ called "foo", e.g. "Foobar", so we need to
  # post-filter the results so we only delete workspaces with the name
  # we actually specified.
  local workspaceName="${1}"
  ( cd "${ourBaseDirectory:-.}" && runScmExpectingSuccess "list workspaces" --name "${workspaceName}" --maximum 1000 ) || return 1
  local ignored_one i ignored_two j ignored_three IFS='()"'
  while read -r ignored_one i ignored_two j ignored_three; do
    if [ "${j}" = "${workspaceName}" ]
    then
      ( cd "${ourBaseDirectory:-.}" && runScmExpectingSuccess "delete snapshot"  "${i}" --all ) || return 1
      ( cd "${ourBaseDirectory:-.}" && runScmExpectingSuccess "delete workspace" "${i}"       ) || return 1
    fi
  done < "${RTCCMDS_TEMP_FILE}"
}

killRtcDaemonProcesses() {
  # RTC relies on running a background daemon to compensate for poor
  # performance and then leaves these processes running, which then causes
  # problems as they interfere with non-daemon processes, so we need to kill
  # them before we do anything that isn't purely running RTC daemon commands.
  # Officially, we should do "scm demon stop ${ourBaseDirectory}" to kill the
  # demon, but this doesn't work: If you tell it what folder the daemon is for
  # then it claims there is no daemon and fails or, if you tell it to kill all
  # of them, it does nothing and claims success (as of RTC6.0.6).
  # So we need to do it ourselves.
  local rtcDaemonParentProcesses=$(findRtcDaemonParentProcesses "${RTCCMDS_TEMP_CONFIGDIR:-}" | tr '\r\n' ' ')
  # They have one or more child processes that we need to kill too.
  local rtcDaemonProcesses=$(findDescendentProcesses ${rtcDaemonParentProcesses} | tr '\r\n' ' ')
  if [ -n "${rtcDaemonProcesses}" ]
  then
    kill ${rtcDaemonProcesses}
  fi
}

# Outputs all given PIDs and all those PIDs' children.
# $@ = pids to examine
# stdout = whitespace separated PIDs
findDescendentProcesses() {
  if [ -n "${*:-}" ]
  then
    echo "$*"
    findDescendentProcesses $(ps --ppid "$*" --format 'pid:1=')
  fi
}

# Finds parent RTC daemon processes.
# $1 = RTC config folder to look for, or empty for default
findRtcDaemonParentProcesses() {
  # RTC daemon processes can be recognised as follows:
  # They are a "scm" cmd args "--config <configdir> daemon start",
  # where <configdir> is ~/.jazz-scm by default but can be anything.
  # They have a PPID of zero (in a docker container) or one (normally).
  local ourRealUserId=$(id -ur)
  if [ -n "${1:-}" ]
  then
    local configDir="${1}"
  else
    local configDir=~/.jazz-scm
  fi
  ps -ww -U "${ourRealUserId}" --format 'ppid:1=,pid:1=,cmd=' \
    | grep -e '^[01] .*scm --config .* daemon start.*$' \
    | sed -e 's_^. \([0-9][0-9]*\) .*scm --config \(.*\) daemon start.*$_\1 \2_g' \
    | while read thisPid thisConfigDir
    do
      if [ "${thisConfigDir}" = "${configDir}" ]
      then
        echo "${thisPid}"
      fi
    done
}

# $1 = what to search for
# $2 = human-readable name of what we are looking for, e.g. "snapshots called foo in stream bar"
# $3 = (optional) text to match the remainder of the line
# Note: Comparison is case-sensitive.
# returns 0 if nothing was found
# returns 0 if one thing was found
# outputs an error to stderr and returns 1 if more than one thing was found.
getUniqueRTCUuidFromListOrEmpty() {
  local nameToFind="${1}"
  shift
  local whatWeAreLookingFor="${1}"
  shift
  local thingAfterNameToFind="${1:-}"
  if [ -z "${thingAfterNameToFind}" ]
  then
    local clarification=''
  else
    local clarification=" (${thingAfterNameToFind})"
  fi
  local numberFound='0'
  local firstFound=''
  local whole_line i j k
  while read -r whole_line; do
    # Expecting lines to be of the form:
    # (uuid) "whatToSearchFor" restOfLine
    # OR
    # SomethingWeIgnore (uuid) "whatToSearchFor" restOfLine
    # The former is output by commands that list things,
    # the latter when creating things.
    i=`expr "${whole_line}" : '^[^(]*(\([^)]*\)) "[^"]*".*$'`
    j=`expr "${whole_line}" : '^[^(]*([^)]*) "\([^"]*\)".*$'`
    k=`expr "${whole_line}" : '^[^(]*([^)]*) "[^"]*"\(.*\)$'`
    if [ "${j}" = "${nameToFind}" -a -z "${thingAfterNameToFind}" ] \
    || [ "${j}" = "${nameToFind}" -a "${k}" = " ${thingAfterNameToFind}" ]
    then
      case "${numberFound}"
      in
        0)
          firstFound="${i}"
          echo "${i}"
          numberFound=1
          ;;
        1)
          echo "Error: Multiple ${whatWeAreLookingFor} named '${nameToFind}'${clarification} found: ${firstFound}" >&2
          echo "Error: Multiple ${whatWeAreLookingFor} named '${nameToFind}'${clarification} found: ${i}" >&2
          numberFound="multiple"
          ;;
        *)
          echo "Error: Multiple ${whatWeAreLookingFor} named '${nameToFind}'${clarification} found: ${i}" >&2
          ;;
      esac
    fi
  done < "${RTCCMDS_TEMP_FILE}"
  if [ "${numberFound}" = "multiple" ]
  then
    return 1
  fi
  return 0
}

# Parses the results of a "scm show attributes --snapshot ..." call.
# Given stdin of the form:
#Name: (_AxHVFmW_Eem6xuuUsmf6Xg) "Apollo_Search_Index_Files_j53_Snapshot"
#Description:
#Snapshot based on snapshot "_xGLGNWW-Eem6xuuUsmf6Xg" for build https://uk-apollo-build.hursley.ibm.com/job/Apollo_Search_Index_Files/53/.
#Owned by: (_ux1LgJg3EeSmgfdjgmzAQg) "Apollo_Search_Index_Files"
#
# $1 = name of variable into which to store the snapshot UUID, or empty to ignore this field.
# $2 = name of variable into which to store the snapshot name, or empty to ignore this field.
# $3 = name of variable into which to store the snapshot description, or empty to ignore this field.
#
# Note: Description is optional - show attributes doesn't give you an empty line if there isn't one, it just doesn't output the header line at all.
# Note2: Description can be multi-line - the last line should be the "owned by" line but everything before that is description.
parseRtcSnapshotAttributes() {
  local varNameUuid="$1"
  local varNameName="$2"
  local varNameDesc="$3"
  local line
  local previousLine
  local lineNumber=1
  if ! read -r line
  then
    echo "Unexpected EOF before line ${lineNumber} while parsing show attributes --snapshot ... result; was expecting Name and UUID." >&2
    return 1
  fi
  if ! expr "${line}" : '^Name: (_......................) ".*"$' >/dev/null
  then
    echo "Unexpected content on line ${lineNumber} while parsing show attributes --snapshot ... result; was expecting Name and UUID but got: ${line}" >&2
    return 1
  fi
  local uuid=$( expr "${line}" : '^Name: (\(_......................\)) ".*"$' )
  local name=$( expr "${line}" : '^Name: (_......................) "\(.*\)"$' )
  ((lineNumber++))
  previousLine="${line}"
  if ! read -r line
  then
    echo "Unexpected EOF before line ${lineNumber} while parsing show attributes --snapshot ... result; was expecting Description or Owned by." >&2
    return 1
  fi
  local eol=$'\n'
  local description=""
  if [ "${line}" = 'Description:' ]
  then
    if ((lineNumber++)) && previousLine="${line}" && read -r line
    then
      while ((lineNumber++)) && previousLine="${line}" && read -r line
      do
        # ignore latest line but append previous one to the description.
        if [ -z "${description}" ]
        then
          description="${previousLine}"
        else
          description="${description}${eol}${previousLine}"
        fi
      done
    else
      echo "Unexpected EOF before line ${lineNumber} while parsing show attributes --snapshot ... result; was expecting description text." >&2
      return 1
    fi
  else
    previousLine="${line}"
  fi
  if ! expr "${previousLine}" : '^Owned by: (_......................) ".*"$' >/dev/null
  then
    echo "Unexpected content on line ${lineNumber} while parsing show attributes --snapshot ... result; was expecting Owned by but got: ${previousLine}" >&2
    return 1
  fi
  if [ -n "${varNameUuid}" ]
  then
    local -n varRefUuid="${varNameUuid}"
    varRefUuid="${uuid}"
  fi
  if [ -n "${varNameName}" ]
  then
    local -n varRefName="${varNameName}"
    varRefName="${name}"
  fi
  if [ -n "${varNameDesc}" ]
  then
    local -n varRefDesc="${varNameDesc}"
    varRefDesc="${description}"
  fi
}

# Parses the results of a "scm list changesets --workspace ..." call.
# Given stdin of the form:
#Change sets:
#  (_w-UnMGW9Eem6xuuUsmf6Xg) ----$ Hlavaty, Ariana Isabel "Version bump for Sparta - For: AUR-2424" 23-Apr-2019 01:49 PM
#  (_PMgqkP1nEei0asbNNwo4cA) ----$ Memarzia, K D "ORION-2535 Version bumps" 11-Dec-2018 06:07 PM
#  (_dd7nAG73EeiCxPketacJtA) ----$ Pook, P D "SCG-3 - version bump to Rouge" 13-Jun-2018 03:48 PM
#  (_G2e7UCaxEeiecP2VaOmkKA) ----$ Pook, P D "Update version to Queen: #ORION-1189" 13-Mar-2018 01:03 PM
#  (_gJTPMCYIEeiecP2VaOmkKA) ----$ Pook, P D "Update version to Queen : #ORION-1189" 13-Mar-2018 11:10 AM
#  (_dizCcOFpEeezDY_wA2cI8Q) ----$ Stevinson, E G "Update fetch manifests to Pegasus" 15-Dec-2017 08:29 AM
#  (_sJthgN9FEeezDY_wA2cI8Q) ----$ Stevinson, E G "Update to Pegasus" 13-Dec-2017 11:57 AM
#  (_5qS0AJPKEeevReX2x3tLGA) ----$ Golova, Elizaveta "Component Version updated" 08-Sep-2017 11:55 AM
#  (_TTSTUEzxEeeMHP_w8FRFGA) ----$ Cowie, Colvin "Update" 09-Jun-2017 10:55 AM
#  (_S4e0gMK2Eeaabsau26XqSw) ----$ Hlavaty, David "Update component versions" 15-Dec-2016 12:12 PM
#There are more items available. Use --maximum option to return more items.
#
# HOWEVER it can be more complicated than what's officially documented.
# While the RTC docs only give "----$" as an example and give no other explanation for alternatives from
# "list changesets", in practise the command is less predictable.
# Elsewhere in the RTC documentation there's a side note listing "SCM status flags" which can be any of
# "*@#!$acpdmACIGMSl>", and it seems that there are specific places in the output that these flags can appear in,
# but this isn't documented either ... but neither is the "-" character and we know that's normally output.
# We do know that it's possible to get "S---$" instead of "----$" so we have to assume that the other characters
# from that "SCM status" list are also possible.
# So, rather than second guess what's possible, we'll accept any of them, in any order, as long as there's 5 of them.
#
# ...but having parsed all that, this function returns just the changeset UUIDs.
getChangesetIDsFromRtcWorkspaceHistory() {
  local line
  local lineNumber=1
  if ! read line
  then
    echo "Unexpected EOF: Unable to read line ${lineNumber}." >&2
    return 1
  fi
  if [ "${line}" != 'Change sets:' ]
  then
    echo "Unexpected contents on line ${lineNumber}: \"${line}\"." >&2
    return 1
  fi
  ((lineNumber++))
  while read line
  do
    local uuid=''
    uuid=$( expr "${line}" : '(\(_......................\)) [-*@#!$acpdmACIGMSl>][-*@#!$acpdmACIGMSl>][-*@#!$acpdmACIGMSl>][-*@#!$acpdmACIGMSl>][-*@#!$acpdmACIGMSl>].*$' )
    if [ -n "${uuid}" ]
    then
      echo "${uuid}"
    elif [ "${line}" = 'There are more items available. Use --maximum option to return more items.' ]
    then
      return 0
    else
      echo "Unexpected contents on line ${lineNumber}: \"${line}\"." >&2
      return 1
    fi
    ((lineNumber++))
  done
}

# Gets the creation date of a snapshot.
# $3 = Snapshot UUID
# stdin = output from "scm list snapshots ..." such that the desired snapshot is listed.
# stdout = YYYY-MM-DDThh:mm:ss, although seconds will be 00 as RTC doesn't store seconds
# or nothing at all.
parseSnapshotTimestamp() {
  # You can't get this information by asking RTC to tell you everything about a snapshot.
  # You have to ask RTC to list all snapshots, and then find the one you asked for
  # (as specifying a name doesn't limit output to just that name) in the output
  # and then looking for the date at the end.
  local snapUuid="${1}"
  shift
  listMatchingSnapshotsFromJson '.*' "${TABCHARACTER}" \
  | (
    IFS="${TABCHARACTER}"
    while read -r lineUuid lineIsoCreationDate restOfLineIgnored
    do
      if [ "${lineUuid}" = "${snapUuid}" ]
      then
        echo "${lineIsoCreationDate}"
      fi
    done
    )
}

# Processes the output of a "list snapshots ... --json" command
# $1 = regex to match each snapshot name
# $2 = string to use to separate the fields in the output.
# stdin is json from RTC of the form:
# {
#     "snapshots": [
#         {
# ...
#         },
# ...
#         {
#             "comment": "<No comment>",
#             "creationDate": "09-Jan-2015 20:45",
#             "name": "Apollo_Search_Index_Files_Snapshot_2",
#             "ownedby": {
#                 "name": "Apollo_Search_Index_Files",
#                 "type": "STREAM",
#                 "url": "https:\/\/jazzc04.hursley.ibm.com:9443\/ccm\/",
#                 "uuid": "_ux1LgJg3EeSmgfdjgmzAQg"
#             },
#             "url": "https:\/\/jazzc04.hursley.ibm.com:9443\/ccm\/",
#             "uuid": "_F8VjQ5g4EeSmgfdjgmzAQg"
#         }
#     ]
# }
# stdout will be zero or more lines of the form
#   "${uuid}${2}${isoCreationDate}${2}${name}${2}${comment}"
listMatchingSnapshotsFromJson() {
  local nameRegex="${1}"
  local fieldSeparator="${2}"
  local line
  read line && [ "${line}" = '{' ]
  read line && [ "${line}" = '"snapshots": [' ]
  while read line; do
    if [ "${line}" = '{' ]
    then
      outputSnapshotFromJsonIfMatching "${nameRegex}" "${fieldSeparator}"
    elif [ "${line}" = ']' ]
    then
      read line
      [ "${line}" = '}' ]
    else
      echo "ERROR: Unexpected text (${line}) in json snapshot list." >&2
      return 1
    fi
  done
  return 0
}
# Processes a single snapshot from the output of a "list snapshots ... --json" command
# $1 = regex to match each snapshot name
# $2 = string to use to separate the fields in the output.
# stdin is json from RTC of the form:
#             "comment": "Migrated to git@github.com:i2group\/Apollo_Search_Index_Files.git commit 5c151bd50e264e8e585e47949e9200f2e03eeb5e",
#             "creationDate": "23-May-2019 12:47",
#             "name": "Migrated Apollo_Search_Index_Files to git@github.com:i2group\/Apollo_Search_Index_Files.git",
#             "ownedby": {
#                 "name": "Apollo_Search_Index_Files",
#                 "type": "STREAM",
#                 "url": "https:\/\/jazzc04.hursley.ibm.com:9443\/ccm\/",
#                 "uuid": "_ux1LgJg3EeSmgfdjgmzAQg"
#             },
#             "url": "https:\/\/jazzc04.hursley.ibm.com:9443\/ccm\/",
#             "uuid": "_IF4CZ31IEemys5VCh8RCyA"
#         },
outputSnapshotFromJsonIfMatching() {
  local nameRegex="${1}"
  local fieldSeparator="${2}"
  local line
  read -r line || return 1
  local escapedComment=$( expr "${line}" : '"comment": "\(.*\)",' ) || return 1
  local comment=$( unescapeJsonString "${escapedComment}" ) || return 1
  # comment can be empty
  read -r line || return 1
  local escapedCreationDate=$( expr "${line}" : '"creationDate": "\(.*\)",' ) || return 1
  local creationDate=$( unescapeJsonString "${escapedCreationDate}" ) || return 1
  [ -n "${creationDate}" ] || return 1 # date can't be empty
  read -r line || return 1
  local escapedName=$( expr "${line}" : '"name": "\(.*\)",' ) || return 1
  local name=$( unescapeJsonString "${escapedName}" ) || return 1
  # sadly, names can be empty too
  read line && [ "${line}" = '"ownedby": {' ] || return 1
  while [ "${line}" != '},' ]
  do
    read -r line || return 1
  done
  read -r line || return 1
  local escapedUrl=$( expr "${line}" : '"url": "\(.*\)",' ) || return 1
  read -r line || return 1
  # don't care about the url
  local escapedUuid=$( expr "${line}" : '"uuid": "\(.*\)"' ) || return 1 ## uuid is last in block so no comma
  local uuid=$( unescapeJsonString "${escapedUuid}" ) || return 1
  [ -n "${uuid}" ] || return 1 # uuid can't be empty
  read -r line || return 1
  [ "${line}" = '},' -o "${line}" = '}' ] || return 1 # expect end of json block
  if expr "${name}" : "${nameRegex}"'$' >/dev/null
  then
    local isoCreationDate=$(date --date="${creationDate}" --utc '+%FT%R') || return 1
    echo "${uuid}${fieldSeparator}${isoCreationDate}${fieldSeparator}${name}${fieldSeparator}${comment}"
  fi
}
unescapeJsonString() {
  echo "$*" | sed \
    -e 's,\\b,\b,g' \
    -e 's,\\f,\f,g' \
    -e 's,\\n,\n,g' \
    -e 's,\\r,\r,g' \
    -e 's,\\",",g' \
    -e 's,\\/,/,g' \
    -e 's,\\\\,\\,g' \
    || true
}

# $1 = value that must be a non-negative integer
isNonNegativeInteger() {
  expr "$1" : '[0-9][0-9]*$' >/dev/null 2>&1
}


# Setup variables to hold the parsed arguments.
arg_gitRepository=""
arg_gitUsername=""
arg_gitPassword=""
arg_rtcUri=""
arg_rtcUsername=""
arg_rtcPassword=""
arg_rtcSource=""
arg_rtcProjectArea=""
# leave unset arg_rtcTeamName=""
arg_skip="existing"
arg_maxChangesUniqueToSnapshot="10"
arg_fieldSeparator="${TABCHARACTER}"
arg_outFile=""
arg_commentPrefix="# "
arg_rtcSnapshotUUIDs=""

# Parse CLI arguments
while [ "$#" -gt 0 ]
do
  case "${1}" in
    help|-h|--help)
      doHelp; exit 0
      ;;
    --gitRepository|--gitUsername|--gitPassword|--rtcUri|--rtcUsername|--rtcPassword|--rtcSource|--rtcProjectArea|--rtcTeamName|--skip|--maxChangesUniqueToSnapshot|--fieldSeparator|--outFile|--commentPrefix)
      if [ "${2-}" = "" -a "${2-X}" = "X" ]; then
        handleArgAndValue "${1}" || ( doHelp >&2 ; exit 1 ) || exit 1
      else
        handleArgAndValue "${1}" "${2}" || ( doHelp >&2 ; exit 1 ) || exit 1
        shift
      fi
      ;;
    --gitRepositoryEnvName|--gitUsernameEnvName|--gitPasswordEnvName|--rtcUriEnvName|--rtcUsernameEnvName|--rtcPasswordEnvName|--rtcSourceEnvName|--rtcProjectAreaEnvName|--rtcTeamNameEnvName|--skipEnvName|--maxChangesUniqueToSnapshotEnvName|--fieldSeparatorEnvName|--outFileEnvName|--commentPrefixEnvName)
      handleArgAndEnvName "${1}" "${2:-}" || ( doHelp >&2 ; exit 1 ) || exit 1
      shift
      ;;
    _*)
      arg_rtcSnapshotUUIDs="${arg_rtcSnapshotUUIDs} ${1}"
      ;;
    *)
      (echo "Unrecognised argument '${1}'"; doHelp) >&2; exit 1
      ;;
  esac
  shift
done

# Validate arguments
if [ -z "${arg_gitRepository}" ];then
  echo "Error: gitRepository argument was not provided." >&2 ; doHelp >&2 ; exit 1
fi
if [ -z "${arg_gitUsername}" ];then
  handleArgAndEnvName --gitUsernameEnvName GIT_USERNAME || ( doHelp >&2 ; exit 1 ) || exit 1
fi
if [ -z "${arg_gitPassword}" ];then
  handleArgAndEnvName --gitPasswordEnvName GIT_PASSWORD || ( doHelp >&2 ; exit 1 ) || exit 1
fi
if [ -z "${arg_rtcUri}" ];then
  arg_rtcUri='https://jazzc04.hursley.ibm.com:9443/ccm/'
fi
RTC_URL="${arg_rtcUri}"
if [ -z "${arg_rtcUsername}" ];then
  handleArgAndEnvName --rtcUsernameEnvName RTC_USERNAME || ( doHelp >&2 ; exit 1 ) || exit 1
else
  RTC_USERNAME="${arg_rtcUsername}"
fi
if [ -z "${arg_rtcPassword}" ];then
  handleArgAndEnvName --rtcPasswordEnvName RTC_PASSWORD || ( doHelp >&2 ; exit 1 ) || exit 1
else
  RTC_PASSWORD="${arg_rtcPassword}"
fi
if [ -z "${arg_rtcSource}" ];then
  echo "Error: rtcSource argument was not provided." >&2 ; doHelp >&2 ; exit 1
fi
if [ -z "${arg_rtcProjectArea}" -a "${arg_rtcTeamName-}" != "" -a "${arg_rtcTeamName-X}" != "X" ];then
  echo "Error: rtcTeamName was set but rtcProjectArea argument was not provided." >&2 ; doHelp >&2 ; exit 1
fi
if [ -n "${arg_rtcProjectArea}" -a  "${arg_rtcTeamName-}" = "" -a "${arg_rtcTeamName-X}" = "X" ];then
  echo "Error: rtcProjectArea was set but rtcTeamName argument was not provided." >&2 ; doHelp >&2 ; exit 1
fi
if [ "${arg_skip}" != "none" -a "${arg_skip}" != "matching" -a "${arg_skip}" != "existing" ];then
  echo "Error: skip argument (${arg_skip}) must be 'none', 'matching' or 'existing'." >&2 ; doHelp >&2 ; exit 1
fi
if [ "${arg_maxChangesUniqueToSnapshot}" != 'all' ] && ( ! isNonNegativeInteger "${arg_maxChangesUniqueToSnapshot}" || [ "${arg_maxChangesUniqueToSnapshot}" -le 0 ] ); then
  echo "Error: maxChangesUniqueToSnapshot argument (${arg_maxChangesUniqueToSnapshot}) was neither 'all' or an integer greater than zero." >&2 ; doHelp >/dev/null ; exit 1
fi
if [ -z "${arg_fieldSeparator}" ];then
  echo "Error: fieldSeparator argument cannot be empty." >&2 ; doHelp >&2 ; exit 1
fi
if [ -z "${arg_rtcSnapshotUUIDs}" ];then
  echo "Error: No rtcSnapshotUUID arguments provided." >&2 ; doHelp >&2 ; exit 1
fi


# Pull in our RTC utility functions
# These need various RTC_... variables to be set, which is why we delay until now.
export RTC_EXE_NAME=lscm
. "${SCRIPT_DIR}/setRtcVariables.sh" || (echo "Something went wrong in ${SCRIPT_DIR}/setRtcVariables.sh" >&2 ; exit 1)
# Ensure that any sub-commands we run use the exact same uniqueness, otherwise we can end up using a different config area, different logs etc.
export RTCCMDS_UNIQUE_NAME



ourTempTempFolderWeMade=$(mktemp --directory --tmpdir "${SCRIPT_NAME}.XXXXXXXXXX")
ourBaseDirectory="${ourTempTempFolderWeMade}"
gitCredentialsHelperScript="${ourBaseDirectory}/gitCredentials.sh"
gitLocalRepoDir="${ourBaseDirectory}/git"
rtcSourceWorkspace=""

# Set up a robust cleanup routine that'll get called even if we crash out with an error.
cleanUp_rtcSourceWorkspace=false
cleanUpOnExit() {
  echo "Cleaning up before exiting..."
  if [ "${cleanUp_rtcSourceWorkspace}" != "false" -a -n "${rtcSourceWorkspace:-}" ]
  then
    deleteRTCWorkspaces "${rtcSourceWorkspace}"
    cleanUp_rtcSourceWorkspace=false
  fi
  killRtcDaemonProcesses || true
  if [ "${ourTempTempFolderWeMade}" != "" ]
  then
    robustlyDelete "${ourTempTempFolderWeMade}"
  fi
  robustlyDelete "${gitCredentialsHelperScript}" || true
  robustlyDelete "${gitLocalRepoDir}" || true
  cleanUpRtcTempFiles
  echo "...clean up before exiting now complete."
}
trap cleanUpOnExit EXIT ERR


robustlyDelete "${gitCredentialsHelperScript}" || ( echo "Error: Internal error: Unable to delete file '${gitCredentialsHelperScript}'." >&2 ; exit 1 )
robustlyDelete "${gitLocalRepoDir}" || ( echo "Error: Internal error: Unable to delete folder '${gitLocalRepoDir}'." >&2 ; exit 1 )

# OK, first we need to set up git so we can talk to the remote end
createGitCredentialsHelper "${gitCredentialsHelperScript}" "${arg_gitRepository}" "${arg_gitUsername}" "${arg_gitPassword}"

# Verify that the Git stuff we've been given is valid
if gitReadAllOfRemoteRepositry "${arg_gitRepository}" "${gitLocalRepoDir}" "${gitCredentialsHelperScript}"
then
  true
else
  echo "Error: Git repository '${arg_gitRepository}' either does not exist or we are not permitted to see it." >&2
  exit 1
fi

# Verify that the RTC stuff we've been given is valid
# This will implicitly check rtc URL and login details.
# Note: We can't easily filter by project area, so we often have to rely on stream names being
# globally unique.
# RTC has a bug whereby, if you have project area "foo" containing a team "bar", and you tell it to
# find something in project area "foo", it will FAIL to find anything that's in "foo" if it's also
# inside bar, and moving things in and out of team areas within a project area is far from rare.
# So, in order to prevent a stream from "suddenly going missing" just because a stream has been
# moved into a team within a project area, we have to _not_ limit our search to just a project area
# we've been told to look at, so we don't do --projectarea "${arg_rtcProjectArea}" unless we're
# absolutely sure we're not looking inside a teamarea.
if [ -z "${arg_rtcProjectArea}" ]
then
  ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "list streams" --name "${arg_rtcSource}" --maximum 1000 )
  rtcSourceUUID=$(getUniqueRTCUuidFromListOrEmpty "${arg_rtcSource}" "streams")
  rtcSourceLocation=''
else
  if [ -z "${arg_rtcTeamName}" ]
  then
    ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "list streams" --projectarea "${arg_rtcProjectArea}" --name "${arg_rtcSource}" --maximum 1000 )
    rtcSourceUUID=$(getUniqueRTCUuidFromListOrEmpty "${arg_rtcSource}" "streams" "${arg_rtcProjectArea}")
    rtcSourceLocation=" in project area '${arg_rtcProjectArea}'"
  else
    # Note: RTC has a bug whereby you're not allowed to specify BOTH a teamName and a ProjectArea
    # ... even if you need to, as RTC can error and complain that multiple ProjectAreas have teams
    # with the same name.
    # So, if you hit this, raise the issue with RTC support, prepare to wait a few years, and use
    # a UUID of the team in the meantime.
    ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "list streams" --teamarea "${arg_rtcTeamName}" --name "${arg_rtcSource}" --maximum 1000 )
    rtcSourceUUID=$(getUniqueRTCUuidFromListOrEmpty "${arg_rtcSource}" "streams")
    rtcSourceLocation=" in project area '${arg_rtcProjectArea}' team area '${arg_rtcTeamName}'"
  fi
fi
if [ -z "${rtcSourceUUID}" ]
then
  echo "Error: No stream called '${arg_rtcSource}' was found${rtcSourceLocation}." >&2
  exit 1
fi
echo "Info: Found RTC stream called '${arg_rtcSource}'${rtcSourceLocation} (${rtcSourceUUID})."

rtcSourceWorkspace="idSnapshotBranches_${rtcSourceUUID}_$$_s"

declare -a linesToOutputAtEnd
for rtcSnapshotUUID in ${arg_rtcSnapshotUUIDs}
do
  # Read RTC snapshot name and description
  rtcSnapshotName=''
  rtcSnapshotDescription=''
  robustlyDelete "${RTCCMDS_TEMP_FILE}"
  ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "show attributes" \
    --snapshot "${rtcSnapshotUUID}" )
  parseRtcSnapshotAttributes '' rtcSnapshotName rtcSnapshotDescription < "${RTCCMDS_TEMP_FILE}"
  # Read RTC snapshot timestamp
  robustlyDelete "${RTCCMDS_TEMP_FILE}"
  ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "list snapshots" \
      --json \
      --maximum 1000 \
      --name "${rtcSnapshotName}" )
  rtcSnapshotTimestamp=$(parseSnapshotTimestamp "${rtcSnapshotUUID}" < "${RTCCMDS_TEMP_FILE}") \
    || (
      echo "Error: Unable to determine creation time of snapshot '${rtcSnapshotName}' (${rtcSnapshotUUID})."
      exit 1
    ) >&2 || exit 1
  # Create RTC workspace based on the snapshot so we can interrogate the history
  deleteRTCWorkspaces "${rtcSourceWorkspace}"
  cleanUp_rtcSourceWorkspace=true
  robustlyDelete "${RTCCMDS_TEMP_FILE}"
  ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "create workspace" \
    --description "Based on snapshot ${rtcSnapshotUUID}" \
    --snapshot "${rtcSnapshotUUID}" "${rtcSourceWorkspace}" )
  rtcSourceWorkspaceUUID=$(getUniqueRTCUuidFromListOrEmpty "${rtcSourceWorkspace}" "workspaces created")
  if [ -n "${rtcSourceWorkspaceUUID}" ]
  then
    echo "Info: Created RTC workspace '${rtcSourceWorkspace}' (${rtcSourceWorkspaceUUID}), based on RTC stream '${arg_rtcSource}' (${rtcSourceUUID}) snapshot '${rtcSnapshotName}' (${rtcSnapshotUUID})."
  else
    echo "Error: Internal error: Unable to find RTC workspace '${rtcSourceWorkspace}' that we just created." >&2
    echo "RTC command output was:" >&2
    echo "-----" >&2
    cat "${RTCCMDS_TEMP_FILE}" >&2
    echo "-----" >&2
    exit 1
  fi
  # List the changesets that happened most recently on that workspace
  robustlyDelete "${RTCCMDS_TEMP_FILE}"
  ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "list changesets" \
    --maximum "${arg_maxChangesUniqueToSnapshot}" \
    --workspace "${rtcSourceWorkspaceUUID}" )
  rtcSnapshotChangesetHistory=$(getChangesetIDsFromRtcWorkspaceHistory < "${RTCCMDS_TEMP_FILE}") \
    || (
      cat "${RTCCMDS_TEMP_FILE}"
      echo "Error: Unable to decipher RTC changeset history for workspace ${rtcSourceWorkspaceUUID}."
      exit 1
    ) >&2 || exit 1
  rtcSnapshotBranchRtcChangeset=''
  rtcSnapshotBranchGitCommit=''
  # Iterate over those changesets
  for rtcSnapshotChangesetFromHistory in ${rtcSnapshotChangesetHistory}
  do
    # Ask git to look for the RTC changesetUUIDs in the git history
    rtcSnapshotBranchGitCommit=$(getUniqueGitCommitFromHistoryOrEmpty "${gitLocalRepoDir}" "${rtcSnapshotChangesetFromHistory}") \
    || (
      echo "Error: Unable to locate snapshot branchpoint: RTC snapshot '${rtcSnapshotName}' (${rtcSnapshotUUID}) recent changeset ${rtcSnapshotChangesetFromHistory} is found multiple times in git history."
      exit 1
    ) >&2 || exit 1
    # If git finds it, that gives us the git-commit-id and we stop looking.
    if [ -n "${rtcSnapshotBranchGitCommit}" ]
    then
      rtcSnapshotBranchRtcChangeset="${rtcSnapshotChangesetFromHistory}"
      break
    fi
    # If we don't find it, we keep looking...
  done
  # ...and fail if we run out of changesets to look for.
  if [ -z "${rtcSnapshotBranchRtcChangeset}" -o -z "${rtcSnapshotBranchGitCommit}" ]
  then
    echo "Error: Unable to locate snapshot branchpoint: RTC snapshot '${rtcSnapshotName}' (${rtcSnapshotUUID}) recent changeset history (${rtcSnapshotChangesetHistory}) has nothing in common with git history." >&2
    exit 1
  fi
  reasonForSkippingThisOne=""
  case "${arg_skip}"
  in
    matching)
      if isGitTagAlreadySet "${gitLocalRepoDir}" "${rtcSnapshotName}" "${rtcSnapshotBranchGitCommit}"
      then
        reasonForSkippingThisOne="git tag '${rtcSnapshotName}' already exists and is ${rtcSnapshotBranchGitCommit}"
      fi
      ;;
    existing)
      if isGitTagAlreadySet "${gitLocalRepoDir}" "${rtcSnapshotName}"
      then
        reasonForSkippingThisOne="git tag '${rtcSnapshotName}' already exists"
      fi
      ;;
  esac
  # <RTC-Snapshot-Timestamp><fieldSeparator><RTC-Snapshot-UUID><fieldSeparator><RTC-changeset-UUID><fieldSeparator><Git-commit-ID><fieldSeparator>"<RTC-Snapshot-Name>"<fieldSeparator>"<RTC-Snapshot-Comment>"
  lineToOutputForThisOne="${rtcSnapshotTimestamp}${arg_fieldSeparator}${rtcSnapshotUUID}${arg_fieldSeparator}${rtcSnapshotBranchRtcChangeset}${arg_fieldSeparator}${rtcSnapshotBranchGitCommit}${arg_fieldSeparator}\"${rtcSnapshotName}\"${arg_fieldSeparator}\"${rtcSnapshotDescription}\""
  if [ -n "${reasonForSkippingThisOne}" ]
  then
    if [ -n "${arg_commentPrefix}" ]
    then
      linesToOutputAtEnd+=("${arg_commentPrefix}${rtcSnapshotTimestamp}${arg_fieldSeparator}${rtcSnapshotUUID}${arg_fieldSeparator}skipped as ${reasonForSkippingThisOne}")
    else
      echo "Info: Skipped RTC snapshot '${rtcSnapshotName}' (${rtcSnapshotUUID}) as ${reasonForSkippingThisOne}"
    fi
  else
    linesToOutputAtEnd+=("${lineToOutputForThisOne}")
  fi
done

# Lastly we clean up
echo "Info: Cleaning up..."
( cd "${ourBaseDirectory}" && runScmExpectingSuccess "delete workspace" \
  "${rtcSourceWorkspaceUUID}" )
cleanUp_rtcSourceWorkspace=false
killRtcDaemonProcesses
robustlyDelete "${gitLocalRepoDir}"
robustlyDelete "${gitCredentialsHelperScript}"
cleanUpRtcTempFiles
echo "Info: Clean up complete - we are done here."
trap - EXIT ERR
if [ -n "${arg_outFile}" ]
then
  robustlyDelete "${arg_outFile}"
  touch "${arg_outFile}"
fi
if [ -n "${arg_commentPrefix}" ]
then
  for headerLine in \
    "Snapshot branchpoints linking ${arg_rtcUri}" \
    "  Stream '${arg_rtcSource}' (${rtcSourceUUID})" \
    "  with ${arg_gitRepository}" \
    "" \
    "YYYY-MM-DDThh:mm${arg_fieldSeparator}rtcSnapshotUUID........${arg_fieldSeparator}branchChangesetUUID....${arg_fieldSeparator}branchGitCommit.........................${arg_fieldSeparator}\"rtcSnapshotName\"${arg_fieldSeparator}\"rtcSnapshotDescription\""
  do
    echo "${arg_commentPrefix}${headerLine}"
    if [ -n "${arg_outFile}" ]
    then
      echo "${arg_commentPrefix}${headerLine}" >> "${arg_outFile}"
    fi
  done
fi
for linesToOutputAtEndIndex in "${!linesToOutputAtEnd[@]}"
do
  echo "${linesToOutputAtEnd[$linesToOutputAtEndIndex]}"
  if [ -n "${arg_outFile}" ]
  then
    echo "${linesToOutputAtEnd[$linesToOutputAtEndIndex]}" >> "${arg_outFile}"
  fi
done
