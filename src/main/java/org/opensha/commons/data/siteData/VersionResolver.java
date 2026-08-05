package org.opensha.commons.data.siteData;

/**
 * The VersionResolver interface is used to describe version tags on GitLab/GitHub.
 * Concrete implementations are enums mapping a name/description to that tag.
 * (e.g., see `CONUS_Versions`)
 */
public interface VersionResolver {
    String getDisplayName();
    String getTag();
}
