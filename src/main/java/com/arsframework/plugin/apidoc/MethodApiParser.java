package com.arsframework.plugin.apidoc;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import com.arsframework.spring.web.utils.param.Rename;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MethodDoc;
import org.springframework.core.io.InputStreamSource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.multipart.MultipartFile;

/**
 * Method api parser
 *
 * @author Woody
 */
public class MethodApiParser {
    /**
     * Api method object
     */
    private final Method method;

    /**
     * Class document information function
     */
    private final Function<Class<?>, ClassDoc> information;

    /**
     * Api document configuration
     */
    private final Configuration configuration;

    private MethodApiParser(Method method, Function<Class<?>, ClassDoc> information, Configuration configuration) {
        Objects.requireNonNull(method, "method not specified");
        Objects.requireNonNull(information, "information not specified");
        Objects.requireNonNull(configuration, "configuration not specified");
        this.method = method;
        this.information = information;
        this.configuration = configuration;
    }

    /**
     * Judge whether the class is reiterated
     *
     * @param stack Class stack
     * @param clazz Target class object
     * @return true/false
     */
    private static boolean isRecursion(LinkedList<Class<?>> stack, Class<?> clazz) {
        if (stack != null && clazz != null) {
            int count = 0;
            for (Class<?> c : stack) {
                if (c == clazz && ++count > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Iteration the active class field
     *
     * @param clazz    Class object
     * @param consumer Field consumer
     */
    private static void iterationActiveClassField(Class<?> clazz, Consumer<Field> consumer) {
        Objects.requireNonNull(clazz, "clazz not specified");
        Objects.requireNonNull(consumer, "consumer not specified");
        for (Field field : clazz.getDeclaredFields()) {
            if (!field.isSynthetic() && !Modifier.isStatic(field.getModifiers())
                    && !field.isAnnotationPresent(JsonIgnore.class)) {
                consumer.accept(field);
            }
        }
    }

    /**
     * Get parameters with class fields
     *
     * @param clazz    Class object
     * @param consumer Field consumer
     * @return Parameter list
     */
    private static List<Parameter> class2parameters(Class<?> clazz, Function<Field, Parameter> consumer) {
        Objects.requireNonNull(clazz, "clazz not specified");
        Objects.requireNonNull(consumer, "consumer not specified");
        Class<?> original = clazz;
        List<Parameter> parameters = new LinkedList<>();

        // Load current and parent class fields
        do {
            iterationActiveClassField(clazz, field -> parameters.add(consumer.apply(field)));
        } while ((clazz = clazz.getSuperclass()) != null && !ClassHelper.isMetaClass(clazz)
                && !Collection.class.isAssignableFrom(clazz));

        // Load subclass field by @JsonTypeInfo
        JsonTypeInfo jsonType = original.getAnnotation(JsonTypeInfo.class);
        if (jsonType != null && jsonType.use() == JsonTypeInfo.Id.NAME && !jsonType.property().isEmpty()) {
            for (JsonSubTypes.Type type : original.getAnnotation(JsonSubTypes.class).value()) {
                iterationActiveClassField(type.value(), field -> parameters.add(consumer.apply(field)));
            }
        }
        return parameters;
    }

    /**
     * Parse the method to api
     *
     * @param method        Method object
     * @param information   Class document information function
     * @param configuration Api document configuration
     * @return Api object
     */
    public static Api parse(Method method, Function<Class<?>, ClassDoc> information, Configuration configuration) {
        return new MethodApiParser(method, information, configuration).execute();
    }

    /**
     * Get parameter type by class
     *
     * @param clazz Class object
     * @return Parameter type class
     */
    private Class<?> getType(Class<?> clazz) {
        Objects.requireNonNull(clazz, "clazz not specified");
        if (clazz == byte.class || clazz == Byte.class) {
            return Byte.class;
        } else if (clazz == char.class || clazz == Character.class) {
            return Character.class;
        } else if (clazz == int.class || clazz == Integer.class) {
            return Integer.class;
        } else if (clazz == short.class || clazz == Short.class) {
            return Short.class;
        } else if (clazz == long.class || clazz == Long.class || BigInteger.class.isAssignableFrom(clazz)) {
            return Long.class;
        } else if (clazz == float.class || clazz == Float.class) {
            return Float.class;
        } else if (clazz == double.class || clazz == Double.class || BigDecimal.class.isAssignableFrom(clazz)) {
            return Double.class;
        } else if (clazz == boolean.class || clazz == Boolean.class) {
            return Boolean.class;
        } else if (clazz == Locale.class || TimeZone.class.isAssignableFrom(clazz) ||
                Enum.class.isAssignableFrom(clazz) || CharSequence.class.isAssignableFrom(clazz)) {
            return String.class;
        } else if (ClassHelper.isDateClass(clazz)) {
            return Date.class;
        } else if (File.class.isAssignableFrom(clazz) || MultipartFile.class.isAssignableFrom(clazz)) {
            return File.class;
        } else if (Reader.class.isAssignableFrom(clazz) || InputStream.class.isAssignableFrom(clazz)
                || InputStreamSource.class.isAssignableFrom(clazz)) {
            return Reader.class;
        } else if (Writer.class.isAssignableFrom(clazz) || OutputStream.class.isAssignableFrom(clazz)) {
            return Writer.class;
        }
        return Object.class;
    }

    /**
     * Judge whether the element is required
     *
     * @param element Annotated element
     * @return true/false
     */
    private boolean isRequired(AnnotatedElement element) {
        return element != null && (element.isAnnotationPresent(NotNull.class)
                || element.isAnnotationPresent(NotBlank.class) || element.isAnnotationPresent(NotEmpty.class)
                || (element.isAnnotationPresent(Size.class) && element.getAnnotation(Size.class).min() > 0));
    }

    /**
     * Judge whether the parameter is required
     *
     * @param parameter Parameter object
     * @return true/false
     */
    private boolean isRequired(java.lang.reflect.Parameter parameter) {
        RequestParam annotation;
        return parameter != null && (this.isRequired((AnnotatedElement) parameter) ||
                ((annotation = parameter.getAnnotation(RequestParam.class)) != null && annotation.required()));
    }

    /**
     * Judge whether the element is deprecated
     *
     * @param element Annotated element
     * @return true/false
     */
    private boolean isDeprecated(AnnotatedElement element) {
        return element != null && element.isAnnotationPresent(Deprecated.class);
    }

    /**
     * Get field naming strategy
     *
     * @param field Filed object
     * @return Property naming strategy
     */
    private PropertyNamingStrategy getPropertyNamingStrategy(Field field) {
        Objects.requireNonNull(field, "field not specified");

        JsonNaming naming = field.getAnnotation(JsonNaming.class);
        if (naming == null) {
            return this.configuration.isEnableSnakeUnderlineConversion() ? PropertyNamingStrategy.SNAKE_CASE : null;
        }
        Class<? extends PropertyNamingStrategy> clazz = naming.value();
        if (clazz == PropertyNamingStrategy.SnakeCaseStrategy.class) {
            return PropertyNamingStrategy.SNAKE_CASE;
        } else if (clazz == PropertyNamingStrategy.UpperCamelCaseStrategy.class) {
            return PropertyNamingStrategy.UPPER_CAMEL_CASE;
        } else if (clazz == PropertyNamingStrategy.LowerCaseStrategy.class) {
            return PropertyNamingStrategy.LOWER_CASE;
        } else if (clazz == PropertyNamingStrategy.KebabCaseStrategy.class) {
            return PropertyNamingStrategy.KEBAB_CASE;
        }
        return PropertyNamingStrategy.LOWER_CAMEL_CASE;
    }

    /**
     * Get field name
     *
     * @param field Field object
     * @return Field name
     */
    private String getName(Field field) {
        Objects.requireNonNull(field, "field not specified");

        // Form parameter conversion
        Rename rename = field.getAnnotation(Rename.class);
        String name = rename == null ? null : rename.value().trim();
        if (name != null && !name.isEmpty()) {
            return name;
        }

        // Json parameter conversion
        JsonProperty property = field.getAnnotation(JsonProperty.class);
        if (property != null && !(name = property.value().trim()).isEmpty()) {
            return name;
        }

        // Property strategy conversion
        name = field.getName();
        PropertyNamingStrategy strategy = this.getPropertyNamingStrategy(field);
        if (strategy instanceof PropertyNamingStrategy.PropertyNamingStrategyBase) {
            return ((PropertyNamingStrategy.PropertyNamingStrategyBase) strategy).translate(name);
        }
        return name;
    }

    /**
     * Get parameter name
     *
     * @param parameter Parameter object
     * @return Parameter name
     */
    private String getName(java.lang.reflect.Parameter parameter) {
        Objects.requireNonNull(parameter, "parameter not specified");
        RequestParam annotation = parameter.getAnnotation(RequestParam.class);
        String name = annotation == null ? null :
                (name = annotation.value().trim()).isEmpty() ? annotation.name().trim() : name;
        return name == null || name.isEmpty() ? parameter.getName() : name;
    }

    /**
     * Get parameter size
     *
     * @param element Annotated element
     * @return Parameter size object
     */
    private Parameter.Size getSize(AnnotatedElement element) {
        Objects.requireNonNull(element, "element not specified");
        Size size = element.getAnnotation(Size.class);
        if (size != null) {
            return Parameter.Size.builder().min((double) size.min()).max((double) size.max()).build();
        }

        Min min = element.getAnnotation(Min.class);
        Max max = element.getAnnotation(Max.class);
        if (min != null && max != null) {
            return Parameter.Size.builder().min((double) min.value()).max((double) max.value()).build();
        } else if (min != null) {
            return Parameter.Size.builder().min((double) min.value()).build();
        } else if (max != null) {
            return Parameter.Size.builder().max((double) max.value()).build();
        }

        DecimalMin decimalMin = element.getAnnotation(DecimalMin.class);
        DecimalMax decimalMax = element.getAnnotation(DecimalMax.class);
        if (decimalMin != null && decimalMax != null) {
            return Parameter.Size.builder().min(Double.parseDouble(decimalMin.value()))
                    .max(Double.parseDouble(decimalMax.value())).build();
        } else if (decimalMin != null) {
            return Parameter.Size.builder().min(Double.parseDouble(decimalMin.value())).build();
        } else if (decimalMax != null) {
            return Parameter.Size.builder().max(Double.parseDouble(decimalMax.value())).build();
        }
        return null;
    }

    /**
     * Get parameter format
     *
     * @param element Annotated element
     * @return Parameter format
     */
    private String getFormat(AnnotatedElement element) {
        Objects.requireNonNull(element, "element not specified");
        String format;
        Annotation annotation;
        if ((annotation = element.getAnnotation(DateTimeFormat.class)) != null
                && !(format = ((DateTimeFormat) annotation).pattern()).isEmpty()) {
            return format;
        }
        if ((annotation = element.getAnnotation(JsonFormat.class)) != null
                && !(format = ((JsonFormat) annotation).pattern()).isEmpty()) {
            return format;
        }
        if ((annotation = element.getAnnotation(Pattern.class)) != null
                && !(format = ((Pattern) annotation).regexp()).isEmpty()) {
            return format;
        }
        return null;
    }

    /**
     * Get parameter default value
     *
     * @param field Field object
     * @return Parameter default value
     */
    private Object getDefaultValue(Field field) {
        Objects.requireNonNull(field, "field not specified");
        try {
            Object instance = null;
            Class<?> clazz = field.getDeclaringClass();

            // Build instance by lombok
            Method method = ClassHelper.lookupMethod(clazz, "builder");
            if (method != null) {
                Object builder = method.invoke(clazz);
                if (builder != null && (method = ClassHelper.lookupMethod(builder.getClass(), "build")) != null) {
                    instance = method.invoke(builder);
                }
            }

            // Build instance by constructor
            if (instance == null || !clazz.isAssignableFrom(instance.getClass())) {
                for (Constructor<?> constructor : clazz.getConstructors()) {
                    if (constructor.getParameterCount() == 0) {
                        constructor.setAccessible(true);
                        instance = constructor.newInstance();
                    }
                }
            }

            // Get default value of field
            if (instance != null && clazz.isAssignableFrom(instance.getClass())) {
                field.setAccessible(true);
                Object defaultValue = field.get(instance);
                if (defaultValue != null
                        && (!(defaultValue instanceof CharSequence) || ((CharSequence) defaultValue).length() > 0)) {
                    return defaultValue;
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Get parameter default value
     *
     * @param parameter Parameter object
     * @return Parameter default value
     */
    private Object getDefaultValue(java.lang.reflect.Parameter parameter) {
        Objects.requireNonNull(parameter, "parameter not specified");
        String defaultValue;
        RequestParam annotation = parameter.getAnnotation(RequestParam.class);
        if (annotation != null && !(defaultValue = annotation.defaultValue()).isEmpty()
                && !defaultValue.equals(ValueConstants.DEFAULT_NONE)) {
            return defaultValue;
        }
        return null;
    }

    /**
     * Get class document
     *
     * @param clazz Class object
     * @return Class document
     */
    private ClassDoc getDocument(Class<?> clazz) {
        Objects.requireNonNull(clazz, "clazz not specified");
        return this.information.apply(clazz);
    }

    /**
     * Get field document
     *
     * @param field Field object
     * @return Field document
     */
    private FieldDoc getDocument(Field field) {
        Objects.requireNonNull(field, "field not specified");
        ClassDoc classDocument = this.getDocument(field.getDeclaringClass());
        if (classDocument != null) {
            for (FieldDoc fieldDocument : classDocument.fields(false)) {
                if (fieldDocument.name().equals(field.getName())) {
                    return fieldDocument;
                }
            }
        }
        return null;
    }

    /**
     * Get document of method
     *
     * @param method Method object
     * @return Method document object
     */
    private MethodDoc getDocument(Method method) {
        Objects.requireNonNull(method, "method not specified");
        ClassDoc classDocument = this.getDocument(method.getDeclaringClass());
        if (classDocument != null) {
            for (MethodDoc methodDocument : classDocument.methods(false)) {
                if (methodDocument.name().equals(method.getName())) {
                    return methodDocument;
                }
            }
        }
        return null;
    }

    /**
     * Get field description
     *
     * @param field Field object
     * @return Field description
     */
    private String getDescription(Field field) {
        FieldDoc document = this.getDocument(field);
        String comment = document == null ? null : document.commentText();
        return comment == null || (comment = comment.trim()).isEmpty() ? null : comment;
    }

    /**
     * Get parameter description
     *
     * @param parameter Parameter object
     * @return Parameter description
     */
    private String getDescription(java.lang.reflect.Parameter parameter) {
        MethodDoc document = this.getDocument(this.method);
        return ApidocHelper.getParameterNote(parameter.getName(), document);
    }

    /**
     * Get parameter options with class
     *
     * @param clazz Class object
     * @return Parameter option list
     */
    private List<Parameter.Option> getOptions(Class<?> clazz) {
        Objects.requireNonNull(clazz, "clazz not specified");
        List<Parameter.Option> options = new LinkedList<>();
        if (Enum.class.isAssignableFrom(clazz)) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isSynthetic() || !field.isEnumConstant()) {
                    continue;
                }
                options.add(Parameter.Option.builder().key(field.getName()).value(this.getDescription(field))
                        .deprecated(field.isAnnotationPresent(Deprecated.class)).build());
            }
        }
        return options;
    }

    /**
     * Judge whether the parameter is be used for request
     *
     * @param parameter Parameter object
     * @param type      Parameter type
     * @return true/false
     */
    private boolean isRequestParameter(java.lang.reflect.Parameter parameter, Class<?> type) {
        if (parameter == null || type == null || parameter.isAnnotationPresent(SessionAttribute.class)) {
            return false;
        } else if (ClassHelper.isMetaClass(type)) {
            return true;
        }
        Package pkg = type.getPackage();
        Set<String> includeGroupIdentities = this.configuration.getIncludeGroupIdentities();
        return pkg != null && includeGroupIdentities != null && !includeGroupIdentities.isEmpty()
                && includeGroupIdentities.stream().anyMatch(pkg.getName()::startsWith);
    }

    /**
     * Convert field to parameter
     *
     * @param field     Field object
     * @param variables Type variable and type mappings
     * @param stack     Class stack
     * @return Parameter object
     */
    private Parameter field2parameter(Field field, Map<TypeVariable<?>, Type> variables, LinkedList<Class<?>> stack) {
        Objects.requireNonNull(field, "field not specified");
        Type type = field.getGenericType();
        if (type instanceof TypeVariable && variables != null && variables.containsKey(type)) {
            type = variables.get(type);
        }
        Class<?> clazz = ClassHelper.type2class(type), target = clazz;
        if (clazz.isArray()) {
            target = clazz.getComponentType();
        } else if (Collection.class.isAssignableFrom(clazz)) {
            target = ClassHelper.type2class(type = ClassHelper.getCollectionActualType(type, variables));
        }
        String example = ApidocHelper.getExampleNote(this.getDocument(field));
        boolean multiple = clazz.isArray() || Collection.class.isAssignableFrom(clazz);
        Parameter parameter = Parameter.builder().type(this.getType(target)).original(target).name(this.getName(field))
                .size(this.getSize(field)).format(this.getFormat(field)).required(this.isRequired(field))
                .multiple(multiple).example(example).deprecated(this.isDeprecated(field))
                .defaultValue(this.getDefaultValue(field)).description(this.getDescription(field))
                .options(this.getOptions(target)).build();
        if (!ClassHelper.isMetaClass(target) && !isRecursion(stack, target)) {
            stack.addLast(target);
            Map<TypeVariable<?>, Type> finalVariables = ClassHelper.getVariableParameterizedMappings(type);
            parameter.setFields(class2parameters(target, f -> this.field2parameter(f, finalVariables, stack)));
            stack.removeLast();
        }
        return parameter;
    }

    /**
     * Get request parameters of current method
     *
     * @return Parameter list
     */
    private List<Parameter> getParameters() {
        List<Parameter> parameters = new LinkedList<>();
        for (java.lang.reflect.Parameter parameter : this.method.getParameters()) {
            Type type = parameter.getParameterizedType();
            Class<?> clazz = ClassHelper.type2class(type), target = clazz;
            if (clazz.isArray()) {
                target = clazz.getComponentType();
            } else if (Collection.class.isAssignableFrom(clazz)) {
                Map<TypeVariable<?>, Type> variables = ClassHelper.getVariableParameterizedMappings(type);
                target = ClassHelper.type2class(type = ClassHelper.getCollectionActualType(type, variables));
            }
            if (!this.isRequestParameter(parameter, target)) {
                continue;
            }
            if (ClassHelper.isMetaClass(target)) {
                boolean multiple = clazz.isArray() || Collection.class.isAssignableFrom(clazz);
                parameters.add(Parameter.builder().type(this.getType(target)).original(target)
                        .name(this.getName(parameter)).size(this.getSize(parameter)).format(this.getFormat(parameter))
                        .required(this.isRequired(parameter)).multiple(multiple)
                        .deprecated(this.isDeprecated(parameter)).defaultValue(this.getDefaultValue(parameter))
                        .description(this.getDescription(parameter)).options(this.getOptions(target)).build());
            } else {
                LinkedList<Class<?>> stack = new LinkedList<>();
                Map<TypeVariable<?>, Type> variables = ClassHelper.getVariableParameterizedMappings(type);
                parameters.addAll(class2parameters(target, f -> this.field2parameter(f, variables, stack)));
            }
        }
        return parameters;
    }

    /**
     * Get return parameter of current method
     *
     * @return Parameter object
     */
    private Parameter getReturned() {
        Type type = this.method.getGenericReturnType();
        if (type == void.class) {
            return null;
        }
        Class<?> clazz = ClassHelper.type2class(type), target = clazz;
        if (clazz.isArray()) {
            target = clazz.getComponentType();
        } else if (Collection.class.isAssignableFrom(clazz)) {
            Map<TypeVariable<?>, Type> variables = ClassHelper.getVariableParameterizedMappings(type);
            target = ClassHelper.type2class(type = ClassHelper.getCollectionActualType(type, variables));
        }
        String example = ApidocHelper.getExampleNote(this.getDocument(this.method));
        boolean multiple = clazz.isArray() || Collection.class.isAssignableFrom(clazz);
        Parameter parameter =
                Parameter.builder().type(this.getType(target)).original(target).multiple(multiple).name("/")
                        .example(example).description(ApidocHelper.getReturnNote(this.getDocument(this.method)))
                        .options(this.getOptions(target)).build();
        if (!ClassHelper.isMetaClass(target)) {
            LinkedList<Class<?>> stack = new LinkedList<>();
            stack.addLast(target);
            Map<TypeVariable<?>, Type> variables = ClassHelper.getVariableParameterizedMappings(type);
            parameter.setFields(class2parameters(target, f -> this.field2parameter(f, variables, stack)));
        }
        return parameter;
    }

    /**
     * Execution api analysis
     *
     * @return Api object
     */
    private Api execute() {
        Class<?> clazz = this.method.getDeclaringClass();
        ClassDoc classDocument = this.getDocument(clazz);
        MethodDoc methodDocument = this.getDocument(this.method);
        String group = ApidocHelper.getCommentOutline(classDocument);
        String name = ApidocHelper.getCommentOutline(methodDocument);
        return Api.builder()
                .key(ApidocHelper.getApiKey(this.method))
                .url(ApidocHelper.getApiUrl(this.method))
                .name(name == null ? this.method.getName() : name)
                .group(group == null ? clazz.getSimpleName() : group)
                .header(ApidocHelper.getApiHeader(this.method))
                .description(ApidocHelper.getCommentDescription(methodDocument))
                .deprecated(ApidocHelper.isApiDeprecated(this.method))
                .methods(ApidocHelper.getApiMethods(this.method))
                .date(ApidocHelper.getDateNote(methodDocument, classDocument))
                .author(ApidocHelper.getAuthorNote(methodDocument, classDocument))
                .version(ApidocHelper.getVersionNote(methodDocument, classDocument))
                .parameters(this.getParameters()).returned(this.getReturned())
                .build();
    }
}
