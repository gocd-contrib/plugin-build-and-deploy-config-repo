import cd.go.contrib.plugins.configrepo.groovy.dsl.GoCD
import cd.go.contrib.plugins.configrepo.groovy.dsl.Job

def allRepos = [
  "gocd-contrib": [
    "gocd-guest-login-plugin",
    "email-notifier",
    "google-oauth-authorization-plugin",
    "gitlab-oauth-authorization-plugin",
    "github-oauth-authorization-plugin",
    "gocd-groovy-dsl-config-plugin",
    "docker-elastic-agents",
    "docker-swarm-elastic-agents",
    "gitter-notifier",
    "gitter-activity-feed-plugin",
    "gocd-build-status-notifier",
    "go-nuget-poller-plugin-2.0"
  ],
  "gocd"        : [
    "gocd-file-based-secrets-plugin",
    "gocd-filebased-authentication-plugin",
    "kubernetes-elastic-agents",
    "docker-registry-artifact-plugin",
    "gocd-ldap-authentication-plugin",
    "gocd-yum-repository-poller-plugin"
  ]
]

def releaseCredentials = {
  return [
    GITHUB_TOKEN: 'AES:9Z9Lv85kry1oWWlOaCUF/w==:fWti8kD99VN7f++r7PfgLmXulS8GPmyb8bWm7yl1DYoDh1QihWEumO1mCfwiJ/O0',
  ]
}

def javaTestJobs = {
  [
    new Job("test", {
      elasticProfileId = "ecs-gocd-dev-build"
      tasks {
        exec { commandLine = ['./gradlew', 'assemble', 'check'] }
      }
    })
  ]
}

def docker_versions = ["17.03.2-ce", "17.06.2-ce", "17.09.1-ce", "17.12.1-ce", "18.03.1-ce", "18.06.3-ce", "18.09.6"]

def dockerTestJobs = {
  return docker_versions.collect { version ->
    new Job("test-$version", {
      elasticProfileId = "ecs-docker-in-docker"
      tasks {
        exec { commandLine = ['bash', '-c', "sudo dvm install $version"] }
        exec { commandLine = ['./gradlew', 'assemble', 'check'] }
      }
    })
  }
}

def testJobs = { repo ->
  return [
    "docker-elastic-agents",
    "docker-swarm-elastic-agents"
  ].contains(repo) ? dockerTestJobs() : javaTestJobs()
}

GoCD.script {
  pipelines {
    allRepos.each { org, repos ->
      repos.each { repo ->
        pipeline("${org}-${repo}-pr") {
          materials {
            githubPR("$repo-material") {
              url = "https://git.gocd.io/git/$org/$repo"
            }
          }
          group = "gocd" == org ? "supported-plugins-pr" : "plugins-pr"
          stages {
            stage("test") {
              jobs {
                addAll(testJobs(repo))
              }
            }
          }
        }
        pipeline("${org}-${repo}") {
          materials {
            git {
              url = "https://git.gocd.io/git/${org}/${repo}"
              shallowClone = false
            }
          }
          group = "gocd" == org ? "supported-plugins" : "plugins"
          stages {
            stage("test") {
              jobs {
                addAll(testJobs(repo))
              }
            }

            stage("github-preview-release") {
              environmentVariables = [GITHUB_USER: org]
              secureEnvironmentVariables = releaseCredentials()
              jobs {
                job("create-preview-release") {
                  elasticProfileId = "ecs-gocd-dev-build"
                  tasks {
                    exec { commandLine = ['./gradlew', 'githubRelease'] }
                  }
                }
              }
            }

            stage("github-release") {
              approval { type = 'manual' }
              environmentVariables = [GITHUB_USER: org, PRERELEASE: "NO"]
              secureEnvironmentVariables = releaseCredentials()
              jobs {
                job("create-release") {
                  elasticProfileId = "ecs-gocd-dev-build"
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
}
