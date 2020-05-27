package org.jepria.tools.mavenplugin.collisioncheck;

import java.io.InputStream;
import java.util.List;

public interface LibJar {
  /**
   * e.g. {@code commons-io-2.5.jar}
   *
   * @return
   */
  String jarName();

  List<ClassFile> listClasses();

  InputStream newInputStream();
}