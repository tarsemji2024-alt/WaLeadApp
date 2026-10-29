package com.mannat.waleads;

import java.io.*;
import java.net.*;
import org.json.*;

public class Gemini {
    // मॉडल का नाम बदलना हो तो सिर्फ यहाँ बदलें
    static final String MODEL = "gemini-2.0-flash";

    public static String ask(String key, String plans, String phone, String customerMsg) throws Exception {
        String sys = "तुम मन्नत कम्युनिकेशंस के सेल्स असिस्टेंट हो और Jio AirFiber के बारे में हिंदी में छोटे, विनम्र जवाब देते हो। "
            + "सिर्फ नीचे दी गई जानकारी से जवाब दो। जो जानकारी नहीं है उसके लिए कहो कि हमारे प्रतिनिधि खुद बताएँगे। कभी कीमत/ऑफर अपनी तरफ से मत बनाओ। "
            + "तुम AI असिस्टेंट हो; कोई पूछे तो साफ बता दो। अगर ग्राहक लगवाना चाहे या कॉल मांगे तो उसका पता/सही समय पूछो और यह नंबर दो: " + phone
            + "। ऐसा होने पर जवाब के अंत में अलग से [LEAD] शब्द लिख दो।\n\nजानकारी:\n" + plans;
        JSONObject body = new JSONObject()
            .put("system_instruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", sys))))
            .put("contents", new JSONArray().put(new JSONObject().put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text", customerMsg)))));
        URL u = new URL("https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent?key=" + key);
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        c.setConnectTimeout(15000); c.setReadTimeout(30000);
        try (OutputStream o = c.getOutputStream()) { o.write(body.toString().getBytes("UTF-8")); }
        InputStream in = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = r.readLine()) != null) sb.append(line);
        JSONObject j = new JSONObject(sb.toString());
        return j.getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
                .getJSONArray("parts").getJSONObject(0).getString("text").trim();
    }
}
