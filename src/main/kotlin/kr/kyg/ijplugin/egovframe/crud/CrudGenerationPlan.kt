package kr.kyg.ijplugin.egovframe.crud

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

internal data class PlannedCrudArtifact(
  val artifact: CrudArtifact,
  val relativePath: String,
  val target: Path,
  val fileName: String,
  val language: String,
  val content: String,
  val collision: Boolean,
  internal val expectedParentRealPath: Path,
)

internal class GenerationPlan internal constructor(
  val artifacts: List<PlannedCrudArtifact>,
  internal val outputRoot: Path,
  internal val projectedRootRealPath: Path,
) {
  fun select(selected: Set<CrudArtifact>): CrudWritePlan {
    require(selected.isNotEmpty()) { "Select at least one CRUD artifact" }
    val planned = artifacts.mapTo(linkedSetOf(), PlannedCrudArtifact::artifact)
    val unknown = selected.filterNot(planned::contains).sortedBy { it.ordinal }
    require(unknown.isEmpty()) {
      "Selected CRUD artifacts are not part of this generation plan: ${unknown.joinToString()}"
    }
    val selectedArtifacts = artifacts.filter { it.artifact in selected }.sortedBy { it.artifact.ordinal }
    return CrudWritePlan(selectedArtifacts, outputRoot, projectedRootRealPath)
  }

  internal companion object {
    fun create(rendered: List<RenderedCrudArtifact>, outputRoot: Path): GenerationPlan {
      val normalizedRoot = outputRoot.toAbsolutePath().normalize()
      val projectedRootRealPath = projectedDirectoryPath(normalizedRoot, outputRoot)
      val planned = rendered.map { artifact ->
        val target = normalizedRoot.resolve(artifact.relativePath).normalize()
        require(target.startsWith(normalizedRoot)) { "CRUD output escapes the project root: $target" }

        val collision = Files.exists(target, NOFOLLOW_LINKS)
        if (collision && Files.isSymbolicLink(target)) {
          throw IllegalArgumentException("CRUD output target must not be a symbolic link: $target")
        }
        if (collision && !Files.isRegularFile(target, NOFOLLOW_LINKS)) {
          throw IllegalArgumentException("CRUD output target is not a regular file: $target")
        }

        val projectedParentRealPath = projectedDirectoryPath(target.parent, outputRoot)
        require(projectedParentRealPath.startsWith(projectedRootRealPath)) {
          "CRUD output escapes the project root: $target"
        }
        PlannedCrudArtifact(
          artifact = artifact.artifact,
          relativePath = artifact.relativePath,
          target = target,
          fileName = artifact.fileName,
          language = artifact.language,
          content = artifact.content,
          collision = collision,
          expectedParentRealPath = projectedParentRealPath,
        )
      }
      return GenerationPlan(planned, normalizedRoot, projectedRootRealPath)
    }

    private fun projectedDirectoryPath(path: Path, originalOutputRoot: Path): Path {
      var existingAncestor = path
      val missingSegments = ArrayDeque<Path>()
      while (!Files.exists(existingAncestor, NOFOLLOW_LINKS)) {
        existingAncestor.fileName?.let(missingSegments::addFirst)
        existingAncestor = existingAncestor.parent
          ?: throw IllegalArgumentException("CRUD output root is not a directory: $originalOutputRoot")
      }
      if (!Files.isDirectory(existingAncestor)) {
        throw IllegalArgumentException("CRUD output root is not a directory: $originalOutputRoot")
      }

      var projected = existingAncestor.toRealPath()
      for (segment in missingSegments) projected = projected.resolve(segment)
      return projected.normalize()
    }
  }
}

internal class CrudWritePlan internal constructor(
  internal val artifacts: List<PlannedCrudArtifact>,
  internal val outputRoot: Path,
  internal val projectedRootRealPath: Path,
)
