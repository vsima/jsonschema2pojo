/**
 * Copyright © 2010-2014 Nokia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jsonschema2pojo.gradle

import org.jsonschema2pojo.Jsonschema2Pojo
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

/**
 * A task that performs code generation.
 *
 * @author Ben Manes (ben.manes@gmail.com)
 */
class GenerateJsonSchemaTask extends DefaultTask {
  def configuration

  enum AndroidProject  { APP, LIBRARY }

  GenerateJsonSchemaTask() {
    description = 'Generates Java classes from a json schema.'
    group = 'Build'

    outputs.upToDateWhen { false }

    project.afterEvaluate {
      configuration = project.jsonSchema2Pojo.configList
      configuration.each { config ->
        println config
        config.targetDirectory = config.targetDirectory ?:
          project.file("${project.buildDir}/generated-sources/js2p")

        if (project.plugins.hasPlugin('java')) {
          configureJava()
        } else if (project.plugins.hasPlugin('com.android.application')) {
          configureAndroid(AndroidProject.APP)
        } else if (project.plugins.hasPlugin('com.android.library')) {
          configureAndroid(AndroidProject.LIBRARY)
        } else {
          throw new GradleException('generateJsonSchema: Java or Android plugin required')
        }
        outputs.dir config.targetDirectory
      }
    }
  }

  def configureJava() {
    configuration.each { config ->
      project.sourceSets.main.java.srcDirs += [ config.targetDirectory ]
      dependsOn(project.tasks.processResources)
      project.tasks.compileJava.dependsOn(this)

      if (!config.source.hasNext()) {
        config.source = project.files("${project.sourceSets.main.output.resourcesDir}/json")
        config.sourceFiles.each { it.mkdir() }
      }
    }
  }

  def configureAndroid(AndroidProject androidProject) {
    def android = project.extensions.android
    configuration.each { config ->
      android.sourceSets.main.java.srcDirs += [ config.targetDirectory ]

      android.(getVariantProperty(androidProject)).all { variant ->
        dependsOn("process${variant.name.capitalize()}Resources")
        variant.javaCompile.dependsOn(this)
      }

      if (!config.source.hasNext()) {
        config.sourceFiles = project.files(
          android.sourceSets.main.resources.srcDirs.collect {
            "${it}/json"
          }.findAll {
            project.file(it).exists()
          })
      }
    }
  }

  def getVariantProperty(AndroidProject androidProject) {
    switch(androidProject) {
      case AndroidProject.APP:
        return "applicationVariants"
      case AndroidProject.LIBRARY:
        return "libraryVariants"
      default:
        throw new IllegalArgumentException("Passed an invalid android project type")
    }
  }

  @TaskAction
  def generate() {
    configuration.each { config ->
      logger.info 'Using this configuration:\n{}', config
      Jsonschema2Pojo.generate(config)
    }
  }
}
