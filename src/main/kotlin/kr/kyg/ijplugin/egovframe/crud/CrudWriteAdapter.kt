package kr.kyg.ijplugin.egovframe.crud

import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

internal data class CrudWriteResult(
  val written: List<Path>,
  val overwritten: List<Path>,
  val cleanupFailures: List<Path>,
)

internal class CrudWriteAdapter(
  private val fileOps: CrudFileOps = NioCrudFileOps,
) {
  fun write(plan: CrudWritePlan): CrudWriteResult {
    validateAll(plan)

    val createdDirectories = mutableListOf<Path>()
    val stages = linkedMapOf<CrudArtifact, Path>()
    val backups = linkedMapOf<CrudArtifact, Path>()
    val attempted = mutableListOf<PlannedCrudArtifact>()
    var committed = false

    try {
      for (directory in missingDirectories(plan)) {
        fileOps.createDirectory(directory)
        createdDirectories.add(directory)
      }
      validateAll(plan)

      for (entry in plan.artifacts) {
        val stage = fileOps.createTempFile(entry.target.parent, ".egov-crud-${entry.artifact.ordinal}-", ".stage")
        stages[entry.artifact] = stage
        fileOps.writeString(stage, entry.content)
      }

      for (entry in plan.artifacts) {
        validateEntry(plan, entry)
        if (entry.collision) {
          val backup = fileOps.createTempFile(
            entry.target.parent,
            ".egov-crud-${entry.artifact.ordinal}-",
            ".backup",
          )
          backups[entry.artifact] = backup
          fileOps.copyReplacing(entry.target, backup)
        }
        attempted.add(entry)
        fileOps.moveReplacing(stages.getValue(entry.artifact), entry.target)
      }
      committed = true
    } catch (error: Exception) {
      rollback(error, attempted, stages, backups, createdDirectories)
      throw error
    }

    check(committed)
    val cleanupFailures = cleanupAfterCommit(plan, stages, backups)
    return CrudWriteResult(
      written = plan.artifacts.map(PlannedCrudArtifact::target),
      overwritten = plan.artifacts.filter(PlannedCrudArtifact::collision).map(PlannedCrudArtifact::target),
      cleanupFailures = cleanupFailures,
    )
  }

  private fun validateAll(plan: CrudWritePlan) {
    try {
      val currentRoot = projectedDirectoryPath(plan.outputRoot)
      staleUnless(currentRoot == plan.projectedRootRealPath)
      for (entry in plan.artifacts) validateEntry(plan, entry)
    } catch (error: IllegalStateException) {
      if (error.message == STALE_MESSAGE) throw error
      throw IllegalStateException(STALE_MESSAGE, error)
    } catch (error: Exception) {
      throw IllegalStateException(STALE_MESSAGE, error)
    }
  }

  private fun validateEntry(plan: CrudWritePlan, entry: PlannedCrudArtifact) {
    try {
      staleUnless(entry.target.startsWith(plan.outputRoot))
      staleUnless(entry.expectedParentRealPath.startsWith(plan.projectedRootRealPath))

      val exists = fileOps.exists(entry.target)
      staleUnless(exists == entry.collision)
      if (exists) {
        staleUnless(!fileOps.isSymbolicLink(entry.target))
        staleUnless(fileOps.isRegularFile(entry.target))
      }

      val currentParent = projectedDirectoryPath(entry.target.parent)
      staleUnless(currentParent == entry.expectedParentRealPath)
      staleUnless(currentParent.startsWith(plan.projectedRootRealPath))
    } catch (error: IllegalStateException) {
      if (error.message == STALE_MESSAGE) throw error
      throw IllegalStateException(STALE_MESSAGE, error)
    } catch (error: Exception) {
      throw IllegalStateException(STALE_MESSAGE, error)
    }
  }

  private fun projectedDirectoryPath(path: Path): Path {
    var existingAncestor = path
    val missingSegments = ArrayDeque<Path>()
    while (!fileOps.exists(existingAncestor)) {
      existingAncestor.fileName?.let(missingSegments::addFirst)
      existingAncestor = existingAncestor.parent ?: throw IllegalStateException(STALE_MESSAGE)
    }
    staleUnless(fileOps.isDirectory(existingAncestor))

    var projected = fileOps.toRealPath(existingAncestor)
    for (segment in missingSegments) projected = projected.resolve(segment)
    return projected.normalize()
  }

  private fun missingDirectories(plan: CrudWritePlan): List<Path> {
    val missing = linkedSetOf<Path>()
    for (entry in plan.artifacts) {
      var directory = entry.target.parent
      while (!fileOps.exists(directory)) {
        missing.add(directory)
        directory = directory.parent ?: break
      }
    }
    return missing.sortedWith(compareBy<Path>({ it.nameCount }, Path::toString))
  }

  private fun rollback(
    originalError: Exception,
    attempted: List<PlannedCrudArtifact>,
    stages: Map<CrudArtifact, Path>,
    backups: Map<CrudArtifact, Path>,
    createdDirectories: List<Path>,
  ) {
    val preservedBackups = mutableSetOf<Path>()
    for (entry in attempted.asReversed()) {
      if (entry.collision) {
        val backup = backups[entry.artifact] ?: continue
        try {
          fileOps.moveReplacing(backup, entry.target)
        } catch (restoreError: Exception) {
          preservedBackups.add(backup)
          originalError.addSuppressed(
            IOException("Failed to restore CRUD file; backup preserved at $backup", restoreError)
          )
        }
      } else {
        try {
          if (fileOps.exists(entry.target)) fileOps.deleteIfExists(entry.target)
        } catch (cleanupError: Exception) {
          originalError.addSuppressed(cleanupException(entry.target, cleanupError))
        }
      }
    }

    for (path in stages.values) deleteDuringRollback(path, originalError)
    for (path in backups.values) {
      if (path !in preservedBackups) deleteDuringRollback(path, originalError)
    }
    for (directory in createdDirectories.asReversed()) {
      try {
        fileOps.deleteIfExists(directory)
      } catch (_: DirectoryNotEmptyException) {
        // Preserve concurrent content.
      } catch (cleanupError: Exception) {
        originalError.addSuppressed(cleanupException(directory, cleanupError))
      }
    }
  }

  private fun deleteDuringRollback(path: Path, originalError: Exception) {
    try {
      fileOps.deleteIfExists(path)
    } catch (cleanupError: Exception) {
      originalError.addSuppressed(cleanupException(path, cleanupError))
    }
  }

  private fun cleanupAfterCommit(
    plan: CrudWritePlan,
    stages: Map<CrudArtifact, Path>,
    backups: Map<CrudArtifact, Path>,
  ): List<Path> {
    val failures = mutableListOf<Path>()
    for (entry in plan.artifacts) {
      backups[entry.artifact]?.let { deleteAfterCommit(it, failures) }
      stages[entry.artifact]?.let { deleteAfterCommit(it, failures) }
    }
    return failures
  }

  private fun deleteAfterCommit(path: Path, failures: MutableList<Path>) {
    try {
      fileOps.deleteIfExists(path)
    } catch (_: Exception) {
      failures.add(path)
    }
  }

  private fun cleanupException(path: Path, cause: Exception): IOException =
    IOException("Failed to clean up CRUD temporary path: $path", cause)

  private fun staleUnless(condition: Boolean) {
    if (!condition) throw IllegalStateException(STALE_MESSAGE)
  }

  private companion object {
    const val STALE_MESSAGE = "CRUD generation plan is stale; review file collisions and try again."
  }
}

internal interface CrudFileOps {
  fun exists(path: Path): Boolean
  fun isDirectory(path: Path): Boolean
  fun isRegularFile(path: Path): Boolean
  fun isSymbolicLink(path: Path): Boolean
  fun toRealPath(path: Path): Path
  fun createDirectory(path: Path)
  fun createTempFile(directory: Path, prefix: String, suffix: String): Path
  fun writeString(path: Path, content: String)
  fun copyReplacing(source: Path, target: Path)
  fun moveReplacing(source: Path, target: Path)
  fun deleteIfExists(path: Path): Boolean
}

internal object NioCrudFileOps : CrudFileOps {
  override fun exists(path: Path): Boolean = Files.exists(path, NOFOLLOW_LINKS)

  override fun isDirectory(path: Path): Boolean = Files.isDirectory(path)

  override fun isRegularFile(path: Path): Boolean = Files.isRegularFile(path, NOFOLLOW_LINKS)

  override fun isSymbolicLink(path: Path): Boolean = Files.isSymbolicLink(path)

  override fun toRealPath(path: Path): Path = path.toRealPath()

  override fun createDirectory(path: Path) {
    Files.createDirectory(path)
  }

  override fun createTempFile(directory: Path, prefix: String, suffix: String): Path =
    Files.createTempFile(directory, prefix, suffix)

  override fun writeString(path: Path, content: String) {
    Files.writeString(path, content, Charsets.UTF_8)
  }

  override fun copyReplacing(source: Path, target: Path) {
    Files.copy(source, target, REPLACE_EXISTING, COPY_ATTRIBUTES)
  }

  override fun moveReplacing(source: Path, target: Path) {
    try {
      Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING)
    } catch (_: AtomicMoveNotSupportedException) {
      Files.move(source, target, REPLACE_EXISTING)
    }
  }

  override fun deleteIfExists(path: Path): Boolean = Files.deleteIfExists(path)
}
