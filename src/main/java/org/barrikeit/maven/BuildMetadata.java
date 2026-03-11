package org.barrikeit.maven;

import java.io.File;
import java.io.FileWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HexFormat;
import java.util.Properties;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/** Mojo to generate build metadata for Maven projects. */
@Mojo(name = "generate-build-metadata", defaultPhase = LifecyclePhase.INITIALIZE)
public class BuildMetadata extends AbstractMojo {

  @Component private MavenProject project;

  // ==== Parameters ====

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

  /** Length of the build number (max 32). If 0, build number is not generated. */
  @Parameter(property = "buildIdLength", defaultValue = "20")
  private int buildIdLength;

  /** Format for the timestamp. */
  @Parameter(property = "buildTimestampFormat", defaultValue = "yyyyMMddHHmmssSSS")
  private String buildTimestampFormat;

  /** Generate a file in outputDirectory. Default: false */
  @Parameter(property = "generateFile", defaultValue = "false")
  private boolean generateFile;

  /** Directory for output file if addOutputDirectoryToResources=true */
  @Parameter(
      property = "fileDirectory",
      defaultValue = "${project.build.directory}/generated-sources/build-metadata")
  private File fileDirectory;

  /** Name of the generated properties file. */
  @Parameter(property = "fileName", defaultValue = "build.properties")
  private String fileName;

  // ==== Execute ====

  @Override
  public void execute() throws MojoExecutionException {
    try {
      // Compute timestamp
      Date date = new Date();
      String timestamp = new SimpleDateFormat(buildTimestampFormat).format(date);

      // Compute random build number based on timestamp if length > 0
      String buildNumber = "";
      if (buildIdLength > 0) {
        buildNumber = generateBuildNumber(buildIdLength);
      }

      // Determine name/version
      String finalAppName = appName != null ? appName : project.getName();
      String finalAppVersion = appVersion != null ? appVersion : project.getVersion();

      // Set properties in Maven project
      if (buildIdLength > 0) {
        project.getProperties().setProperty(buildIdProperty, buildNumber);
      }
      project.getProperties().setProperty(buildTimestampProperty, timestamp);
      if (finalAppName != null) {
        project.getProperties().setProperty(appNameProperty, finalAppName);
      }
      if (finalAppVersion != null) {
        project.getProperties().setProperty(appVersionProperty, finalAppVersion);
      }

      // Optionally write to file
      if (generateFile) {
        if (!fileDirectory.exists()) {
          fileDirectory.mkdirs();
        }
        File outputFile = new File(fileDirectory, fileName);
        Properties fileProps = new Properties();
        fileProps.setProperty(buildIdProperty, buildNumber);
        fileProps.setProperty(buildTimestampProperty, timestamp);
        fileProps.setProperty(appNameProperty, finalAppName);
        fileProps.setProperty(appVersionProperty, finalAppVersion);
        try (FileWriter writer = new FileWriter(outputFile)) {
          fileProps.store(writer, "Created by build system. Do not modify");
        }
        getLog().info("Build metadata written to " + outputFile.getAbsolutePath());
      }

      getLog().info("Build metadata generated successfully");

    } catch (Exception e) {
      throw new MojoExecutionException("Error generating build metadata", e);
    }
  }

  // ==== Helper ====

  private String generateBuildNumber(int length) {
    try {
      String input = System.currentTimeMillis() + "-" + project.getArtifactId();
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes());
      String hex = HexFormat.of().formatHex(hash);

      // Truncate to desired length
      return hex.length() > length ? hex.substring(0, length) : hex;
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }
}
