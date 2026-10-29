package com.mannat.waleads;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.service.notification.*;
import java.util.*;

public class ReplyService extends NotificationListenerService {
    private final Set<String> seen = new HashSet<>();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (!pkg.equals("com.whatsapp") && !pkg.equals("com.whatsapp.w4b")) return;
        Notification n = sbn.getNotification();
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        Bundle e = n.extras;
        String title = String.valueOf(e.getCharSequence(Notification.EXTRA_TITLE, ""));
        CharSequence t = e.getCharSequence(Notification.EXTRA_TEXT);
        if (t == null) return;
        String text = t.toString();
        if (title.contains(":")) return; // ग्रुप चैट छोड़ दो
        String digits = title.replaceAll("\\D", "");
        if (digits.length() < 10) return;
        final String num = digits.substring(digits.length() - 10);
        // सिर्फ इम्पोर्ट की गई लिस्ट वाले नंबर
        if (!Store.numbers(this).contains(num)) return;
        String id = sbn.getKey() + text;
        if (!seen.add(id)) return;

        Notification.Action reply = null;
        if (n.actions != null) for (Notification.Action a : n.actions)
            if (a.getRemoteInputs() != null && a.getRemoteInputs().length > 0) { reply = a; break; }
        final Notification.Action ra = reply;
        final String msg = text;

        new Thread(() -> {
            SharedPreferences p = Store.p(this);
            String key = p.getString("key", "");
            if (key.isEmpty()) { Store.addLog(this, num + " ने लिखा: " + msg + "\n(Gemini key नहीं है)"); return; }
            try {
                String ans = Gemini.ask(key, p.getString("plans", ""), p.getString("phone", ""), msg);
                boolean lead = ans.contains("[LEAD]");
                ans = ans.replace("[LEAD]", "").trim();
                String lower = msg.toLowerCase();
                if (lead || lower.contains("लगवाना") || lower.contains("लगवा") || lower.contains("कॉल कर") || lower.contains("call")) {
                    Store.addInterested(this, num, msg);
                    alert(num);
                }
                if (p.getBoolean("auto", false) && ra != null) {
                    android.app.RemoteInput[] ri = ra.getRemoteInputs();
                    Intent i = new Intent();
                    Bundle b = new Bundle();
                    b.putCharSequence(ri[0].getResultKey(), ans);
                    android.app.RemoteInput.addResultsToIntent(ri, i, b);
                    ra.actionIntent.send(this, 0, i);
                    Store.addLog(this, "[AUTO भेजा] " + num + "\nग्राहक: " + msg + "\nजवाब: " + ans);
                } else {
                    Store.addLog(this, "[ड्राफ्ट] " + num + "\nग्राहक: " + msg + "\nजवाब (कॉपी करके भेजें): " + ans);
                }
            } catch (Exception ex) {
                Store.addLog(this, num + " : AI error " + ex);
            }
        }).start();
    }

    private void alert(String num) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("lead", "Interested Lead", NotificationManager.IMPORTANCE_HIGH));
        Notification.Builder b = new Notification.Builder(this, "lead")
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("पक्की लीड!").setContentText(num + " को कॉल करें").setAutoCancel(true);
        nm.notify((int) System.currentTimeMillis(), b.build());
    }
}
