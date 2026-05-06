package org.opensha.commons.data.siteData;

public enum CONUS_Versions implements VersionResolver {
    NSHM23("NSHM (2023)", "6.1.3"),
    NSHM18("NSHM (2018)", "5.2.4");

    CONUS_Versions(String displayName, String tag) {
       this.displayName = displayName;
       this.tag = tag;
    }
    private final String displayName;
    private final String tag;

    public String getDisplayName() {
        return displayName;
    }

    public String getTag() {
        return tag;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

