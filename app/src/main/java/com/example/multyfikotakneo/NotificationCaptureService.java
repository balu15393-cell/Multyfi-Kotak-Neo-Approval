package com.example.multyfikotakneo;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationCaptureService extends NotificationListenerService {
    private static final String MULTYFI_PACKAGE = "com.multyfi.invest";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !MULTYFI_PACKAGE.equals(sbn.getPackageName())) return;
        SettingsRepo settings = new SettingsRepo(this);
        if (!settings.isEnabled()) return;

        Bundle extras = sbn.getNotification().extras;
        String title = charSeq(extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = charSeq(extras.getCharSequence(Notification.EXTRA_TEXT));
        String big = charSeq(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        String sub = charSeq(extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        StringBuilder body = new StringBuilder();
        append(body, text); append(body, big); append(body, sub);
        if (lines != null) for (CharSequence line : lines) append(body, charSeq(line));

        String raw = (title + " " + body).replaceAll("\\s+", " ").trim();
        if (raw.isEmpty()) return;
        boolean exitUpdate = ExitAlertParser.looksLikeExit(title, body.toString());
        String upper = raw.toUpperCase();
        boolean entryAlert = upper.contains("BUY") || upper.contains("SELL");
        if (!exitUpdate && !entryAlert) return;

        TradeStore store = new TradeStore(this);
        if (store.isDuplicateToday(raw)) return;
        executor.execute(() -> {
            if (exitUpdate) processExit(title, body.toString(), settings, store);
            else processEntry(title, body.toString(), settings, store);
        });
    }

    private void processEntry(String title, String body, SettingsRepo settings, TradeStore store) {
        try {
            Signal signal = SignalParser.parse(title, body, settings.strictIntraday());
            KotakNeoCredentials credentials = new KotakNeoCredentialStore(this).load();
            KotakNeoSession session = new KotakNeoSessionStore(this).load();
            requireReady(credentials, session);

            KotakNeoScripResolver.Instrument instrument = new KotakNeoScripResolver(this)
                    .resolveNseEquity(credentials.consumerKey, session, signal.symbol);
            double ltp = KotakNeoLtpClient.fetch(credentials.consumerKey, session, instrument.token);
            double availableFunds = KotakNeoLimitsClient.fetchAvailableFunds(session);
            double effectiveCapital = Math.min(settings.capital(), availableFunds);
            if (effectiveCapital <= 0) {
                throw new IllegalStateException("No usable Kotak Neo funds are available for this entry.");
            }
            TradeTicket ticket = TradeDecision.build(
                    signal, ltp, effectiveCapital, settings.utilizationPct(),
                    settings.tolerancePct(), settings.exchange());
            ticket.instrumentToken = instrument.token;
            ticket.tradingSymbol = instrument.tradingSymbol;
            store.saveTicket(ticket);
            store.appendLog("ENTRY_TICKET_PREPARED", ticket,
                    ticket.reason + " Entered capital cap=₹" + String.format(java.util.Locale.ROOT, "%.2f", settings.capital()) +
                            ", Kotak live available funds=₹" + String.format(java.util.Locale.ROOT, "%.2f", availableFunds) +
                            ", effective capital=₹" + String.format(java.util.Locale.ROOT, "%.2f", effectiveCapital) +
                            ". Kotak token: " + instrument.token + ", trading symbol: " + instrument.tradingSymbol + ".");
            ApprovalNotifier.showApproval(this, ticket);
        } catch (Exception e) {
            store.appendLog("ENTRY_ALERT_BLOCKED", null, safeMessage(e));
            ApprovalNotifier.showStatus(this, "Multyfi entry alert blocked", safeMessage(e));
        }
    }

    private void processExit(String title, String body, SettingsRepo settings, TradeStore store) {
        try {
            ExitAlert alert = ExitAlertParser.parse(title, body);
            KotakNeoCredentials credentials = new KotakNeoCredentialStore(this).load();
            KotakNeoSession session = new KotakNeoSessionStore(this).load();
            requireReady(credentials, session);

            KotakNeoPortfolioClient.Position position = KotakNeoPortfolioClient.fetchMisPosition(session, alert.symbol);
            if (position == null || !position.isOpen()) {
                store.appendLog("EXIT_NO_OPEN_POSITION", null,
                        alert.symbol + ": no open Kotak Neo MIS quantity was found. No exit ticket prepared.");
                ApprovalNotifier.showStatus(this,
                        alert.symbol + " — already closed / no open MIS position",
                        "Multyfi requested an exit, but Kotak Neo shows no open MIS quantity. If you closed it manually, no further action is needed.");
                return;
            }

            if (KotakNeoOrderClient.hasActiveExitOrder(session, alert.symbol, position.exitSide())) {
                store.appendLog("EXIT_ORDER_ALREADY_PENDING", null,
                        alert.symbol + ": an active opposite-side Kotak Neo MIS order already exists. Duplicate exit blocked.");
                ApprovalNotifier.showStatus(this,
                        alert.symbol + " — exit already pending",
                        "Kotak Neo already has an active " + position.exitSide() + " MIS order for this symbol. No duplicate exit ticket was prepared.");
                return;
            }

            double ltp = 0;
            try {
                String token = position.instrumentToken;
                if (token == null || token.trim().isEmpty()) {
                    token = new KotakNeoScripResolver(this)
                            .resolveNseEquity(credentials.consumerKey, session, alert.symbol).token;
                }
                ltp = KotakNeoLtpClient.fetch(credentials.consumerKey, session, token);
            } catch (Exception ignored) {
                // Position verification is the critical exit safety check. LTP is informational.
            }

            TradeTicket ticket = TradeDecision.buildExit(position, ltp, alert.reason, settings.exchange());
            store.saveTicket(ticket);
            store.appendLog("URGENT_EXIT_TICKET_PREPARED", ticket,
                    ticket.reason + " Remaining Kotak Neo MIS quantity verified: " + ticket.quantity + ".");
            ApprovalNotifier.showApproval(this, ticket);
        } catch (Exception e) {
            store.appendLog("EXIT_ALERT_BLOCKED", null, safeMessage(e));
            ApprovalNotifier.showStatus(this, "Multyfi exit alert needs review", safeMessage(e));
        }
    }

    private static void requireReady(KotakNeoCredentials credentials, KotakNeoSession session) {
        if (credentials == null || !credentials.isComplete()) {
            throw new IllegalStateException("Kotak Neo credentials are missing. Open the assistant and save API token/consumer key, mobile and UCC.");
        }
        if (session == null || !session.isComplete()) {
            throw new IllegalStateException("Kotak Neo API login is required. Open the assistant and log in with current TOTP + MPIN.");
        }
    }

    private static String safeMessage(Exception e) {
        String m = e == null ? null : e.getMessage();
        return m == null || m.trim().isEmpty() ? "Unknown error" : m;
    }
    private static String charSeq(CharSequence c) { return c == null ? "" : c.toString(); }
    private static void append(StringBuilder b, String s) {
        if (s == null || s.trim().isEmpty()) return;
        if (b.length() > 0) b.append(" ");
        b.append(s.trim());
    }
    @Override public void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
