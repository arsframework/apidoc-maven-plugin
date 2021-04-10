package com.arsframework.plugin.apidoc;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

import org.springframework.core.io.InputStreamSource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Class helper
 *
 * @author Woody
 */
public final class ClassHelper {
    private ClassHelper() {
    }

    /**
     * Convert type to class
     *
     * @param type Type object
     * @return Class object
     */
    public static Class<?> type2class(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) type).getRawType();
        }
        return Object.class;
    }

    /**
     * Judge whether the class is basic
     *
     * @param clazz Class object
     * @return true/false
     */
    public static boolean isBasicClass(Class<?> clazz) {
        try {
            return clazz != null && ((Class<?>) clazz.getField("TYPE").get(null)).isPrimitive();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return clazz.isPrimitive() || clazz == Byte.class || clazz == Character.class || clazz == Integer.class
                    || clazz == Short.class || clazz == Long.class || clazz == Float.class || clazz == Double.class
                    || clazz == Boolean.class;
        }
    }

    /**
     * Judge whether the class is date type
     *
     * @param clazz Class object
     * @return true/false
     */
    public static boolean isDateClass(Class<?> clazz) {
        return clazz != null && (Date.class.isAssignableFrom(clazz) || LocalDate.class.isAssignableFrom(clazz)
                || LocalDateTime.class.isAssignableFrom(clazz));
    }

    /**
     * Judge whether the class is int type
     *
     * @param clazz Class object
     * @return true/false
     */
    public static boolean isIntClass(Class<?> clazz) {
        return clazz != null && (clazz == byte.class || clazz == Byte.class || clazz == short.class
                || clazz == Short.class || clazz == int.class || clazz == Integer.class || clazz == long.class
                || clazz == Long.class || BigInteger.class.isAssignableFrom(clazz));
    }

    /**
     * Judge whether the class is float type
     *
     * @param clazz Class object
     * @return true/false
     */
    public static boolean isFloatClass(Class<?> clazz) {
        return clazz != null && (clazz == float.class || clazz == Float.class || clazz == double.class
                || clazz == Double.class || BigDecimal.class.isAssignableFrom(clazz));
    }

    /**
     * Judge whether the class is stream type
     *
     * @param clazz Class object
     * @return true/false
     */
    public static boolean isStreamClass(Class<?> clazz) {
        return clazz != null && (File.class.isAssignableFrom(clazz) || MultipartFile.class.isAssignableFrom(clazz)
                || Reader.class.isAssignableFrom(clazz) || InputStream.class.isAssignableFrom(clazz)
                || InputStreamSource.class.isAssignableFrom(clazz) || Writer.class.isAssignableFrom(clazz)
                || OutputStream.class.isAssignableFrom(clazz));
    }

    /**
     * Judge whether the class is meta type
     *
     * @param clazz Class object
     * @return true/false
     */
    public static boolean isMetaClass(Class<?> clazz) {
        return isBasicClass(clazz) || clazz == Object.class || Map.class.isAssignableFrom(clazz)
                || Enum.class.isAssignableFrom(clazz) || Number.class.isAssignableFrom(clazz)
                || clazz == Locale.class || TimeZone.class.isAssignableFrom(clazz)
                || CharSequence.class.isAssignableFrom(clazz) || isDateClass(clazz) || File.class.isAssignableFrom(clazz)
                || MultipartFile.class.isAssignableFrom(clazz) || InputStream.class.isAssignableFrom(clazz)
                || InputStreamSource.class.isAssignableFrom(clazz) || Reader.class.isAssignableFrom(clazz)
                || OutputStream.class.isAssignableFrom(clazz) || Writer.class.isAssignableFrom(clazz);
    }

    /**
     * Lookup method from class with no parameters
     *
     * @param clazz Class object
     * @param name  Method name
     * @return Method object
     */
    public static Method lookupMethod(Class<?> clazz, String name) {
        Objects.requireNonNull(clazz, "clazz not specified");
        Objects.requireNonNull(name, "name not specified");
        do {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getParameterCount() == 0 && method.getName().equals(name)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        } while ((clazz = clazz.getSuperclass()) != Object.class);
        return null;
    }

    /**
     * Get actual type of collection
     *
     * @param type      Type object
     * @param variables Type variable and type mappings
     * @return Type object
     */
    public static Type getCollectionActualType(Type type, Map<TypeVariable<?>, Type> variables) {
        Objects.requireNonNull(type, "type not specified");
        if (type instanceof ParameterizedType) {
            Type[] arguments = ((ParameterizedType) type).getActualTypeArguments();
            if (arguments.length > 0) {
                Type argument = arguments[0];
                if (argument instanceof TypeVariable) {
                    return variables == null ? null : variables.get(argument);
                }
                return argument;
            }
        }
        return Object.class;
    }

    /**
     * Get variable and parameterized mappings
     *
     * @param type Type object
     * @return Type variable and type mappings
     */
    public static Map<TypeVariable<?>, Type> getVariableParameterizedMappings(Type type) {
        if (!(type instanceof ParameterizedType)) {
            return Collections.emptyMap();
        }
        Type[] types = ((ParameterizedType) type).getActualTypeArguments();
        TypeVariable<?>[] variables = ((Class<?>) ((ParameterizedType) type).getRawType()).getTypeParameters();
        Map<TypeVariable<?>, Type> mappings = new HashMap<>(types.length);
        for (int i = 0; i < variables.length; i++) {
            mappings.put(variables[i], types[i]);
        }
        return mappings;
    }
}
