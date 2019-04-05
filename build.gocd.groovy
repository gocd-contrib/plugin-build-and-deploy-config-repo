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
    "docker-swarm-elastic-agents"
  ],
  "gocd"        : [
    "gocd-file-based-secrets-plugin",
    "gocd-filebased-authentication-plugin",
    "kubernetes-elastic-agents",
    "docker-registry-artifact-plugin"
  ]
]

def releaseCredentials = { org ->
  return [
    GITHUB_USER : org,
    GITHUB_TOKEN: 'foo',
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

def docker_versions = ["17.03.0", "17.03.1", "17.03.2", "17.06.0", "17.06.1", "17.06.2", "17.09.0", "17.09.1", "17.12.0", "17.12.1",
                       "18.03.0", "18.03.1", "18.06.0", "18.06.1", "18.06.2", "18.06.3", "18.09.0", "18.09.1", "18.09.2", "18.09.3", "18.09.4"]

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
              environmentVariables = releaseCredentials(org)
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
              environmentVariables = releaseCredentials(org) + ["PRERELEASE": "NO"]
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