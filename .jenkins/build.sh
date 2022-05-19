#!/usr/bin/env bash

set -xe

# Builds the components with tests, Docker image and spotBugs enabled
# Also generates the Talend components uispec
# $1: the Jenkinsfile's params.Action
# $2: Execute maven verify or deploy whether the branch is maintenance or master.
# $3: Execute sonar anlaysis or not, from jenkinsfile's params.SONAR_ANALYSIS
# $4: Sonar analyzed branch
# $@: the extra parameters to be used in the maven commands
main() (
  jenkinsAction="${1?Missing Jenkins action}"; shift
  isOnMasterOrMaintenanceBranch="${1?Missing is on master or a maintenance branch}"; shift
  sonar="${1?Missing sonar option}"; shift
  branch="${1?Missing branch name}"; shift
  extraBuildParams=("$@")

  mavenPhase='verify'
  if [[ "${isOnMasterOrMaintenanceBranch}" == 'true' ]]; then
    mavenPhase='deploy'
  fi

  # Real task
  # ITs profile is added for jdbc
  mvn "${mavenPhase}" \
      --errors \
      --batch-mode \
      --fail-at-end \
      --activate-profiles "${jenkinsAction},ITs" \
      "${extraBuildParams[@]}"

  if [[ "${sonar}" == 'true' ]]; then
    declare -a LIST_FILE_ARRAY=( $(find $(pwd) -type f -name 'jacoco.xml') )
    LIST_FILE=$(IFS=, ; echo "${LIST_FILE_ARRAY[*]}")
    # Why sonar plugin is not declared in pom.xml: https://blog.sonarsource.com/we-had-a-dream-mvn-sonarsonar
    mvn sonar:sonar \
        --define 'sonar.host.url=https://sonar-eks.datapwn.com' \
        --define "sonar.login=${SONAR_LOGIN}" \
        --define "sonar.password=${SONAR_PASSWORD}" \
        --define "sonar.branch.name=${branch}" \
        --define "sonar.coverage.jacoco.xmlReportPaths='${LIST_FILE}'" \
        --activate-profiles SONAR \
        "${extraBuildParams[@]}"
  fi
)

main "$@"
