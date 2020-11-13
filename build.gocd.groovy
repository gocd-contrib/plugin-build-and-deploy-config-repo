import cd.go.contrib.plugins.configrepo.groovy.dsl.GoCD
import cd.go.contrib.plugins.configrepo.groovy.dsl.Job

def javaTestJobs = { repo ->
  return [
          new Job("test", {
            elasticProfileId = repo['elasticProfileForTests']
            tasks {
              exec { commandLine = ['./gradlew', 'assemble', 'check'] }
            }
          })
  ]
}

def dockerTestJobs = { repo ->
  def docker_versions = ["17.03.2-ce", "17.06.2-ce", "17.09.1-ce", "17.12.1-ce", "18.03.1-ce", "18.06.3-ce", "18.09.6"]

  return docker_versions.collect { version ->
    new Job("test-$version", {
      elasticProfileId = repo['elasticProfileForTests']
      tasks {
        exec { commandLine = ['bash', '-c', "curl -sL https://howtowhale.github.io/dvm/downloads/latest/install.sh | sh"] }
        exec { commandLine = ['bash', '-c', "source /go/.dvm/dvm.sh; dvm install $version"] }
        exec { commandLine = ['bash', '-c', "source /go/.dvm/dvm.sh; dvm use $version && docker swarm init && docker version && exec ./gradlew assemble check"] }
      }
    })
  }
}

def defaultElasticProfile = 'ecs-gocd-dev-build-dind'

def allRepos = [
    [ 'org': 'gocd-contrib', repo: 'email-notifier',                       elasticProfileForTests: 'ecs-gocd-dev-build-dind',   testJobs: javaTestJobs,   mainBranch: 'master' ],
    [ 'org': 'gocd-contrib', repo: 'google-oauth-authorization-plugin',    elasticProfileForTests: 'ecs-gocd-dev-build-dind',   testJobs: javaTestJobs,   mainBranch: 'master' ],
    [ 'org': 'gocd-contrib', repo: 'gitlab-oauth-authorization-plugin',    elasticProfileForTests: 'ecs-gocd-dev-build-dind',   testJobs: javaTestJobs,   mainBranch: 'master' ],
    [ 'org': 'gocd-contrib', repo: 'github-oauth-authorization-plugin',    elasticProfileForTests: 'ecs-gocd-dev-build-dind',   testJobs: javaTestJobs,   mainBranch: 'master' ],
    [ 'org': 'gocd-contrib', repo: 'gocd-groovy-dsl-config-plugin',        elasticProfileForTests: 'ecs-gocd-dev-build-dind',   testJobs: javaTestJobs,   mainBranch: 'master' ],
    [ 'org': 'gocd-contrib', repo: 'docker-elastic-agents-plugin',         elasticProfileForTests: 'ecs-dind-gocd-agent',       testJobs: dockerTestJobs, mainBranch: 'master' ],
    [ 'org': 'gocd-contrib', repo: 'docker-swarm-elastic-agent-plugin',    elasticProfileForTests: 'ecs-dind-gocd-agent',       testJobs: dockerTestJobs, mainBranch: 'master' ],
    [ 'org': 'gocd-contrib', repo: 'gitter-notifier',                      elasticProfileForTests: 'ecs-gocd-dev-build-dind',   testJobs: javaTestJobs,   mainBranch: 'master' ],
    [ 'org': 'gocd-contrib', repo: 'gitter-activity-feed-plugin',          elasticProfileForTests: 'ecs-gocd-dev-build-dind',   testJobs: javaTestJobs,   mainBranch: 'master' ],
    [ 'org': 'gocd-contrib', repo: 'gocd-build-status-notifier',           elasticProfileForTests: 'ecs-gocd-dev-build-dind',   testJobs: javaTestJobs,   mainBranch: 'master' ],
    [ 'org': 'gocd-contrib', repo: 'go-nuget-poller-plugin-2.0',           elasticProfileForTests: 'ecs-gocd-dev-build-dind',   testJobs: javaTestJobs,   mainBranch: 'master' ],
    [ 'org': 'gocd',         repo: 'gocd-yum-repository-poller-plugin',    elasticProfileForTests: 'ecs-gocd-dev-build-dind',   testJobs: javaTestJobs,   mainBranch: 'master' ],
    [ 'org': 'gocd',         repo: 'gocd-analytics-plugin',                elasticProfileForTests: 'ecs-plugin-build-postgres', testJobs: javaTestJobs,   mainBranch: 'main'  , envVars: [DB_USER: 'go'] ],
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
                tasks {
                  exec { commandLine = ['./gradlew', 'githubRelease'] }
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
                  exec { commandLine = ['./gradlew', 'githubRelease'] }
                }
              }
            }
          }
        }
      }
    }
  }
}
