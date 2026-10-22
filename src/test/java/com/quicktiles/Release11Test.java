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
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.config.Keybind;
import net.runelite.http.api.RuneLiteAPI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Quick Tiles 1.1: notes, undo and redo, 8 colours, Ctrl shortcuts.
 */
public class Release11Test
{
	private static final Color YELLOW = new Color(255, 215, 0);
	private static final Color BLUE = new Color(0, 200, 255);
	private final TileStore store = new TileStore(null, RuneLiteAPI.GSON);

	/**
	 * Regions in memory, standing in for the saved ones.
	 */
	private static final class Memory implements TileHistory.Regions
	{
		final Map<Integer, List<TilePoint>> saved = new HashMap<>();

		@Override
		public List<TilePoint> get(int regionId)
		{
			return new ArrayList<>(saved.getOrDefault(regionId, new ArrayList<>()));
		}

		@Override
		public void save(int regionId, List<TilePoint> tiles)
		{
			saved.put(regionId, new ArrayList<>(tiles));
		}
	}

	private static TilePoint tile(int x)
	{
		return new TilePoint(12850, x, 10, 0, null, null);
	}

	// ---- saving: notes ride along, Ground Markers still reads it ----

	@Test
	public void noteSurvivesSaving()
	{
		List<TilePoint> tiles = Arrays.asList(tile(1).withColor(YELLOW).withNote("Stand here for spawn"));
		TilePoint back = store.parse(store.toJson(tiles)).get(0);
		assertEquals("Stand here for spawn", back.getNote());
		assertEquals(YELLOW, back.getColor());
	}

	@Test
	public void oldSavesAndGroundMarkersHaveNoNote()
	{
		List<TilePoint> tiles = store.parse("[{\"regionId\":12850,\"regionX\":20,\"regionY\":31,\"z\":0,\"color\":\"#FFFF0000\"}]");
		assertNull(tiles.get(0).getNote());
	}

	@Test
	public void changesKeepTheOtherFields()
	{
		TilePoint t = tile(1).withColor(YELLOW).withLabel("A").withNote("note");
		TilePoint recoloured = t.withColor(BLUE);
		assertEquals("A", recoloured.getLabel());
		assertEquals("note", recoloured.getNote());
		// The same tile, whatever its colour and note.
		assertEquals(t, tile(1));
	}

	// ---- undo / redo ----

	@Test
	public void undoAndRedoAStroke()
	{
		Memory regions = new Memory();
		TileHistory history = new TileHistory();
		history.begin();
		for (int x = 0; x < 3; x++)
		{
			List<TilePoint> tiles = regions.get(12850);
			history.touch(12850, tiles);
			TileEdits.apply(tiles, tile(x), TileEdits.Mode.PAINT, YELLOW);
			regions.save(12850, tiles);
		}
		history.end(regions);
		assertEquals(3, regions.get(12850).size());

		// One undo takes back the whole stroke.
		assertTrue(history.undo(regions));
		assertTrue(regions.get(12850).isEmpty());
		assertFalse(history.undo(regions));

		assertTrue(history.redo(regions));
		assertEquals(3, regions.get(12850).size());
		assertFalse(history.redo(regions));
	}

	@Test
	public void editsThatChangeNothingAreNotKept()
	{
		Memory regions = new Memory();
		TileHistory history = new TileHistory();
		history.begin();
		history.touch(12850, regions.get(12850));
		history.end(regions);
		assertFalse(history.canUndo());
	}

	@Test
	public void aNewEditClearsRedo()
	{
		Memory regions = new Memory();
		TileHistory history = new TileHistory();
		mark(history, regions, 1);
		history.undo(regions);
		assertTrue(history.canRedo());
		mark(history, regions, 2);
		assertFalse(history.canRedo());
	}

	@Test
	public void historyIsCapped()
	{
		Memory regions = new Memory();
		TileHistory history = new TileHistory();
		for (int x = 0; x < TileHistory.LIMIT + 10; x++)
		{
			mark(history, regions, x % 60);
		}
		int undone = 0;
		while (history.undo(regions))
		{
			undone++;
		}
		assertEquals(TileHistory.LIMIT, undone);
	}

	@Test
	public void colourChangesCanBeUndone()
	{
		Memory regions = new Memory();
		regions.save(12850, Arrays.asList(tile(1).withColor(YELLOW)));
		TileHistory history = new TileHistory();
		history.begin();
		List<TilePoint> tiles = regions.get(12850);
		history.touch(12850, tiles);
		TileEdits.apply(tiles, tile(1), TileEdits.Mode.PAINT, BLUE);
		regions.save(12850, tiles);
		history.end(regions);
		history.undo(regions);
		assertEquals(YELLOW, regions.get(12850).get(0).getColor());
	}

	private static void mark(TileHistory history, Memory regions, int x)
	{
		history.begin();
		List<TilePoint> tiles = regions.get(12850);
		history.touch(12850, tiles);
		TileEdits.apply(tiles, tile(x), TileEdits.Mode.PAINT, YELLOW);
		regions.save(12850, tiles);
		history.end(regions);
	}



	// ---- hotkeys ----

	@Test
	public void ctrlShortcutsByDefault()
	{
		QuickTilesConfig config = new QuickTilesConfig()
		{
		};
		assertEquals(new Keybind(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), config.undoHotkey());
		assertEquals(new Keybind(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), config.redoHotkey());
		assertEquals(new Keybind(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK), config.colourHotkey());
		// 1.0's own keys are still unset until you pick them.
		assertEquals(Keybind.NOT_SET, config.markHotkey());
		assertEquals(Keybind.NOT_SET, config.toggleHotkey());
		// The right-click option is unchanged.
		assertEquals(QuickTilesConfig.MenuMode.ALWAYS, config.rightClick());
	}

	@Test
	public void everyPanelHotkeyHasAName()
	{
		assertEquals(5, QuickTilesPlugin.hotkeys().length);
		assertEquals(8, QuickTilesPlugin.COLOUR_COUNT);
		assertEquals("Not set", QuickTilesPanel.keyText(Keybind.NOT_SET));
	}
}
