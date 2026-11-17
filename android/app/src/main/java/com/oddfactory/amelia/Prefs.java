package com.oddfactory.amelia;

import android.content.Context;
import android.content.SharedPreferences;

/** 설정 값: 크기(dp), 대기 시간(초), 걷기 빈도(%), 볼 꼬집기 켜기, 가로·세로 전환 때 화면도 돌리기. */
final class Prefs {
    static SharedPreferences p(Context c) { return c.getSharedPreferences("amelia", 0); }
    static int size(Context c) { return p(c).getInt("size", 120); }
    static void size(Context c, int v) { p(c).edit().putInt("size", v).apply(); }
    static int idleSec(Context c) { return p(c).getInt("idle", 2); }
    static void idleSec(Context c, int v) { p(c).edit().putInt("idle", v).apply(); }
    static int walkPct(Context c) { return p(c).getInt("walk", 35); }
    static void walkPct(Context c, int v) { p(c).edit().putInt("walk", v).apply(); }
    static boolean pinch(Context c) { return p(c).getBoolean("pinch", true); }
    static void pinch(Context c, boolean v) { p(c).edit().putBoolean("pinch", v).apply(); }
}
