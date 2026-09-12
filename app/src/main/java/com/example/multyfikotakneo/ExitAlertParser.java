package com.example.multyfikotakneo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExitAlertParser {
    private ExitAlertParser() {}

    private static final Set<String> BLACKLIST = new HashSet<>(Arrays.asList(
            "EXIT", "NOW", "BOOK", "PROFIT", "PROFITS", "COST", "TO", "AT", "CMP",
            "CLOSE", "POSITION", "TRADE", "SQUARE", "OFF", "REDUCE", "LOSS", "STOP",
            "UPDATE", "ADVISORY", "MULTYFI", "INTRADAY", "MIS", "NSE", "BSE", "BUY", "SELL",
            "TARGET", "SL", "BREAKEVEN", "BREAK", "EVEN", "IMMEDIATELY", "FULL", "ALL"
    ));

    public static boolean looksLikeExit(String title, String body) {
        String lower = normalize((title == null ? "" : title) + " " + (body == null ? "" : body))
                .toLowerCase(Locale.ROOT);
        return containsAny(lower,
                "exit now", "exit trade", "exit position", "exit immediately", "exit at cmp",
                "book profit", "book profits", "book full profit", "close trade", "close position",
                "square off", "square-off", "cost to cost", "cost-to-cost", "breakeven exit",
                "break even exit", "reduce loss and exit", "reduce the loss and exit", "cut loss and exit",
                "exit cost to cost", "exit at cost");
    }

    public static ExitAlert parse(String title, String body) {
        String raw = normalize((title == null ? "" : title) + " " + (body == null ? "" : body));
        String lower = raw.toLowerCase(Locale.ROOT);
        if (!looksLikeExit(title, body)) {
            throw new IllegalArgumentException("This Multyfi notification does not contain a strong full-exit instruction.");
        }
        if (containsAny(lower, "partial profit", "book partial", "exit partial", "partial exit", "reduce quantity")) {
            throw new IllegalArgumentException("Partial-exit instruction detected. Manual review is required so the app does not close the full position.");
        }

        String symbol = extractSymbol(raw.toUpperCase(Locale.ROOT));
        if (symbol == null) {
            throw new IllegalArgumentException("Exit instruction detected, but the stock symbol could not be identified reliably.");
        }

        String reason;
        if (containsAny(lower, "cost to cost", "cost-to-cost", "breakeven", "break even", "exit at cost")) {
            reason = "Multyfi requested a cost-to-cost / breakeven exit.";
        } else if (containsAny(lower, "reduce loss", "cut loss")) {
            reason = "Multyfi requested an early exit to reduce the loss.";
        } else if (containsAny(lower, "book profit", "book profits")) {
            reason = "Multyfi requested booking profit and exiting now.";
        } else {
            reason = "Multyfi requested an immediate full exit.";
        }
        return new ExitAlert(symbol, reason, raw);
    }

    private static String extractSymbol(String upper) {
        String token = "([A-Z][A-Z0-9&.\\-]{1,24})";
        Pattern[] patterns = new Pattern[] {
                Pattern.compile("\\b(?:SYMBOL|TICKER|SCRIP|STOCK)\\s*[:=\\-]\\s*" + token + "\\b"),
                Pattern.compile("\\b(?:EXIT|CLOSE)\\s+(?:NOW\\s+|TRADE\\s+|POSITION\\s+)?" + token + "\\b"),
                Pattern.compile("\\b(?:BOOK\\s+(?:FULL\\s+)?PROFIT(?:S)?(?:\\s+IN)?|SQUARE[ -]?OFF|COST[ -]?TO[ -]?COST(?:\\s+EXIT)?)\\s*[:=\\-]?\\s*" + token + "\\b"),
                Pattern.compile("\\b" + token + "\\s*[:=\\-]?\\s*(?:EXIT|EXIT\\s+NOW|BOOK\\s+(?:FULL\\s+)?PROFIT(?:S)?|CLOSE|SQUARE[ -]?OFF)\\b")
        };
        for (Pattern p : patterns) {
            Matcher m = p.matcher(upper);
            while (m.find()) {
                String candidate = m.group(m.groupCount());
                if (isSymbolCandidate(candidate)) return candidate;
            }
        }

        // Last-resort scan. It is intentionally conservative; generic advisory words are rejected.
        Matcher m = Pattern.compile("\\b[A-Z][A-Z0-9&.\\-]{1,24}\\b").matcher(upper);
        while (m.find()) {
            String candidate = m.group();
            if (isSymbolCandidate(candidate)) return candidate;
        }
        return null;
    }

    private static boolean isSymbolCandidate(String s) {
        if (s == null) return false;
        String c = s.trim().toUpperCase(Locale.ROOT);
        if (c.length() < 2 || BLACKLIST.contains(c)) return false;
        if (c.matches("[0-9.\\-]+")) return false;
        return true;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    private static boolean containsAny(String text, String... words) {
        for (String w : words) if (text.contains(w)) return true;
        return false;
    }
}
