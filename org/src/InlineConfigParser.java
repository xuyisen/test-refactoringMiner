package com.puppycrawl.tools.checkstyle.bdd;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.puppycrawl.tools.checkstyle.api.CheckstyleException;

public final class InlineConfigParser {

    /** A pattern matching the symbol: "\\" or "/". */
    private static final Pattern SLASH_PATTERN = Pattern.compile("[\\/]");

    /** A pattern to find the string: "// violation". */
    private static final Pattern VIOLATION_PATTERN = Pattern
            .compile(".*//\\s*violation(?:\\W+'(.*)')?$");

    /** A pattern to find the string: "// violation above". */
    private static final Pattern VIOLATION_ABOVE_PATTERN = Pattern
            .compile(".*//\\s*violation above(?:\\W+'(.*)')?$");

    /** A pattern to find the string: "// violation below". */
    private static final Pattern VIOLATION_BELOW_PATTERN = Pattern
            .compile(".*//\\s*violation below(?:\\W+'(.*)')?$");

    /** A pattern to find the string: "// X violations". */
    private static final Pattern MULTIPLE_VIOLATIONS_PATTERN = Pattern
            .compile(".*//\\s*(\\d+) violations$");

    /** A pattern to find the string: "// X violations above". */
    private static final Pattern MULTIPLE_VIOLATIONS_ABOVE_PATTERN = Pattern
            .compile(".*//\\s*(\\d+) violations above$");

    /** A pattern to find the string: "// X violations below". */
    private static final Pattern MULTIPLE_VIOLATIONS_BELOW_PATTERN = Pattern
            .compile(".*//\\s*(\\d+) violations below$");

    /** The String "(null)". */
    private static final String NULL_STRING = "(null)";

    /** Stop instances being created. **/
    private InlineConfigParser() {
    }

    /**
     * Parses the input file provided.
     *
     * @param inputFilePath the input file path.
     * @throws Exception if unable to read file or file not formatted properly.
     */
    public static TestInputConfiguration parse(String inputFilePath) throws Exception {
        final Path filePath = Paths.get(inputFilePath);
        final List<String> lines = readLinesFromFile(filePath);
        final TestInputConfiguration.Builder inputConfigBuilder =
                new TestInputConfiguration.Builder();
        setCheckName(inputConfigBuilder, inputFilePath, lines);
        setCheckProperties(inputConfigBuilder, inputFilePath, lines);
        setViolations(inputConfigBuilder, lines);
        return inputConfigBuilder.build();
    }

    /**
     * Reads all lines from the specified file.
     *
     * @param filePath the path to the file to read.
     * @return a list of lines from the file.
     * @throws CheckstyleException if unable to read the file.
     */
    private static List<String> readLinesFromFile(Path filePath) throws CheckstyleException {
        try {
            return Files.readAllLines(filePath);
        }
        catch (IOException ex) {
            throw new CheckstyleException("Failed to read lines from file: " + filePath, ex);
        }
    }

    private static String getFullyQualifiedClassName(String filePath, String checkName) {
        final String path = SLASH_PATTERN.matcher(filePath).replaceAll("\\.");
        final int beginIndex = path.indexOf("com.puppycrawl");
        final int endIndex = path.lastIndexOf(checkName.toLowerCase(Locale.ROOT));
        return path.substring(beginIndex, endIndex) + checkName + "Check";
    }

    private static String getFilePath(String fileName, String inputFilePath) {
        final int lastSlashIndex = Math.max(inputFilePath.lastIndexOf('\\'),
                inputFilePath.lastIndexOf('/'));
        final String root = inputFilePath.substring(0, lastSlashIndex + 1);
        return root + fileName;
    }

    private static String getResourcePath(String fileName, String inputFilePath) {
        final String filePath = getUriPath(fileName, inputFilePath);
        final int lastSlashIndex = filePath.lastIndexOf('/');
        final String root = filePath.substring(filePath.indexOf("puppycrawl") - 5,
                lastSlashIndex + 1);
        return root + fileName;
    }

    private static String getUriPath(String fileName, String inputFilePath) {
        return new File(getFilePath(fileName, inputFilePath)).toURI().toString();
    }

    private static String getResolvedPath(String fileValue, String inputFilePath) {
        final String resolvedFilePath;
        if (fileValue.startsWith("(resource)")) {
            resolvedFilePath =
                    getResourcePath(fileValue.substring(fileValue.indexOf(')') + 1),
                            inputFilePath);
        }
        else if (fileValue.startsWith("(uri)")) {
            resolvedFilePath =
                    getUriPath(fileValue.substring(fileValue.indexOf(')') + 1), inputFilePath);
        }
        else {
            resolvedFilePath = getFilePath(fileValue, inputFilePath);
        }
        return resolvedFilePath;
    }

    private static void setCheckName(TestInputConfiguration.Builder inputConfigBuilder,
                                     String filePath, List<String> lines)
            throws CheckstyleException {
        if (lines.size() < 2) {
            throw new CheckstyleException("Config not specified in " + filePath);
        }
        final String checkName = lines.get(1);
        final String fullyQualifiedClassName;
        if (checkName.startsWith("com.")) {
            fullyQualifiedClassName = checkName + "Check";
        }
        else {
            fullyQualifiedClassName = getFullyQualifiedClassName(filePath, checkName);
        }
        inputConfigBuilder.setCheckName(fullyQualifiedClassName);
    }

    private static void setCheckProperties(TestInputConfiguration.Builder inputConfigBuilder,
                                           String inputFilePath,
                                           List<String> lines)
            throws Exception {
        final StringBuilder stringBuilder = new StringBuilder(128);
        int lineNo = 2;
        for (String line = lines.get(lineNo); !line.contains("*/");
             ++lineNo, line = lines.get(lineNo)) {
            stringBuilder.append(line).append('\n');
        }
        final Properties properties = new Properties();
        try (Reader reader = new StringReader(stringBuilder.toString())) {
            properties.load(reader);
        }
        for (final Map.Entry<Object, Object> entry : properties.entrySet()) {
            final String key = entry.getKey().toString();
            final String value = entry.getValue().toString();
            if (key.startsWith("message.")) {
                inputConfigBuilder.addCheckMessage(key.substring(8), value);
            }
            else if (value.startsWith("(file)")) {
                final String fileName = value.substring(value.indexOf(')') + 1);
                final String filePath = getResolvedPath(fileName, inputFilePath);
                inputConfigBuilder.addNonDefaultProperty(key, filePath);
            }
            else if (value.startsWith("(default)")) {
                final String defaultValue = value.substring(value.indexOf(')') + 1);
                if (NULL_STRING.equals(defaultValue)) {
                    inputConfigBuilder.addDefaultProperty(key, null);
                }
                else {
                    inputConfigBuilder.addDefaultProperty(key, defaultValue);
                }
            }
            else {
                if (NULL_STRING.equals(value)) {
                    inputConfigBuilder.addNonDefaultProperty(key, null);
                }
                else {
                    inputConfigBuilder.addNonDefaultProperty(key, value);
                }
            }
        }
    }

    private static void setViolations(TestInputConfiguration.Builder inputConfigBuilder,
                                      List<String> lines) {
        for (int lineNo = 0; lineNo < lines.size(); lineNo++) {
            final Matcher violationMatcher =
                    VIOLATION_PATTERN.matcher(lines.get(lineNo));
            final Matcher violationAboveMatcher =
                    VIOLATION_ABOVE_PATTERN.matcher(lines.get(lineNo));
            final Matcher violationBelowMatcher =
                    VIOLATION_BELOW_PATTERN.matcher(lines.get(lineNo));
            final Matcher multipleViolationsMatcher =
                    MULTIPLE_VIOLATIONS_PATTERN.matcher(lines.get(lineNo));
            final Matcher multipleViolationsAboveMatcher =
                    MULTIPLE_VIOLATIONS_ABOVE_PATTERN.matcher(lines.get(lineNo));
            final Matcher multipleViolationsBelowMatcher =
                    MULTIPLE_VIOLATIONS_BELOW_PATTERN.matcher(lines.get(lineNo));
            if (violationMatcher.matches()) {
                inputConfigBuilder.addViolation(lineNo + 1, violationMatcher.group(1));
            }
            else if (violationAboveMatcher.matches()) {
                inputConfigBuilder.addViolation(lineNo, violationAboveMatcher.group(1));
            }
            else if (violationBelowMatcher.matches()) {
                inputConfigBuilder.addViolation(lineNo + 2, violationBelowMatcher.group(1));
            }
            else if (multipleViolationsMatcher.matches()) {
                Collections
                        .nCopies(Integer.parseInt(multipleViolationsMatcher.group(1)), lineNo + 1)
                        .forEach(actualLineNumber -> {
                            inputConfigBuilder.addViolation(actualLineNumber, null);
                        });
            }
            else if (multipleViolationsAboveMatcher.matches()) {
                Collections
                        .nCopies(Integer.parseInt(multipleViolationsAboveMatcher.group(1)), lineNo)
                        .forEach(actualLineNumber -> {
                            inputConfigBuilder.addViolation(actualLineNumber, null);
                        });
            }
            else if (multipleViolationsBelowMatcher.matches()) {
                Collections
                        .nCopies(Integer.parseInt(multipleViolationsBelowMatcher.group(1)),
                                lineNo + 2)
                        .forEach(actualLineNumber -> {
                            inputConfigBuilder.addViolation(actualLineNumber, null);
                        });
            }
        }
    }
}