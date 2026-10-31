package dev.blackholexp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BlackHoleXpClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HudElementRegistry.replaceElement(VanillaHudElements.INFO_BAR, (HudElement vanilla) -> (ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (BlackHoleHud.isActive(mc)) {
                BlackHoleHud.render(ctx, mc);
            } else {
                vanilla.render(ctx, tick);
            }
        });

        HudElementRegistry.replaceElement(VanillaHudElements.EXPERIENCE_LEVEL, (HudElement vanilla) -> (ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!BlackHoleHud.isActive(mc)) {
                vanilla.render(ctx, tick);
            }
        });
    }

    static final class BlackHoleHud {

    private static final int[] PURPLE = {124, 58, 237};
    private static final int[] ORANGE = {255, 138, 61};
    private static final int[] DEEP = {91, 33, 182};
    private static final int[] ORB = {184, 240, 58};
    private static final int MAX_LEVEL_COLOR = 100;

    private static final int[] WHITE = {255, 255, 255};

    private static final int SEGS = 11;
    private static final int SEG_W = 12;
    private static final int SEG_GAP = 3;
    private static final int BAR_H = 7;
    private static final int HOLE_DX = 194;

    private static final Random RNG = new Random();
    private static final List<Particle> PARTICLES = new ArrayList<>();
    private static final List<Wave> WAVES = new ArrayList<>();

    private static float lastTotal = -1f;
    private static int lastLevel = -1;
    private static long levelUpAt = -100000L;
    private static float pulse = 0f;

    private record Particle(float sx, float sy, long born, int dur) {}
    private record Wave(long born, int dur, boolean flash) {}

    private BlackHoleHud() {}

    public static boolean isActive(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) return false;
        if (!mc.interactionManager.hasExperienceBar()) return false;
        return p.getJumpingMount() == null;
    }

    public static void render(DrawContext ctx, MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        long now = System.currentTimeMillis();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        int x0 = sw / 2 - 91;
        int y0 = sh - 32 + 3;
        int barTop = y0 - 1;
        int cx = x0 + HOLE_DX;
        int cy = barTop + BAR_H / 2;

        int level = player.experienceLevel;
        float progress = player.experienceProgress;
        trackChanges(level, progress, x0, y0, sw, sh, now);

        float lt = Math.min(1f, Math.max(0f, (level - 1) / (float) (MAX_LEVEL_COLOR - 1)));
        int[] main = lerp(PURPLE, ORANGE, lt);
        int[] light = lerp(main, new int[]{255, 255, 255}, 0.45f);

        drawBar(ctx, x0, barTop, progress, main, now);
        drawDisk(ctx, cx, cy, main, light, now, false);
        drawCore(ctx, cx, cy, light, now);
        drawDisk(ctx, cx, cy, main, light, now, true);
        drawParticles(ctx, cx, cy, main, now);
        drawWaves(ctx, cx, cy, main, now);

        if (level > 0) {
            boolean popping = now - levelUpAt < 450;
            int[] c = popping ? new int[]{255, 255, 255} : main;
            ctx.drawCenteredTextWithShadow(mc.textRenderer, String.valueOf(level), sw / 2, sh - 40, argb(255, c));
        }
    }

    private static void trackChanges(int level, float progress, int x0, int y0, int sw, int sh, long now) {
        float total = level + progress;
        if (lastLevel < 0) {
            lastLevel = level;
            lastTotal = total;
            return;
        }
        if (level < lastLevel) {
            lastLevel = level;
            lastTotal = total;
            PARTICLES.clear();
            return;
        }
        if (level > lastLevel) {
            levelUpAt = now;
            WAVES.add(new Wave(now, 600, true));
            for (int i = 0; i < 3; i++) WAVES.add(new Wave(now + i * 160L, 1100, false));
            spawn(14, x0, y0, now);
        } else if (total > lastTotal + 0.0001f) {
            float d = total - lastTotal;
            int n = Math.max(3, Math.min(10, (int) (d * 60f) + 3));
            spawn(n, x0, y0, now);
        }
        lastLevel = level;
        lastTotal = total;
    }

    private static void spawn(int n, int x0, int y0, long now) {
        for (int i = 0; i < n && PARTICLES.size() < 120; i++) {
            boolean top = RNG.nextBoolean();
            float sx = x0 - 10 + RNG.nextFloat() * 200f;
            float sy = top ? y0 - 70 + RNG.nextFloat() * 50f : y0 + 18 + RNG.nextFloat() * 14f;
            PARTICLES.add(new Particle(sx, sy, now + RNG.nextInt(450), 800 + RNG.nextInt(500)));
        }
    }

    private static void drawBar(DrawContext ctx, int x0, int top, float progress, int[] main, long now) {
        float pos = Math.max(0f, Math.min(1f, progress)) * SEGS;
        int full = (int) pos;
        float frac = pos - full;
        float shift = (now % 900L) / 900f * 12f;

        for (int i = 0; i < SEGS; i++) {
            int sx = x0 + i * (SEG_W + SEG_GAP);
            float t = i / (float) (SEGS - 1);
            int h = BAR_H - Math.round(t * 4f);
            int fy = top + (BAR_H - h) / 2;
            boolean filled = i < full;

            if (!filled) {
                ctx.fill(sx - 1, top - 1, sx + SEG_W + 1, top + BAR_H + 1, 0xFF3A2F66);
                ctx.fill(sx, top, sx + SEG_W, top + BAR_H, 0xFF000000);
            }

            int fillW = filled ? SEG_W : (i == full ? Math.round(frac * SEG_W) : 0);
            for (int c = 0; c < fillW; c++) {
                float wave = ((sx + c - shift) % 12f + 12f) % 12f;
                int[] col = lerp(DEEP, main, t);
                if (wave < 2f) col = lerp(col, WHITE, 0.28f);
                ctx.fill(sx + c, fy, sx + c + 1, fy + h, argb(255, col));
            }
        }
    }

    private static void drawDisk(DrawContext ctx, int cx, int cy, int[] main, int[] light, long now, boolean front) {
        double tilt = Math.toRadians(-12);
        double ct = Math.cos(tilt), st = Math.sin(tilt);
        double spin = now * 0.004;
        double[] rxs = {19, 14.5, 10.5};
        double[] rys = {5.2, 3.9, 2.8};
        int[] ns = {84, 64, 48};
        double[] speeds = {1.0, -1.5, 2.2};
        for (int ring = 0; ring < 3; ring++) {
            for (int k = 0; k < ns[ring]; k++) {
                double a = k * Math.PI * 2 / ns[ring];
                double ey = Math.sin(a) * rys[ring];
                boolean isFront = ey >= 0;
                if (isFront != front) continue;
                double ex = Math.cos(a) * rxs[ring];
                int px = cx + (int) Math.round(ex * ct - ey * st);
                int py = cy + (int) Math.round(ex * st + ey * ct);
                double g = 0.5 + 0.5 * Math.cos(a - spin * speeds[ring]);
                double glow = g * g * g;
                double m = 0.25 + 0.75 * glow;
                int[] c = lerp(main, light, (float) m);
                if (ring == 2) c = lerp(c, WHITE, (float) (0.55 * glow));
                int size = glow > 0.5 ? 2 : 1;
                ctx.fill(px, py, px + size, py + size, argb(ring == 1 ? 225 : 255, c));
            }
        }
    }

    private static void drawCore(DrawContext ctx, int cx, int cy, int[] light, long now) {
        pulse *= 0.94f;
        double breathe = 0.5 + 0.5 * Math.sin(now / 318.0);
        double ringR = 8.0 + breathe * 0.6 + pulse * 2.5;
        int a = (int) Math.min(255, 170 + breathe * 80);
        int pts = 56;
        for (int k = 0; k < pts; k++) {
            double ang = k * Math.PI * 2 / pts;
            int px = cx + (int) Math.round(Math.cos(ang) * ringR);
            int py = cy + (int) Math.round(Math.sin(ang) * ringR);
            ctx.fill(px, py, px + 1, py + 1, argb(a, lerp(light, WHITE, 0.35f)));
        }
        double haloR = ringR + 2.4;
        int hpts = 72;
        for (int k = 0; k < hpts; k++) {
            double ang = k * Math.PI * 2 / hpts;
            double s = 0.5 + 0.5 * Math.cos(ang - now * 0.003);
            int ha = (int) (35 + 110 * s * s);
            int px = cx + (int) Math.round(Math.cos(ang) * haloR);
            int py = cy + (int) Math.round(Math.sin(ang) * haloR);
            ctx.fill(px, py, px + 1, py + 1, argb(ha, light));
        }
        fillCircle(ctx, cx, cy, 7, 0xFF000000);
    }

    private static void drawParticles(DrawContext ctx, int cx, int cy, int[] main, long now) {
        PARTICLES.removeIf(q -> {
            float p = (now - q.born()) / (float) q.dur();
            if (p >= 1f) {
                pulse = Math.min(pulse + 0.3f, 1.2f);
                return true;
            }
            if (p <= 0f) return false;
            float dx = cx - q.sx();
            float dy = cy - q.sy();
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1f) len = 1f;
            float e = p * p;
            float off = (float) Math.sin(p * Math.PI) * 14f * (1f - p);
            float x = q.sx() + dx * e + (-dy / len) * off;
            float y = q.sy() + dy * e + (dx / len) * off;
            int size = p < 0.6f ? 3 : 2;
            int[] c = lerp(ORB, main, p);
            ctx.fill((int) x, (int) y, (int) x + size, (int) y + size, argb(255, c));
            return false;
        });
    }

    private static void drawWaves(DrawContext ctx, int cx, int cy, int[] main, long now) {
        WAVES.removeIf(w -> {
            float p = (now - w.born()) / (float) w.dur();
            if (p >= 1f) return true;
            if (p < 0f) return false;
            if (w.flash()) {
                fillCircle(ctx, cx, cy, (int) (8 + 26 * p), argb((int) (200 * (1f - p)), new int[]{255, 255, 255}));
            } else {
                float q = 1f - (1f - p) * (1f - p);
                double r = 10 + 60 * q;
                int a = (int) (230 * (1f - p));
                int pts = Math.max(24, (int) (r * 2.2));
                for (int k = 0; k < pts; k++) {
                    double ang = k * Math.PI * 2 / pts;
                    int px = cx + (int) Math.round(Math.cos(ang) * r);
                    int py = cy + (int) Math.round(Math.sin(ang) * r);
                    ctx.fill(px, py, px + 1, py + 1, argb(a, main));
                }
            }
            return false;
        });
    }

    private static void fillCircle(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt((double) r * r - (double) dy * dy);
            ctx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static int[] lerp(int[] a, int[] b, float t) {
        return new int[]{
                Math.round(a[0] + (b[0] - a[0]) * t),
                Math.round(a[1] + (b[1] - a[1]) * t),
                Math.round(a[2] + (b[2] - a[2]) * t)
        };
    }

    private static int argb(int alpha, int[] c) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (c[0] << 16) | (c[1] << 8) | c[2];
    }
    }
}
