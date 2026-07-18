package org.ual.utils.config;

public class CsvFormatConfig {
    private String separator = ",";
    private String decimalSymbol = ".";
    private boolean includeHeaders = true;
    private boolean writeGnuplotMetadata = false;

    public static CsvFormatConfig defaultConfig() {
        return new CsvFormatConfig();
    }

    public String getSeparator() {
        return separator;
    }

    public void setSeparator(String separator) {
        this.separator = separator;
    }

    public String getDecimalSymbol() {
        return decimalSymbol;
    }

    public void setDecimalSymbol(String decimalSymbol) {
        this.decimalSymbol = decimalSymbol;
    }

    public boolean isIncludeHeaders() {
        return includeHeaders;
    }

    public void setIncludeHeaders(boolean includeHeaders) {
        this.includeHeaders = includeHeaders;
    }

    public boolean isWriteGnuplotMetadata() {
        return writeGnuplotMetadata;
    }

    public void setWriteGnuplotMetadata(boolean writeGnuplotMetadata) {
        this.writeGnuplotMetadata = writeGnuplotMetadata;
    }

    public String normalizedSeparator() {
        if (separator == null || separator.trim().isEmpty()) {
            return ",";
        }
        return separator;
    }

    public char normalizedDecimalSymbol() {
        if (decimalSymbol == null || decimalSymbol.isEmpty()) {
            return '.';
        }
        char symbol = decimalSymbol.charAt(0);
        return symbol == ',' ? ',' : '.';
    }
}

