/*
 * Copyright (c) 2019 Risk Focus Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

folder("${NAMESPACE}") {
  description("""<table><tr><td>
Folder for environment - $NAMESPACE
<ul>
${CADMIUM.links.collect { title, template -> "<li><a href='${myTemplate(template)}'>${title}</a>" }.join()}
</ul>
<td><p>Env creation time parameters:
<small><pre>${ORIG_PARAMS_MAP.sort().toString().replace(', ', ',\n')}</pre></small>
</p>
<!-- td><p>Flavor parameters:
<small><pre>${FLAVOR_PARAMS_MAPS.sort().toString().replace(', ', ',\n')}</pre></small>
</p></td -->
</table>
""")
  properties {
    folderProperties {
      properties {
        stringProperty {
          key('NAMESPACE')
          value(NAMESPACE)
        }
        stringProperty {
          key('SCHEDULE_NAME')
          value(SCHEDULE_NAME)
        }
      }
    }
  }
}

CADMIUM.apps.each { app, settings ->
  if (settings.build) {
    switch (settings.build.type) {
      case "jenkinsfile":
        def parsedPath = new URI(settings.repo).getPath().split('/')
        if (parsedPath.size() < 2) {
          throw new Exception("Can't parse github repo url: ${settings.repo}")
        }
        def repoOwner = parsedPath[-2]
        def repoName = parsedPath[-1] - '.git'
        if (settings.folder) {
          folder("${NAMESPACE}/${settings.folder}") {
            jenkinsfileTypeJob "${NAMESPACE}/${settings.folder}/${app}", repoOwner, repoName, settings.repo, settings.build.location
          }
        } else {
          jenkinsfileTypeJob "${NAMESPACE}/${app}", repoOwner, repoName, settings.repo, settings.build.location
        }
        break
      case "inline":
        if (settings.folder) {
          folder("${NAMESPACE}/${settings.folder}") {
            inlineTypeJob "${NAMESPACE}/${settings.folder}/${app}", settings.build.script
          }
        } else {
            inlineTypeJob "${NAMESPACE}/${app}", settings.build.script
        }
        break
    }
  }
}

if (CADMIUM.settings?.enableRecreateCluster)
  pipelineJob("$NAMESPACE/Re-create Cluster") {
    definition {
      cps {
        sandbox(true)
        script("""
                    stage("Re-create Cluster") {
                        // TODO Handle Booleans
                        build job: '../Infrastructure/Create',
                            parameters: ${ORIG_PARAMS_MAP.collect { k, v -> "\n stringParam(name: '$k', value: \$/$v/\$)" }},
                            wait: true
                    }
                """.stripIndent())
      }
    }
  }

pipelineJob("$NAMESPACE/Destroy Environment") {
  parameters {
    booleanParam('DELETE_JOB_FOLDER', true)
  }
  definition {
    cps {
      sandbox(true)

      DESTROY_ENV_STAGE = ""

      switch (CADMIUM.undeploy.type) {
        case "job":
          DESTROY_ENV_STAGE = """
            stage("Force destroy Environment") {
              build job: '${CADMIUM.undeploy.jobName}',
                  wait: true
            }
            """
          break
        case "inline":
          DESTROY_ENV_STAGE = """
            stage("Force destroy Environment") {
              ${CADMIUM.undeploy.script}
            }
            """
          break
      }

      script("""
                node() {
                    ${DESTROY_ENV_STAGE}
                    stage("Destroy Infrastructure") {
                        build job: '/Infrastructure/Destroy',
                            parameters: [
                                string(name: 'NAMESPACE', value: '$NAMESPACE'),
                                string(name: 'ENVIRONMENT_TYPE', value: '$ENVIRONMENT_TYPE')
                            ],
                            wait: true

                        if (params.DELETE_JOB_FOLDER)
                            Jenkins.instance.getItemByFullName('$NAMESPACE').delete()
                    }
                }
                """.stripIndent())
    }
  }
}

def jenkinsfileTypeJob(GString envName, inputRepoOwner, inputRepoName, inputRepoUrl, String script = 'Jenkinsfile') {
  multibranchPipelineJob(envName) {
    branchSources {
      branchSource {
        source {
          github {
            id('origin')
            repositoryUrl(inputRepoUrl)
            repoOwner(inputRepoOwner)
            repository(inputRepoName)
            configuredByUrl(false)
            credentialsId('cadmium')
            traits {
              // TODO figure out proper syntax to invoke this: cleanBeforeCheckoutTrait(cleanBeforeCheckout())
              gitHubBranchDiscovery {
                strategyId(1)
              }
              gitHubTagDiscovery()
            }
          }
        }
        strategy {
          defaultBranchPropertyStrategy {
            props {
              // Suppresses the normal SCM commit trigger coming from branch indexing.
              noTriggerBranchProperty()
            }
          }
        }
      }
    }
    orphanedItemStrategy {
      discardOldItems()
    }
    triggers {
      periodic(1440)
    }
    factory {
      workflowBranchProjectFactory {
        // Relative location within the checkout of your Pipeline script.
        scriptPath(script)
      }
    }
    configure {
      def traits = it / sources / data / 'jenkins.branch.BranchSource' / source / traits
      traits << 'org.jenkinsci.plugins.github__branch__source.OriginPullRequestDiscoveryTrait' { strategyId(1) }
    }
  }
}

def myTemplate(template) {
  vars = [
    ENV_FQDN: "${NAMESPACE}.${PROJECT_PREFIX}.${GLOBAL_FQDN ?: 'example.com'}",
    ENV     : "${NAMESPACE}"
  ]
  template.replaceAll(/\$\{(\w+)\}/) { k -> vars[k[1]] ?: k[0] }
}


def inlineTypeJob(jobPath, jobScript) {
  pipelineJob(jobPath) {
    definition {
      cps {
        sandbox(true)
        script("""
          node() {
            stage("Run job script") {
              ${jobScript}
            }
          }
          """.stripIndent())
      }
    }
  }
}