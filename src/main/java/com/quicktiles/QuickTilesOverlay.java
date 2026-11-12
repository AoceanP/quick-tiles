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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Tile;
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
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

class QuickTilesOverlay extends Overlay
{
	private static final int MAX_DRAW_DISTANCE = 32;
	/**
	 * How far along each edge a corner line reaches, in the Corners style.
	 */
	private static final double CORNER_FRACTION = 0.3;

	private static final Color AREA_OUTLINE = new Color(255, 255, 255, 200);
	private static final Color AREA_EXPORT_OUTLINE = new Color(0, 200, 255, 220);
	private static final Color SIGHT_PIN = new Color(232, 89, 60);

	private final Client client;
	private final QuickTilesPlugin plugin;
	private final QuickTilesConfig config;
	private final TooltipManager tooltipManager;

	@Inject
	QuickTilesOverlay(Client client, QuickTilesPlugin plugin, QuickTilesConfig config, TooltipManager tooltipManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.tooltipManager = tooltipManager;
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(PRIORITY_LOW);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		// Line of sight and the area outline show even with tiles hidden: you asked for them.
		drawSight(graphics);
		drawArea(graphics);

		Map<Integer, List<MarkedTile>> marked = plugin.getMarked();
		if (!plugin.isTilesVisible() || marked.isEmpty())
		{
			return null;
		}

		Stroke stroke = new BasicStroke(config.borderWidth());
		WorldPoint playerTile = player.getWorldLocation();
		boolean checkDistance = player.getWorldView().isTopLevel();
		WorldPoint hovered = hoveredSceneTile(player);
		String hoverNote = null;

		if (config.tileLines())
		{
			drawLines(graphics, marked, playerTile, checkDistance);
		}
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
				if (tile.getNote() != null && point.equals(hovered))
				{
					hoverNote = tile.getNote();
				}
			}
		}
		if (hoverNote != null && !client.isMenuOpen())
		{
			tooltipManager.add(new Tooltip(hoverNote));
		}
		return null;
	}

	/**
	 * The scene tile under the mouse, in the same coordinates as marked tiles.
	 */
	private WorldPoint hoveredSceneTile(Player player)
	{
		WorldView wv = player.getWorldView();
		Tile tile = wv == null ? null : wv.getSelectedSceneTile();
		if (tile == null && wv != null && !wv.isTopLevel())
		{
			tile = client.getTopLevelWorldView().getSelectedSceneTile();
		}
		return tile == null ? null : tile.getWorldLocation();
	}

	/**
	 * The pinned tile and every tile with a clear line to it, worked out once when pinned.
	 */
	private void drawSight(Graphics2D graphics)
	{
		WorldPoint pin = plugin.getSightPin();
		WorldView wv = pin == null ? null : client.getWorldView(plugin.getSightWorldView());
		if (pin == null || wv == null || pin.getPlane() != wv.getPlane())
		{
			return;
		}
		Color colour = config.sightColour();
		Stroke thin = new BasicStroke(1);
		boolean fill = config.sightStyle() == QuickTilesConfig.SightStyle.FILL;
		Color border = new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), Math.min(255, colour.getAlpha() * 2 + 40));
		for (WorldPoint point : plugin.getSightTiles())
		{
			LocalPoint lp = LocalPoint.fromWorld(wv, point);
			Polygon poly = lp == null ? null : Perspective.getCanvasTilePoly(client, lp);
			if (poly != null)
			{
				OverlayUtil.renderPolygon(graphics, poly, border, fill ? colour : new Color(0, 0, 0, 0), thin);
			}
		}
		LocalPoint pinLocal = LocalPoint.fromWorld(wv, pin);
		Polygon pinPoly = pinLocal == null ? null : Perspective.getCanvasTilePoly(client, pinLocal);
		if (pinPoly != null)
		{
			OverlayUtil.renderPolygon(graphics, pinPoly, SIGHT_PIN, new Color(232, 89, 60, 60), new BasicStroke(2));
		}
	}

	/**
	 * While choosing the second corner, an outline of the area from the first corner to the mouse.
	 */
	private void drawArea(Graphics2D graphics)
	{
		WorldPoint start = plugin.getAreaStart();
		WorldPoint end = start == null ? null : plugin.hoveredTile();
		if (start == null)
		{
			return;
		}
		if (end == null || !TileArea.isValid(start, end))
		{
			end = start;
		}
		Color colour = plugin.isAreaExport() ? AREA_EXPORT_OUTLINE : AREA_OUTLINE;
		Stroke stroke = new BasicStroke(1.5f);
		WorldView wv = client.getTopLevelWorldView();
		for (WorldPoint saved : TileArea.edge(start, end))
		{
			for (WorldPoint point : WorldPoint.toLocalInstance(wv, saved))
			{
				LocalPoint lp = LocalPoint.fromWorld(wv, point);
				Polygon poly = lp == null ? null : Perspective.getCanvasTilePoly(client, lp);
				if (poly != null)
				{
					OverlayUtil.renderPolygon(graphics, poly, colour, new Color(255, 255, 255, 25), stroke);
				}
			}
		}
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

		String label = tileText(config.tileNumbers() ? tile.getNumber() : 0, config.showLabels() ? tile.getLabel() : null);
		if (label != null)
		{
			Point textPoint = Perspective.getCanvasTextLocation(client, graphics, lp, label, 0);
			if (textPoint != null)
			{
				OverlayUtil.renderTextLocation(graphics, textPoint, label, colour);
			}
		}
	}

	/**
	 * WHAT: The text drawn on a tile: its number, its label, both ("3 Spawn"), or nothing.
	 * WHY: Numbers and labels share the tile's middle, so they're joined rather than drawn over each other.
	 * PRE: number is 0 when numbers are off; label is null when labels are off.
	 * POST: Nothing changes.
	 * RETURN: the text, or null for none
	 * THROWS: Nothing.
	 */
	static String tileText(int number, String label)
	{
		boolean hasLabel = label != null && !label.isEmpty();
		if (number <= 0)
		{
			return hasLabel ? label : null;
		}
		return hasLabel ? number + " " + label : String.valueOf(number);
	}

	/**
	 * WHAT: Joins each group's tiles with a line, in the order they were marked.
	 * WHY: Shows a route or a rotation at a glance; off by default.
	 * PRE: Client thread. marked is worldview id to tiles, each list in marking order.
	 * POST: Lines are drawn between consecutive tiles of a group that are both on screen.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	private void drawLines(Graphics2D graphics, Map<Integer, List<MarkedTile>> marked, WorldPoint playerTile, boolean checkDistance)
	{
		Stroke line = new BasicStroke(Math.max(1.5f, (float) config.borderWidth()));
		for (Map.Entry<Integer, List<MarkedTile>> entry : marked.entrySet())
		{
			WorldView wv = client.getWorldView(entry.getKey());
			if (wv == null)
			{
				continue;
			}
			Map<String, Point> last = new HashMap<>();
			for (MarkedTile tile : entry.getValue())
			{
				WorldPoint point = tile.getPoint();
				if (point.getPlane() != wv.getPlane() || (checkDistance && point.distanceTo(playerTile) >= MAX_DRAW_DISTANCE))
				{
					last.remove(tile.getGroup());
					continue;
				}
				LocalPoint lp = LocalPoint.fromWorld(wv, point);
				Point here = lp == null ? null : Perspective.localToCanvas(client, lp, wv.getPlane());
				Point before = here == null ? null : last.get(tile.getGroup());
				if (before != null)
				{
					graphics.setColor(tile.getColor() == null ? config.colour1() : tile.getColor());
					graphics.setStroke(line);
					graphics.drawLine(before.getX(), before.getY(), here.getX(), here.getY());
				}
				if (here == null)
				{
					last.remove(tile.getGroup());
				}
				else
				{
					last.put(tile.getGroup(), here);
				}
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
