package com.oddfactory.amelia;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** 블루투스 작업 드론: 위에서 날아와 아래로 넓어지는 삼각형 빔을 쏘며 스캔(빔 안에 가로 스캔선이 위→아래로 흐름). */
final class DroneView extends View {
    Bitmap drone;
    float beam = 0f;        // 0~1 빔 길이
    float scan = 0f;        // 0~1 스캔선 위치
    float hover = 0f;       // 위아래 둥실
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), bp = new Paint(Paint.FILTER_BITMAP_FLAG);
    int droneSize, beamLen;
    float lens = 0.80f; int beamTop = 0xCCC77DFF, beamBot = 0x33B07CFF, edge = 0xFFE2B8FF;   // R41(보라) 기본, 아멜리아는 하늘색

    DroneView(Context c, Bitmap d, int droneSize, int beamLen) {
        super(c); drone = d; this.droneSize = droneSize; this.beamLen = beamLen; bp.setFilterBitmap(false);
    }

    @Override protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, top = droneSize * 0.15f + hover;
        float lensY = top + droneSize * lens;
        if (beam > 0) {
            float len = beamLen * beam, half = beamLen * 0.42f * beam;
            Path tri = new Path(); tri.moveTo(cx, lensY); tri.lineTo(cx - half, lensY + len); tri.lineTo(cx + half, lensY + len); tri.close();
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, lensY, 0, lensY + len, beamTop, beamBot, Shader.TileMode.CLAMP));
            c.drawPath(tri, p); p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2, droneSize / 40f)); p.setColor(edge); c.drawPath(tri, p);
            // 스캔선
            for (int k = 0; k < 2; k++) {
                float t = (scan + k * 0.5f) % 1f, y = lensY + len * t, w = half * t;
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(3, droneSize / 25f)); p.setColor(0xEEFFFFFF);
                c.drawLine(cx - w, y, cx + w, y, p);
            }
        }
        if (drone != null) {
            Rect src = new Rect(0, 0, drone.getWidth(), drone.getHeight());
            RectF dst = new RectF(cx - droneSize / 2f, top, cx + droneSize / 2f, top + droneSize);
            c.drawBitmap(drone, src, dst, bp);
        }
    }
}
