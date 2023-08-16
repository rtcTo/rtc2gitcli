#!/bin/bash
# Script to migrate from RTC to Git.
# Will push the latest RTC changes to Git, and can be invoked again later if more changes have happened.
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


# Outputs CLI usage help text to stdout.
doHelp() {
  cat <<ENDHELP
Syntax:
  ${SCRIPT_NAME} --gitRepository ... [--gitUsername ...] [--gitPassword ...] [--gitBranchName ...] [--gitTagName ...] [--gitTagDescription ...] [--gitTagTimestamp ...] [--gitLfs ...] [--gitLfsAboveSize ...] [--rtcUri ...] [--rtcUsername ...] [--rtcPassword ...] --rtcSource ... [--rtcProjectArea ... --rtcTeamName ...] [--rtcSourceSnapshot ...] [--branchAtRtcChangeset ...] [--branchAtGitCommit ...] [--rtcMarkerSnapshot ...] [--rtcMarkerSnapshotEnabled ...] [--rtcIncludeRoot ...] [--tmpDir ...] [--maxBatchSize ...]
Where
  --gitRepository "URL"
    Specifies the git repository that the RTC changes will be pushed to.
    If this already exists then its history will be appended to.
  --gitUsername "username"
  --gitPassword "password"
    Specifies the credentials to be used when logging into git to clone and push.
    Note: For security reasons, it is better to use the --gitPasswordEnvName form (see below).
    Defaults to using environment variables GIT_USERNAME and GIT_PASSWORD.
  --gitBranchName "branchname" (Optional)
    Specifies which branch the RTC changes will be pushed to.  This will be created if it does not exist.
    Defaults to match the remote repo main branch, probably "master".
  --gitTagName "tag" (Optional)
  --gitTagDescription "tag description" (Optional)
  --gitTagTimestamp "YYYY-MM-DDThh:mm:ss" (Optional)
    Specifies the name, description and timestamp (ISO8601 format, although the 'T' can be space if you wish)
    of a tag to be placed at the end of the migrated history marking the end of the migration.
    If this tag already exists then it will be updated.
    The description and timestamp are only used if the name is set.
    Description will default to a machine-generated description if left unset.
    Timestamp will default to "the time of the migration" if left unset.
  --gitLfs "fileOrPattern,fileOrPattern..." (Optional)
    Specifies filenames and/or file patterns for git paths to be migrated into git-lfs before any git-push.
    The value given will be passed to "git lfs migrate import" as a "--include" argument,
    so file names and/or patterns must be comma-separated.
    Defaults to nothing.
  --gitLfsAboveSize "sizeAndUnits" (Optional)
    Specifies a size threshold above which files will be migrated into git-lfs before any git-push.
    The value is the same syntax as if passed to "git lfs migrate import" as a "--above" argument,
    although an alternative implementation (which works) is used instead of --above.
    Units understood are e.g. b, k, kb, m, mb, g, gb, t, tb.
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
  --rtcSourceSnapshot "snapshotname" (Optional)
    Specifies the name of the snapshot in the rtcSource to be used as the endpoint of the migration.
    If this is specified then this must exist.
    By default, migration will run until all changesets have been processed.
  --branchAtRtcChangeset "rtc changeset uuid"
  --branchAtGitCommit "git commit id"
    Specifies that the migration (towards rtcSource/rtcSourceSnapshot) should be started at the specified
    RTC changeset rather than at the last marker-snapshot AND specifies the equivalent git commit.
    This MUST be accompanied by a gitTagName or gitBranchName argument.
  --rtcMarkerSnapshot "snapshotname" (Optional)
    Ignored if branchAtRtcChangeset/branchAtGitCommit are set.
    Specifies the name of the snapshot to be used as the starting point of the migration (if it exists),
    and which will be created in the source stream/workspace at the end of the migration to mark
    where we have got to.
    Defaults to "Migrated <rtcSource> to <gitRepository>".
  --rtcMarkerSnapshotEnabled true|false
    Ignored if branchAtRtcChangeset/branchAtGitCommit are set.
    If false then no rtcMarkerSnapshot will be looked for or created.
    Defaults to true unless branchAtRtcChangeset/branchAtGitCommit are set; if they're set then it's false.
  --rtcIncludeRoot true|false
    Affects how RTC components in the RTC stream are mapped into the git repository.
    If false (the default), all RTC components are loaded into the same folder.
    This allows for different components to provide files "side by side".
    However, some streams contain components that contain files with the same path, causing a collision.
    For these streams, it is necessary to tell RTC to '--include-root' causing it to load each RTC component
    into a subfolder named after the component in order to keep the identically-named paths seperated.
    Defaults to false.
  --tmpDir true|false|dirname
    If true then a temp folder will be created, in which the migration will be run.
    If false then the migration will put its temp files in the current directory.
    If set to anything other than true or false then that will set the folder to be used.
    Defaults to false, i.e. "."
  --maxBatchSize "positiveInteger"
    If set to a non-zero number, limits the number of commits that will be migrated before
    results will be pushed to git.
    Setting this to a low number will result in push errors being detected earlier and intermediate
    results being pushed so that less work is lost in the case of an error.
    Setting this to a high number allows for a faster throughput.
    Defaults to 0, i.e. no limit, so the entire migration will be done in one go.
  --xxxEnvName "environmentVariableName"
    For any/all of the above arguments, specifies that the value for argument 'xxx' is to be read from
    the specified environment variable.
Description:
  Migrates source code from RTC to Git.
  If the git repository already exists then its history will be appended to.
  If the RTC source has a snapshot of the specified name then this will be used as a starting point.
  After changes have been successfully pushed to Git, the snapshot will be updated so that subsequent
  migrations can continue where we left off.
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

# $1 = value that must be 'true' or 'false'
isTrueOrFalse() {
  [ "$1" = "true" -o "$1" = "false" ]
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

# Outputs, to stdout, gitignore rules to ignore RTC files.
gitIgnoreJazzFiles() {
  echo
  echo "# File originally generated by ${SCRIPT_NAME} when migrating from RTC"
  echo '/.jazz5'
  echo '/.jazz5/'
  echo '/.jazzShed'
  echo '/.jazzShed/'
  echo '/.metadata'
  echo '/.metadata/'
  echo
}

# Lists the contents of a git repo/rtc workspace *excluding* all the
# git/rtc meta-data files (/.git, /.jazz5 etc)
# $1 = the folder whose contents to list
listNoteworthyContents() {
  local baseDir="$1"
  shift
  (
  cd "${baseDir}"
  export LC_ALL=C.UTF-8
  find . \
    -mindepth 1 \
    -type d -path './.git' -prune -o \
    -type d -path './.jazz5' -prune -o \
    -type d -path './.metadata' -prune -o \
    -type d -path './.jazzShed' -prune -o \
    -printf '%4.4M %10s %p\n' \
  | sort -b --stable --key=1.16 \
  | cut -c1-16,18-
  ) | \
  (
  IFS=
  if read -r line
  then
    echo "${line}"
    cat
  else
    echo "[empty]"
  fi
  )
}

# Copies git-only files from a git repo into an RTC workspace,
# making the RTC workspace both a git repo and an RTC workspace.
# $1 = git repo dir
# $2 = rtc workspace dir
copyGitOnlyFilesIntoRtcWorkspace() {
  local gitRepoDir="$1"
  local rtcRepoDir="$2"
  local IFS=
  local filename
  # We want:
  # ./.git/...
  # ./.gitattributes
  # all .gitignore
  (
    cd "${gitRepoDir}" || exit 1
    find . \
      -path './.git' -print -o \
      -path './.gitattributes' -print -o \
      -name '.gitignore' -print \
  ) \
  | cut -c3- | \
  ( \
    IFS=
    while read -r filename
    do
      parent="$(dirname "${filename}")"
      if [ -d "${rtcRepoDir}/${parent}" ]
      then
        ( cd "${gitRepoDir}" && tar -cf - "${filename}" ) | ( cd "${rtcRepoDir}" && tar -xf - )
      fi
    done
  )
}

# Tries to grab existing source from git, and leaves us with a minimal clone or an empty clone.
# Note: If the remote repo doesn't exist, or we're not allowed to access it, then we'll fail.
# $1 = remote git repo
# $2 = where to put it
# $3 = credentials helper script
# returns 0 if we have a repository (which may be empty) that's now ready for use.
# returns 1 if the remote repository does not exist, or does exist and we're not allowed to use it.
gitReadRemoteRepositry() {
  local originUrl="$1"
  shift
  local repoDir="$1"
  shift
  local credHelper="$1"
  shift
  local numberOfAttempts=5
  (
    if echoAndRunCommandWithRetries ${numberOfAttempts} git clone --config "credential.helper=${credHelper}" --no-checkout --depth 1 --single-branch "${originUrl}" "${repoDir}"
    then
      true
    else
      robustlyDelete "${repoDir}"
      false
    fi
  )
  if [ -d "${repoDir}/.git" ]
  then
    (
      cd "${repoDir}"
      git config --add "credential.helper" "${credHelper}"
      # We've previously run into issues where git says:
      # error: RPC failed; curl 92 HTTP/2 stream 0 was not closed cleanly: CANCEL (err 8)
      # Googling suggests that switching off HTTP/2 can work
      # as can increasing the http.postBuffer size.
      #
      # Default http.postBuffer size is 1 MiB, i.e. 1048576
      # git-config doc says increasing this will increase memory usage as it'll always allocate
      # the whole buffer and isn't a good solution ... but we're desperate.
      # We tried 128meg, as that should be larger than the largest non-lfs file we'll
      # encounter but it didn't help, so other posts suggested 500meg so we'll try 1gig.
      git config http.postBuffer 1073741824
      # As the error is coming from HTTP/2 and there's talk that this can be caused by faulty
      # network proxies, and we're doing this from a work network where trying-to-be-clever-but-
      # failing network devices are all too common (and never found to be at fault, even when
      # they're at fault), this might do the trick too.
      git config http.version HTTP/1.1
      # ...however setting this flag only changed the error message to one that isn't HTTP/2
      # and now it says:
      # error: RPC failed; curl 18 transfer closed with outstanding read data remaining
      # Other posts suggest that the answer is to switch to using SSH instead of HTTP.
      #
      # It's most likely that the answer is to use git-lfs to keep the non-lfs
      # data size to a minimum, and then these problems seem to disappear.
      # Certainly, after fixing a myriad of issues with our use of git-lfs, these network
      # errors seem to have gone away...
    )
  else
    return 1
  fi
}

# Examines a local repo and tells us if it's not empty.
# If it isn't empty, it ensures we've got all the contents pulled that we'll need.
# $1 = where the local repo is
# returns 0 if we have an existing (non-empty) repository that's now ready for use.
# returns 1 if the remote repository does not exist, or does exist and is empty.
gitLocalRepositoryIsNotEmpty() {
  local repoDir="$1"
  shift
  (
    cd "${repoDir}"
    # if the repo is brand new, it'll be empty and "git show" will complain "fatal: bad default revision 'HEAD'"
    if git show >/dev/null 2>&1
    then
      return 0
    else
      return 1
    fi
  )
}

# Given a local partial clone of a remote repo, ensures that we've got all the
# contents pulled that we'll need.
# If we're not told where to branch from then we'll append to the end of master.
# If we're given a branch name then we'll append to that.  If it does exist already then we'll create it.
# $1 = remote git repo
# $2 = where to put it
# $3 = credentials helper script
# $4 = branch name or empty - if $5 is empty then we'll append to this, creating it from master if necessary
# $5 = commit to branch from - normally we start at the end of the branch/master, but we can fork from an earlier commit
# $5 = email we claim as ours when committing to this repo
# $6 = username we claim as ours when committing to this repo
# returns 0 if we have an existing (non-empty) repository that's now ready for use.
# returns 1 if the remote repository does not exist, or does exist and is empty.
gitSetupNonEmptyLocalRepository() {
  local originUrl="$1"
  shift
  local repoDir="$1"
  shift
  local credHelper="$1"
  shift
  local branchName="$1"
  shift
  local branchFromCommit="$1"
  shift
  local ourEmail="$1"
  shift
  local ourUsername="$1"
  shift
  local numberOfAttempts=5
  (
    cd "${repoDir}"
    # We may update tags later, so we need to ensure that we know about all
    # of them.
    echoAndRunCommandWithRetries ${numberOfAttempts} git fetch --tags origin
    # If we're told to branch from a specific point or we're using a branch, we're going to need everything
    if [ -n "${branchFromCommit}" -o -n "${branchName}" ]
    then
      echoAndRunCommandWithRetries ${numberOfAttempts} git fetch origin '+refs/heads/*:refs/remotes/origin/*'
    fi
    # The git migrator sometimes does a JGit "garbage collect" which tends to
    # fail if it can't find a commit, so unfortunately we'll need to pull the
    # entire history.
    echoAndRunCommandWithRetries ${numberOfAttempts} git fetch --unshallow
    # If we are putting data into git notes then we need to ensure that
    # we've fetched the notes too, as they're not fetched by default.
    echoAndRunCommandWithRetries ${numberOfAttempts} git fetch origin 'refs/notes/commits:refs/notes/commits' || true
    echoAndRunCommandWithRetries ${numberOfAttempts} git fetch origin 'refs/notes/*' || true
    # In case we need to commit/push anything from this script.
    git config user.email "${ourEmail}"
    git config user.name "${ourUsername}"
    # Make sure that git never pays any attention to RTC files.
    touch .git/info/exclude
    gitIgnoreJazzFiles >> .git/info/exclude
    # If we're told to branch from a specific point, we will, otherwise we'll default to the end of master.
    if [ -n "${branchFromCommit}" ]
    then
      # We're starting from a specific point in history
      echo git checkout "${branchFromCommit}"
           git checkout "${branchFromCommit}"
      if [ -n "${branchName}" ]
      then
        # ...and making a branch there
        echo git checkout -b "${branchName}"
             git checkout -b "${branchName}"
      fi
    else
      if [ -n "${branchName}" ]
      then
        # We're either using an existing branch
        if echoAndRunCommandWithRetries ${numberOfAttempts} git fetch origin "${branchName}"
        then
          echo git checkout "${branchName}"
               git checkout "${branchName}"
        else
          # ... or starting a new one
          echo git checkout -b "${branchName}"
               git checkout -b "${branchName}"
        fi
      else
        echo git checkout
             git checkout
      fi
    fi
  )
}

# Creates a new blank local clone pointing back at a remote repo.
# The remote repo must already exist and allow us to push to it.
# $1 = remote git repo
# $2 = where to put it
# $3 = credentials helper script
# $4 = branch name (not empty - should be "master" if nobody's specified one)
# $5 = email we claim as ours when committing to this repo
# $6 = username we claim as ours when committing to this repo
gitSetupEmptyLocalRepository() {
  robustlyDelete "${2}"
  echo git init "${2}"
  git init "${2}"
  (
    cd "${2}"
    git config --add "credential.helper" "${3}"
    git config user.email "${5}"
    git config user.name "${6}"
    echo git remote add origin "${1}"
    git remote add origin "${1}"
    if [ "${4}" != "master" ]
    then
      echo git checkout -b "${4}"
           git checkout -b "${4}"
    fi
    git remote -v
    touch .git/info/exclude
    gitIgnoreJazzFiles >> .git/info/exclude
  )
}

# Creates (or re-creates) an annotated tag in the git repo
# The tag will be placed at the current head of the current branch
# $1 = folder where the git repo is
# $2 = name of the git tag to create
# $3 = description of the tag, or empty for no description
# $4 = creation timestamp for the tag, or empty for "now".
gitCreateOrOverwriteTag() {
  local repoDir="${1}"
  local tagName="${2}"
  local tagDescriptionOrEmpty="${3}"
  local tagTimestampOrEmpty="${4}"
  (
    cd "${repoDir}"
    echo git tag -d "${tagName}"
         git tag -d "${tagName}" || true
    if [ -n "${tagTimestampOrEmpty}" ]
    then
      export GIT_COMMITTER_DATE="${tagTimestampOrEmpty}"
    fi
    if [ -n "${tagDescriptionOrEmpty}" ]
    then
      echo git tag -a -m "${tagDescriptionOrEmpty}" "${tagName}"
           git tag -a -m "${tagDescriptionOrEmpty}" "${tagName}"
    else
      echo git tag -a                               "${tagName}"
           git tag -a                               "${tagName}"
    fi
  )
}

# Pushes our local git repo to the server
# $1 = remote git repo url
# $2 = where our local repo is
# $3 = credentials helper script
# $4 = branch name or empty
# If we don't have a branch or tag but we do have a commit, we'll be in detached mode, so we can't push anything except notes.
# Note: Because "git push" goes over the network, it'll never be 100% reliable.
# Because we will have worked hard to get this far, we implement retries to give
# us the best chance to succeed.
# Note: Because we might invoke git-lfs, we need to ensure that our working directory is "clean", so we git-stash any dirt
# before we start the push, and restore it afterwards.  e.g. It might be dirty because of "missing" .gitignore files that
# are in the index but not in the working directory right now.
gitPushToRemote() {
  local repoUrl="${1}"
  local repoDir="${2}"
  local gitCredHelper="${3}"
  local branchOrEmpty="${4}"
  local numberOfAttempts=5
  local needToUnStashAfterWork='false'
  local exitCode=255
  if [ -n "$(cd "${repoDir}"; git status --porcelain)" ]
  then
    echo "Git status is not clean ahead of push to remote"
    (cd "${repoDir}"; set -x; git status --porcelain)
    if [ -n "$(cd "${repoDir}"; git status --porcelain | grep '^.M ')" ]
    then
      echo "Git says the following files have been modified:"
      ( cd "${repoDir}"; git status --porcelain | grep '^.M ' | sed -e 's,^.M ,,g' | sed -e 's,^"\(.*\)",\1,g' | \
      while read modifiedFile
      do
        ls -ald "${modifiedFile}"
        file "${modifiedFile}"
        ( set -x; git diff "${modifiedFile}" )
      done )
    fi
    # We only want to retain .gitignore deletions, nothing else
    # Any other "changes" will be false-reporting due to git-lfs issues that we'll resolve in a moment...
    local -a stashArgs
    readarray -t stashArgs < <( cd "${repoDir}"; git status --porcelain \
      | ( grep -e '^ D \.gitignore$\|^ D .*/\.gitignore$\|^ D ".*/\.gitignore"$' || true ) \
      | sed -e 's,^ D ,,g' \
      | sed -e 's,^"\(.* .*\)",\1,g' \
      | sed -e 's,^.*/\.gitignore$,*/.gitignore,g' \
      | sort -u \
      )
    if [ -n "${stashArgs:-}" ]
    then
      (cd "${repoDir}"; set -x; git stash push "${stashArgs[@]}" )
      needToUnStashAfterWork='true'
    fi
  fi
  # find out what files (if any) are at or above the specified size threshold.
  # Put paths of big files into this array
  local -a gitPathsOverSize=()
  if [ -n "${arg_gitLfsAboveSize}" ]
  then
    local gitLfsAboveSizeInBytes=$(gitLfsImportMigrateAboveSizeToBytes "${arg_gitLfsAboveSize}")
    readarray -t gitPathsOverSize < <( gitFindAllFilesLargerThanSize "${repoDir}" "${gitLfsAboveSizeInBytes}" )
    if [ -n "${gitPathsOverSize:-}" ]
    then
      echo "Info: Git LFS required for ${#gitPathsOverSize[@]} file(s) at least ${gitLfsAboveSizeInBytes} bytes in size: ${gitPathsOverSize[@]}"
    else
      echo "Info: No files at least ${gitLfsAboveSizeInBytes} bytes in size were found."
    fi
  fi
  # Put paths of any existing should-be-in-LFS already stuff into this array
  local -a gitPathsAlreadyLfsed
  readarray -t gitPathsAlreadyLfsed < <( gitListFilePathsThatShouldBeInLfsAlready "${repoDir}" )
  if [ -n "${gitPathsAlreadyLfsed:-}" ]
  then
    echo "Info: Git LFS already used by ${#gitPathsAlreadyLfsed[@]} pattern(s): ${gitPathsAlreadyLfsed[@]}"
  fi
  # If we were asked to put specific paths into LFS regardless of size, list those here.
  local -a gitPathsRequestedToBeLfsed=()
  if [ -n "${arg_gitLfs}" ]
  then
    readarray -t gitPathsRequestedToBeLfsed < <( echo "${arg_gitLfs}" | tr ',' '\n' | sort -u )
    if [ -n "${gitPathsRequestedToBeLfsed:-}" ]
    then
      echo "Info: Git LFS requested for ${#gitPathsRequestedToBeLfsed[@]} pattern(s): ${gitPathsRequestedToBeLfsed[@]}"
    fi
  fi
  # Combine to make one big string of the above lists as a single sorted CSV
  local gitPathsToLfsInclude=$( \
    ( \
      for arg in "${gitPathsRequestedToBeLfsed[@]}" ; do echo "${arg}"; done ; \
      for arg in "${gitPathsOverSize[@]}"           ; do echo "${arg}"; done ; \
      for arg in "${gitPathsAlreadyLfsed[@]}"       ; do echo "${arg}"; done \
    ) | sort -u | stdinToCsv)
  if [ -n "${gitPathsToLfsInclude}" ]
  then
    # We have LFS work to do
    echo "Info: Git LFS needed: calling git lfs install"
    if (cd "${repoDir}"; git lfs install)
    then
      true
    else
      returncode="$?"
      return "${returncode}"
    fi
    # LFS can go wrong in multiple ways.
    # - using the --above=... causes wildcards in .gitattributes rather than specific files
    # - using the --above=... doesn't cause all files matching the .gitattribute rules to go into LFS
    # - JGit doesn't seem to always respect git-lfs rules and can put things in as non-LFS
    # - if there's any mismatches, git status shows files as changed even if they haven't.
    # To fix it up, we need to tell LFS to re-import everything we know ought to be in LFS.
    # This takes a while, which is why we make sure we only need to call it once, telling it
    # to do absolutely everything needed, to everywhere.
    #
    # Note: lfs is a bit borked in that the --everything flag doesn't do everything, so we need
    # to make a list of "what <everything> consists of" and tell LFS to process that instead.
    # We've also found that the explicit list doesn't necessarily do it either, so we'll *also*
    # try with a --everything too.
    local -a gitLfsMigrateEverythingArgs
    readarray -t gitLfsMigrateEverythingArgs < <( getAllGitLfsImportIncludeRefs "${repoDir}" )
    # Note: If lfs is a bit borked and things aren't in LFS when .gitattributes says they should be
    # then we'll also get false "this has changed" reports.
    # So, if this has anything to do, it's very likely to think it's about to overwrite local
    # changes.
    # That's why it's safe (and necessary) to say --yes here.
    echo "Info: Git LFS required: calling git lfs migrate import --yes --verbose --everything '--include=${gitPathsToLfsInclude}'"
    if (cd "${repoDir}";                  git lfs migrate import --yes --verbose --everything "--include=${gitPathsToLfsInclude}")
    then
      true
    else
      returncode="$?"
      return "${returncode}"
    fi
    echo "Info: ... and also calling  git lfs migrate import --yes --verbose ${gitLfsMigrateEverythingArgs[@]} '--include=${gitPathsToLfsInclude}'"
    if (cd "${repoDir}";              git lfs migrate import --yes --verbose "${gitLfsMigrateEverythingArgs[@]}" "--include=${gitPathsToLfsInclude}")
    then
      true
    else
      returncode="$?"
      return "${returncode}"
    fi
    (cd "${repoDir}"; echoCurrentGitLfsFiles)
  fi
  if (
    cd "${repoDir}"
    # Work out what we've got that might need pushing
    local -a whatToGitPush
    readarray -t whatToGitPush < <( getAllGitRefsToPush ".")
    # Note: It's important to push everything at once, otherwise our on-demand lfs code can
    # cause git history to be rewritten and invalidate earlier pushes.
    # It needs to be "all at once or not at all".
    echoAndRunCommandWithRetries ${numberOfAttempts} gitWithLfsIfRequired push --force origin "${whatToGitPush[@]}"
  )
  then
    exitCode=0
  else
    exitCode="$?"
  fi
  if [ "${needToUnStashAfterWork}" = 'true' ]
  then
    echo "Git status was not clean and we stashed the changes we wanted to retain - unstashing now"
    (cd "${repoDir}"; set -x; git stash pop)
  fi
  return "${exitCode}"
}

# Determines if the local git repo is meant to be using lfs already
# $1 = where our local repo is
# returns 0 if git-lfs is required
# returns 1 if there's no indication of existing git-lfs usage
gitRepoAlreadyUsesLfs() {
  local repoDir="${1}"
  # If there's an lfs folder in our .git folder then that's a sure sign.
  if [ -d "${repoDir}/.git/lfs" ]
  then
    return 0
  fi
  # if no gitattributes then no call for lfs
  if [ ! -f "${repoDir}/.gitattributes" -o ! -r "${repoDir}/.gitattributes" ]
  then
    return 1
  fi
  # if we do have a .gitattributes then that could ask for lfs
  local filename remainder
  while read filename remainder
  do
    # if the first char is a hash then that's a comment and we ignore it
    if [ "${filename:0:1}" = '#' ]
    then
      continue
    fi
    # if the remainder contains "filter=lfs diff=lfs merge=lfs" then that means lfs
    if expr match "${remainder}" '.*filter=lfs diff=lfs merge=lfs' >/dev/null
    then
      # if we find evidence of lfs then we return success
      return 0
    fi
  done < "${repoDir}/.gitattributes"
  # if not, we don't need lfs
  return 1
}

# Outputs the list of file globs (paths or patterns) for which git-lfs is already enabled.
# Note: Assumes that this is only ever cumulative over history.
# $1 = git repo folder
# stdout = one entry per line
gitListFilePathsThatShouldBeInLfsAlready() {
  cd "$1"
  if [ -f '.gitattributes' ]
  then
    cat .gitattributes | grep ' filter=lfs ' | grep -v '^ *#' | awk '{print $1}' | sort -u | sed 's,\[\[:space:\]\], ,g'
  fi
}

# Works out what --include-ref=... args are necessary for a git lfs migrate import call
# to process everything (as in, absolutely everything, nothing missed out).
# $1 = git repo folder
# stdout = arguments to use, one per line
getAllGitLfsImportIncludeRefs() {
  cd "${1}"
  # To make matters complicated, the git lfs migrate --everything flag doesn't actually
  # do everything.
  # It only does commits reachable from "all known branches", which means we can miss
  # tags that aren't on any of the branches (e.g. dealiased build tags are one commit
  # removed).
  # So, instead, we need to tell it to --include-ref=refs/... for all non-remote refs.
  git for-each-ref --format='%(refname)' refs/ \
  | grep -v '^refs/remotes/' \
  | sed 's,^,--include-ref=,g' \
  | sort -u
}

# Works out what args to git push are necessary to push absolutely everything.
# $1 = git repo folder
# stdout = arguments to use, one per line
getAllGitRefsToPush() {
  cd "${1}"
  # All tags can be replaced by a single argument --tags
  # ...but if we're doing tags, we should --follow-tags too.
  # Anything else can be abbreviated using globs.
  git for-each-ref --format='%(refname)' refs/ \
  | grep -v '^refs/remotes/' \
  | grep -v '^refs/stash$' \
  | sed -e 's,^\(refs/.*\)/[^/]*$,\1/\*,g' \
        -e 's,^refs/tags/.*$,--tags\n--follow-tags,g' \
  | sort -u
}

# Scans the entire git history for files exceeding a certain size.
# This can be used to figure out what files might need to go into git-lfs
# without using git-lfs's --above feature (which doesn't work in git 2.30.2).
# $1 = git repo folder
# $2 = min size in bytes to list
# stdout one path per line of every file that is the specified size or larger.
# including all paths that a file has been known as.
gitFindAllFilesLargerThanSize() {
  cd "${1}"
  local minSize="$2"
  local gitBlobId
  local gitSize
  local gitFilePath
  local gitCommitId
  # Maintenance note:
  # If you search the 'net you'll probably find a solution telling you
  # to git rev-list --objects --all \
  # | git cat-file --batch-check='%(objecttype) %(objectname) %(objectsize) %(rest)' \
  # | sed -n 's/^blob //p'
  # to find all the files by size.
  # HOWEVER, if you do this, you'll not find all the filenames that blobs are known as
  # over the whole history.
  # e.g. if a 100meg file is added in two places and/or gets renamed, we'd only see
  # one of those names ... and git-lfs will need to be told about all the names.
  # So instead we have to list the whole git tree for each commit to find the
  # path of every blob, then find the size of each blob.
  # Note: While git rev-list is fast, the ls-tree takes ages and generates a lot of data
  # such that the sort -u has a lot of data to handle - a big repo can use 10gigs.
  # Once the sort -u has happened, data sizes shrink and are much more manageable.
  while read gitBlobId gitSize gitFilePath
  do
    if [ "${minSize}" -gt "${gitSize}" ]
    then
      return
    fi
    echo "${gitFilePath}"
  done < <( git rev-list --all --reverse \
            | ( while read commit; \
                do \
                  git ls-tree -r "${commit}"; \
                done ) \
            | sed -e 's,^[0-9]* blob ,,g' \
            | tr '\t' ' ' \
            | sort -u \
            | git cat-file --batch-check='%(objecttype) %(objectname) %(objectsize) %(rest)' \
            | sed -n 's/^blob //p' \
            | sort --numeric-sort --reverse --key=2 \
           ) \
  | sort -u
}

# Used to wrap a git-push to auto-detect the need to use git-lfs,
# and to migrate files into lfs if they are too large as-is.
#
# $* = arguments to give to git to make it do a push
#
# stdout/stderr = any git-lfs activity, followed by the git push
# exit code = 0 on success
gitWithLfsIfRequired() {
  local tmpdir="$(mktemp -d -t gitpush.$$.XXXXXXXXXX)"
  local trytogitpush="true"
  local returncode=255
  local gitlfsinstalled=false
  local hashOfGitLfsStateBeforeWeLfsMigrate
  local hashOfGitLfsStateAfterWeLfsMigrated
  local numberOfTimesWeveMigratedToNoEffect=0
  local maxNumberOfTimesWeMigrateToNoEffectBeforeAborting=3
  while [ "${trytogitpush}" != "false" ]
  do
    trytogitpush="false"
    # Note: 3>&1 1>&2- 2>&3- swaps stdout and stderr
    # letting us capture stderr for later inspection without interrupting it
    echo "Info: Calling git ${@}"
    if ( set -o pipefail ; git "${@}" 3>&1 1>&2- 2>&3- | tee "${tmpdir}/stderr" 3>&1 1>&2- 2>&3- )
    then
      # success - we can stop now
      rm -rf "${tmpdir}"
      return 0
    else
      returncode="$?"
    fi
    hashOfGitLfsStateBeforeWeLfsMigrate="$(echoCurrentGitLfsFiles | sha256sum)"
    while read line
    do
      filepath="$(expr "${line}" : '.*File \(.*\) is .*; this exceeds .* file size limit')"
      if [ -n "${filepath}" ]
      then
        # We need to use LFS, so install it if needed
        if [ "${gitlfsinstalled}" != "true" ]
        then
          echo "Info: Git LFS required: calling git lfs install"
          if git lfs install
          then
            gitlfsinstalled="true"
          else
            returncode="$?"
            rm -rf "${tmpdir}"
            return "${returncode}"
          fi
        fi
        # We don't trust git-lfs's --everything argument to do everything - it doesn't include all tags
        # Equally, we don't trust the explicit list either, so we do both.
        local -a gitLfsMigrateEverythingArgs
        readarray -t gitLfsMigrateEverythingArgs < <( getAllGitLfsImportIncludeRefs "." )
        echo "Info: Git LFS required: calling git lfs migrate import --verbose --everything --include=\"${filepath}\""
        if                                    git lfs migrate import --verbose --everything --include="${filepath}"
        then
          trytogitpush="true"
        else
          returncode="$?"
          rm -rf "${tmpdir}"
          return "${returncode}"
        fi
        echo "Info: ... and also calling git lfs migrate import --verbose ${gitLfsMigrateEverythingArgs[@]} --include=\"${filepath}\""
        if                               git lfs migrate import --verbose "${gitLfsMigrateEverythingArgs[@]}" --include="${filepath}"
        then
          trytogitpush="true"
        else
          returncode="$?"
          rm -rf "${tmpdir}"
          return "${returncode}"
        fi
        echoCurrentGitLfsFiles
      fi
      # Note: We read from a file instead of piping because a pipe would require a subshell
      # and we can't change variables from a subshell.
    done < "${tmpdir}/stderr"
    # if we've made a difference, all is good. If not, we might run out of patience.
    hashOfGitLfsStateAfterWeLfsMigrated="$(echoCurrentGitLfsFiles | sha256sum)"
    if [ "${hashOfGitLfsStateBeforeWeLfsMigrate}" = "${hashOfGitLfsStateAfterWeLfsMigrated}" ]
    then
      numberOfTimesWeveMigratedToNoEffect=$((numberOfTimesWeveMigratedToNoEffect+1))
      if [ "${trytogitpush}" = "true" ]
      then
        if [ "${numberOfTimesWeveMigratedToNoEffect}" -le "${maxNumberOfTimesWeMigrateToNoEffectBeforeAborting}" ]
        then
          echo "Warn: Git LFS situation was unchanged by lfs migration (${numberOfTimesWeveMigratedToNoEffect} of ${maxNumberOfTimesWeMigrateToNoEffectBeforeAborting})."
        else
          echo "Error: Git LFS situation was unchanged by ${maxNumberOfTimesWeMigrateToNoEffectBeforeAborting} lfs migrations; abandoning."
          trytogitpush="false"
        fi
      fi
    else
      numberOfTimesWeveMigratedToNoEffect=0
    fi
  done
  rm -rf "${tmpdir}"
  return "${returncode}"
}

# Lists details of the files we have in git-lfs
# Must already be in the git repository folder
# Takes no arguments
# Outputs human-readable text to stdout.
echoCurrentGitLfsFiles() {
  echo "Info: Git LFS files are as follows:"
  (git lfs ls-files --long --size --all --deleted 2>&1 || true ) | ( sed 's,^,  ,g' || true )
}

# $* = command to run
echoAndRunCommandWithRetries() {
  local attemptsTotal=$1
  local attemptsRemaining=$1
  local attemptsMade=0
  shift
  local exitCode
  while [ "${attemptsRemaining}" -gt 1 ]
  do
    attemptsMade=$((attemptsMade + 1))
    attemptsRemaining=$((attemptsRemaining - 1))
    echo "${@}"
    if "${@}"
    then
      return 0
    else
      exitCode=$?
      echo "Warning: $@ failed, exit code ${exitCode} - retrying [${attemptsMade}/${attemptsTotal}]"
      sleep 1
    fi
  done
  echo "${@}"
  if "${@}"
  then
    return 0
  else
    exitCode=$?
    echo "$@ failed, exit code ${exitCode} [on attempt ${attemptsTotal}/${attemptsTotal}]"
    return "${exitCode}"
  fi
}

# Gets the current commit from git
# $1 = where local git repo is
# stdout = "commit 82121520af71e4db9f5d267fa467fdb774f02ebc" or similar.
gitGetCurrentCommit() {
  (
    cd "${1}"
    git log --max-count=1 --pretty=raw | head -1
  )
}

# Given a string of the form <number><units>
# where <number> is a integer and <units> is
# one of b, k, kb, m, mb, g, or gb,
# this outputs the size in bytes.
# In case of error, nothing is output and it returns 1.
gitLfsImportMigrateAboveSizeToBytes() {
  local numberAndUnits="$1"
  local numberOnly=$(expr "${numberAndUnits}" : '^\([0-9]*\).*$')
  local unitsOnly=$(expr  "${numberAndUnits}" : '^[0-9]*\(.*\)$')
  if [ -z "${numberOnly}" -o -z "${unitsOnly}" ]
  then
    return 1
  fi
  local unitSize
  case "${unitsOnly}"
  in
    b|B)
      unitSize=1
      ;;
    k|K|kb|Kb|kB|KB)
      unitSize=1024
      ;;
    m|M|mb|Mb|mB|MB)
      unitSize=1048576
      ;;
    g|G|gb|Gb|gB|GB)
      unitSize=1073741824
      ;;
    *)
      return 1
      ;;
  esac
  expr "${numberOnly}" '*' "${unitSize}"
}

# reads in zero or more lines of text and turns them into a comma separated list on a single line.
# Note: No handling is done of commas within lines.
# stdin = zero or more strings on separate lines
# stdout = either nothing (if no input), or the lines joined by commas.
stdinToCsv() {
  local line
  local result=""
  if read line
  then
    result="${line}"
    while read line
    do
      result+=",${line}"
    done
    echo "${result}"
  fi
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

# $1 = name to search for - this is expected to be in quotes after the UUID
# $2 = human-readable name of what we are looking for, e.g. "snapshots called foo in stream bar"
#      This is used
# $3 = (optional) text to match the remainder of the line
# $4 = type to search for - this must be before the UUID
#      If this is omitted then we ignore whatever is before the UUID.
# Note: Comparison is case-sensitive.
# stdin should be from the scm command whose output we are parsing.
# stdout receives the UUID we found.
# returns 0 if nothing was found.
# returns 0 if one thing was found.
# outputs an error to stderr and returns 1 if more than one thing was found.
getUniqueRTCUuidFromStdinListOrEmpty() {
  local nameToFind="${1}"
  shift
  local whatWeAreLookingFor="${1}"
  shift
  local thingAfterNameToFind="${1:-}"
  shift
  local whatToFind="${1:-}"
  if [ -z "${thingAfterNameToFind}" ]
  then
    if [ -z "${whatToFind}" ]
    then
      local clarification=''
    else
      local clarification=" (${whatToFind})"
      shift
    fi
  else
    if [ -z "${whatToFind}" ]
    then
      local clarification=" (${thingAfterNameToFind})"
    else
      local clarification=" (${whatToFind} ${thingAfterNameToFind})"
      shift
    fi
  fi
  local numberFound='0'
  local firstFound=''
  local whole_line preUuid uuid name afterName
  while read -r whole_line; do
    # Expecting lines to be of the form:
    # (uuid) "whatToSearchFor" restOfLine
    # OR
    # SomethingWeMightIgnore (uuid) "whatToSearchFor" restOfLine
    # The former is output by commands that list things,
    # the latter when creating things.
    if [ -n "${whatToFind}" ]
    then
      preUuid=`expr "${whole_line}" : '^\([^(]*\)([^)]*) "[^"]*".*$'`
      if ! expr "${preUuid}" : " *${whatToFind} *$" >/dev/null
      then
        continue
      fi
    fi
    uuid=`expr "${whole_line}" : '^[^(]*(\([^)]*\)) "[^"]*".*$'`
    name=`expr "${whole_line}" : '^[^(]*([^)]*) "\([^"]*\)".*$'`
    if [ "${name}" != "${nameToFind}" ]
    then
      continue
    fi
    if [ -n "${thingAfterNameToFind}" ]
    then
      afterName=`expr "${whole_line}" : '^[^(]*([^)]*) "[^"]*"\(.*\)$'`
      if [ "${afterName}" != " ${thingAfterNameToFind}" ]
      then
        continue
      fi
    fi
    case "${numberFound}"
    in
      0)
        firstFound="${uuid}"
        echo "${uuid}"
        numberFound=1
        ;;
      1)
        echo "Error: Multiple ${whatWeAreLookingFor} named '${nameToFind}'${clarification} found: ${firstFound}" >&2
        echo "Error: Multiple ${whatWeAreLookingFor} named '${nameToFind}'${clarification} found: ${uuid}" >&2
        numberFound="multiple"
        ;;
      *)
        echo "Error: Multiple ${whatWeAreLookingFor} named '${nameToFind}'${clarification} found: ${uuid}" >&2
        ;;
    esac
  done
  if [ "${numberFound}" = "multiple" ]
  then
    return 1
  fi
  return 0
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
  getUniqueRTCUuidFromStdinListOrEmpty "${nameToFind}" "${whatWeAreLookingFor}" "${thingAfterNameToFind}" '' < "${RTCCMDS_TEMP_FILE}"
}

# $1 = what kind of thing we're looking for, e.g. Snapshot
# $2 = what name to search for
# If nothing matches, stdout is empty and it returns 0.
# If one thing matches, stdout is the uuid and it returns 0.
# outputs an error to stderr and returns 1 if more than one thing was found.
getUniqueRTCUuidFromMultiTypeListOrEmpty() {
  local whatToFind="${1}"
  local nameToFind="${2}"
  getUniqueRTCUuidFromStdinListOrEmpty "${nameToFind}" "${whatToFind}" '' "${whatToFind}" < "${RTCCMDS_TEMP_FILE}"
}

# Determines if the RTC migration just run has reached the endpoint or not.
# $1 = the RTC source workspace UUID that represents what we're working towards
# $2 = the RTC target workspace UUID that's our current migration position
# $3 = where the RTC target workspace is loaded on the filesystem
# exit code is 0 and stdout is "true" if the migration is done (i.e. RTC source workspace == RTC target workspace)
# exit code is 0 and stdout is "false" if the migration is incomplete.
# exit code is non-zero if we can't tell.
rtcMigrationIsComplete() {
  # We want to compare the two workspaces and return 0 is there is no
  # difference in their contents/changesets etc.
  # i.e. there are no pending changes ready to be accepted into the target
  # from the source.
  # While this sounds easy, RTC lacks a workable "is there a difference
  # between workspace X and Y" command :-(
  #
  # In theory there is a "compare" command that can compare two workspaces
  # to see if they contain the same changesets, but in practise it isn't
  # viable as it takes forever to run (except in very trivial scenarios),
  # presumably because it doesn't support limiting the number of differences
  # to be reported.
  #
  # There is a "diff" command that allows you to compare two workspaces and to
  # set an upper limit on the number of changes to be reported, but this does
  # not work either - if you ask it to compare two different workspaces then
  # it'll sometimes tell you that there are no differences, and at other times
  # it'll list all the changes (ignoring --maximum) and taking forever.
  #
  # There is also "show status", which requires a loaded workspace (luckily we
  # we happen to have one of those) but while this also has a --max-changes
  # argument, like "diff" it ignores it and returns everything regardless, so
  # it's only viable for trivial use-cases too.
  #
  # We can't assume that we've got a trivial number of changesets, so none of
  # those options are viable (This is why folks are migrating away from RTC).
  # This means that we've got little other option than to scrape the logs from
  # the migration tool output and look for tell-tale strings that tells us that
  # the migration is either fully complete or is only partly complete because
  # we stopped before we reached the end.
  # Obviously this is rather dodgy as changeset descriptions are _also_ present
  # in the output, but this is the best we can do within RTC's limitations.

  # If migration is complete, we expect to see
  # 'Migration took [<integer>] s'
  # in the log, and only see that once.
  local migrationCompletePattern='Migration took \[[0-9][0-9]*\] s'
  # If migration is incomplete, we expect to see
  # 'Incomplete migration took [<integer>] s'
  # in the log, and only see that once.
  local migrationIncompletePattern='Incomplete migration took \[[0-9][0-9]*\] s'
  # In both cases, we expect that text to be prefixed by a timestamp of the
  # form '[YYYY-MM-DD hh:mm:ss.SSS]' and then a space.
  local timestampPattern='\[[0-9][0-9][0-9][0-9]-[01][0-9]-[0-3][0-9] [012][0-9]:[0-6][0-9]:[0-6][.0-9]*\]'
  local migrationCompleteRegex="^${timestampPattern} ${migrationCompletePattern}\$"
  local migrationIncompleteRegex="^${timestampPattern} ${migrationIncompletePattern}\$"
  local migrationCompleteCount=$(grep "${migrationCompleteRegex}" "${RTCCMDS_TEMP_FILE}" | wc -l)
  local migrationIncompleteCount=$(grep "${migrationIncompleteRegex}" "${RTCCMDS_TEMP_FILE}" | wc -l)
  if [ "${migrationCompleteCount}" -eq 1 -a "${migrationIncompleteCount}" -eq 0 ]
  then
    echo "true"
    return 0
  elif [ "${migrationCompleteCount}" -eq 0 -a "${migrationIncompleteCount}" -eq 1 ]
  then
    echo "false"
    return 0
  else
    echo "Error: rtc migration log '${RTCCMDS_TEMP_FILE}' contained ${migrationCompleteCount} counts of regex '${migrationCompleteRegex}' and ${migrationIncompleteCount} counts of regex '${migrationIncompleteRegex}'.  We were expecting just one occurrence of just one of them." >&2
    return 1
  fi
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

# $1 = string that might be a RTC UUID, but might not.
isRtcUuid() {
  expr "${1}" : '^_[-_a-zA-Z0-9]....................[-_a-zA-Z0-9]$' >/dev/null 2>&1
}

# Given stdin of zero or more lines until we get the one we want.
# stdout = the lines up to, but not including, the one we want.
# $1 = the contents of the line we want
# exit code 0 if we find it, exit 1 if not.
getLinesPreceeding() {
  local whatWeEndOn="$1"
  local found="false"
  local line
  while read line
  do
    if [ "${line}" = "${whatWeEndOn}" ]
    then
      found="true"
    fi
    if [ "${found}" != "true" ]
    then
      echo "${line}"
    fi
  done
  [ "${found}" = "true" ]
}

# $1 = changeset we're looking for
# stdout = whitespace-separated list of changeset IDs that preceed that.
# exit code 0 if we found the changeset
# exit code 1 if we didn't.
getChangesetsThatPreceed() {
  local branchAtRtcChangeset="${1}"
  cat "${RTCCMDS_TEMP_FILE}" \
  | getChangesetIDsFromRtcWorkspaceHistory \
  | getLinesPreceeding "${branchAtRtcChangeset}"
}



# Setup variables to hold the parsed arguments.
arg_gitRepository=""
arg_gitUsername=""
arg_gitPassword=""
arg_gitBranchName=""
arg_gitTagName=""
arg_gitTagDescription=""
arg_gitTagTimestamp=""
arg_gitLfs=""
arg_gitLfsAboveSize=""
arg_rtcUri=""
arg_rtcUsername=""
arg_rtcPassword=""
arg_rtcSource=""
arg_rtcProjectArea=""
# leave unset arg_rtcTeamName=""
arg_rtcSourceSnapshot=""
arg_branchAtRtcChangeset=""
arg_branchAtGitCommit=""
arg_rtcMarkerSnapshotEnabled="true"
arg_rtcMarkerSnapshot=""
arg_rtcIncludeRoot="false"
arg_tmpDir="false"
arg_maxBatchSize="0"

# Parse CLI arguments
while [ "$#" -gt 0 ]
do
  case "${1}" in
    help|-h|--help)
      doHelp; exit 0
      ;;
    --gitRepository|--gitUsername|--gitPassword|--gitBranchName|--gitTagName|--gitTagDescription|--gitTagTimestamp|--gitLfs|--gitLfsAboveSize|--rtcUri|--rtcUsername|--rtcPassword|--rtcSource|--rtcProjectArea|--rtcTeamName|--branchAtRtcChangeset|--branchAtGitCommit|--rtcSourceSnapshot|--rtcMarkerSnapshot|--rtcMarkerSnapshotEnabled|--rtcIncludeRoot|--tmpDir|--maxBatchSize)
      if [ "${2-}" = "" -a "${2-X}" = "X" ]; then
        handleArgAndValue "${1}" || ( doHelp >&2 ; exit 1 ) || exit 1
      else
        handleArgAndValue "${1}" "${2}" || ( doHelp >&2 ; exit 1 ) || exit 1
        shift
      fi
      ;;
    --gitRepositoryEnvName|--gitUsernameEnvName|--gitPasswordEnvName|--gitBranchNameEnvName|--gitTagNameEnvName|--gitTagTimestampEnvName|--gitTagDescriptionEnvName|--gitLfsEnvName|--gitLfsAboveSizeEnvName|--rtcUriEnvName|--rtcUsernameEnvName|--rtcPasswordEnvName|--rtcSourceEnvName|--rtcProjectAreaEnvName|--rtcTeamNameEnvName|--branchAtRtcChangesetEnvName|--branchAtGitCommitEnvName|--rtcSourceSnapshotEnvName|--rtcMarkerSnapshotEnvName|--rtcMarkerSnapshotEnabledEnvName|--rtcIncludeRootEnvName|--tmpDirEnvName|--maxBatchSizeEnvName)
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
# arg_gitBranchName can be empty
# arg_gitTagName can be empty
# arg_gitTagDescription can be empty
if [ -n "${arg_gitTagTimestamp}" ];then # timestamp must be "YYYY-MM-DD hh:mm:ss" or "YYYY-MM-DDThh:mm:ss".
  if ! expr "${arg_gitTagTimestamp}" : '^[12][0-9][0-9][0-9]-[01][0-9]-[0-3][0-9][ T][012][0-9]:[0-5][0-9]:[0-6][0-9]$' >/dev/null 2>&1; then
    echo "Error: gitTagTimestamp argument (${arg_gitTagTimestamp}) is not a valid timestamp.  Expecting YYYY-MM-DD hh:mm:ss or YYYY-MM-DDThh:mm:ss (or nothing at all)." >&2 ; doHelp >&2 ; exit 1
  fi
fi
# arg_gitLfs can be empty
# arg_gitLfsAboveSize can be empty
if [ -n "${arg_gitLfsAboveSize}" ];then # arg_gitLfsAboveSize must have a recognised unit
  if ! gitLfsImportMigrateAboveSizeToBytes "${arg_gitLfsAboveSize}" >/dev/null 2>&1; then
    echo "Error: gitLfsAboveSize argument (${arg_gitLfsAboveSize}) is not a valid size.  Expecting this argument to be blank or <number><unit> where unit is b, kb, mb or gb." >&2 ; doHelp >&2 ; exit 1
  fi
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
# arg_rtcSourceSnapshot can be empty
if [ -n "${arg_branchAtRtcChangeset}" ];then
  if [ -n "${arg_branchAtGitCommit}" ];then
    if ! isRtcUuid "${arg_branchAtRtcChangeset}"; then
      echo "Error: branchAtRtcChangeset argument (${arg_branchAtRtcChangeset}) is not a valid RTC UUID.  Expecting an underscore then 22 characters (or nothing at all)." >&2 ; doHelp >&2 ; exit 1
    fi
    if ! expr "${arg_branchAtGitCommit}" : '^[a-f0-9]......................................[a-f0-9]$' >/dev/null 2>&1; then
      echo "Error: branchAtGitCommit argument (${arg_branchAtGitCommit}) is not a valid (full) git commit ID.  Expecting a 40 (lower-case) hex digits (or nothing at all)." >&2 ; doHelp >&2 ; exit 1
    fi
    if [ -z "${arg_gitTagName}" -a -z "${arg_gitBranchName}" ]; then
      echo "Error: branchAtGitCommit and branchAtRtcChangeset arguments have been set, but neither gitBranchName or gitTagName has been set." >&2 ; doHelp >&2 ; exit 1
    fi
    arg_rtcMarkerSnapshotEnabled="false"
  else
    echo "Error: branchAtRtcChangeset argument was provided but branchAtGitCommit argument was not provided." >&2 ; doHelp >&2 ; exit 1
  fi
else
  if [ -n "${arg_branchAtGitCommit}" ];then
    echo "Error: branchAtGitCommit argument was provided but branchAtRtcChangeset argument was not provided." >&2 ; doHelp >&2 ; exit 1
  fi
fi
if ! isTrueOrFalse "${arg_rtcMarkerSnapshotEnabled}"; then
  echo "Error: rtcMarkerSnapshotEnabled argument (${arg_rtcMarkerSnapshotEnabled}) was neither 'true' or 'false'." >&2 ; doHelp >&2 ; exit 1
fi
if [ "${arg_rtcMarkerSnapshotEnabled}" = "true" ]; then
  if [ -z "${arg_rtcMarkerSnapshot}" ];then
    arg_rtcMarkerSnapshot="Migrated ${arg_rtcSource} to ${arg_gitRepository}"
  fi
else
  arg_rtcMarkerSnapshot=""
fi
if ! isTrueOrFalse "${arg_rtcIncludeRoot}"; then
  echo "Error: rtcIncludeRoot argument (${arg_rtcIncludeRoot}) was neither 'true' or 'false'." >&2 ; doHelp >&2 ; exit 1
fi
# arg_tmpDir can be anything
if ! [ "${arg_maxBatchSize}" -ge 0 ] 2>/dev/null; then
  echo "Error: maxBatchSize argument (${arg_maxBatchSize}) must be an integer >= 0." >&2 ; doHelp >&2 ; exit 1
fi

#echo "arg_gitRepository============'${arg_gitRepository}'"
#echo "arg_gitUsername=============='${arg_gitUsername}'"
#echo "arg_gitPassword=============='${arg_gitPassword}'"
#echo "arg_gitBranchName============'${arg_gitBranchName}'"
#echo "arg_gitTagName==============='${arg_gitTagName}'"
#echo "arg_gitTagDescription========'${arg_gitTagDescription}'"
#echo "arg_gitTagTimestamp=========='${arg_gitTagTimestamp}'"
#echo "arg_gitLfs==================='${arg_gitLfs}'"
#echo "arg_gitLfsAboveSize=========='${arg_gitLfsAboveSize}'"
#echo "arg_rtcUri==================='${arg_rtcUri}'"
#echo "arg_rtcUsername=============='${arg_rtcUsername}'"
#echo "arg_rtcPassword=============='${arg_rtcPassword}'"
#echo "arg_rtcSource================'${arg_rtcSource}'"
#echo "arg_rtcProjectArea==========='${arg_rtcProjectArea}'"
#echo "arg_rtcTeamName=============='${arg_rtcTeamName-(unset)}'"
#echo "arg_rtcSourceSnapshot========'${arg_rtcSourceSnapshot}'"
#echo "arg_branchAtRtcChangeset====='${arg_branchAtRtcChangeset}'"
#echo "arg_branchAtGitCommit========'${arg_branchAtGitCommit}'"
#echo "arg_rtcMarkerSnapshotEnabled='${arg_rtcMarkerSnapshotEnabled}'"
#echo "arg_rtcMarkerSnapshot========'${arg_rtcMarkerSnapshot}'"
#echo "arg_rtcIncludeRoot==========='${arg_rtcIncludeRoot}'"
#echo "arg_tmpDir==================='${arg_tmpDir}'"
#echo "arg_maxBatchSize============='${arg_maxBatchSize}'"
#exit 0

# Pull in our RTC utility functions
# These need various RTC_... variables to be set, which is why we delay until now.
export RTC_EXE_NAME=lscm
. "${SCRIPT_DIR}/setRtcVariables.sh" || (echo "Something went wrong in ${SCRIPT_DIR}/setRtcVariables.sh" >&2 ; exit 1)
# Ensure that any sub-commands we run use the exact same uniqueness, otherwise we can end up using a different config area, different logs etc.
export RTCCMDS_UNIQUE_NAME

#set -x

# Set up some variables we'll need later
case "${arg_tmpDir}"
in
  true)
    ourTempTempFolderWeMade=$(mktemp --directory --tmpdir "${SCRIPT_NAME}.XXXXXXXXXX")
    ourBaseDirectory="${ourTempTempFolderWeMade}"
    ;;
  false)
    ourTempTempFolderWeMade=""
    ourBaseDirectory="$(pwd)"
    ;;
  *)
    ourBaseDirectory="${arg_tmpDir}"
    if [ -d "${arg_tmpDir}" ]
    then
      ourTempTempFolderWeMade=""
    else
      ourTempTempFolderWeMade="${arg_tmpDir}"
      mkdir -p "${arg_tmpDir}"
    fi
    ;;
esac
gitCredentialsHelperScript="${ourBaseDirectory}/gitCredentials.sh"
gitLocalRepoDir="${ourBaseDirectory}/git"
rtcSourceWorkspace=""
rtcSourceSnapshotWorkspace=""
rtcTargetWorkspace=""
rtcWorkingDir="${ourBaseDirectory}/rtc"

# Set up a robust cleanup routine that'll get called even if we crash out with an error.
cleanUp_rtcSourceWorkspace=false
cleanUp_rtcSourceSnapshotWorkspace=false
cleanUp_rtcTargetWorkspace=false
cleanUpOnExit() {
  set +x
  #echo "** ATTENTION ** : Type something to continue."
  #echo "  Something went wrong and we're going to clean up once you've given the go-ahead."
  #echo
  #read ignored
  echo "Cleaning up before exiting..."
  if [ "${cleanUp_rtcTargetWorkspace}" != "false" -a -n "${rtcTargetWorkspace:-}"  ]
  then
    deleteRTCWorkspaces "${rtcTargetWorkspace}"
    cleanUp_rtcTargetWorkspace=false
  fi
  if [ "${cleanUp_rtcSourceWorkspace}" != "false" -a -n "${rtcSourceWorkspace:-}" ]
  then
    deleteRTCWorkspaces "${rtcSourceWorkspace}"
    cleanUp_rtcSourceWorkspace=false
  fi
  if [ "${cleanUp_rtcSourceSnapshotWorkspace}" != "false" -a -n "${rtcSourceSnapshotWorkspace:-}" ]
  then
    deleteRTCWorkspaces "${rtcSourceSnapshotWorkspace}"
    cleanUp_rtcSourceSnapshotWorkspace=false
  fi
  killRtcDaemonProcesses || true
  if [ "${ourTempTempFolderWeMade}" != "" ]
  then
    robustlyDelete "${ourTempTempFolderWeMade}"
  fi
  robustlyDelete "${gitCredentialsHelperScript}" || true
  robustlyDelete "${gitLocalRepoDir}" || true
  robustlyDelete "${rtcWorkingDir}" || true
  cleanUpRtcTempFiles
  echo "...clean up before exiting now complete."
}
trap cleanUpOnExit EXIT ERR


robustlyDelete "${gitCredentialsHelperScript}" || ( echo "Error: Internal error: Unable to delete file '${gitCredentialsHelperScript}'." >&2 ; exit 1 )
robustlyDelete "${gitLocalRepoDir}" || ( echo "Error: Internal error: Unable to delete folder '${gitLocalRepoDir}'." >&2 ; exit 1 )
robustlyDelete "${rtcWorkingDir}" || ( echo "Error: Internal error: Unable to delete folder '${rtcWorkingDir}'." >&2 ; exit 1 )

# OK, first we need to set up git so we can talk to the remote end
createGitCredentialsHelper "${gitCredentialsHelperScript}" "${arg_gitRepository}" "${arg_gitUsername}" "${arg_gitPassword}"

# Verify that the Git stuff we've been given is valid
if gitReadRemoteRepositry "${arg_gitRepository}" "${gitLocalRepoDir}" "${gitCredentialsHelperScript}"
then
  true
else
  echo "Error: Git repository '${arg_gitRepository}' either does not exist or we are not permitted to see it - you'll need to create it remotely yourself (as an empty repo) and ensure that ${arg_gitUsername} can clone/commit/push to it before this process can populate the repository." >&2
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
echo "Info: Found RTC stream called '${arg_rtcSource}' (${rtcSourceUUID})${rtcSourceLocation}."

# Now find our source snapshot - this might be a UUID or a name.
rtcSourceSnapshotUUID=''
rtcSourceSnapshotName=''
if [ -n "${arg_rtcSourceSnapshot}" ]
then
  if ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "list snapshots" --name "${arg_rtcSourceSnapshot}" --maximum 1000 )
  then
    rtcSourceSnapshotUUID=$(getUniqueRTCUuidFromListOrEmpty "${arg_rtcSourceSnapshot}" "snapshots")
    if [ -n "${rtcSourceSnapshotUUID}" ]
    then
      rtcSourceSnapshotName="${arg_rtcSourceSnapshot}"
    fi
  fi
  if [ -z "${rtcSourceSnapshotUUID}" ] && isRtcUuid "${arg_rtcSourceSnapshot}"
  then
    if ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "show attributes" \
      --snapshot "${arg_rtcSourceSnapshot}" )
    then
      parseRtcSnapshotAttributes rtcSourceSnapshotUUID rtcSourceSnapshotName '' < "${RTCCMDS_TEMP_FILE}"
    fi
  fi
  if [ -z "${rtcSourceSnapshotUUID}" -o -z "${rtcSourceSnapshotName}" ]
  then
    echo "Error: No snapshot called '${arg_rtcSourceSnapshot}' was found in stream '${arg_rtcSource}'." >&2
    exit 1
  fi
  echo "Info: Found RTC source snapshot called '${rtcSourceSnapshotName}' (${rtcSourceSnapshotUUID})."
fi

# Find our marker snapshot - this must be a name not a UUID.
if [ "${arg_rtcMarkerSnapshotEnabled}" = "true" ]; then
  ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "list snapshots" --name "${arg_rtcMarkerSnapshot}" --maximum 1000 )
  rtcMarkerSnapshotUUID=$(getUniqueRTCUuidFromListOrEmpty "${arg_rtcMarkerSnapshot}" "snapshots")
  if [ -n "${rtcMarkerSnapshotUUID}" ]
  then
    echo "Info: Found RTC snapshot '${arg_rtcMarkerSnapshot}' (${rtcMarkerSnapshotUUID}), so this looks like it's not the first time we've done this."
  else
    echo "Info: No RTC snapshot '${arg_rtcMarkerSnapshot}' found, so this looks like it's the first time we've done this."
  fi
else
  rtcMarkerSnapshotUUID=''
fi

if gitLocalRepositoryIsNotEmpty "${gitLocalRepoDir}"
then
  # git repo exists and isn't empty
  if [ -z "${arg_branchAtGitCommit}" ]
  then
    # we're doing a primary migration not a snapshot branch, so RTC's state should match git's.
    if [ "${arg_rtcMarkerSnapshotEnabled}" != "true" ]; then
      echo "Error: rtcMarkerSnapshotEnabled is false so we can only do from-empty migrations BUT git repository '${arg_gitRepository}' is not empty." >&2
      exit 1
    fi
    if [ -z "${rtcMarkerSnapshotUUID}" ]
    then
      echo "Error: Git repository '${arg_gitRepository}' already exists BUT RTC had no record of any previous migration - there's no snapshot called ${arg_rtcMarkerSnapshot}." >&2
      exit 1
    fi
    echo "Git repository '${arg_gitRepository}' already exists and contents should match RTC snapshot ${rtcMarkerSnapshotUUID}."
  else
    echo "Git repository '${arg_gitRepository}' already exists and commit ${arg_branchAtGitCommit} should match RTC changeset ${arg_branchAtRtcChangeset}."
  fi
  gitSetupNonEmptyLocalRepository \
    "${arg_gitRepository}" \
    "${gitLocalRepoDir}"   \
    "${gitCredentialsHelperScript}" \
    "${arg_gitBranchName}" \
    "${arg_branchAtGitCommit}" \
    "${arg_gitUsername}" \
    "Migration-from-RTC"
  echo "Info: Obtained local clone of git repository '${arg_gitRepository}' now exists."
else
  # git repo exists but is empty
  if [ -n "${rtcMarkerSnapshotUUID}" ]
  then
    echo "Error: Git repository '${arg_gitRepository}' is empty BUT RTC has a record a previous migration - there's a snapshot called '${arg_rtcMarkerSnapshot}' (${rtcMarkerSnapshotUUID})." >&2
    exit 1
  fi
  if [ -n "${arg_branchAtGitCommit}" ]
  then
    echo "Error: Git repository '${arg_gitRepository}' is empty BUT argument branchAtGitCommit (${arg_branchAtGitCommit}) was specified." >&2
    exit 1
  fi
  if [ -z "${arg_gitBranchName}" ]
  then
    echo "Git repository '${arg_gitRepository}' is empty; using default branch of master."
    arg_gitBranchName=master
  else
    echo "Git repository '${arg_gitRepository}' is empty."
  fi
  gitSetupEmptyLocalRepository \
    "${arg_gitRepository}" \
    "${gitLocalRepoDir}" \
    "${gitCredentialsHelperScript}" \
    "${arg_gitBranchName}" \
    "${arg_gitUsername}" \
    "Migration-from-RTC"
  echo "Info: Created local clone of git repository '${arg_gitRepository}', albeit empty."
fi
echo "Info: Contents of the loaded git repository:"
listNoteworthyContents "${gitLocalRepoDir}" | sed 's,^,  ,g'

# Set up RTC

# Create the "SOURCE_WORKSPACE" which represents what we want to end up being in Git.
# This is a workspace with a flow-target from the actual source stream we're migrating, either representing the latest code (default)
# or optionally set to be a snapshot of earlier history.
# If we're aiming at a snapshot of earlier history AND we're branching from a particular place in history, we need to create a workspace
# that's _just_ that fork and then point our SOURCE_WORKSPACE at that instead.
if [ -n "${arg_branchAtRtcChangeset}" -a -n "${rtcSourceSnapshotUUID}" ]
then
  rtcSourceSnapshotWorkspace="migrateRTC2Git_${rtcSourceUUID}_$$_ss"
  deleteRTCWorkspaces "${rtcSourceSnapshotWorkspace}"
  cleanUp_rtcSourceSnapshotWorkspace=true
  ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "create workspace" \
    --description "rtc2gitcli snapshot workspace being used to migrate ${arg_rtcSource} (${rtcSourceUUID}) snapshot '${rtcSourceSnapshotName}' (${rtcSourceSnapshotUUID}) to ${arg_gitRepository}" \
    --snapshot "${rtcSourceSnapshotUUID}" "${rtcSourceSnapshotWorkspace}" )
  rtcSourceSnapshotWorkspaceUUID=$(getUniqueRTCUuidFromListOrEmpty "${rtcSourceSnapshotWorkspace}" "workspaces created")
  if [ -n "${rtcSourceSnapshotWorkspaceUUID}" ]
  then
    echo "Info: Created new RTC workspace '${rtcSourceSnapshotWorkspace}' (${rtcSourceSnapshotWorkspaceUUID}), based on RTC stream '${arg_rtcSource}' (${rtcSourceUUID}) snapshot '${rtcSourceSnapshotName}' (${rtcSourceSnapshotUUID})."
  else
    echo "Error: Internal error: Unable to find new RTC workspace '${rtcSourceWorkspace}' that we just created." >&2
    echo "RTC command output was:" >&2
    echo "-----" >&2
    cat "${RTCCMDS_TEMP_FILE}" >&2
    echo "-----" >&2
    exit 1
  fi
else
  rtcSourceSnapshotWorkspaceUUID=''
fi

# Now we've got a source-snapshot workspace if we need one,
# we can create the SOURCE_WORKSPACE itself.
rtcSourceWorkspace="migrateRTC2Git_${rtcSourceUUID}_$$_s"
deleteRTCWorkspaces "${rtcSourceWorkspace}"
cleanUp_rtcSourceWorkspace=true
if [ -z "${rtcSourceSnapshotWorkspaceUUID}" ]
then
  ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "create workspace" \
    --description "rtc2gitcli SOURCE_WORKSPACE being used to migrate ${arg_rtcSource} (${rtcSourceUUID}) to ${arg_gitRepository}" \
    --stream "${rtcSourceUUID}" \
    "${rtcSourceWorkspace}" )
else
  ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "create workspace" \
    --description "rtc2gitcli SOURCE_WORKSPACE being used to migrate ${arg_rtcSource} (${rtcSourceUUID}) to ${arg_gitRepository}" \
    --stream "${rtcSourceSnapshotWorkspaceUUID}" \
    "${rtcSourceWorkspace}" )
fi
rtcSourceWorkspaceUUID=$(getUniqueRTCUuidFromListOrEmpty "${rtcSourceWorkspace}" "workspaces created")
if [ -n "${rtcSourceWorkspaceUUID}" ]
then
  if [ -n "${arg_branchAtRtcChangeset}" -a -n "${rtcSourceSnapshotUUID}" ]
  then
    echo "Info: Created new RTC workspace '${rtcSourceWorkspace}' (${rtcSourceWorkspaceUUID}), based on RTC stream '${arg_rtcSource}' (${rtcSourceUUID}) snapshot '${rtcSourceSnapshotName}' (${rtcSourceSnapshotUUID})."
  else
    echo "Info: Created new RTC workspace '${rtcSourceWorkspace}' (${rtcSourceWorkspaceUUID}), based on RTC stream '${arg_rtcSource}' (${rtcSourceUUID})."
  fi
else
  echo "Error: Internal error: Unable to find new RTC workspace '${rtcSourceWorkspace}' that we just created." >&2
  echo "RTC command output was:" >&2
  echo "-----" >&2
  cat "${RTCCMDS_TEMP_FILE}" >&2
  echo "-----" >&2
  exit 1
fi
if [ -n "${rtcSourceSnapshotWorkspaceUUID}" ]
then
  ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "set flowtarget" \
    --flow-direction i \
    "${rtcSourceWorkspaceUUID}" \
    "${rtcSourceSnapshotWorkspaceUUID}" )
else
  ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "set flowtarget" \
    --flow-direction i \
    "${rtcSourceWorkspaceUUID}" \
    "${rtcSourceUUID}" )
fi
if [ -z "${arg_branchAtRtcChangeset}" -a -n "${rtcSourceSnapshotUUID}" ]
then
  ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "set component" \
    --all \
    --no-local-refresh \
    --overwrite-uncommitted \
    --source "${rtcSourceUUID}" \
    --nobackup "${rtcSourceWorkspaceUUID}" \
    snapshot \
    "${rtcSourceSnapshotUUID}" )
  echo "Info: Limiting RTC workspace '${rtcSourceWorkspace}' (${rtcSourceWorkspaceUUID}) to RTC snapshot '${rtcSourceSnapshotName}' (${rtcSourceSnapshotUUID})."
fi

# Create the "TARGET_WORKSPACE" which represents what is currently in Git, but with incoming changes from the SOURCE_WORKSPACE.
# This is a workspace with a flow-target from the SOURCE_WORKSPACE, initially being empty (if this is a new migration) or with contents equal to ${arg_rtcMarkerSnapshot} (rtcMarkerSnapshotUUID)
rtcTargetWorkspace="migrateRTC2Git_${rtcSourceUUID}_$$_t"
deleteRTCWorkspaces "${rtcTargetWorkspace}"
cleanUp_rtcTargetWorkspace=true
( cd "${ourBaseDirectory}" && runScmExpectingSuccess "create workspace" \
  --description "rtc2gitcli TARGET_WORKSPACE being used to migrate ${arg_rtcSource} (${rtcSourceUUID}) to ${arg_gitRepository}" \
  --stream "${rtcSourceWorkspaceUUID}" \
  "${rtcTargetWorkspace}" )
rtcTargetWorkspaceUUID=$(getUniqueRTCUuidFromListOrEmpty "${rtcTargetWorkspace}" "workspaces created")
if [ -z "${rtcTargetWorkspaceUUID}" ]
then
  echo "Error: Internal error: Unable to find new RTC workspace '${rtcTargetWorkspace}' that we just created." >&2
  echo "RTC command output was:" >&2
  echo "-----" >&2
  cat "${RTCCMDS_TEMP_FILE}" >&2
  echo "-----" >&2
  exit 1
fi
if [ -n "${rtcMarkerSnapshotUUID}" ]
then
  # we need to set up our target workspace to initially contain our rtcMarkerSnapshot
  ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "set component" \
    --all \
    --no-local-refresh \
    --overwrite-uncommitted \
    --source "${rtcSourceUUID}" \
    --nobackup \
    "${rtcTargetWorkspaceUUID}" \
    snapshot \
    "${rtcMarkerSnapshotUUID}" )
  echo "Info: Created new RTC workspace '${rtcTargetWorkspace}' (${rtcTargetWorkspaceUUID}), initially containing RTC snapshot '${arg_rtcMarkerSnapshot}' (${rtcMarkerSnapshotUUID})."
elif [ -n "${arg_branchAtRtcChangeset}" ]
then
  # we need to rewind history until branchAtRtcChangeset
  ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "list changesets" \
    --maximum all \
    --workspace "${rtcTargetWorkspaceUUID}" )
  # find desired changeset (fail if not found)
  changesetsWeNeedToRemove=$(getChangesetsThatPreceed "${arg_branchAtRtcChangeset}") || (cat "${RTCCMDS_TEMP_FILE}" ; echo "Error: No changeset with UUID ${arg_branchAtRtcChangeset} found in history for stream ${arg_rtcSource}" ; exit 1) >&2 || exit 1
  if [ -n "${changesetsWeNeedToRemove}" ]
  then
    # remove all the changesets we don't want
    ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "discard" \
      --no-local-refresh \
      --overwrite-uncommitted \
      --workspace "${rtcTargetWorkspaceUUID}" \
      ${changesetsWeNeedToRemove} )
  fi
else
  # we need to set up our target workspace to initially be at "the beginning of time"
  # ...and it seems that RTC-speak for "the beginning of time" is 'Initial Baseline' (it doesn't understand a baseline id of "1").
  ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "set component" \
    --all \
    --no-local-refresh \
    --overwrite-uncommitted \
    --baseline 'Initial Baseline' \
    --nobackup \
    "${rtcTargetWorkspaceUUID}" \
    stream \
    "${rtcSourceUUID}" )
  echo "Info: Created new RTC workspace '${rtcTargetWorkspace}' (${rtcTargetWorkspaceUUID}), initially empty."
fi
( cd "${ourBaseDirectory}" && runScmExpectingSuccess "set flowtarget" \
  --flow-direction i \
  "${rtcTargetWorkspaceUUID}" \
  "${rtcSourceWorkspaceUUID}" )

#echo
#echo
#echo
#echo "** ATTENTION ** : Type something to continue."
#echo "  RTC 'source' ${rtcSourceWorkspace} (${rtcSourceWorkspaceUUID}) should be set to specify what we are working towards."
#echo "  RTC 'target' ${rtcTargetWorkspace} (${rtcTargetWorkspaceUUID}) should be set to specify what we are starting from."
#echo "  Git repo ${arg_gitRepository} is loaded at ${gitLocalRepoDir} and should be the same as RTC 'target' workspace."
#echo
#read ignored

# We've got everything in place server-side in RTC, now we get the code locally ...
mkdir -p "${rtcWorkingDir}"
(
  loadArgs=( '--directory' "${rtcWorkingDir}" )
  if [ "${arg_rtcIncludeRoot}" = "true" ]
  then
    loadArgs+=( '--include-root' )
  fi
  #  loadArgs+=( '--all' )
  #  loadArgs+=( '--force' )
  #  loadArgs+=( '--allow' )
  loadArgs+=( "${rtcTargetWorkspaceUUID}" )
  cd "${ourBaseDirectory}" && runScmExpectingSuccess "load" "${loadArgs[@]}"
)

echo "Info: Contents of the loaded RTC sandbox:"
listNoteworthyContents "${rtcWorkingDir}" | sed 's,^,  ,g'

# ... merge our git stuff into it ...
copyGitOnlyFilesIntoRtcWorkspace "${gitLocalRepoDir}" "${rtcWorkingDir}"
# ...so now we have a functioning git repository in the RTC sandbox.

migrationPropertiesFile="$(pwd)/migration.properties"
if [ "${arg_maxBatchSize}" -gt 0 ]
then
  migrationMaxChangesetsArgsString="--maxChangesets ${arg_maxBatchSize}"
else
  migrationMaxChangesetsArgsString=""
fi
migrationComplete="false"
migrationBatchCount=0

while [ "${migrationComplete}" != "true" ]
do
  migrationBatchCount=$(( migrationBatchCount + 1 ))
  if [ "${arg_maxBatchSize}" -gt 0 ]
  then
    migrationBatchCountString=" (batch ${migrationBatchCount})"
  else
    migrationBatchCountString=""
  fi

  # Rational say that you should use the "lscm" daemon-based CLI to counteract
  # the poor performance from scm.
  # Rational claim that lscm does everything that scm does.
  # However, there's a bug in RTC whereby "lscm" does not include any plugins
  # that were added to "scm" (despite lscm running the scm code), so we can't use
  # lscm when running the migration.
  # Worse, there's a bug in RTC's "lscm" demon code whereby it locks resources
  # and causes "scm" commands to crash (you can't use a mix of lscm and scm even
  # though that's what you have to do).
  # ...and the RTC CLI command to shut down the daemon often fails, so we have
  # to use our own kill method to do it properly.
  # TL;DR: We need to kill the daemon before we can do the migration itself.
  killRtcDaemonProcesses
  robustlyDelete "${RTCCMDS_TEMP_FILE}"

  # ... do the migration, which builds up commits in git.
  echo "Info: Migrating RTC changes to local git clone..."
  (
    cd "${ourBaseDirectory}"
    RTC_SCM_RETRIES=0
    RTC_EXE_NAME=scm
    . "${SCRIPT_DIR}/setRtcVariables.sh" || (echo "Something went wrong in ${SCRIPT_DIR}/setRtcVariables.sh" >&2 ; exit 1)
    migrateArgs=( '--directory' "${rtcWorkingDir}" )
    migrateArgs+=( '--migrationProperties' "${migrationPropertiesFile}" )
    migrateArgs+=( '--sourceStream' "${rtcSourceUUID}" )
    migrateArgs+=( '--initialCommitDatestamp' '2000-01-01T00:00:01' )
    migrateArgs+=( '--autocrlf' 'false' )
    if [ -n "${rtcMarkerSnapshotUUID}" -o "${migrationBatchCount}" -gt 1 -o -n "${arg_branchAtRtcChangeset}" ]
    then
      migrateArgs+=( ${migrationMaxChangesetsArgsString} '--update' )
    else
      migrateArgs+=( ${migrationMaxChangesetsArgsString} )
    fi
    if [ "${arg_rtcIncludeRoot}" = "true" ]
    then
      migrateArgs+=( '--include-root' )
    fi
    migrateArgs+=( "${rtcSourceWorkspaceUUID}" )
    migrateArgs+=( "${rtcTargetWorkspaceUUID}" )
    runScmExpectingSuccess --verbose "migrate-to-git" "${migrateArgs[@]}"
  )
# We don't need to cat ${RTCCMDS_TEMP_FILE} as we used the --verbose flag to ensure it's already output.
#  cat "${RTCCMDS_TEMP_FILE}"

#  if grep 'There was a \[FAILURE\]' "${RTCCMDS_TEMP_FILE}" >/dev/null
#  then
#    echo "Error:${migrationBatchCountString} Migration failed."
    outputAllRtcLogs || true
#    exit 1
#  fi
  if [ "${arg_maxBatchSize}" -gt 0 ]
  then
    migrationComplete=$(rtcMigrationIsComplete "${rtcSourceWorkspaceUUID}" "${rtcTargetWorkspaceUUID}" "${rtcWorkingDir}")
    if [ "${migrationComplete}" = "true" ]
    then
      echo "Info:${migrationBatchCountString} Migration completed - All RTC changes are now in local git clone."
    else
      echo "Info:${migrationBatchCountString} Partial migration done - Some RTC changes are now in local git clone."
    fi
  else
    migrationComplete="true"
    echo "Info:${migrationBatchCountString} Migration completed - RTC changes are now in local git clone."
  fi



  # If that succeeded, we then need to make a note of what we did and save the results.

  gitStatus=$(gitGetCurrentCommit "${rtcWorkingDir}")
  if [ "${arg_rtcMarkerSnapshotEnabled}" = "true" ]; then
    # Create a new RTC marker snapshot in our workspace
    ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "create snapshot" \
      --description "Migrated to ${arg_gitRepository} ${gitStatus}" \
      --overwrite-uncommitted \
      --name "${arg_rtcMarkerSnapshot}" \
      "${rtcTargetWorkspaceUUID}" )
    newRtcMarkerSnapshotUUID=$(getUniqueRTCUuidFromMultiTypeListOrEmpty 'Snapshot' "${arg_rtcMarkerSnapshot}")
    if [ -n "${newRtcMarkerSnapshotUUID}" ]
    then
      echo "Info:${migrationBatchCountString} Created new RTC marker snapshot '${arg_rtcMarkerSnapshot}' (${newRtcMarkerSnapshotUUID})."
    else
      echo "Error:${migrationBatchCountString} Internal error: Unable to find new RTC marker snapshot '${arg_rtcMarkerSnapshot}' that we just created." >&2
      echo "RTC command output was:" >&2
      echo "-----" >&2
      cat "${RTCCMDS_TEMP_FILE}" >&2
      echo "-----" >&2
      exit 1
    fi
  else
    newRtcMarkerSnapshotUUID=''
  fi

  #echo
  #echo
  #echo
  #echo "** ATTENTION ** : Type something to continue."
  #echo "  RTC 'source' ${rtcSourceWorkspace} (${rtcSourceWorkspaceUUID}) should have been set to specify what we are working towards."
  #if [ "${migrationComplete}" = "true" ]
  #then
  #  echo "  RTC 'target' ${rtcTargetWorkspace} (${rtcTargetWorkspaceUUID}) should now contain the same."
  #  echo "  Git repo at ${gitLocalRepoDir} should == RTC 'target' == RTC 'source' workspace."
  #else
  #  echo "  RTC 'target' ${rtcTargetWorkspace} (${rtcTargetWorkspaceUUID}) should now be less different."
  #  echo "  Git repo at ${gitLocalRepoDir} should == RTC 'target'."
  #fi
  #echo "  Git repo ${arg_gitRepository} should be the same as ${gitLocalRepoDir} with some un-pushed changes."
  #echo
  #read ignored


  # If we've got this far, we've got a heap of commits in git to push
  echo "Info:${migrationBatchCountString} Pushing migrated data to git repository '${arg_gitRepository}' ..."
  gitPushToRemote \
    "${arg_gitRepository}" \
    "${rtcWorkingDir}" \
    "${gitCredentialsHelperScript}" \
    "${arg_gitBranchName}"
  echo "Info:${migrationBatchCountString} Push complete - data is now in git."


  # and if that succeeded then we need to update our records in RTC so we don't try that again.
  if [ -n "${newRtcMarkerSnapshotUUID}" ]
  then
    # We do that by moving the snapshot we created earlier to the stream so everyone sees it...
    echo "Info: Setting marker snapshot in RTC to migration endpoint ..."
    ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "set attributes" \
      --snapshot "${newRtcMarkerSnapshotUUID}" \
      --ownedby "${rtcSourceUUID}" )
    # ... and removing the old snapshot (if any).
    if [ -n "${rtcMarkerSnapshotUUID}" ]
    then
      # RTC doesn't let us delete a snapshot from a stream (even if we put it there) ...
      # ... but it does let us move a snapshot frmo a workspace and delete it from there.
      ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "set attributes" \
        --snapshot "${rtcMarkerSnapshotUUID}" \
        --ownedby "${rtcTargetWorkspaceUUID}" )
      ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "delete snapshot" \
        "${rtcTargetWorkspaceUUID}" \
        "${rtcMarkerSnapshotUUID}" )
      echo "Info:${migrationBatchCountString} Removed old RTC marker snapshot ${rtcMarkerSnapshotUUID}."
    fi
    echo "Info:${migrationBatchCountString} RTC marker snapshot complete - RTC and Git are now in sync."
  fi

  # if we limited stuff and there's more to do
  if [ "${migrationComplete}" != "true" ]
  then
    # make sure our next iteration moves the snapshot we just created
    rtcMarkerSnapshotUUID="${newRtcMarkerSnapshotUUID}"
    newRtcMarkerSnapshotUUID=''
  fi

done

if [ -n "${arg_gitTagName}" ]
then
  if [ -z "${arg_gitTagDescription}" ];then
    if [ -n "${arg_rtcMarkerSnapshot}" -a -n "${newRtcMarkerSnapshotUUID}" ]; then
      arg_gitTagDescription="Migrated from ${rtcUrl} stream '${arg_rtcSource}' (${rtcSourceUUID}) up to snapshot '${arg_rtcMarkerSnapshot}' (${newRtcMarkerSnapshotUUID})."
    elif [ -n "${rtcSourceSnapshotName}" -a -n "${rtcSourceSnapshotUUID}" ]; then
      arg_gitTagDescription="Migrated from ${rtcUrl} stream '${arg_rtcSource}' (${rtcSourceUUID}) snapshot '${rtcSourceSnapshotName}' (${rtcSourceSnapshotUUID})."
    elif [ -n "${newRtcMarkerSnapshotUUID}" ]; then
      arg_gitTagDescription="Migrated from ${rtcUrl} stream '${arg_rtcSource}' (${rtcSourceUUID}) up to snapshot (${newRtcMarkerSnapshotUUID})."
    else
      arg_gitTagDescription="Migrated from ${rtcUrl} stream '${arg_rtcSource}' (${rtcSourceUUID})."
    fi
  fi
  gitCreateOrOverwriteTag \
    "${rtcWorkingDir}" \
    "${arg_gitTagName}" \
    "${arg_gitTagDescription}" \
    "${arg_gitTagTimestamp}"
  gitPushToRemote \
    "${arg_gitRepository}" \
    "${rtcWorkingDir}" \
    "${gitCredentialsHelperScript}" \
    "${arg_gitBranchName}"
fi
echo "Info: Contents of the migrated repository:"
listNoteworthyContents "${rtcWorkingDir}" | sed 's,^,  ,g'


# Lastly we clean up
echo "Info: Cleaning up..."
( cd "${ourBaseDirectory}" && runScmExpectingSuccess "delete workspace" \
  "${rtcTargetWorkspaceUUID}" )
cleanUp_rtcTargetWorkspace=false
( cd "${ourBaseDirectory}" && runScmExpectingSuccess "delete workspace" \
  "${rtcSourceWorkspaceUUID}" )
cleanUp_rtcSourceWorkspace=false
if [ -n "${rtcSourceSnapshotWorkspaceUUID}" ]
then
  ( cd "${ourBaseDirectory}" && runScmExpectingSuccess "delete workspace" \
  "${rtcSourceSnapshotWorkspaceUUID}" )
  cleanUp_rtcSourceSnapshotWorkspace=false
fi
killRtcDaemonProcesses
robustlyDelete "${rtcWorkingDir}"
robustlyDelete "${gitLocalRepoDir}"
robustlyDelete "${gitCredentialsHelperScript}"
cleanUpRtcTempFiles
trap - EXIT ERR
echo "Info: Clean up complete - we are done here."
