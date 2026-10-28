package com.staffsuite.util;

import java.nio.charset.StandardCharsets;

/** Minimal CSV writer: RFC 4180 quoting, CRLF line ends, a UTF-8 BOM so Excel reads non-English text correctly. */
public final class Csv {

    private static final String BOM = "﻿";

    private final StringBuilder out = new StringBuilder(BOM);

    public Csv row(Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(cell(cells[i]));
        }
        out.append("\r\n");
        return this;
    }

    public byte[] bytes() {
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Quotes when needed, and defuses spreadsheet formulas: a text cell that starts with = + - @ (or a tab /
     * carriage return) would be executed by Excel, so it is prefixed with an apostrophe. Numbers are written as is.
     */
    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        String text = value.toString();
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
