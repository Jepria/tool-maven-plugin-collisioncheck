package org.jepria.tools.mavenplugin.collisioncheck;

import java.util.List;

public interface War {
  /**
   * From WEB-INF/lib
   *
   * @return
   */
  List<LibJar> listLibJars();

  /**
   * From WEB-INF/classes
   *
   * @return
   */
  List<ClassFile> listClasses();
}