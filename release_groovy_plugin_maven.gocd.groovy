def secretParam = { String param ->
  return "{{SECRET:[build-pipelines][$param]}}".toString()
}

String org = "gocd-contrib"
String repo = "gocd-groovy-dsl-config-plugin"

GoCD.script {
  pipelines {
    pipeline('upload-groovy-plugin-to-maven') {
      group = 'plugins'
      labelTemplate = '${COUNT}'
      lockBehavior = 'none'
      environmentVariables = [
        GIT_USER: 'gocd-ci-user',
        GIT_PASSWORD: secretParam("GOCD_CI_USER_RELEASE_TOKEN")
      ]
      materials {
        svn('signing-keys') {
          url = "https://github.com/gocd-private/signing-keys/trunk"
          username = "gocd-ci-user"
          password = secretParam("GOCD_CI_USER_TOKEN_WITH_REPO_ACCESS")
          destination = "signing-keys"
        }
        git('groovy') {
          url = "https://git.gocd.io/git/${org}/${repo}"
          shallowClone = false
          destination = 'groovy'
        }
        dependency('GroovyPipeline') {
          pipeline = "${org}-${repo}"
          stage = 'github-release'
        }
      }
      stages {
        stage('upload') {
          artifactCleanupProhibited = false
          cleanWorkingDir = true
          fetchMaterials = true
          environmentVariables = [
            'GNUPGHOME'              : '.signing',
            'GOCD_GPG_KEYRING_FILE'  : 'signing-key.gpg',
            'GOCD_GPG_PASSPHRASE'    : secretParam("GOCD_GPG_PASSPHRASE"),
            'MAVEN_NEXUS_USERNAME'   : 'arvindsv',
            'MAVEN_NEXUS_PASSWORD'   : secretParam("ARVINDSV_NEXUS_PASSWORD")
          ]
          secureEnvironmentVariables = [
            GOCD_GPG_KEY_ID: 'AES:+ORNmqROtoiLtfp+q4FlfQ==:PxQcI6mOtG4J/WQHS9jakg=='
          ]
          jobs {
            job('upload-to-maven') {
              elasticProfileId = 'ecs-gocd-dev-build'
              tasks {
                bash {
                  commandString = 'git pull && mkdir -p ${GNUPGHOME}'
                  workingDir = "groovy"
                }
                bash {
                  commandString = 'echo ${GOCD_GPG_PASSPHRASE} > gpg-passphrase'
                  workingDir = "groovy"
                }
                bash {
                  commandString = 'gpg --quiet --batch --passphrase-file gpg-passphrase --output - ../signing-keys/gpg-keys.pem.gpg | gpg --import --batch --quiet'
                  workingDir = "groovy"
                }
                bash {
                  commandString = 'gpg --export-secret-keys ${GOCD_GPG_KEY_ID} > ${GOCD_GPG_KEYRING_FILE}'
                  workingDir = "groovy/dsl"
                }
                bash {
                  commandString = './gradlew clean dsl:uploadArchives closeAndReleaseRepository'
                  workingDir = "groovy"
                }
              }
            }
          }
        }
      }
    }
  }
}
