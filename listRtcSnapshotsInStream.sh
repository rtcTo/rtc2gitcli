#!/bin/bash
# Script to list snapshots in RTC.
# This is used to identify what snapshots we might want to migrate to Git, e.g. just the release snapshots.

# Ensure we fail if we reference a variable we've not set
set -u
# Ensure we fail if we execute a command that returns a non-zero exit code
set -e
# Ensure we fail if we execute a command that has a failure in any step of a pipeline
set -o pipefail

SCRIPT_NAME=`basename ${0}`

# Outputs CLI usage help text to stdout.
doHelp() {
  cat <<ENDHELP
Syntax:
  ${SCRIPT_NAME} [--rtcUri ...] [--rtcUsername ...] [--rtcPassword ...] --rtcSource ... [--rtcProjectArea ... --rtcTeamName ...] [--rtcSnapshot ...] [--fieldSeparator ...]
Where
  --rtcUri "rtcBaseUrl" (Optional)
    Specifies the RTC server to be used.
    Defaults to https://jazzc04.hursley.ibm.com:9443/ccm/
  --rtcUsername "username"
  --rtcPassword "password"
    Specifies the credentials to be used when logging into RTC.
    Defaults to using environment variables RTC_USERNAME and RTC_PASSWORD.
  --rtcSource "streamOrWorkspaceName"
    Specifies the stream (or workspace) that holds the snapshots to be listed.
  --rtcProjectArea "projectAreaName" (Optional)
  --rtcTeamName "teamNameOrEmpty" (Optional)
    Specifies the project area and team name where the rtcSource can be found.
    If either is specified, both must be specified; if the source is not in a team, give an empty team name.
    Note: These are only required if the rtcSource name is not unique.
  --rtcSnapshot "snapshotNameRegex" (Optional)
    Specifies a regular expression that must match the whole snapshot name for the name to be listed.
    Defaults to '.*', which will show everything.
  --fieldSeparator "string" (Optional)
    Specifies how to separate the different columns that we output.
    Defaults to a tab.
  --xxxEnvName "environmentVariableName"
    For any/all of the above arguments, specifies that the value for argument 'xxx' is to be read from
    the specified environment variable.
Description:
  Lists snapshots in RTC that we might want to migrate to git.
  Outputs one line per snapshot.
  Each line is of the syntax:
    <UUID><fieldSeparator><creationDate><fieldSeparator><snapshotName><fieldSeparator><snapshotComment>
  where
    <UUID> is the RTC snapshot's (unique) UUID.
    <fieldSeparator> is the value specified to --fieldSeparator.
    <creationDate> is of form YYYY-MM-DDThh:mm (as RTC does not provide seconds).
    <snapshotName> is the name of the snapshot, and will be matched against the --rtcSnapshot regex.
    <snapshotComment> is the name of the snapshot.
  Returns 0 on success (even if nothing is found), non-zero on failure.
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
# exit code 0 on success, 1 if unexpected EOF, 2 if malformed
listMatchingSnapshotsFromJson() {
  local nameRegex="${1}"
  local fieldSeparator="${2}"
  local line
  local hitEof
  if ! read -r line
  then
    echo "ERROR: Unexpectedly empty json snapshot list." >&2
    return 1
  fi
  if [ "${line}" != '{' ]
  then
    (
      echo "ERROR: Unexpected text (${line}) in json snapshot list - was expecting '{' on line 1."
      echo "ERROR: Remaining text follows:"
      cat
      echo "ERROR: End of remaining text."
    ) >&2
    return 2
  fi
  if ! read -r line
  then
    echo "ERROR: Unexpected end of json snapshot list on line 2." >&2
    return 1
  fi
  if [ "${line}" != '"snapshots": [' ]
  then
    (
      echo "ERROR: Unexpected text (${line}) in json snapshot list - was expecting '\"snapshots\": [' on line 2."
      echo "ERROR: Remaining text follows:"
      cat
      echo "ERROR: End of remaining text."
    ) >&2
    return 2
  fi
  while read -r line; do
    if [ "${line}" = '{' ]
    then
      # This is the beginning of a new json block describing a snapshot
      if ! outputSnapshotFromJsonIfMatching "${nameRegex}" "${fieldSeparator}"
      then
        (
          echo "ERROR: Malformed json snapshot details."
          echo "ERROR: Remaining text follows:"
          cat
          echo "ERROR: End of remaining text."
        ) >&2
        return 2
      fi
    elif [ "${line}" = ']' ]
    then
      # This is the end of the  "snapshots": [  section.
      # We expect a } *but* it might EOF immediately after that, causing read to fail.
      line=''
      if read -r line
      then
        hitEof='false'
      else
        hitEof='true'
      fi
      if [ "${line}" != '}' ]
      then
        if [ "${hitEof}" = "true" ]
        then
          echo "ERROR: Unexpected end of json snapshot list - '\"snapshots\": [' ... ']' array should be followed by a '}' but it was ${line}<EOF>."
          return 1
        else
          echo "ERROR: Unexpected text at end of json snapshot list - '\"snapshots\": [' ... ']' array should be followed by a '}' but it was '${line}'."
          echo "ERROR: Remaining text follows:"
          cat
          echo "ERROR: End of remaining text."
          return 2
        fi >&2
      fi
    else
      (
        echo "ERROR: Unexpected text (${line}) in json snapshot list."
        echo "ERROR: Remaining text follows:"
        cat
        echo "ERROR: End of remaining text."
      ) >&2
      return 2
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
  read -r line && [ "${line}" = '"ownedby": {' ] || return 1
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

# Setup variables to hold the parsed arguments.
arg_rtcUri=""
arg_rtcUsername=""
arg_rtcPassword=""
arg_rtcSource=""
arg_rtcProjectArea=""
# leave unset arg_rtcTeamName=""
arg_rtcSnapshot='.*'
arg_fieldSeparator="$(echo -e '\t')"

# Parse CLI arguments
while [ "$#" -gt 0 ]
do
  case "${1}" in
    help|-h|--help)
      doHelp; exit 0
      ;;
    --rtcUri|--rtcUsername|--rtcPassword|--rtcSource|--rtcProjectArea|--rtcTeamName|--rtcSnapshot|--fieldSeparator)
      if [ "${2-}" = "" -a "${2-X}" = "X" ]; then
        handleArgAndValue "${1}" || ( doHelp >&2 ; exit 1 ) || exit 1
      else
        handleArgAndValue "${1}" "${2}" || ( doHelp >&2 ; exit 1 ) || exit 1
        shift
      fi
      ;;
    --rtcUriEnvName|--rtcUsernameEnvName|--rtcPasswordEnvName|--rtcSourceEnvName|--rtcProjectAreaEnvName|--rtcTeamNameEnvName|--rtcSnapshotEnvName|--fieldSeparatorEnvName)
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
if expr '' : "${arg_rtcSnapshot}" >/dev/null; then
  true
else
  if [ "$?" -eq "2" ];then
    echo "Error: rtcSnapshot argument (${arg_rtcSnapshot}) is not a valid regular expression." >&2 ; doHelp >&2 ; exit 1
  fi
fi
if [ -z "${arg_fieldSeparator}" ];then
  echo "Error: fieldSeparator argument cannot be empty." >&2 ; doHelp >&2 ; exit 1
fi


# Pull in our RTC utility functions
# These need various RTC_... variables to be set, which is why we delay until now.
export RTC_EXE_NAME=lscm
. "${Jenkins_Build_Scripts_HOME}/setRtcVariables.sh" >/dev/null || . "${Jenkins_Build_Scripts_HOME}/setRtcVariables.sh" || (echo "Something went wrong in ${Jenkins_Build_Scripts_HOME}/setRtcVariables.sh" >&2 ; exit 1)
# Ensure that any sub-commands we run use the exact same uniqueness, otherwise we can end up using a different config area, different logs etc.
export RTCCMDS_UNIQUE_NAME


# Set up a robust cleanup routine that'll get called even if we crash out with an error.
cleanUpOnExit() {
  killRtcDaemonProcesses || true
  cleanUpRtcTempFiles
}
trap cleanUpOnExit EXIT ERR


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
  runScmExpectingSuccess "list streams" --name "${arg_rtcSource}" --maximum 1000 >&2
  rtcSourceUUID=$(getUniqueRTCUuidFromListOrEmpty "${arg_rtcSource}" "streams")
  rtcSourceLocation=''
else
  if [ -z "${arg_rtcTeamName}" ]
  then
    runScmExpectingSuccess "list streams" --projectarea "${arg_rtcProjectArea}" --name "${arg_rtcSource}" --maximum 1000 >&2
    rtcSourceUUID=$(getUniqueRTCUuidFromListOrEmpty "${arg_rtcSource}" "streams" "${arg_rtcProjectArea}")
    rtcSourceLocation=" in project area '${arg_rtcProjectArea}'"
  else
    # Note: RTC has a bug whereby you're not allowed to specify BOTH a teamName and a ProjectArea
    # ... even if you need to, as RTC can error and complain that multiple ProjectAreas have teams
    # with the same name.
    # So, if you hit this, raise the issue with RTC support, prepare to wait a few years, and use
    # a UUID of the team in the meantime.
    runScmExpectingSuccess "list streams" --teamarea "${arg_rtcTeamName}" --name "${arg_rtcSource}" --maximum 1000 >&2
    rtcSourceUUID=$(getUniqueRTCUuidFromListOrEmpty "${arg_rtcSource}" "streams")
    rtcSourceLocation=" in project area '${arg_rtcProjectArea}' team area '${arg_rtcTeamName}'"
  fi
fi
if [ -z "${rtcSourceUUID}" ]
then
  echo "Error: No stream called '${arg_rtcSource}' was found${rtcSourceLocation}." >&2
  exit 1
fi
#echo "Info: Found RTC stream called '${arg_rtcSource}'${rtcSourceLocation} (${rtcSourceUUID})."

# When listing snapshots, we have to tell it to use json format otherwise it omits important information like the comment/description field :-(
runScmExpectingSuccess "list snapshots" --json --maximum all "${rtcSourceUUID}" >&2
listMatchingSnapshotsFromJson "${arg_rtcSnapshot}" "${arg_fieldSeparator}" < "${RTCCMDS_TEMP_FILE}"

killRtcDaemonProcesses
cleanUpRtcTempFiles
trap - EXIT ERR
