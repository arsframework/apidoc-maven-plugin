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
import java.io.FileWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.stream.Collectors;

import com.arsframework.apidoc.core.Api;
import com.arsframework.apidoc.core.ClassHelper;
import com.arsframework.apidoc.core.Configuration;
import com.arsframework.apidoc.core.ContextHelper;
import com.arsframework.apidoc.core.Parameter;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.springframework.util.CollectionUtils;

/**
 * Apidoc build mojo
 *
 * @author Woody
 */
@Mojo(name = "build", requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresDependencyCollection = ResolutionScope.COMPILE, threadSafe = true)
public class ApidocBuildMojo extends AbstractBuildMojo {
    /**
     * Output file of api document
     */
    @org.apache.maven.plugins.annotations.Parameter(
            defaultValue = "${project.basedir}/${project.name}.apidoc", required = true)
    private String output;

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
     * Convert the api to document
     *
     * @param api Api object
     * @return Api document string
     */
    private String api2document(Api api) {
        Objects.requireNonNull(api, "api not specified");
        StringBuilder document = new StringBuilder();
        Configuration configuration = ContextHelper.getConfiguration();
        document.append("\n/**");
        document.append("\n * @api {").append(String.join(" | ", api.getMethods())).append("} ")
                .append(api.getUrl()).append(" ").append(api.getName());
        if (!configuration.isEnableSampleRequest()) {
            document.append("\n * @apiSampleRequest off");
        }
        document.append("\n * @apiName ").append(api.getKey());
        document.append("\n * @apiGroup ").append(api.getGroup());
        document.append("\n * @apiHeader ").append(api.getHeader());
        if (!CollectionUtils.isEmpty(this.includeHeaders)) {
            this.includeHeaders.forEach(header -> document.append("\n * @apiHeader ").append(header));
        }
        if (api.getVersion() != null) {
            document.append("\n * @apiVersion ").append(api.getVersion());
        }
        StringBuilder description = new StringBuilder();
        if (api.getDescription() != null) {
            description.append(api.getDescription().replace("\n", "\n *\n *"));
        }
        if (api.getAuthors() != null && !api.getAuthors().isEmpty() && configuration.isDisplayAuthor()) {
            description.append("\n *\n * Author: ").append(String.join(", ", api.getAuthors()));
        }
        if (api.getDate() != null && configuration.isDisplayDate()) {
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
            parameters.forEach(p -> document.append(parameter2document("@apiParam", null, p, false)));
        }
        Parameter returned = api.getReturned();
        if (returned != null) {
            List<Parameter> fields = returned.getFields();
            if (returned.isMultiple() || fields == null || fields.isEmpty()) {
                document.append(parameter2document("@apiSuccess", null, returned, true));
            } else {
                fields.forEach(f -> document.append(parameter2document("@apiSuccess", null, f, true)));
            }
            if (configuration.isEnableResponseExample()) {
                document.append("\n * @apiSuccessExample Response");
                document.append("\n *\n * ").append(parameter2example(returned));
            }
        }
        document.append("\n */\n");
        return document.toString();
    }

    @Override
    protected void building(List<Api> apis) throws Exception {
        if (apis == null || apis.isEmpty()) {
            return;
        }
        this.getLog().info("Building apidoc: " + this.output);

        // Write document file
        try (Writer writer = new BufferedWriter(new FileWriter(this.output))) {
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
}
