package dev.blackholexp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BlackHoleXpClient implements ClientModInitializer {

    private static final float SHIFT = 5f;

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

        HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, (HudElement vanilla) -> (ctx, tick) ->
                BlackHoleHud.renderHealth(ctx, MinecraftClient.getInstance()));

        HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, (HudElement vanilla) -> (ctx, tick) ->
                BlackHoleHud.renderFood(ctx, MinecraftClient.getInstance()));

        // Armor, udara, dan hati tunggangan ikut naik supaya tidak menabrak hati baru.
        HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, BlackHoleXpClient::shifted);
        HudElementRegistry.replaceElement(VanillaHudElements.AIR_BAR, BlackHoleXpClient::shifted);
        HudElementRegistry.replaceElement(VanillaHudElements.MOUNT_HEALTH, BlackHoleXpClient::shifted);

        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
                Identifier.of("blackholexpbar", "hotbar_frame"),
                (ctx, tick) -> BlackHoleHud.renderHotbarFrame(ctx, MinecraftClient.getInstance()));
    }

    private static HudElement shifted(HudElement vanilla) {
        return (ctx, tick) -> {
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(0f, -SHIFT);
            vanilla.render(ctx, tick);
            ctx.getMatrices().popMatrix();
        };
    }

    static final class BlackHoleHud {

    // Warna: ungu di level 1, makin naik makin oranye, berhenti di oranye (level 100+).
    private static final int[] PURPLE = {124, 58, 237};
    private static final int[] ORANGE = {255, 138, 61};
    private static final int[] DEEP = {91, 33, 182};
    private static final int[] ORB = {184, 240, 58};
    private static final int MAX_LEVEL_COLOR = 100;

    private static final int[] WHITE = {255, 255, 255};

    // Bar putus-putus: 11 kotak, makin ke arah black hole makin kecil.
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
        int barTop = sh - 33;
        int cx = x0 + HOLE_DX;
        int cy = barTop + BAR_H / 2;

        int level = player.experienceLevel;
        float progress = player.experienceProgress;
        trackChanges(level, progress, x0, y0, sw, sh, now);

        int[] main = levelColor(level);
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
            ctx.drawCenteredTextWithShadow(mc.textRenderer, String.valueOf(level), sw / 2, sh - 44, argb(255, c));
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

    // front=false gambar separuh disk di belakang lubang, front=true separuh di depan.
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

    private static int[] levelColor(int level) {
        float lt = Math.min(1f, Math.max(0f, (level - 1) / (float) (MAX_LEVEL_COLOR - 1)));
        return lerp(PURPLE, ORANGE, lt);
    }

    // ---------- Hati, lapar, dan bingkai hotbar ----------

    private record Pal(int o, int m, int h, int d, int b) {}
    private record Rect(int x, int y, int w, int h, char c) {}

    private static final Pal HEART_NORMAL = new Pal(0xFF3B1466, 0xFFE0245E, 0xFFFF9FC4, 0xFF8E1450, 0);
    private static final Pal HEART_GOLD = new Pal(0xFF4A2A08, 0xFFFFB347, 0xFFFFE3A0, 0xFFC26A1A, 0);
    private static final Pal HEART_POISON = new Pal(0xFF1E3A12, 0xFF7BD34A, 0xFFD5F5A8, 0xFF3F7A22, 0);
    private static final Pal HEART_WITHER = new Pal(0xFF0A0810, 0xFF3C3547, 0xFF6C6580, 0xFF1B1724, 0);
    private static final Pal ICON_EMPTY = new Pal(0xFF3A2F66, 0xFF07050E, 0xFF07050E, 0xFF07050E, 0xFF2A2340);
    private static final Pal FOOD_NORMAL = new Pal(0xFF3B1466, 0xFFFF8A3D, 0xFFFFC26B, 0xFFB5501A, 0xFFE9DFFF);
    private static final Pal FOOD_HUNGER = new Pal(0xFF2A3012, 0xFF8AA24A, 0xFFC4D67E, 0xFF55662A, 0xFFCFCFA8);

    private static final String[] HEART_BMP = {
            ".oo...oo.",
            "oMMo.oMMo",
            "oHMMoMMMo",
            "oHMMMMMMo",
            "oMMMMMMDo",
            ".oMMMMDo.",
            "..oMMDo..",
            "...oDo...",
            "....o...."
    };

    private static final String[] FOOD_BMP = {
            "....ooo..",
            "...oMMMo.",
            "..oMHMMMo",
            "..oMMMMMo",
            "..oMMMMDo",
            "..oMMMDo.",
            ".oBoDDo..",
            "oBBo.oo..",
            ".oo......"
    };

    private static final List<Rect> HEART_RECTS = rects(HEART_BMP);
    private static final List<Rect> FOOD_RECTS = rects(FOOD_BMP);

    private static List<Rect> rects(String[] bmp) {
        List<Rect> out = new ArrayList<>();
        List<int[]> open = new ArrayList<>();
        for (int y = 0; y <= bmp.length; y++) {
            List<int[]> runs = new ArrayList<>();
            if (y < bmp.length) {
                String r = bmp[y];
                int x = 0;
                while (x < r.length()) {
                    char c = r.charAt(x);
                    int e = x;
                    while (e < r.length() && r.charAt(e) == c) e++;
                    if (c != '.') runs.add(new int[]{x, e - x, c});
                    x = e;
                }
            }
            List<int[]> next = new ArrayList<>();
            for (int[] o : open) {
                int[] match = null;
                for (int[] r : runs) {
                    if (r[0] == o[0] && r[1] == o[1] && r[2] == o[3]) {
                        match = r;
                        break;
                    }
                }
                if (match != null) {
                    runs.remove(match);
                    next.add(o);
                } else {
                    out.add(new Rect(o[0], o[2], o[1], y - o[2], (char) o[3]));
                }
            }
            for (int[] r : runs) next.add(new int[]{r[0], r[1], y, r[2]});
            open = next;
        }
        return out;
    }

    private static int palColor(Pal p, char c) {
        switch (c) {
            case 'o': return p.o();
            case 'M': return p.m();
            case 'H': return p.h();
            case 'D': return p.d();
            default: return p.b();
        }
    }

    // Kolom [from, to) pakai palet penuh, sisanya palet kosong.
    private static void drawIcon(DrawContext ctx, int ix, int iy, List<Rect> rs, Pal full, int from, int to) {
        for (Rect r : rs) {
            int xs = r.x();
            int xe = r.x() + r.w();
            int a = Math.max(xs, from);
            int b = Math.min(xe, to);
            if (a < b) ctx.fill(ix + a, iy + r.y(), ix + b, iy + r.y() + r.h(), palColor(full, r.c()));
            int le = Math.min(xe, from);
            if (xs < le) ctx.fill(ix + xs, iy + r.y(), ix + le, iy + r.y() + r.h(), palColor(ICON_EMPTY, r.c()));
            int rs2 = Math.max(xs, to);
            if (rs2 < xe) ctx.fill(ix + rs2, iy + r.y(), ix + xe, iy + r.y() + r.h(), palColor(ICON_EMPTY, r.c()));
        }
    }

    public static void renderHealth(DrawContext ctx, MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        long now = System.currentTimeMillis();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int x0 = sw / 2 - 91;
        int baseY = sh - 44;

        int hp = (int) Math.ceil(p.getHealth());
        int absHp = (int) Math.ceil(p.getAbsorptionAmount());
        int maxHearts = (int) Math.ceil(p.getMaxHealth() / 2f);
        int absHearts = (int) Math.ceil(p.getAbsorptionAmount() / 2f);
        int total = maxHearts + absHearts;
        int rows = (int) Math.ceil(total / 10.0);
        int rowH = Math.max(10 - (rows - 2), 3);

        Pal base = HEART_NORMAL;
        if (p.hasStatusEffect(StatusEffects.WITHER)) base = HEART_WITHER;
        else if (p.hasStatusEffect(StatusEffects.POISON)) base = HEART_POISON;

        for (int i = total - 1; i >= 0; i--) {
            int row = i / 10;
            int col = i % 10;
            int x = x0 + col * 8;
            int y = baseY - row * rowH;
            if (hp <= 4 && ((now / 50 + i * 13L) % 3 == 0)) y += 1;

            if (i < maxHearts) {
                int left = hp - i * 2;
                Pal pal = base;
                if (base == HEART_NORMAL) {
                    float tw = (float) (0.5 + 0.5 * Math.sin(now / 350.0 + i * 0.8));
                    pal = new Pal(base.o(), base.m(), argb0(lerp(new int[]{255, 159, 196}, WHITE, tw * 0.7f)), base.d(), 0);
                }
                if (left >= 2) drawIcon(ctx, x, y, HEART_RECTS, pal, 0, 9);
                else if (left == 1) drawIcon(ctx, x, y, HEART_RECTS, pal, 0, 5);
                else drawIcon(ctx, x, y, HEART_RECTS, pal, 0, 0);
            } else {
                int left = absHp - (i - maxHearts) * 2;
                if (left >= 2) drawIcon(ctx, x, y, HEART_RECTS, HEART_GOLD, 0, 9);
                else if (left == 1) drawIcon(ctx, x, y, HEART_RECTS, HEART_GOLD, 0, 5);
            }
        }
    }

    public static void renderFood(DrawContext ctx, MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int x0 = sw / 2 - 91;
        int baseY = sh - 44;
        int food = p.getHungerManager().getFoodLevel();
        Pal pal = p.hasStatusEffect(StatusEffects.HUNGER) ? FOOD_HUNGER : FOOD_NORMAL;

        for (int i = 0; i < 10; i++) {
            int x = x0 + 173 - 8 * i;
            int left = food - i * 2;
            if (left >= 2) drawIcon(ctx, x, baseY, FOOD_RECTS, pal, 0, 9);
            else if (left == 1) drawIcon(ctx, x, baseY, FOOD_RECTS, pal, 4, 9);
            else drawIcon(ctx, x, baseY, FOOD_RECTS, pal, 0, 0);
        }
    }

    // Bingkai tipis di atas hotbar. Tekstur slot hotbar tidak disentuh.
    public static void renderHotbarFrame(DrawContext ctx, MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        long now = System.currentTimeMillis();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int x0 = sw / 2 - 91;
        int top = sh - 23;
        int[] main = levelColor(p.experienceLevel);
        float shift = (now % 2600L) / 2600f;

        for (int i = 0; i < 182; i += 3) {
            float t = i / 181f;
            float d = Math.abs(t - shift);
            d = Math.min(d, 1f - d);
            float b = Math.max(0f, 1f - d * 7f);
            int[] c = lerp(lerp(DEEP, main, t), WHITE, 0.55f * b);
            int w = Math.min(3, 182 - i);
            ctx.fill(x0 + i, top, x0 + i + w, top + 1, argb(235, c));
        }
    }

    private static int argb0(int[] c) {
        return 0xFF000000 | (c[0] << 16) | (c[1] << 8) | c[2];
    }
    }
}
