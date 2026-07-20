package kr.kyg.ijplugin.egovframe.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.nio.file.Path

class MavenProjectLinkerImpl : MavenProjectLinker {
  override fun link(project: Project, pomPath: Path) {
    val pomFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(pomPath) ?: return
    val manager = MavenProjectsManager.getInstance(project)
    manager.addManagedFiles(listOf(pomFile))
    manager.scheduleUpdateAllMavenProjects(MavenSyncSpec.full("eGovFrame 템플릿 프로젝트 생성"))
  }
}
