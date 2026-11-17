package com.oddfactory.amelia;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.Insets;
import android.os.*;
import android.provider.Settings;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import java.io.InputStream;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 아멜리아 오버레이.
 * 시작: 등장 → 대기(2초) → 홀로그램 → 대기 → 아메리카노 → 대기 … (걷기는 대기 사이 무작위로 무작위 위치)
 * 더블터치: 가로↔세로 전환(회전 고정 창) → 방향이 바뀌면 회전 바꿔치기(세로=아멜리아, 가로=R41)
 * 세 번 터치: 메뉴(설정·회전 고정 풀기·종료). 길게 누름: 볼 꼬집혀 버둥(누른 채 끌면 이동). 종료: 퇴장 후 닫힘.
 */
public final class AmeliaService extends Service {
    public static volatile AmeliaService current;
    static final int FRAME_MS = 150, WALK_MS = 140, IDLE_HOLD_MS = 2000, WALK_TIME_MS = 1500;
    static final String[] CYCLE = {"coffee", "walk", "holo", "walk"};   // 대기 사이에 차례로(사용자 지정 순서)

    final Handler h = new Handler(Looper.getMainLooper());
    WindowManager wm;
    ImageView view;
    WindowManager.LayoutParams lp;
        JSONObject motions;
    LruCache<String, Bitmap> cache;
    String ch;
    int token, cycle, taps, sizePx;
    boolean running, exiting, swapping, dragging, walking, pinched;
    float downX, downY, lastMoveX; int startX, startY; long downAt;
    AlertDialog menu;
    final Random rnd = new Random();

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        current = this;
        wm = getSystemService(WindowManager.class);
        cache = new LruCache<String, Bitmap>(48 * 1024 * 1024) {
            @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
        };
        try (InputStream in = getAssets().open("motions.json")) {
            motions = new JSONObject(new String(in.readAllBytes(), "UTF-8"));
        } catch (Exception e) { motions = new JSONObject(); }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY; }
        if (!running) start();
        return START_STICKY;
    }

    void start() {
        running = true; exiting = false;
        foreground();
        sizePx = dp(Prefs.size(this));
        view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        lp = new WindowManager.LayoutParams(sizePx, sizePx, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        // 좌표를 화면 전체 기준으로(상태바 아래 기준이면 아래로 밀려 몸이 폰 밖으로 나감). 위치는 area()로 제한.
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        Rect a = area();
        lp.x = a.left + (a.width() - sizePx) / 2;
        lp.y = a.bottom - sizePx - dp(24);
        view.setOnTouchListener(this::touch);
        wm.addView(view, lp);
        ch = landscape() ? "r41" : "amelia";
        play("enter", 110, this::idle);   // 등장 빠르게(2026-11-18)
        IntentFilter bf = new IntentFilter(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED);
        bf.addAction("com.oddfactory.amelia.PREVIEW_BT");   // 설정의 '미리보기'·검수용
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(btRx, bf, Context.RECEIVER_EXPORTED); else registerReceiver(btRx, bf);
    }

    /** 블루투스 기기(버즈 등)가 연결되면 하던 동작을 멈추고 블루투스 모션(차원 배송) 재생. */
    final BroadcastReceiver btRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (!running || exiting || swapping || pinched) return;
            // 버즈는 왼쪽·오른쪽 이어폰과 여러 프로필이 각각 연결 신호를 보내서 한 번 연결에 신호가 여러 번 온다 → 한 번만 연출
            long now = android.os.SystemClock.uptimeMillis();
            if (drone != null || now - lastBt < 15000) return;
            lastBt = now;
            android.bluetooth.BluetoothDevice d = i.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE);
            String name = i.getStringExtra("name"); int cls = i.getIntExtra("cls", 0);
            try { if (d != null) { name = d.getName(); if (d.getBluetoothClass() != null) cls = d.getBluetoothClass().getDeviceClass(); } } catch (SecurityException ignored) { }
            final String icon = deviceIcon(name, cls), label = name == null || name.isEmpty() ? "블루투스 기기" : name;
            h.postDelayed(() -> {
                if (!running || exiting || swapping || pinched) return;
                walking = false; view.setScaleX(1f);
                showDrone(() -> showBadge(icon, label));
                play("bt", 300, AmeliaService.this::idle);   // 드론 스캔(1.9초)+알림 동안 이어지게
            }, 300);
        }
    };

    /** 기기 종류 → 아이콘 이름(assets/dev). 이름에 Buds·AirPods 등이 있으면 이어폰. */
    static String deviceIcon(String name, int cls) {
        String n = name == null ? "" : name.toLowerCase();
        if (n.contains("bud") || n.contains("airpod") || n.contains("이어") || n.contains("earbud")) return "buds";
        if (n.contains("watch") || n.contains("워치") || n.contains("band")) return "watch";
        int major = cls & 0x1F00;
        if (cls == 0x0414) return "speaker";
        if (cls == 0x0420) return "car";
        if (major == 0x0400) return cls == 0x0418 ? "headphones" : "buds";
        if (major == 0x0700) return "watch";
        if (major == 0x0100) return "pc";
        if (major == 0x0500) { if ((cls & 0xC0) == 0x40) return "keyboard"; if ((cls & 0xC0) == 0x80) return "mouse"; if ((cls & 0x3C) == 0x08) return "gamepad"; }
        return "buds";
    }

    DroneView drone; WindowManager.LayoutParams dl; long lastBt = -100000;
    /** 드론이 위에서 날아와 캐릭터 위에 멈추고, 아래로 넓어지는 삼각 빔으로 스캔 → 끝나면 then 실행(연결 알림) → 위로 날아감. */
    void showDrone(Runnable then) {
        hideDrone();
        Bitmap b = null;
        try (InputStream in = getAssets().open("fx/drone_" + ch + ".webp")) { b = BitmapFactory.decodeStream(in); } catch (Exception ignored) { }
        int ds = Math.max(dp(72), sizePx * 46 / 100), beam = sizePx * 82 / 100;
        DroneView dv = new DroneView(this, b, ds, beam);
        if (ch.equals("amelia")) { dv.lens = 0.72f; dv.beamTop = 0xCC7FD4FF; dv.beamBot = 0x3366BBFF; dv.edge = 0xFFC8EEFF; }   // 초승달 드론·하늘색 빔
        int w = Math.max(ds, beam), hgt = ds + beam + ds / 4;
        dl = new WindowManager.LayoutParams(w, hgt, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        dl.gravity = Gravity.TOP | Gravity.START;
        final float handX = ch.equals("amelia") ? 0.60f : 0.55f;   // 블루투스 모션에서 기기 든 손의 가로 위치(프레임 폭 비율)
        final int tx = Math.round(lp.x + sizePx * handX) - w / 2, ty = lp.y + sizePx * 55 / 100 - (ds + beam);   // 드론 높이는 그대로, 빔 끝이 손 높이   // 빔 끝이 캐릭터 가슴(손) 높이
        dl.x = tx; dl.y = ty - dp(400);
        try { wm.addView(dv, dl); } catch (Exception e) { then.run(); return; }
        drone = dv;
        final long t0 = android.os.SystemClock.uptimeMillis();
        new Runnable() { @Override public void run() {
            if (drone != dv) return;
            long t = android.os.SystemClock.uptimeMillis() - t0;
            float in = Math.min(1f, t / 500f);                                   // 0.5초 날아옴
            float e = 1 - (1 - in) * (1 - in);
            dl.y = Math.round(ty - dp(400) * (1 - e));
            dv.hover = (float) Math.sin(t / 160.0) * dp(3);
            dv.beam = t < 500 ? 0 : Math.min(1f, (t - 500) / 300f);              // 빔 펼침
            dv.scan = (t % 700) / 700f;
            if (t > 2300) dl.y = Math.round(ty - dp(400) * Math.min(1f, (t - 2300) / 400f));   // 위로 떠남
            if (t > 2200) dv.beam = Math.max(0, 1 - (t - 2200) / 200f);
            try { wm.updateViewLayout(dv, dl); } catch (Exception ignored) { }
            dv.invalidate();
            if (t == 0 || (t >= 1900 && t < 1950)) { }
            if (t < 2700) h.postDelayed(this, 16); else hideDrone();
        } }.run();
        h.postDelayed(() -> { if (running) then.run(); }, 1900);                // 스캔 끝나면 '연결됨' 알림
    }

    void hideDrone() { if (drone != null) { try { wm.removeView(drone); } catch (Exception ignored) { } drone = null; } }

    View badge;
    /** 캐릭터 머리 위에 크게: [기기 아이콘 + ✓ 연결됨 + 기기 이름] 말풍선. 톡 커졌다가 4초 뒤 사라짐. */
    void showBadge(String icon, String label) {
        hideBadge(); hideDrone();
        Bitmap b = null;
        try (InputStream in = getAssets().open("dev/" + icon + ".webp")) { b = BitmapFactory.decodeStream(in); } catch (Exception ignored) { }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(14), dp(10), dp(14), dp(12));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xF2FFFFFF); bg.setCornerRadius(dp(22)); bg.setStroke(dp(3), 0xFF7A5EC8); box.setBackground(bg);
        ImageView iv = new ImageView(this); iv.setImageBitmap(b); iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (b != null) iv.getDrawable().setFilterBitmap(false);
        int icSz = Math.max(dp(96), sizePx * 45 / 100);
        box.addView(iv, new LinearLayout.LayoutParams(icSz, icSz));
        TextView ok = AppUi.text(this, "✓ 연결됨", 20, true); ok.setTextColor(0xFF2E9E5B); box.addView(ok);
        TextView nm = AppUi.text(this, label, 14, false); nm.setMaxLines(1); nm.setEllipsize(android.text.TextUtils.TruncateAt.END); nm.setMaxWidth(dp(220)); box.addView(nm);
        WindowManager.LayoutParams bl = new WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        bl.gravity = Gravity.TOP | Gravity.START;
        box.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int bw = box.getMeasuredWidth(), bh = box.getMeasuredHeight();
        Rect a = area();
        bl.x = Math.max(a.left, Math.min(a.right - bw, lp.x + sizePx / 2 - bw / 2));
        bl.y = lp.y - bh + sizePx / 8; if (bl.y < a.top) bl.y = Math.min(a.bottom - bh, lp.y + sizePx);   // 위에 자리가 없으면 아래로
        try { wm.addView(box, bl); } catch (Exception e) { return; }
        badge = box;
        box.setScaleX(0.3f); box.setScaleY(0.3f); box.setAlpha(0f);
        box.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(260).setInterpolator(new android.view.animation.OvershootInterpolator()).start();
        final View me = box;
        h.postDelayed(() -> { if (badge == me) me.animate().alpha(0f).setDuration(400).withEndAction(this::hideBadge).start(); }, 4000);
    }

    void hideBadge() { if (badge != null) { try { wm.removeView(badge); } catch (Exception ignored) { } badge = null; } }

    void foreground() {
        NotificationManager n = getSystemService(NotificationManager.class);
        n.createNotificationChannel(new NotificationChannel("amelia", getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification no = new Notification.Builder(this, "amelia").setSmallIcon(android.R.drawable.star_on)
                .setContentTitle("아멜리아가 나와 있어요").setContentText("두 번 터치: 가로·세로 전환 / 세 번 터치: 메뉴")
                .setContentIntent(open).setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(31, no, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(31, no);
    }

    // ---------- 재생 ----------
    Bitmap frame(String name) {
        Bitmap b = cache.get(name);
        if (b == null) {
            try (InputStream in = getAssets().open("f/" + name + ".webp")) {
                b = BitmapFactory.decodeStream(in);
                if (b != null) cache.put(name, b);
            } catch (Exception ignored) { }
        }
        return b;
    }

    List<String> frames(String motion) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = motions.getJSONObject(ch).getJSONArray(motion);
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) { }
        return out;
    }

    void show(String name) { Bitmap b = frame(name); if (b != null && view != null) view.setImageBitmap(b); }

    /** 모션을 한 번 재생하고 끝나면 done. 다른 재생이 시작되면 token이 바뀌어 이전 재생은 멈춘다. */
    void play(String motion, int ms, Runnable done) {
        final int t = ++token;
        final List<String> fs = frames(motion);
        if (fs.isEmpty()) { if (done != null) h.post(done); return; }
        new Runnable() {
            int i = 0;
            @Override public void run() {
                if (t != token || !running) return;
                if (i >= fs.size()) { if (done != null) done.run(); return; }
                show(fs.get(i++));
                h.postDelayed(this, ms);
            }
        }.run();
    }

    /** 대기 2초 → 다음 행동(홀로그램/아메리카노 번갈아, 사이사이 무작위로 걷기). */
    void idle() {
        if (!running || exiting) return;
        view.setScaleX(1f);
        play("idle", Prefs.idleSec(this) * 1000 / 11, () -> {
            final int t = token;
            h.postDelayed(() -> {
                if (t != token || !running || exiting) return;
                String m = CYCLE[cycle++ % CYCLE.length];
                if (m.equals("walk")) walk();
                else play(m, m.equals("holo") ? 170 : 200, this::idle);
            }, 200);
        });
    }

    /** 걷기: 1.5초 동안 무작위 방향으로 걷다가 멈춤(왼쪽이면 좌우 반전). 화면 밖으로는 안 나감. */
    void walk() {
        final int t = ++token;
        walking = true;
        final List<String> fs = frames("walk");
        final int steps = WALK_TIME_MS / WALK_MS;
        Rect a = area();
        final float step = dp(9);
        int dirX = lp.x + sizePx / 2 < a.centerX() ? 1 : -1;              // 화면 가운데 쪽으로 가는 걸 우선
        if (rnd.nextInt(100) < 35) dirX = -dirX;
        final int dx = dirX;
        final float dy = (rnd.nextFloat() - 0.5f) * 0.6f;
        view.setScaleX(dx);
        new Runnable() {
            int i = 0;
            @Override public void run() {
                if (t != token || !running) { walking = false; return; }
                if (i >= steps) { walking = false; view.setScaleX(1f); idle(); return; }
                int ox = lp.x, oy = lp.y;
                lp.x += Math.round(dx * step); lp.y += Math.round(dy * step);
                keepInside();
                if (lp.x == ox && i > 0) { walking = false; view.setScaleX(1f); idle(); return; }   // 벽에 닿으면 멈춤
                if (!fs.isEmpty()) show(fs.get(i % fs.size()));
                i++;
                h.postDelayed(this, WALK_MS);
            }
        }.run();
    }

    // ---------- 회전 ----------
    boolean landscape() { Point s = screen(); return s.x > s.y; }

    /** 캐릭터가 화면(상태바·내비게이션바·카메라 구멍 제외) 밖으로 나가지 않게 쓰는 영역. */
    Rect area() {
        WindowMetrics m = wm.getCurrentWindowMetrics();
        Rect r = new Rect(m.getBounds());
        Insets in = m.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        r.left += in.left; r.top += in.top; r.right -= in.right; r.bottom -= in.bottom;
        return r;
    }

    Point screen() {
        Rect r = wm.getCurrentWindowMetrics().getBounds();
        return new Point(r.width(), r.height());
    }

    /** 더블터치: 시스템 회전 설정(자동 회전 끄고 방향 지정)으로 가로·세로를 바꾼다.
     *  안드로이드 기본 회전과 같아서, 가로를 지원하지 않는 홈 화면은 세로 그대로 있고 앱 화면만 돌아간다.
     *  '시스템 설정 변경' 허용이 필요(처음 한 번 사용자가 허용). 종료·회전 고정 풀기 때 원래 자동 회전 설정으로 되돌린다. */
    int savedAuto = -1;
    boolean toggleRotation() {
        if (!Settings.System.canWrite(this)) {
            Toast.makeText(this, "가로·세로 전환에는 '시스템 설정 변경' 허용이 필요해요", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:" + getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return false;
        }
        ContentResolver cr = getContentResolver();
        try {
            if (savedAuto < 0) savedAuto = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1);
            Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0);
            int cur = wm.getDefaultDisplay().getRotation();          // 지금 방향의 90° 옆으로(폴드 펼침처럼 기본이 가로인 화면도 맞게)
            Settings.System.putInt(cr, Settings.System.USER_ROTATION, (cur == Surface.ROTATION_0 || cur == Surface.ROTATION_180) ? Surface.ROTATION_90 : Surface.ROTATION_0);
            return true;
        } catch (Exception e) { return false; }
    }

    void releaseRotation() {
        if (savedAuto >= 0 && Settings.System.canWrite(this)) {
            try {
                ContentResolver cr = getContentResolver();
                Settings.System.putInt(cr, Settings.System.USER_ROTATION, Surface.ROTATION_0);
                Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, savedAuto);
            } catch (Exception ignored) { }
        }
        savedAuto = -1;
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if (!running || view == null) return;
        closeMenu();
        keepInside();
        h.postDelayed(this::keepInside, 400);
        String want = landscape() ? "r41" : "amelia";
        if (!want.equals(ch) && !exiting) swapIn(want);
    }

    /** 더블터치 → 지금 방향에서 먼저 넘어져 바닥 차원문으로 빨려 들어감 → 다 사라지면 그때 가로·세로 전환. */
    void swapOut() {
        swapping = true; walking = false; pinched = false;
        view.setScaleX(1f);
        play("swapout", 100, () -> {
            boolean rot = toggleRotation();
            final String other = ch.equals("amelia") ? "r41" : "amelia";
            final int t = token;
            // 화면이 돌아가면 onConfigurationChanged가 바로 나오게 한다. 홈 화면처럼 안 돌아가는 곳이면 잠시 뒤 그 자리에서 바뀐 캐릭터가 나옴.
            h.postDelayed(() -> { if (t == token && swapping && running) swapIn(other); }, rot ? 1200 : 300);
        });
    }

    /** 방향이 바뀌는 순간 다른 차원 아멜리아가 차원문에서 나옴(폰을 직접 돌렸을 때도 이것만 재생). */
    void swapIn(String next) {
        ch = next; swapping = true; walking = false; pinched = false;
        view.setScaleX(1f);
        play("swapin", 100, () -> { swapping = false; idle(); });
    }

    /** 길게 누르고 있으면 볼을 꼬집힌 채 버둥거림(손을 뗄 때까지 반복, 누른 채 움직이면 같이 끌려옴). */
    void pinchStart() {
        if (!running || exiting || swapping || !Prefs.pinch(this)) return;
        pinched = true; walking = false;
        view.setScaleX(1f);
        final int t = ++token;
        final List<String> fs = frames("pinch");
        if (fs.isEmpty()) return;
        new Runnable() {
            int i = 0;
            @Override public void run() {
                if (t != token || !running || !pinched) return;
                show(fs.get(i < fs.size() ? i : 1 + (i - 1) % Math.max(1, fs.size() - 1)));
                i++;
                h.postDelayed(this, 120);
            }
        }.run();
    }

    void pinchEnd() {   // 손을 놓으면 툭 떨어져 엉덩방아 → 볼 문지르며 일어남 → 대기
        if (!pinched) return;
        pinched = false; view.setScaleX(1f);
        play("pinchend", 120, this::idle);
    }

    void keepInside() {
        Rect a = area();
        lp.x = Math.max(a.left, Math.min(a.right - sizePx, lp.x));
        lp.y = Math.max(a.top, Math.min(a.bottom - sizePx, lp.y));
        update();
    }

    void update() { if (view != null && view.isAttachedToWindow()) wm.updateViewLayout(view, lp); }

    // ---------- 터치 ----------
    final Runnable judgeTaps = () -> { if (taps == 2) onDouble(); taps = 0; };
    final Runnable holdCheck = this::pinchStart;

    boolean touch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX(); downY = e.getRawY(); startX = lp.x; startY = lp.y; downAt = e.getEventTime(); dragging = false; lastMoveX = downX;
                h.removeCallbacks(holdCheck); h.postDelayed(holdCheck, 450);
                return true;
            case MotionEvent.ACTION_MOVE:
                float mx = e.getRawX() - downX, my = e.getRawY() - downY;
                if (!pinched && Math.hypot(mx, my) > dp(10)) h.removeCallbacks(holdCheck);
                if (!dragging && e.getEventTime() - downAt > 350 && Math.hypot(mx, my) > dp(6)) dragging = true;
                if (dragging) {
                    lp.x = startX + Math.round(mx); lp.y = startY + Math.round(my); keepInside();
                    float vx = e.getRawX() - lastMoveX;          // 끄는 방향에 따라 좌우 반전(오른쪽=그대로, 왼쪽=반전)
                    if (Math.abs(vx) > dp(4)) { view.setScaleX(vx > 0 ? 1f : -1f); lastMoveX = e.getRawX(); }
                }
                return true;
            case MotionEvent.ACTION_UP:
                h.removeCallbacks(holdCheck);
                if (pinched) { dragging = false; pinchEnd(); return true; }
                if (dragging) { dragging = false; view.setScaleX(1f); return true; }
                if (Math.hypot(e.getRawX() - downX, e.getRawY() - downY) > dp(12)) return true;
                taps++;
                h.removeCallbacks(judgeTaps);
                if (taps >= 3) { taps = 0; showMenu(); }
                else h.postDelayed(judgeTaps, 320);
                return true;
            case MotionEvent.ACTION_CANCEL:
                h.removeCallbacks(holdCheck); dragging = false; pinchEnd(); return true;
        }
        return false;
    }

    void onDouble() { if (!swapping && !exiting) swapOut(); }

    void showMenu() {
        closeMenu();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(16));
        TextView title = new TextView(this); title.setText(R.string.app_name); title.setTextSize(20); title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title);
        add(box, "설정", () -> startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        add(box, "회전 원래대로", this::releaseRotation);
        add(box, "종료", this::exit);
        AlertDialog d = new AlertDialog.Builder(this).setView(box).create();
        d.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        d.setOnDismissListener(x -> { if (menu == d) menu = null; });
        menu = d; d.show();
    }

    void add(LinearLayout box, String text, Runnable task) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false);
        b.setOnClickListener(x -> { closeMenu(); task.run(); });
        box.addView(b, new LinearLayout.LayoutParams(-1, -2));
    }

    void closeMenu() { if (menu != null) { AlertDialog d = menu; menu = null; d.dismiss(); } }

    // ---------- 종료 ----------
    /** 퇴장 모션 후 닫기. */
    public void exit() {
        if (!running || exiting) return;
        exiting = true; walking = false;
        view.setScaleX(1f);
        releaseRotation();
        play("exit", FRAME_MS, this::finish);
    }

    void finish() {
        running = false; token++;
        try { unregisterReceiver(btRx); } catch (Exception ignored) { }
        hideBadge();
        h.removeCallbacksAndMessages(null);
        closeMenu(); releaseRotation();
        if (view != null) { try { wm.removeView(view); } catch (Exception ignored) { } view = null; }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        MainActivity.refresh();
    }

    public void resize() {
        if (!running || view == null) return;
        sizePx = dp(Prefs.size(this)); lp.width = lp.height = sizePx; keepInside();
    }

    @Override public void onDestroy() {
        if (running) { running = false; token++; h.removeCallbacksAndMessages(null); releaseRotation();
            if (view != null) try { wm.removeView(view); } catch (Exception ignored) { } }
        current = null;
        super.onDestroy();
    }

    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
