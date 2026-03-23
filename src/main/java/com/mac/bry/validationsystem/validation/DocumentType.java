package com.mac.bry.validationsystem.validation;

/**
 * Typy dokumentów generowanych w ramach walidacji.
 * Prefix jest używany w numeracji dokumentów (np. SW/LZTHLA/2026/001).
 */
public enum DocumentType {

    SCHEMAT_WIZUALNY("SW", "Schemat wizualny PDF"),
    RAPORT_WORD("RW", "Raport Word (.docx)"),
    ZIP_PACKAGE("ZP", "Paczka ZIP");

    private final String prefix;
    private final String displayName;

    DocumentType(String prefix, String displayName) {
        this.prefix = prefix;
        this.displayName = displayName;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getDisplayName() {
        return displayName;
    }
}
