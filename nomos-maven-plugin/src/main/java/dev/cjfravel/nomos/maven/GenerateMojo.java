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
    @Parameter(property = "nomos.templateDirectory", defaultValue = "src/main/resources/templates")
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

        // Process each template
        int successCount = 0;
        int failureCount = 0;
        List<String> generatedSourceRoots = new ArrayList<>();

        for (File templateFile : templateFiles) {
            String relativePath = templateDir.toPath().relativize(templateFile.toPath()).toString();
            getLog().info("");
            getLog().info("Processing: " + relativePath);
            
            try {
                // Read and parse template
                String templateContent = new String(Files.readAllBytes(templateFile.toPath()));
                scala.util.Either<?, ?> parseResult = Nomos.parseTemplate(templateContent);
                
                if (parseResult.isLeft()) {
                    getLog().error("  Failed to parse template: " + parseResult.left().get());
                    failureCount++;
                    continue;
                }
                
                MultiTemplate template = (MultiTemplate) parseResult.right().get();
                getLog().info("  Base package: " + template.basePackage());
                getLog().info("  Output dir: " + template.outputDir());
                getLog().info("  Definitions: " + template.definitions().size());
                
                // Resolve output directory relative to project basedir
                String resolvedOutputDir = new File(basedir, template.outputDir()).getAbsolutePath();
                
                // Create template with resolved output directory
                MultiTemplate resolvedTemplate = new MultiTemplate(
                    template.basePackage(),
                    resolvedOutputDir,
                    template.mainClass(),
                    template.definitions(),
                    template.useOptionTypes(),
                    template.listType()
                );
                
                // Generate code using the Nomos API
                scala.util.Either<?, ?> generateResult = Nomos.generateCode(resolvedTemplate);
                
                if (generateResult.isLeft()) {
                    getLog().error("  Generation failed: " + generateResult.left().get());
                    failureCount++;
                    continue;
                }
                
                // Get write report
                Object writeReport = generateResult.right().get();
                getLog().info("  Code generation completed successfully");
                successCount++;
                
                // Add resolved output directory as source root
                if (!generatedSourceRoots.contains(resolvedOutputDir)) {
                    generatedSourceRoots.add(resolvedOutputDir);
                }
                
            } catch (Exception e) {
                getLog().error("  Error processing template: " + e.getMessage(), e);
                failureCount++;
            }
        }

        // Add generated source roots to project
        for (String sourceRoot : generatedSourceRoots) {
            getLog().info("");
            getLog().info("Adding source root: " + sourceRoot);
            project.addCompileSourceRoot(sourceRoot);
        }

        // Summary
        getLog().info("");
        getLog().info("=== Generation Complete ===");
        getLog().info("Success: " + successCount);
        getLog().info("Failures: " + failureCount);
        
        if (failureCount > 0) {
            throw new MojoFailureException("Code generation failed for " + failureCount + " template(s)");
        }
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