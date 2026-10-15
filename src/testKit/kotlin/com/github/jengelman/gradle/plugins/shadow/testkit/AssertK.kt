@file:Suppress("PackageDirectoryMismatch")

package assertk.assertions

import assertk.Assert
import assertk.assertions.support.expected
import assertk.assertions.support.show
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

// TODO: https://github.com/assertk-org/assertk/pull/542
fun Assert<Path>.doesNotExist(vararg options: LinkOption) = given { actual ->
  if (!Files.notExists(actual, *options)) {
    expected("${show(actual)} does not exist, but it exists")
  }
}
