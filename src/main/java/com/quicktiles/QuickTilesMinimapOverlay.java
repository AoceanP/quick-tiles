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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

class QuickTilesMinimapOverlay extends Overlay
{
	private final Client client;
	private final QuickTilesPlugin plugin;
	private final QuickTilesConfig config;

	@Inject
	QuickTilesMinimapOverlay(Client client, QuickTilesPlugin plugin, QuickTilesConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(PRIORITY_LOW);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.minimap() || !plugin.isTilesVisible())
		{
			return null;
		}
		for (Map.Entry<Integer, List<MarkedTile>> entry : plugin.getMarked().entrySet())
		{
			WorldView wv = client.getWorldView(entry.getKey());
			if (wv == null)
			{
				continue;
			}
			for (MarkedTile tile : entry.getValue())
			{
				if (tile.getPoint().getPlane() != wv.getPlane())
				{
					continue;
				}
				LocalPoint lp = LocalPoint.fromWorld(wv, tile.getPoint());
				if (lp == null)
				{
					continue;
				}
				Polygon poly = minimapPoly(wv, lp);
				if (poly != null)
				{
					Color colour = tile.getColor() == null ? config.colour1() : tile.getColor();
					graphics.setColor(colour);
					graphics.fillPolygon(poly);
				}
			}
		}
		return null;
	}

	private Polygon minimapPoly(WorldView wv, LocalPoint lp)
	{
		int size = Perspective.LOCAL_TILE_SIZE;
		int x = lp.getX() & -size;
		int y = lp.getY() & -size;
		Point p1 = Perspective.localToMinimap(client, new LocalPoint(x, y, wv.getId()));
		Point p2 = Perspective.localToMinimap(client, new LocalPoint(x, y + size, wv.getId()));
		Point p3 = Perspective.localToMinimap(client, new LocalPoint(x + size, y + size, wv.getId()));
		Point p4 = Perspective.localToMinimap(client, new LocalPoint(x + size, y, wv.getId()));
		if (p1 == null || p2 == null || p3 == null || p4 == null)
		{
			return null;
		}
		Polygon poly = new Polygon();
		poly.addPoint(p1.getX(), p1.getY());
		poly.addPoint(p2.getX(), p2.getY());
		poly.addPoint(p3.getX(), p3.getY());
		poly.addPoint(p4.getX(), p4.getY());
		return poly;
	}
}
