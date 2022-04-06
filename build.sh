#!/bin/bash
#
# Builds the rtc2gitcli extension and prepares it for use.
# - Requires the RTC "scmTools" jar to be provided, either as a .zip or a folder.
#   If a folder is provided, it can either be the base folder or the subfolder
#   that contains the scm executable (base/jazz/scmtools/eclipse).

set -u
set -e
set -o pipefail

# Deletes a folder and its contents, even if Microsoft are involved.
# If we are running in an environment where our working folder is a Microsoft
# Windows filesystem then the filesystem is fundamentally unreliable and thus
# "rm -rf" can fail for no good reason.  So we retry :-(
# (Actually the reason is "file in use" because Windows OSs have a lot of
# processes which lock files "open" - File Explorer, Windows Search and most
# anti-virus software are common culprits and "difficult to remove")
robustlyDelete() {
  for attempt in 1 2 3 4 5
  do
    if rm -rf "${@}"
    then
      return 0
    else
      sleep 1
    fi
  done
  rm -rf "${@}"
}

# Copies the designated official RTC CLI to the specified folder,
# then removes any JVM it contained and reconfigures RTC to use
# whatever version of Java is on the $PATH instead.
# (because RTC ships with a version of Java that's too old)
# $1 = source zip or folder
# $2 = destination folder
extractScmToolsToUseOurJava() {
  local scmToolsDirOrZip="${1}"
  local targetScmDir="${2}"
  robustlyDelete "${targetScmDir}"
  mkdir -p "${targetScmDir}"
  if [ -d "${scmToolsDirOrZip}" ]
  then
    ( cd "${scmToolsDirOrZip}" ; tar -cf - . ) | ( cd "${targetScmDir}" ; tar -xf - )
  else
    if [ -f "${scmToolsDirOrZip}" ]
    then
      unzip -q -K "${scmToolsDirOrZip}" -d "${targetScmDir}"
    else
      echo "[ERROR] SCM tool zip/folder '${scmToolsDirOrZip}' is neither a file or a directory." >&2
      return 1
    fi
  fi
  if [ -d "${targetScmDir}/jazz/jre" ]
  then
    local scriptFile
    robustlyDelete "${targetScmDir}/jazz/jre"
    for scriptFile in "${targetScmDir}/jazz/scmtools/eclipse/lscm" "${targetScmDir}/jazz/scmtools/eclipse/scm.sh"
    do
      sed --in-place \
        -e 's,[^ "]*/jre/bin/java,java,g' \
        -e 's,EXTRA_JAVA_OPTS="-X[^"]*",EXTRA_JAVA_OPTS="",g' \
        "${scriptFile}"
    done
    cp -p "${targetScmDir}/jazz/scmtools/eclipse/scm.ini" "${targetScmDir}/jazz/scmtools/eclipse/scm.ini.orig"
    # Need to remove the -vm line and the following line.
    # Also need to remove any unusual -X... arguments, e.g. -Xshareclasses:nonfatal
    cat "${targetScmDir}/jazz/scmtools/eclipse/scm.ini.orig" \
    | awk '\
BEGIN {
  skipNext="false";
}
/^-vm$/ {
  skipNext="true";
  next;
}
/^-Xms/ {
  print;
  next;
}
/^-Xmx/ {
  print;
  next;
}
/^-X/ {
  next;
}
{
  if (skipNext!="true") {
    print;
  } else {
    skipNext="false";
  }
  next;
}' \
    > "${targetScmDir}/jazz/scmtools/eclipse/scm.ini"
    rm "${targetScmDir}/jazz/scmtools/eclipse/scm.ini.orig"
  fi
}

# The rtc2gitcli code has out of date versions of libs in the manually-written manifest.
# We need to update them so they're correct.
# $1 = original (incorrect) manifest with wrong libs files in it
# $2 = where to put the fixed manifest file
# $3 = folder where the actual libs are
makeFixedManifestFile() {
  [ -f "${1}" ]
  [ -d "${3}" ]
  robustlyDelete "${2}"
  mkdir -p $(dirname "${2}")
  touch "${2}"
  local line
  local IFS=''
  while read -r line
  do
    local filenameFound=$(expr "${line}" : '.*lib/\([a-zA-Z].*-[0-9].*\.jar\).*$')
    if [ -n "${filenameFound}" ]
    then
      local filenamePattern=$(expr "${filenameFound}" : '\([a-zA-Z].*-\)[0-9].*\.jar')'*.jar'
      local correctedFilename=$(cd "${3}" ; echo ${filenamePattern})
      local textBeforeFilenameFound=$(expr "${line}" : '\(.*lib/\)[a-zA-Z].*-[0-9].*\.jar.*$')
      local textAfterFilenameFound=$(expr "${line}" : '.*lib/[a-zA-Z].*-[0-9].*\.jar\(.*\)$')
      echo "${textBeforeFilenameFound}${correctedFilename}${textAfterFilenameFound}"
    else
      echo "${line}"
    fi
  done >"${2}" <"${1}"
}

# Gets maven to do the build, then assembles the RTC plugin
# This involves quite a lot of moving & renaming for some reason.
cleanBuildRtc2GitCliPlugin() {
  local builtJarName="cli.extension-1.0.1.jar" # what's built
  local pluginJarName="to.rtc.cli.migrate_1.0.1.jar" # what we need to output
  local buildJarNameInsidePlugin="to.rtc.cli.migration.jar" # what what's built is called inside the output :-/
  robustlyDelete target lib bin bin_test
  # We set -DskipTests=true as the code's unit-tests always fail.
  # We set -B because otherwise the console is filled with download progress text during CI builds.
  # We ask for "package" to be built because that creates the jars we need.
  mvn -B package -DskipTests=true
  mkdir -p target target/plugins target/pluginjar/lib
  makeFixedManifestFile META-INF/MANIFEST.MF target/pluginjar.MANIFEST.MF lib
  ( cd lib && tar -cf - . --exclude='rtcScmTools.jar' ) | ( cd "target/pluginjar/lib" && tar -xf - )
  cp -p plugin.xml target/pluginjar/plugin.xml
  cp -p "target/${builtJarName}" "target/pluginjar/${buildJarNameInsidePlugin}"
  ( cd target/pluginjar && jar -cfm "../plugins/${pluginJarName}" ../pluginjar.MANIFEST.MF . )
}

# Checks that the migration plugin has successfully been "installed" into the SCM tool.
# If we've got library/classpath issues then it'll fail.
# Note: this doesn't guarantee it'll work perfectly, just that it's able to start.
# $1 = (patched) RTC scm executable to test
verifyScmToolsHasMigrateToGitOption() {
  "${1}" --help | \
  if grep 'migrate-to-git' >/dev/null
  then
    echo "[INFO] RTC CLI '${1}' contains migrate-to-git plugin."
  else
    echo "[ERROR] RTC CLI '${1}' does not contain migrate-to-git plugin." >&2
    return 1
  fi
  if "${1}" help migrate-to-git
  then
    echo "[INFO] RTC CLI '${1}' migrate-to-git plugin is available for use."
  else
    echo "[ERROR] RTC CLI '${1}' migrate-to-git plugin does not work." >&2
    return 1
  fi
}

mainMethod() {
  local scmToolsArg="${1}"
  if expr "${scmToolsArg}" : '.*//*jazz//*scmtools//*eclipse/*$' >/dev/null 2>&1
  then
    echo "[INFO] Given SCM tools location ${scmToolsArg} ends in 'jazz/scmtools/eclipse' - assuming this is the location of the scm executable not the base folder."
    local scmToolsLocation=$( expr "${scmToolsArg}" : '\(.*\)//*jazz//*scmtools//*eclipse/*$' )
  else
    local scmToolsLocation="${scmToolsArg}"
  fi
  echo "[INFO] Copying '${scmToolsLocation}' to rtcScmTools/"
  extractScmToolsToUseOurJava "${scmToolsLocation}" rtcScmTools
  # TODO: Find some way of putting the manifest jar in as a maven artefact locally instead of system-scope.
  # as this method of hacking the pom.xml is nasty.
  if ! ./mkmanifestjar.sh rtcScmTools/rtcScmTools.jar jazz/scmtools/eclipse/plugins/
  then
    (
    echo "[ERROR] Failed to identify RTC CLI jar files.  Was expecting rtcScmTools/jazz/scmtools/eclipse/plugins/ to contain jars etc but it's actually:"
    ls -ld rtcScmTools rtcScmTools/jazz rtcScmTools/jazz/scmtools rtcScmTools/jazz/scmtools/eclipse rtcScmTools/jazz/scmtools/eclipse/plugins rtcScmTools/jazz/scmtools/eclipse/plugins/* 2>&1 || true
    ) >&2
    return 1
  fi
  echo "[INFO] Building rtc2git.cli.extension"
  (
    cd rtc2git.cli.extension && cleanBuildRtc2GitCliPlugin
  )
  echo "[INFO] Installing rtc2git plugin into RTC cli"
  cp -p rtc2git.cli.extension/target/plugins/* rtcScmTools/jazz/scmtools/eclipse/plugins/
#  robustlyDelete rtcScmTools/rtcScmTools.jar
  echo "[INFO] Testing RTC cli"
  verifyScmToolsHasMigrateToGitOption rtcScmTools/jazz/scmtools/eclipse/scm.sh
  echo "[INFO] SUCCESS."
}

mainMethod "$@"
