package com.example.multyfikotakneo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class ApprovalNotifier {
    public static final String CHANNEL_APPROVAL = "trade_approvals";
    public static final String CHANNEL_STATUS = "trade_status";
    public static final String ACTION_APPROVE = "com.example.multyfikotakneo.APPROVE";
    public static final String ACTION_REJECT = "com.example.multyfikotakneo.REJECT";
    public static final String EXTRA_TICKET_ID = "ticket_id";

    private ApprovalNotifier() {}

    public static void createChannels(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel approvals = new NotificationChannel(
                    CHANNEL_APPROVAL, "Trade approvals", NotificationManager.IMPORTANCE_HIGH);
            approvals.setDescription("Multyfi intraday entry and urgent exit tickets waiting for your review");
            NotificationChannel status = new NotificationChannel(
                    CHANNEL_STATUS, "Trade assistant status", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(approvals);
            nm.createNotificationChannel(status);
        }
    }

    public static void showApproval(Context c, TradeTicket t) {
        createChannels(c);
        int requestCode = Math.abs(t.id.hashCode());
        Intent approve = new Intent(c, TradeActionReceiver.class)
                .setAction(ACTION_APPROVE)
                .putExtra(EXTRA_TICKET_ID, t.id);
        Intent reject = new Intent(c, TradeActionReceiver.class)
                .setAction(ACTION_REJECT)
                .putExtra(EXTRA_TICKET_ID, t.id);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent approvePi = PendingIntent.getBroadcast(c, requestCode, approve, flags);
        PendingIntent rejectPi = PendingIntent.getBroadcast(c, requestCode + 1, reject, flags);

        boolean exit = "EXIT".equals(t.ticketKind);
        String order = "MARKET".equals(t.orderType) ? "MARKET" : String.format("LIMIT ₹%.2f", t.orderPrice);
        String text = exit
                ? String.format("Remaining Qty %d • %s • %s", t.quantity, order,
                    t.ltp > 0 ? String.format("LTP ₹%.2f", t.ltp) : "LTP unavailable")
                : String.format("%s • Qty %d • %s • LTP ₹%.2f", t.advisoryEntry, t.quantity, order, t.ltp);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(c, CHANNEL_APPROVAL)
                : new Notification.Builder(c);
        b.setSmallIcon(exit ? android.R.drawable.ic_dialog_alert : android.R.drawable.ic_dialog_info)
                .setContentTitle(exit
                        ? "URGENT EXIT — " + t.symbol + " — Approve & send"
                        : t.symbol + " " + t.side + " — Approve & send MIS order")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(t.displayText()))
                .setAutoCancel(false)
                .setOnlyAlertOnce(false)
                .setPriority(exit ? Notification.PRIORITY_MAX : Notification.PRIORITY_HIGH)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_send, exit ? "EXIT NOW & SEND" : "APPROVE & SEND", approvePi).build())
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel, exit ? "IGNORE" : "REJECT", rejectPi).build());

        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(requestCode, b.build());
    }

    public static void showStatus(Context c, String title, String message) {
        createChannels(c);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(c, CHANNEL_STATUS)
                : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setAutoCancel(true);
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE))
                .notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), b.build());
    }

    public static void cancelTicket(Context c, TradeTicket t) {
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE))
                .cancel(Math.abs(t.id.hashCode()));
    }
}
