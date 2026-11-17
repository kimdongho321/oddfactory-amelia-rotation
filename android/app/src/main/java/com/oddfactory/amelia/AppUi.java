package com.oddfactory.amelia;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/** 두두·키디언 AppUi와 같은 구성(메이플스토리 Light/Bold, 둥근 버튼). 색은 아멜리아(차원문 보라·복숭아) 팔레트. */
final class AppUi {
    static final int INK = 0xff3a2c3a, BG = 0xfffff6ed, ACCENT = 0xff7a5ec8, SOFT = 0xfff1e4ef, MUTED = 0xff7a6a78;
    static int dp(Context c, float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    private static Typeface light, bold;
    static synchronized Typeface font(Context c, boolean b) {
        try {
            if (b) { if (bold == null) bold = Typeface.createFromAsset(c.getAssets(), "fonts/Maplestory-Bold.ttf"); return bold; }
            if (light == null) light = Typeface.createFromAsset(c.getAssets(), "fonts/Maplestory-Light.ttf"); return light;
        } catch (RuntimeException e) { return b ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT; }
    }
    static TextView text(Context c, String s, int size, boolean b) {
        TextView v = new TextView(c); v.setText(s); v.setTextSize(size); v.setTextColor(INK); v.setTypeface(font(c, b)); v.setLineSpacing(0, 1.15f); return v;
    }
    static TextView muted(Context c, String s, int size) { TextView v = text(c, s, size, false); v.setTextColor(MUTED); return v; }
    static Button button(Context c, String s, boolean primary, Runnable action) {
        Button v = new Button(c); v.setText(s); v.setAllCaps(false); v.setTextSize(primary ? 22 : 16); v.setTypeface(font(c, primary));
        v.setTextColor(primary ? 0xffffffff : INK); v.setMinHeight(dp(c, primary ? 72 : 48));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(primary ? ACCENT : SOFT); bg.setCornerRadius(dp(c, 16)); v.setBackground(bg);
        v.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10)); v.setOnClickListener(w -> action.run()); return v;
    }
    static CheckBox check(Context c, String s, boolean on) {
        CheckBox b = new CheckBox(c); b.setText(s); b.setTextSize(16); b.setTextColor(INK); b.setTypeface(font(c, false));
        b.setButtonTintList(ColorStateList.valueOf(ACCENT)); b.setChecked(on); b.setMinHeight(dp(c, 44)); return b;
    }
    static void fonts(Context c, View v) {
        if (v instanceof TextView) ((TextView) v).setTypeface(font(c, v instanceof Button));
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) fonts(c, g.getChildAt(i)); }
    }
}
