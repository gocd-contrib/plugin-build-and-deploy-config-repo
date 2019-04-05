def allRepos = [
  "gocd-contrib": [
    "gocd-guest-login-plugin",
    "email-notifier",
    "google-oauth-authorization-plugin",
    "gitlab-oauth-authorization-plugin",
    "github-oauth-authorization-plugin",
    "gocd-groovy-dsl-config-plugin"
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

GoCD.script {
  pipelines {
    allRepos.each { org, repos ->
      repos.each { repo ->
        pipeline("plugin-${org}-${repo}") {
          materials {
            git {
              url = "https://git.gocd.io/git/${org}/${repo}"
              shallowClone = false
            }
          }
          group = "gocd" == org ? "supported_plugins" : "plugins"
          stages {
            stage("test") {
              jobs {
                job("test") {
                  elasticProfileId = "ecs-gocd-dev-build"
                  tasks {
                    exec { commandLine = ['./gradlew', 'assemble', 'check'] }
                  }
                }
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