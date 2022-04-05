#!/bin/bash

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
  ${SCRIPT_NAME} [--apiUrl ...] [--user ...] [--org ...] --repo ... [--gitUsername ... ] [--gitPassword ...]
Where
  --apiUrl "url" (Optional)
    Specifies the GitHub API URL.
    Defaults to https://api.github.com/
  --user "gitUserName" (Optional)
  --org "gitOrganisationName" (Optional)
    One, and only one, of these must be set.
    If user is set, it specifies the GitHub username that the repository belongs to.
    If org is set, it specifies the GitHub organisation name that the repository belongs to.
  --repo "name"
    Specifies the name of the git repository within the user/org it belongs to.
  --gitUsername "username" (Optional)
  --gitPassword "password" (Optional)
    Specifies the credentials to be used when logging into git to clone and push.
    Note: For security reasons, it is better to use the --gitPasswordEnvName form (see below).
    Defaults to using environment variables GIT_USERNAME and GIT_PASSWORD.
  --xxxEnvName "environmentVariableName"
    For any/all of the above arguments, specifies that the value for argument 'xxx' is to be read from
    the specified environment variable.
Description:
  Deletes a GitHub repository.
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

# Setup variables to hold the parsed arguments.
arg_apiUrl="https://api.github.com/"
arg_user=""
arg_org=""
arg_repo=""
arg_gitUsername=""
arg_gitPassword=""
arg_private="false"
arg_issues="false"
arg_wiki="false"
arg_allowSquashMerge="true"
arg_allowMergeCommit="true"
arg_allowRebaseMerge="true"


# Parse CLI arguments
while [ "$#" -gt 0 ]
do
  case "${1}" in
    help|-h|--help)
      doHelp; exit 0
      ;;
    --apiUrl|--user|--org|--repo|--gitUsername|--gitPassword)
      if [ "${2-}" = "" -a "${2-X}" = "X" ]; then
        handleArgAndValue "${1}" || ( doHelp >&2 ; exit 1 ) || exit 1
      else
        handleArgAndValue "${1}" "${2}" || ( doHelp >&2 ; exit 1 ) || exit 1
        shift
      fi
      ;;
    --apiUrlEnvName|--userEnvName|--orgEnvName|--repoEnvName|--gitUsernameEnvName|--gitPasswordEnvName)
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
if [ -z "${arg_user}" -a -z "${arg_org}" ];then
  echo "Error: Neither user or org argument were provided." >&2 ; doHelp >&2 ; exit 1
fi
if [ -n "${arg_user}" -a -n "${arg_org}" ];then
  echo "Error: Both user and org argument were provided." >&2 ; doHelp >&2 ; exit 1
fi
if [ -z "${arg_repo}" ];then
  echo "Error: repo argument was not provided." >&2 ; doHelp >&2 ; exit 1
fi
if [ -z "${arg_gitUsername}" ];then
  handleArgAndEnvName --gitUsernameEnvName GIT_USERNAME || ( doHelp >&2 ; exit 1 ) || exit 1
fi
if [ -z "${arg_gitPassword}" ];then
  handleArgAndEnvName --gitPasswordEnvName GIT_PASSWORD || ( doHelp >&2 ; exit 1 ) || exit 1
fi


# $1 = url to send the delete to
githubDeleteRepo() {
  echo "HTTP DELETE to $1"
  curl --header "Authorization: token ${arg_gitPassword}" --request DELETE "$1"
}

if [ -n "${arg_user}" ]
then
  githubDeleteRepo "${arg_apiUrl}repos/${arg_user}/${arg_repo}"
else
  githubDeleteRepo "${arg_apiUrl}repos/${arg_org}/${arg_repo}"
fi
