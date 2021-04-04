package com.arsframework.plugin.apidoc;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Api model
 *
 * @author Woody
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Api {
    /**
     * Api key
     */
    private String key;

    /**
     * Api name
     */
    private String name;

    /**
     * Api group
     */
    private String group;

    /**
     * Api url
     */
    private String url;

    /**
     * Api date
     */
    private String date;

    /**
     * Api request header
     */
    private String header;

    /**
     * Api author
     */
    private String author;

    /**
     * Api version
     */
    private String version;

    /**
     * Api description
     */
    private String description;

    /**
     * Whether the deprecated is enabled
     */
    private boolean deprecated;

    /**
     * Api request methods
     */
    private List<String> methods;

    /**
     * Api request parameters
     */
    private List<Parameter> parameters;

    /**
     * Api return parameter
     */
    private Parameter returned;
}
