import cd.go.contrib.plugins.configrepo.groovy.dsl.GoCD
import cd.go.contrib.plugins.configrepo.groovy.dsl.Job

def javaTestJobs = { repo ->
  return [
          new Job("test", {
            elasticProfileId = repo['elasticProfileForTests']
            tasks {
              bash {
                commandString = 'JAVA_VERSION=15 with-java ./gradlew assemble check'
              }
            }
          })
  ]
}

def defaultElasticProfile = 'ecs-gocd-dev-build-dind'

def allRepos = [
    [ 'org': 'gocd-contrib', repo: 'gocd-groovy-dsl-config-plugin',        elasticProfileForTests: 'ecs-gocd-dev-build-dind',   testJobs: javaTestJobs,   mainBranch: 'master' ],
    [ 'org': 'gocd',         repo: 'gocd-yum-repository-poller-plugin',    elasticProfileForTests: 'ecs-gocd-dev-build-dind',   testJobs: javaTestJobs,   mainBranch: 'master' ]
  ]

def releaseCredentials = {
  return [
    GITHUB_TOKEN: 'AES:9Z9Lv85kry1oWWlOaCUF/w==:fWti8kD99VN7f++r7PfgLmXulS8GPmyb8bWm7yl1DYoDh1QihWEumO1mCfwiJ/O0',
  ]
}

GoCD.script {
  pipelines {
    allRepos.each { repo ->
      pipeline("${repo['org']}-${repo['repo']}-pr") {
        environmentVariables = repo['envVars']
        materials {
          githubPR("${repo['repo']}-material") {
            url = "https://git.gocd.io/git/${repo['org']}/${repo['repo']}"
            branch = "${repo['mainBranch']}"
          }
        }
        group = "gocd" == repo['org'] ? "supported-plugins-pr" : "plugins-pr"
        stages {
          stage("test") {
            jobs {
              addAll(repo['testJobs'](repo))
            }
          }
        }
      }
      pipeline("${repo['org']}-${repo['repo']}") {
        environmentVariables = repo['envVars']
        materials {
          git {
            url = "https://git.gocd.io/git/${repo['org']}/${repo['repo']}"
            shallowClone = false
            branch = repo['mainBranch']
          }
        }
        group = "gocd" == repo['org'] ? "supported-plugins" : "plugins"
        stages {
          stage("test") {
            jobs {
              addAll(repo['testJobs'](repo))
            }
          }

          stage("github-preview-release") {
            environmentVariables = [GITHUB_USER: repo['org']]
            secureEnvironmentVariables = releaseCredentials()
            jobs {
              job("create-preview-release") {
                elasticProfileId = defaultElasticProfile
                bash {
                  commandString = 'JAVA_VERSION=15 with-java ./gradlew githubRelease'
                }
              }
            }
          }

          stage("github-release") {
            approval { type = 'manual' }
            environmentVariables = [GITHUB_USER: repo['org'], PRERELEASE: "NO"]
            secureEnvironmentVariables = releaseCredentials()
            jobs {
              job("create-release") {
                elasticProfileId = defaultElasticProfile
                tasks {
                  bash {
                    commandString = 'JAVA_VERSION=15 with-java ./gradlew githubRelease'
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
