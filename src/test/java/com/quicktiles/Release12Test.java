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
import java.util.Arrays;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.http.api.RuneLiteAPI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Quick Tiles 1.2: patterns you can paste anywhere, numbered tiles, lines between tiles and a
 * tile counter. Every new display option is off for everyone until turned on.
 */
public class Release12Test
{
	private static final WorldPoint HERE = new WorldPoint(3200, 3200, 0);
	private final TileStore store = new TileStore(null, RuneLiteAPI.GSON);

	private static TilePoint at(int dx, int dy)
	{
		return TilePoint.of(HERE.dx(dx).dy(dy), Color.YELLOW);
	}

	@Test
	public void aPatternIsTheShapeAroundYou()
	{
		List<TilePoint> saved = Arrays.asList(at(1, 0).withLabel("A").withGroup("Vorkath"), at(-3, 2),
			at(TilePattern.RADIUS + 1, 0), TilePoint.of(new WorldPoint(3201, 3200, 1), null));
		TilePattern pattern = TilePattern.around(HERE, saved, TilePattern.RADIUS);
		assertEquals("Too far, or another floor, is left out", 2, pattern.tiles.size());
		assertEquals(1, pattern.tiles.get(0).dx);
		assertEquals("A", pattern.tiles.get(0).label);
		assertEquals("Vorkath", pattern.tiles.get(0).group);
	}

	@Test
	public void aPatternPastesTheSameShapeSomewhereElse()
	{
		TilePattern pattern = TilePattern.around(HERE, Arrays.asList(at(1, 0).withNote("Stand here"), at(-3, 2)), TilePattern.RADIUS);
		WorldPoint there = new WorldPoint(2272, 4050, 0);
		List<TilePoint> placed = pattern.placeAt(there);
		assertEquals(2, placed.size());
		assertEquals(there.dx(1), placed.get(0).toWorldPoint());
		assertEquals(there.dx(-3).dy(2), placed.get(1).toWorldPoint());
		assertEquals("Stand here", placed.get(0).getNote());
		assertEquals(Color.YELLOW, placed.get(1).getColor());
	}

	@Test
	public void patternTextRoundTripsAndIsToldApartFromExports()
	{
		TilePattern pattern = TilePattern.around(HERE, Arrays.asList(at(2, 2).withLabel("Spot")), TilePattern.RADIUS);
		TilePattern back = store.parsePattern(store.patternJson(pattern));
		assertNotNull(back);
		assertEquals("Spot", back.tiles.get(0).label);
		assertEquals(Color.YELLOW, back.tiles.get(0).color);
		// A normal export is a tile list, not a pattern; junk is neither.
		assertNull(store.parsePattern(store.toJson(Arrays.asList(at(0, 0)))));
		assertNull(store.parsePattern("{\"hello\": 1}"));
		assertNull(store.parsePattern("not json"));
		assertNull(store.parsePattern(null));
		assertFalse(new TilePattern().isValid());
	}

	@Test
	public void numbersAndLabelsShareTheTile()
	{
		assertEquals("3", QuickTilesOverlay.tileText(3, null));
		assertEquals("3 Spawn", QuickTilesOverlay.tileText(3, "Spawn"));
		assertEquals("Spawn", QuickTilesOverlay.tileText(0, "Spawn"));
		assertNull(QuickTilesOverlay.tileText(0, ""));
		assertNull(QuickTilesOverlay.tileText(0, null));
	}

	@Test
	public void markedTilesKnowTheirGroupAndPlace()
	{
		MarkedTile t = new MarkedTile(HERE, Color.RED, null, null, "vorkath", 4);
		assertEquals("vorkath", t.getGroup());
		assertEquals(4, t.getNumber());
		MarkedTile old = new MarkedTile(HERE, Color.RED, "A");
		assertEquals("", old.getGroup());
		assertEquals(0, old.getNumber());
	}

	@Test
	public void newDisplayOptionsAreOffByDefault()
	{
		QuickTilesConfig config = new QuickTilesConfig()
		{
		};
		assertFalse(config.tileNumbers());
		assertFalse(config.tileLines());
		assertFalse(config.tileCounter());
		assertTrue(QuickTilesCounterOverlay.MAX_GROUPS > 0);
	}
}
