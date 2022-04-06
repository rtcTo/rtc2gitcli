#!/bin/bash
# Script to rename and/or re-comment an RTC snapshot.

# Ensure we fail if we reference a variable we've not set
set -u
# Ensure we fail if we execute a command that returns a non-zero exit code
set -e
# Ensure we fail if we execute a command that has a failure in any step of a pipeline
set -o pipefail

SCRIPT_NAME=`basename ${0}`
SCRIPT_DIR=`dirname ${0}`

# Outputs CLI usage help text to stdout.
doHelp() {
  cat <<ENDHELP
Syntax:
  ${SCRIPT_NAME} [--rtcUri ...] [--rtcUsername ...] [--rtcPassword ...] --rtcSnapshotId ... [--nameSedScript ...] [--commentSedScript ...] [--test ...]
Where
  --rtcUri "rtcBaseUrl" (Optional)
    Specifies the RTC server to be used.
    Defaults to https://jazzc04.hursley.ibm.com:9443/ccm/
  --rtcUsername "username"
  --rtcPassword "password"
    Specifies the credentials to be used when logging into RTC.
    Defaults to using environment variables RTC_USERNAME and RTC_PASSWORD.
  --rtcSnapshotId "uuid"
    Specifies the RTC snapshot UUID of the snapshot to be changed.
  --nameSedScript "string" (Optional)
    Specifies a transform to perform on the snapshot name, using 'sed' syntax.
    e.g. "s,Foo,Bar,g" to replace all occurances of "Foo" in the name with "Bar".
  --commentSedScript "string" (Optional)
    Specifies a transform to perform on the snapshot comment, using 'sed' syntax.
    e.g. "s,Foo,Bar,g" to replace all occurances of "Foo" in the comment with "Bar".
  --test true or false (Optional)
    If true, we only echo out what we would have run instead of running it as well.
  --xxxEnvName "environmentVariableName"
    For any/all of the above arguments, specifies that the value for argument 'xxx' is to be read from
    the specified environment variable.
Description:
  Reads the details of an RTC snapshot, performs a transform on the snapshot name and/or comment, and updates the snapshot name and/or comment.
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
arg_rtcUri=""
arg_rtcUsername=""
arg_rtcPassword=""
arg_rtcSnapshotId=""
arg_nameSedScript=""
arg_commentSedScript=""
arg_test="false"

# Parse CLI arguments
while [ "$#" -gt 0 ]
do
  case "${1}" in
    help|-h|--help)
      doHelp; exit 0
      ;;
    --rtcUri|--rtcUsername|--rtcPassword|--rtcSnapshotId|--nameSedScript|--commentSedScript|--test)
      if [ "${2-}" = "" -a "${2-X}" = "X" ]; then
        handleArgAndValue "${1}" || ( doHelp >&2 ; exit 1 ) || exit 1
      else
        handleArgAndValue "${1}" "${2}" || ( doHelp >&2 ; exit 1 ) || exit 1
        shift
      fi
      ;;
    --rtcUriEnvName|--rtcUsernameEnvName|--rtcPasswordEnvName|--rtcSnapshotIdEnvName|--nameSedScriptEnvName|--commentSedScriptEnvName|--testEnvName)
      handleArgAndEnvName "${1}" "${2:-}" || ( doHelp >&2 ; exit 1 ) || exit 1
      shift
      ;;
    *)
      (echo "Unrecognised argument '${1}'"; doHelp) >&2; exit 1
      ;;
  esac
  shift
done

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
if [ -z "${arg_rtcSnapshotId}" ];then
  echo "Error: rtcSnapshotId argument was not provided." >&2 ; doHelp >&2 ; exit 1
fi
if [ -z "${arg_nameSedScript}" -a -z "${arg_commentSedScript}" ];then
  echo "Error: neither nameSedScript argument or commentSedScript were provided." >&2 ; doHelp >&2 ; exit 1
fi
if ! isTrueOrFalse "${arg_test}"; then
  echo "Error: test argument (${arg_test}) was neither 'true' or 'false'." >&2 ; doHelp >&2 ; exit 1
fi


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

# stdin = something of the form:
# Name: (_3TGKBeobEeuJpcf0dcuE1Q) "Migrated ANA_Localisation to https://github.com/i2group-archive/ANA_Localisation.git"
# Description:
# Migrated to https://github.com/i2group-archive/ANA_Localisation.git commit ebd95bfffcdd7d97ef019b540e2fcbb8ec82057b
parseScmGetAttributesForName() {
  local ignoredName
  local ignoredId
  local quotedName
  read -r ignoredName ignoredId quotedName
  expr "${quotedName}" : '^"\(.*\)"$'
}
parseScmGetAttributesForDescription() {
  local ignoredLine
  read -r ignoredLine
  read -r ignoredLine
  cat
}

# Pull in our RTC utility functions
# These need various RTC_... variables to be set, which is why we delay until now.
. "${SCRIPT_DIR}/setRtcVariables.sh" >/dev/null || . "${SCRIPT_DIR}/setRtcVariables.sh" || (echo "Something went wrong in ${SCRIPT_DIR}/setRtcVariables.sh" >&2 ; exit 1)
# Ensure that any sub-commands we run use the exact same uniqueness, otherwise we can end up using a different config area, different logs etc.
export RTCCMDS_UNIQUE_NAME

# Set up a robust cleanup routine that'll get called even if we crash out with an error.
cleanUpOnExit() {
  killRtcDaemonProcesses || true
  cleanUpRtcTempFiles
}
trap cleanUpOnExit EXIT ERR

runScmExpectingSuccess "get attributes" --snapshot "${arg_rtcSnapshotId}" --name --description

originalName=$(parseScmGetAttributesForName < "${RTCCMDS_TEMP_FILE}")
if [ -n "${arg_nameSedScript}" ]
then
  echo "originalName:"
  echo "${originalName}"
  replacementName=$(echo "${originalName}" | sed -e "${arg_nameSedScript}")
  echo "replacementName:"
  echo "${replacementName}"
else
  replacementName="${originalName}"
fi
originalDescription=$(parseScmGetAttributesForDescription < "${RTCCMDS_TEMP_FILE}")
if [ -n "${arg_commentSedScript}" ]
then
  echo "originalDescription:"
  echo "${originalDescription}"
  replacementDescription=$(echo "${originalDescription}" | sed -e "${arg_commentSedScript}")
  echo "replacementDescription:"
  echo "${replacementDescription}"
else
  replacementDescription="${originalDescription}"
fi

if [ "${replacementName}" != "${originalName}" ]
then
  if [ "${replacementDescription}" != "${originalDescription}" ]
  then
    # Both name and description need to change
    runScmExpectingSuccess "set attributes" --snapshot "${arg_rtcSnapshotId}" --name "${replacementName}" --description "${replacementDescription}"
  else
    # Just the name needs to change
    runScmExpectingSuccess "set attributes" --snapshot "${arg_rtcSnapshotId}" --name "${replacementName}"
  fi
else
  if [ "${replacementDescription}" != "${originalDescription}" ]
  then
    # Just the description needs to change
    runScmExpectingSuccess "set attributes" --snapshot "${arg_rtcSnapshotId}" --description "${replacementDescription}"
  else
    # nothing needs to change
    echo "No changes made."
  fi
fi
