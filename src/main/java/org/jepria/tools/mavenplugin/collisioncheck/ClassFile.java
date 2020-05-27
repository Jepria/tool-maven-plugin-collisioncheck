package org.jepria.tools.mavenplugin.collisioncheck;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

public interface ClassFile {

  /**
   * e.g. {@code java.util.List}
   *
   * @return
   */
  String canonicalClassName();

  /**
   * e.g. {@code List} for the {@code java.util.List} class
   *
   * @return
   */
  default String simpleName() {
    String canonicalName = canonicalClassName();
    if (canonicalName == null) {
      return null;
    } else {
      int lastDot = canonicalName.lastIndexOf('.');
      if (lastDot == -1) {
        return canonicalName;
      } else {
        return canonicalName.substring(lastDot + 1);
      }
    }
  }

  /**
   * e.g. {@code java/util/List.class} for the {@code java.util.List} class
   *
   * @return
   */
  default Path path() {
    return Paths.get(canonicalClassName().replaceAll("\\.", "/") + ".class");
  }

  InputStream newInputStream();
}
