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
import net.runelite.http.api.RuneLiteAPI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TileStoreTest
{
	// Only parse and toJson are used, which don't touch the config manager.
	private final TileStore store = new TileStore(null, RuneLiteAPI.GSON);

	@Test
	public void readsGroundMarkersExport()
	{
		String groundMarkers = "[{\"regionId\":12850,\"regionX\":20,\"regionY\":31,\"z\":0,\"color\":\"#FFFF0000\"},"
			+ "{\"regionId\":12850,\"regionX\":21,\"regionY\":31,\"z\":0,\"color\":\"#FF00FF00\",\"label\":\"stand\"},"
			+ "{\"regionId\":12850,\"regionX\":22,\"regionY\":31,\"z\":0}]";
		List<TilePoint> tiles = store.parse(groundMarkers);
		assertEquals(3, tiles.size());
		assertEquals(new Color(255, 0, 0), tiles.get(0).getColor());
		assertEquals("stand", tiles.get(1).getLabel());
		assertNull(tiles.get(2).getColor());
	}

	@Test
	public void roundTripsThroughJson()
	{
		List<TilePoint> tiles = Arrays.asList(
			new TilePoint(12850, 1, 2, 0, new Color(0, 200, 255, 128), "a"),
			new TilePoint(12851, 3, 4, 1, null, null));
		List<TilePoint> back = store.parse(store.toJson(tiles));
		assertEquals(tiles, back);
		assertEquals(new Color(0, 200, 255, 128), back.get(0).getColor());
		assertEquals("a", back.get(0).getLabel());
	}

	@Test
	public void ignoresJunk()
	{
		assertTrue(store.parse("hello").isEmpty());
		assertTrue(store.parse("{\"regionId\":1}").isEmpty());
		assertTrue(store.parse("").isEmpty());
		assertTrue(store.parse(null).isEmpty());
		assertTrue(store.parse("[{\"regionId\":12850,\"regionX\":80,\"regionY\":1,\"z\":0}]").isEmpty());
	}
}
