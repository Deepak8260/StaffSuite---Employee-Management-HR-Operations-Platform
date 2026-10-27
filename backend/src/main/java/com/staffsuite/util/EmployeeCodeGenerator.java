package com.staffsuite.util;

/** Builds sequential, zero-padded employee codes such as EMP-0001 from the row id. */
public final class EmployeeCodeGenerator {

    private static final String PREFIX = "EMP-";

    private EmployeeCodeGenerator() {
    }

    public static String fromSequence(long sequence) {
        return String.format("%s%04d", PREFIX, sequence);
    }
}
