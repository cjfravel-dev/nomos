package dev.cjfravel.nomos.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GenerateMojoTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void generatesSourcesAndAddsCompileRoot() throws Exception {
        File baseDir = temporaryFolder.newFolder("project");
        writeTemplate(baseDir, "src/main/resources/nomos/templates/com/example/user.json", validTemplate("User"));
        MavenProject project = project(baseDir);

        mojo(project, "src/main/resources/nomos/templates", false, "**/*.json", null).execute();

        File generated = new File(baseDir, "target/generated-sources/nomos/com/example/User.scala");
        assertTrue(generated.isFile());
        assertTrue(Files.readAllLines(generated.toPath(), StandardCharsets.UTF_8).contains("package com.example"));
        assertEquals(
                new File(baseDir, "target/generated-sources/nomos").getAbsolutePath(),
                project.getCompileSourceRoots().get(project.getCompileSourceRoots().size() - 1));
    }

    @Test
    public void skipsMissingDefaultTemplateDirectory() throws Exception {
        File baseDir = temporaryFolder.newFolder("missing-default");
        MavenProject project = project(baseDir);
        int rootsBefore = project.getCompileSourceRoots().size();

        mojo(project, "src/main/resources/nomos/templates", false, "**/*.json", null).execute();

        assertEquals(rootsBefore, project.getCompileSourceRoots().size());
        assertFalse(new File(baseDir, "target/generated-sources/nomos").exists());
    }

    @Test
    public void failsForMissingConfiguredTemplateDirectory() throws Exception {
        File baseDir = temporaryFolder.newFolder("missing-custom");

        try {
            mojo(project(baseDir), "custom/templates", false, "**/*.json", null).execute();
            fail("Expected a missing configured template directory to fail");
        } catch (MojoFailureException expected) {
            assertTrue(expected.getMessage().contains("Template directory does not exist"));
        }
    }

    @Test
    public void appliesIncludeAndExcludePatterns() throws Exception {
        File baseDir = temporaryFolder.newFolder("patterns");
        writeTemplate(baseDir, "templates/com/example/included.json", validTemplate("Included"));
        writeTemplate(baseDir, "templates/com/example/excluded.json", validTemplate("Excluded"));

        mojo(project(baseDir), "templates", true, "**/*.json", "**/excluded.json").execute();

        assertTrue(new File(baseDir, "target/generated-sources/nomos/com/example/Included.scala").isFile());
        assertFalse(new File(baseDir, "target/generated-sources/nomos/com/example/Excluded.scala").exists());
    }

    @Test
    public void rejectsInvalidPackageDerivedFromTemplatePath() throws Exception {
        File baseDir = temporaryFolder.newFolder("invalid-package");
        writeTemplate(baseDir, "templates/Bad-Package/user.json", validTemplate("User"));

        try {
            mojo(project(baseDir), "templates", true, "**/*.json", null).execute();
            fail("Expected an invalid directory-derived package to fail");
        } catch (MojoFailureException expected) {
            assertTrue(expected.getMessage().contains("basePackage"));
            assertFalse(new File(baseDir, "target/generated-sources/nomos/Bad-Package/User.scala").exists());
        }
    }

    private static GenerateMojo mojo(
            MavenProject project,
            String templateDirectory,
            boolean failOnMissingTemplates,
            String includes,
            String excludes) throws Exception {
        GenerateMojo mojo = new GenerateMojo();
        set(mojo, "project", project);
        set(mojo, "templateDirectory", templateDirectory);
        set(mojo, "failOnMissingTemplates", failOnMissingTemplates);
        set(mojo, "includes", includes);
        set(mojo, "excludes", excludes);
        set(mojo, "outputDirectory", "target/generated-sources/nomos");
        return mojo;
    }

    private static MavenProject project(File baseDir) throws Exception {
        File pom = new File(baseDir, "pom.xml");
        Files.write(pom.toPath(), "<project/>".getBytes(StandardCharsets.UTF_8));
        MavenProject project = new MavenProject();
        project.setFile(pom);
        return project;
    }

    private static void writeTemplate(File baseDir, String path, String content) throws Exception {
        File file = new File(baseDir, path);
        assertTrue(file.getParentFile().mkdirs() || file.getParentFile().isDirectory());
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static String validTemplate(String name) {
        return "{\"definitions\":[{\"name\":\"" + name + "\",\"template\":{\"value\":\"string\"}}]}";
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
