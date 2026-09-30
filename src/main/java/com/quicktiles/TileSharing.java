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

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.menus.WidgetMenuOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Import, Export, Clear and Copy Ground Markers on the world map orb's right-click menu.
 */
@Singleton
class TileSharing
{
	private static final Logger log = LoggerFactory.getLogger(TileSharing.class);
	private static final String TARGET = "Quick Tiles";

	private static final WidgetMenuOption EXPORT = option("Export");
	private static final WidgetMenuOption IMPORT = option("Import");
	private static final WidgetMenuOption COPY_GROUND_MARKERS = option("Copy Ground Markers to");
	private static final WidgetMenuOption CLEAR = option("Clear");

	private final QuickTilesPlugin plugin;
	private final Client client;
	private final MenuManager menuManager;
	private final ChatboxPanelManager chatboxPanelManager;
	private final TileStore store;

	@Inject
	TileSharing(QuickTilesPlugin plugin, Client client, MenuManager menuManager,
		ChatboxPanelManager chatboxPanelManager, TileStore store)
	{
		this.plugin = plugin;
		this.client = client;
		this.menuManager = menuManager;
		this.chatboxPanelManager = chatboxPanelManager;
		this.store = store;
	}

	private static WidgetMenuOption option(String name)
	{
		return new WidgetMenuOption(name, TARGET, InterfaceID.Orbs.WORLDMAP, InterfaceID.OrbsNomap.WORLDMAP);
	}

	void addMenuOptions()
	{
		menuManager.addManagedCustomMenu(EXPORT, e -> exportTiles());
		menuManager.addManagedCustomMenu(IMPORT, e -> promptImport());
		menuManager.addManagedCustomMenu(COPY_GROUND_MARKERS, e -> promptCopyGroundMarkers());
		menuManager.addManagedCustomMenu(CLEAR, this::promptClear);
	}

	void removeMenuOptions()
	{
		menuManager.removeManagedCustomMenu(EXPORT);
		menuManager.removeManagedCustomMenu(IMPORT);
		menuManager.removeManagedCustomMenu(COPY_GROUND_MARKERS);
		menuManager.removeManagedCustomMenu(CLEAR);
	}

	private int[] loadedRegions()
	{
		WorldView wv = client.getTopLevelWorldView();
		int[] regions = wv == null ? null : wv.getMapRegions();
		return regions == null ? new int[0] : regions;
	}

	private void exportTiles()
	{
		List<TilePoint> tiles = new ArrayList<>();
		for (int region : loadedRegions())
		{
			tiles.addAll(store.get(region));
		}
		if (tiles.isEmpty())
		{
			plugin.chat("Quick Tiles: there are no marked tiles nearby to export.");
			return;
		}
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(store.toJson(tiles)), null);
		plugin.chat("Quick Tiles: copied " + count(tiles.size()) + " to your clipboard. They also paste into Ground Markers.");
	}

	private void promptImport()
	{
		String text;
		try
		{
			text = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
		}
		catch (IOException | UnsupportedFlavorException | IllegalStateException e)
		{
			log.debug("Couldn't read the clipboard", e);
			plugin.chat("Quick Tiles: couldn't read your clipboard.");
			return;
		}

		List<TilePoint> tiles = store.parse(text);
		if (tiles.isEmpty())
		{
			plugin.chat("Quick Tiles: your clipboard doesn't have any tiles. Copy a tile export (Quick Tiles, Ground Markers or a tile pack) first.");
			return;
		}
		chatboxPanelManager.openTextMenuInput("Import " + count(tiles.size()) + " from your clipboard?")
			.option("Yes", () -> importTiles(tiles, "from your clipboard"))
			.option("No", () ->
			{
			})
			.build();
	}

	private void promptCopyGroundMarkers()
	{
		List<TilePoint> tiles = new ArrayList<>();
		for (int region : store.groundMarkerRegions())
		{
			tiles.addAll(store.groundMarkers(region));
		}
		if (tiles.isEmpty())
		{
			plugin.chat("Quick Tiles: you have no Ground Markers tiles to copy.");
			return;
		}
		chatboxPanelManager.openTextMenuInput("Copy all " + count(tiles.size()) + " from Ground Markers?<br>Your Ground Markers stay as they are.")
			.option("Yes", () -> importTiles(tiles, "from Ground Markers"))
			.option("No", () ->
			{
			})
			.build();
	}

	private void importTiles(List<TilePoint> incoming, String source)
	{
		Map<Integer, List<TilePoint>> byRegion = new LinkedHashMap<>();
		for (TilePoint point : incoming)
		{
			byRegion.computeIfAbsent(point.getRegionId(), r -> new ArrayList<>()).add(point);
		}
		int added = 0;
		for (Map.Entry<Integer, List<TilePoint>> entry : byRegion.entrySet())
		{
			List<TilePoint> tiles = store.get(entry.getKey());
			int regionAdded = TileEdits.merge(tiles, entry.getValue());
			if (regionAdded > 0)
			{
				store.save(entry.getKey(), tiles);
				added += regionAdded;
			}
		}
		plugin.loadTiles();
		int skipped = incoming.size() - added;
		plugin.chat("Quick Tiles: added " + count(added) + " " + source + "."
			+ (skipped > 0 ? " " + count(skipped) + " you already had were skipped." : ""));
	}

	private void promptClear(MenuEntry entry)
	{
		int[] regions = loadedRegions();
		int total = 0;
		for (int region : regions)
		{
			total += store.get(region).size();
		}
		if (total == 0)
		{
			plugin.chat("Quick Tiles: there are no marked tiles nearby to clear.");
			return;
		}
		int cleared = total;
		chatboxPanelManager.openTextMenuInput("Clear the " + count(total) + " marked nearby?<br>Tip: Export first if you might want them back.")
			.option("Yes", () ->
			{
				for (int region : regions)
				{
					store.save(region, new ArrayList<>());
				}
				plugin.loadTiles();
				plugin.chat("Quick Tiles: cleared " + count(cleared) + ".");
			})
			.option("No", () ->
			{
			})
			.build();
	}

	static String count(int tiles)
	{
		return tiles + (tiles == 1 ? " tile" : " tiles");
	}
}
