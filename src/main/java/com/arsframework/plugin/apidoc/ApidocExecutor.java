package com.arsframework.plugin.apidoc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.RootDoc;
import com.sun.tools.javadoc.Main;

/**
 * Apidoc executor
 *
 * @author Woody
 */
public class ApidocExecutor {
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

    /**
     * classpath
     */
    private final String classpath;

    /**
     * Class loader
     */
    private final URLClassLoader classLoader;

    /**
     * Source file directory
     */
    private final String sourceDirectory;

    /**
     * Api document configuration
     */
    private final Configuration configuration;

    /**
     * Class and source mappings
     */
    private final Map<Class<?>, String> sources = new LinkedHashMap<>();

    /**
     * Source file name and class document mappings
     */
    private final Map<String, ClassDoc> documents = new HashMap<>();

    public ApidocExecutor(URLClassLoader classLoader, String sourceDirectory, Configuration configuration) {
        Objects.requireNonNull(classLoader, "classLoader not specified");
        Objects.requireNonNull(sourceDirectory, "sourceDirectory not specified");
        Objects.requireNonNull(configuration, "configuration not specified");
        this.classLoader = classLoader;
        this.sourceDirectory = sourceDirectory;
        this.configuration = configuration;
        this.classpath = String.join(CLASSPATH_SEPARATOR,
                Stream.of(classLoader.getURLs()).map(URL::getPath).toArray(String[]::new));
    }

    /**
     * Convert the parameter to document
     *
     * @param define     Parameter annotation define
     * @param parent     Parent parameter key
     * @param parameter  Parameter object
     * @param demandable Parameter is demandable
     * @return Parameter document string
     */
    private static String parameter2document(String define, String parent, Parameter parameter, boolean demandable) {
        Objects.requireNonNull(define, "define not specified");
        Objects.requireNonNull(parameter, "parameter not specified");
        StringBuilder document = new StringBuilder();
        String name = parent == null ? parameter.getName() : (parent + "." + parameter.getName());
        document.append("\n * ").append(define).append(" { ").append(parameter.getType().getSimpleName());
        if (parameter.isMultiple()) {
            document.append("[]");
        }
        List<Parameter.Option> options = parameter.getOptions();
        if (options != null && !options.isEmpty()) {
            document.append(" = ").append(options.stream().map(option -> option.getValue() == null ? option.getKey() :
                    String.format("%s(%s)", option.getKey(), option.getValue())).collect(Collectors.joining(" , ")));
        }
        Parameter.Size size = parameter.getSize();
        if (size != null) {
            String min = size.getMin() == null ? "" :
                    BigDecimal.valueOf(size.getMin()).stripTrailingZeros().toPlainString();
            String max = size.getMax() == null ? "" :
                    BigDecimal.valueOf(size.getMax()).stripTrailingZeros().toPlainString();
            if (Number.class.isAssignableFrom(parameter.getType())) {
                document.append(String.format(" { %s-%s }", min, max));
            } else {
                document.append(String.format(" { %s..%s }", min, max));
            }
        } else if (parameter.getFormat() != null) {
            document.append(String.format(" { %s }", parameter.getFormat()));
        }
        document.append(" } ");
        String property = parameter.getDefaultValue() == null ? name : (name + " = " + parameter.getDefaultValue());
        if (parameter.isRequired() || demandable) {
            document.append(property);
        } else {
            document.append("[ ").append(property).append(" ]");
        }
        if (parameter.getDescription() != null) {
            document.append(" ");
            if (parameter.isDeprecated()) {
                document.append("<p class=\"deprecated\"><span>DEPRECATED</span></p>\n *\n * ");
            }
            document.append(parameter.getDescription().replace("\n", "\n *\n *"));
        }
        if (parameter.getFields() != null) {
            parameter.getFields().forEach(p -> document.append(parameter2document(define, name, p, demandable)));
        }
        return document.toString();
    }

    /**
     * Convert parameter to example json
     *
     * @param parameter Parameter object
     * @return Parameter example json
     */
    private static String parameter2example(Parameter parameter) {
        Objects.requireNonNull(parameter, "parameter not specified");
        Class<?> type = parameter.getType();
        String example = parameter.getExample();
        List<Parameter> fields = parameter.getFields();
        if (type == Object.class && fields != null && !fields.isEmpty()) {
            example = String.format("{%s}", fields.stream().map(f ->
                    String.format("\"%s\":%s", f.getName(), parameter2example(f))).collect(Collectors.joining(", ")));
        } else if (type == Boolean.class) {
            example = example == null ? "true" : example;
        } else if (type == String.class) {
            if (parameter.getOriginal() == Locale.class) {
                example = String.format("\"%s\"", example == null ? Locale.getDefault() : example);
            } else if (parameter.getOriginal() == TimeZone.class) {
                example = String.format("\"%s\"", example == null ? TimeZone.getDefault().getID() : example);
            } else {
                List<Parameter.Option> options = parameter.getOptions();
                example = String.format("\"%s\"", example == null ?
                        options == null || options.isEmpty() ? "" : options.get(0).getKey() : example);
            }
        } else if (ClassHelper.isIntClass(type)) {
            example = example == null ? "1" : example;
        } else if (ClassHelper.isFloatClass(type)) {
            example = example == null ? "1.0" : example;
        } else if (type == Date.class) {
            String format = parameter.getFormat();
            example = example == null ? format == null ? String.valueOf(System.currentTimeMillis()) :
                    String.format("\"%s\"", DateTimeFormatter.ofPattern(format).format(LocalDateTime.now())) : example;
        } else if (ClassHelper.isStreamClass(type)) {
            example = example == null ? "[0b00000001]" : example;
        }
        return parameter.isMultiple() ? example == null ? "[]" : String.format("[%s]", example) : example;
    }

    /**
     * Get document of class
     *
     * @param clazz Class object
     * @return Class document object
     */
    private ClassDoc getDocument(Class<?> clazz) {
        Objects.requireNonNull(clazz, "clazz not specified");
        String name = clazz.getName().replace("$", ".");
        ClassDoc document = this.documents.get(name);
        if (document == null) {
            String source = this.sources.get(clazz);
            if (source == null) {
                return null;
            }
            Main.execute(this.classLoader, "-doclet", Doclet.class.getName(), "-quiet", "-encoding", "utf-8",
                    "-sourcepath", this.sourceDirectory, "-classpath", this.classpath, source);
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
        return this.classLoader.loadClass(name.substring(0, name.lastIndexOf('.')));
    }

    /**
     * Initialize class of source directory
     *
     * @param directory Source directory
     * @throws IOException            IO exception
     * @throws ClassNotFoundException Class not found exception
     */
    private void initializeClasses(File directory) throws IOException, ClassNotFoundException {
        for (File file : ApidocHelper.listDirectoryFiles(directory)) {
            if (file.isDirectory()) {
                this.initializeClasses(file);
            } else if (file.getName().endsWith(SOURCE_FILE_SUFFIX)
                    && !file.getName().equalsIgnoreCase(PACKAGE_INFO_DEFINITION_NAME)) {
                this.sources.put(this.loadClass(file), file.getPath());
            }
        }
    }

    /**
     * Convert the api to document
     *
     * @param api Api object
     * @return Api document string
     */
    private String api2document(Api api) {
        Objects.requireNonNull(api, "api not specified");
        StringBuilder document = new StringBuilder();
        document.append("\n/**");
        document.append("\n * @api {").append(String.join(" | ", api.getMethods())).append("} ")
                .append(api.getUrl()).append(" ").append(api.getName());
        if (!this.configuration.isEnableSampleRequest()) {
            document.append("\n * @apiSampleRequest off");
        }
        document.append("\n * @apiName ").append(api.getKey());
        document.append("\n * @apiGroup ").append(api.getGroup());
        document.append("\n * @apiHeader ").append(api.getHeader());
        if (api.getVersion() != null) {
            document.append("\n * @apiVersion ").append(api.getVersion());
        }
        StringBuilder description = new StringBuilder();
        if (api.getDescription() != null) {
            description.append(api.getDescription().replace("\n", "\n *\n *"));
        }
        if (api.getAuthors() != null && !api.getAuthors().isEmpty() && this.configuration.isDisplayAuthor()) {
            description.append("\n *\n * Author: ").append(String.join(", ", api.getAuthors()));
        }
        if (api.getDate() != null && this.configuration.isDisplayDate()) {
            description.append("\n *\n * Date: ").append(api.getDate());
        }
        if (description.length() > 0) {
            document.append("\n * @apiDescription ").append(description.toString());
        }
        if (api.isDeprecated()) {
            document.append("\n * @apiDeprecated");
        }
        List<Parameter> parameters = api.getParameters();
        if (parameters != null && !parameters.isEmpty()) {
            parameters.forEach(parameter -> document.append(parameter2document("@apiParam", null, parameter, false)));
        }
        Parameter returned = api.getReturned();
        if (returned != null) {
            List<Parameter> fields = returned.getFields();
            if (returned.isMultiple() || fields == null || fields.isEmpty()) {
                document.append(parameter2document("@apiSuccess", null, returned, true));
            } else {
                fields.forEach(field -> document.append(parameter2document("@apiSuccess", null, field, true)));
            }
            if (this.configuration.isEnableResponseExample()) {
                document.append("\n * @apiSuccessExample Response");
                document.append("\n *\n * ").append(parameter2example(returned));
            }
        }
        document.append("\n */\n");
        return document.toString();
    }

    /**
     * Api document analysis executing
     *
     * @param output Api document file
     * @throws IOException            IO exception
     * @throws ClassNotFoundException Class not found exception
     */
    public void execute(String output)
            throws IOException, ClassNotFoundException {
        Objects.requireNonNull(output, "output not specified");
        this.initializeClasses(new File(this.sourceDirectory));

        // Build apis
        List<Api> apis = this.sources.keySet().stream().filter(ApidocHelper::isApiClass).flatMap(clazz ->
                Stream.of(clazz.getDeclaredMethods()).filter(ApidocHelper::isApiMethod)
                        .map(method -> new MethodAnalyser(method, this::getDocument, this.configuration).parse())
        ).collect(Collectors.toList());
        if (apis.isEmpty()) {
            return;
        }

        // Write document file
        try (Writer writer = new BufferedWriter(new FileWriter(output))) {
            // Override api group
            Map<String, String> groupDefineMappings = new LinkedHashMap<>();
            int index = 1;
            for (Api api : apis) {
                String group = api.getGroup();
                String define = groupDefineMappings.get(group);
                if (define == null) {
                    define = "Group" + index++;
                    groupDefineMappings.put(group, define);
                    writer.write("\n/**");
                    writer.write("\n * @apiDefine ".concat(define).concat(" ").concat(group));
                    writer.write("\n */\n");
                }
                api.setGroup(define);
            }

            // Build api document
            for (Api api : apis) {
                writer.write(this.api2document(api));
            }
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
