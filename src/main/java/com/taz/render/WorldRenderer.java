package com.taz.render;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.taz.Taz;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Boxes, lines and labels in the world, for every Taz feature. Use from LevelRenderEvents.END_MAIN: open a Batch, add
 * shapes, close it.
 */
public final class WorldRenderer {
	private static final RenderType BOX_THROUGH = boxType("through_walls_box", CompareOp.ALWAYS_PASS);
	private static final RenderType LINES_THROUGH = linesType("through_walls_lines", CompareOp.ALWAYS_PASS);
	private static final RenderType BOX_DEPTH = boxType("depth_box", CompareOp.LESS_THAN_OR_EQUAL);
	private static final RenderType LINES_DEPTH = linesType("depth_lines", CompareOp.LESS_THAN_OR_EQUAL);

	private WorldRenderer() {
	}

	private static RenderType boxType(String name, CompareOp depth) {
		RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
				.withLocation(Identifier.fromNamespaceAndPath(Taz.MOD_ID, "pipeline/" + name))
				.withCull(false)
				.withDepthStencilState(new DepthStencilState(depth, false))
				.build();
		return RenderType.create("taz_" + name, RenderSetup.builder(pipeline).sortOnUpload().createRenderSetup());
	}

	private static RenderType linesType(String name, CompareOp depth) {
		RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
				.withLocation(Identifier.fromNamespaceAndPath(Taz.MOD_ID, "pipeline/" + name))
				.withDepthStencilState(new DepthStencilState(depth, false))
				.build();
		return RenderType.create("taz_" + name, RenderSetup.builder(pipeline).createRenderSetup());
	}

	public static Batch begin(LevelRenderContext context) {
		return new Batch(context);
	}

	/** Where a line from the crosshair should start: just in front of the camera. */
	public static Vec3 crosshair(Vec3 camera) {
		Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
		return camera.add(Vec3.directionFromRotation(cam.xRot(), cam.yRot()).scale(2));
	}

	public static final class Batch implements AutoCloseable {
		private record Label(Vec3 pos, String text, int argb, boolean throughWalls, int row) {
		}

		private final LevelRenderContext context;
		private final PoseStack poseStack;
		private final MultiBufferSource.BufferSource buffers;
		public final Vec3 camera;
		private final List<Label> labels = new ArrayList<>();
		private boolean usedBoxThrough, usedBoxDepth, usedLinesThrough, usedLinesDepth;

		private Batch(LevelRenderContext context) {
			this.context = context;
			this.poseStack = context.poseStack();
			this.buffers = context.bufferSource();
			this.camera = context.levelState().cameraRenderState.pos;
			poseStack.pushPose();
			poseStack.translate(-camera.x, -camera.y, -camera.z);
		}

		/** The 12 edges of a box. */
		public void outline(AABB b, int argb, float width, boolean throughWalls) {
			VertexConsumer lines = lines(throughWalls);
			PoseStack.Pose pose = poseStack.last();
			Vec3[] c = {
					new Vec3(b.minX, b.minY, b.minZ), new Vec3(b.maxX, b.minY, b.minZ), new Vec3(b.maxX, b.minY, b.maxZ), new Vec3(b.minX, b.minY, b.maxZ),
					new Vec3(b.minX, b.maxY, b.minZ), new Vec3(b.maxX, b.maxY, b.minZ), new Vec3(b.maxX, b.maxY, b.maxZ), new Vec3(b.minX, b.maxY, b.maxZ),
			};
			int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
			for (int[] e : edges) vertexLine(pose, lines, c[e[0]], c[e[1]], argb, width);
		}

		/** A see-through filled box (use a low alpha). */
		public void filled(AABB b, int argb, boolean throughWalls) {
			VertexConsumer quads = throughWalls ? use(BOX_THROUGH, true) : use(BOX_DEPTH, false);
			PoseStack.Pose pose = poseStack.last();
			float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
			float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
			quad(pose, quads, argb, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
			quad(pose, quads, argb, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1);
			quad(pose, quads, argb, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1);
			quad(pose, quads, argb, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
			quad(pose, quads, argb, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
			quad(pose, quads, argb, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
		}

		/** A line from (x1, y1, z1) to (x2, y2, z2) without creating objects, for drawing many lines every frame. */
		public void line(float x1, float y1, float z1, float x2, float y2, float z2, int argb, float width, boolean throughWalls) {
			VertexConsumer c = lines(throughWalls);
			PoseStack.Pose pose = poseStack.last();
			float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1;
			c.addVertex(pose, x1, y1, z1).setColor(argb).setNormal(pose, nx, ny, nz).setLineWidth(width);
			c.addVertex(pose, x2, y2, z2).setColor(argb).setNormal(pose, nx, ny, nz).setLineWidth(width);
		}

		public void line(Vec3 a, Vec3 b, int argb, float width, boolean throughWalls) {
			vertexLine(poseStack.last(), lines(throughWalls), a, b, argb, width);
		}

		/** Text facing the camera, scaled up with distance so it stays readable. Drawn when the batch closes. */
		public void label(Vec3 pos, String text, int argb, boolean throughWalls) {
			label(pos, text, argb, throughWalls, 0);
		}

		/** A label stacked {@code row} lines above another one at the same spot (it stays clear at any distance). */
		public void label(Vec3 pos, String text, int argb, boolean throughWalls, int row) {
			labels.add(new Label(pos, text, argb, throughWalls, row));
		}

		@Override
		public void close() {
			if (usedBoxThrough) buffers.endBatch(BOX_THROUGH);
			if (usedBoxDepth) buffers.endBatch(BOX_DEPTH);
			if (usedLinesThrough) buffers.endBatch(LINES_THROUGH);
			if (usedLinesDepth) buffers.endBatch(LINES_DEPTH);
			poseStack.popPose();
			if (labels.isEmpty()) return;

			Font font = Minecraft.getInstance().font;
			for (Label l : labels) {
				double distance = camera.distanceTo(l.pos());
				float scale = (float) (0.03 * Math.max(1.0, distance / 6.0));
				poseStack.pushPose();
				poseStack.translate(l.pos().x - camera.x, l.pos().y - camera.y + scale * (12 + l.row() * 11), l.pos().z - camera.z);
				poseStack.mulPose(context.levelState().cameraRenderState.orientation);
				poseStack.scale(scale, -scale, scale);
				// Shadowed text with no background box, like the HUDs.
				font.drawInBatch(l.text(), -font.width(l.text()) / 2.0F, 0, l.argb(), true, poseStack.last().pose(), buffers,
						l.throughWalls() ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, 0, 0xF000F0);
				poseStack.popPose();
			}
			buffers.endBatch();
		}

		private VertexConsumer lines(boolean throughWalls) {
			return throughWalls ? use(LINES_THROUGH, true) : use(LINES_DEPTH, false);
		}

		private VertexConsumer use(RenderType type, boolean through) {
			if (type == BOX_THROUGH || type == BOX_DEPTH) {
				if (through) usedBoxThrough = true;
				else usedBoxDepth = true;
			} else if (through) {
				usedLinesThrough = true;
			} else {
				usedLinesDepth = true;
			}
			return buffers.getBuffer(type);
		}
	}

	private static void vertexLine(PoseStack.Pose pose, VertexConsumer consumer, Vec3 a, Vec3 b, int color, float width) {
		float nx = (float) (b.x - a.x);
		float ny = (float) (b.y - a.y);
		float nz = (float) (b.z - a.z);
		consumer.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
		consumer.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer consumer, int color, float... v) {
		for (int i = 0; i < 12; i += 3) consumer.addVertex(pose, v[i], v[i + 1], v[i + 2]).setColor(color);
	}
}
