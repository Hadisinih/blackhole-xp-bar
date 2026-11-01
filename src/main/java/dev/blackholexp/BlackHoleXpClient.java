package dev.blackholexp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Arm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

public class BlackHoleXpClient implements ClientModInitializer {

    private static final float SHIFT = 11f;

    @Override
    public void onInitializeClient() {
        Cfg.load();

        // Crosshair kecil bertema; di mode spectator atau kalau dimatikan, pakai bawaan game.
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, (HudElement vanilla) -> (ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!Cfg.cross() || (mc.player != null && mc.player.isSpectator())) {
                vanilla.render(ctx, tick);
                return;
            }
            BlackHoleHud.renderCrosshair(ctx, mc);
        });

        HudElementRegistry.replaceElement(VanillaHudElements.INFO_BAR, (HudElement vanilla) -> (ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            BlackHoleHud.update(mc);
            if (BlackHoleHud.useCustomBar(mc)) {
                BlackHoleHud.render(ctx, mc);
            } else {
                vanilla.render(ctx, tick);
            }
        });

        HudElementRegistry.replaceElement(VanillaHudElements.EXPERIENCE_LEVEL, (HudElement vanilla) -> (ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!BlackHoleHud.useCustomBar(mc)) {
                vanilla.render(ctx, tick);
            }
        });

        HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, (HudElement vanilla) -> (ctx, tick) -> {
            if (Cfg.hud()) {
                BlackHoleHud.renderHealth(ctx, MinecraftClient.getInstance());
            } else {
                shifted(vanilla, 0f, lift()).render(ctx, tick);
            }
        });

        HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, (HudElement vanilla) -> (ctx, tick) -> {
            if (Cfg.hud()) {
                BlackHoleHud.renderFood(ctx, MinecraftClient.getInstance());
            } else {
                shifted(vanilla, 0f, lift()).render(ctx, tick);
            }
        });

        // Armor ikut hati ke kiri, udara dan hati tunggangan ikut lapar ke kanan, semuanya naik.
        HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, (HudElement v) -> (ctx, tick) ->
                shifted(v, Cfg.hud() ? -13f : 0f, lift()).render(ctx, tick));
        HudElementRegistry.replaceElement(VanillaHudElements.AIR_BAR, (HudElement v) -> (ctx, tick) ->
                shifted(v, Cfg.hud() ? 13f : 0f, lift()).render(ctx, tick));
        HudElementRegistry.replaceElement(VanillaHudElements.MOUNT_HEALTH, (HudElement v) -> (ctx, tick) ->
                shifted(v, Cfg.hud() ? 13f : 0f, lift()).render(ctx, tick));

        // Nama item yang dipegang ikut naik di survival supaya tidak menimpa armor.
        HudElementRegistry.replaceElement(VanillaHudElements.HELD_ITEM_TOOLTIP, (HudElement vanilla) -> (ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            boolean bars = mc.player != null && !mc.player.isCreative() && !mc.player.isSpectator();
            if (bars && lift() > 0f) {
                shifted(vanilla, 0f, lift()).render(ctx, tick);
            } else {
                vanilla.render(ctx, tick);
            }
        });

        // Hotbar diganti total dengan versi bertema black hole.
        HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, (HudElement vanilla) -> (ctx, tick) -> {
            if (Cfg.bar()) {
                BlackHoleHud.renderHotbar(ctx, MinecraftClient.getInstance());
            } else {
                vanilla.render(ctx, tick);
            }
        });
    }

    // Seberapa jauh hati, lapar, armor, dan udara diangkat ke atas supaya tidak menimpa bar XP kita.
    private static float lift() {
        return (Cfg.hud() || Cfg.xp()) ? SHIFT : 0f;
    }

    private static HudElement shifted(HudElement vanilla, float dx, float dy) {
        return (ctx, tick) -> {
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(dx, -dy);
            vanilla.render(ctx, tick);
            ctx.getMatrices().popMatrix();
        };
    }

    // ---------- Pengaturan (bisa dibuka dari Mod Menu) ----------

    public static final class Cfg {
        public static boolean enabled = true;
        public static boolean xpBar = true;
        public static boolean locator = true;
        public static boolean stats = true;
        public static boolean hotbar = true;
        public static boolean crosshair = true;
        public static boolean sword = true;
        public static boolean levelFx = true;
        public static boolean xpParticles = true;

        static final String[] NAMES = {
                "Bar XP black hole", "Penunjuk arah petir", "Hati & lapar", "Hotbar",
                "Crosshair", "Indikator pedang", "Efek naik level", "Partikel serap XP"
        };

        static boolean xp() { return enabled && xpBar; }
        static boolean loc() { return enabled && locator; }
        static boolean hud() { return enabled && stats; }
        static boolean bar() { return enabled && hotbar; }
        static boolean cross() { return enabled && crosshair; }
        static boolean blade() { return enabled && sword; }
        static boolean fx() { return enabled && levelFx; }
        static boolean orbs() { return enabled && xpParticles; }

        static boolean get(int i) {
            switch (i) {
                case 0: return xpBar;
                case 1: return locator;
                case 2: return stats;
                case 3: return hotbar;
                case 4: return crosshair;
                case 5: return sword;
                case 6: return levelFx;
                default: return xpParticles;
            }
        }

        static void set(int i, boolean v) {
            switch (i) {
                case 0: xpBar = v; break;
                case 1: locator = v; break;
                case 2: stats = v; break;
                case 3: hotbar = v; break;
                case 4: crosshair = v; break;
                case 5: sword = v; break;
                case 6: levelFx = v; break;
                default: xpParticles = v; break;
            }
        }

        private static Path file() {
            return FabricLoader.getInstance().getConfigDir().resolve("blackholexpbar.properties");
        }

        private static boolean flag(Properties p, String key, boolean def) {
            String v = p.getProperty(key);
            return v == null ? def : Boolean.parseBoolean(v.trim());
        }

        public static void load() {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(file())) {
                p.load(in);
            } catch (IOException | RuntimeException e) {
                return;
            }
            enabled = flag(p, "enabled", true);
            xpBar = flag(p, "xpBar", true);
            locator = flag(p, "locator", true);
            stats = flag(p, "stats", true);
            hotbar = flag(p, "hotbar", true);
            crosshair = flag(p, "crosshair", true);
            sword = flag(p, "sword", true);
            levelFx = flag(p, "levelFx", true);
            xpParticles = flag(p, "xpParticles", true);
        }

        public static void save() {
            Properties p = new Properties();
            p.setProperty("enabled", String.valueOf(enabled));
            p.setProperty("xpBar", String.valueOf(xpBar));
            p.setProperty("locator", String.valueOf(locator));
            p.setProperty("stats", String.valueOf(stats));
            p.setProperty("hotbar", String.valueOf(hotbar));
            p.setProperty("crosshair", String.valueOf(crosshair));
            p.setProperty("sword", String.valueOf(sword));
            p.setProperty("levelFx", String.valueOf(levelFx));
            p.setProperty("xpParticles", String.valueOf(xpParticles));
            try (OutputStream out = Files.newOutputStream(file())) {
                p.store(out, "Blackhole XP Bar");
            } catch (IOException | RuntimeException ignored) {
                // Gagal simpan tidak boleh bikin game error.
            }
        }
    }

    public static final class ConfigScreen extends Screen {
        private final Screen parent;

        public ConfigScreen(Screen parent) {
            super(Text.literal("Blackhole XP Bar"));
            this.parent = parent;
        }

        private static Text label(String name, boolean on) {
            return Text.literal(name + ": ").append(Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED));
        }

        @Override
        protected void init() {
            int mid = this.width / 2;
            this.addDrawableChild(ButtonWidget.builder(label("Aktifkan mod", Cfg.enabled), b -> {
                Cfg.enabled = !Cfg.enabled;
                b.setMessage(label("Aktifkan mod", Cfg.enabled));
            }).dimensions(mid - 155, 36, 310, 20).build());

            for (int i = 0; i < Cfg.NAMES.length; i++) {
                final int idx = i;
                int x = mid - 155 + (i % 2) * 160;
                int y = 66 + (i / 2) * 24;
                this.addDrawableChild(ButtonWidget.builder(label(Cfg.NAMES[i], Cfg.get(i)), b -> {
                    Cfg.set(idx, !Cfg.get(idx));
                    b.setMessage(label(Cfg.NAMES[idx], Cfg.get(idx)));
                }).dimensions(x, y, 150, 20).build());
            }

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Selesai"), b -> this.close())
                    .dimensions(mid - 100, this.height - 30, 200, 20).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFFFF);
        }

        @Override
        public void close() {
            Cfg.save();
            if (this.client != null) this.client.setScreen(this.parent);
        }
    }

    // Dipanggil Mod Menu supaya muncul tombol pengaturan di daftar mod.
    public static final class ModMenuHook implements com.terraformersmc.modmenu.api.ModMenuApi {
        @Override
        public com.terraformersmc.modmenu.api.ConfigScreenFactory<?> getModConfigScreenFactory() {
            return ConfigScreen::new;
        }
    }

    static final class BlackHoleHud {

    // Warna: ungu di level 1, makin naik makin oranye, berhenti di oranye (level 100+).
    private static final int[] PURPLE = {124, 58, 237};
    private static final int[] ORANGE = {255, 138, 61};
    private static final int[] DEEP = {91, 33, 182};
    private static final int[] ORB = {184, 240, 58};
    private static final int MAX_LEVEL_COLOR = 50;

    private static final int[] WHITE = {255, 255, 255};

    // Bar terbelah dua (kiri dan kanan), black hole di tengah.
    // Tiap sisi 5 kotak, makin dekat black hole makin kecil.
    private static final int SEGS_SIDE = 5;
    private static final int SEG_W = 11;
    private static final int SEG_GAP = 3;
    private static final int BAR_H = 7;
    // Tinggi harus ganjil supaya kotak terisi pas di tengah bar (7 - tinggi) / 2 selalu bulat.
    private static final int[] SEG_H = {7, 5, 5, 3, 3};

    private static final Random RNG = new Random();
    private static final List<Particle> PARTICLES = new ArrayList<>();
    private static final List<Wave> WAVES = new ArrayList<>();

    private static float lastTotal = -1f;
    private static int lastLevel = -1;
    private static long levelUpAt = -100000L;
    // Penunjuk arah player (locator bar): tampil menggantikan bar XP, seperti di game asli.
    private static long xpActivityAt = -100000L;
    private static final long XP_SHOW_MS = 5000L;
    // Kalau titik player ternyata terbalik kiri-kanan, ubah jadi true.
    private static final boolean LOCATOR_FLIP = false;
    private static final int[][] DOT_COLORS = {
            {255, 120, 170}, {90, 220, 255}, {255, 205, 70}, {184, 240, 58}, {235, 235, 255}, {255, 138, 61}
    };
    private static int[][] TRACK;
    private static float pulse = 0f;

    private record Particle(float sx, float sy, long born, int dur) {}
    private record Wave(long born, int dur, boolean flash) {}

    private BlackHoleHud() {}

    // Dipanggil tiap frame supaya perubahan XP tetap terdeteksi walau yang tampil penunjuk arah.
    public static void update(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || !Cfg.xp()) return;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        trackChanges(p.experienceLevel, p.experienceProgress, sw / 2 - 91, sh - 32 + 3, sw, sh, System.currentTimeMillis());
    }

    public static boolean useCustomBar(MinecraftClient mc) {
        if (!Cfg.xp() || !isActive(mc)) return false;
        return Cfg.loc() || !locatorMode(mc, System.currentTimeMillis());
    }

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
        int barTop = sh - 37;
        int cx = sw / 2;
        int cy = barTop + BAR_H / 2;

        int level = player.experienceLevel;
        float progress = player.experienceProgress;
        trackChanges(level, progress, x0, y0, sw, sh, now);

        int[] main = levelColor(level);
        int[] light = lerp(main, new int[]{255, 255, 255}, 0.45f);

        boolean locator = locatorMode(mc, now);
        if (locator) {
            drawTrack(ctx, x0, cy, main, light, now);
        } else {
            drawBar(ctx, x0, barTop, progress, main);
        }
        drawDisk(ctx, cx, cy, main, light, now, false);
        fillCircle(ctx, cx, cy, 7, 0xFF000000);

        // Angka level di tengah lubang, digambar sebelum cincin supaya tertutup cincin.
        if (level > 0) {
            boolean popping = now - levelUpAt < 450;
            int[] c = popping ? new int[]{255, 255, 255} : light;
            drawLevelNumber(ctx, cx, cy, level, argb(255, c));
        }

        drawHalo(ctx, cx, cy, light, now);
        drawDisk(ctx, cx, cy, main, light, now, true);
        if (locator) drawLocatorDots(ctx, mc, cx, cy);
        drawParticles(ctx, cx, cy, main, now);
        drawWaves(ctx, cx, cy, main, now);
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
            xpActivityAt = now;
            if (Cfg.fx()) {
                levelUpAt = now;
                WAVES.add(new Wave(now, 450, true));
                for (int i = 0; i < 2; i++) WAVES.add(new Wave(now + i * 220L, 900, false));
            }
            if (Cfg.orbs()) spawn(8, x0, y0, now);
        } else if (total > lastTotal + 0.0001f) {
            float d = total - lastTotal;
            xpActivityAt = now;
            int n = Math.max(3, Math.min(10, (int) (d * 60f) + 3));
            if (Cfg.orbs()) spawn(n, x0, y0, now);
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

    // Locator bar tampil kalau ada player lain yang bisa dilacak, kecuali beberapa detik setelah dapat XP.
    private static boolean locatorMode(MinecraftClient mc, long now) {
        var handler = mc.getNetworkHandler();
        if (handler == null) return false;
        if (now - xpActivityAt < XP_SHOW_MS) return false;
        return handler.getWaypointHandler().hasWaypoint();
    }

    // Garis lintasan berbentuk petir, selebar bar XP. Dihitung sekali lalu dipakai ulang.
    private static int[][] track() {
        if (TRACK != null) return TRACK;
        int[] zig = {-2, 1, -3, 2, -1, 3, -2, 0, 2, -3, 1, -1, 3, -2, 2, -3, 0, 1, -2, 3, -1, 2, -3, 0, 2, -1, -2, 3, -1, 1, -3, 2, 0};
        int segW = 5;
        int n = 180 / segW;
        int[] pts = new int[n + 1];
        for (int i = 1; i < n; i++) pts[i] = zig[(i - 1) % zig.length];
        List<int[]> runs = new ArrayList<>();
        for (int sIdx = 0; sIdx < n; sIdx++) {
            int xa = 1 + sIdx * segW;
            int dy = pts[sIdx + 1] - pts[sIdx];
            int rows = Math.abs(dy) + 1;
            int step = Integer.signum(dy);
            for (int r = 0; r < rows; r++) {
                int xs = xa + r * segW / rows;
                int xe = xa + (r + 1) * segW / rows;
                if (xe <= xs) xe = xs + 1;
                runs.add(new int[]{xs, xe, pts[sIdx] + r * step});
            }
        }
        TRACK = runs.toArray(new int[0][]);
        return TRACK;
    }

    private static void drawTrack(DrawContext ctx, int x0, int cy, int[] main, int[] light, long now) {
        int[] base = lerp(DEEP, main, 0.8f);
        int baseCol = argb(255, base);
        int glow = argb(55, main);
        double hp = (now * 0.09) % 300.0 - 60.0;
        double hp2 = 182.0 - hp;
        for (int[] r : track()) {
            int xs = x0 + r[0];
            int xe = x0 + r[1];
            int y = cy + r[2];
            double xm = (r[0] + r[1]) * 0.5;
            double g = Math.max(Math.max(0.0, 1.0 - Math.abs(xm - hp) / 18.0), Math.max(0.0, 1.0 - Math.abs(xm - hp2) / 18.0));
            ctx.fill(xs, y - 1, xe, y + 2, glow);
            ctx.fill(xs, y, xe, y + 1, g > 0.02 ? argb(255, lerp(base, light, (float) g)) : baseCol);
        }
        // Ujung lintasan, sejajar dengan ujung bar XP.
        int cap = argb(255, DEEP);
        ctx.fill(x0, cy - 3, x0 + 1, cy + 4, cap);
        ctx.fill(x0 + 181, cy - 3, x0 + 182, cy + 4, cap);
    }

    // Bulatan penunjuk arah player lain, posisi mengikuti arah pandangan.
    private static void drawLocatorDots(DrawContext ctx, MinecraftClient mc, int cx, int cy) {
        var handler = mc.getNetworkHandler();
        var entity = mc.getCameraEntity();
        if (handler == null || entity == null || mc.world == null) return;
        var cam = mc.gameRenderer.getCamera();
        float tickProgress = mc.getRenderTickCounter().getTickProgress(false);
        List<double[]> dots = new ArrayList<>();
        handler.getWaypointHandler().forEachWaypoint(entity, wp -> {
            double d = wp.getRelativeYaw(mc.world, cam, e -> tickProgress);
            if (d <= -61.0 || d > 60.0) return;
            dots.add(new double[]{d, wp.getSource().hashCode() & 0x7fffffff});
        });
        for (double[] dot : dots) {
            double off = Math.max(-86.0, Math.min(86.0, dot[0] / 61.0 * 86.0));
            int px = cx + (int) Math.round(LOCATOR_FLIP ? -off : off);
            int[] c = DOT_COLORS[(int) (dot[1] % DOT_COLORS.length)];
            fillCircle(ctx, px, cy, 4, 0xE00A0614);
            fillCircle(ctx, px, cy, 3, argb(255, c));
            ctx.fill(px - 1, cy - 1, px, cy, argb(220, WHITE));
        }
    }

    // Crosshair kecil: empat garis pendek dan titik tengah, tidak menutupi apa yang dibidik.
    public static void renderCrosshair(DrawContext ctx, MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || !mc.options.getPerspective().isFirstPerson()) return;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int ix = (sw - 7) / 2;
        int iy = (sh - 7) / 2;
        int[] main = levelColor(p.experienceLevel);
        float prog = p.getAttackCooldownProgress(0.0f);
        boolean target = mc.targetedEntity instanceof net.minecraft.entity.LivingEntity;
        int ink = argb(255, lerp(main, WHITE, target && prog >= 1.0f ? 0.85f : 0.45f));
        int dot = argb(255, target ? ORANGE : WHITE);
        for (int pass = 0; pass < 2; pass++) {
            int o = pass == 0 ? 1 : 0;
            int c = pass == 0 ? 0x90000000 : ink;
            ctx.fill(ix + 2 + o, iy + o, ix + 5 + o, iy + 1 + o, c);
            ctx.fill(ix + 2 + o, iy + 6 + o, ix + 5 + o, iy + 7 + o, c);
            ctx.fill(ix + o, iy + 2 + o, ix + 1 + o, iy + 5 + o, c);
            ctx.fill(ix + 6 + o, iy + 2 + o, ix + 7 + o, iy + 5 + o, c);
            ctx.fill(ix + 3 + o, iy + 3 + o, ix + 4 + o, iy + 4 + o, pass == 0 ? c : dot);
        }
        // Indikator serangan versi crosshair (hanya kalau opsi Attack Indicator = Crosshair).
        if (mc.options.getAttackIndicator().getValue() == AttackIndicator.CROSSHAIR) {
            if (Cfg.blade()) {
                int sx = (sw - 16) / 2;
                int sy = iy + 10;
                if (prog < 1.0f) {
                    drawSwordMask(ctx, SWORD_H, sx + 1, sy + 1, true, 16, 0x90000000, 0x90000000);
                    drawSwordMask(ctx, SWORD_H, sx, sy, true, Math.max(1, Math.round(prog * 16f)),
                            argb(255, lerp(main, WHITE, 0.4f)), 0xFF4A3F7A);
                } else if (target) {
                    drawSwordMask(ctx, SWORD_H, sx + 1, sy + 1, true, 16, 0x90000000, 0x90000000);
                    int full = argb(255, lerp(ORANGE, WHITE, 0.35f));
                    drawSwordMask(ctx, SWORD_H, sx, sy, true, 16, full, full);
                }
            } else if (prog < 1.0f) {
                ctx.fill(ix - 1, iy + 10, ix + 8, iy + 12, 0x90000000);
                int w = Math.max(1, (int) (prog * 9f));
                ctx.fill(ix - 1, iy + 10, ix - 1 + w, iy + 12, argb(255, main));
            }
        }
    }

    // Pedang untuk indikator serangan: versi miring (di slot hotbar) dan versi mendatar (di bawah crosshair).
    private static final String[] SWORD_V = {
            "...............#",
            "............###.",
            "...........###..",
            "..........###...",
            ".........###....",
            "........###.....",
            "..##...###......",
            "...##.###.......",
            "....####........",
            ".....##.........",
            "....#.##........",
            "...#...##.......",
            "..#.............",
            ".#..............",
            "##..............",
            "#..............."
    };
    private static final String[] SWORD_H = {
            ".....#..........",
            ".....#..........",
            "#....##########.",
            "################",
            "#....##########.",
            ".....#..........",
            ".....#.........."
    };

    // Menggambar pedang; bagian yang "terisi" berwarna terang, sisanya redup.
    // Horizontal: kolom di bawah boundary terisi. Vertikal: baris dari boundary ke bawah terisi.
    private static void drawSwordMask(DrawContext ctx, String[] m, int x, int y, boolean horizontal,
                                      int boundary, int fillCol, int emptyCol) {
        for (int r = 0; r < m.length; r++) {
            String line = m[r];
            int c = 0;
            while (c < line.length()) {
                if (line.charAt(c) != '#') {
                    c++;
                    continue;
                }
                int e = c;
                while (e < line.length() && line.charAt(e) == '#') e++;
                if (horizontal) {
                    int split = Math.max(c, Math.min(e, boundary));
                    if (split > c) ctx.fill(x + c, y + r, x + split, y + r + 1, fillCol);
                    if (e > split) ctx.fill(x + split, y + r, x + e, y + r + 1, emptyCol);
                } else {
                    ctx.fill(x + c, y + r, x + e, y + r + 1, r >= boundary ? fillCol : emptyCol);
                }
                c = e;
            }
        }
    }

    private static void drawBar(DrawContext ctx, int x0, int top, float progress, int[] main) {
        float pos = Math.max(0f, Math.min(1f, progress)) * (SEGS_SIDE * 2);

        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < SEGS_SIDE; i++) {
                int idx = side * SEGS_SIDE + i;
                int sx = side == 0
                        ? x0 + i * (SEG_W + SEG_GAP)
                        : x0 + 182 - SEG_W - i * (SEG_W + SEG_GAP);
                float t = i / (float) (SEGS_SIDE - 1);
                int h = SEG_H[i];
                int fy = top + (BAR_H - h) / 2;

                float f = Math.max(0f, Math.min(1f, pos - idx));
                int fillW = Math.round(f * SEG_W);

                if (fillW < SEG_W) {
                    ctx.fill(sx - 1, top - 1, sx + SEG_W + 1, top + BAR_H + 1, 0xFF3A2F66);
                    ctx.fill(sx, top, sx + SEG_W, top + BAR_H, 0xFF000000);
                }
                if (fillW > 0) {
                    int xa = side == 0 ? sx : sx + SEG_W - fillW;
                    int xb = side == 0 ? sx + fillW : sx + SEG_W;
                    ctx.fill(xa, fy, xb, fy + h, argb(255, lerp(DEEP, main, t)));
                }
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

    private static final String[][] DIGITS = {
            {".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."},
            {"..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."},
            {".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"},
            {".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###."},
            {"...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."},
            {"#####", "#....", "####.", "....#", "....#", "#...#", ".###."},
            {".###.", "#....", "#....", "####.", "#...#", "#...#", ".###."},
            {"#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."},
            {".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."},
            {".###.", "#...#", "#...#", ".####", "....#", "....#", ".###."}
    };

    // Angka level digambar sendiri per piksel supaya pas di tengah lubang,
    // tidak tergantung lebar huruf bawaan game.
    private static void drawLevelNumber(DrawContext ctx, int cx, int cy, int level, int color) {
        String s = String.valueOf(level);
        int n = s.length();
        int x = cx + 1 - 3 * n;
        int y = cy - 3;
        for (int d = 0; d < n; d++) {
            String[] g = DIGITS[s.charAt(d) - '0'];
            for (int row = 0; row < 7; row++) {
                String line = g[row];
                int col = 0;
                while (col < 5) {
                    if (line.charAt(col) == '#') {
                        int end = col;
                        while (end < 5 && line.charAt(end) == '#') end++;
                        ctx.fill(x + col, y + row, x + end, y + row + 1, color);
                        col = end;
                    } else {
                        col++;
                    }
                }
            }
            x += 6;
        }
    }

    private static void drawHalo(DrawContext ctx, int cx, int cy, int[] light, long now) {
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
                fillCircle(ctx, cx, cy, (int) (8 + 22 * p), argb((int) (190 * (1f - p)), new int[]{255, 255, 255}));
            } else {
                float q = 1f - (1f - p) * (1f - p);
                double r = 10 + 55 * q;
                int a = (int) (230 * (1f - p));
                int pts = Math.max(20, (int) (r * 1.3));
                for (int k = 0; k < pts; k++) {
                    double ang = k * Math.PI * 2 / pts;
                    int px = cx + (int) Math.round(Math.cos(ang) * r);
                    int py = cy + (int) Math.round(Math.sin(ang) * r);
                    ctx.fill(px, py, px + 2, py + 2, argb(a, main));
                }
            }
            return false;
        });
    }

    private static void fillCircle(DrawContext ctx, int cx, int cy, int r, int color) {
        int step = r > 12 ? 2 : 1;
        for (int dy = -r; dy <= r; dy += step) {
            int dx = (int) Math.sqrt((double) r * r - (double) dy * dy);
            ctx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + step, color);
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
        int x0 = sw / 2 - 91 - 13;
        int baseY = sh - 50;

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
        int baseY = sh - 50;
        int food = p.getHungerManager().getFoodLevel();
        Pal pal = p.hasStatusEffect(StatusEffects.HUNGER) ? FOOD_HUNGER : FOOD_NORMAL;

        for (int i = 0; i < 10; i++) {
            int x = x0 + 186 - 8 * i;
            int left = food - i * 2;
            if (left >= 2) drawIcon(ctx, x, baseY, FOOD_RECTS, pal, 0, 9);
            else if (left == 1) drawIcon(ctx, x, baseY, FOOD_RECTS, pal, 4, 9);
            else drawIcon(ctx, x, baseY, FOOD_RECTS, pal, 0, 0);
        }
    }

    // ---------- Hotbar ----------

    // Kotak dengan pojok melengkung (pojok dipotong 1px).
    private static void roundedOutline(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 1, y, x + w - 1, y + 1, color);
        ctx.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static void drawSlot(DrawContext ctx, int bx, int by, boolean selected, int[] main, long now) {
        if (selected) {
            int[] bright = lerp(main, WHITE, 0.3f);
            ctx.fill(bx + 1, by + 1, bx + 17, by + 17, 0xF0160F2C);
            roundedOutline(ctx, bx - 2, by - 2, 22, 22, argb(70, main));
            roundedOutline(ctx, bx - 1, by - 1, 20, 20, argb(190, main));
            roundedOutline(ctx, bx, by, 18, 18, argb(255, bright));
            roundedOutline(ctx, bx + 1, by + 1, 16, 16, argb(150, main));
            return;
        }
        roundedOutline(ctx, bx, by, 18, 18, 0xFF3A2F66);
        ctx.fill(bx + 1, by + 1, bx + 17, by + 17, 0xE0000000);
    }

    public static void renderHotbar(DrawContext ctx, MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        long now = System.currentTimeMillis();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int x0 = sw / 2 - 91;
        int[] main = levelColor(p.experienceLevel);
        int sel = p.getInventory().getSelectedSlot();

        // Plat gelap tembus pandang di belakang semua slot.
        ctx.fill(x0, sh - 22, x0 + 182, sh, 0xA0070512);
        ctx.fill(x0, sh - 22, x0 + 182, sh - 21, 0xFF1E1536);
        ctx.fill(x0, sh - 22, x0 + 1, sh, 0xFF1E1536);
        ctx.fill(x0 + 181, sh - 22, x0 + 182, sh, 0xFF1E1536);

        for (int i = 0; i < 9; i++) {
            int bx = x0 + 2 + 20 * i;
            int by = sh - 20;
            drawSlot(ctx, bx, by, i == sel, main, now);
            ItemStack st = p.getInventory().getStack(i);
            if (!st.isEmpty()) drawStack(ctx, mc, st, bx + 1, by + 1);
        }

        ItemStack off = p.getOffHandStack();
        if (!off.isEmpty()) {
            boolean left = p.getMainArm() == Arm.RIGHT;
            int bx = left ? x0 - 27 : x0 + 191;
            int by = sh - 20;
            ctx.fill(bx - 2, sh - 22, bx + 20, sh, 0xA0070512);
            drawSlot(ctx, bx, by, false, main, now);
            drawStack(ctx, mc, off, bx + 1, by + 1);
        }

        drawAttackIndicator(ctx, mc, p, x0, sh, main, now);
    }

    // Item "memantul" sebentar waktu baru masuk ke slot, sama seperti hotbar bawaan.
    private static void drawStack(DrawContext ctx, MinecraftClient mc, ItemStack st, int x, int y) {
        float pop = st.getBobbingAnimationTime();
        boolean anim = pop > 0f;
        if (anim) {
            float g = 1.0f + pop / 5.0f;
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(x + 8f, y + 12f);
            ctx.getMatrices().scale(1.0f / g, (g + 1.0f) / 2.0f);
            ctx.getMatrices().translate(-(x + 8f), -(y + 12f));
        }
        ctx.drawItem(st, x, y);
        if (anim) ctx.getMatrices().popMatrix();
        ctx.drawStackOverlay(mc.textRenderer, st, x, y);
    }

    // Indikator cooldown serangan versi hotbar (hanya kalau opsi Attack Indicator = Hotbar).
    private static void drawAttackIndicator(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p,
                                            int x0, int sh, int[] main, long now) {
        if (mc.options.getAttackIndicator().getValue() != AttackIndicator.HOTBAR) return;
        float prog = p.getAttackCooldownProgress(0.0f);
        if (prog >= 1.0f) return;

        boolean right = p.getMainArm() == Arm.RIGHT;
        int bx = right ? x0 + 182 + 6 : x0 - 6 - 18;
        int by = sh - 20;
        ctx.fill(bx - 2, sh - 22, bx + 20, sh, 0xA0070512);
        ctx.fill(bx + 1, by, bx + 17, by + 1, 0xFF3A2F66);
        ctx.fill(bx + 1, by + 17, bx + 17, by + 18, 0xFF3A2F66);
        ctx.fill(bx, by + 1, bx + 1, by + 17, 0xFF3A2F66);
        ctx.fill(bx + 17, by + 1, bx + 18, by + 17, 0xFF3A2F66);
        ctx.fill(bx + 1, by + 1, bx + 17, by + 17, 0xE0000000);

        int h = Math.max(1, (int) (prog * 16f));
        int[] light = lerp(main, WHITE, 0.5f);
        if (Cfg.blade()) {
            drawSwordMask(ctx, SWORD_V, bx + 1, by + 1, false, 16 - h, argb(255, light), 0xFF4A3F7A);
        } else {
            ctx.fill(bx + 2, by + 17 - h, bx + 16, by + 17, argb(255, main));
            ctx.fill(bx + 2, by + 17 - h, bx + 16, by + 18 - h, argb(255, light));
        }
    }

    private static int argb0(int[] c) {
        return 0xFF000000 | (c[0] << 16) | (c[1] << 8) | c[2];
    }
    }
}
