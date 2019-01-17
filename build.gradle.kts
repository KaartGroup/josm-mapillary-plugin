import com.github.spotbugs.SpotBugsTask
import net.ltgt.gradle.errorprone.*
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.openstreetmap.josm.gradle.plugin.config.I18nConfig
import org.openstreetmap.josm.gradle.plugin.config.JosmManifest
import org.openstreetmap.josm.gradle.plugin.task.MarkdownToHtml
import java.net.URL

plugins {
  id("org.sonarqube") version "2.6.2"
  id("org.openstreetmap.josm") version "0.5.3"
  id("com.github.ben-manes.versions") version "0.20.0"
  id("com.github.spotbugs") version "1.6.4"
  id("net.ltgt.errorprone") version "0.6"

  eclipse
  jacoco
  java
  `maven-publish`
  pmd
}

repositories {
  jcenter()
}

// Set up ErrorProne
dependencies {
  errorprone("com.google.errorprone:error_prone_core:2.3.2")
  if (!JavaVersion.current().isJava9Compatible) {
    errorproneJavac("com.google.errorprone:javac:9+181-r4173-1")
  }
}
tasks.withType(JavaCompile::class).configureEach {
  options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial"))
  options.errorprone {
    check("ClassCanBeStatic", CheckSeverity.ERROR)
    check("StringEquality", CheckSeverity.ERROR)
    check("WildcardImport", CheckSeverity.ERROR)
    check("MethodCanBeStatic", CheckSeverity.WARN)
    check("RemoveUnusedImports", CheckSeverity.WARN)
    check("PrivateConstructorForUtilityClass", CheckSeverity.WARN)
    check("LambdaFunctionalInterface", CheckSeverity.WARN)
    check("ConstantField", CheckSeverity.WARN)
  }
}

apply(from = "gradle/tool-config.gradle")

java.sourceCompatibility = JavaVersion.VERSION_1_8
base.archivesBaseName = "Mapillary"

dependencies {
  //implementation(files("../thirdparties/jcef/src/"))
 // implementation(files("../thirdparties/jcef/src/jcef_build/native/Release/jcef.jar"))
  implementation(fileTree("../thirdparties/jcef/src/jcef_build/native/Release"))
  testImplementation("org.openstreetmap.josm:josm-unittest:SNAPSHOT") { isChanging = true }
  testImplementation("com.github.tomakehurst:wiremock:2.19.0")
  val junitVersion = "5.3.1"
  testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
  testImplementation("org.junit.vintage:junit-vintage-engine:$junitVersion")
  testImplementation("org.awaitility:awaitility:3.1.2")
  testImplementation("org.jmockit:jmockit:1.42")
  testImplementation("com.github.spotbugs:spotbugs-annotations:3.1.7")
}

sourceSets {
  getByName("test") {
    java {
      setSrcDirs(listOf("test/unit"))
    }
    resources {
      setSrcDirs(listOf("test/data"))
    }
  }
}

tasks {
  "processResources"(ProcessResources::class) {
    from(project.projectDir) {
      include("LICENSE")
      include("LICENSE_*")
    }
  }
}

tasks.create("md2html", MarkdownToHtml::class) {
  destDir = File(buildDir, "md2html")
  source(projectDir)
  include("README.md", "LICENSE.md")
  tasks.withType(ProcessResources::class)["processResources"].from(this)
}

josm {
  debugPort = 7051
  manifest {
    // See https://floscher.github.io/gradle-josm-plugin/kdoc/current/gradle-josm-plugin/org.openstreetmap.josm.gradle.plugin.config/-josm-manifest/old-version-download-link.html
    oldVersionDownloadLink(13733, "v1.5.15", URL("https://github.com/JOSM/Mapillary/releases/download/v1.5.15/Mapillary.jar"))
    oldVersionDownloadLink(13643, "v1.5.14", URL("https://github.com/JOSM/Mapillary/releases/download/v1.5.14/Mapillary.jar"))
    oldVersionDownloadLink(13558, "v1.5.12+pre13643", URL("https://github.com/JOSM/Mapillary/releases/download/v1.5.12%2Bpre13643/Mapillary.jar"))
    oldVersionDownloadLink(12987, "v1.5.10", URL("https://github.com/JOSM/Mapillary/releases/download/v1.5.10/Mapillary.jar"))
    oldVersionDownloadLink(12675, "v1.5.7", URL("https://github.com/JOSM/Mapillary/releases/download/v1.5.7/Mapillary.jar"))
    oldVersionDownloadLink(12128, "v1.5.5", URL("https://github.com/JOSM/Mapillary/releases/download/v1.5.5/Mapillary.jar"))
    oldVersionDownloadLink(10824, "v1.5.3", URL("https://github.com/JOSM/Mapillary/releases/download/v1.5.3/Mapillary.jar"))
  }
  i18n {
    pathTransformer = getPathTransformer("gitlab.com/JOSM/Mapillary/blob")
  }
}

eclipse {
  project {
    name = "JOSM-Mapillary"
    comment = josm.manifest.description
    natures("org.sonarlint.eclipse.core.sonarlintNature", "ch.acanda.eclipse.pmd.builder.PMDNature", "org.eclipse.buildship.core.gradleprojectnature")
    buildCommand("org.sonarlint.eclipse.core.sonarlintBuilder")
    buildCommand("ch.acanda.eclipse.pmd.builder.PMDBuilder")
    buildCommand("org.eclipse.buildship.core.gradleprojectbuilder")
  }
}
tasks["eclipseClasspath"].dependsOn("cleanEclipseClasspath")
tasks["eclipseProject"].dependsOn("cleanEclipseProject")
tasks["eclipse"].setDependsOn(setOf("eclipseClasspath", "eclipseProject"))

tasks.withType(JavaCompile::class) {
  // Character encoding of Java files
  options.encoding = "UTF-8"
}
tasks.withType(Javadoc::class) {
  isFailOnError = false
}
tasks.withType(SpotBugsTask::class) {
  reports {
    xml.isEnabled = false
    html.isEnabled = true
  }
}

tasks {
  "test"(Test::class) {
    project.afterEvaluate {
      jvmArgs("-javaagent:${classpath.find { it.name.contains("jmockit") }!!.absolutePath}")
    }
    testLogging {
      exceptionFormat = TestExceptionFormat.FULL
      events = setOf(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
      showCauses = true

      info {
        events = setOf(TestLogEvent.STARTED, TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED, TestLogEvent.STANDARD_OUT, TestLogEvent.STANDARD_ERROR)
        showStandardStreams = true
      }
    }
  }
}

// Configuration for publishing to Maven repository at gitlab.com/JOSM/plugins
val PROJECT_ID = 8611940 // repository JOSM/plugins
val PERSONAL_ACCESS_TOKEN = System.getenv("PERSONAL_ACCESS_TOKEN")

publishing {
  repositories {
    maven("https://gitlab.com/api/v4/projects/$PROJECT_ID/packages/maven") {
      if (PERSONAL_ACCESS_TOKEN != null) {
        credentials(HttpHeaderCredentials::class) {
          name = "Private-Token"
          value = PERSONAL_ACCESS_TOKEN
        }
        authentication {
          removeAll { a -> true }
          create("auth", HttpHeaderAuthentication::class)
        }
      }
    }
  }
  publications {
    create("plugins", MavenPublication::class) {
      artifactId = base.archivesBaseName
      groupId = "org.openstreetmap.josm.plugins"
      version = project.version.toString()
      from(components["java"])
      pom {
        name.set("JOSM-${base.archivesBaseName}")
        description.set("The Mapillary plugin for JOSM")
        url.set("https://gitlab.com/JOSM/Mapillary")
        licenses {
          license {
            name.set("GNU General Public License Version 2")
            url.set("https://www.gnu.org/licenses/old-licenses/gpl-2.0")
          }
        }
        scm {
          connection.set("scm:git:git://gitlab.com/JOSM/Mapillary.git")
          developerConnection.set("scm:git:ssh://gitlab.com/JOSM/Mapillary.git")
          url.set("https://gitlab.com/JOSM/Mapillary")
        }
      }
    }
  }
}
