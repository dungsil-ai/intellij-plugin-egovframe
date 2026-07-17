package kr.kyg.ijplugin.egovframe.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.nio.file.Path

class MavenProjectLinkerImpl : MavenProjectLinker {
  override fun link(project: Project, pomPath: Path) {
    val pomFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(pomPath) ?: return
    MavenProjectsManager.getInstance(project).addManagedFiles(listOf(pomFile))
  }
}
