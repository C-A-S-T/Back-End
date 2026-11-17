package com.example.cast.source.entity;

/** Only these three categories appear in the source library sidebar. */
public enum SourceFolderCategory {
    HOUSING("청약·주거"),
    GOV_SUPPORT("정부 지원정책"),
    EDUCATION("교육");

    private final String displayName;

    SourceFolderCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
