#!/bin/bash
#
# Script that lets us edit settings on a GitHub repo.
# e.g. granting/removing access
# e.g. turning flags on and off.

# Ensure we fail if we reference a variable we've not set
set -u
# Ensure we fail if we execute a command that returns a non-zero exit code
set -e
# Ensure we fail if we execute a command that has a failure in any step of a pipeline
set -o pipefail

SCRIPT_NAME=`basename ${0}`

# Setup variables to hold the parsed arguments.
default_apiUrl="https://api.github.com/"
default_gitUsername=""
default_gitPassword=""
default_owner=""
default_repo=""
default_moveFromOwner=""
default_defaultBranch=""
default_description=""
default_homepage=""
default_private=""
default_issues=""
default_projects=""
default_wiki=""
default_allowSquashMerge=""
default_allowMergeCommit=""
default_allowRebaseMerge=""

# Outputs CLI usage help text to stdout.
doHelp() {
  cat <<ENDHELP
Syntax:
  ${SCRIPT_NAME} [--apiUrl ...] [--gitUsername ...] [--gitPassword ...] --owner ... --repo ... [--defaultBranch ...] [--description ...] [--homepage ...] [--private ...] [--issues ...] [--projects ...] [--wiki ...] [--allowSquashMerge ...] [--allowMergeCommit ...] [--allowRebaseMerge] [--teamCollaborator ...=...] [--userCollaborator ...=...]
Where
  --apiUrl "url" (Optional)
    Specifies the GitHub API URL.
    Defaults to "${default_apiUrl}".
  --gitUsername "username" (Optional)
  --gitPassword "password" (Optional)
    Specifies the credentials to be used when logging into git to clone and push.
    Note: For security reasons, it is better to use the --gitPasswordEnvName form (see below).
    Defaults to using environment variables GIT_USERNAME and GIT_PASSWORD.
  --owner ["gitOrganisationName"|"gitUserName"]
    Specifies the "owner" (organisation or user) of the repository.
    Note: If moveFromOwner is used then --owner specifies the new owner.
  --repo "name"
    Specifies the name of the git repository within the user/org it belongs to.
All remaining arguments are optional and default to "no change":
  --moveFromOwner ["gitOrganisationName"|"gitUserName"]
    Specifies that the repository be transferred from the owner specified by this argument so that
    it now belongs to the owner specified by --owner instead.
  --defaultBranch "branchName"
    Sets the default branch for the repository.
  --description "Repository description"
    Specifies the description to be set on the repository.
  --homepage "url"
    Specifies the homepage (that documents the repository) for the repository.
  --private true|false
    If true, the repository will be made private instead of public.
  --issues true|false
    If true, the repository will allow GitHub issues.
  --projects true|false
    If true, the repository will support having GitHub projects.
  --wiki true|false
    If true, the repository will have a GitHub wiki.
  --allowSquashMerge true|false
  --allowMergeCommit true|false
  --allowRebaseMerge true|false
    When true, permits this kind of merging of pull requests.
  --teamCollaborator teamName=[none|pull|push|admin]
  --userCollaborator userName=[none|pull|push|admin]
    Grants/edits/removes access to this repository for the given team or user.
    Team names must be the GitHub "slug" for that team.
  --xxxEnvName "environmentVariableName"
    For any/all of the above arguments, specifies that the value for argument 'xxx' is to be read from
    the specified environment variable.
Description:
  Changes settings on an existing GitHub repository.
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
  local envName="${2-}"
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

# $1 = --foo
# $2 = value for arg_foo
handleArgAndMultiValue() {
  local argName="$(expr ${1} : '--\(.*\)$')"
  shift
  processArgNameAndMultiValue "${argName}" "$@"
}

# $1 = --fooEnvName
# $2 = name of env var that contains key=value for arg_foo
handleArgAndMultiValueEnvName() {
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
  processArgNameAndMultiValue "${argName}" "${argValueOrEmpty}"
}

# $1 = argName
# $2 = value of the form key=value
processArgNameAndMultiValue() {
  local argName="${1}"
  if [ "${2-}" = "" -a "${2-X}" = "X"  ]
  then
    echo "Error: Missing value for argument ${argName}." >&2
    return 1
  fi
  local argKeyEqualsValue="${2}"
  local associativeArrayName="arg_${argName}s"
  if ! expr "${argKeyEqualsValue}" : '[^=][^=]*=[^=]*$' >/dev/null
  then
    echo "Error: Invalid value '${argKeyEqualsValue}' for argument ${argName} - must be of the form <key>=<value>." >&2
    return 1
  fi
  local argKey="$(   expr ${argKeyEqualsValue} : '\([^=][^=]*\)=[^=]*$' )"
  local argValue="$( expr ${argKeyEqualsValue} : '[^=][^=]*=\([^=]*\)$' )"
  # Note: Ideally, we'd use "local -n ..." but (quoting bash manpage)
  # "The -n attribute cannot be applied to array variables."
  eval "${associativeArrayName}[${argKey}]='${argValue}'"
}

# $1 = value that must be 'true' or 'false'
isTrueOrFalse() {
  [ "$1" = "true" -o "$1" = "false" ]
}

# $1 = value that must be empty, 'true' or 'false'
isTrueOrFalseOrEmpty() {
  [ "$1" = "" ] || isTrueOrFalse "$1"
}

# $1 = value that must be 'none', 'pull', 'push' or 'admin'.
isNonePullPushOrAdmin() {
  [ "$1" = "none" -o "$1" = "pull" -o "$1" = "push" -o "$1" = "admin" ]
}



# $1 = team name "slug", i.e. githubified name (no spaces etc).
# ... although a team name will also suffice.
# Prefix with orgName/ for teams outside our target org.
#
# exit code 0 on success, stdout = team id
# exit code non-zero on failure
declare -A cachedGithubOrgTeamToId
declare -A haveCachedGithubTeamsFromOrg
getGithubTeamIdFromNameSlug() {
  local combinedTeamName="$1"
  if expr "${combinedTeamName}" : '..*/..*$' >/dev/null
  then
    local orgName="$(  expr ${combinedTeamName} : '\(..*\)/..*$' )"
    local teamName="$( expr ${combinedTeamName} : '..*/\(..*\)$' )"
  else
    local orgName="${arg_owner}"
    local teamName="${combinedTeamName}"
  fi
  if [ "${haveCachedGithubTeamsFromOrg[${orgName}]:-}" != "true" ]
  then
    # We've not parsed this org's teams yet, so we need to do so.
    # We do all teams in one job lot as github rate-limits API calls
    # but doesn't care how big the call is.
    local apiUrl="${arg_apiUrl}orgs/${orgName}/teams"
    local lastName=""
    local lastId=""
    local lastSlug=""
    local line
    while read line
    do
      if expr "${line}" : ' *"[^"][^"]*":  *..*,$' >/dev/null
      then
        local fieldName=`expr  "${line}" : ' *"\([^"][^"]*\)":  *..*,$'`
        if [ "${fieldName}" = "name" ]
        then
          local fieldValue=`expr "${line}" : ' *"[^"][^"]*":  *"\(..*\)",$'`
          lastName="${fieldValue}"
        fi
        if [ "${fieldName}" = "id" ]
        then
          local fieldValue=`expr "${line}" : ' *"[^"][^"]*":  *\(..*\),$'`
          lastId="${fieldValue}"
        fi
        if [ "${fieldName}" = "slug" ]
        then
          local fieldValue=`expr "${line}" : ' *"[^"][^"]*":  *"\(..*\)",$'`
          lastSlug="${fieldValue}"
        fi
      else
        if [ -n "${lastId}" -a -n "${lastName}" -a -n "${lastSlug}" ]
        then
          cachedGithubOrgTeamToId["${orgName}/${lastName}"]="${lastId}"
          cachedGithubOrgTeamToId["${orgName}/${lastSlug}"]="${lastId}"
        fi
        lastName=""
        lastId=""
        lastSlug=""
      fi
    done < <(curl --header "Authorization: token ${arg_gitPassword}" --silent "${apiUrl}")
    haveCachedGithubTeamsFromOrg["${orgName}"]="true"
  fi
  echo "${cachedGithubOrgTeamToId[${orgName}/${teamName}]}"
}

# stdout = what to send to github's API
# exit code 0 if it's worth sending, 123 if there's nothing to send.
githubEditRepositoryPostRequestPayload() {
  local changes='false'
  echo '{'
  if [ -n "${arg_description}" ]
  then
    echo "  \"description\": \"${arg_description}\","
    changes='true'
  fi
  if [ -n "${arg_homepage}" ]
  then
    echo "  \"homepage\": \"${arg_homepage}\","
    changes='true'
  fi
  if [ -n "${arg_private}" ]
  then
    echo "  \"private\": ${arg_private},"
    changes='true'
  fi
  if [ -n "${arg_issues}" ]
  then
    echo "  \"has_issues\": ${arg_issues},"
    changes='true'
  fi
  if [ -n "${arg_projects}" ]
  then
    echo "  \"has_projects\": ${arg_projects},"
    changes='true'
  fi
  if [ -n "${arg_wiki}" ]
  then
    echo "  \"has_wiki\": ${arg_wiki},"
    changes='true'
  fi
  if [ -n "${arg_defaultBranch}" ]
  then
    echo "  \"default_branch\": ${arg_defaultBranch},"
    changes='true'
  fi
  if [ -n "${arg_allowSquashMerge}" ]
  then
    echo "  \"allow_squash_merge\": ${arg_allowSquashMerge},"
    changes='true'
  fi
  if [ -n "${arg_allowMergeCommit}" ]
  then
    echo "  \"allow_merge_commit\": ${arg_allowMergeCommit},"
    changes='true'
  fi
  if [ -n "${arg_allowRebaseMerge}" ]
  then
    echo "  \"allow_rebase_merge\": ${arg_allowRebaseMerge},"
    changes='true'
  fi
  echo "  \"name\": \"${arg_repo}\""
  echo '}'
  if [ "${changes}" = "true" ]
  then
    return 0
  else
    return 123
  fi
}
isEditNeeded() {
  if githubEditRepositoryPostRequestPayload >/dev/null
  then
    return 0
  else
    return 1
  fi
}
githubEditRepo() {
  local apiUrl="${arg_apiUrl}repos/${arg_owner}/${arg_repo}"
  echo "HTTP PATCH to ${apiUrl}"
  githubEditRepositoryPostRequestPayload
  githubEditRepositoryPostRequestPayload | curl --header "Authorization: token ${arg_gitPassword}" --silent --request PATCH --fail --data '@-' "${apiUrl}"
}

# stdout = what to send to github's API
# exit code 0 if it's worth sending, 123 if there's nothing to send.
githubMoveRepositoryPostRequestPayload() {
  if [ -n "${arg_moveFromOwner}" ]
  then
    echo '{'
    echo "  \"new_owner\": \"${arg_owner}\""
    echo '}'
    return 0
  else
    return 123
  fi
}
isMoveNeeded() {
  if githubMoveRepositoryPostRequestPayload >/dev/null
  then
    return 0
  else
    return 1
  fi
}
githubMoveRepo() {
  local apiUrl="${arg_apiUrl}repos/${arg_moveFromOwner}/${arg_repo}/transfer"
  echo "HTTP POST to ${apiUrl}"
  githubMoveRepositoryPostRequestPayload
  githubMoveRepositoryPostRequestPayload | curl --header "Authorization: token ${arg_gitPassword}" --silent --request POST --fail --data '@-' "${apiUrl}"
}

# $1 = permission, e.g. 'pull', 'push', 'admin'
githubCollaborationPutRequestPayload() {
  echo '{'
  echo "  \"permission\": \"${1}\""
  echo '}'
}
# $1 = username
# $2 = level of access, i.e. 'none', 'pull', 'push' or 'admin'.
setUserCollaboration() {
  local username="$1"
  local permission="$2"
  local apiUrl="${arg_apiUrl}repos/${arg_owner}/${arg_repo}/collaborators/${username}"
  if [ "${permission}" != "none" ]
  then
    echo "HTTP PUT to ${apiUrl}"
    githubCollaborationPutRequestPayload "${permission}"
    githubCollaborationPutRequestPayload "${permission}" | curl --header "Authorization: token ${arg_gitPassword}" --silent --request PUT --fail --data '@-' "${apiUrl}"
  else
    echo "HTTP DELETE to ${apiUrl}"
    curl --header "Authorization: token ${arg_gitPassword}" --silent --request DELETE --fail "${apiUrl}"
  fi
}
# $1 = teamId
# $2 = level of access, i.e. 'none', 'pull', 'push' or 'admin'.
setTeamIdCollaboration() {
  local teamId="$1"
  local permission="$2"
  local apiUrl="${arg_apiUrl}teams/${teamId}/repos/${arg_owner}/${arg_repo}"
  if [ "${permission}" != "none" ]
  then
    echo "HTTP PUT to ${apiUrl}"
    githubCollaborationPutRequestPayload "${permission}"
    githubCollaborationPutRequestPayload "${permission}" | curl --header "Authorization: token ${arg_gitPassword}" --silent --request PUT --fail --data '@-' "${apiUrl}"
  else
    echo "HTTP DELETE to ${apiUrl}"
    curl --header "Authorization: token ${arg_gitPassword}" --silent --request DELETE --fail "${apiUrl}"
  fi
}



# Setup variables to hold the parsed arguments.
arg_apiUrl="${default_apiUrl}"
arg_owner="${default_owner}"
arg_repo="${default_repo}"
arg_moveFromOwner="${default_moveFromOwner}"
arg_defaultBranch="${default_defaultBranch}"
arg_description="${default_description}"
arg_homepage="${default_homepage}"
arg_gitUsername="${default_gitUsername}"
arg_gitPassword="${default_gitPassword}"
arg_private="${default_private}"
arg_issues="${default_issues}"
arg_projects="${default_projects}"
arg_wiki="${default_wiki}"
arg_allowSquashMerge="${default_allowSquashMerge}"
arg_allowMergeCommit="${default_allowMergeCommit}"
arg_allowRebaseMerge="${default_allowRebaseMerge}"
declare -A arg_teamCollaborators
declare -A arg_userCollaborators
declare -A teamNameToId

# Parse CLI arguments
while [ "$#" -gt 0 ]
do
  case "${1}" in
    help|-h|--help)
      doHelp; exit 0
      ;;
    --apiUrl|--owner|--repo|--moveFromOwner|--defaultBranch|--description|--homepage|--gitUsername|--gitPassword|--private|--issues|--projects|--wiki|--allowSquashMerge|--allowMergeCommit|--allowRebaseMerge)
      if [ "${2-}" = "" -a "${2-X}" = "X" ]; then
        handleArgAndValue "${1}" || ( doHelp >&2 ; exit 1 ) || exit 1
      else
        handleArgAndValue "${1}" "${2}" || ( doHelp >&2 ; exit 1 ) || exit 1
        shift
      fi
      ;;
    --teamCollaborator|--userCollaborator)
      if [ "${2-}" = "" -a "${2-X}" = "X" ]; then
        handleArgAndMultiValue "${1}" || ( doHelp >&2 ; exit 1 ) || exit 1
      else
        handleArgAndMultiValue "${1}" "${2}" || ( doHelp >&2 ; exit 1 ) || exit 1
        shift
      fi
      ;;
    --apiUrlEnvName|--ownerEnvName|--repoEnvName|--defaultBranchEnvName|--descriptionEnvName|--homepageEnvName|--gitUsernameEnvName|--gitPasswordEnvName|--privateEnvName|--issuesEnvName|--projectsEnvName|--wikiEnvName|--allowSquashMergeEnvName|--allowMergeCommitEnvName|--allowRebaseMergeEnvName)
      handleArgAndEnvName "${1}" "${2:-}" || ( doHelp >&2 ; exit 1 ) || exit 1
      shift
      ;;
    ----teamCollaboratorEnvName|--userCollaboratorEnvName)
      handleArgAndMultiValueEnvName "${1}" "${2:-}" || ( doHelp >&2 ; exit 1 ) || exit 1
      shift
      ;;
    *)
      (echo "Unrecognised argument '${1}'"; doHelp) >&2; exit 1
      ;;
  esac
  shift
done

# Validate arguments
if [ -z "${arg_owner}" ];then
  echo "Error: owner argument was not provided." >&2 ; doHelp >&2 ; exit 1
fi
if [ -z "${arg_repo}" ];then
  echo "Error: repo argument was not provided." >&2 ; doHelp >&2 ; exit 1
fi
# description can be empty
# homepage can be empty
if [ -z "${arg_gitUsername}" ];then
  handleArgAndEnvName --gitUsernameEnvName GIT_USERNAME || ( doHelp >&2 ; exit 1 ) || exit 1
fi
if [ -z "${arg_gitPassword}" ];then
  handleArgAndEnvName --gitPasswordEnvName GIT_PASSWORD || ( doHelp >&2 ; exit 1 ) || exit 1
fi
if ! isTrueOrFalseOrEmpty "${arg_private}"; then
  echo "Error: private argument (${arg_private}) was neither empty, 'true' or 'false'." >&2 ; doHelp >&2 ; exit 1
fi
if ! isTrueOrFalseOrEmpty "${arg_issues}"; then
  echo "Error: issues argument (${arg_issues}) was neither empty, 'true' or 'false'." >&2 ; doHelp >&2 ; exit 1
fi
if ! isTrueOrFalseOrEmpty "${arg_projects}"; then
  echo "Error: projects argument (${arg_projects}) was neither empty, 'true' or 'false'." >&2 ; doHelp >&2 ; exit 1
fi
if ! isTrueOrFalseOrEmpty "${arg_wiki}"; then
  echo "Error: wiki argument (${arg_wiki}) was neither empty, 'true' or 'false'." >&2 ; doHelp >&2 ; exit 1
fi
if ! isTrueOrFalseOrEmpty "${arg_allowSquashMerge}"; then
  echo "Error: allowSquashMerge argument (${arg_allowSquashMerge}) was neither empty, 'true' or 'false'." >&2 ; doHelp >&2 ; exit 1
fi
if ! isTrueOrFalseOrEmpty "${arg_allowMergeCommit}"; then
  echo "Error: allowMergeCommit argument (${arg_allowMergeCommit}) was neither empty, 'true' or 'false'." >&2 ; doHelp >&2 ; exit 1
fi
if ! isTrueOrFalseOrEmpty "${arg_allowRebaseMerge}"; then
  echo "Error: allowRebaseMerge argument (${arg_allowRebaseMerge}) was neither empty, 'true' or 'false'." >&2 ; doHelp >&2 ; exit 1
fi

for teamName in "${!arg_teamCollaborators[@]}"
do
  if ! isNonePullPushOrAdmin "${arg_teamCollaborators[$teamName]}"; then
    echo "Error: teamCollaborator argument (${teamName}=${arg_teamCollaborators[$teamName]}) did not set value to either 'none', 'pull', 'push' or 'admin'." >&2 ; doHelp >&2 ; exit 1
  fi
done
for userName in "${!arg_userCollaborator[@]}"
do
  if ! isNonePullPushOrAdmin "${arg_userCollaborator[$userName]}"; then
    echo "Error: teamCollaborator argument (${userName}=${arg_userCollaborator[$userName]}) did not set value to either 'none', 'pull', 'push' or 'admin'." >&2 ; doHelp >&2 ; exit 1
  fi
done
for teamName in "${!arg_teamCollaborators[@]}"
do
  teamNameToId["${teamName}"]=$(getGithubTeamIdFromNameSlug "${teamName}") || (
    echo "Error: teamCollaborator argument (${teamName}=${arg_teamCollaborators[$teamName]}) team name '${teamName}' could not be found."
    doHelp
    exit 1
  ) >&2 || exit 1
  if [ -z "${teamNameToId[$teamName]}" ]
  then
    echo "Error: teamCollaborator argument (${teamName}=${arg_teamCollaborators[$teamName]}) team name '${teamName}' was not recognised." >&2 ; doHelp >&2 ; exit 1
  fi
done


if isMoveNeeded
then
  githubMoveRepo
fi
if isEditNeeded
then
  githubEditRepo
fi
for teamName in "${!arg_teamCollaborators[@]}"
do
  echo "Set team '${teamName}' (Id ${teamNameToId[$teamName]}) access to '${arg_teamCollaborators[$teamName]}':"
  setTeamIdCollaboration "${teamNameToId[$teamName]}" "${arg_teamCollaborators[$teamName]}"
done
for userName in "${!arg_userCollaborators[@]}"
do
  echo "Set user '${userName}' access to '${arg_userCollaborators[$userName]}':"
  setUserCollaboration "${userName}" "${arg_userCollaborators[$userName]}"
done
echo "DONE"
