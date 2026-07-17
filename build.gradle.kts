import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("org.jetbrains.intellij.platform")
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
  jvmToolchain(21)
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

  testImplementation(kotlin("test"))
  testImplementation("junit:junit:4.13.2")

  intellijPlatform {
    intellijIdeaCommunity(providers.gradleProperty("platformVersion").get())

    bundledPlugin("com.intellij.java")
    bundledPlugin("org.jetbrains.idea.maven")

    testFramework(TestFrameworkType.Platform)
    pluginVerifier()
  }
}

intellijPlatform {
  pluginConfiguration {
    id = "kr.kyg.intellij.plugin.egovframe"
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
    useJUnit()
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
