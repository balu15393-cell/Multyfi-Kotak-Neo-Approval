package com.example.multyfikotakneo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SignalParser {
    private SignalParser() {}

    private static final String NUM = "([0-9][0-9,]*(?:\\.[0-9]+)?)";
    private static final Set<String> SYMBOL_BLACKLIST = new HashSet<>(Arrays.asList(
            "BUY", "SELL", "INTRADAY", "MIS", "NSE", "BSE", "ENTRY", "TARGET",
            "STOP", "LOSS", "SL", "AT", "ABOVE", "BELOW", "CMP", "CALL", "TRADE"
    ));

    public static Signal parse(String title, String body, boolean strictIntraday) {
        String combined = normalize((title == null ? "" : title) + " " + (body == null ? "" : body));
        String lower = combined.toLowerCase(Locale.ROOT);
        String upper = combined.toUpperCase(Locale.ROOT);

        Matcher sideMatcher = Pattern.compile("\\b(BUY|SELL)\\b", Pattern.CASE_INSENSITIVE).matcher(combined);
        if (!sideMatcher.find()) throw new IllegalArgumentException("BUY/SELL was not found in the Multyfi alert.");
        String side = sideMatcher.group(1).toUpperCase(Locale.ROOT);

        boolean intraday = containsAny(lower, "intraday", "intra day", "day trade", "daytrade", "mis");
        if (containsAny(lower, "positional", "swing", "delivery", "long term", "long-term", "investment")) {
            throw new IllegalArgumentException("Alert looks positional/swing/delivery, so it was blocked.");
        }
        if (strictIntraday && !intraday) {
            throw new IllegalArgumentException("Strict intraday mode: intraday/MIS wording was not detected.");
        }

        String symbol = extractSymbol(upper, side);
        if (symbol == null) throw new IllegalArgumentException("Stock ticker/symbol could not be identified reliably.");

        double[] range = extractEntry(combined, side, symbol);
        if (range == null) throw new IllegalArgumentException("Advisory entry price/range was not found.");

        Double target = extractSingle(combined, "(?:target(?:\\s*1)?|tgt(?:\\s*1)?)");
        Double stop = extractSingle(combined, "(?:stop\\s*loss|stoploss|\\bsl\\b)");
        if (stop == null) throw new IllegalArgumentException("Stop-loss was not found. Alert blocked for review.");

        return new Signal(side, symbol, range[0], range[1], target, stop, combined, intraday);
    }

    private static String extractSymbol(String upper, String side) {
        Pattern[] patterns = new Pattern[] {
                Pattern.compile("\\b(?:SYMBOL|TICKER|SCRIP|STOCK)\\s*[:=\\-]\\s*([A-Z][A-Z0-9&.\\-]{1,24})\\b"),
                Pattern.compile("\\b" + Pattern.quote(side) + "\\s+([A-Z][A-Z0-9&.\\-]{1,24})\\b"),
                Pattern.compile("\\b([A-Z][A-Z0-9&.\\-]{1,24})\\s+" + Pattern.quote(side) + "\\b")
        };
        for (Pattern p : patterns) {
            Matcher m = p.matcher(upper);
            if (m.find()) {
                String candidate = m.group(1);
                if (!SYMBOL_BLACKLIST.contains(candidate)) return candidate;
            }
        }
        return null;
    }

    private static double[] extractEntry(String text, String side, String symbol) {
        String labels = "(?:entry(?:\\s*price)?|buy\\s*price|sell\\s*price|buy\\s*around|sell\\s*around)";
        Pattern rangePattern = Pattern.compile(
                labels + "\\s*[:=\\-@]?\\s*(?:around|near|at)?\\s*(?:rs\\.?|inr|₹)?\\s*" + NUM +
                        "\\s*(?:-|–|—|to)\\s*(?:rs\\.?|inr|₹)?\\s*" + NUM,
                Pattern.CASE_INSENSITIVE);
        Matcher r = rangePattern.matcher(text);
        if (r.find()) {
            double a = parseNum(r.group(1));
            double b = parseNum(r.group(2));
            return new double[] {Math.min(a, b), Math.max(a, b)};
        }

        Pattern singlePattern = Pattern.compile(
                labels + "\\s*[:=\\-@]?\\s*(?:around|near|at|above|below)?\\s*(?:rs\\.?|inr|₹)?\\s*" + NUM,
                Pattern.CASE_INSENSITIVE);
        Matcher s = singlePattern.matcher(text);
        if (s.find()) {
            double v = parseNum(s.group(1));
            return new double[] {v, v};
        }

        Pattern compact = Pattern.compile(
                "\\b" + Pattern.quote(side) + "\\b\\s+" + Pattern.quote(symbol) +
                        "\\s*(?:@|at)\\s*(?:rs\\.?|inr|₹)?\\s*" + NUM,
                Pattern.CASE_INSENSITIVE);
        Matcher c = compact.matcher(text);
        if (c.find()) {
            double v = parseNum(c.group(1));
            return new double[] {v, v};
        }
        return null;
    }

    private static Double extractSingle(String text, String labelRegex) {
        Pattern p = Pattern.compile(labelRegex + "\\s*(?:price)?\\s*[:=\\-@]?\\s*(?:rs\\.?|inr|₹)?\\s*" + NUM,
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        return m.find() ? parseNum(m.group(1)) : null;
    }

    private static double parseNum(String s) {
        return Double.parseDouble(s.replace(",", ""));
    }

    private static String normalize(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    private static boolean containsAny(String text, String... words) {
        for (String w : words) if (text.contains(w)) return true;
        return false;
    }
}
