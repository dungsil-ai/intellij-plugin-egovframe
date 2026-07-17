package kr.kyg.ijplugin.egovframe.project

import com.intellij.openapi.project.Project
import java.nio.file.Path

interface MavenProjectLinker {
  fun link(project: Project, pomPath: Path)
}
