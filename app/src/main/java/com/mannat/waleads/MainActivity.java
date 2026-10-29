package com.mannat.waleads;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.io.*;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

public class MainActivity extends Activity {
    EditText etMsg, etDelay, etKey, etPlans, etPhone;
    TextView tvCount, tvCurrent, tvLog;
    CheckBox cbAuto;
    Button btnSend;
    List<String> nums = new ArrayList<>();
    Handler h = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);
        etMsg = findViewById(R.id.etMsg); etDelay = findViewById(R.id.etDelay);
        etKey = findViewById(R.id.etKey); etPlans = findViewById(R.id.etPlans); etPhone = findViewById(R.id.etPhone);
        tvCount = findViewById(R.id.tvCount); tvCurrent = findViewById(R.id.tvCurrent); tvLog = findViewById(R.id.tvLog);
        cbAuto = findViewById(R.id.cbAuto); btnSend = findViewById(R.id.btnSend);
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);

        SharedPreferences p = Store.p(this);
        etKey.setText(p.getString("key", "")); etPlans.setText(p.getString("plans", ""));
        etPhone.setText(p.getString("phone", "")); cbAuto.setChecked(p.getBoolean("auto", false));
        if (p.contains("msg")) etMsg.setText(p.getString("msg", ""));
        nums = Store.numbers(this);
        refresh();

        findViewById(R.id.btnImport).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, 10);
        });
        btnSend.setOnClickListener(v -> sendCurrent());
        findViewById(R.id.btnSkip).setOnClickListener(v -> { advance(); });
        findViewById(R.id.btnReset).setOnClickListener(v -> { p.edit().putInt("idx", 0).apply(); refresh(); });
        findViewById(R.id.btnSave).setOnClickListener(v -> {
            p.edit().putString("key", etKey.getText().toString().trim()).putString("plans", etPlans.getText().toString())
                .putString("phone", etPhone.getText().toString().trim()).putBoolean("auto", cbAuto.isChecked())
                .putString("msg", etMsg.getText().toString()).apply();
            Toast.makeText(this, "सेव हो गया", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btnAccess).setOnClickListener(v -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        findViewById(R.id.btnRefresh).setOnClickListener(v -> showLog());
        showLog();
    }

    void showLog() {
        SharedPreferences p = Store.p(this);
        tvLog.setText("=== Interested Customers ===\n" + p.getString("interested", "(अभी कोई नहीं)")
            + "\n=== AI लॉग ===\n" + p.getString("log", ""));
    }

    void refresh() {
        int idx = Store.p(this).getInt("idx", 0);
        tvCount.setText("कुल नंबर: " + nums.size() + "   भेजे गए: " + Math.min(idx, nums.size()));
        tvCurrent.setText(idx < nums.size() ? "अगला: " + nums.get(idx) : "लिस्ट पूरी / खाली");
    }

    void advance() {
        SharedPreferences p = Store.p(this);
        p.edit().putInt("idx", p.getInt("idx", 0) + 1).apply();
        refresh();
    }

    void sendCurrent() {
        int idx = Store.p(this).getInt("idx", 0);
        if (idx >= nums.size()) { Toast.makeText(this, "लिस्ट खत्म", Toast.LENGTH_SHORT).show(); return; }
        Store.p(this).edit().putString("msg", etMsg.getText().toString()).apply();
        try {
            String url = "https://wa.me/91" + nums.get(idx) + "?text=" + URLEncoder.encode(etMsg.getText().toString(), "UTF-8");
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            advance();
            // गैप टाइमर: Send बटन कुछ सेकंड के लिए बंद ताकि जल्दबाज़ी में स्पैम न हो
            int d = 12;
            try { d = Integer.parseInt(etDelay.getText().toString().trim()); } catch (Exception ignored) {}
            btnSend.setEnabled(false);
            final int[] left = {d};
            h.post(new Runnable() { public void run() {
                if (left[0] <= 0) { btnSend.setEnabled(true); btnSend.setText("2. WhatsApp खोलो (मैसेज भरा हुआ)"); return; }
                btnSend.setText("रुकें… " + left[0]-- + " सेकंड");
                h.postDelayed(this, 1000);
            }});
        } catch (Exception e) { Toast.makeText(this, "WhatsApp नहीं खुला: " + e, Toast.LENGTH_LONG).show(); }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 10 || res != RESULT_OK || data == null) return;
        try {
            InputStream in = getContentResolver().openInputStream(data.getData());
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            byte[] bytes = bo.toByteArray();
            String text;
            if (bytes.length > 3 && bytes[0] == 'P' && bytes[1] == 'K') text = xlsxText(bytes); // .xlsx
            else text = new String(bytes, "UTF-8");                                           // .csv
            LinkedHashSet<String> set = new LinkedHashSet<>();
            // 10 अंक, 6-9 से शुरू; +91 / 0 / स्पेस-डैश हटाकर
            Matcher m = Pattern.compile("(?<!\\d)(?:\\+?91[\\s-]?|0)?([6-9][\\d\\s-]{9,13})(?!\\d)").matcher(text);
            while (m.find()) {
                String d = m.group(1).replaceAll("[^\\d]", "");
                if (d.length() == 10) set.add(d);
            }
            nums = new ArrayList<>(set);
            Store.setNumbers(this, nums);
            refresh();
            Toast.makeText(this, nums.size() + " नंबर मिले", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Toast.makeText(this, "फाइल पढ़ने में दिक्कत: " + e, Toast.LENGTH_LONG).show(); }
    }

    String xlsxText(byte[] bytes) throws IOException {
        StringBuilder sb = new StringBuilder();
        ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(bytes));
        ZipEntry e;
        while ((e = z.getNextEntry()) != null) {
            String nm = e.getName();
            if (nm.equals("xl/sharedStrings.xml") || nm.startsWith("xl/worksheets/sheet")) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] b = new byte[8192]; int n;
                while ((n = z.read(b)) > 0) bo.write(b, 0, n);
                // टैग हटाकर हर सेल को अलग लाइन में रखो ताकि नंबर आपस में न जुड़ें
                sb.append(bo.toString("UTF-8").replaceAll("<[^>]+>", "\n")).append("\n");
            }
        }
        return sb.toString();
    }
}
