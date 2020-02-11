package org.jepria.tools.mavenplugin.collisioncheck;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Goal which checks collisions.
 *
 */
@Mojo( name = "check-collision", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, requiresDependencyResolution = ResolutionScope.TEST )
public class CollisionCheckMojo
    extends AbstractMojo
{
  
  /**
   * Plugin configuration to use in the execution.
   */
  @Parameter
  private XmlPlexusConfiguration configuration;

  /**
   * Mojo input parameter, semicolon-separated classpath roots (relative to the maven project root).
   */
  @Parameter( property = "classPathRoots", required = true )
  private String classPathRoots;
  
  /**
   * Mojo input parameter, semicolon-separated classpath references (names or any IDs) matching {@link #classPathRoots}.
   */
  @Parameter( property = "classPathRefs", required = true )
  private String classPathRefs;
  
  /**`
   * Mojo input parameter, semicolon-separated filepath roots (relative to the maven project root).
   */
  @Parameter( property = "filePathRoots" )
  private String filePathRoots;

  /**
   * Mojo input parameter, semicolon-separated filepath references (names or any IDs) matching {@link #filePathRoots}.
   */
  @Parameter( property = "filePathRefs" )
  private String filePathRefs;
  
  /**
   * Mojo input parameter, flag that sets the search for class collision only.
   */
  @Parameter( defaultValue = "true", property = "onlyClassCollision", required = true )
  private boolean onlyClassCollision;

  protected Set<Path> walkClassPathRoot(Path classPathRoot) throws MojoExecutionException {
    try {
      return Files.walk(classPathRoot)
              .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".class"))
              .map(path -> path.subpath(2, path.getNameCount())) // crop the path root (0-th element) and one subfolder (1-th element)
              .collect(Collectors.toSet());
    } catch (IOException e) {
      throw new MojoExecutionException("Exception traversing class path root [" + classPathRoot + "]", e);
    }
  }

  protected Set<Path> walkFilePathRoot(Path filePathRoot) throws MojoExecutionException {
    try {
      return Files.walk(filePathRoot)
              .filter(path -> Files.isRegularFile(path))
              .map(path -> path.subpath(2, path.getNameCount())) // crop the path root (0-th element) and one subfolder (1-th element)
              .collect(Collectors.toSet());
    } catch (IOException e) {
      throw new MojoExecutionException("Exception traversing file path root [" + filePathRoot + "]", e);
    }
  }
  
  protected String pathToClassname(Path path) {
    String classname = path.toString();
    classname = classname.substring(0, classname.lastIndexOf('.'));
    classname = classname.replaceAll(Pattern.quote(File.separator), ".");
    return classname;
  }
  
  public void execute() throws MojoExecutionException {

    if (classPathRoots == null) {
      throw new MojoExecutionException("classPathRoots attribute is mandatory");
    }

    List<String> classPathRootsSplit = Arrays.asList(classPathRoots.split("\\s*;\\s*"));
    List<String> classPathRefsSplit = Arrays.asList(classPathRefs.split("\\s*;\\s*"));
    
    final Map<Path, Set<Integer>> classpaths = new HashMap<>();
    
    if (classPathRootsSplit.size() > 1) {
      for (int index = 0; index < classPathRootsSplit.size(); index++) {
        String classPathRoot = classPathRootsSplit.get(index);

        final Path classPathRootPath = Paths.get(classPathRoot); // maven works with paths relative to the maven project root as if they are absolute paths

        if (!Files.isDirectory(classPathRootPath)) {
          throw new MojoExecutionException("classPathRoot must be a directory: " + classPathRoot);
        }

        // classes from a single classpath
        Set<Path> classpath = walkClassPathRoot(classPathRootPath);

        for (Path clazz: classpath) {
          Set<Integer> s = classpaths.get(clazz);
          if (s == null) {
            s = new HashSet<>();
            classpaths.put(clazz, s);
          }
          s.add(index);
        }
      }

      boolean hasCollisions = false;

      // log class collisions
      Iterator<Map.Entry<Path, Set<Integer>>> eit = classpaths.entrySet().iterator();
      while (eit.hasNext()) {
        Map.Entry<Path, Set<Integer>> e = eit.next();
        if (e.getValue().size() > 1) {

          hasCollisions = true;

          StringBuilder sb = new StringBuilder();

          sb.append("The classpaths");
          for (Integer i : e.getValue()) {
            if (classPathRefsSplit.size() > i) {
              sb.append("\n> ").append(classPathRefsSplit.get(i));
            } else {
              sb.append("\n> #").append(i);
            }
          }
          sb.append("\ncontain collision: ").append(pathToClassname(e.getKey()));
          String msg = sb.toString();

          getLog().info(msg);

        }
      }

      if (hasCollisions) {
        throw new MojoExecutionException("The classpaths have collisions. Fix them before merging into a single war");
      } else {
        getLog().info("The classpaths do not have collisions");
      }
      
    }  
      
    if ((!onlyClassCollision)&&(filePathRoots!=null)&&(filePathRoots.length()>0)) {
      
      List<String> filePathRootsSplit = Arrays.asList(filePathRoots.split("\\s*;\\s*"));
      List<String> filePathRefsSplit = Arrays.asList(filePathRefs.split("\\s*;\\s*"));
      
      final Map<Path, Set<Integer>> filepaths = new HashMap<>();

      if (filePathRootsSplit.size() > 1) {
        for (int index = 0; index < filePathRootsSplit.size(); index++) {
          String filePathRoot = filePathRootsSplit.get(index);

          final Path filePathRootPath = Paths.get(filePathRoot); // maven works with paths relative to the maven project root as if they are absolute paths

          if (!Files.isDirectory(filePathRootPath)) {
            throw new MojoExecutionException("filePathRoot must be a directory: " + filePathRoot);
          }

          // files from a single filepath
          Set<Path> filepath = walkFilePathRoot(filePathRootPath);

          for (Path currfile: filepath) {
            Set<Integer> s = filepaths.get(currfile);
            if (s == null) {
              s = new HashSet<>();
              filepaths.put(currfile, s);
            }
            s.add(index);
          }
        }

        // log file collisions
        Iterator<Map.Entry<Path, Set<Integer>>> eit = filepaths.entrySet().iterator();
        while (eit.hasNext()) {
          Map.Entry<Path, Set<Integer>> e = eit.next();
          if (e.getValue().size() > 1) {

            StringBuilder sb = new StringBuilder();

            sb.append("The filepaths");
            for (Integer i : e.getValue()) {
              if (filePathRefsSplit.size() > i) {
                sb.append("\n> ").append(filePathRefsSplit.get(i));
              } else {
                sb.append("\n> #").append(i);
              }
            }
            sb.append("\ncontain collision: ").append(e.getKey().toString());
            String msg = sb.toString();

            getLog().warn(msg);

          }
        }
        
      }  
    }
    
  }
  
}
