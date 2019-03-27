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
          group = "plugins"
          stages {
            stage("test") {
              jobs {
                job("test") {
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
