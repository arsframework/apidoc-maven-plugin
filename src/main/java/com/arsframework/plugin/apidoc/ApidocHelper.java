package com.arsframework.plugin.apidoc;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.sun.javadoc.Doc;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Apidoc helper
 *
 * @author Woody
 */
public final class ApidocHelper {
    /**
     * Enter definition name
     */
    private static final String ENTER_DEFINITION_NAME = "\n";

    /**
     * Date definition name
     */
    private static final String DATE_DEFINITION_NAME = "@date";

    /**
     * Author definition name
     */
    private static final String AUTHOR_DEFINITION_NAME = "@author";

    /**
     * Version definition name
     */
    private static final String VERSION_DEFINITION_NAME = "@version";

    /**
     * Return definition name
     */
    private static final String RETURN_DEFINITION_NAME = "@return";

    private ApidocHelper() {
    }

    /**
     * Whether the class is api class
     *
     * @param clazz Class object
     * @return true/false
     */
    public static boolean isApiClass(Class<?> clazz) {
        return clazz != null
                && (clazz.isAnnotationPresent(Controller.class) || clazz.isAnnotationPresent(RestController.class));
    }

    /**
     * Whether the method is api method
     *
     * @param method Method object
     * @return true/false
     */
    public static boolean isApiMethod(Method method) {
        return method != null && (method.isAnnotationPresent(RequestMapping.class)
                || method.isAnnotationPresent(PostMapping.class) || method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PutMapping.class) || method.isAnnotationPresent(DeleteMapping.class)
                || method.isAnnotationPresent(PatchMapping.class));
    }

    /**
     * Whether the method is deprecated
     *
     * @param method Method object
     * @return true/false
     */
    public static boolean isApiDeprecated(Method method) {
        return method == null ? false : method.isAnnotationPresent(Deprecated.class)
                || method.getDeclaringClass().isAnnotationPresent(Deprecated.class);
    }

    /**
     * Get active mapping for request url
     *
     * @param mappings Request url array
     * @return Request mapping url
     */
    private static String getActiveMapping(String[]... mappings) {
        if (mappings == null || mappings.length == 0) {
            return null;
        }
        for (String[] values : mappings) {
            if (values == null || values.length == 0) {
                continue;
            }
            for (String value : values) {
                if (value != null && !(value = value.trim()).isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * Get url mapping of class
     *
     * @param clazz Class object
     * @return URL mapping
     */
    private static String getClassMapping(Class<?> clazz) {
        String mapping;
        Annotation annotation;
        if ((annotation = clazz.getAnnotation(Controller.class)) != null
                && !(mapping = ((Controller) annotation).value().trim()).isEmpty()) {
            return mapping;
        }
        if ((annotation = clazz.getAnnotation(RestController.class)) != null
                && !(mapping = ((RestController) annotation).value().trim()).isEmpty()) {
            return mapping;
        }
        if ((annotation = clazz.getAnnotation(RequestMapping.class)) != null
                && (mapping = getActiveMapping(((RequestMapping) annotation).value(),
                ((RequestMapping) annotation).path())) != null) {
            return mapping;
        }
        return null;
    }

    /**
     * Get request mapping of method
     *
     * @param method Method object
     * @return Request mapping
     */
    private static String getMethodMapping(Method method) {
        String mapping;
        Annotation annotation;
        if ((annotation = method.getAnnotation(RequestMapping.class)) != null
                && (mapping = getActiveMapping(((RequestMapping) annotation).value(),
                ((RequestMapping) annotation).path())) != null) {
            return mapping;
        }
        if ((annotation = method.getAnnotation(PostMapping.class)) != null
                && (mapping = getActiveMapping(((PostMapping) annotation).value(),
                ((PostMapping) annotation).path())) != null) {
            return mapping;
        }
        if ((annotation = method.getAnnotation(GetMapping.class)) != null
                && (mapping = getActiveMapping(((GetMapping) annotation).value(),
                ((GetMapping) annotation).path())) != null) {
            return mapping;
        }
        if ((annotation = method.getAnnotation(PutMapping.class)) != null
                && (mapping = getActiveMapping(((PutMapping) annotation).value(),
                ((PutMapping) annotation).path())) != null) {
            return mapping;
        }
        if ((annotation = method.getAnnotation(DeleteMapping.class)) != null
                && (mapping = getActiveMapping(((DeleteMapping) annotation).value(),
                ((DeleteMapping) annotation).path())) != null) {
            return mapping;
        }
        if ((annotation = method.getAnnotation(PatchMapping.class)) != null
                && (mapping = getActiveMapping(((PatchMapping) annotation).value(),
                ((PatchMapping) annotation).path())) != null) {
            return mapping;
        }
        return null;
    }

    /**
     * Get api key of method
     *
     * @param method Method object
     * @return Api key
     */
    public static String getApiKey(Method method) {
        Objects.requireNonNull(method, "method not specified");
        return String.format("%s.%s", method.getDeclaringClass().getName(), method.getName());
    }

    /**
     * Get api url of method
     *
     * @param method Method object
     * @return Api url
     */
    public static String getApiUrl(Method method) {
        Objects.requireNonNull(method, "method not specified");
        StringBuilder api = new StringBuilder();
        String prefix = getClassMapping(method.getDeclaringClass());
        String suffix = getMethodMapping(method);
        if (prefix != null) {
            api.append("/").append(prefix);
        }
        if (suffix != null) {
            api.append("/").append(suffix);
        }
        return api.toString().replace("//", "/");
    }

    /**
     * Get api request method
     *
     * @param method Method object
     * @return Request method list
     */
    public static List<String> getApiMethods(Method method) {
        Objects.requireNonNull(method, "method not specified");
        Annotation annotation;
        List<RequestMethod> methods = new LinkedList<>();
        if ((annotation = method.getAnnotation(RequestMapping.class)) != null
                && getActiveMapping(((RequestMapping) annotation).value(),
                ((RequestMapping) annotation).path()) != null) {
            methods.addAll(Arrays.asList(((RequestMapping) annotation).method()));
        }
        if ((annotation = method.getAnnotation(PostMapping.class)) != null
                && getActiveMapping(((PostMapping) annotation).value(),
                ((PostMapping) annotation).path()) != null) {
            methods.add(RequestMethod.POST);
        }
        if ((annotation = method.getAnnotation(GetMapping.class)) != null
                && getActiveMapping(((GetMapping) annotation).value(),
                ((GetMapping) annotation).path()) != null) {
            methods.add(RequestMethod.GET);
        }
        if ((annotation = method.getAnnotation(PutMapping.class)) != null
                && getActiveMapping(((PutMapping) annotation).value(),
                ((PutMapping) annotation).path()) != null) {
            methods.add(RequestMethod.PUT);
        }
        if ((annotation = method.getAnnotation(DeleteMapping.class)) != null
                && getActiveMapping(((DeleteMapping) annotation).value(),
                ((DeleteMapping) annotation).path()) != null) {
            methods.add(RequestMethod.DELETE);
        }
        if ((annotation = method.getAnnotation(PatchMapping.class)) != null
                && getActiveMapping(((PatchMapping) annotation).value(),
                ((PatchMapping) annotation).path()) != null) {
            methods.add(RequestMethod.PATCH);
        }
        if (methods.isEmpty()) {
            methods.addAll(Arrays.asList(RequestMethod.values()));
        }
        return methods.stream().distinct().map(RequestMethod::name).collect(Collectors.toList());
    }

    /**
     * Get api request header
     *
     * @param method Method object
     * @return Request header
     */
    public static String getApiHeader(Method method) {
        Objects.requireNonNull(method, "method not specified");

        // @RequestBody
        for (Annotation[] annotations : method.getParameterAnnotations()) {
            for (Annotation annotation : annotations) {
                if (annotation instanceof RequestBody) {
                    return "{String} Content-Type application/json";
                }
            }
        }

        // File upload
        for (Class<?> type : method.getParameterTypes()) {
            if (!ClassHelper.isMetaClass(type)) {
                for (Field field : type.getDeclaredFields()) {
                    if (!field.isSynthetic() && MultipartFile.class.isAssignableFrom(field.getType())) {
                        return "{String} Content-Type multipart/form-data";
                    }
                }
            }
        }

        // Form
        return "{String} Content-Type application/x-www-form-urlencoded";
    }

    /**
     * Get comment lines
     *
     * @param document Document object
     * @return Comment lines
     */
    public static List<String> getCommentLines(Doc document) {
        if (document == null) {
            return new ArrayList<>(0);
        }
        String[] splits = document.commentText().split(ENTER_DEFINITION_NAME);
        List<String> lines = new ArrayList<>(splits.length);
        for (String line : splits) {
            if (!(line = line.trim()).isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * Get comment outline
     *
     * @param document Document object
     * @return Comment outline
     */
    public static String getCommentOutline(Doc document) {
        List<String> lines = getCommentLines(document);
        return lines.isEmpty() ? null : lines.get(0);
    }

    /**
     * Get comment description
     *
     * @param document Document object
     * @return Comment description
     */
    public static String getCommentDescription(Doc document) {
        List<String> lines = getCommentLines(document);
        return lines.size() < 2 ? null : String.join(ENTER_DEFINITION_NAME, lines.subList(1, lines.size()));
    }

    /**
     * Get annotation note
     *
     * @param name      Annotation name
     * @param documents Document object array
     * @return Annotation note
     */
    private static String getAnnotationNote(String name, Doc... documents) {
        Objects.requireNonNull(name, "name not specified");
        if (documents == null || documents.length == 0) {
            return null;
        }
        String note;
        for (Doc document : documents) {
            if (document == null) {
                continue;
            }
            for (String line : document.getRawCommentText().trim().split(ENTER_DEFINITION_NAME)) {
                if (!(line = line.trim()).isEmpty() && line.startsWith(name)
                        && !(note = line.substring(name.length()).trim()).isEmpty()) {
                    return note;
                }
            }
        }
        return null;
    }

    /**
     * Get date note from document
     *
     * @param documents Document object array
     * @return Date for string
     */
    public static String getDateNote(Doc... documents) {
        return getAnnotationNote(DATE_DEFINITION_NAME, documents);
    }

    /**
     * Get author note from document
     *
     * @param documents Document object array
     * @return Api author
     */
    public static String getAuthorNote(Doc... documents) {
        return getAnnotationNote(AUTHOR_DEFINITION_NAME, documents);
    }

    /**
     * Get version note from document
     *
     * @param documents Document object array
     * @return Api version
     */
    public static String getVersionNote(Doc... documents) {
        return getAnnotationNote(VERSION_DEFINITION_NAME, documents);
    }

    /**
     * Get parameter note
     *
     * @param name      Parameter name
     * @param documents Document object array
     * @return Parameter note
     */
    public static String getParameterNote(String name, Doc... documents) {
        Objects.requireNonNull(name, "name not specified");
        return getAnnotationNote(String.format("@param %s ", name), documents);
    }

    /**
     * Get return note from document
     *
     * @param documents Document object array
     * @return Api return
     */
    public static String getReturnNote(Doc... documents) {
        return getAnnotationNote(RETURN_DEFINITION_NAME, documents);
    }
}
