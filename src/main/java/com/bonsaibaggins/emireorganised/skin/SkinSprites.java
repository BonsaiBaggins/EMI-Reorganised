package com.bonsaibaggins.emireorganised.skin;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Draws GUI sprites the way {@link GuiGraphics#blitSprite} does, but sends a tiled or nine-sliced sprite to the GPU
 * as one cached mesh. Vanilla issues a draw call per tile, which for a small sprite filling a sidebar is tens of
 * thousands of draws a frame. A mesh is built once per sprite and size and reused wherever it is drawn, so moving a
 * panel costs nothing; it is rebuilt when the size changes or a resource reload replaces the sprite. Tiles of a
 * single colour along a row or column are merged into one stretched quad, which looks identical.
 */
public final class SkinSprites {
    private static final int MAX_CACHED = 32;
    private static final Map<Key, Mesh> CACHE = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Mesh> eldest) {
            if (size() > MAX_CACHED) {
                eldest.getValue().buffer().close();
                return true;
            }
            return false;
        }
    };

    private record Key(ResourceLocation id, int width, int height) {
    }

    private record Mesh(TextureAtlasSprite sprite, GuiSpriteScaling scaling, VertexBuffer buffer) {
    }

    private SkinSprites() {
    }

    public static void draw(GuiGraphics graphics, ResourceLocation id, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        var sprites = Minecraft.getInstance().getGuiSprites();
        TextureAtlasSprite sprite = sprites.getSprite(id);
        GuiSpriteScaling scaling = sprites.getSpriteScaling(sprite);
        if (scaling instanceof GuiSpriteScaling.Stretch) {
            graphics.blitSprite(id, x, y, width, height);
            return;
        }
        Key key = new Key(id, width, height);
        Mesh mesh = CACHE.get(key);
        if (mesh == null || mesh.sprite() != sprite || mesh.scaling() != scaling) {
            if (mesh != null) {
                mesh.buffer().close();
                CACHE.remove(key);
            }
            VertexBuffer buffer = build(sprite, scaling, width, height);
            if (buffer == null) {
                return;
            }
            mesh = new Mesh(sprite, scaling, buffer);
            CACHE.put(key, mesh);
        }
        RenderSystem.setShaderTexture(0, sprite.atlasLocation());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix())
                .mul(graphics.pose().last().pose())
                .translate(x, y, 0);
        mesh.buffer().bind();
        mesh.buffer().drawWithShader(modelView, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
        VertexBuffer.unbind();
    }

    private static VertexBuffer build(TextureAtlasSprite sprite, GuiSpriteScaling scaling, int width, int height) {
        SpriteContents contents = sprite.contents();
        NativeImage image = contents.getOriginalImage();
        boolean animated = image.getWidth() != contents.width() || image.getHeight() != contents.height();
        Mesher mesher = new Mesher(sprite,
                Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX),
                animated ? null : image);
        if (scaling instanceof GuiSpriteScaling.Tile tile) {
            mesher.tiled(0, 0, width, height, 0, 0, tile.width(), tile.height(), tile.width(), tile.height());
        } else if (scaling instanceof GuiSpriteScaling.NineSlice nine) {
            mesher.nineSlice(nine, width, height);
        }
        MeshData data = mesher.builder.build();
        if (data == null) {
            return null;
        }
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(data);
        VertexBuffer.unbind();
        return buffer;
    }

    /** The same tiles vanilla's nine-slice and tile scaling draw, at the origin, as quads in one buffer. */
    private record Mesher(TextureAtlasSprite sprite, BufferBuilder builder, @Nullable NativeImage image) {
        void quad(int sw, int sh, int u, int v, int x, int y, int w, int h) {
            quad(sw, sh, u, v, w, h, x, y, w, h);
        }

        /** Draws the {@code uw}x{@code vh} source area at {@code u, v} into a {@code w}x{@code h} quad. */
        void quad(int sw, int sh, int u, int v, int uw, int vh, int x, int y, int w, int h) {
            if (w == 0 || h == 0) {
                return;
            }
            float u0 = sprite.getU(u / (float) sw);
            float u1 = sprite.getU((u + uw) / (float) sw);
            float v0 = sprite.getV(v / (float) sh);
            float v1 = sprite.getV((v + vh) / (float) sh);
            builder.addVertex(x, y, 0).setUv(u0, v0);
            builder.addVertex(x, y + h, 0).setUv(u0, v1);
            builder.addVertex(x + w, y + h, 0).setUv(u1, v1);
            builder.addVertex(x + w, y, 0).setUv(u1, v0);
        }

        void tiled(int x, int y, int w, int h, int u, int v, int tw, int th, int sw, int sh) {
            if (w <= 0 || h <= 0) {
                return;
            }
            if (tw <= 0 || th <= 0) {
                throw new IllegalArgumentException("Tiled sprite texture size must be positive, got " + tw + "x" + th);
            }
            boolean flatX = uniform(u, v, tw, th, sw, sh, true);
            boolean flatY = uniform(u, v, tw, th, sw, sh, false);
            int stepX = flatX ? w : tw;
            int stepY = flatY ? h : th;
            for (int i = 0; i < w; i += stepX) {
                int cw = Math.min(stepX, w - i);
                for (int j = 0; j < h; j += stepY) {
                    int ch = Math.min(stepY, h - j);
                    quad(sw, sh, u, v, flatX ? tw : cw, flatY ? th : ch, x + i, y + j, cw, ch);
                }
            }
        }

        /** Whether every row ({@code alongX}) or every column of the tile is one colour, so it can stretch. */
        boolean uniform(int u, int v, int tw, int th, int sw, int sh, boolean alongX) {
            if (image == null) {
                return false;
            }
            int x0 = u * image.getWidth() / sw;
            int x1 = (u + tw) * image.getWidth() / sw;
            int y0 = v * image.getHeight() / sh;
            int y1 = (v + th) * image.getHeight() / sh;
            if (x1 <= x0 || y1 <= y0) {
                return false;
            }
            if (alongX) {
                for (int py = y0; py < y1; py++) {
                    int colour = image.getPixelRGBA(x0, py);
                    for (int px = x0 + 1; px < x1; px++) {
                        if (image.getPixelRGBA(px, py) != colour) {
                            return false;
                        }
                    }
                }
            } else {
                for (int px = x0; px < x1; px++) {
                    int colour = image.getPixelRGBA(px, y0);
                    for (int py = y0 + 1; py < y1; py++) {
                        if (image.getPixelRGBA(px, py) != colour) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        void nineSlice(GuiSpriteScaling.NineSlice nine, int w, int h) {
            int sw = nine.width();
            int sh = nine.height();
            GuiSpriteScaling.NineSlice.Border border = nine.border();
            int left = Math.min(border.left(), w / 2);
            int right = Math.min(border.right(), w / 2);
            int top = Math.min(border.top(), h / 2);
            int bottom = Math.min(border.bottom(), h / 2);
            if (w == sw && h == sh) {
                quad(sw, sh, 0, 0, 0, 0, w, h);
            } else if (h == sh) {
                quad(sw, sh, 0, 0, 0, 0, left, h);
                tiled(left, 0, w - right - left, h, left, 0, sw - right - left, sh, sw, sh);
                quad(sw, sh, sw - right, 0, w - right, 0, right, h);
            } else if (w == sw) {
                quad(sw, sh, 0, 0, 0, 0, w, top);
                tiled(0, top, w, h - bottom - top, 0, top, sw, sh - bottom - top, sw, sh);
                quad(sw, sh, 0, sh - bottom, 0, h - bottom, w, bottom);
            } else {
                int midW = sw - right - left;
                int midH = sh - bottom - top;
                quad(sw, sh, 0, 0, 0, 0, left, top);
                tiled(left, 0, w - right - left, top, left, 0, midW, top, sw, sh);
                quad(sw, sh, sw - right, 0, w - right, 0, right, top);
                quad(sw, sh, 0, sh - bottom, 0, h - bottom, left, bottom);
                tiled(left, h - bottom, w - right - left, bottom, left, sh - bottom, midW, bottom, sw, sh);
                quad(sw, sh, sw - right, sh - bottom, w - right, h - bottom, right, bottom);
                tiled(0, top, left, h - bottom - top, 0, top, left, midH, sw, sh);
                tiled(left, top, w - right - left, h - bottom - top, left, top, midW, midH, sw, sh);
                tiled(w - right, top, right, h - bottom - top, sw - right, top, right, midH, sw, sh);
            }
        }
    }
}
