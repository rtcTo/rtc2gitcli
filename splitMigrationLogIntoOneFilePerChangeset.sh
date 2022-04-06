#!/bin/bash
#
# Utility script to take one or more files containing logs that came from running the migrateFromRTCtoGit.sh script
# and split the logs into batches and then into one file per changeset.
# This makes it easier to "see the big picture" and identify failure patterns.
#
# It expects that log filenames will be of the form "<somePrefix><number><someSuffix>".
# It creates a folder called "<somePrefix><number>"
# ... that contains numerically-named folders
#      - One folder per batch of changesets, i.e. one per git push, plus the first one is setup stuff.
# ... that contains files named <number><someSuffix>
#      - One file for each changeset/commit, plus the first one is setup stuff.

set -eu
set -o pipefail

# Splits a build into batches, so each should start with a call to the migrator
# and end with a git-push.
# $1 = filename to split - expected to be of the format <something><buildnumber><something>
splitConsoleIntoGitPushes() {
  local wholeConsoleFile="$1"
  local filenamePrefix=$( expr "${wholeConsoleFile}" : '^\([^0-9]*[0-9][0-9]*\)[^0-9]*$' )
  local filenameSuffix=$( expr "${wholeConsoleFile}" : '^[^0-9]*[0-9][0-9]*\([^0-9]*\)$' )
  if [ -z "${filenamePrefix}" -o -z "${filenameSuffix}" ]
  then
    echo "Filename '${wholeConsoleFile}' does not contain a number so we can't determine where to split it in two. Filenames must be of the form <text><number><text>." >&2
    return 1
  fi
  local splitRegex='^Info: Migrating RTC changes to local git clone\.\.\.$'
  local -a csplitArgs=()
  csplitArgs+=( "--prefix=${filenamePrefix}" )
  csplitArgs+=( "--suffix-format=/%02d${filenameSuffix}" )
  csplitArgs+=( "--quiet" )
  csplitArgs+=( "${wholeConsoleFile}" )
  csplitArgs+=( "/${splitRegex}/" )
  csplitArgs+=( '{*}' )
  mkdir -p "${filenamePrefix}"
  csplit "${csplitArgs[@]}"
  local splitFile
  for splitFile in ${filenamePrefix}/[0-9][0-9]${filenameSuffix}
  do
    splitConsoleGitPushIntoChangesets "${splitFile}" && rm "${splitFile}"
  done
}

# $1 = filename to split - expected to be of the format <something><buildnumber>.<batchnumber><something>
splitConsoleGitPushIntoChangesets() {
  local wholeGitPushFile="$1"
  local filenamePrefix=$( expr "${wholeGitPushFile}" : '^\([^0-9]*[0-9][0-9]*/[0-9][0-9]*\)[^0-9]*$' )
  local filenameSuffix=$( expr "${wholeGitPushFile}" : '^[^0-9]*[0-9][0-9]*/[0-9][0-9]*\([^0-9]*\)$' )
  if [ -z "${filenamePrefix}" -o -z "${filenameSuffix}" ]
  then
    echo "Filename '${wholeConsoleFile}' does not contain a sequence of <number>/<number> so we can't determine where to split it in two. Filenames must be of the form <text>/<number><text>." >&2
    return 1
  fi
  local splitRegex='^\[[-:. 0-9]*\]     DelegateCommand \[accept .*\] running\.\.\.$'
  local -a csplitArgs=()
  mkdir -p "${filenamePrefix}"
  csplitArgs+=( "--prefix=${filenamePrefix}" )
  csplitArgs+=( "--suffix-format=/%03d${filenameSuffix}" )
  csplitArgs+=( "--quiet" )
  csplitArgs+=( "${wholeGitPushFile}" )
  csplitArgs+=( "/${splitRegex}/" )
  csplitArgs+=( '{*}' )
  csplit "${csplitArgs[@]}"
  local splitFile
  for splitFile in ${filenamePrefix}/[0-9][0-9][0-9]${filenameSuffix}
  do
    postProcessSingleChangesetFile "${splitFile}"
  done
}

# Does final tidy-ups
# $1 = filename to post-process
postProcessSingleChangesetFile() {
  if grep -q '^Logfiles found:$' "${1}"
  then
    cat "${1}" | turnXmlLoggedExceptionIntoText > "${1}.new"
    mv "${1}.new" "${1}"
  fi
}

turnXmlLoggedExceptionIntoText() {
  awk '
BEGIN {
  insideException="false";
  insideRecord="false";
}
/<record>/ {
  insideRecord="true";
  recordDate=""
  recordMillis=""
  recordLogger=""
  recordLevel=""
  recordThread=""
  next;
}
/<\/record>/ {
  insideRecord="false";
  next;
}
/  *<date>.*<\/date>/ {
  if (insideRecord!="true") {
    print $0;
  } else {
    recordDate=gensub(/  *<date>([0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]*)T([0-9][0-9]:[0-9][0-9]:[0-9][0-9])<\/date>/,"\\1 \\2","g",$0);
  }
  next;
}
/  *<millis>.*<\/millis>/ {
  if (insideRecord!="true") {
    print $0;
  } else {
    recordMillis=gensub(/  *<millis>[0-9]*([0-9][0-9][0-9])<\/millis>/,"\\1","g",$0);
  }
  next;
}
/  *<sequence>.*<\/sequence>/ {
  if (insideRecord!="true") {
    print $0;
  }
  next;
}
/  *<thread>.*<\/thread>/ {
  if (insideRecord!="true") {
    print $0;
  } else {
    recordThread=gensub(/  *<thread>(.*)<\/thread>/,"\\1","g",$0);
  }
  next;
}
/  *<logger>.*<\/logger>/ {
  if (insideRecord!="true") {
    print $0;
  } else {
    recordLogger=gensub(/  *<logger>(.*)<\/logger>/,"\\1","g",$0);
  }
  next;
}
/  *<level>.*<\/level>/ {
  if (insideRecord!="true") {
    print $0;
  } else {
    recordLevel=gensub(/  *<level>(.*)<\/level>/,"\\1","g",$0);
  }
  next;
}
/  *<exception>/ {
  insideException="true";
  next;
}
/  *<\/exception>/ {
  insideException="false";
  next;
}
/  *<message>.*<\/message>/ {
  msgText=gensub(/  *<message>([^<]*)<\/message>/,"\\1","g",$0)
  if (insideException=="true") {
    printf("[%s.%s] %s: \t%s\n", recordDate, recordMillis, recordLevel, msgText);
    next;
  } else {
    if (insideRecord=="true") {
      printf("\n[%s.%s] %s: %s[%s] %s\n", recordDate, recordMillis, recordLevel, recordLogger, recordThread, msgText);
      next;
    }
  }
}
/  *<frame>/ {
  frameClass="";
  frameMethod="";
  frameLine="";
  next;
}
/  *<class>.*<\/class>/ {
  frameClass=gensub(/  *<class>([^<]*)<\/class>/,"\\1","g",$0);
  next;
}
/  *<method>.*<\/method>/ {
  frameMethod=gensub(/  *<method>([^<]*)<\/method>/,"\\1","g",$0);
  next;
}
/  *<line>.*<\/line>/ {
  frameLine=gensub(/  *<line>([^<]*)<\/line>/,"\\1","g",$0);
  next;
}
/  *<\/frame>/ {
  if (frameClass!="") {
    printf("[%s.%s] %s: \t\t%s",recordDate, recordMillis, recordLevel, frameClass);
    if (frameMethod!="") {
      printf(".%s",frameMethod);
    }
    if (frameLine!="") {
      printf(":%s",frameLine);
    }
    printf("\n");
  }
  frameClass="";
  frameMethod="";
  frameLine="";
  next;
}
{ print }
  '
}

for FILE
do
  splitConsoleIntoGitPushes "${FILE}"
done
