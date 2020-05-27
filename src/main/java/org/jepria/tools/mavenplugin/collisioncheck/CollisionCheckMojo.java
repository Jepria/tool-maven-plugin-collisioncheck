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
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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
   * Mojo input parameter.
   * Application-gwt.war path (relative to the maven project root).
   */
  @Parameter( property = "warGwtPath")
  private String warGwtPath;
  
  /**
   * Mojo input parameter.
   * Application-service-rest.war path (relative to the maven project root)
   */
  @Parameter( property = "warServiceRestPath")
  private String warServiceRestPath;

  /**
   * Mojo input parameter.
   * If collisions found, whether to fail execution.
   * values: "1", "0", "true", "false", "TRUE", "FALSE"
   */
  @Parameter( property = "strict")
  private String strict = "true";

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {

    if (warGwtPath == null || warServiceRestPath == null) {
      getLog().error("Class collision check skipped: either \"warGwt\" or \"warServiceRest\" configuration parameter is empty.");

    } else {

      Path warGwtPath0;
      try {
        warGwtPath0 = Paths.get(warGwtPath); // maven works with paths relative to the maven project root as if they are absolute paths
        if (!Files.isRegularFile(warGwtPath0)) {
          throw new MojoFailureException("The \"warGwt\" configuration parameter does not represent a regular file: [" + warGwtPath + "]");
        }
      } catch (Throwable e) {
        getLog().error(e);
        throw new MojoFailureException("The \"warGwt\" configuration parameter does not represent a valid path: [" + warGwtPath + "]");
      }

      Path warServiceRestPath0;
      try {
        warServiceRestPath0 = Paths.get(warServiceRestPath); // maven works with paths relative to the maven project root as if they are absolute paths
        if (!Files.isRegularFile(warServiceRestPath0)) {
          throw new MojoFailureException("The \"warServiceRest\" configuration parameter does not represent a regular file: [" + warServiceRestPath + "]");
        }
      } catch (Throwable e) {
        getLog().error(e);
        throw new MojoFailureException("The \"warServiceRest\" configuration parameter does not represent a valid path: [" + warServiceRestPath + "]");
      }

      final War warGwt;
      try {
        warGwt = new WarImpl(warGwtPath0.toFile());
      } catch (IOException e) {
        // impossible
        throw new RuntimeException(e);
      }
      final War warServiceRest;
      try {
        warServiceRest = new WarImpl(warServiceRestPath0.toFile());
      } catch (IOException e) {
        // impossible
        throw new RuntimeException(e);
      }

      getLog().info("Collision check began.");
      getLog().info("War files: [" + warGwtPath + "], [" + warServiceRestPath + "]");

      CollisionAnalyzer.CollisionAnalyzeResult result = CollisionAnalyzer.analyzeCollisions(warGwt, warServiceRest);

      // log collisions
      boolean hasCollisions = false;

      if (result.identicalJarTuples != null && result.identicalJarTuples.size() > 0) {
        // identical jars is not an error case
        getLog().warn("Identical jars:");
        for (CollisionAnalyzer.LibJarTuple identicalJarTuple : result.identicalJarTuples) {
          getLog().warn("    " + identicalJarTuple.jar1.jarName());
        }
      }

      if (result.identicalClassesInJarTuples != null && result.identicalClassesInJarTuples.size() > 0) {
        // identical classes in jars is not an error case
        getLog().warn("Identical classes in jars:");
        for (CollisionAnalyzer.LibJarTuple libJarTuple : result.identicalClassesInJarTuples.keySet()) {
          Collection<CollisionAnalyzer.ClassCollision> element = result.identicalClassesInJarTuples.get(libJarTuple);
          getLog().warn("    [" + libJarTuple.jar1.jarName() + "], [" + libJarTuple.jar2.jarName() + "] " +
                  "having " + element.size() + " class collisions:");
          // list class collisions in case of non-same artifacts
          for (CollisionAnalyzer.ClassCollision collision : element) {
            getLog().warn("        " + collision.class1.classFile.canonicalClassName());
          }
        }
      }

      if (result.collisionsInJarTuples != null && result.collisionsInJarTuples.size() > 0) {

        hasCollisions = true;


        // distinguish same artifact jars and non-same artifact jars for better logging
        Map<CollisionAnalyzer.LibJarTuple, Collection<CollisionAnalyzer.ClassCollision>> sameArtifactJarCollisions = new HashMap<>();
        Map<CollisionAnalyzer.LibJarTuple, Collection<CollisionAnalyzer.ClassCollision>> nonSameArtifactJarCollisions = new HashMap<>();


        for (CollisionAnalyzer.LibJarTuple libJarTuple : result.collisionsInJarTuples.keySet()) {
          Collection<CollisionAnalyzer.ClassCollision> element = result.collisionsInJarTuples.get(libJarTuple);

          // test whether the two jars represent the same artifact
          final boolean sameArtifact;
          {
            int classesInJar1 = libJarTuple.jar1.listClasses().size();
            int classesInJar2 = libJarTuple.jar2.listClasses().size();
            sameArtifact = (double) element.size() / classesInJar1 > 0.75
                    && (double) element.size() / classesInJar2 > 0.75;
          }

          if (sameArtifact) {
            sameArtifactJarCollisions.put(libJarTuple, element);
          } else {
            nonSameArtifactJarCollisions.put(libJarTuple, element);
          }
        }


        if (sameArtifactJarCollisions.size() > 0) {
          getLog().error("Class collisions in jars which seem to represent the same artifact of different versions:");
          for (CollisionAnalyzer.LibJarTuple libJarTuple : sameArtifactJarCollisions.keySet()) {
            Collection<CollisionAnalyzer.ClassCollision> element = sameArtifactJarCollisions.get(libJarTuple);
            getLog().error("    [" + libJarTuple.jar1.jarName() + "], [" + libJarTuple.jar2.jarName() + "] " +
                    "having " + element.size() + " class collisions");
            // no need to list class collisions in case of same artifacts
          }
        }

        if (nonSameArtifactJarCollisions.size() > 0) {
          getLog().error("Class collisions in jars:");
          for (CollisionAnalyzer.LibJarTuple libJarTuple : nonSameArtifactJarCollisions.keySet()) {
            Collection<CollisionAnalyzer.ClassCollision> element = nonSameArtifactJarCollisions.get(libJarTuple);
            getLog().error("    [" + libJarTuple.jar1.jarName() + "], [" + libJarTuple.jar2.jarName() + "] " +
                    "having " + element.size() + " class collisions:");
            // list class collisions in case of non-same artifacts
            for (CollisionAnalyzer.ClassCollision collision : element) {
              getLog().error("        " + collision.class1.classFile.canonicalClassName());
            }
          }
        }
      }

      if (result.collisionsOther != null && result.collisionsOther.size() > 0) {
        hasCollisions = true;

        getLog().error("Mixed class collisions:");
        for (CollisionAnalyzer.ClassCollision collision : result.collisionsOther) {
          getLog().error("    [" + warGwt
                  + (collision.class1.location.type == CollisionAnalyzer.Location.Type.WEBINF_CLASSES ? "/WEB-INF/classes" : ("/WEB-INF/lib/" + collision.class1.location.libJar.jarName()))
                  + "], [" + warServiceRest
                  + (collision.class2.location.type == CollisionAnalyzer.Location.Type.WEBINF_CLASSES ? "/WEB-INF/classes" : ("/WEB-INF/lib/" + collision.class2.location.libJar.jarName()))
                  + " having class collision:");
          getLog().error("        " + collision.class1.classFile.canonicalClassName());
        }
      }

      if (hasCollisions) {
        getLog().error("Collision check ended. Class collisions found.");
        if (isStrict()) {
          throw new MojoFailureException("War files contain class collisions");
        }
      } else {
        getLog().info("Collision check ended. No class collisions found.");
      }
    }
  }

  protected boolean isStrict() {
    return "true".equalsIgnoreCase(strict) || "1".equals(strict);
  }
}
