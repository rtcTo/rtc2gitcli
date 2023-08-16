#!/bin/bash
# Script to add multiple RTC snapshots to a migrated Git repo as annotated Git
# tags.
# RTC snapshots are not necessarily *on* the main history, they can be some
# changesets removed, so to add them to Git we need to identify where, in Git's
# history and in RTC's history, the snapshot parted from the main branch.
#
# Before this script can be used, you'll need a file that specifies the
# snapshot name, description & timestamp, plus the point (RTC changeset and git
# commit) where it's history diverges from the main history.
# You can use identifyRTCSnapshotBranchpointsInRTCAndGit.sh for that.
#
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
  ${SCRIPT_NAME} --gitRepository ... [--gitUsername ...] [--gitPassword ...] [--gitLfs ...] [--gitLfsAboveSize ...] [--rtcUri ...] [--rtcUsername ...] [--rtcPassword ...] --rtcSource ... [--rtcProjectArea ... --rtcTeamName ...] [--rtcIncludeRoot ...] --inFile ... [--fieldSeparator ...] [--commentPrefix ...] [--test ...]
Where
  --gitRepository "URL"
    Specifies the git repository that the RTC changes will be pushed to.
    If this already exists then its history will be appended to.
  --gitUsername "username"
  --gitPassword "password"
    Specifies the credentials to be used when logging into git to clone and push.
    Note: For security reasons, it is better to use the --gitPasswordEnvName form (see below).
    Defaults to using environment variables GIT_USERNAME and GIT_PASSWORD.
  --gitLfs "fileOrPattern,fileOrPattern..." (Optional)
    Specifies filenames and/or file patterns for git paths to be migrated into git-lfs before any git-push.
    The value given will be passed to migrateFromRTCtoGit.sh.
    Defaults to nothing.
  --gitLfsAboveSize "sizeAndUnits" (Optional)
    Specifies a size threshold above which files will be migrated into git-lfs before any git-push.
    The value given will be passed to migrateFromRTCtoGit.sh.
    Defaults to nothing (as in empty string, not 0b).
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
  --rtcIncludeRoot true|false
    Should be set consistently to how the main (non-snapshot) code was migrated.
    Defaults to false.
  --inFile "filename"
    Specifies the file that contains the snapshot data to be migrated (expected syntax is described below).
    If this is "-" then we will read from stdin.
  --fieldSeparator "string" (Optional)
    Specifies how to separate the different columns when reading the input.
    Defaults to a tab.
  --commentPrefix "linePrefix" (Optional)
    Specifies a series of characters that indicate a comment in the input file, e.g. '# '.
    If this is empty, the input file must not contain any comments, only data.
    Defaults to "# ".
  --test true or false (Optional)
    If true, we only echo out what we would have run instead of running it as well.
  --xxxEnvName "environmentVariableName"
    For any/all of the above arguments, specifies that the value for argument 'xxx' is to be read from
    the specified environment variable.
Description:
  Reads the input file, skipping any comments.
  For each line in turn.
   - Reads a line of the form:
     <RTC-Snapshot-Timestamp><fieldSeparator><RTC-Snapshot-UUID><fieldSeparator><RTC-changeset-UUID><fieldSeparator><Git-commit-ID><fieldSeparator>"<RTC-Snapshot-Name>"<fieldSeparator>"<RTC-Snapshot-Comment>"
   - Invokes migrateFromRTCtoGit.sh to add that snapshot to the git repository.
ENDHELP
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

# $1 = value that must be 'true' or 'false'
isTrueOrFalse() {
  [ "$1" = "true" -o "$1" = "false" ]
}

# Setup variables to hold the parsed arguments.
arg_gitRepository=""
arg_gitUsername=""
arg_gitPassword=""
arg_gitLfs=""
arg_gitLfsAboveSize=""
arg_rtcUri=""
arg_rtcUsername=""
arg_rtcPassword=""
arg_rtcSource=""
arg_rtcProjectArea=""
# leave unset arg_rtcTeamName=""
arg_rtcIncludeRoot="false"
arg_inFile=""
arg_test="false"
arg_fieldSeparator="${TABCHARACTER}"
arg_commentPrefix="# "

# Parse CLI arguments
while [ "$#" -gt 0 ]
do
  case "${1}" in
    help|-h|--help)
      doHelp; exit 0
      ;;
    --gitRepository|--gitUsername|--gitPassword|--gitLfs|--gitLfsAboveSize|--rtcUri|--rtcUsername|--rtcPassword|--rtcSource|--rtcProjectArea|--rtcTeamName|--rtcIncludeRoot|--inFile|--fieldSeparator|--commentPrefix|--test)
      if [ "${2-}" = "" -a "${2-X}" = "X" ]; then
        handleArgAndValue "${1}" || ( doHelp >&2 ; exit 1 ) || exit 1
      else
        handleArgAndValue "${1}" "${2}" || ( doHelp >&2 ; exit 1 ) || exit 1
        shift
      fi
      ;;
    --gitRepositoryEnvName|--gitUsernameEnvName|--gitLfsEnvName|--gitLfsAboveSizeEnvName|--gitPasswordEnvName|--rtcUriEnvName|--rtcUsernameEnvName|--rtcPasswordEnvName|--rtcSourceEnvName|--rtcProjectAreaEnvName|--rtcTeamNameEnvName|--rtcIncludeRootEnvName|--inFileEnvName|--fieldSeparatorEnvName|--commentPrefixEnvName|--testEnvName)
      handleArgAndEnvName "${1}" "${2:-}" || ( doHelp >&2 ; exit 1 ) || exit 1
      shift
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
if [ -z "${arg_rtcUsername}" ];then
  handleArgAndEnvName --rtcUsernameEnvName RTC_USERNAME || ( doHelp >&2 ; exit 1 ) || exit 1
fi
if [ -z "${arg_rtcPassword}" ];then
  handleArgAndEnvName --rtcPasswordEnvName RTC_PASSWORD || ( doHelp >&2 ; exit 1 ) || exit 1
fi
if [ "${arg_inFile}" != "-" ];then
  if ! [ -r "${arg_inFile}" ];then
    echo "Error: inFile argument (${arg_inFile}) is not a file." >&2 ; doHelp >&2 ; exit 1
  fi
  if ! [ -s "${arg_inFile}" ];then
    echo "Error: file '${arg_inFile}' (set by inFile argument) is empty." >&2 ; doHelp >&2 ; exit 1
  fi
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
if ! isTrueOrFalse "${arg_rtcIncludeRoot}"; then
  echo "Error: rtcIncludeRoot argument (${arg_rtcIncludeRoot}) was neither 'true' or 'false'." >&2 ; doHelp >&2 ; exit 1
fi
if [ -z "${arg_fieldSeparator}" ];then
  echo "Error: fieldSeparator argument cannot be empty." >&2 ; doHelp >&2 ; exit 1
fi
if ! isTrueOrFalse "${arg_test}"; then
  echo "Error: test argument (${arg_test}) was neither 'true' or 'false'." >&2 ; doHelp >&2 ; exit 1
fi

# $1 = comment prefix, or empty
filterOutComments() {
  local prefix="${1}"
  if [ -z "${prefix}" ]
  then
    cat # just pass all stdin to stdout
  else
    (
    IFS=''
    local prefixLength=$(expr length "${prefix}")
    local linePrefix
    while read -r line
    do
      linePrefix=$(expr substr "${line}" 1 ${prefixLength})
      if [ "${linePrefix}" != "${prefix}" ]
      then
        echo "${line}"
      fi
    done
    )
  fi
}

# Sanitizes an RTC snapshot name (which has few, if any, naming restrictions)
# to make it suitable for use as a git tag name.
# This is intended to be functionally identical to the GitMigrator method createTagName.
# $@ = RTC name
# stdout = git tag
createTagName() {
  local charNUL=$'\000'
  local charUS=$'\037'
  local charDEL=$'\177'
  local -a sedArgs=()
  # 1. cant begin with a dot .
  sedArgs+=('-e' 's=/\.=/_=g')
  # 1. cant end with the sequence .lock
  sedArgs+=('-e' 's=\.lock/=_lock/=g')
  # 3. cannot have two consecutive dots .. anywhere
  sedArgs+=('-e' 's=\.\.=__=g')
  # 4. cannot have ASCII control characters (i.e. bytes lower than \040 or \177 DEL)
  sedArgs+=('-e' "s=[${charNUL}-${charUS}]=_=g")
  sedArgs+=('-e' "s=${charDEL}=_=g")
  # 4. ... space, tilde ~, caret ^, or colon :
  sedArgs+=('-e' 's=[ ~^:]=_=g')
  # 5. cannot have question-mark ?, asterisk *, or open bracket [
  sedArgs+=('-e' 's=[[*?]=_=g')
  # 6. we turn all / into _ as it's easier
  sedArgs+=('-e' 's=/=_=g')
  # 8. cannot contain a sequence @{
  sedArgs+=('-e' 's=@{=@_=g')
  # 10. cannot contain a \ anywhere
  sedArgs+=('-e' 's=\\=_=g')
  # 1. can't begin with a dot .
  sedArgs+=('-e' 's=^\\.=_=g')
  # 1. can't end with the sequence .lock
  sedArgs+=('-e' 's=\\.lock$=_lock=g')
  # 7. cannot end with a dot .
  sedArgs+=('-e' 's=\\.$=_=g')
  # Note: Points 2 and 9 are satisfied because a git tag is always refs/tags/gitTagName
  # git-lfs has problems with commas due to bugs in its cli, so we avoid commas too
  sedArgs+=('-e' 's=,=_=g')
  echo "$@" | sed "${sedArgs[@]}"
}

# This relies on the following arguments having been set:
#   arg_fieldSeparator
#   arg_gitRepository
#   arg_gitUsername
#   arg_gitPassword
#   arg_rtcUri
#   arg_rtcUsername
#   arg_rtcPassword
#   arg_rtcSource
#   arg_test
processSnapshots() {
  local scriptToDelegateTo="${SCRIPT_DIR}/migrateFromRTCtoGit.sh"
  local line
  local timestamp
  local rtcSnapshotUUID
  local branchChangesetUUID
  local branchGitCommit
  local rtcSnapshotName
  local rtcSnapshotDescription
  local tagDescription
  local tagName
  #YYYY-MM-DDThh:mm	rtcSnapshotUUID........	branchChangesetUUID....	branchGitCommit.........................	"rtcSnapshotName"	"rtcSnapshotDescription"
  while read -r line
  do
    timestamp=$(              expr "${line}" : "^\(.*\)${arg_fieldSeparator}.*${arg_fieldSeparator}.*${arg_fieldSeparator}.*${arg_fieldSeparator}\".*\"${arg_fieldSeparator}\".*\"\$" )
    rtcSnapshotUUID=$(        expr "${line}" : "^.*${arg_fieldSeparator}\(.*\)${arg_fieldSeparator}.*${arg_fieldSeparator}.*${arg_fieldSeparator}\".*\"${arg_fieldSeparator}\".*\"\$" )
    branchChangesetUUID=$(    expr "${line}" : "^.*${arg_fieldSeparator}.*${arg_fieldSeparator}\(.*\)${arg_fieldSeparator}.*${arg_fieldSeparator}\".*\"${arg_fieldSeparator}\".*\"\$" )
    branchGitCommit=$(        expr "${line}" : "^.*${arg_fieldSeparator}.*${arg_fieldSeparator}.*${arg_fieldSeparator}\(.*\)${arg_fieldSeparator}\".*\"${arg_fieldSeparator}\".*\"\$" )
    rtcSnapshotName=$(        expr "${line}" : "^.*${arg_fieldSeparator}.*${arg_fieldSeparator}.*${arg_fieldSeparator}.*${arg_fieldSeparator}\"\(.*\)\"${arg_fieldSeparator}\".*\"\$" )
    # expr returns a failure instead of an empty string if the rtcSnapshotDescription is empty, so we test for that case
    if                        expr "${line}" : "^.*${arg_fieldSeparator}.*${arg_fieldSeparator}.*${arg_fieldSeparator}.*${arg_fieldSeparator}\".*\"${arg_fieldSeparator}\"\"\$" >/dev/null 2>&1
    then
      rtcSnapshotDescription=""
      tagDescription="${arg_rtcUri}resource/itemOid/com.ibm.team.scm.BaselineSet/${rtcSnapshotUUID}"
    else
      rtcSnapshotDescription=$( expr "${line}" : "^.*${arg_fieldSeparator}.*${arg_fieldSeparator}.*${arg_fieldSeparator}.*${arg_fieldSeparator}\".*\"${arg_fieldSeparator}\"\(.*\)\"\$" )
      tagDescription="${rtcSnapshotDescription}

${arg_rtcUri}resource/itemOid/com.ibm.team.scm.BaselineSet/${rtcSnapshotUUID}"
    fi
    # timestamp needs to be YYYY-MM-DDThh:mm:ss but RTC doesn't give us :ss so we add :00
    if [ $(expr length "${timestamp}") = 16 ]
    then
      timestamp="${timestamp}:00"
    fi
    tagName="$(createTagName "${rtcSnapshotName}")"
    local -a scriptArgs=()
    scriptArgs+=(   '--gitRepository'        "${arg_gitRepository}"   )
    if [ -n "${arg_gitLfs}" ]
    then
      scriptArgs+=( '--gitLfs'               "${arg_gitLfs}"          )
    fi
    if [ -n "${arg_gitLfsAboveSize}" ]
    then
      scriptArgs+=( '--gitLfsAboveSize'      "${arg_gitLfsAboveSize}" )
    fi
    scriptArgs+=(   '--rtcUri'               "${arg_rtcUri}"          )
    scriptArgs+=(   '--rtcSource'            "${arg_rtcSource}"       )
    if [ -n "${arg_rtcProjectArea}" ]
    then
      scriptArgs+=( '--rtcProjectArea'       "${arg_rtcProjectArea}"  )
      scriptArgs+=( '--rtcTeamName'          "${arg_rtcTeamName}"     )
    fi
    scriptArgs+=(   '--rtcSourceSnapshot'    "${rtcSnapshotUUID}"     )
    scriptArgs+=(   '--rtcIncludeRoot'       "${arg_rtcIncludeRoot}"  )
    scriptArgs+=(   '--branchAtRtcChangeset' "${branchChangesetUUID}" )
    scriptArgs+=(   '--branchAtGitCommit'    "${branchGitCommit}"     )
    scriptArgs+=(   '--gitTagName'           "${tagName}"             )
    scriptArgs+=(   '--gitTagDescription'    "${tagDescription}"      )
    scriptArgs+=(   '--gitTagTimestamp'      "${timestamp}"           )
    scriptArgs+=(   '--tmpDir'               true                     )
    if [ "${arg_test}" != "true" ]
    then
      echo "Info: Processing '${rtcSnapshotName}'"
      (
        export GIT_USERNAME="${arg_gitUsername}"
        export GIT_PASSWORD="${arg_gitPassword}"
        export RTC_USERNAME="${arg_rtcUsername}"
        export RTC_PASSWORD="${arg_rtcPassword}"
        set -x
        "${scriptToDelegateTo}" "${scriptArgs[@]}"
      )
    else
      (
        echo "# ${rtcSnapshotName}"
        set -x
        true "${scriptArgs[@]}"
      ) 2>&1 | sed "s,^\+ true ,${scriptToDelegateTo} ,"
    fi
  done
}

cat "${arg_inFile}" \
  | filterOutComments "${arg_commentPrefix}" \
  | processSnapshots
