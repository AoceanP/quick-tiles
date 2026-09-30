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

import com.google.inject.Provides;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.WorldViewLoaded;
import net.runelite.api.events.WorldViewUnloaded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.HotkeyListener;

@PluginDescriptor(
	name = "Quick Tiles",
	description = "Mark tiles with a hotkey or one right-click, and paint many at once by holding the key",
	tags = {"tile", "tiles", "marker", "ground", "hotkey", "paint", "overlay"}
)
public class QuickTilesPlugin extends Plugin
{
	static final int COLOUR_COUNT = 4;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private QuickTilesConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private QuickTilesOverlay overlay;

	@Inject
	private QuickTilesMinimapOverlay minimapOverlay;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ChatboxPanelManager chatboxPanelManager;

	@Inject
	private TileStore store;

	@Inject
	private TileSharing sharing;

	/**
	 * Marked tiles in the loaded scene, by world view id (the main world, or a boat).
	 */
	private final Map<Integer, List<MarkedTile>> marked = new HashMap<>();

	private boolean tilesVisible = true;

	// The paint stroke in progress while the mark hotkey is held.
	private boolean stroking;
	@Nullable
	private TileEdits.Mode strokeMode;
	@Nullable
	private WorldPoint lastStrokeTile;

	private final HotkeyListener markListener = new HotkeyListener(() -> config.markHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(QuickTilesPlugin.this::startStroke);
		}

		@Override
		public void hotkeyReleased()
		{
			clientThread.invoke(QuickTilesPlugin.this::endStroke);
		}
	};

	private final HotkeyListener colourListener = new HotkeyListener(() -> config.colourHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(QuickTilesPlugin.this::nextColour);
		}
	};

	private final HotkeyListener toggleListener = new HotkeyListener(() -> config.toggleHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(QuickTilesPlugin.this::toggleVisible);
		}
	};

	@Provides
	QuickTilesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(QuickTilesConfig.class);
	}

	@Override
	protected void startUp()
	{
		tilesVisible = true;
		overlayManager.add(overlay);
		overlayManager.add(minimapOverlay);
		keyManager.registerKeyListener(markListener);
		keyManager.registerKeyListener(colourListener);
		keyManager.registerKeyListener(toggleListener);
		if (config.orbOptions())
		{
			sharing.addMenuOptions();
		}
		clientThread.invoke(() -> loadTiles());
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayManager.remove(minimapOverlay);
		keyManager.unregisterKeyListener(markListener);
		keyManager.unregisterKeyListener(colourListener);
		keyManager.unregisterKeyListener(toggleListener);
		sharing.removeMenuOptions();
		stroking = false;
		marked.clear();
		store.clearCache();
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		clientThread.invoke(() ->
		{
			store.clearCache();
			loadTiles();
		});
	}

	@Subscribe
	public void onWorldViewLoaded(WorldViewLoaded event)
	{
		loadTiles(event.getWorldView());
	}

	@Subscribe
	public void onWorldViewUnloaded(WorldViewUnloaded event)
	{
		marked.remove(event.getWorldView().getId());
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!QuickTilesConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if (event.getKey().startsWith(TileStore.REGION_PREFIX))
		{
			// Tiles changed outside this plugin, e.g. a profile sync.
			clientThread.invoke(() ->
			{
				store.clearCache();
				loadTiles();
			});
		}
		else if (QuickTilesConfig.ORB_OPTIONS_KEY.equals(event.getKey()))
		{
			sharing.removeMenuOptions();
			if (config.orbOptions())
			{
				sharing.addMenuOptions();
			}
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (stroking)
		{
			paintHoveredTile();
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		MenuEntry entry = event.getMenuEntry();
		MenuAction type = entry.getType();
		if (type != MenuAction.WALK && type != MenuAction.SET_HEADING)
		{
			return;
		}

		boolean shift = client.isKeyPressed(KeyCode.KC_SHIFT);
		QuickTilesConfig.MenuMode mode = config.rightClick();
		if (mode == QuickTilesConfig.MenuMode.OFF || (mode == QuickTilesConfig.MenuMode.SHIFT && !shift))
		{
			return;
		}

		WorldView wv = client.getWorldView(entry.getWorldViewId());
		Tile tile = wv == null ? null : wv.getSelectedSceneTile();
		if (tile == null)
		{
			return;
		}

		WorldPoint point = WorldPoint.fromLocalInstance(client, tile.getLocalLocation());
		TilePoint target = TilePoint.of(point, null);
		List<TilePoint> tiles = store.get(target.getRegionId());
		int index = tiles.indexOf(target);
		TilePoint existing = index >= 0 ? tiles.get(index) : null;
		Color brush = brushColour();

		Menu menu = client.getMenu();
		menu.createMenuEntry(-1)
			.setOption(existing == null ? "Mark" : "Unmark")
			.setTarget(ColorUtil.wrapWithColorTag("Tile", existing == null ? brush : colourOf(existing)))
			.setType(MenuAction.RUNELITE)
			.onClick(e -> toggleTile(point));

		if (existing == null || !shift)
		{
			return;
		}

		menu.createMenuEntry(-2)
			.setOption("Label")
			.setTarget(ColorUtil.wrapWithColorTag("Tile", colourOf(existing)))
			.setType(MenuAction.RUNELITE)
			.onClick(e -> promptLabel(existing));

		MenuEntry colourEntry = menu.createMenuEntry(-3)
			.setOption("Colour")
			.setTarget(ColorUtil.wrapWithColorTag("Tile", colourOf(existing)))
			.setType(MenuAction.RUNELITE);
		Menu submenu = colourEntry.createSubMenu();
		for (int i = COLOUR_COUNT - 1; i >= 0; i--)
		{
			Color colour = colour(i);
			submenu.createMenuEntry(0)
				.setOption(ColorUtil.prependColorTag("Colour " + (i + 1), colour))
				.setType(MenuAction.RUNELITE)
				.onClick(e -> recolour(existing, colour));
		}
	}

	// ---- editing ----

	private void startStroke()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		stroking = true;
		strokeMode = null;
		lastStrokeTile = null;
		paintHoveredTile();
	}

	private void endStroke()
	{
		stroking = false;
		strokeMode = null;
		lastStrokeTile = null;
	}

	/**
	 * Applies the stroke to the tile under the mouse, once per tile.
	 */
	private void paintHoveredTile()
	{
		WorldPoint point = hoveredTile();
		if (point == null || point.equals(lastStrokeTile))
		{
			return;
		}
		lastStrokeTile = point;

		TilePoint target = TilePoint.of(point, null);
		List<TilePoint> tiles = store.get(target.getRegionId());
		Color brush = brushColour();
		if (strokeMode == null)
		{
			int index = tiles.indexOf(target);
			strokeMode = TileEdits.modeFor(index >= 0 ? tiles.get(index) : null, brush);
		}
		if (TileEdits.apply(tiles, target, strokeMode, brush))
		{
			store.save(target.getRegionId(), tiles);
			loadTiles();
		}
	}

	private void toggleTile(WorldPoint point)
	{
		TilePoint target = TilePoint.of(point, null);
		List<TilePoint> tiles = store.get(target.getRegionId());
		TileEdits.Mode mode = tiles.contains(target) ? TileEdits.Mode.ERASE : TileEdits.Mode.PAINT;
		if (TileEdits.apply(tiles, target, mode, brushColour()))
		{
			store.save(target.getRegionId(), tiles);
			loadTiles();
		}
	}

	private void recolour(TilePoint existing, Color colour)
	{
		List<TilePoint> tiles = store.get(existing.getRegionId());
		if (TileEdits.apply(tiles, existing, TileEdits.Mode.PAINT, colour))
		{
			store.save(existing.getRegionId(), tiles);
			loadTiles();
		}
	}

	private void promptLabel(TilePoint existing)
	{
		chatboxPanelManager.openTextInput("Tile label")
			.value(existing.getLabel() == null ? "" : existing.getLabel())
			.onDone((String input) -> clientThread.invoke(() -> setLabel(existing, input)))
			.build();
	}

	private void setLabel(TilePoint existing, String input)
	{
		String label = input == null || input.trim().isEmpty() ? null : input.trim();
		List<TilePoint> tiles = store.get(existing.getRegionId());
		int index = tiles.indexOf(existing);
		if (index < 0)
		{
			return;
		}
		tiles.set(index, tiles.get(index).withLabel(label));
		store.save(existing.getRegionId(), tiles);
		loadTiles();
	}

	private void nextColour()
	{
		int next = (brushIndex() + 1) % COLOUR_COUNT;
		configManager.setConfiguration(QuickTilesConfig.GROUP, QuickTilesConfig.BRUSH_KEY, next);
		chat("Quick Tiles: now marking in " + ColorUtil.wrapWithColorTag("Colour " + (next + 1), colour(next)) + ".");
	}

	private void toggleVisible()
	{
		tilesVisible = !tilesVisible;
		chat(tilesVisible ? "Quick Tiles: tiles shown." : "Quick Tiles: tiles hidden. Press your show/hide hotkey again to bring them back.");
	}

	/**
	 * The world tile under the mouse, or null when the mouse isn't over the game world.
	 */
	@Nullable
	private WorldPoint hoveredTile()
	{
		if (client.isMenuOpen())
		{
			return null;
		}
		Player player = client.getLocalPlayer();
		WorldView top = client.getTopLevelWorldView();
		if (player == null || top == null)
		{
			return null;
		}
		// On a boat, prefer the deck under the mouse.
		WorldView own = player.getWorldView();
		Tile tile = own != null && !own.isTopLevel() ? own.getSelectedSceneTile() : null;
		if (tile == null)
		{
			tile = top.getSelectedSceneTile();
		}
		return tile == null ? null : WorldPoint.fromLocalInstance(client, tile.getLocalLocation());
	}

	// ---- loading ----

	void loadTiles()
	{
		marked.clear();
		WorldView top = client.getTopLevelWorldView();
		if (top == null)
		{
			return;
		}
		loadTiles(top);
		for (WorldEntity entity : top.worldEntities())
		{
			loadTiles(entity.getWorldView());
		}
	}

	private void loadTiles(WorldView wv)
	{
		int[] regions = wv.getMapRegions();
		if (regions == null)
		{
			marked.remove(wv.getId());
			return;
		}
		List<MarkedTile> tiles = new ArrayList<>();
		for (int region : regions)
		{
			for (TilePoint saved : store.get(region))
			{
				for (WorldPoint local : WorldPoint.toLocalInstance(wv, saved.toWorldPoint()))
				{
					tiles.add(new MarkedTile(local, saved.getColor(), saved.getLabel()));
				}
			}
		}
		marked.put(wv.getId(), tiles);
	}

	// ---- for overlays and sharing ----

	Map<Integer, List<MarkedTile>> getMarked()
	{
		return Collections.unmodifiableMap(marked);
	}

	boolean isTilesVisible()
	{
		return tilesVisible;
	}

	int brushIndex()
	{
		return Math.floorMod(config.brush(), COLOUR_COUNT);
	}

	Color brushColour()
	{
		return colour(brushIndex());
	}

	Color colour(int index)
	{
		switch (index)
		{
			case 1:
				return config.colour2();
			case 2:
				return config.colour3();
			case 3:
				return config.colour4();
			default:
				return config.colour1();
		}
	}

	/**
	 * Tiles imported without a colour use colour 1.
	 */
	Color colourOf(@Nullable TilePoint tile)
	{
		return tile == null || tile.getColor() == null ? config.colour1() : tile.getColor();
	}

	void chat(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(message)
			.build());
	}
}
