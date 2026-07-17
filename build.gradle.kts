import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("org.jetbrains.intellij.platform")
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
  "egovframe-boot-simple-backend.zip",
  "egovframe-boot-web.zip",
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

tasks {
  test {
    useJUnitPlatform()
  }

  processResources {
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
