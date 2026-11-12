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
import java.util.Map;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * WHAT: A small box with how many marked tiles are around you, in all and per group.
 * WHY: Handy when laying out a big pattern or checking a tile pack loaded; off by default.
 * PRE: Registered by the plugin; renders on the client thread.
 * POST: Not applicable.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
class QuickTilesCounterOverlay extends OverlayPanel
{
	/** At most this many group lines, so a long list doesn't cover the screen. */
	static final int MAX_GROUPS = 6;

	private final QuickTilesPlugin plugin;
	private final QuickTilesConfig config;

	@Inject
	QuickTilesCounterOverlay(QuickTilesPlugin plugin, QuickTilesConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	/**
	 * WHAT: Draws the counts, from the numbers worked out when tiles last loaded.
	 * WHY: Counting every frame would read every saved region; loading already has them.
	 * PRE: Client thread.
	 * POST: The box is drawn, or nothing when turned off or hidden.
	 * RETURN: the box's size, or null
	 * THROWS: Nothing.
	 */
	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.tileCounter() || !plugin.isTilesVisible())
		{
			return null;
		}
		Map<String, Integer> counts = plugin.getNearbyCounts();
		int total = 0;
		for (int c : counts.values())
		{
			total += c;
		}
		panelComponent.getChildren().add(TitleComponent.builder().text("Quick Tiles").color(Color.WHITE).build());
		panelComponent.getChildren().add(LineComponent.builder().left("Nearby").right(String.valueOf(total)).build());
		int shown = 0;
		for (Map.Entry<String, Integer> e : counts.entrySet())
		{
			if (e.getValue() <= 0 || counts.size() < 2)
			{
				continue;
			}
			if (shown++ >= MAX_GROUPS)
			{
				break;
			}
			panelComponent.getChildren().add(LineComponent.builder().left(e.getKey()).right(String.valueOf(e.getValue()))
				.leftColor(new Color(190, 190, 190)).build());
		}
		return super.render(graphics);
	}
}
