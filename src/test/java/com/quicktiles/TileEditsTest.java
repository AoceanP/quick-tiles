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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TileEditsTest
{
	private static final Color YELLOW = Color.YELLOW;
	private static final Color RED = Color.RED;

	private static TilePoint tile(int x, Color color)
	{
		return new TilePoint(12850, x, 10, 0, color, null);
	}

	@Test
	public void strokeOnEmptyTilePaints()
	{
		assertEquals(TileEdits.Mode.PAINT, TileEdits.modeFor(null, YELLOW));
	}

	@Test
	public void strokeOnSameColourErases()
	{
		assertEquals(TileEdits.Mode.ERASE, TileEdits.modeFor(tile(1, YELLOW), YELLOW));
	}

	@Test
	public void strokeOnOtherColourRecolours()
	{
		assertEquals(TileEdits.Mode.PAINT, TileEdits.modeFor(tile(1, RED), YELLOW));
	}

	@Test
	public void paintAddsThenIgnoresRepeat()
	{
		List<TilePoint> tiles = new ArrayList<>();
		assertTrue(TileEdits.apply(tiles, tile(1, null), TileEdits.Mode.PAINT, YELLOW));
		assertFalse(TileEdits.apply(tiles, tile(1, null), TileEdits.Mode.PAINT, YELLOW));
		assertEquals(1, tiles.size());
		assertEquals(YELLOW, tiles.get(0).getColor());
	}

	@Test
	public void paintRecoloursAndKeepsLabel()
	{
		List<TilePoint> tiles = new ArrayList<>(Arrays.asList(tile(1, RED).withLabel("safe")));
		assertTrue(TileEdits.apply(tiles, tile(1, null), TileEdits.Mode.PAINT, YELLOW));
		assertEquals(YELLOW, tiles.get(0).getColor());
		assertEquals("safe", tiles.get(0).getLabel());
	}

	@Test
	public void eraseRemovesOnlyMarkedTiles()
	{
		List<TilePoint> tiles = new ArrayList<>(Arrays.asList(tile(1, YELLOW), tile(2, RED)));
		assertTrue(TileEdits.apply(tiles, tile(2, null), TileEdits.Mode.ERASE, YELLOW));
		assertFalse(TileEdits.apply(tiles, tile(3, null), TileEdits.Mode.ERASE, YELLOW));
		assertEquals(Arrays.asList(tile(1, null)), tiles);
	}

	@Test
	public void mergeSkipsDuplicatesAndBadTiles()
	{
		List<TilePoint> tiles = new ArrayList<>(Arrays.asList(tile(1, YELLOW)));
		int added = TileEdits.merge(tiles, Arrays.asList(tile(1, RED), tile(2, RED), new TilePoint(12850, 99, 0, 0, RED, null), null));
		assertEquals(1, added);
		assertEquals(2, tiles.size());
		assertEquals(YELLOW, tiles.get(0).getColor());
	}
}
