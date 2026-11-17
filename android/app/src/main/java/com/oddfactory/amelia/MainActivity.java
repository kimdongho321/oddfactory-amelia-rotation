package com.oddfactory.amelia;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** 설정 화면: 부르기/들여보내기(가장 크게) → 크기·동작 설정 → 권한 → 조작 안내 → 글꼴·이용 고지 → 현재 버전. 글꼴은 메이플스토리. */
public final class MainActivity extends Activity {
    static MainActivity shown;
    Button run;
    TextView state, sizeValue, idleValue, walkValue, rotState, overlayState;
    com.oddfactory.update.UpdateCheck appUpdates;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        appUpdates = new com.oddfactory.update.UpdateCheck(this, v -> {});
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS", "android.permission.BLUETOOTH_CONNECT"}, 1);
        else if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission("android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"}, 2);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(28), dp(22), dp(28));
        box.setBackgroundColor(AppUi.BG);

        box.addView(AppUi.text(this, "아멜리아 화면 회전", 28, true));
        state = AppUi.muted(this, "", 15); box.addView(state);
        run = AppUi.button(this, "", true, this::toggle); add(box, run, 84);

        // ── 크기·동작 ──
        section(box, "크기");
        sizeValue = AppUi.muted(this, "", 14); box.addView(sizeValue);
        box.addView(seek(260, Prefs.size(this) - 120, p -> { Prefs.size(this, p + 120); showSize(); if (AmeliaService.current != null) AmeliaService.current.resize(); }));

        section(box, "대기 시간");
        idleValue = AppUi.muted(this, "", 14); box.addView(idleValue);
        box.addView(seek(5, Prefs.idleSec(this) - 1, p -> { Prefs.idleSec(this, p + 1); showIdle(); }));

        CheckBox pinch = AppUi.check(this, "길게 누르면 볼 꼬집기", Prefs.pinch(this));
        pinch.setOnCheckedChangeListener((v, on) -> Prefs.pinch(this, on));
        add(box, pinch, -2);

        // ── 권한 ──
        section(box, "권한");
        overlayState = AppUi.muted(this, "", 14); box.addView(overlayState);
        add(box, AppUi.button(this, "다른 앱 위에 표시 설정", false, () ->
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))), 52);
        rotState = AppUi.muted(this, "", 14); add(box, rotState, -2);
        add(box, AppUi.button(this, "시스템 설정 변경 허용(가로·세로 전환)", false, () ->
                startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName())))), 52);
        add(box, AppUi.button(this, "회전 원래대로(자동 회전 되돌리기)", false, () -> { if (AmeliaService.current != null) AmeliaService.current.releaseRotation(); }), 52);

        // ── 안내·고지 ──
        section(box, "조작");
        box.addView(AppUi.text(this, "세로 화면엔 아멜리아, 가로 화면엔 R41 아멜리아가 나와요.\n"
                + "· 두 번 터치: 차원문에 빨려 들어간 뒤 가로·세로 전환 → 반대 아멜리아 등장\n"
                + "   (홈 화면처럼 회전을 안 하는 화면에선 화면은 그대로, 아멜리아만 바뀌어요)\n"
                + "· 세 번 터치: 메뉴(설정·회전 원래대로·종료)\n"
                + "· 블루투스 기기(버즈 등)를 연결하면: 차원 배송 블루투스 모션\n"
                + "· 길게 누르고 있기: 볼 꼬집혀 버둥 → 놓으면 넘어졌다 일어나요\n"
                + "· 누른 채 끌기: 위치 옮기기(끄는 방향으로 돌아봐요)", 14, false));
        add(box, AppUi.button(this, "블루투스 연결 미리보기", false, () -> sendBroadcast(new Intent("com.oddfactory.amelia.PREVIEW_BT").setPackage(getPackageName()).putExtra("name", "Galaxy Buds"))), 52);
        add(box, AppUi.button(this, "글꼴·이용 고지", false, this::licenses), 52);

        add(box, AppUi.button(this, "업데이트 확인 (현재 " + BuildConfig.VERSION_NAME + ")", false, () -> appUpdates.checkNow()), 52);
        TextView v = AppUi.muted(this, "현재 " + BuildConfig.VERSION_NAME + "\n글꼴: 메이플스토리 서체(넥슨) 사용\n트릭컬 팬아트로 만든 비상업 팬메이드 앱이에요.", 12);
        v.setGravity(Gravity.END); add(box, v, -2);

        ScrollView sv = new ScrollView(this); sv.setBackgroundColor(AppUi.BG); sv.addView(box);
        setContentView(sv);
        showSize(); showIdle();
    }

    interface IntCb { void on(int p); }
    SeekBar seek(int max, int value, IntCb cb) {
        SeekBar s = new SeekBar(this); s.setMax(max); s.setProgress(value);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar x, int p, boolean u) { if (u) cb.on(p); }
            public void onStartTrackingTouch(SeekBar x) { }
            public void onStopTrackingTouch(SeekBar x) { }
        });
        return s;
    }
    void section(LinearLayout box, String t) { TextView v = AppUi.text(this, t, 17, true); v.setPadding(0, dp(22), 0, 0); box.addView(v); }
    void add(LinearLayout box, View v, int h) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, h < 0 ? h : dp(h)); p.topMargin = dp(10); box.addView(v, p); }

    void showSize() {
        int d = Prefs.size(this); int px = dp(d);
        sizeValue.setText(d + "dp  (이 폰에서 " + px + "×" + px + "px)");
    }
    void showIdle() { idleValue.setText("행동 사이 " + Prefs.idleSec(this) + "초 대기"); }

    void licenses() {
        String font;
        try (java.io.InputStream in = getAssets().open("licenses/maplestory.txt")) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n); font = out.toString("UTF-8");
        } catch (Exception e) { font = "메이플스토리 서체의 지적 재산권을 포함한 모든 권리는 ㈜넥슨코리아에 있습니다."; }
        AlertDialog d = new AlertDialog.Builder(this).setTitle("글꼴·이용 고지").setMessage(
                "[팬메이드 앱 안내]\n아멜리아 화면 회전은 게임 「트릭컬」의 캐릭터를 바탕으로 그린 팬아트로 만든 비공식·비상업 팬메이드 앱이에요. "
                + "무료로 배포하며 판매하거나 광고를 넣지 않아요. 원작 캐릭터와 게임에 대한 권리는 원저작권자에게 있어요.\n\n"
                + "[글꼴]\n이 앱에는 넥슨코리아가 제공한 메이플스토리 서체(Bold·Light)가 적용되어 있어요.\n\n" + font)
                .setPositiveButton("확인", null).create();
        d.setOnShowListener(x -> AppUi.fonts(this, d.getWindow().getDecorView()));
        d.show();
    }

    @Override protected void onResume() { super.onResume(); shown = this; paint(); appUpdates.resume(); }
    @Override protected void onPause() { appUpdates.pause(); super.onPause(); if (shown == this) shown = null; }
    @Override protected void onDestroy() { appUpdates.destroy(); super.onDestroy(); }
    @Override protected void onActivityResult(int request, int result, Intent data) { super.onActivityResult(request, result, data); appUpdates.onActivityResult(request); }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if (focused) appUpdates.onWindowFocus(); }
    static void refresh() { MainActivity a = shown; if (a != null) a.runOnUiThread(a::paint); }

    void paint() {
        boolean on = AmeliaService.current != null && AmeliaService.current.running;
        boolean ov = Settings.canDrawOverlays(this);
        run.setText(on ? "종료" : "시작");
        state.setText(ov ? (on ? "나와 있어요" : "쉬는 중") : "다른 앱 위에 표시 허용이 필요해요");
        overlayState.setText("다른 앱 위에 표시: " + (ov ? "허용됨" : "허용 필요"));
        rotState.setText("가로·세로 전환(시스템 설정 변경): " + (Settings.System.canWrite(this) ? "허용됨" : "허용 필요"));
    }

    void toggle() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        AmeliaService s = AmeliaService.current;
        if (s != null && s.running) s.exit();
        else startForegroundService(new Intent(this, AmeliaService.class));
        run.postDelayed(this::paint, 400);
    }

    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
