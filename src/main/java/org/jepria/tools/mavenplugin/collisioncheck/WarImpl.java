package org.jepria.tools.mavenplugin.collisioncheck;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class WarImpl implements War {

  protected final ZipFile warFileZip;

  protected static final Pattern webInfClassesClassPattern = Pattern.compile("WEB-INF/classes/(.+/)?(.+)\\.class");
  protected static final Pattern webInfLibJarPattern = Pattern.compile("WEB-INF/lib/(.+\\.jar)");
  protected static final Pattern jarClassPattern = Pattern.compile("(.+/)?(.+)\\.class");

  protected final List<ClassFile> classFiles;
  protected final List<LibJar> libJars;

  public WarImpl(File warFile) throws IOException {
    warFileZip = new ZipFile(warFile);

    Enumeration<? extends ZipEntry> warEntries = warFileZip.entries();

    classFiles = new ArrayList<>();
    libJars = new ArrayList<>();

    while (warEntries.hasMoreElements()) {
      ZipEntry warEntry = warEntries.nextElement();
      String warEntryName = warEntry.getName();

      Matcher webInfClassesClassMatcher = webInfClassesClassPattern.matcher(warEntryName);
      if (webInfClassesClassMatcher.matches()) {

        String path = webInfClassesClassMatcher.group(1);
        String name = webInfClassesClassMatcher.group(2);
        String canonicalClassName = (path == null ? "" : path.replaceAll("/", ".")) + name;

        ClassFile classFile = new ClassFile() {
          @Override
          public String canonicalClassName() {
            return canonicalClassName;
          }

          @Override
          public InputStream newInputStream() {
            try {
              return warFileZip.getInputStream(warEntry);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        };

        classFiles.add(classFile);

      } else {
        Matcher webInfLibJarMatcher = webInfLibJarPattern.matcher(warEntryName);
        if (webInfLibJarMatcher.matches()) {

          String jarName = webInfLibJarMatcher.group(1);

          InputStream jarInput = warFileZip.getInputStream(warEntry);
          ZipInputStream jarInputZip = new ZipInputStream(jarInput);

          List<ClassFile> jarClassFiles = new ArrayList<>();

          ZipEntry jarEntry;
          while ((jarEntry = jarInputZip.getNextEntry()) != null) {
            String jarEntryName = jarEntry.getName();

            Matcher jarClassMatcher = jarClassPattern.matcher(jarEntryName);
            if (jarClassMatcher.matches()) {

              String path = jarClassMatcher.group(1);
              String name = jarClassMatcher.group(2);
              String canonicalClassName = (path == null ? "" : path.replaceAll("/", ".")) + name;

              ClassFile classFile = new ClassFile() {
                @Override
                public String canonicalClassName() {
                  return canonicalClassName;
                }

                @Override
                public InputStream newInputStream() {
                  try {
                    InputStream jarInput = warFileZip.getInputStream(warEntry);
                    ZipInputStream jarInputZip = new ZipInputStream(jarInput);
                    InputStream in = getInputStreamForZipInputStreamEntry(jarInputZip, jarEntryName);
                    if (in != null) {
                      return in;
                    } else {
                      // no such entry found
                      throw new NoSuchElementException("No ZipEntry named [" + jarEntryName + "] found in the ZipInputStream");
                    }
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                }
              };

              jarClassFiles.add(classFile);
            }
          }

          LibJar libJar = new LibJar() {
            @Override
            public String jarName() {
              return jarName;
            }

            @Override
            public List<ClassFile> listClasses() {
              return jarClassFiles;
            }

            @Override
            public InputStream newInputStream() {
              try {
                return warFileZip.getInputStream(warEntry);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            }
          };

          libJars.add(libJar);
        }
      }
    }
  }

  @Override
  public List<LibJar> listLibJars() {
    return libJars;
  }

  @Override
  public List<ClassFile> listClasses() {
    return classFiles;
  }

  private static InputStream getInputStreamForZipInputStreamEntry(ZipInputStream stream, String entryName) throws IOException {
    ZipEntry entry;
    while ((entry = stream.getNextEntry()) != null) {
      String jarEntryName0 = entry.getName();
      if (jarEntryName0.equals(entryName)) {
        // read entry
        return new InputStream() {
          @Override
          public int read() throws IOException {
            return stream.read();
          }
        };
      }
    }
    return null;
  }
}