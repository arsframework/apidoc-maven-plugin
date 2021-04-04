package com.arsframework.plugin.apidoc;

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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomUtils;

import static com.arsframework.plugin.apidoc.ConfigurationHelper.configuration;
import static com.arsframework.plugin.apidoc.ConfigurationHelper.element;
import static com.arsframework.plugin.apidoc.ConfigurationHelper.toXpp3Dom;

/**
 * Api document builder
 *
 * @author Woody
 */
@Execute(phase = LifecyclePhase.COMPILE)
@Mojo(name = "build", requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresDependencyCollection = ResolutionScope.COMPILE, threadSafe = true)
public class ApidocBuildMojo extends AbstractMojo {
    @Component
    private BuildPluginManager manager;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
    private List<String> compileDirectories;

    @Parameter(defaultValue = "${project.build.directory}/sources", readonly = true, required = true)
    private String dependencySourceDirectory;

    @Parameter(defaultValue = "${project.groupId}", required = true)
    private String includeGroupIdentities;

    @Parameter(defaultValue = "${project.basedir}/${project.name}.apidoc", required = true)
    private String output;

    /**
     * Whether the date is displayed
     */
    @Parameter(defaultValue = "false", required = true)
    private boolean displayDate;

    /**
     * Whether the author is displayed
     */
    @Parameter(defaultValue = "false", required = true)
    private boolean displayAuthor;

    /**
     * Whether the sample request is enabled
     */
    @Parameter(defaultValue = "false", required = true)
    private boolean enableSampleRequest;

    /**
     * Copy file directory
     *
     * @param source Source directory
     * @param target Target directory
     * @throws IOException IO exception
     */
    private static void copyDirectory(File source, File target) throws IOException {
        if (source == null || target == null || !source.exists() || !source.isDirectory() || target.isFile()) {
            return;
        }
        if (!target.exists()) {
            target.mkdirs();
        }
        for (File file : source.listFiles()) {
            File to = new File(target, file.getName());
            if (file.isFile()) {
                Files.copy(file.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                copyDirectory(file, to);
            }
        }
    }

    /**
     * Remove file directory
     *
     * @param directory File directory
     */
    private static void removeDirectory(File directory) {
        if (directory != null && directory.exists()) {
            if (directory.isDirectory()) {
                for (File child : directory.listFiles()) {
                    removeDirectory(child);
                }
            }
            directory.delete();
        }
    }

    private Set<String> getIncludeGroupIdentities() {
        return Stream.of(this.includeGroupIdentities.split("[, ]"))
                .filter(group -> group != null && !group.isEmpty()).collect(Collectors.toSet());
    }

    /**
     * Initialize class loader
     *
     * @return URL class loader
     * @throws IOException IO exception
     */
    private URLClassLoader initializeClassLoader() throws IOException {
        Set<Artifact> artifacts = this.project.getArtifacts();
        URL[] urls = new URL[this.compileDirectories.size() + artifacts.size()];
        int i = 0;
        for (String directory : this.compileDirectories) {
            urls[i++] = new File(directory).toURI().toURL();
        }
        for (Artifact artifact : artifacts) {
            urls[i++] = artifact.getFile().toURI().toURL();
        }
        return new URLClassLoader(urls, this.getClass().getClassLoader());
    }

    /**
     * Unpack dependencies
     *
     * @throws MojoExecutionException Mojo execution exception
     */
    private void unpackDependencies() throws MojoExecutionException {
        Plugin plugin = this.project.getPluginManagement().getPluginsAsMap()
                .get("org.apache.maven.plugins:maven-dependency-plugin");
        if (plugin == null) {
            throw new MojoExecutionException("Dependency plugin could not be found in <pluginManagement>");
        }
        try {
            MojoDescriptor descriptor = this.manager.loadPlugin(plugin, this.project.getRemotePluginRepositories(),
                    this.session.getRepositorySession()).getMojo("unpack-dependencies");
            Xpp3Dom configuration = Xpp3DomUtils.mergeXpp3Dom(configuration(
                    element("classifier", "sources"),
                    element("includeScope", "compile"),
                    element("includeGroupIds", String.join(",", this.getIncludeGroupIdentities())),
                    element("failOnMissingClassifierArtifact", "false"),
                    element("outputDirectory", this.dependencySourceDirectory)
            ), toXpp3Dom(descriptor.getMojoConfiguration()));
            this.manager.executeMojo(this.session, new MojoExecution(descriptor, configuration));
        } catch (Exception e) {
            throw new MojoExecutionException("Unpack dependencies failed", e);
        }
    }

    /**
     * Unpack project sources
     *
     * @throws IOException IO exception
     */
    private void unpackProjectSources() throws IOException {
        List<String> roots = this.project.getCompileSourceRoots();
        if (roots != null && !roots.isEmpty()) {
            for (String root : roots) {
                copyDirectory(new File(root), new File(this.dependencySourceDirectory));
            }
        }
    }

    @Override
    public void execute() throws MojoExecutionException {
        try {
            this.unpackDependencies();
            this.unpackProjectSources();
            URLClassLoader classLoader = this.initializeClassLoader();
            Configuration configuration = Configuration.builder().displayDate(this.displayDate)
                    .displayAuthor(this.displayAuthor).enableSampleRequest(this.enableSampleRequest)
                    .includeGroupIdentities(this.getIncludeGroupIdentities()).build();
            try (Writer writer = new BufferedWriter(new FileWriter(this.output))) {
                ApidocAnalyser.parse(classLoader, this.dependencySourceDirectory, configuration, writer);
            }
            getLog().info(String.format("Api document built successfully (%s)", this.output));
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            removeDirectory(new File(this.dependencySourceDirectory));
        }
    }
}
