package dev.cjfravel.nomos.maven;

import dev.cjfravel.nomos.Nomos;
import dev.cjfravel.nomos.generation.GeneratedFile;
import dev.cjfravel.nomos.generation.GeneratorConfig;
import dev.cjfravel.nomos.model.MultiTemplate;
import dev.cjfravel.nomos.parser.TemplateParser;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import scala.collection.JavaConverters;
import scala.util.Either;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Maven goal to generate Scala case classes from Nomos JSON templates.
 *
 * Scans the template directory for JSON files and generates code for each template.
 * All configuration is contained in the template files themselves.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class GenerateMojo extends AbstractMojo {

    private static final String DEFAULT_TEMPLATE_DIRECTORY = "src/main/resources/nomos/templates";

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Directory containing template files.
     * All paths are resolved relative to ${project.basedir}.
     */
    @Parameter(property = "nomos.templateDirectory", defaultValue = DEFAULT_TEMPLATE_DIRECTORY)
    private String templateDirectory;

    /**
     * Fail the build when the template directory is missing or contains no templates. A template
     * directory configured to a non-default location that does not exist always fails, since that
     * is a misconfiguration rather than "nothing to do".
     */
    @Parameter(property = "nomos.failOnMissingTemplates", defaultValue = "false")
    private boolean failOnMissingTemplates;

    /**
     * File patterns to include (Ant-style).
     */
    @Parameter(property = "nomos.includes", defaultValue = "**/*.json")
    private String includes;

    /**
     * File patterns to exclude (Ant-style).
     */
    @Parameter(property = "nomos.excludes")
    private String excludes;

    /**
     * Output directory for generated sources, relative to ${project.basedir}. Defaults to the
     * conventional generated-sources location, which is added as a compile root and should not be
     * committed. Point it at a source dir (e.g. src/main/scala) only if you intend to commit output.
     */
    @Parameter(property = "nomos.outputDirectory", defaultValue = "target/generated-sources/nomos")
    private String outputDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("=== Nomos Code Generation ===");
        
        // Resolve template directory relative to project basedir
        File basedir = project.getBasedir();
        File templateDir = new File(basedir, templateDirectory);
        
        getLog().info("Project basedir: " + basedir.getAbsolutePath());
        getLog().info("Template directory: " + templateDir.getAbsolutePath());
        
        if (!templateDir.exists()) {
            boolean configured = !DEFAULT_TEMPLATE_DIRECTORY.equals(templateDirectory);
            if (configured || failOnMissingTemplates) {
                throw new MojoFailureException("Template directory does not exist: " + templateDir.getAbsolutePath());
            }
            getLog().warn("Template directory does not exist: " + templateDir.getAbsolutePath());
            getLog().warn("Skipping code generation");
            return;
        }

        // Find all template files
        List<File> templateFiles = findTemplateFiles(templateDir);
        
        if (templateFiles.isEmpty()) {
            if (failOnMissingTemplates) {
                throw new MojoFailureException("No template files found in: " + templateDir.getAbsolutePath());
            }
            getLog().warn("No template files found in: " + templateDir.getAbsolutePath());
            return;
        }

        getLog().info("Found " + templateFiles.size() + " template file(s)");

        // Parse each template; refs resolve across all files in a shared definition space
        List<String> parseFailures = new ArrayList<>();
        java.util.List<MultiTemplate> templates = new ArrayList<>();

        for (File templateFile : templateFiles) {
            String relativePath = templateDir.toPath().relativize(templateFile.toPath()).toString().replace(File.separatorChar, '/');
            getLog().info("");
            getLog().info("Processing: " + relativePath);

            try {
                String templateContent = new String(Files.readAllBytes(templateFile.toPath()), StandardCharsets.UTF_8);
                String basePackage = packageFromPath(templateDir.toPath(), templateFile.toPath());
                getLog().info("  Base package: " + basePackage);

                scala.util.Either<?, ?> parseResult = Nomos.parseTemplateDeferred(templateContent, basePackage, relativePath);
                if (parseResult.isLeft()) {
                    String details = String.valueOf(parseResult.left().get());
                    getLog().error("  Failed to parse template: " + details);
                    parseFailures.add(relativePath + ": " + details);
                    continue;
                }
                MultiTemplate template = (MultiTemplate) parseResult.right().get();
                getLog().info("  Definitions: " + template.definitions().size());
                templates.add(template);
            } catch (Exception e) {
                getLog().error("  Error processing template: " + e.getMessage(), e);
                parseFailures.add(relativePath + ": " + e.getMessage());
            }
        }

        if (!parseFailures.isEmpty()) {
            throw new MojoFailureException(
                    "Code generation failed for " + parseFailures.size() + " template(s): "
                            + String.join("; ", parseFailures));
        }

        String resolvedOutputDir = new File(basedir, outputDirectory).getAbsolutePath();
        scala.util.Either<?, ?> generateResult = Nomos.generateAll(templates, resolvedOutputDir);
        if (generateResult.isLeft()) {
            throw new MojoFailureException("Generation failed: " + generateResult.left().get());
        }
        getLog().info("");
        getLog().info("Code generation completed successfully");
        getLog().info("Adding source root: " + resolvedOutputDir);
        project.addCompileSourceRoot(resolvedOutputDir);

        getLog().info("");
        getLog().info("=== Generation Complete ===");
    }

    /**
     * Derives the base package from a template file's location relative to the template root.
     * e.g. root/com/example/models/user.json -> "com.example.models".
     */
    private String packageFromPath(Path root, Path templateFile) {
        Path relative = root.relativize(templateFile);
        Path parent = relative.getParent();
        if (parent == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Path part : parent) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(part.toString());
        }
        return sb.toString();
    }

    /**
     * Finds all template files in the directory matching the include/exclude patterns.
     */
    private List<File> findTemplateFiles(File directory) throws MojoExecutionException {
        try {
            org.codehaus.plexus.util.DirectoryScanner scanner = new org.codehaus.plexus.util.DirectoryScanner();
            scanner.setBasedir(directory);
            String[] includePatterns = splitPatterns(includes);
            if (includePatterns.length > 0) {
                scanner.setIncludes(includePatterns);
            }
            String[] excludePatterns = splitPatterns(excludes);
            if (excludePatterns.length > 0) {
                scanner.setExcludes(excludePatterns);
            }
            scanner.addDefaultExcludes();
            scanner.scan();

            List<File> matched = new ArrayList<>();
            for (String relative : scanner.getIncludedFiles()) {
                matched.add(new File(directory, relative));
            }
            return matched;
        } catch (RuntimeException e) {
            throw new MojoExecutionException("Failed to scan template directory", e);
        }
    }

    /**
     * Splits a comma-separated Ant pattern list into trimmed, non-empty patterns.
     */
    private String[] splitPatterns(String patterns) {
        if (patterns == null || patterns.trim().isEmpty()) {
            return new String[0];
        }
        List<String> result = new ArrayList<>();
        for (String part : patterns.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result.toArray(new String[0]);
    }
}
