package com.arsframework.plugin.apidoc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.arsframework.apidoc.core.Api;
import com.arsframework.apidoc.core.Configuration;
import com.arsframework.apidoc.core.ContextHelper;
import com.arsframework.apidoc.core.DocumentHelper;
import com.arsframework.apidoc.core.MethodAnalyser;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.RootDoc;
import com.sun.tools.javadoc.Main;
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
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomUtils;

import static com.arsframework.plugin.apidoc.XmlHelper.configuration;
import static com.arsframework.plugin.apidoc.XmlHelper.element;
import static com.arsframework.plugin.apidoc.XmlHelper.toXpp3Dom;

/**
 * Abstract build mojo
 *
 * @author Woody
 */
@Execute(phase = LifecyclePhase.COMPILE)
public abstract class AbstractBuildMojo extends AbstractMojo {
    /**
     * Source file suffix
     */
    private static final String SOURCE_FILE_SUFFIX = ".java";

    /**
     * Package definition name
     */
    private static final String PACKAGE_DEFINITION_NAME = "package ";

    /**
     * package-info definition name
     */
    private static final String PACKAGE_INFO_DEFINITION_NAME = "package-info.java";

    /**
     * Classpath separator
     */
    private static final String CLASSPATH_SEPARATOR = System.getProperty("path.separator");

    @Component
    protected BuildPluginManager manager;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession session;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
    protected List<String> compileDirectories;

    @Parameter(defaultValue = "${project.build.directory}/sources", readonly = true, required = true)
    protected String dependencySourceDirectory;

    @Parameter(defaultValue = "${project.groupId}", required = true)
    protected String includeGroupIdentities;

    /**
     * Whether the date is displayed
     */
    @Parameter(defaultValue = "false", required = true)
    protected boolean displayDate;

    /**
     * Whether the author is displayed
     */
    @Parameter(defaultValue = "false", required = true)
    protected boolean displayAuthor;

    /**
     * Whether the sample request is enabled
     */
    @Parameter(defaultValue = "true", required = true)
    protected boolean enableSampleRequest;

    /**
     * Whether the response example is enabled
     */
    @Parameter(defaultValue = "true", required = true)
    protected boolean enableResponseExample;

    /**
     * Whether the snake and underline conversion is enabled
     */
    @Parameter(defaultValue = "false", required = true)
    protected boolean enableSnakeUnderlineConversion;

    @Parameter
    protected List<String> excludeClasses;

    /**
     * Api analyser factory class
     */
    @Parameter
    protected String analyserFactoryClass;

    /**
     * Class and source mappings
     */
    private final Map<Class<?>, String> sources = new LinkedHashMap<>();

    /**
     * Source file name and class document mappings
     */
    private final Map<String, ClassDoc> documents = new HashMap<>();

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
     * Initialize class of source directory
     *
     * @param directory Source directory
     */
    private void initializeClasses(File directory) {
        for (File file : DocumentHelper.listDirectoryFiles(directory)) {
            if (file.isDirectory()) {
                this.initializeClasses(file);
            } else if (file.getName().endsWith(SOURCE_FILE_SUFFIX)
                    && !file.getName().equalsIgnoreCase(PACKAGE_INFO_DEFINITION_NAME)) {
                try {
                    this.sources.put(this.loadClass(file), file.getPath());
                } catch (IOException | ClassNotFoundException e) {
                    this.getLog().warn("Class loading failed: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Load class by source file
     *
     * @param file Source file
     * @return Class object
     * @throws IOException            IO exception
     * @throws ClassNotFoundException Class not found exception
     */
    private Class<?> loadClass(File file) throws IOException, ClassNotFoundException {
        Objects.requireNonNull(file, "file not specified");
        String name = file.getName();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!(line = line.trim()).isEmpty() && line.startsWith(PACKAGE_DEFINITION_NAME)) {
                    name = (line.split("[ ;]")[1]).concat(".").concat(name);
                    break;
                }
            }
        }
        return ContextHelper.getClassLoader().loadClass(name.substring(0, name.lastIndexOf('.')));
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
                    element("includeGroupIds", String.join(",", ContextHelper.getIncludeGroupIdentities())),
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
                DocumentHelper.copyDirectory(new File(root), new File(this.dependencySourceDirectory));
            }
        }
    }

    /**
     * Build method analyser factory
     *
     * @return Method analyser factory
     * @throws ReflectiveOperationException Reflective operation exception
     */
    private MethodAnalyser.Factory buildMethodAnalyserFactory() throws ReflectiveOperationException {
        String type;
        if (this.analyserFactoryClass == null || (type = this.analyserFactoryClass.trim()).isEmpty()) {
            return MethodAnalyser::new;
        }
        return (MethodAnalyser.Factory) Class.forName(type, true, ContextHelper.getClassLoader()).newInstance();
    }

    /**
     * Initialize
     *
     * @throws IOException            IO exception
     * @throws MojoExecutionException Mojo execution exception
     */
    private void initialize() throws IOException, MojoExecutionException {
        // Class loader
        URLClassLoader classLoader = this.initializeClassLoader();
        ContextHelper.setClassLoader(classLoader);

        // Class path
        String classpath = String.join(CLASSPATH_SEPARATOR,
                Stream.of(classLoader.getURLs()).map(URL::getPath).toArray(String[]::new));
        ContextHelper.setClasspath(classpath);

        // Configuration
        Configuration configuration = Configuration.builder().displayDate(this.displayDate)
                .displayAuthor(this.displayAuthor).enableSampleRequest(this.enableSampleRequest)
                .enableResponseExample(this.enableResponseExample)
                .enableSnakeUnderlineConversion(this.enableSnakeUnderlineConversion).build();
        ContextHelper.setConfiguration(configuration);

        // Include group identities
        Set<String> groups = Stream.of(this.includeGroupIdentities.split("[, ]"))
                .filter(group -> group != null && !group.isEmpty()).collect(Collectors.toSet());
        groups.add(this.project.getGroupId());
        ContextHelper.setIncludeGroupIdentities(groups);

        // Document provider
        ContextHelper.setDocumentProvider(clazz -> {
            Objects.requireNonNull(clazz, "clazz not specified");
            String name = clazz.getName().replace("$", ".");
            ClassDoc document = this.documents.get(name);
            if (document == null) {
                String source = this.sources.get(clazz);
                if (source == null) {
                    return null;
                }
                Main.execute(classLoader, "-doclet", Doclet.class.getName(), "-quiet", "-encoding", "utf-8",
                        "-sourcepath", this.dependencySourceDirectory, "-classpath", classpath, source);
                if (Doclet.root != null) {
                    for (ClassDoc doc : Doclet.root.classes()) {
                        if (doc.toString().equals(name)) {
                            document = doc;
                        }
                        this.documents.put(doc.toString(), doc);
                    }
                }
            }
            return document;
        });

        // Unpack dependencies
        this.unpackDependencies();

        // Unpack project sources
        this.unpackProjectSources();

        // initialize classes
        this.initializeClasses(new File(this.dependencySourceDirectory));
    }

    /**
     * Get apis
     *
     * @param factory Method analyser factory
     * @return Api list
     */
    private List<Api> getApis(MethodAnalyser.Factory factory) {
        Objects.requireNonNull(factory, "factory not specified");
        return this.sources.keySet().stream().filter(clazz -> DocumentHelper.isApiClass(clazz)
                && (this.excludeClasses == null
                || this.excludeClasses.stream().noneMatch(clazz.getName()::startsWith))).flatMap(clazz -> {
            try {
                return Stream.of(clazz.getDeclaredMethods()).filter(DocumentHelper::isApiMethod)
                        .map(method -> factory.build(method).parse());
            } catch (Throwable e) {
                this.getLog().warn("Api loading failed: " + e.getMessage());
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * Build the document with apis
     *
     * @param apis Api list
     * @throws Exception Exception
     */
    protected abstract void building(List<Api> apis) throws Exception;

    @Override
    public final void execute() throws MojoExecutionException {
        try {
            this.initialize();
            this.building(this.getApis(this.buildMethodAnalyserFactory()));
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            ContextHelper.clear();
            DocumentHelper.removeDirectory(new File(this.dependencySourceDirectory));
        }
    }

    /**
     * Document doclet
     */
    public static final class Doclet {
        /**
         * Root document
         */
        private static RootDoc root;

        /**
         * Receive the root document
         *
         * @param root Root document
         * @return true/false
         */
        public static boolean start(RootDoc root) {
            Doclet.root = root;
            return true;
        }
    }
}
