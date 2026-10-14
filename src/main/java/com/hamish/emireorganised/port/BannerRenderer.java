package com.hamish.emireorganised.port;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.emi.emi.screen.EmiScreenManager;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Draws banners: over the placeholder rows reserved by {@link BannerLayout}, and in the Recreative editor. */
public final class BannerRenderer {
    public static final int ROW = 18;
    private static final String ELLIPSIS = "...";
    private static final Set<ResourceLocation> HOVERED = new HashSet<>();
    private static final Map<SpriteContents, int[]> EDGE_COLOURS = new WeakHashMap<>();
    private static final Set<Object> CREATIVE_HOVERED = new HashSet<>();
    private static long creativeHoverTime;

    private BannerRenderer() {
    }

    /** A texture region to draw a banner background from; u/v are atlas or texture coordinates. */
    private record Source(ResourceLocation texture, int width, float u0, float u1, float v0, float v1, int[] edges) {
    }

    public static void render(EmiScreenManager.ScreenSpace space, BannerLayout layout, GuiGraphics graphics,
                              int mouseX, int mouseY, int startIndex) {
        if (layout.banners.isEmpty() || space.pageSize <= 0) {
            return;
        }
        Map<BannerView, Boolean> hovers = new HashMap<>();
        for (BannerLayout.Banner banner : layout.banners) {
            int offset = banner.index() - startIndex;
            boolean hovered = false;
            if (offset >= 0 && offset < space.pageSize) {
                int y = space.getRawY(offset);
                int row = (y - space.ty) / ROW;
                if (row >= 0 && row < space.th) {
                    int x = space.getX(0, row);
                    int w = space.getWidth(row) * ROW;
                    hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW;
                    drawBanner(graphics, banner.view(), x, y, w, ROW);
                }
            }
            hovers.merge(banner.view(), hovered, Boolean::logicalOr);
        }
        applyHovers(hovers);
    }

    /** Starts/pauses hover animations. Several views may share a background; any hovered one plays it. */
    /** Hovers from the creative inventory; kept briefly so EMI drawn in the same frame doesn't pause them. */
    public static void applyCreativeHovers(Map<BannerView, Boolean> hovers) {
        CREATIVE_HOVERED.clear();
        hovers.forEach((view, hovered) -> {
            if (hovered && view.animationKey() != null) {
                CREATIVE_HOVERED.add(view.animationKey());
            }
        });
        creativeHoverTime = net.minecraft.Util.getMillis();
        applyHovers(hovers);
    }

    public static void applyHovers(Map<BannerView, Boolean> hovers) {
        Map<Object, Boolean> byKey = new HashMap<>();
        Map<Object, BannerView> views = new HashMap<>();
        boolean creativeFresh = net.minecraft.Util.getMillis() - creativeHoverTime < 100;
        hovers.forEach((view, hovered) -> {
            if (view.animateOnHover() && view.animationKey() != null) {
                boolean creative = creativeFresh && CREATIVE_HOVERED.contains(view.animationKey());
                byKey.merge(view.animationKey(), hovered || creative, Boolean::logicalOr);
                views.putIfAbsent(view.animationKey(), view);
            }
        });
        byKey.forEach((key, hovered) -> {
            BannerView view = views.get(key);
            if (view.image() != null) {
                BannerImages.setPlaying(view.image(), hovered);
            } else if (view.sprite() != null) {
                com.hamish.emireorganised.compat.SimulatedCompat.setPlaying(view.sprite(), hovered);
                if (hovered) {
                    HOVERED.add(view.sprite());
                }
            }
        });
    }

    /**
     * Simulated's creative screen pauses every visible banner it isn't hovering, after EMI has rendered. Re-apply
     * EMI hovers just before textures tick so hovering either banner animates both.
     */
    public static void applyHoverAnimations() {
        HOVERED.forEach(sprite -> com.hamish.emireorganised.compat.SimulatedCompat.setPlaying(sprite, true));
        HOVERED.clear();
    }

    public static void drawBanner(GuiGraphics graphics, BannerView view, int x, int y, int w, int h) {
        if (w <= 0) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.flush();

        Source source = source(view);
        if (source == null) {
            graphics.fill(x, y, x + w, y + h, 0xFF8B8B8B);
            fillHorizontalGradient(graphics, x + 1, y + 1, x + w - 1, y + h - 1, view.fillLeft(), view.fillRight());
            graphics.flush();
        } else {
            drawBackground(graphics, source, x, y, w, h);
        }
        drawTitle(graphics, view, x, y, w, h);
    }

    public static void fillHorizontalGradient(GuiGraphics graphics, int x0, int y0, int x1, int y1, int left, int right) {
        int w = x1 - x0;
        if (left == right || w <= 1) {
            graphics.fill(x0, y0, x1, y1, left);
            return;
        }
        for (int i = 0; i < w; i++) {
            graphics.fill(x0 + i, y0, x0 + i + 1, y1, net.minecraft.util.FastColor.ARGB32.lerp(i / (float) (w - 1), left, right));
        }
    }

    private static Source source(BannerView view) {
        if (view.image() != null) {
            BannerImages.Image image = BannerImages.get(view.image());
            if (image != null) {
                int frame = image.frame();
                float v0 = (float) (frame * image.frameHeight) / image.height;
                float v1 = (float) ((frame + 1) * image.frameHeight) / image.height;
                return new Source(image.texture, Math.max(image.width, 3), 0, 1, v0, v1, image.edges);
            }
        }
        if (view.sprite() != null) {
            TextureAtlasSprite sprite = Minecraft.getInstance().getGuiSprites().getSprite(view.sprite());
            return new Source(sprite.atlasLocation(), Math.max(sprite.contents().width(), 3), sprite.getU0(),
                    sprite.getU1(), sprite.getV0(), sprite.getV1(), edgeColours(sprite));
        }
        return null;
    }

    private static void drawBackground(GuiGraphics graphics, Source s, int x, int y, int w, int h) {
        int tw = s.width();
        if (w >= tw) {
            // Centre the artwork at native size and stretch its inner edge columns out to the row edges.
            int cx = x + (w - tw) / 2;
            int right = x + w;
            blit(graphics, s, x, y, 1, h, 0, 1);
            blit(graphics, s, x + 1, y, cx - x, h, 1, 2);
            blit(graphics, s, cx + 1, y, tw - 2, h, 1, tw - 1);
            blit(graphics, s, cx + tw - 1, y, right - 1 - (cx + tw - 1), h, tw - 2, tw - 1);
            blit(graphics, s, right - 1, y, 1, h, tw - 1, tw);
            if (com.hamish.emireorganised.Config.get(com.hamish.emireorganised.Config.EDGE_MODE) == com.hamish.emireorganised.Config.EdgeMode.AVERAGE && w > tw && s.edges() != null) {
                // Keep the stretched frame rows, but replace the stretched interior with a solid average colour.
                graphics.fill(x + 1, y + 1, cx + 1, y + h - 1, s.edges()[0]);
                graphics.fill(cx + tw - 1, y + 1, right - 1, y + h - 1, s.edges()[1]);
                graphics.flush();
            }
        } else {
            // Narrower than the artwork: keep the 1px frame and crop the interior evenly from both sides.
            int inner = w - 2;
            int u0 = 1 + (tw - 2 - inner) / 2;
            blit(graphics, s, x, y, 1, h, 0, 1);
            blit(graphics, s, x + 1, y, inner, h, u0, u0 + inner);
            blit(graphics, s, x + w - 1, y, 1, h, tw - 1, tw);
        }
    }

    /** Width of the title box drawn at the banner's left, for hit-testing the rename area. */
    public static int titleBoxRight(BannerView view, int x, int w) {
        Font font = Minecraft.getInstance().font;
        return x + Math.min(font.width(view.title()), Math.max(0, w - 10)) + 8;
    }

    private static void drawTitle(GuiGraphics graphics, BannerView view, int x, int y, int w, int h) {
        Font font = Minecraft.getInstance().font;
        Component text = view.title();
        int maxTextWidth = w - 10;
        if (maxTextWidth <= 0 || text.getString().isEmpty()) {
            return;
        }
        if (font.width(text) > maxTextWidth) {
            String trimmed = font.plainSubstrByWidth(text.getString(), Math.max(0, maxTextWidth - font.width(ELLIPSIS)));
            text = Component.literal(trimmed + ELLIPSIS).withStyle(text.getStyle());
        }
        int textWidth = font.width(text);
        graphics.fill(x + 2, y + 2, x + textWidth + 8, y + h - 2, view.titleBackground());
        drawAuraText(graphics, text, view.secondaryColor(), view.color(), x + 5, y + 5);
    }

    /** Edge colours of an atlas sprite, read from its source PNG so animation doesn't affect them. */
    private static int[] edgeColours(TextureAtlasSprite sprite) {
        SpriteContents contents = sprite.contents();
        return EDGE_COLOURS.computeIfAbsent(contents, c -> {
            ResourceLocation file = c.name().withPath(path -> "textures/gui/sprites/" + path + ".png");
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(file);
                 NativeImage image = NativeImage.read(in)) {
                return BannerImages.edges(image, c.height());
            } catch (Exception e) {
                return null;
            }
        });
    }

    private static void blit(GuiGraphics graphics, Source s, int x, int y, int w, int h, int u0, int u1) {
        if (w <= 0 || h <= 0) {
            return;
        }
        float du = s.u1() - s.u0();
        float minU = s.u0() + du * u0 / s.width();
        float maxU = s.u0() + du * u1 / s.width();
        RenderSystem.setShaderTexture(0, s.texture());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, x, y, 0).setUv(minU, s.v0());
        buffer.addVertex(matrix, x, y + h, 0).setUv(minU, s.v1());
        buffer.addVertex(matrix, x + w, y + h, 0).setUv(maxU, s.v1());
        buffer.addVertex(matrix, x + w, y, 0).setUv(maxU, s.v0());
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /**
     * Same look as {@code SimulatedCreativeTab.drawAuraText}, but uses the GuiGraphics scissor stack;
     * Simulated's version calls {@code RenderSystem.disableScissor()}, which breaks any enclosing clip.
     */
    private static void drawAuraText(GuiGraphics graphics, Component text, int color1, int color2, int x, int y) {
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        graphics.drawString(font, text, x, y, color1, true);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 1.0F);
        org.joml.Matrix4f pose = graphics.pose().last().pose();
        org.joml.Vector3f a = pose.transformPosition(new org.joml.Vector3f(x, y, 0));
        org.joml.Vector3f b = pose.transformPosition(new org.joml.Vector3f(x + font.width(text), y + 9.0F / 1.8F, 0));
        graphics.enableScissor((int) Math.floor(a.x), (int) Math.floor(a.y), (int) Math.ceil(b.x), Math.round(b.y));
        graphics.drawString(font, text, x, y, color2, false);
        graphics.disableScissor();
        graphics.pose().popPose();
    }

    /** Some item renderers switch GL scissoring off; re-apply the GuiGraphics scissor stack if so. */
    public static void restoreScissor(GuiGraphics graphics) {
        if (!org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST)) {
            graphics.enableScissor(0, 0, 1, 1);
            graphics.disableScissor();
        }
    }
}
