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

folder("${ENVIRONMENT_NAME}") {
  description("""<table><tr><td>
Folder for environment - $ENVIRONMENT_NAME
<ul>
${CADMIUM.links.collect { title, template -> "<li><a href='${myTemplate(template)}'>${title}</a>" }.join()}
</ul>
<td><p>Env creation time parameters:
<small><pre>${ORIG_PARAMS_MAP.sort().toString().replace(', ', ',\n')}</pre></small>
</p>
<td><p>Flavor parameters:
<small><pre>${FLAVOR_PARAMS_MAPS.sort().toString().replace(', ', ',\n')}</pre></small>
</p></td>
</table>
""")
}

CADMIUM.apps.each { app, settings ->
  if (settings.build && settings.build.type == "jenkinsfile") {
    jenkinsfileTypeJob("${ENVIRONMENT_NAME}/${app}", settings.repo, settings.build.location ?: 'Jenkinsfile')
  }
}

if (CADMIUM.settings?.enableRecreateCluster)
  pipelineJob("$ENVIRONMENT_NAME/Re-create Cluster") {
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

pipelineJob("$ENVIRONMENT_NAME/Destroy Environment") {
  parameters {
    booleanParam('DELETE_JOB_FOLDER', true)
  }
  definition {
    cps {
      sandbox(true)

      DESTROY_ENV_STAGE = ""
      if (CADMIUM.undeploy.type == "job") {
        DESTROY_ENV_STAGE = """
            stage("Force destroy Environment") {
              build job: '${CADMIUM.undeploy.jobName}',
                  wait: true
            }
            """
      }
      script("""
                node() {
                    ${DESTROY_ENV_STAGE}
                    stage("Destroy Infrastructure") {
                        build job: '/Infrastructure/Destroy',
                            parameters: [
                                string(name: 'ENVIRONMENT_NAME', value: '$ENVIRONMENT_NAME'),
                                string(name: 'ENVIRONMENT_TYPE', value: '$ENVIRONMENT_TYPE')
                            ],
                            wait: true

                        if (params.DELETE_JOB_FOLDER)
                            Jenkins.instance.getItemByFullName('$ENVIRONMENT_NAME').delete()
                    }
                }
                """.stripIndent())
    }
  }
}

def jenkinsfileTypeJob(GString envName, repo, String script) {
  multibranchPipelineJob(envName) {
    branchSources {
      branchSource {
        source {
          git {
            id 'origin'
            remote(repo)
            credentialsId('cadmium')
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
      traits << 'jenkins.plugins.git.traits.BranchDiscoveryTrait' {}
    }
  }
}

def myTemplate(template) {
  vars = [
      ENV_FQDN: "${ENVIRONMENT_NAME}.${PROJECT_PREFIX}.${GLOBAL_FQDN ?: 'example.com'}",
      ENV     : "${ENVIRONMENT_NAME}"
  ]
  template.replaceAll(/\$\{(\w+)\}/) { k -> vars[k[1]] ?: k[0] }
}
