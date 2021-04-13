package com.arsframework.plugin.apidoc;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.MethodDoc;

/**
 * Method analyser
 *
 * @author Woody
 */
public class MethodAnalyser {
    /**
     * Api method object
     */
    protected final Method method;
    /**
     * Method document object
     */
    protected final MethodDoc document;
    /**
     * Class document information function
     */
    protected final Function<Class<?>, ClassDoc> information;
    /**
     * Api document configuration
     */
    protected final Configuration configuration;

    public MethodAnalyser(Method method, Function<Class<?>, ClassDoc> information, Configuration configuration) {
        Objects.requireNonNull(method, "method not specified");
        Objects.requireNonNull(information, "information not specified");
        Objects.requireNonNull(configuration, "configuration not specified");
        this.method = method;
        this.information = information;
        this.configuration = configuration;
        this.document = ApidocHelper.getDocument(information.apply(method.getDeclaringClass()), method);
    }

    /**
     * Get class document of method
     *
     * @return Class document object
     */
    protected ClassDoc getClassDocument() {
        return this.document == null ? null : this.document.containingClass();
    }

    /**
     * Get api key
     *
     * @return Api key
     */
    protected String getKey() {
        return ApidocHelper.getApiKey(this.method);
    }

    /**
     * Get api url
     *
     * @return Api url
     */
    protected String getUrl() {
        return ApidocHelper.getApiUrl(this.method);
    }

    /**
     * Get api name
     *
     * @return Api name
     */
    protected String getName() {
        String name = ApidocHelper.getCommentOutline(this.document);
        return name == null ? this.method.getName() : name;
    }

    /**
     * Get api group
     *
     * @return Api group
     */
    protected String getGroup() {
        String group = ApidocHelper.getCommentOutline(this.getClassDocument());
        return group == null ? this.method.getDeclaringClass().getSimpleName() : group;
    }

    /**
     * Get api header
     *
     * @return Api header
     */
    protected String getHeader() {
        return ApidocHelper.getApiHeader(this.method);
    }

    /**
     * Judge whether the api is deprecated
     *
     * @return true/false
     */
    protected boolean isDeprecated() {
        return ApidocHelper.isApiDeprecated(this.method);
    }

    /**
     * Get api description
     *
     * @return Api description
     */
    protected String getDescription() {
        return ApidocHelper.getCommentDescription(this.document);
    }

    /**
     * Get api date
     *
     * @return Api date
     */
    protected String getDate() {
        return ApidocHelper.getDateNote(this.document, this.getClassDocument());
    }

    /**
     * Get api version
     *
     * @return Api version
     */
    protected String getVersion() {
        return ApidocHelper.getVersionNote(this.document, this.getClassDocument());
    }

    /**
     * Get api authors
     *
     * @return Api authors
     */
    protected List<String> getAuthors() {
        return ApidocHelper.getAuthorNotes(this.document, this.getClassDocument());
    }

    /**
     * Get api request methods
     *
     * @return Api request methods
     */
    protected List<String> getMethods() {
        return ApidocHelper.getApiMethods(this.method);
    }

    /**
     * Get parameter analyser
     *
     * @return Parameter analyser
     */
    protected ParameterAnalyser getParameterAnalyser() {
        return new ParameterAnalyser(this.method, this.information, this.configuration);
    }

    /**
     * Parse the method to api
     *
     * @return Api object
     */
    public Api parse() {
        ParameterAnalyser parameterAnalyser = this.getParameterAnalyser();
        Objects.requireNonNull(parameterAnalyser, "ParameterAnalyser must not be null");
        return Api.builder().key(this.getKey()).url(this.getUrl()).name(this.getName()).group(this.getGroup())
                .header(this.getHeader()).description(this.getDescription()).deprecated(this.isDeprecated())
                .methods(this.getMethods()).date(this.getDate()).authors(this.getAuthors()).version(this.getVersion())
                .parameters(parameterAnalyser.getParameters()).returned(parameterAnalyser.getReturned()).build();
    }
}
