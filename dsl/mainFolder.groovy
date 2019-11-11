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

String folderName = "Infrastructure"

folder(folderName) {
  description("Main folder")
}

pipelineJob("$folderName/Create") {
  definition {
    cpsScm {
      scm {
        git {
          branch(REPO_BRANCH)
          remote {
            name 'origin'
            credentials('cadmium')
            url(REPO_URL)
          }
        }
        scriptPath("Jenkinsfile.create")
      }
    }
  }
}

pipelineJob("$folderName/Destroy") {
  parameters {
    stringParam("NAMESPACE", "", "Environment name")
    stringParam("ENVIRONMENT_TYPE", "", "Platform type")
  }
  definition {
    cpsScm {
      scm {
        git {
          branch(REPO_BRANCH)
          remote {
            name 'origin'
            credentials("cadmium")
            url(REPO_URL)
          }
        }
        scriptPath("Jenkinsfile.destroy")
      }
    }
  }
}
