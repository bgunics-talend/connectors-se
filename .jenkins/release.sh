#!/usr/bin/env bash

set -xe

# Releases the components
# $1: the Jenkinsfile's params.Action
# $2: the version being released (semver, extracted from pom)
# $@: the extra parameters to be used in the maven commands
main() (
  jenkinsAction="${1?Missing Jenkins action}"; shift
  releaseVersion="${1?Missing release version}"; shift
  extraBuildParams=("$@")

  setMavenVersion "${releaseVersion}"
  setMavenProperty 'common.version' "${releaseVersion}"
  setMavenProperty 'connectors-test-bom.version' "${releaseVersion}"

  mvn deploy \
    --errors \
    --batch-mode \
    --fail-at-end \
    --activate-profiles "${jenkinsAction}" \
    "${extraBuildParams[@]}"

  git add --update
  git commit --message "[jenkins-release] Release ${releaseVersion}"

  majorVersion="$(cut --delimiter '.' --fields 1 <<< "${releaseVersion}")"
  minorVersion="$(cut --delimiter '.' --fields 2 <<< "${releaseVersion}")"
  patchVersion="$(cut --delimiter '.' --fields 3 <<< "${releaseVersion}")"
  postReleaseVersion="${majorVersion}.${minorVersion}.$((patchVersion + 1))-SNAPSHOT"

  tag="release/${releaseVersion}"
  git tag "${tag}"

  setMavenVersion "${postReleaseVersion}"
  setMavenProperty 'common.version' "${postReleaseVersion}"
  setMavenProperty 'connectors-test-bom.version' "${postReleaseVersion}"

  git add --update
  git commit --message "[jenkins-release] Prepare for next development iteration ${postReleaseVersion}"

  git push origin "${tag}"
  git push origin "$(git rev-parse --abbrev-ref HEAD)"

  # Deploy the next SNAPSHOT version
  mvn deploy \
    --errors \
    --batch-mode \
    --fail-at-end \
    --activate-profiles 'DEPLOY_SNAPSHOT_AFTER_RELEASE' \
    "${extraBuildParams[@]}"
)

setMavenVersion() (
  version="$1"
  mvn 'versions:set' \
    --batch-mode \
    --define "newVersion=${version}"
)

setMavenProperty() (
  propertyName="$1"
  propertyValue="$2"
  mvn 'versions:set-property' \
    --batch-mode \
    --define "property=${propertyName}" \
    --define "newVersion=${propertyValue}"
)

main "$@"
