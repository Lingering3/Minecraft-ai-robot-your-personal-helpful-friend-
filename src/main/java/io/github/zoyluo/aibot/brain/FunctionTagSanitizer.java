package io.github.zoyluo.aibot.brain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模型伪 function-call 标签清洗器。
 * 模型偶尔不使用原生 function calling,而输出文本标签包裹的内容。
 * 这类标签不能直接进入聊天;本类提取 say 的 msg 内容,并兜底清除所有残留标签。
 */
public final class FunctionTagSanitizer {
    private static final Pattern SAY_PATTERN = Pattern.compile(buildSayRegex(), Pattern.DOTALL);

    private FunctionTagSanitizer() {
    }

    private static String buildSayRegex() {
        // 形如: function=say 标签内 parameter=msg 标签包裹的内容
        return "<" + "function=say>\\s*<" + "parameter=msg>(.*?)</" + "parameter>\\s*</" + "function>";
    }

    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw;
        Matcher matcher = SAY_PATTERN.matcher(text);
        StringBuilder extracted = new StringBuilder();
        while (matcher.find()) {
            extracted.append(matcher.group(1));
        }
        if (extracted.length() > 0) {
            text = extracted.toString();
        }
        text = stripTag(text, "function");
        text = stripTag(text, "parameter");
        return text.trim();
    }

    private static String stripTag(String input, String tagName) {
        String text = input.replaceAll("<" + tagName + "=[^>]*>", "");
        text = text.replace("<" + tagName + ">", "");
        return text.replace("</" + tagName + ">", "");
    }
}
