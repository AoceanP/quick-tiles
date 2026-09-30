/*
 * Copyright (c) 2026, AoceanP <https://github.com/AoceanP>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.quicktiles;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

class QuickTilesOverlay extends Overlay
{
	private static final int MAX_DRAW_DISTANCE = 32;
	/**
	 * How far along each edge a corner line reaches, in the Corners style.
	 */
	private static final double CORNER_FRACTION = 0.3;

	private final Client client;
	private final QuickTilesPlugin plugin;
	private final QuickTilesConfig config;

	@Inject
	QuickTilesOverlay(Client client, QuickTilesPlugin plugin, QuickTilesConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(PRIORITY_LOW);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Map<Integer, List<MarkedTile>> marked = plugin.getMarked();
		Player player = client.getLocalPlayer();
		if (!plugin.isTilesVisible() || marked.isEmpty() || player == null)
		{
			return null;
		}

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Stroke stroke = new BasicStroke(config.borderWidth());
		WorldPoint playerTile = player.getWorldLocation();
		boolean checkDistance = player.getWorldView().isTopLevel();

		for (Map.Entry<Integer, List<MarkedTile>> entry : marked.entrySet())
		{
			WorldView wv = client.getWorldView(entry.getKey());
			if (wv == null)
			{
				continue;
			}
			for (MarkedTile tile : entry.getValue())
			{
				WorldPoint point = tile.getPoint();
				if (point.getPlane() != wv.getPlane()
					|| (checkDistance && point.distanceTo(playerTile) >= MAX_DRAW_DISTANCE)
					|| (config.hideUnderPlayer() && point.equals(playerTile)))
				{
					continue;
				}
				draw(graphics, wv, tile, stroke);
			}
		}
		return null;
	}

	private void draw(Graphics2D graphics, WorldView wv, MarkedTile tile, Stroke stroke)
	{
		LocalPoint lp = LocalPoint.fromWorld(wv, tile.getPoint());
		if (lp == null)
		{
			return;
		}
		Color colour = tile.getColor() == null ? config.colour1() : tile.getColor();

		Polygon poly = Perspective.getCanvasTilePoly(client, lp);
		if (poly != null)
		{
			switch (config.style())
			{
				case BORDER:
					OverlayUtil.renderPolygon(graphics, poly, colour, new Color(0, 0, 0, 0), stroke);
					break;
				case CORNERS:
					drawCorners(graphics, poly, colour, stroke);
					break;
				default:
					Color fill = new Color(colour.getRed(), colour.getGreen(), colour.getBlue(),
						Math.min(config.fillOpacity(), colour.getAlpha()));
					OverlayUtil.renderPolygon(graphics, poly, colour, fill, stroke);
					break;
			}
		}

		String label = tile.getLabel();
		if (config.showLabels() && label != null && !label.isEmpty())
		{
			Point textPoint = Perspective.getCanvasTextLocation(client, graphics, lp, label, 0);
			if (textPoint != null)
			{
				OverlayUtil.renderTextLocation(graphics, textPoint, label, colour);
			}
		}
	}

	/**
	 * Draws a short line out from each corner along both of its edges.
	 */
	private static void drawCorners(Graphics2D graphics, Polygon poly, Color colour, Stroke stroke)
	{
		graphics.setColor(colour);
		graphics.setStroke(stroke);
		int n = poly.npoints;
		for (int i = 0; i < n; i++)
		{
			int x = poly.xpoints[i];
			int y = poly.ypoints[i];
			int next = (i + 1) % n;
			int prev = (i + n - 1) % n;
			graphics.drawLine(x, y, x + (int) ((poly.xpoints[next] - x) * CORNER_FRACTION), y + (int) ((poly.ypoints[next] - y) * CORNER_FRACTION));
			graphics.drawLine(x, y, x + (int) ((poly.xpoints[prev] - x) * CORNER_FRACTION), y + (int) ((poly.ypoints[prev] - y) * CORNER_FRACTION));
		}
	}
}
