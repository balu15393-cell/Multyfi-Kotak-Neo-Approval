package com.example.multyfikotakneo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TradeActionReceiver extends BroadcastReceiver {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        String ticketId = intent.getStringExtra(ApprovalNotifier.EXTRA_TICKET_ID);
        if (ticketId == null) return;
        TradeStore store = new TradeStore(context);

        if (ApprovalNotifier.ACTION_REJECT.equals(intent.getAction())) {
            try {
                TradeTicket t = store.loadTicket(ticketId);
                if (t == null) return;
                boolean exit = "EXIT".equals(t.ticketKind);
                ApprovalNotifier.cancelTicket(context, t);
                store.appendLog(exit ? "EXIT_IGNORED" : "ENTRY_REJECTED", t,
                        exit ? "User ignored the prepared exit ticket." : "User rejected the prepared entry ticket.");
                store.deleteTicket(ticketId);
            } catch (Exception e) {
                ApprovalNotifier.showStatus(context, "Trade assistant", "Could not reject ticket: " + safe(e));
            }
            return;
        }

        if (!ApprovalNotifier.ACTION_APPROVE.equals(intent.getAction())) return;
        if (!store.claimExecution(ticketId)) {
            ApprovalNotifier.showStatus(context, "Kotak Neo order", "This ticket is already being processed.");
            return;
        }

        final PendingResult pending = goAsync();
        EXECUTOR.execute(() -> {
            try {
                TradeTicket t = store.loadTicket(ticketId);
                if (t == null) return;
                boolean exit = "EXIT".equals(t.ticketKind);
                ApprovalNotifier.showStatus(context,
                        exit ? t.symbol + " — sending exit" : t.symbol + " — sending order",
                        "Checking the latest Kotak Neo state and submitting your approved " + (exit ? "exit" : "entry") + "…");

                KotakNeoExecutionClient.Result r = KotakNeoExecutionClient.execute(context, t);
                if (r.submitted) {
                    store.appendLog(exit ? "EXIT_ORDER_SUBMITTED" : "ENTRY_ORDER_SUBMITTED", t,
                            r.message + " Final side=" + r.side + ", quantity=" + r.quantity +
                                    ", type=" + r.orderType + (r.price == null ? "" : ", price=" + r.price) + ".");
                    ApprovalNotifier.cancelTicket(context, t);
                    ApprovalNotifier.showStatus(context,
                            exit ? t.symbol + " — EXIT SENT" : t.symbol + " — ORDER SENT", r.message);
                    store.deleteTicket(ticketId);
                } else if (r.noAction) {
                    store.appendLog(exit ? "EXIT_NO_ACTION" : "ENTRY_NO_ACTION", t, r.message);
                    ApprovalNotifier.cancelTicket(context, t);
                    ApprovalNotifier.showStatus(context, t.symbol + " — no order sent", r.message);
                    store.deleteTicket(ticketId);
                }
            } catch (Exception e) {
                store.releaseExecution(ticketId);
                ApprovalNotifier.showStatus(context, "Kotak Neo order NOT sent", safe(e));
            } finally {
                pending.finish();
            }
        });
    }

    private static String safe(Exception e) {
        String m = e == null ? null : e.getMessage();
        return m == null || m.trim().isEmpty() ? "Unknown error" : m;
    }
}
