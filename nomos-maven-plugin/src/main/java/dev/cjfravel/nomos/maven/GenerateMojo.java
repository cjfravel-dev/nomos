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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Maven goal to generate Scala case classes from Nomos JSON templates.
 *
 * Scans the template directory for JSON files and generates code for each template.
 * All configuration is contained in the template files themselves.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateMojo extends AbstractMojo {

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Directory containing template files.
     * All paths are resolved relative to ${project.basedir}.
     */
    @Parameter(property = "nomos.templateDirectory", defaultValue = "src/main/resources/nomos/templates")
    private String templateDirectory;

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
     * Output directory for generated sources, relative to ${project.basedir}.
     */
    @Parameter(property = "nomos.outputDirectory", defaultValue = "src/main/scala")
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
            getLog().warn("Template directory does not exist: " + templateDir.getAbsolutePath());
            getLog().warn("Skipping code generation");
            return;
        }

        // Find all template files
        List<File> templateFiles = findTemplateFiles(templateDir);
        
        if (templateFiles.isEmpty()) {
            getLog().warn("No template files found in: " + templateDir.getAbsolutePath());
            return;
        }

        getLog().info("Found " + templateFiles.size() + " template file(s)");

        // Parse each template; refs resolve across all files in a shared definition space
        int parseFailures = 0;
        java.util.List<MultiTemplate> templates = new ArrayList<>();

        for (File templateFile : templateFiles) {
            String relativePath = templateDir.toPath().relativize(templateFile.toPath()).toString();
            getLog().info("");
            getLog().info("Processing: " + relativePath);

            try {
                String templateContent = new String(Files.readAllBytes(templateFile.toPath()));
                String basePackage = packageFromPath(templateDir.toPath(), templateFile.toPath());
                getLog().info("  Base package: " + basePackage);

                scala.util.Either<?, ?> parseResult = Nomos.parseTemplate(templateContent, basePackage);
                if (parseResult.isLeft()) {
                    getLog().error("  Failed to parse template: " + parseResult.left().get());
                    parseFailures++;
                    continue;
                }
                MultiTemplate template = (MultiTemplate) parseResult.right().get();
                getLog().info("  Definitions: " + template.definitions().size());
                templates.add(template);
            } catch (Exception e) {
                getLog().error("  Error processing template: " + e.getMessage(), e);
                parseFailures++;
            }
        }

        if (parseFailures > 0) {
            throw new MojoFailureException("Code generation failed for " + parseFailures + " template(s)");
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
        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".json"))
                .map(Path::toFile)
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to scan template directory", e);
        }
    }
}