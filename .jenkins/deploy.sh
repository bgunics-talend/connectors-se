#!/usr/bin/env bash

set -xe

# Builds the components and deploys them on Nexus, SKIPPING TESTS!
# $1: the Jenkinsfile's params.Action
# $@: the extra parameters to be used in the maven command
main() (
  jenkinsAction="${1?Missing Jenkins action}"; shift
  extraBuildParams=("$@")

  mvn deploy \
    --errors \
    --batch-mode \
    --fail-at-end \
    --activate-profiles "${jenkinsAction}" \
    "${extraBuildParams[@]}"
)

main "$@"
