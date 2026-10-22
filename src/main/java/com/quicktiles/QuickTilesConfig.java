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
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup(QuickTilesConfig.GROUP)
public interface QuickTilesConfig extends Config
{
	String GROUP = "quicktiles";
	String BRUSH_KEY = "brush";
	String ORB_OPTIONS_KEY = "orbOptions";
	String PANEL_KEY = "sidebarPanel";

	enum MenuMode
	{
		ALWAYS("Always"),
		SHIFT("Holding Shift"),
		OFF("Off");

		private final String name;

		MenuMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	enum TileStyle
	{
		FILLED("Fill and border"),
		BORDER("Border only"),
		CORNERS("Corners only");

		private final String name;

		TileStyle(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	@ConfigSection(
		name = "Marking",
		description = "How you mark tiles",
		position = 0
	)
	String markingSection = "marking";

	@ConfigSection(
		name = "Colours",
		description = "The colours you can switch between",
		position = 1
	)
	String coloursSection = "colours";

	@ConfigSection(
		name = "Appearance",
		description = "How marked tiles are drawn",
		position = 2
	)
	String appearanceSection = "appearance";


	@ConfigItem(
		keyName = "markHotkey",
		name = "Mark hotkey",
		description = "Point at a tile and press this to mark it. Hold it and move the mouse to paint several tiles."
			+ "<br>Starting on a tile that already has your current colour erases instead.",
		position = 0,
		section = markingSection
	)
	default Keybind markHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "colourHotkey",
		name = "Next colour hotkey",
		description = "Switches to the next colour from the Colours section.",
		position = 1,
		section = markingSection
	)
	default Keybind colourHotkey()
	{
		return new Keybind(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK);
	}

	@ConfigItem(
		keyName = "toggleHotkey",
		name = "Show/hide hotkey",
		description = "Hides or shows every marked tile.",
		position = 2,
		section = markingSection
	)
	default Keybind toggleHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "rightClick",
		name = "Right-click option",
		description = "When to add Mark tile / Unmark tile to the right-click menu on the ground."
			+ "<br>Colour and Label options always need Shift, to keep the menu short.",
		position = 3,
		section = markingSection
	)
	default MenuMode rightClick()
	{
		return MenuMode.ALWAYS;
	}

	@ConfigItem(
		keyName = ORB_OPTIONS_KEY,
		name = "World map orb options",
		description = "Adds Import, Export, Clear and Copy Ground Markers to the world map orb's right-click menu.",
		position = 4,
		section = markingSection
	)
	default boolean orbOptions()
	{
		return true;
	}

	@ConfigItem(
		keyName = "undoHotkey",
		name = "Undo hotkey",
		description = "Undoes your last mark, paint stroke or area.",
		position = 7,
		section = markingSection
	)
	default Keybind undoHotkey()
	{
		return new Keybind(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK);
	}

	@ConfigItem(
		keyName = "redoHotkey",
		name = "Redo hotkey",
		description = "Puts back what you last undid.",
		position = 8,
		section = markingSection
	)
	default Keybind redoHotkey()
	{
		return new Keybind(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK);
	}

	@ConfigItem(
		keyName = PANEL_KEY,
		name = "Sidebar panel",
		description = "Adds a Quick Tiles button to the sidebar, with your colours, undo, export and hotkeys.",
		position = 10,
		section = markingSection
	)
	default boolean sidebarPanel()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "colour1",
		name = "Colour 1",
		description = "The first marking colour.",
		position = 0,
		section = coloursSection
	)
	default Color colour1()
	{
		return new Color(0xFF, 0xD7, 0x00);
	}

	@Alpha
	@ConfigItem(
		keyName = "colour2",
		name = "Colour 2",
		description = "The second marking colour.",
		position = 1,
		section = coloursSection
	)
	default Color colour2()
	{
		return new Color(0x00, 0xC8, 0xFF);
	}

	@Alpha
	@ConfigItem(
		keyName = "colour3",
		name = "Colour 3",
		description = "The third marking colour.",
		position = 2,
		section = coloursSection
	)
	default Color colour3()
	{
		return new Color(0x3C, 0xE6, 0x5A);
	}

	@Alpha
	@ConfigItem(
		keyName = "colour4",
		name = "Colour 4",
		description = "The fourth marking colour.",
		position = 3,
		section = coloursSection
	)
	default Color colour4()
	{
		return new Color(0xFF, 0x46, 0x46);
	}

	@Alpha
	@ConfigItem(
		keyName = "colour5",
		name = "Colour 5",
		description = "The fifth marking colour.",
		position = 4,
		section = coloursSection
	)
	default Color colour5()
	{
		return new Color(0xC7, 0x7D, 0xFF);
	}

	@Alpha
	@ConfigItem(
		keyName = "colour6",
		name = "Colour 6",
		description = "The sixth marking colour.",
		position = 5,
		section = coloursSection
	)
	default Color colour6()
	{
		return new Color(0xFF, 0x9F, 0x40);
	}

	@Alpha
	@ConfigItem(
		keyName = "colour7",
		name = "Colour 7",
		description = "The seventh marking colour.",
		position = 6,
		section = coloursSection
	)
	default Color colour7()
	{
		return new Color(0xFF, 0xFF, 0xFF);
	}

	@Alpha
	@ConfigItem(
		keyName = "colour8",
		name = "Colour 8",
		description = "The eighth marking colour.",
		position = 7,
		section = coloursSection
	)
	default Color colour8()
	{
		return new Color(0x8C, 0x8C, 0x8C);
	}

	@ConfigItem(
		keyName = BRUSH_KEY,
		name = "",
		description = "",
		hidden = true
	)
	default int brush()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "style",
		name = "Style",
		description = "How marked tiles are drawn.",
		position = 0,
		section = appearanceSection
	)
	default TileStyle style()
	{
		return TileStyle.FILLED;
	}

	@Range(max = 255)
	@ConfigItem(
		keyName = "fillOpacity",
		name = "Fill opacity",
		description = "0 is see-through, 255 is solid.",
		position = 1,
		section = appearanceSection
	)
	default int fillOpacity()
	{
		return 50;
	}

	@Range(min = 1, max = 6)
	@ConfigItem(
		keyName = "borderWidth",
		name = "Border width",
		description = "Thickness of the tile border.",
		position = 2,
		section = appearanceSection
	)
	default int borderWidth()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "showLabels",
		name = "Show labels",
		description = "Shows the text you gave a tile with Shift + right-click > Label.",
		position = 3,
		section = appearanceSection
	)
	default boolean showLabels()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideUnderPlayer",
		name = "Hide tile you stand on",
		description = "Stops a marked tile from covering your character while you stand on it.",
		position = 4,
		section = appearanceSection
	)
	default boolean hideUnderPlayer()
	{
		return false;
	}

	@ConfigItem(
		keyName = "minimap",
		name = "Show on minimap",
		description = "Also draws marked tiles on the minimap.",
		position = 5,
		section = appearanceSection
	)
	default boolean minimap()
	{
		return false;
	}
}
