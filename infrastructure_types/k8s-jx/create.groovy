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

def label = "cadmium-${UUID.randomUUID().toString()}"
podTemplate(label: label, inheritFrom: 'jx-base', serviceAccount: 'jenkins') {
  node(label) {
    stage('Create Environment/Namespace') {
      container('jx-base') {
        sh "jx ns --create ${params.NAMESPACE}"
        sh "kubectl label ns ${params.NAMESPACE} --overwrite schedule=${schedule}"
      }
    }
  }
}
