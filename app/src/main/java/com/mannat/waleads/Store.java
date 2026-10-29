package com.mannat.waleads;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

public class Store {
    public static SharedPreferences p(Context c) { return c.getSharedPreferences("wa", 0); }

    public static List<String> numbers(Context c) {
        String s = p(c).getString("numbers", "");
        List<String> l = new ArrayList<>();
        for (String x : s.split(",")) if (x.length() == 10) l.add(x);
        return l;
    }
    public static void setNumbers(Context c, List<String> l) {
        p(c).edit().putString("numbers", String.join(",", l)).putInt("idx", 0).apply();
    }
    public static void addLog(Context c, String line) {
        String old = p(c).getString("log", "");
        String all = line + "\n\n" + old;
        if (all.length() > 8000) all = all.substring(0, 8000);
        p(c).edit().putString("log", all).apply();
    }
    public static void addInterested(Context c, String num, String text) {
        String old = p(c).getString("interested", "");
        if (old.contains(num)) return;
        p(c).edit().putString("interested", old + num + " : " + text + "\n").apply();
    }
}
