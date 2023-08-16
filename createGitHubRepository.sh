#!/bin/bash
#
# Script that creates a new GitHub repository.
# If the repository already exists then it fails.

# Ensure we fail if we reference a variable we've not set
set -u
# Ensure we fail if we execute a command that returns a non-zero exit code
set -e
# Ensure we fail if we execute a command that has a failure in any step of a pipeline
set -o pipefail

SCRIPT_NAME=`basename ${0}`

# Setup variables to hold the parsed arguments.
default_apiUrl="https://api.github.com/"
default_org=""
default_repo=""
default_description=""
default_homepage=""
default_gitUsername=""
default_gitPassword=""
default_private="false"
default_issues="false"
default_projects="false"
default_wiki="false"
default_teamId=""
default_autoInit="false"
default_allowSquashMerge="true"
default_allowMergeCommit="true"
default_allowRebaseMerge="true"

# Outputs CLI usage help text to stdout.
doHelp() {
  cat <<ENDHELP
Syntax:
  ${SCRIPT_NAME} [--apiUrl ...] [--gitUsername ...] [--gitPassword ...] [--org ...] --repo ... [--description ...] [--homepage ...] [--private ...] [--issues ...] [--projects ...] [--wiki ...] [--teamId ...] [--autoInit ...] [--allowSquashMerge ...] [--allowMergeCommit ...] [--allowRebaseMerge]
Where
  --apiUrl "url" (Optional)
    Specifies the GitHub API URL.
    Defaults to "${default_apiUrl}".
  --gitUsername "username" (Optional)
  --gitPassword "password" (Optional)
    Specifies the credentials to be used when logging into git to clone and push.
    Note: For security reasons, it is better to use the --gitPasswordEnvName form (see below).
    Defaults to using environment variables GIT_USERNAME and GIT_PASSWORD.
  --org "gitOrganisationName" (Optional)
    If set, it specifies the GitHub organisation name that the repository will belong to.
    If not set, the repository will belong to the user being used to create the repository.
    Defaults to "${default_org}".
  --repo "name"
    Specifies the name of the git repository within the user/org it belongs to.
  --description "Repository description" (Optional)
    Specifies the description to be set on the repository.
    Defaults to "${default_description}".
  --homepage "url" (Optional)
    Specifies the homepage (that documents the repository) for the repository.
    Defaults to "${default_homepage}"
  --private true|false (Optional)
    If true, the repository will be made private instead of public.
    Defaults to "${default_private}".
  --issues true|false (Optional)
    If true, the repository will allow GitHub issues.
    Defaults to "${default_issues}".
  --projects true|false (Optional)
    If true, the repository will support having GitHub projects.
    Defaults to "${default_projects}".
  --wiki true|false (Optional)
    If true, the repository will have a GitHub wiki.
    Defaults to "${default_wiki}".
  --teamId integer (Optional)
    Sets the Id of the team to be granted access to this organisation repository.
    This is not valid for user repositories.
    Defaults to "${default_teamId}".
  --autoInit true|false (Optional)
    If true, an initial README.md file will be added as an initial commit.
    Defaults to "${default_autoInit}".
  --allowSquashMerge true|false (Optional)
  --allowMergeCommit true|false (Optional)
  --allowRebaseMerge true|false (Optional)
    When true, permits this kind of merging of pull requests.
    Defaults to "${default_allowSquashMerge}", "${default_allowMergeCommit}" and "${default_allowRebaseMerge}" respectively.
  --xxxEnvName "environmentVariableName"
    For any/all of the above arguments, specifies that the value for argument 'xxx' is to be read from
    the specified environment variable.
Description:
  Creates a new GitHub repository.
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

# $1 = value that must be an integer
isInteger() {
  expr "$1" : '-?[0-9][0-9]*$' >/dev/null 2>&1
}

# Setup variables to hold the parsed arguments.
arg_apiUrl="${default_apiUrl}"
arg_org="${default_org}"
arg_repo="${default_repo}"
arg_description="${default_description}"
arg_homepage="${default_homepage}"
arg_gitUsername="${default_gitUsername}"
arg_gitPassword="${default_gitPassword}"
arg_private="${default_private}"
arg_issues="${default_issues}"
arg_projects="${default_projects}"
arg_wiki="${default_wiki}"
arg_teamId="${default_teamId}"
arg_autoInit="${default_autoInit}"
arg_allowSquashMerge="${default_allowSquashMerge}"
arg_allowMergeCommit="${default_allowMergeCommit}"
arg_allowRebaseMerge="${default_allowRebaseMerge}"

# Parse CLI arguments
while [ "$#" -gt 0 ]
do
  case "${1}" in
    help|-h|--help)
      doHelp; exit 0
      ;;
    --apiUrl|--org|--repo|--description|--homepage|--gitUsername|--gitPassword|--private|--issues|--projects|--wiki|--teamId|--autoInit|--allowSquashMerge|--allowMergeCommit|--allowRebaseMerge)
      if [ "${2-}" = "" -a "${2-X}" = "X" ]; then
        handleArgAndValue "${1}" || ( doHelp >&2 ; exit 1 ) || exit 1
      else
        handleArgAndValue "${1}" "${2}" || ( doHelp >&2 ; exit 1 ) || exit 1
        shift
      fi
      ;;
    --apiUrlEnvName|--orgEnvName|--repoEnvName|--descriptionEnvName|--homepageEnvName|--gitUsernameEnvName|--gitPasswordEnvName|--privateEnvName|--issuesEnvName|--projectsEnvName|--wikiEnvName|--teamIdEnvName|--autoInitEnvName|--allowSquashMergeEnvName|--allowMergeCommitEnvName|--allowRebaseMergeEnvName)
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
# org can be empty
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
if ! isTrueOrFalse "${arg_private}"; then
  echo "Error: private argument (${arg_private}) was neither 'true' or 'false'." >&2 ; doHelp >&2 ; exit 1
fi
if ! isTrueOrFalse "${arg_issues}"; then
  echo "Error: issues argument (${arg_issues}) was neither 'true' or 'false'." >&2 ; doHelp >&2 ; exit 1
fi
if ! isTrueOrFalse "${arg_projects}"; then
  echo "Error: projects argument (${arg_projects}) was neither 'true' or 'false'." >&2 ; doHelp >&2 ; exit 1
fi
if ! isTrueOrFalse "${arg_wiki}"; then
  echo "Error: wiki argument (${arg_wiki}) was neither 'true' or 'false'." >&2 ; doHelp >&2 ; exit 1
fi
if [ -n "${arg_teamId}" ] && ! isInteger "${arg_teamId}"; then
  echo "Error: teamId argument (${arg_teamId}) was neither empty or an integer." >&2 ; doHelp >&2 ; exit 1
fi
if ! isTrueOrFalse "${arg_autoInit}"; then
  echo "Error: autoInit argument (${arg_autoInit}) was neither 'true' or 'false'." >&2 ; doHelp >&2 ; exit 1
fi
if ! isTrueOrFalse "${arg_allowSquashMerge}"; then
  echo "Error: allowSquashMerge argument (${arg_allowSquashMerge}) was neither 'true' or 'false'." >&2 ; doHelp >&2 ; exit 1
fi
if ! isTrueOrFalse "${arg_allowMergeCommit}"; then
  echo "Error: allowMergeCommit argument (${arg_allowMergeCommit}) was neither 'true' or 'false'." >&2 ; doHelp >&2 ; exit 1
fi
if ! isTrueOrFalse "${arg_allowRebaseMerge}"; then
  echo "Error: allowRebaseMerge argument (${arg_allowRebaseMerge}) was neither 'true' or 'false'." >&2 ; doHelp >&2 ; exit 1
fi

githubCreationPostRequestPayload() {
  echo '{'
  echo "  \"name\": \"${arg_repo}\","
  if [ -n "${arg_description}" ]
  then
    echo "  \"description\": \"${arg_description}\","
  fi
  if [ -n "${arg_homepage}" ]
  then
    echo "  \"homepage\": \"${arg_homepage}\","
  fi
  echo "  \"private\": ${arg_private},"
  echo "  \"has_issues\": ${arg_issues},"
  echo "  \"has_projects\": ${arg_projects},"
  echo "  \"has_wiki\": ${arg_wiki},"
  if [ -n "${arg_teamId}" ]
  then
    echo "  \"team_id\": ${arg_teamId},"
  fi
  echo "  \"auto_init\": ${arg_autoInit},"
  echo "  \"allow_squash_merge\": ${arg_allowSquashMerge},"
  echo "  \"allow_merge_commit\": ${arg_allowMergeCommit},"
  echo "  \"allow_rebase_merge\": ${arg_allowRebaseMerge}"
  echo '}'
}

# $1 = url to post to
githubCreateRepo() {
  echo "HTTP POST to $1"
  githubCreationPostRequestPayload
  githubCreationPostRequestPayload | curl --header "Authorization: token ${arg_gitPassword}" --silent --request POST --data '@-' "$1"
}

if [ -z "${arg_org}" ]
then
  githubCreateRepo "${arg_apiUrl}user/repos"
else
  githubCreateRepo "${arg_apiUrl}orgs/${arg_org}/repos"
fi
