import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.WriteProperties
import org.gradle.work.DisableCachingByDefault
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream


plugins {
  id("org.jetbrains.kotlin.jvm")
  id("org.jetbrains.intellij.platform")
}
@DisableCachingByDefault(because = "Validates a generated archive without producing outputs")
abstract class VerifyPluginDistribution : DefaultTask() {

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val archiveFile: RegularFileProperty

  @get:Input
  abstract val requiredClasses: SetProperty<String>

  @TaskAction
  fun verifyBundledRuntimeClasses() {
    val archive = archiveFile.get().asFile
    val missing = requiredClasses.get().toMutableSet()

    ZipFile(archive).use { pluginZip ->
      val handlebarsJar = pluginZip.entries().asSequence().firstOrNull { entry ->
        !entry.isDirectory &&
          entry.name.substringAfterLast('/').startsWith("handlebars-") &&
          entry.name.endsWith(".jar")
      } ?: throw GradleException(
        "Marketplace ZIP does not bundle Handlebars.java. Upload the build/distributions ZIP, never build/libs JARs.",
      )

      ZipInputStream(pluginZip.getInputStream(handlebarsJar)).use { nestedJar ->
        while (missing.isNotEmpty()) {
          val entry = nestedJar.nextEntry ?: break
          missing.remove(entry.name)
        }
      }
    }

    if (missing.isNotEmpty()) {
      throw GradleException("Marketplace ZIP is missing Handlebars runtime classes: ${missing.sorted().joinToString()}")
    }

    logger.lifecycle("Verified Marketplace upload artifact: ${archive.absolutePath}")
  }
}


group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
  jvmToolchain(21)
  compilerOptions {
    jvmDefault = JvmDefaultMode.NO_COMPATIBILITY
  }
}

val upstreamDir = layout.projectDirectory.dir("vendor/egovframe-vscode-initializr")
val bundledUpstreamZips = listOf(
  "egovframe-ai-rag-langchain4j.zip",
  "egovframe-ai-rag-springai.zip",
  "egovframe-boot-batch-db-commandline.zip",
  "egovframe-boot-batch-db-scheduler.zip",
  "egovframe-boot-batch-db-web.zip",
  "egovframe-boot-batch-file-commandline.zip",
  "egovframe-boot-batch-file-scheduler.zip",
  "egovframe-boot-batch-file-web.zip",
  "egovframe-boot-simple-backend.zip",
  "egovframe-boot-simple-frontend.zip",
  "egovframe-boot-web.zip",
  "egovframe-mobile-common-components.zip",
  "egovframe-mobile-deviceapi.zip",
  "egovframe-mobile-web.zip",
  "egovframe-msa-common-components.zip",
  "egovframe-msa-portal-backend.zip",
  "egovframe-msa-portal-frontend.zip",
  "egovframe-template-common-components.zip",
  "egovframe-template-enterprise.zip",
  "egovframe-template-portal.zip",
  "egovframe-template-simple.zip",
  "egovframe-web.zip",
)


dependencies {
  // Renderer parity with upstream VS Code extension (handlebars.js 4.7.9).
  // ANTLR is shaded inside the artifact; slf4j is bundled by the IDE.
  implementation("com.github.jknack:handlebars:4.5.3") {
    exclude(group = "org.slf4j")
  }

  testImplementation(platform("org.junit:junit-bom:5.14.4"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  // IntelliJ 2025.1's JUnit 5 test environment still loads JUnit 4 TestRule (IJPL-159134).
  testRuntimeOnly("junit:junit:4.13.2")

  intellijPlatform {
    when (providers.gradleProperty("platformType").get()) {
      "IC" -> intellijIdeaCommunity(providers.gradleProperty("platformVersion").get())
      "IU" -> intellijIdea(providers.gradleProperty("platformVersion").get())
      else -> error("platformType must be IC or IU")
    }

    bundledPlugin("com.intellij.java")
    bundledPlugin("org.jetbrains.idea.maven")

    pluginVerifier()
  }
}

intellijPlatform {
  pluginConfiguration {
    id = "kr.kyg.ijplugin.egovframe"
    name = providers.gradleProperty("pluginName").get()
    version = providers.gradleProperty("pluginVersion").get()

    ideaVersion {
      sinceBuild = "251"
      untilBuild = provider { null }
    }
  }

  pluginVerification {
    ides {
      recommended()
      create(IntelliJPlatformType.IntellijIdea, "2026.1.4")
      create(IntelliJPlatformType.IntellijIdea, "2026.2")
    }
    freeArgs = listOf("-mute", "TemplateWordInPluginId,TemplateWordInPluginName")
  }
  signing {
    certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
    privateKey = providers.environmentVariable("PRIVATE_KEY")
    password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
  }

  publishing {
    token = providers.environmentVariable("PUBLISH_TOKEN")
  }
}
val generatePluginMetadata = tasks.register<WriteProperties>("generatePluginMetadata") {
  destinationFile = layout.buildDirectory.file("generated/pluginMetadata/plugin-metadata.properties").get().asFile
  property("version", providers.gradleProperty("pluginVersion").get())
}


tasks {
  buildPlugin {
    archiveFileName.set("${project.name}-${project.version}-marketplace.zip")
  }

  val verifyMarketplaceArtifact = register<VerifyPluginDistribution>("verifyMarketplaceArtifact") {
    group = "verification"
    description = "Checks that the Marketplace ZIP contains the Handlebars runtime classes."
    archiveFile.set(buildPlugin.flatMap { it.archiveFile })
    requiredClasses.set(
      setOf(
        "com/github/jknack/handlebars/Context.class",
        "com/github/jknack/handlebars/Context\$Builder.class",
        "com/github/jknack/handlebars/EscapingStrategy.class",
        "com/github/jknack/handlebars/Handlebars.class",
        "com/github/jknack/handlebars/Handlebars\$SafeString.class",
        "com/github/jknack/handlebars/Helper.class",
        "com/github/jknack/handlebars/Options.class",
        "com/github/jknack/handlebars/ValueResolver.class",
        "com/github/jknack/handlebars/context/JavaBeanValueResolver.class",
        "com/github/jknack/handlebars/context/MapValueResolver.class",
        "com/github/jknack/handlebars/context/MethodValueResolver.class",
      ),
    )
  }

  buildPlugin {
    finalizedBy(verifyMarketplaceArtifact)
  }

  check {
    dependsOn(verifyMarketplaceArtifact)
  }

  signPlugin {
    dependsOn(verifyMarketplaceArtifact)
  }

  publishPlugin {
    dependsOn(verifyMarketplaceArtifact)
  }

  test {
    useJUnitPlatform {
      excludeTags("remoteZip")
    }
  }

  register<Test>("remoteZipTest") {
    group = "verification"
    description = "Downloads and verifies all 20 remote upstream template ZIPs."
    useJUnitPlatform {
      includeTags("remoteZip")
    }
  }

  register<Test>("symlinkTest") {
    group = "verification"
    description = "Runs symlink security tests in strict mode (requires OS symlink privileges)."
    useJUnitPlatform()
    systemProperty("egovframe.test.symlink.strict", "true")
  }

  processResources {
    from(generatePluginMetadata) {
      into("egovframe")
    }
    from(upstreamDir.dir("templates/code")) {
      into("egovframe/code")
    }
    from(upstreamDir.dir("templates/config")) {
      into("egovframe/config")
    }
    from(upstreamDir.dir("templates/projects/pom")) {
      into("egovframe/projects/pom")
    }
    from(upstreamDir.dir("templates")) {
      include("templates-projects.json", "templates-context-xml.json")
      into("egovframe")
    }
    from(upstreamDir.dir("templates/projects/examples")) {
      include(bundledUpstreamZips)
      into("egovframe/projects/examples")
    }
  }

  runIde {
    jvmArgs(
      "-Didea.accept.eula=true",
      "-Djb.consents.confirmation.enabled=false",
      "-Didea.initially.ask.config=false",
    )
  }
}
