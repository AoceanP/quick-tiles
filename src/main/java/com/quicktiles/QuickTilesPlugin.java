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

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.WorldViewLoaded;
import net.runelite.api.events.WorldViewUnloaded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
	name = "Quick Tiles",
	description = "Mark tiles with a hotkey or one right-click, and paint many at once by holding the key",
	tags = {"tile", "tiles", "marker", "ground", "hotkey", "paint", "overlay"}
)
public class QuickTilesPlugin extends Plugin
{
	static final int COLOUR_COUNT = 8;

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
	private QuickTilesCounterOverlay counterOverlay;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ChatboxPanelManager chatboxPanelManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private Gson gson;

	@Inject
	private TileStore store;

	@Inject
	private TileSharing sharing;

	/**
	 * Marked tiles in the loaded scene, by world view id (the main world, or a boat).
	 */
	private final Map<Integer, List<MarkedTile>> marked = new HashMap<>();
	// Tiles shown around you per group name, worked out when tiles load, for the counter.
	private volatile Map<String, Integer> nearbyCounts = Collections.emptyMap();

	private boolean tilesVisible = true;

	// The paint stroke in progress while the mark hotkey is held.
	private boolean stroking;
	@Nullable
	private TileEdits.Mode strokeMode;
	@Nullable
	private WorldPoint lastStrokeTile;

	private final TileHistory history = new TileHistory();
	private final TileHistory.Regions regions = new TileHistory.Regions()
	{
		@Override
		public List<TilePoint> get(int regionId)
		{
			return store.get(regionId);
		}

		@Override
		public void save(int regionId, List<TilePoint> tiles)
		{
			store.save(regionId, tiles);
		}
	};

	private final TileGroups groups = new TileGroups();
	// The group new tiles go into, or null for ungrouped.
	@Nullable
	private String activeGroup;

	// Area select: the first corner, waiting for the second. Saved coordinates, like tiles.
	@Nullable
	private WorldPoint areaStart;
	// The next area is copied to the clipboard instead of being marked.
	private boolean areaExport;

	// Line of sight: the pinned tile and the tiles with a clear line to it, in scene coordinates.
	@Nullable
	private WorldPoint sightPin;
	private int sightWorldView = -1;
	private Set<WorldPoint> sightTiles = Collections.emptySet();

	@Nullable
	private QuickTilesPanel panel;
	@Nullable
	private NavigationButton navButton;

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

	private final HotkeyListener areaListener = new HotkeyListener(() -> config.areaHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(QuickTilesPlugin.this::areaCorner);
		}
	};

	private final HotkeyListener sightListener = new HotkeyListener(() -> config.sightHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(QuickTilesPlugin.this::toggleSight);
		}
	};

	private final HotkeyListener undoListener = new HotkeyListener(() -> config.undoHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(QuickTilesPlugin.this::undo);
		}
	};

	private final HotkeyListener redoListener = new HotkeyListener(() -> config.redoHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(QuickTilesPlugin.this::redo);
		}
	};

	private final HotkeyListener groupListener = new HotkeyListener(() -> config.groupHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(QuickTilesPlugin.this::nextGroup);
		}
	};

	// Esc cancels an area in progress. The key still reaches the game.
	private final KeyListener escListener = new KeyListener()
	{
		@Override
		public void keyTyped(KeyEvent e)
		{
		}

		@Override
		public void keyPressed(KeyEvent e)
		{
			if (e.getKeyCode() == KeyEvent.VK_ESCAPE && areaStart != null)
			{
				clientThread.invoke(QuickTilesPlugin.this::cancelArea);
			}
		}

		@Override
		public void keyReleased(KeyEvent e)
		{
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
		overlayManager.add(counterOverlay);
		for (KeyListener listener : listeners())
		{
			keyManager.registerKeyListener(listener);
		}
		if (config.orbOptions())
		{
			sharing.addMenuOptions();
		}
		loadGroups();
		if (config.sidebarPanel())
		{
			addPanel();
		}
		clientThread.invoke(() -> loadTiles());
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayManager.remove(minimapOverlay);
		overlayManager.remove(counterOverlay);
		for (KeyListener listener : listeners())
		{
			keyManager.unregisterKeyListener(listener);
		}
		sharing.removeMenuOptions();
		removePanel();
		stroking = false;
		areaStart = null;
		areaExport = false;
		clearSight();
		history.clear();
		marked.clear();
		store.clearCache();
	}

	private KeyListener[] listeners()
	{
		return new KeyListener[]{markListener, colourListener, toggleListener, areaListener, sightListener,
			undoListener, redoListener, groupListener, escListener};
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		clientThread.invoke(() ->
		{
			store.clearCache();
			history.clear();
			loadGroups();
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
		if (event.getWorldView().getId() == sightWorldView)
		{
			clearSight();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!QuickTilesConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		String key = event.getKey();
		if (key.startsWith(TileStore.REGION_PREFIX))
		{
			// Tiles changed outside this plugin, e.g. a profile sync.
			clientThread.invoke(() ->
			{
				store.clearCache();
				loadTiles();
			});
		}
		else if (QuickTilesConfig.ORB_OPTIONS_KEY.equals(key))
		{
			sharing.removeMenuOptions();
			if (config.orbOptions())
			{
				sharing.addMenuOptions();
			}
		}
		else if (QuickTilesConfig.PANEL_KEY.equals(key))
		{
			removePanel();
			if (config.sidebarPanel())
			{
				addPanel();
			}
		}
		else if (key.startsWith("sight"))
		{
			clientThread.invoke(this::recomputeSight);
		}
		else
		{
			// Colours and hotkeys show in the panel.
			refreshPanel();
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

		menu.createMenuEntry(-3)
			.setOption("Note")
			.setTarget(ColorUtil.wrapWithColorTag("Tile", colourOf(existing)))
			.setType(MenuAction.RUNELITE)
			.onClick(e -> promptNote(existing));

		MenuEntry groupEntry = menu.createMenuEntry(-4)
			.setOption("Group")
			.setTarget(ColorUtil.wrapWithColorTag("Tile", colourOf(existing)))
			.setType(MenuAction.RUNELITE);
		Menu groupMenu = groupEntry.createSubMenu();
		groupMenu.createMenuEntry(0)
			.setOption("New group...")
			.setType(MenuAction.RUNELITE)
			.onClick(e -> promptNewGroup(existing));
		List<String> names = new ArrayList<>(groups.names());
		Collections.reverse(names);
		for (String name : names)
		{
			groupMenu.createMenuEntry(0)
				.setOption(name.equalsIgnoreCase(String.valueOf(existing.getGroup())) ? name + " (now)" : name)
				.setType(MenuAction.RUNELITE)
				.onClick(e -> setTileGroup(existing, name));
		}
		groupMenu.createMenuEntry(0)
			.setOption(TileGroups.UNGROUPED)
			.setType(MenuAction.RUNELITE)
			.onClick(e -> setTileGroup(existing, null));

		MenuEntry colourEntry = menu.createMenuEntry(-5)
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
		history.begin();
		paintHoveredTile();
	}

	private void endStroke()
	{
		if (stroking)
		{
			history.end(regions);
			refreshPanel();
		}
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

		TilePoint target = newTile(point);
		List<TilePoint> tiles = store.get(target.getRegionId());
		Color brush = brushColour();
		if (strokeMode == null)
		{
			int index = tiles.indexOf(target);
			strokeMode = TileEdits.modeFor(index >= 0 ? tiles.get(index) : null, brush);
		}
		history.touch(target.getRegionId(), tiles);
		if (TileEdits.apply(tiles, target, strokeMode, brush))
		{
			store.save(target.getRegionId(), tiles);
			loadTiles();
		}
	}

	/**
	 * A tile to add at a point, in the group new tiles go into.
	 */
	private TilePoint newTile(WorldPoint point)
	{
		return TilePoint.of(point, null).withGroup(activeGroup);
	}

	private void toggleTile(WorldPoint point)
	{
		TilePoint target = newTile(point);
		List<TilePoint> tiles = store.get(target.getRegionId());
		TileEdits.Mode mode = tiles.contains(target) ? TileEdits.Mode.ERASE : TileEdits.Mode.PAINT;
		history.begin();
		history.touch(target.getRegionId(), tiles);
		if (TileEdits.apply(tiles, target, mode, brushColour()))
		{
			store.save(target.getRegionId(), tiles);
			loadTiles();
		}
		history.end(regions);
		refreshPanel();
	}

	private void recolour(TilePoint existing, Color colour)
	{
		List<TilePoint> tiles = store.get(existing.getRegionId());
		history.begin();
		history.touch(existing.getRegionId(), tiles);
		if (TileEdits.apply(tiles, existing, TileEdits.Mode.PAINT, colour))
		{
			store.save(existing.getRegionId(), tiles);
			loadTiles();
		}
		history.end(regions);
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
		updateTile(existing, t -> t.withLabel(label));
	}

	private void promptNote(TilePoint existing)
	{
		chatboxPanelManager.openTextInput("Tile note (shown when you point at the tile)")
			.value(existing.getNote() == null ? "" : existing.getNote())
			.onDone((String input) -> clientThread.invoke(() ->
			{
				String note = input == null || input.trim().isEmpty() ? null : input.trim();
				updateTile(existing, t -> t.withNote(note));
			}))
			.build();
	}

	private void promptNewGroup(TilePoint existing)
	{
		chatboxPanelManager.openTextInput("New group name")
			.onDone((String input) -> clientThread.invoke(() ->
			{
				String name = addGroup(input);
				if (name != null)
				{
					setTileGroup(existing, name);
				}
			}))
			.build();
	}

	private void setTileGroup(TilePoint existing, @Nullable String group)
	{
		updateTile(existing, t -> t.withGroup(group));
		refreshPanel();
	}

	/**
	 * Changes one saved tile, as an undoable edit.
	 */
	private void updateTile(TilePoint existing, java.util.function.UnaryOperator<TilePoint> change)
	{
		List<TilePoint> tiles = store.get(existing.getRegionId());
		int index = tiles.indexOf(existing);
		if (index < 0)
		{
			return;
		}
		history.begin();
		history.touch(existing.getRegionId(), tiles);
		tiles.set(index, change.apply(tiles.get(index)));
		store.save(existing.getRegionId(), tiles);
		history.end(regions);
		loadTiles();
	}

	void nextColour()
	{
		setBrush((brushIndex() + 1) % COLOUR_COUNT);
		chat("Quick Tiles: now marking in " + ColorUtil.wrapWithColorTag("Colour " + (brushIndex() + 1), brushColour()) + ".");
	}

	void setBrush(int index)
	{
		configManager.setConfiguration(QuickTilesConfig.GROUP, QuickTilesConfig.BRUSH_KEY, Math.floorMod(index, COLOUR_COUNT));
	}

	void toggleVisible()
	{
		tilesVisible = !tilesVisible;
		chat(tilesVisible ? "Quick Tiles: tiles shown." : "Quick Tiles: tiles hidden. Press your show/hide hotkey again to bring them back.");
	}

	// ---- undo ----

	void undo()
	{
		if (stroking)
		{
			return;
		}
		if (history.undo(regions))
		{
			loadTiles();
		}
		refreshPanel();
	}

	void redo()
	{
		if (stroking)
		{
			return;
		}
		if (history.redo(regions))
		{
			loadTiles();
		}
		refreshPanel();
	}

	// ---- area select ----

	private void areaCorner()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		WorldPoint point = hoveredTile();
		if (point == null)
		{
			return;
		}
		if (areaStart == null)
		{
			areaStart = point;
			chat("Quick Tiles: " + (areaExport ? "exporting an area. " : "") + "Point at the other corner and press "
				+ config.areaHotkey() + " again. Esc cancels.");
			return;
		}
		WorldPoint start = areaStart;
		areaStart = null;
		if (!TileArea.isValid(start, point))
		{
			chat(start.getPlane() != point.getPlane() ? "Quick Tiles: both corners need to be on the same floor."
				: "Quick Tiles: that area is too big. Each side can be up to " + TileArea.MAX_SIDE + " tiles.");
			areaExport = false;
			return;
		}
		if (areaExport)
		{
			areaExport = false;
			exportArea(start, point);
			return;
		}
		fillArea(start, point);
	}

	private void fillArea(WorldPoint start, WorldPoint end)
	{
		Color brush = brushColour();
		TilePoint startTile = TilePoint.of(start, null);
		List<TilePoint> startRegion = store.get(startTile.getRegionId());
		int index = startRegion.indexOf(startTile);
		TileEdits.Mode mode = TileEdits.modeFor(index >= 0 ? startRegion.get(index) : null, brush);

		Map<Integer, List<TilePoint>> byRegion = new LinkedHashMap<>();
		int changed = 0;
		history.begin();
		for (WorldPoint point : TileArea.tiles(start, end))
		{
			TilePoint target = newTile(point);
			List<TilePoint> tiles = byRegion.computeIfAbsent(target.getRegionId(), r ->
			{
				List<TilePoint> current = store.get(r);
				history.touch(r, current);
				return current;
			});
			if (TileEdits.apply(tiles, target, mode, brush))
			{
				changed++;
			}
		}
		byRegion.forEach(store::save);
		history.end(regions);
		loadTiles();
		refreshPanel();
		chat("Quick Tiles: " + (mode == TileEdits.Mode.ERASE ? "erased " : "marked ") + TileSharing.count(changed) + ".");
	}

	private void exportArea(WorldPoint start, WorldPoint end)
	{
		Set<TilePoint> wanted = new HashSet<>();
		for (WorldPoint point : TileArea.tiles(start, end))
		{
			wanted.add(TilePoint.of(point, null));
		}
		List<TilePoint> found = new ArrayList<>();
		Set<Integer> seen = new HashSet<>();
		for (TilePoint t : wanted)
		{
			if (seen.add(t.getRegionId()))
			{
				for (TilePoint saved : store.get(t.getRegionId()))
				{
					if (wanted.contains(saved))
					{
						found.add(saved);
					}
				}
			}
		}
		sharing.copy(found, "in that area");
	}

	private void cancelArea()
	{
		if (areaStart != null)
		{
			areaStart = null;
			areaExport = false;
			chat("Quick Tiles: area cancelled.");
		}
	}

	/**
	 * The next area is copied to the clipboard instead of marked.
	 */
	void armAreaExport()
	{
		areaExport = true;
		areaStart = null;
		chat("Quick Tiles: point at one corner of the area to export and press " + config.areaHotkey()
			+ ", then the other corner.");
	}

	// ---- line of sight ----

	private void toggleSight()
	{
		Player player = client.getLocalPlayer();
		WorldView wv = player == null ? null : player.getWorldView();
		Tile tile = wv == null || client.isMenuOpen() ? null : wv.getSelectedSceneTile();
		if (tile == null)
		{
			return;
		}
		WorldPoint point = tile.getWorldLocation();
		if (point.equals(sightPin) && wv.getId() == sightWorldView)
		{
			clearSight();
			refreshPanel();
			return;
		}
		sightPin = point;
		sightWorldView = wv.getId();
		recomputeSight();
		refreshPanel();
	}

	/**
	 * Works out which tiles have a clear line to the pinned tile, once. It doesn't follow the
	 * mouse or the player, so the shading stays still.
	 */
	private void recomputeSight()
	{
		WorldPoint pin = sightPin;
		WorldView wv = pin == null ? null : client.getWorldView(sightWorldView);
		if (pin == null || wv == null)
		{
			sightTiles = Collections.emptySet();
			return;
		}
		int range = config.sightRange();
		WorldArea from = new WorldArea(pin, 1, 1);
		CollisionData[] maps = wv.getCollisionMaps();
		int[][] flags = maps == null || pin.getPlane() >= maps.length || maps[pin.getPlane()] == null
			? null : maps[pin.getPlane()].getFlags();
		Set<WorldPoint> seen = new HashSet<>();
		for (int dx = -range; dx <= range; dx++)
		{
			for (int dy = -range; dy <= range; dy++)
			{
				if (dx == 0 && dy == 0)
				{
					continue;
				}
				WorldPoint to = pin.dx(dx).dy(dy);
				if (isSolid(wv, flags, to))
				{
					continue;
				}
				if (from.hasLineOfSightTo(wv, to))
				{
					seen.add(to);
				}
			}
		}
		sightTiles = seen;
	}

	/**
	 * Walls and objects you can't stand on aren't shaded.
	 */
	private static boolean isSolid(WorldView wv, @Nullable int[][] flags, WorldPoint point)
	{
		if (flags == null)
		{
			return false;
		}
		int x = point.getX() - wv.getBaseX();
		int y = point.getY() - wv.getBaseY();
		if (x < 0 || y < 0 || x >= flags.length || y >= flags[x].length)
		{
			return true;
		}
		return (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0;
	}

	void clearSight()
	{
		sightPin = null;
		sightWorldView = -1;
		sightTiles = Collections.emptySet();
	}

	// ---- groups ----

	private void loadGroups()
	{
		TileGroups.Saved saved = null;
		String json = configManager.getConfiguration(QuickTilesConfig.GROUP, QuickTilesConfig.GROUPS_KEY);
		if (json != null && !json.isEmpty())
		{
			try
			{
				saved = gson.fromJson(json, TileGroups.Saved.class);
			}
			catch (JsonParseException | IllegalStateException e)
			{
				saved = null;
			}
		}
		groups.load(saved);
		String active = configManager.getConfiguration(QuickTilesConfig.GROUP, QuickTilesConfig.ACTIVE_GROUP_KEY);
		activeGroup = active == null || active.isEmpty() || !groups.exists(active) ? null : active;
		refreshPanel();
	}

	private void saveGroups()
	{
		configManager.setConfiguration(QuickTilesConfig.GROUP, QuickTilesConfig.GROUPS_KEY, gson.toJson(groups.export()));
	}

	void setActiveGroup(@Nullable String group)
	{
		activeGroup = group;
		if (group == null)
		{
			configManager.unsetConfiguration(QuickTilesConfig.GROUP, QuickTilesConfig.ACTIVE_GROUP_KEY);
		}
		else
		{
			configManager.setConfiguration(QuickTilesConfig.GROUP, QuickTilesConfig.ACTIVE_GROUP_KEY, group);
		}
		refreshPanel();
	}

	private void nextGroup()
	{
		if (groups.names().isEmpty())
		{
			chat("Quick Tiles: you have no groups yet. Make one in the Quick Tiles sidebar.");
			return;
		}
		setActiveGroup(groups.next(activeGroup));
		chat("Quick Tiles: new tiles go into " + TileGroups.display(activeGroup) + ".");
	}

	/**
	 * @return the group's name as saved, or null if the name was empty or taken
	 */
	@Nullable
	String addGroup(String name)
	{
		String added = groups.add(name);
		if (added == null)
		{
			chat("Quick Tiles: that group name is empty or already taken.");
			return null;
		}
		saveGroups();
		refreshPanel();
		return added;
	}

	void renameGroup(String from, String to)
	{
		String renamed = groups.rename(from, to);
		if (renamed == null)
		{
			chat("Quick Tiles: that group name is empty or already taken.");
			return;
		}
		rewriteGroup(from, renamed);
		if (from.equalsIgnoreCase(String.valueOf(activeGroup)))
		{
			setActiveGroup(renamed);
		}
		saveGroups();
		loadTiles();
		refreshPanel();
	}

	/**
	 * @param deleteTiles delete the group's tiles too, rather than keeping them ungrouped
	 */
	void deleteGroup(String name, boolean deleteTiles)
	{
		history.begin();
		for (int region : store.allRegions())
		{
			List<TilePoint> tiles = store.get(region);
			List<TilePoint> updated = new ArrayList<>();
			boolean changed = false;
			for (TilePoint t : tiles)
			{
				if (name.equalsIgnoreCase(String.valueOf(t.getGroup())))
				{
					changed = true;
					if (!deleteTiles)
					{
						updated.add(t.withGroup(null));
					}
				}
				else
				{
					updated.add(t);
				}
			}
			if (changed)
			{
				history.touch(region, tiles);
				store.save(region, updated);
			}
		}
		history.end(regions);
		groups.remove(name);
		if (name.equalsIgnoreCase(String.valueOf(activeGroup)))
		{
			setActiveGroup(null);
		}
		saveGroups();
		loadTiles();
		refreshPanel();
	}

	/**
	 * Imported tiles that name a group bring that group with them.
	 */
	void ensureGroups(List<TilePoint> tiles)
	{
		boolean added = false;
		for (TilePoint t : tiles)
		{
			if (t.getGroup() != null && !groups.exists(t.getGroup()))
			{
				added |= groups.add(t.getGroup()) != null;
			}
		}
		if (added)
		{
			saveGroups();
			refreshPanel();
		}
	}

	void setGroupHidden(@Nullable String name, boolean hidden)
	{
		groups.setHidden(name, hidden);
		saveGroups();
		loadTiles();
		refreshPanel();
	}

	/**
	 * Every saved tile in a group (null for ungrouped).
	 */
	List<TilePoint> groupTiles(@Nullable String name)
	{
		List<TilePoint> out = new ArrayList<>();
		for (int region : store.allRegions())
		{
			for (TilePoint t : store.get(region))
			{
				if (TileGroups.key(groups.groupOf(t)).equals(TileGroups.key(name)))
				{
					out.add(t);
				}
			}
		}
		return out;
	}

	private void rewriteGroup(String from, String to)
	{
		for (int region : store.allRegions())
		{
			List<TilePoint> tiles = store.get(region);
			boolean changed = false;
			for (int i = 0; i < tiles.size(); i++)
			{
				if (from.equalsIgnoreCase(String.valueOf(tiles.get(i).getGroup())))
				{
					tiles.set(i, tiles.get(i).withGroup(to));
					changed = true;
				}
			}
			if (changed)
			{
				store.save(region, tiles);
			}
		}
	}

	/**
	 * Tile counts per group (key "" for ungrouped): [all saved tiles, tiles around you].
	 */
	Map<String, int[]> groupCounts()
	{
		Set<Integer> nearby = new HashSet<>();
		WorldView top = client.getTopLevelWorldView();
		int[] loaded = top == null ? null : top.getMapRegions();
		if (loaded != null)
		{
			for (int r : loaded)
			{
				nearby.add(r);
			}
		}
		Map<String, int[]> counts = new HashMap<>();
		for (int region : store.allRegions())
		{
			for (TilePoint t : store.get(region))
			{
				int[] c = counts.computeIfAbsent(TileGroups.key(groups.groupOf(t)), k -> new int[2]);
				c[0]++;
				if (nearby.contains(region))
				{
					c[1]++;
				}
			}
		}
		return counts;
	}

	// ---- panel ----

	private void addPanel()
	{
		panel = new QuickTilesPanel(this, config, configManager);
		BufferedImage icon = ImageUtil.loadImageResource(QuickTilesPlugin.class, "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Quick Tiles")
			.icon(icon)
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		refreshPanel();
	}

	private void removePanel()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}
		navButton = null;
		panel = null;
	}

	/**
	 * Updates the sidebar from the client thread's state.
	 */
	void refreshPanel()
	{
		QuickTilesPanel p = panel;
		if (p == null)
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			QuickTilesPanel.State state = new QuickTilesPanel.State();
			for (int i = 0; i < COLOUR_COUNT; i++)
			{
				state.colours.add(colour(i));
			}
			state.brush = brushIndex();
			state.groups = new ArrayList<>(groups.names());
			state.hidden = new HashMap<>();
			for (String name : state.groups)
			{
				state.hidden.put(TileGroups.key(name), groups.isHidden(name));
			}
			state.hidden.put(TileGroups.key(null), groups.isHidden(null));
			state.counts = groupCounts();
			state.activeGroup = activeGroup;
			state.canUndo = history.canUndo();
			state.canRedo = history.canRedo();
			state.sightPinned = sightPin != null;
			SwingUtilities.invokeLater(() -> p.update(state));
		});
	}

	// ---- shared helpers ----

	/**
	 * The world tile under the mouse, or null when the mouse isn't over the game world.
	 */
	@Nullable
	WorldPoint hoveredTile()
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

	/**
	 * WHAT: The tile you're standing on, in saved (non-instance) coordinates.
	 * WHY: Patterns are copied and pasted relative to where you stand.
	 * PRE: Client thread.
	 * POST: Nothing changes.
	 * RETURN: the tile, or null when not logged in
	 * THROWS: Nothing.
	 */
	@Nullable
	WorldPoint standingTile()
	{
		Player player = client.getLocalPlayer();
		net.runelite.api.coords.LocalPoint lp = player == null ? null : player.getLocalLocation();
		return lp == null ? null : WorldPoint.fromLocalInstance(client, lp);
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
		int[] mapRegions = wv.getMapRegions();
		if (mapRegions == null)
		{
			marked.remove(wv.getId());
			return;
		}
		List<MarkedTile> tiles = new ArrayList<>();
		// Each group's tiles are numbered in the order they were marked (the order they're saved in).
		Map<String, Integer> numbers = new HashMap<>();
		Map<String, Integer> counts = new java.util.LinkedHashMap<>();
		for (int region : mapRegions)
		{
			for (TilePoint saved : store.get(region))
			{
				String group = groups.groupOf(saved);
				if (groups.isHidden(group))
				{
					continue;
				}
				int number = numbers.merge(TileGroups.key(group), 1, Integer::sum);
				counts.merge(TileGroups.display(group), 1, Integer::sum);
				for (WorldPoint local : WorldPoint.toLocalInstance(wv, saved.toWorldPoint()))
				{
					tiles.add(new MarkedTile(local, saved.getColor(), saved.getLabel(), saved.getNote(), TileGroups.key(group), number));
				}
			}
		}
		marked.put(wv.getId(), tiles);
		if (wv.isTopLevel())
		{
			nearbyCounts = Collections.unmodifiableMap(counts);
		}
	}

	// ---- for overlays, the panel and sharing ----

	Map<Integer, List<MarkedTile>> getMarked()
	{
		return Collections.unmodifiableMap(marked);
	}

	/**
	 * WHAT: How many tiles are shown around you, per group name.
	 * WHY: The tile counter overlay; counted when tiles load, not every frame.
	 * PRE: None.
	 * POST: Nothing changes.
	 * RETURN: group name to count, in the order first seen
	 * THROWS: Nothing.
	 */
	Map<String, Integer> getNearbyCounts()
	{
		return nearbyCounts;
	}

	boolean isTilesVisible()
	{
		return tilesVisible;
	}

	@Nullable
	WorldPoint getAreaStart()
	{
		return areaStart;
	}

	boolean isAreaExport()
	{
		return areaExport;
	}

	@Nullable
	WorldPoint getSightPin()
	{
		return sightPin;
	}

	int getSightWorldView()
	{
		return sightWorldView;
	}

	Set<WorldPoint> getSightTiles()
	{
		return sightTiles;
	}

	TileSharing getSharing()
	{
		return sharing;
	}

	ClientThread getClientThread()
	{
		return clientThread;
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
			case 4:
				return config.colour5();
			case 5:
				return config.colour6();
			case 6:
				return config.colour7();
			case 7:
				return config.colour8();
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

	/**
	 * The hotkeys the panel can change, as config key and display name, in order.
	 */
	static String[][] hotkeys()
	{
		return new String[][]{
			{"markHotkey", "Mark / paint"},
			{"areaHotkey", "Area"},
			{"sightHotkey", "Line of sight"},
			{"colourHotkey", "Next colour"},
			{"groupHotkey", "Next group"},
			{"undoHotkey", "Undo"},
			{"redoHotkey", "Redo"},
			{"toggleHotkey", "Show / hide"},
		};
	}

	Keybind hotkey(String key)
	{
		switch (key)
		{
			case "markHotkey":
				return config.markHotkey();
			case "areaHotkey":
				return config.areaHotkey();
			case "sightHotkey":
				return config.sightHotkey();
			case "colourHotkey":
				return config.colourHotkey();
			case "groupHotkey":
				return config.groupHotkey();
			case "undoHotkey":
				return config.undoHotkey();
			case "redoHotkey":
				return config.redoHotkey();
			default:
				return config.toggleHotkey();
		}
	}

	void chat(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(message)
			.build());
	}
}
