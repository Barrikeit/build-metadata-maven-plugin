package dev.barrikeit.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Generates build metadata (build ID, timestamp, name, version) and:
 *
 * <ul>
 *   <li>injects the values as Maven properties (available to other plugins and resource filtering)
 *   <li>writes a {@code .properties} file directly into the project's source resources directory so
 *       the IDE can find it on the classpath without running any Maven phase first
 * </ul>
 *
 * <p>The output file is written with sorted keys and no date comment, so the file content is
 * deterministic: re-running with the same inputs produces an identical file, avoiding spurious git
 * diffs when nothing actually changed.
 */
@Mojo(name = "generate-build-metadata", defaultPhase = LifecyclePhase.INITIALIZE)
public class BuildMetadata extends AbstractMojo {

  private static final int MAX_BUILD_ID_LENGTH = 64;

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  /** Skip plugin execution entirely. */
  @Parameter(property = "buildMetadata.skip", defaultValue = "false")
  private boolean skip;

  /** Override default application name. Defaults to {@code project.build.finalName}. */
  @Parameter(property = "appName")
  private String appName;

  /** Override default application version. Defaults to {@code project.version}. */
  @Parameter(property = "appVersion")
  private String appVersion;

  /** Maven property name written for the build ID. */
  @Parameter(property = "buildIdProperty", defaultValue = "buildNumber")
  private String buildIdProperty;

  /** Maven property name written for the build timestamp. */
  @Parameter(property = "buildTimestampProperty", defaultValue = "buildTimestamp")
  private String buildTimestampProperty;

  /** Maven property name written for the application name. */
  @Parameter(property = "appNameProperty", defaultValue = "appName")
  private String appNameProperty;

  /** Maven property name written for the application version. */
  @Parameter(property = "appVersionProperty", defaultValue = "appVersion")
  private String appVersionProperty;

  /**
   * Length of the build ID in hex characters (1–64). Set to {@code 0} to skip build ID generation.
   * SHA-256 produces 64 hex chars; values above that are capped.
   */
  @Parameter(property = "buildIdLength", defaultValue = "20")
  private int buildIdLength;

  /** {@link java.text.SimpleDateFormat} pattern for the build timestamp. */
  @Parameter(property = "buildTimestampFormat", defaultValue = "yyyyMMddHHmmssSSS")
  private String buildTimestampFormat;

  /**
   * Whether to write a {@code .properties} file. Defaults to {@code true}.
   *
   * <p>The file is written to {@link #fileDirectory}/{@link #fileName}. By default this is {@code
   * src/main/resources/build.properties} — a location the IDE already has on its classpath, so the
   * application can be run directly from the IDE without executing any Maven phase first.
   */
  @Parameter(property = "generateFile", defaultValue = "true")
  private boolean generateFile;

  /**
   * Directory where the properties file is written.
   *
   * <p>Defaults to {@code src/main/resources} so the file is always available to the IDE without a
   * prior Maven build.
   */
  @Parameter(property = "fileDirectory", defaultValue = "${project.basedir}/src/main/resources")
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

    if (buildIdLength < 0) {
      throw new MojoExecutionException("buildIdLength must be >= 0, got: " + buildIdLength);
    }
    if (buildIdLength > MAX_BUILD_ID_LENGTH) {
      throw new MojoExecutionException(
          "buildIdLength cannot exceed " + MAX_BUILD_ID_LENGTH + ", got: " + buildIdLength);
    }

    try {
      String timestamp = new SimpleDateFormat(buildTimestampFormat).format(new Date());
      String buildNumber = buildIdLength > 0 ? generateBuildNumber(buildIdLength) : null;

      String finalAppName =
          (appName != null && !appName.isBlank()) ? appName : project.getBuild().getFinalName();
      String finalAppVersion =
          (appVersion != null && !appVersion.isBlank()) ? appVersion : project.getVersion();

      // Collect all entries; use TreeMap for deterministic ordering
      Map<String, String> entries = new TreeMap<>();
      if (buildNumber != null) entries.put(buildIdProperty, buildNumber);
      entries.put(buildTimestampProperty, timestamp);
      if (finalAppName != null) entries.put(appNameProperty, finalAppName);
      if (finalAppVersion != null) entries.put(appVersionProperty, finalAppVersion);

      // Inject into Maven property space (used by banner plugin and resource filtering)
      entries.forEach(
          (k, v) -> {
            project.getProperties().setProperty(k, v);
            getLog().debug("Set Maven property " + k + " = " + v);
          });

      if (generateFile) {
        writePropertiesFile(entries);
      }

      getLog().info("Build metadata generated successfully");

    } catch (MojoExecutionException e) {
      throw e;
    } catch (Exception e) {
      throw new MojoExecutionException("Error generating build metadata", e);
    }
  }

  /**
   * Writes the entries as a {@code .properties} file with sorted keys and no date comment, so the
   * output is deterministic across runs with identical inputs.
   */
  private void writePropertiesFile(Map<String, String> entries) throws MojoExecutionException {
    File outputDir = new File(fileDirectory);
    if (!outputDir.exists() && !outputDir.mkdirs()) {
      throw new MojoExecutionException(
          "Failed to create output directory: " + outputDir.getAbsolutePath());
    }

    File outputFile = new File(outputDir, fileName);
    StringBuilder sb = new StringBuilder();
    entries.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
    String newContent = sb.toString();

    try {
      if (outputFile.exists()) {
        String current = Files.readString(outputFile.toPath());
        if (current.equals(newContent)) {
          getLog().info("Build metadata unchanged at " + outputFile.getAbsolutePath());
          return;
        }
      }
      Files.writeString(outputFile.toPath(), newContent);
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to write " + outputFile.getAbsolutePath(), e);
    }

    getLog().info("Build metadata written to " + outputFile.getAbsolutePath());
  }

  private String generateBuildNumber(int length) throws MojoExecutionException {
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
      return HexFormat.of().formatHex(hash).substring(0, length);
    } catch (NoSuchAlgorithmException e) {
      throw new MojoExecutionException("SHA-256 not available", e);
    }
  }
}
