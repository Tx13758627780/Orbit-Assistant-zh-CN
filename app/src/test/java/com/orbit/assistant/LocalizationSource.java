package com.orbit.assistant;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keep existing structural UI contracts independent of a literal's resource wrapper.
 * The resource audit separately verifies that each English fallback matches its resource.
 */
final class LocalizationSource {
    private static final Pattern RESOURCE_CALL = Pattern.compile(
            "OrbitText\\.get\\(R\\.string\\.\\w+,\\s*(\"(?:\\\\.|[^\"\\\\])*\"|[A-Z][A-Z0-9_]*)\\)");

    private LocalizationSource() {}

    static String withoutResourceCalls(String source) {
        Matcher matcher = RESOURCE_CALL.matcher(source);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(1)));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
