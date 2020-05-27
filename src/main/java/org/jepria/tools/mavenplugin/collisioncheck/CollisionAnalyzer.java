package org.jepria.tools.mavenplugin.collisioncheck;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class CollisionAnalyzer {

  public static class Location {
    enum Type {
      WEBINF_CLASSES,
      WEBINF_LIB_JAR
    }

    public Type type;
    /**
     * Only if {@link #type} == {@link Type#WEBINF_LIB_JAR}
     */
    public LibJar libJar;
  }

  public static class ClassFileWithLocation {
    public ClassFile classFile;
    public Location location;
  }

  public static class ClassCollision {
    public ClassFileWithLocation class1, class2;
  }

  public static class LibJarTuple {
    /**
     * Non null
     */
    public LibJar jar1;
    /**
     * Non null
     */
    public LibJar jar2;

    @Override
    // for using the type as a map key
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof LibJarTuple)) return false;
      LibJarTuple that = (LibJarTuple) o;
      return Objects.equals(jar1.jarName(), that.jar1.jarName()) &&
              Objects.equals(jar2.jarName(), that.jar2.jarName());
    }

    @Override
    // for using the type as a map key
    public int hashCode() {
      return Objects.hash(jar1.jarName(), jar2.jarName());
    }
  }

  public static class CollisionAnalyzeResult {
    /**
     * Identical classes in [WEB-INF/lib/*.jar x WEB-INF/lib/*.jar] tuples
     */
    public Map<LibJarTuple, Collection<ClassCollision>> identicalClassesInJarTuples;

    /**
     * Class collisions in [WEB-INF/lib/*.jar x WEB-INF/lib/*.jar] tuples
     */
    public Map<LibJarTuple, Collection<ClassCollision>> collisionsInJarTuples;

    /**
     * Identical jar tuples
     */
    public Set<LibJarTuple> identicalJarTuples;

    /**
     * Class collisions in [WEB-INF/classes x WEB-INF/lib/*.jars], [WEB-INF/lib/*.jars x WEB-INF/classes], [WEB-INF/classes x WEB-INF/classes]
     */
    public Collection<ClassCollision> collisionsOther;
  }

  public static CollisionAnalyzeResult analyzeCollisions(War war1, War war2) {

    // collect class entries from both WEB-INF/lib/*.jar and WEB-INF/lib/classes
    List<ClassFileWithLocation> classes1 = new ArrayList<>();
    List<ClassFileWithLocation> classes2 = new ArrayList<>();
    {
      {
        for (ClassFile c : war1.listClasses()) {
          ClassFileWithLocation cwl = new ClassFileWithLocation();
          cwl.classFile = c;
          Location loc = new Location();
          loc.type = Location.Type.WEBINF_CLASSES;
          cwl.location = loc;
          classes1.add(cwl);
        }
        for (LibJar libJar : war1.listLibJars()) {
          for (ClassFile c : libJar.listClasses()) {
            ClassFileWithLocation cwl = new ClassFileWithLocation();
            cwl.classFile = c;
            Location loc = new Location();
            loc.type = Location.Type.WEBINF_LIB_JAR;
            loc.libJar = libJar;
            cwl.location = loc;
            classes1.add(cwl);
          }
        }
      }
      {
        for (ClassFile c : war2.listClasses()) {
          ClassFileWithLocation cwl = new ClassFileWithLocation();
          cwl.classFile = c;
          Location loc = new Location();
          loc.type = Location.Type.WEBINF_CLASSES;
          cwl.location = loc;
          classes2.add(cwl);
        }
        for (LibJar libJar : war2.listLibJars()) {
          for (ClassFile c : libJar.listClasses()) {
            ClassFileWithLocation cwl = new ClassFileWithLocation();
            cwl.classFile = c;
            Location loc = new Location();
            loc.type = Location.Type.WEBINF_LIB_JAR;
            loc.libJar = libJar;
            cwl.location = loc;
            classes2.add(cwl);
          }
        }
      }
    }

    // collect collisions by canonical classnames between the two wars
    // do not find collisions within the same war (if so, this is a build mistake)
    List<ClassCollision> collisions = new ArrayList<>();
    for (ClassFileWithLocation c1 : classes1) {
      for (ClassFileWithLocation c2 : classes2) {
        if (c1.classFile.canonicalClassName().equals(c2.classFile.canonicalClassName())) {
          ClassCollision col = new ClassCollision();
          col.class1 = c1;
          col.class2 = c2;
          collisions.add(col);
          // do not break, check further class collisions
        }
      }
    }

    // group collisions by jars
    Set<LibJarTuple> identicalJarTuples = new HashSet<>();
    Map<LibJarTuple, Collection<ClassCollision>> collisionsInJars = new HashMap<>();
    Map<LibJarTuple, Collection<ClassCollision>> identicalClassesInJars = new HashMap<>();

    Iterator<ClassCollision> it = collisions.iterator();
    while (it.hasNext()) {
      ClassCollision collision = it.next();
      if (collision.class1.location.type == Location.Type.WEBINF_LIB_JAR
              && collision.class2.location.type == Location.Type.WEBINF_LIB_JAR) {
        LibJarTuple libJarTuple = new LibJarTuple();
        libJarTuple.jar1 = collision.class1.location.libJar;
        libJarTuple.jar2 = collision.class2.location.libJar;

        if (!identicalJarTuples.contains(libJarTuple)) { // otherwise skip

          // check the entire jar tuple equality on the first collision in that tuple
          if (libJarsEqual(libJarTuple.jar1, libJarTuple.jar2)) {
            identicalJarTuples.add(libJarTuple);

          } else {

            boolean identicalClasses;
            try {
              identicalClasses = inputsEqual(collision.class1.classFile.newInputStream(),
                      collision.class2.classFile.newInputStream());
            } catch (IOException e) {
              throw new RuntimeException(e);
            }

            if (identicalClasses) {
              Collection<ClassCollision> identicalClassesInJarsElement =
                      identicalClassesInJars.computeIfAbsent(libJarTuple, k -> new ArrayList<>());
              identicalClassesInJarsElement.add(collision);
            } else {
              Collection<ClassCollision> collisionsInJarsElement =
                      collisionsInJars.computeIfAbsent(libJarTuple, k -> new ArrayList<>());
              collisionsInJarsElement.add(collision);
            }
          }
        }

        it.remove();
      }
    }

    CollisionAnalyzeResult result = new CollisionAnalyzeResult();
    result.collisionsInJarTuples = collisionsInJars;
    result.identicalClassesInJarTuples = identicalClassesInJars;
    result.identicalJarTuples = identicalJarTuples;
    result.collisionsOther = collisions;
    return result;
  }

  private static boolean libJarsEqual(LibJar jar1, LibJar jar2) {
    if (jar1 == null && jar2 == null) {
      return true;
    } else if (jar1 == null || jar2 == null) {
      return false;
    } else {
      if (jar1 == jar2) {
        return true;
      } else {
        if (jar1.jarName().equals(jar2.jarName())) {
          boolean contentsEqual;
          try {
            contentsEqual = inputsEqual(jar1.newInputStream(), jar2.newInputStream());
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
          return contentsEqual;
        }
        return false;
      }
    }
  }

  private static boolean classFilesEqual(ClassFile class1, ClassFile class2) {
    if (class1 == null && class2 == null) {
      return true;
    } else if (class1 == null || class2 == null) {
      return false;
    } else if (class1 == class2) {
      return true;
    } else {
      if (class1.canonicalClassName().equals(class2.canonicalClassName())) {
        boolean contentsEqual;
        try {
          contentsEqual = inputsEqual(class1.newInputStream(), class2.newInputStream());
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        return contentsEqual;
      }
      return false;
    }
  }

  private static boolean inputsEqual(InputStream i1, InputStream i2) throws IOException {
    byte[] buf1 = new byte[64 * 1024];
    byte[] buf2 = new byte[64 * 1024];
    try (InputStream _i1 = i1; InputStream _i2 = i2) {
      DataInputStream d2 = new DataInputStream(_i2);
      int len;
      while ((len = _i1.read(buf1)) > 0) {
        d2.readFully(buf2, 0, len);
        for (int i = 0; i < len; i++)
          if (buf1[i] != buf2[i]) {
            return false;
          }
      }
      return d2.read() < 0; // is the end of the second file also.
    } catch (EOFException ioe) {
      return false;
    }
  }
}
