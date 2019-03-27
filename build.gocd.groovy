def allRepos = [
  "gocd-contrib": [
    "gocd-guest-login-plugin",
    "email-notifier",
    "google-oauth-authorization-plugin",
    "gitlab-oauth-authorization-plugin",
    "github-oauth-authorization-plugin",
  ],
  "gocd": [

  ]
]

GoCD.script {
  pipelines {
    allRepos.each { org, repos ->
      repos.each { repo ->
        pipeline("plugin-${org}-${repo}") {
          materials {
            git {
              url = "https://git.gocd.io/git/${org}/${repo}"
            }
          }
          group = "plugins"
          stages {
            stage("test") {
              jobs {
                job("test") {
                  elasticProfileId = "ecs-gocd-dev-build"
                  tasks {
                    exec {
                      commandLine = ['./gradlew', 'assemble', 'check']
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
}
