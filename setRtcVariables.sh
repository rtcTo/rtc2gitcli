# Reads the current environment, and works out where the RTC "scm" tool should be.
# Assumes that SCRIPT_NAME has been set to the name of the script that the user called originally.
#
# Sets the following environment variables
#   RTCSCMEXE = absolute path to the scm executable
#   RTCVERSION = 5 or 6 (at present).
#   RTCCMDS_UNIQUE_NAME = a short name that's guaranteed to be unique for this build, but not so unique it doesn't get reused.
#   RTCCMDS_TEMP_CONFIGDIR = a folder that can be used by any RTC commands as a configuration folder.
#   RTCCMDS_TEMP_FILE = a file that can be used by any RTC commands to capture output.
# ensures that the following can be relied on
#   RTC_USERNAME = username that we should use to log into RTC.
#   RTC_PASSWORD = password that we should use to log into RTC.
#   RTC_URL = URL of the RTC instance
# requires that the following are set
#   RTC_TOOLKIT = name of the "Custom tool" containing the RTC cli, e.g. RTC_5.0.2
#   ${RTC_TOOLKIT}_SCM_HOME = location of the RTC cli
# and can be influenced by the following
#   RTC_EXE_NAME = name of the RTC cli executable, e.g. "scm" (the default) or "scm.sh"
#   RTC_SCM_USE_NATIVE_JAVA = If using "scm" executable, you can set this to true to force the use of the OS-installed Java
#   RTC_SCM_RETRIES = number of times we'll retry a scm command if it failed.  Defaults to 9 retries, 10 attempts total.
#
# Note: On Linux, if you're using "scm" then you'll probably have to use the
# version of Java that it's configured to use, as it doesn't play nicely with
# non-IBM Javas.
# However, IBM Java doesn't always work, so then you may need to use "scm.sh"
# instead, and that uses the OS-installed Java (which hopefully will work).
#
# Earlier versions of this script also ensured that RTC_PROJECT_AREA was set, but this was later
# determined to be problematic as, if something is in a team in a project area, RTC then fails to
# find it in the project area.
# It's like putting something in a box in a room - RTC will deny it's in the room, even though it
# knows it's in the box that's in the room.  So don't limit by RTC_PROJECT_AREA or things fail.


function cleanUpRtcTempFiles() {
  if [ ! -z "${RTCCMDS_TEMP_CONFIGDIR:-}" ]; then
    rm -rf "${RTCCMDS_TEMP_CONFIGDIR}"
  fi
  if [ ! -z "${RTCCMDS_TEMP_FILE:-}" ]; then
    rm -rf "${RTCCMDS_TEMP_FILE}"
  fi
}

# Bash has restrictions on what it permits as variable names.
# e.g. it doesn't allow periods.
# Unfortunately, we opted to use periods in the environment variable
# names we pass in, so that makes it difficult to extract.
# This function returns the value of the specified environment variable,
# regardless of how nasty the variable name is from bash's point of view,
# although we can't cope with = characters.
# $1 = environment variable name
# stdout = environment variable value
function getEnvironmentVariableValue() {
  local readonly desiredEnvVarName="$1"
  local IFS="="
  local envVarName envVarValue
  env | \
  while read envVarName envVarValue; do
    if [ "${envVarName}" = "${desiredEnvVarName}" ]; then
      echo "${envVarValue}"
    fi
  done
}
function setRtcVariables() {
  : ${RTC_USERNAME:?"${SCRIPT_NAME}: Error: Environment variable RTC_USERNAME is not set - need to know that in order to know how to log into RTC."}
  : ${RTC_PASSWORD:?"${SCRIPT_NAME}: Error: Environment variable RTC_PASSWORD is not set - need to know that in order to know how to log into RTC."}
  : ${RTC_URL:?"${SCRIPT_NAME}: Error: Environment variable RTC_URL is not set - need to know that in order to know which RTC instance to talk to."}
  : ${RTC_TOOLKIT:?"${SCRIPT_NAME}: Error: Environment variable RTC_TOOLKIT is not set - need to know that in order to know which SCM command line to use."}
  if [ -z "${RTCCMDS_UNIQUE_NAME:-}" ]; then
    if [ -z "${NODE_NAME:-}" -a -z "${EXECUTOR_NUMBER:-}" ]; then
      local DATESTAMP_NANOSECONDS="$(date '+%Y%m%d.%H%M%S%N')"
      RTCCMDS_UNIQUE_NAME="$(hostname).${DATESTAMP_NANOSECONDS:0:17}"
    else
      RTCCMDS_UNIQUE_NAME="${NODE_NAME}.${EXECUTOR_NUMBER}"
    fi
  fi
  RTCCMDS_TEMP_CONFIGDIR="/tmp/${SCRIPT_NAME}.${RTCCMDS_UNIQUE_NAME}.config"
  RTCCMDS_TEMP_FILE="/tmp/${SCRIPT_NAME}.${RTCCMDS_UNIQUE_NAME}.tmp"
  cleanUpRtcTempFiles
  if [ -e "${RTCCMDS_TEMP_CONFIGDIR}" ]; then
    echo "${SCRIPT_NAME}: Error: Unable to delete '${RTCCMDS_TEMP_CONFIGDIR}'"
    return 1
  fi
  if [ -e "${RTCCMDS_TEMP_FILE}" ]; then
    echo "${SCRIPT_NAME}: Error: Unable to delete '${RTCCMDS_TEMP_FILE}'"
    return 1
  fi
  local SCM_HOME_VARIABLE_NAME="${RTC_TOOLKIT}_SCM_HOME"
  local SCM_HOME="$(getEnvironmentVariableValue ${SCM_HOME_VARIABLE_NAME})"
  if [ -z "${SCM_HOME}" ]; then
    echo "${SCRIPT_NAME}: Error: Environment variable RTC_TOOLKIT=${RTC_TOOLKIT} but environment variable ${SCM_HOME_VARIABLE_NAME} is not set - need to know that in order to know where SCM command line for ${RTC_TOOLKIT} can be found.  This probably means that the Jenkins build wasn't configured with the right tool requirements."
    return 1
  fi
  if [ -d "${SCM_HOME}" ]; then
    if [ -z "${RTC_EXE_NAME:-}" ]; then
      RTCSCMEXE="${SCM_HOME}/scm"
    else
      # We have concerns that the RTC "scm" executable might not actually
      # work properly on all flavours of Linux, so we provide the means to
      # specify a different entry point by setting environment variable
      # RTC_EXE_NAME to "fec", "lscm", "lscm2", "scm" or "scm.sh".
      RTCSCMEXE="${SCM_HOME}/${RTC_EXE_NAME}"
      if [ "${RTC_EXE_NAME}" = "scm.sh" ]; then
        # The "scm.sh" command (incorrectly) assumes that JAVA_HOME will be set
        # (normally, JAVA_HOME is not set and java is simply on the path) and
        # then fails when "$JAVA_HOME/bin/java" doesn't exist.
        # So, if JAVA_HOME isn't set then we need to reverse-engineer what to
        # set it to in order to stop "scm.sh" from failing.
        if [ -z "${JAVA_HOME:-}" ]; then
          local WHICH_JAVA=$(which java)
          local DIRECTORY_HOLDING_JAVA=$(dirname "${WHICH_JAVA}")
          export JAVA_HOME=$(dirname "${DIRECTORY_HOLDING_JAVA}")
          echo "WARNING: Using scm.sh but JAVA_HOME was not set, so setting JAVA_HOME=${JAVA_HOME} in the hope that scm.sh will then find ${WHICH_JAVA}."
        fi
      fi
    fi
  else
    RTCSCMEXE="${SCM_HOME}"
  fi
  if [ ! -x "${RTCSCMEXE}" ]; then
    echo "ERROR: ${RTCSCMEXE} is not an executable." >&2
    return 1
  fi
  local SCM_DIR=$(dirname "${RTCSCMEXE}")
  if [ "${RTCSCMEXE}" = "${SCM_DIR}/scm" ]; then
    # We are using the "scm" executable, so we need to ensure that scm.ini is
    # correctly configured.
    # We have found that the IBM JVM that we "bundle" with the tools doesn't
    # necessarily work on all linux environments in which we need it to, so
    # we've got the ability to use the "native" Java if environment variable
    # RTC_SCM_USE_NATIVE_JAVA is set to "true".
    local SCM_INI_TEMPDIR="${RTCCMDS_TEMP_CONFIGDIR}/tmpscmini"
    rm -rf "${SCM_INI_TEMPDIR}"
    mkdir -p "${SCM_INI_TEMPDIR}" || return 1
    if [ "${RTC_SCM_USE_NATIVE_JAVA:-false}" != "true" ]; then
      # Must ensure that we're using the bundled Java not the native installed Java
      # Remove comments from the lines that comment-out the setting of the JVM
      # i.e. "#-vm", "#../.. ... /jre/bin" must be changed to "-vm", "../.. ... /jre/bin"
      local SCM_JVM_DESCRIPTION="bundled IBM JVM"
      sed -e 's,^#\(-vm\)$,\1,' -e 's,^#\(.*jre.*/bin\)$,\1,' "${SCM_DIR}/scm.ini" > "${SCM_INI_TEMPDIR}/scm.ini" || return 1
    else
      # Must ensure that we're not using the bundled Java
      # Comment out the lines that set the JVM to use.
      # i.e. "-vm", "../.. ... /jre/bin" must be changed to "#-vm", "#../.. ... /jre/bin"
      local SCM_JVM_DESCRIPTION="native installed JVM"
      sed -e 's,^\(-vm\)$,#\1,' -e 's,^\(.*jre.*/bin\)$,#\1,' "${SCM_DIR}/scm.ini" > "${SCM_INI_TEMPDIR}/scm.ini" || return 1
    fi
    if ! cmp -s "${SCM_DIR}/scm.ini" "${SCM_INI_TEMPDIR}/scm.ini"; then
      chmod "--reference=${SCM_DIR}/scm.ini" "${SCM_INI_TEMPDIR}/scm.ini" && \
      chmod +w "${SCM_DIR}/scm.ini" && \
      truncate -s 0 "${SCM_DIR}/scm.ini" && \
      cat "${SCM_INI_TEMPDIR}/scm.ini" >> "${SCM_DIR}/scm.ini" && \
      chmod "--reference=${SCM_INI_TEMPDIR}/scm.ini" "${SCM_DIR}/scm.ini" && \
      echo "WARNING: Have switched ${SCM_DIR}/scm to using ${SCM_JVM_DESCRIPTION}" || return 1
    fi
    rm -rf "${SCM_INI_TEMPDIR}" || return 1
  else
    local NEWNAME=`basename ${RTCSCMEXE}`
    if [ "${NEWNAME}" != "scm" ]; then
      echo "WARNING: Using scm executable entry point \"${NEWNAME}\" instead of \"scm\"."
    fi
  fi
  RTCVERSION=$(expr "${RTC_TOOLKIT}" : 'RTC_\([0-9]\)')
  return 0
}

# Finds names of RTC logfiles mentioned in its command-line output.
# RTC has an irritating habit of logging stuff "elsewhere" and then telling a human
# to go look at those files, instead of simply looking at the logfiles in the logs
# folder.
# So we need to pretend to be a human and try to guess where RTC stored the logfile
# this time.
# $@ = zero or more files containing the scm command's output.
# stdin = the scm command's output, if $@ is empty.
# stdout = zero or more filenames.
function guessWhereRtcLoggedTo() {
  cat "$@" | tr ' ' '\n' | grep '\.log' | sort -u | while read potentialFilename
  do
    if [ -f "${potentialFilename}" ]; then
      echo "${potentialFilename}"
    else
      # RTC states filenames in its output BUT, just to make it more difficult,
      # it can add a full stop to the end so we need to also try without that.
      local withoutFullStop=$(expr "${potentialFilename}" : '\(.*\)\.$')
      if [ -f "${withoutFullStop}" ]; then
        echo "${withoutFullStop}"
      fi
    fi
  done
}
# Finds names of RTC logfiles, both those it mentions in its output
# and those it litters inside its config folder.
# stdout = zero or more filenames, one per line, no duplicates.
function findAllRtcLogFiles() {
  (
    if [ -f "${RTCCMDS_TEMP_FILE}" ]
    then
      guessWhereRtcLoggedTo "${RTCCMDS_TEMP_FILE}"
    fi
    find "${RTCCMDS_TEMP_CONFIGDIR}/" -name '*.log' -print 2>/dev/null
  ) | sort -u | grep -v '^$'
}

# Runs an SCM command
# Retries a number of times because we want our builds to be much more reliable than RTC is.
# RTC can be spectacularly shit so we need to retry this command a lot - 4 wasn't enough!
# Returns success (exit code zero) if SCM succeeded, returns failure (exit code 1) if it failed every time.
# Each time SCM fails, it outputs SCM's own output.
# If SCM fails every time then it outputs all of SCM's logfiles as well before returning an error.
# $1 can optionally be "--verbose" to ensure that all the command's output is logged to stdout immediately as well as to ${RTCCMDS_TEMP_FILE}.
# If this is not given then the scm output is output to ${RTCCMDS_TEMP_FILE} and not to stdout.
# $1 or $2 = scm command, e.g. "show workspaces"
# $2 onwards = arguments for that command
function runScmExpectingSuccess() {
  local IFS=" "
  if [ "$1" = "--verbose" ]; then
    local readonly verboseFlag="true"
    shift
  else
    local readonly verboseFlag="false"
  fi
  local readonly scmCommand="$1"
  shift
  case "${scmCommand}"
  in
    # Some commands cannot be told where the RTC instance is...
    "checkin"|"show lastmod"|"show sandbox-structure"|"show status"|"show status")
      local readonly optionalURLArg=""
      ;;
    # ...but everything else needs to know.
    *)
      local readonly optionalURLArg="-r ${RTC_URL}"
      ;;
  esac
  local runScmExpectingSuccessTryLimit="${RTC_SCM_RETRIES:-9}"
  local runScmExpectingSuccessTryCount=0
  while true; do
    runScmExpectingSuccessTryCount=`expr 1 + ${runScmExpectingSuccessTryCount}`
    if [ "${runScmExpectingSuccessTryCount}" -eq "1" ]; then
      echo -n "Calling scm ${scmCommand} $@ ..."
    else
      echo -n "Retrying scm ${scmCommand} $@ ..."
    fi
    clearAllRtcLogs
    if [ "${verboseFlag}" = "true" ]; then
      # We want to allow all output from the scm command to reach stdout immediately
      # as well as capturing it in file ${RTCCMDS_TEMP_FILE} for later examination.
      # Ensure pipefail option is set and restore it afterwards
      local restorePipeFailOptionCmd="$(set +o | grep pipefail)"
      set -o pipefail
      if "${RTCSCMEXE}" --config "${RTCCMDS_TEMP_CONFIGDIR}" --non-interactive --show-alias no --show-uuid yes ${scmCommand} ${optionalURLArg} -u "${RTC_USERNAME}" -P "${RTC_PASSWORD}" "$@" 2>&1 | tr -d '\r' | tee "${RTCCMDS_TEMP_FILE}" ; then
        local ERRORLEVEL=$?
      else
        local ERRORLEVEL=$?
      fi
      eval "${restorePipeFailOptionCmd}"
    else
      # We capture the scm command's stdout and store in ${RTCCMDS_TEMP_FILE} for later.
      # It won't be shown on stdout unless the caller decides to show it.
      if "${RTCSCMEXE}" --config "${RTCCMDS_TEMP_CONFIGDIR}" --non-interactive --show-alias no --show-uuid yes ${scmCommand} ${optionalURLArg} -u "${RTC_USERNAME}" -P "${RTC_PASSWORD}" "$@" 2>&1 | tr -d '\r' >"${RTCCMDS_TEMP_FILE}"; then
        local ERRORLEVEL=$?
      else
        local ERRORLEVEL=$?
      fi
    fi
    if [ "${ERRORLEVEL}" -eq 0 ]; then
      echo "... Done."
      return 0
    fi
    if [ "${runScmExpectingSuccessTryCount}" -lt "${runScmExpectingSuccessTryLimit}" ]; then
      echo "... FAILED on attempt ${runScmExpectingSuccessTryCount} of ${runScmExpectingSuccessTryLimit}."
      echo "Warning: \"${RTCSCMEXE}\" FAILED, exit code ${ERRORLEVEL} on attempt ${runScmExpectingSuccessTryCount} of ${runScmExpectingSuccessTryLimit}." >&2
    else
      echo "... FAILED!"
      echo "" >&2
      echo "Error: \"${RTCSCMEXE}\" FAILED, exit code ${ERRORLEVEL}" >&2
    fi
    echo "${RTCSCMEXE}" --config "${RTCCMDS_TEMP_CONFIGDIR}" --non-interactive --show-alias no --show-uuid yes ${scmCommand} ${optionalURLArg} -u "${RTC_USERNAME}" -P '******' "$@" >&2
    cat "${RTCCMDS_TEMP_FILE}" | ( sed "s|-P ${RTC_PASSWORD}|-P ******|g" 2>/dev/null || cat ) >&2
    if [ "${runScmExpectingSuccessTryCount}" -le "${runScmExpectingSuccessTryLimit}" ]; then
      sleep "${runScmExpectingSuccessTryCount}"
      continue
    fi
    echo "" >&2
    outputAllRtcLogs >&2
    echo ""
    echo "${SCRIPT_NAME}: Error: scm command FAILED, exit code ${ERRORLEVEL}"
    return 1
  done
}
# RTC puts logfiles all over the place.  This method tries to output them to stdout.
function outputAllRtcLogs() {
  local logFileNames=$( findAllRtcLogFiles )
  if [ ! -z "${logFileNames}" ]; then
    echo "Logfiles found:"
    echo "${logFileNames}" | ( while read logFile; do
      (
      echo "Logfile \"${logFile}\""
      cat "${logFile}" | ( sed "s|-P ${RTC_PASSWORD}|-P ******|g" 2>/dev/null || cat )
      )
    done )
  fi
}
# Tries to wipe all logs that might be output by outputAllRtcLogs() later.
# If this is not called then any subsequent call to outputAllRtcLogs() may include logging from earlier commands.
function clearAllRtcLogs() {
  local logFileNames=$( findAllRtcLogFiles )
  if [ ! -z "${logFileNames}" ]; then
    echo "${logFileNames}" | ( while read logFile; do
      rm -f "${logFile}"
    done )
  fi
}


# $1 = scm command, e.g. "show workspaces"
# $2 onwards = arguments for that command
function runScm() {
  local IFS=" "
  local scmCommand=$1
  shift
  echo -n "Calling scm ${scmCommand} $@ ..."
  if "${RTCSCMEXE}" --config "${RTCCMDS_TEMP_CONFIGDIR}" --non-interactive --show-alias no --show-uuid yes ${scmCommand} -r "${RTC_URL}" -u "${RTC_USERNAME}" -P "${RTC_PASSWORD}" "$@" >"${RTCCMDS_TEMP_FILE}" 2>&1; then
    local ERRORLEVEL=$?
  else
    local ERRORLEVEL=$?
  fi
  echo "... Done: exit code ${ERRORLEVEL}"
  return 0
}

setRtcVariables
