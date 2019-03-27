def allRepos = [
  "gocd-contrib": [
    "gocd-guest-login-plugin",
    "email-notifier"
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
                      commandLine = ['./gradle', 'assemble', 'check']
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
