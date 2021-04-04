package com.arsframework.plugin.apidoc;

import java.util.Collections;
import java.util.Set;

import lombok.Builder;
import lombok.Getter;

/**
 * Api document configuration
 *
 * @author Woody
 */
@Getter
@Builder
public class Configuration {
    /**
     * Whether the date is displayed
     */
    private boolean displayDate;

    /**
     * Whether the author is displayed
     */
    private boolean displayAuthor;

    /**
     * Whether the sample request is enabled
     */
    private boolean enableSampleRequest;

    /**
     * Include group identities
     */
    @Builder.Default
    private Set<String> includeGroupIdentities = Collections.emptySet();
}
