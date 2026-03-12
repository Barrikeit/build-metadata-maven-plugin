package dev.barrikeit.maven;

import java.io.File;
import java.io.FileWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HexFormat;
import java.util.Properties;
import java.util.UUID;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/** Mojo to generate build metadata for Maven projects. */
@Mojo(name = "generate-build-metadata", defaultPhase = LifecyclePhase.INITIALIZE)
public class BuildMetadata extends AbstractMojo {

  private static final int MAX_BUILD_ID_LENGTH = 64;

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  /** Skip plugin execution entirely. */
  @Parameter(property = "buildMetadata.skip", defaultValue = "false")
  private boolean skip;

  /** Override default application name. Optional. */
  @Parameter(property = "appName")
  private String appName;

  /** Override default application version. Optional. */
  @Parameter(property = "appVersion")
  private String appVersion;

  /** Property name for the build number. */
  @Parameter(property = "buildIdProperty", defaultValue = "buildNumber")
  private String buildIdProperty;

  /** Property name for the build timestamp. */
  @Parameter(property = "buildTimestampProperty", defaultValue = "buildTimestamp")
  private String buildTimestampProperty;

  /** Property name for the application name. */
  @Parameter(property = "appNameProperty", defaultValue = "appName")
  private String appNameProperty;

  /** Property name for the application version. */
  @Parameter(property = "appVersionProperty", defaultValue = "appVersion")
  private String appVersionProperty;

  /**
   * Length of the build number (max 64). If 0, build number is not generated. SHA-256 produces 64
   * hex characters; values above this are capped.
   */
  @Parameter(property = "buildIdLength", defaultValue = "20")
  private int buildIdLength;

  /** Format for the timestamp. */
  @Parameter(property = "buildTimestampFormat", defaultValue = "yyyyMMddHHmmssSSS")
  private String buildTimestampFormat;

  /** Generate a properties file in fileDirectory. Default: false */
  @Parameter(property = "generateFile", defaultValue = "false")
  private boolean generateFile;

  /** Directory for the generated properties file. */
  @Parameter(
      property = "fileDirectory",
      defaultValue = "${project.build.directory}/generated-sources/build-metadata")
  private String fileDirectory;

  /** Name of the generated properties file. */
  @Parameter(property = "fileName", defaultValue = "build.properties")
  private String fileName;

  @Override
  public void execute() throws MojoExecutionException {
    if (skip) {
      getLog().info("Build metadata generation skipped (buildMetadata.skip=true)");
      return;
    }

    // Validate buildIdLength
    if (buildIdLength < 0) {
      throw new MojoExecutionException("buildIdLength must be >= 0, got: " + buildIdLength);
    }
    if (buildIdLength > MAX_BUILD_ID_LENGTH) {
      throw new MojoExecutionException(
          "buildIdLength cannot exceed " + MAX_BUILD_ID_LENGTH + ", got: " + buildIdLength);
    }

    try {
      // Compute timestamp
      Date date = new Date();
      String timestamp = new SimpleDateFormat(buildTimestampFormat).format(date);

      // Compute build number if length > 0
      String buildNumber = null;
      if (buildIdLength > 0) {
        buildNumber = generateBuildNumber(buildIdLength);
      }

      // Determine name/version
      String finalAppName = (appName != null && !appName.isBlank()) ? appName : project.getName();
      String finalAppVersion =
          (appVersion != null && !appVersion.isBlank()) ? appVersion : project.getVersion();

      // Set properties in Maven project
      if (buildNumber != null) {
        project.getProperties().setProperty(buildIdProperty, buildNumber);
        getLog().debug("Set property " + buildIdProperty + " = " + buildNumber);
      }
      project.getProperties().setProperty(buildTimestampProperty, timestamp);
      getLog().debug("Set property " + buildTimestampProperty + " = " + timestamp);

      if (finalAppName != null) {
        project.getProperties().setProperty(appNameProperty, finalAppName);
        getLog().debug("Set property " + appNameProperty + " = " + finalAppName);
      }
      if (finalAppVersion != null) {
        project.getProperties().setProperty(appVersionProperty, finalAppVersion);
        getLog().debug("Set property " + appVersionProperty + " = " + finalAppVersion);
      }

      // Optionally write to file
      if (generateFile) {
        File outputDir = new File(fileDirectory);
        if (!outputDir.exists() && !outputDir.mkdirs()) {
          throw new MojoExecutionException(
              "Failed to create output directory: " + outputDir.getAbsolutePath());
        }

        File outputFile = new File(outputDir, fileName);
        Properties fileProps = new Properties();

        // Only include buildNumber entry if it was generated
        if (buildNumber != null) {
          fileProps.setProperty(buildIdProperty, buildNumber);
        }
        fileProps.setProperty(buildTimestampProperty, timestamp);
        if (finalAppName != null) {
          fileProps.setProperty(appNameProperty, finalAppName);
        }
        if (finalAppVersion != null) {
          fileProps.setProperty(appVersionProperty, finalAppVersion);
        }

        try (FileWriter writer = new FileWriter(outputFile)) {
          fileProps.store(writer, "Created by build system. Do not modify.");
        }
        getLog().info("Build metadata written to " + outputFile.getAbsolutePath());
      }

      getLog().info("Build metadata generated successfully");

    } catch (MojoExecutionException e) {
      throw e;
    } catch (Exception e) {
      throw new MojoExecutionException("Error generating build metadata", e);
    }
  }

  /**
   * Generates a unique build number by hashing a combination of the current timestamp, a random
   * UUID, and the project artifactId using SHA-256.
   *
   * @param length number of hex characters to return (1–64)
   * @return hex string of the requested length
   */
  private String generateBuildNumber(int length) {
    try {
      String input =
          System.currentTimeMillis()
              + "-"
              + UUID.randomUUID()
              + "-"
              + project.getArtifactId()
              + "-"
              + new SecureRandom().nextLong();

      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes());
      String hex = HexFormat.of().formatHex(hash); // always 64 chars
      return hex.substring(0, length);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }
}
