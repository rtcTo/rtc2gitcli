#!/bin/bash
#
# Script to build a docker image that is a Jenkins agent containing the rtc2git migration code.
# The docker container can be used outside of Jenkins, but it's primarily intended
# as a Jenkins agent.
#
# Requires various variables to be set in addition to the arguments below:
#   $1 = Name of this docker image, e.g. foobar:mybranch-123 or foobar:123
#
set -o pipefail
set -eu



# Download the internal certificate CA(s) that we need our image to "trust" so that it can
# connect to our Jenkins servers.
downloadCertificatesThatOurJvmMustTrust() {
  # If your working environment uses self-signed CAs then you'll want your
  # docker container's JVM to "trust" your CAs.
  # e.g. if your RTC server has CA that isn't trusted by default, or your
  # Jenkins server etc.
  # So, if that's the case, you'll need to uncomment the code below
  # and make it download the certificates into the TrustedCertificates folder.
  #
  # Any certs placed in the TrustedCertificates folder will end up in the trust
  # store of the docker container's JVM.

  #mkdir -p TrustedCertificates
  #(
  #cd TrustedCertificates
  #for URL in \
  #    'https://my.internal.server/some-internal-path/caintermediatecert.der' \
  #    'https://my.internal.server/some-internal-path/carootcert.der' \
  #;do
  #  local filename=`basename "${URL}"`
  #  if [ -f "${filename}" ]
  #  then
  #    echo "Have already downloaded ${URL}"
  #  else
  #    echo "Downloading ${URL}..."
  #    curl -H "X-JFrog-Art-Api:${ARTIFACTORY_PASSWORD}" -O "${URL}" || exit 1
  #    echo "...Done."
  #  fi
  #done
  #chmod 0444 *
  #ls -al
  #)
}



# build the rtc2gitcli migration-enabled RTC toolkit.
compileAndBuildTheMigrationCode() {
  if [ -d rtcScmTools ]
  then
    echo "Skipping build of migration code as it already exists in rtcScmTools/"
  else
    ./build.sh "${RTC_ScmTools_HOME}"
  fi
}



# Create a tar file containing everything we want to include in the new agent image
# We do this in two phases:
# 1) We do all the "owned and writable by jenkins" stuff
# 2) We do all the "owned by root and not writable by jenkins" stuff
createContentsTarFile() {
  rm -rf root
  mkdir root

  # 1) Stuff owned by jenkins
  #
  # figure out where the tools are cached on the build agent this build is running on
  # and include them in the image under contruction
  srcWorkspacesFolder="$(cd .. && pwd)"
  srcJenkinsAgentFolder="$(cd ${srcWorkspacesFolder} && cd .. && pwd)"
  srcJenkinsToolsFolder="${srcJenkinsAgentFolder}/tools"
  mkdir -p "root/${AgentBaseImageJenkinsRoot}"
  if [ -d "${srcJenkinsToolsFolder}" ]
  then
    mkdir -p "root/${AgentBaseImageJenkinsRoot}/tools"
    tar -cf - -C "${srcJenkinsToolsFolder}" . | tar -xf - -C "root/${AgentBaseImageJenkinsRoot}/tools"
  else
    echo "WARNING: Could not detect any Jenkins agent 'tools'"
  fi
  #
  # add the jenkins-owned files
  tar -cf contents.tar \
    -C root \
    --owner=jenkins --group=jenkins \
    "${AgentBaseImageJenkinsRoot}"
  #
  # clean up
  rm -rf root/*

  # 2) Stuff owned by root
  #
  # Include our github/migration scripts
  mkdir -p root/usr/local/bin
  cp -p *.sh root/usr/local/bin
  # but not these
  rm -f root/usr/local/bin/build*.sh root/usr/local/bin/mkmanifestjar.sh
  #
  # include the RTC SCM that includes rtc2git migration support
  mkdir -p root/opt
  mv rtcScmTools root/opt/rtcScmTools
  # If we need to override the RAM for the RTC CLI, we do so here.
  if [ -n "${RTC_CLI_MaxMemory-}" ]
  then
    scmIniFile='root/opt/rtcScmTools/jazz/scmtools/eclipse/scm.ini'
    sed --in-place 's,^-Xmx[0-9][0-9]*[kmg]$,-Xmx'"${RTC_CLI_MaxMemory}"',' "${scmIniFile}" || exit 1
    echo "${scmIniFile} is now:"
    cat "${scmIniFile}"
  fi
  # and include our default migration.properties file
  cp -p migration.properties root/opt/rtcScmTools/
  #
  # Include any extra CA certificates we're going to need our JVM to trust
  # in order to connect to our Jenkins instance.
  if [ -d "TrustedCertificates" ]
  then
    mkdir -p "root/jvmExtraCerts"
    tar -cf - -C "TrustedCertificates" . | tar -xf - -C "root/jvmExtraCerts"
  fi
  #
  # ensure that none of it is globally writable
  chmod -R o-w root
  # make a tar file of the root-owned stuff
  tar -uf contents.tar \
    -C root \
    --owner=root --group=root \
    $(cd root && ls -1)
  # clean up
  mv root/opt/rtcScmTools rtcScmTools
  rm -rf root/*

  # list contents
  rm -rf root
  tar -tvf contents.tar >contents.txt
}



createExtraArgsTxtFile() {
  # Find all tools we've defined (accidentally or otherwise) and set default locations for them.
  tar -tf contents.tar | (grep '^.*/tools/[^ /][^ /]*/[^ /][^ /]*/$' || true) | \
  while read line
  do
    thisFolderBase=$( expr "${line}" : '\(.*\)/tools/[^ /][^ /]*/[^ /][^ /]*/$' )
    if [ "${thisFolderBase}" = "${AgentBaseImageJenkinsRoot}" ]
    then
      toolName=$(     expr "${line}" : '.*/tools/[^ /][^ /]*/\([^ /][^ /]*\)/$' )
      toolLocation=$( expr "${line}" : '\(.*/tools/[^ /][^ /]*/[^ /][^ /]*\)/$' )
      echo "${toolName}_HOME=/${toolLocation}"
    fi
  done > extraArgs.txt
}



# Ensure we only send the docker context files we mean to send
createDockerIgnoreFile() {
  (
  cat <<ENDTEXT
# Ignore everything except contents.txt
*
!contents.tar
ENDTEXT
  ) >.dockerignore
}



# Create the Dockerfile
# $1 = pref
createDockerfile() {
  # Maintenance note: \\ turns into \ when the cat command runs.
  # Maintenance note: to defer ENV expansion within a RUN, we need to escape a $ as \$.
  (
  cat <<ENDTEXT
# Dockerfile to extend Jenkins agent with rtc2gitcli tooling
FROM ${AgentBaseImage}

# Add in some extra commands that we're going to require
# and try to install some form of traceroute for diagnostics.
USER root
RUN apt-get update \\
    && apt-get install -y \\
        curl \\
        debianutils \\
        file \\
        procps \\
        sed \\
        traceroute \\
    && apt-get clean -y \\
    && rm -rf /var/lib/apt/lists/*

# Install the latest git-lfs.
# We need this because the packaged version we've got has bugs that are only fixed in
# recent versions, and those versions are available on the official debian package repos.
RUN true \\
    && echo 'Installing latest git-lfs' \\
    && apt-get update \\
    && curl -s https://packagecloud.io/install/repositories/github/git-lfs/script.deb.sh | bash \\
    && apt-get install -y \\
        git-lfs \\
    && apt-get clean -y \\
    && rm -rf /var/lib/apt/lists/* \\
    && echo 'Reporting git-lfs version' \\
    && git lfs version \\
    && true

# Build git from source and use openssl instead of gnutls as the latter results in
# "GnuTLS recv error (-110): The TLS connection was non-properly terminated" errors.
# See https://stackoverflow.com/questions/52529639/gnutls-recv-error-110-the-tls-connection-was-non-properly-terminated
# ...but we do also need to add the deb-src rows to source.list to find our source code.
RUN true \\
    && echo 'Adding deb-src to sources.list' \\
    && cp -p /etc/apt/sources.list /etc/apt/sources.list.orig \\
    && sed -i -e 's,^deb \\(.*\\)$,deb \\1\\ndeb-src \\1,g' /etc/apt/sources.list \\
    && cat /etc/apt/sources.list \\
    && echo 'Getting build pre-reqs' \\
    && apt-get update \\
    && apt-get install -y \\
        build-essential \\
        dpkg-dev \\
        fakeroot \\
    && apt-get build-dep -y \\
        git \\
    && apt-get install -y \\
        libcurl4-openssl-dev \\
    && echo 'Obtaining git package source' \\
    && mkdir /tmp/source-git \\
    && cd /tmp/source-git/ \\
    && apt-get source \\
        git \\
    && ls -al \\
    && cd git-*/ \\
    && echo 'Reconfigure git package to use openssl instead of gnutls' \\
    && sed -i -- 's/libcurl4-gnutls-dev/libcurl4-openssl-dev/' ./debian/control \\
    && sed -i -- '/TEST\\s*=\\s*test/d' ./debian/rules \\
    && echo 'Building git package using openssl instead of gnutls' \\
    && dpkg-buildpackage -rfakeroot -b -uc -us \\
    && ls -al /tmp/source-git \\
    && echo 'Installing new git openssl-based git' \\
    && dpkg -i ../git_*.deb \\
    && echo 'Tidying up' \\
    && apt-get remove -y \\
        build-essential \\
        dpkg-dev \\
        fakeroot \\
        libcurl4-openssl-dev \\
    && apt-get autoremove -y \\
    && apt-get clean -y \\
    && mv -f /etc/apt/sources.list.orig /etc/apt/sources.list \\
    && rm -rf /var/lib/apt/lists/* /tmp/source-git \\
    && echo 'Reporting git version' \\
    && git --version \\
    && true

# Say where our rtc2git-enabled RTC toolkit is
ENV RTC_TOOLKIT=RTC_ToGitMigation
ENV RTC_ToGitMigation_SCM_HOME=/opt/rtcScmTools/jazz/scmtools/eclipse

# Show where any other Jenkins tools are
ENDTEXT
  while read line
  do
    echo "ENV ${line}"
  done < extraArgs.txt
  cat <<ENDTEXT

# Add things to PATH
ENV PATH \${RTC_ToGitMigation_SCM_HOME}:\${PATH}

# copy in everything
ADD contents.tar /

# Ensure that our JVM trusts all the certificates we need it to trust
# Java8 keystore is at /opt/java/openjdk/jre/lib/security/cacerts
# Java11 is at /opt/java/openjdk/lib/security/cacerts
# We might have either the JRE or full JDK on the path
RUN /bin/bash -c '\\
    set -eux;\\
    WHICHKEYTOOL=\$(which keytool);\\
    WHEREKEYTOOL=\$(dirname "\${WHICHKEYTOOL}");\\
    if [ -f "\${WHEREKEYTOOL}/../lib/security/cacerts" ];\\
    then \\
      WHERECERTS=\$(cd "\${WHEREKEYTOOL}" && cd ../lib/security && pwd);\\
    elif [ -f "\${WHEREKEYTOOL}/../jre/lib/security/cacerts" ];\\
    then \\
      WHERECERTS=\$(cd "\${WHEREKEYTOOL}" && cd ../jre/lib/security && pwd);\\
    else \\
      echo "Found Java keytool at \${WHICHKEYTOOL} but this Java has no trust store at \${WHEREKEYTOOL}/../lib/security/cacerts or \${WHEREKEYTOOL}/../jre/lib/security/cacerts" >&2;\\
      exit 1;\\
    fi;\\
    CERTFILE="\${WHERECERTS}/cacerts";\\
    if [ -d /jvmExtraCerts ];\\
    then \\
      cd /jvmExtraCerts \\
      && for CACERT in * \\
      ; do \\
        echo "Telling Java to trust certificate \${CACERT}" \\
        && keytool \\
              -importcert \\
              -noprompt \\
              -alias "\${CACERT}" \\
              -file "\${CACERT}" \\
              -keystore "\${CERTFILE}" \\
              -storepass changeit; \\
      done; \\
    fi'

LABEL Description="${ImageDescription}"
ENV DOCKER_IMAGE_DESCRIPTION "${ImageDescription}"

USER jenkins
ENDTEXT
) >Dockerfile
}



if [ -z "${AgentBaseImage:-}" ]
then
  # the RTC CLI does not like running on Java11
  AgentBaseImage='jenkins/inbound-agent:latest-jdk8'
fi
if [ -z "${AgentBaseImageJenkinsRoot:-}" ]
then
  AgentBaseImageJenkinsRoot='home/jenkins'
fi

ImageDescription="Jenkins agent ${AgentBaseImage} with rtc2gitcli support built in."
if [ -n "${1:-}" ]
then
  ImageDescription="${ImageDescription} ${1:-}."
fi
if [ -n "${BUILD_URL:-}" ]
then
  ImageDescription="${ImageDescription} Built by ${BUILD_URL}."
fi
ImageDescription="${ImageDescription} For internal use only."

echo "Ensure we have the certificates that our JVM must trust"
downloadCertificatesThatOurJvmMustTrust
echo "Ensure we have a build RTC CLI with the migration code"
compileAndBuildTheMigrationCode
echo "Ensure we have a contents.tar file for the docker image"
createContentsTarFile
echo "Find what extra env vars we should have"
createExtraArgsTxtFile
echo "Create docker ignore file"
createDockerIgnoreFile
echo "Create Dockerfile"
createDockerfile
echo "Dockerfile created together with the resources it requires to build."
