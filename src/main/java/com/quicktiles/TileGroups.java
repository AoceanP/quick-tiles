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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * The player's tile groups, in order, and which are hidden. Tiles name their group; a tile with
 * no group is "Ungrouped". Saved as JSON, so field names are the save format.
 */
final class TileGroups
{
	static final String UNGROUPED = "Ungrouped";
	/**
	 * Longest group name.
	 */
	static final int MAX_NAME = 30;

	static final class Group
	{
		String name;
		boolean hidden;

		Group()
		{
		}

		Group(String name)
		{
			this.name = name;
		}
	}

	private final List<Group> groups = new ArrayList<>();
	private boolean ungroupedHidden;

	/**
	 * @param saved from {@link #export()}, or null to start empty
	 */
	void load(@Nullable Saved saved)
	{
		groups.clear();
		ungroupedHidden = false;
		if (saved == null)
		{
			return;
		}
		ungroupedHidden = saved.ungroupedHidden;
		if (saved.groups != null)
		{
			for (Group g : saved.groups)
			{
				String name = g == null ? null : clean(g.name);
				if (name != null && find(name) == null)
				{
					Group copy = new Group(name);
					copy.hidden = g.hidden;
					groups.add(copy);
				}
			}
		}
	}

	/**
	 * The save format.
	 */
	static final class Saved
	{
		List<Group> groups;
		boolean ungroupedHidden;
	}

	Saved export()
	{
		Saved saved = new Saved();
		saved.groups = new ArrayList<>();
		for (Group g : groups)
		{
			Group copy = new Group(g.name);
			copy.hidden = g.hidden;
			saved.groups.add(copy);
		}
		saved.ungroupedHidden = ungroupedHidden;
		return saved;
	}

	/**
	 * A group name trimmed and shortened, or null if it's empty or the reserved "Ungrouped".
	 */
	@Nullable
	static String clean(@Nullable String name)
	{
		if (name == null)
		{
			return null;
		}
		String trimmed = name.trim().replaceAll("\\s+", " ");
		if (trimmed.isEmpty() || trimmed.equalsIgnoreCase(UNGROUPED))
		{
			return null;
		}
		return trimmed.length() > MAX_NAME ? trimmed.substring(0, MAX_NAME) : trimmed;
	}

	@Nullable
	private Group find(@Nullable String name)
	{
		if (name == null)
		{
			return null;
		}
		for (Group g : groups)
		{
			if (g.name.equalsIgnoreCase(name))
			{
				return g;
			}
		}
		return null;
	}

	/**
	 * The group names in order, without "Ungrouped".
	 */
	List<String> names()
	{
		List<String> names = new ArrayList<>();
		for (Group g : groups)
		{
			names.add(g.name);
		}
		return Collections.unmodifiableList(names);
	}

	boolean exists(@Nullable String name)
	{
		return name == null || find(name) != null;
	}

	/**
	 * Adds a group.
	 *
	 * @return its name as saved, or null if the name was empty or taken
	 */
	@Nullable
	String add(String name)
	{
		String clean = clean(name);
		if (clean == null || find(clean) != null)
		{
			return null;
		}
		groups.add(new Group(clean));
		return clean;
	}

	/**
	 * @return the new name, or null if it was empty, taken or the group doesn't exist
	 */
	@Nullable
	String rename(String from, String to)
	{
		Group g = find(from);
		String clean = clean(to);
		if (g == null || clean == null || (find(clean) != null && find(clean) != g))
		{
			return null;
		}
		g.name = clean;
		return clean;
	}

	void remove(String name)
	{
		Group g = find(name);
		if (g != null)
		{
			groups.remove(g);
		}
	}

	/**
	 * @param name a group, or null for ungrouped tiles
	 */
	boolean isHidden(@Nullable String name)
	{
		if (name == null)
		{
			return ungroupedHidden;
		}
		Group g = find(name);
		// Tiles naming a group that was never added (an import) show like ungrouped ones.
		return g == null ? ungroupedHidden : g.hidden;
	}

	void setHidden(@Nullable String name, boolean hidden)
	{
		if (name == null)
		{
			ungroupedHidden = hidden;
			return;
		}
		Group g = find(name);
		if (g != null)
		{
			g.hidden = hidden;
		}
	}

	/**
	 * The group after this one for new tiles, wrapping round through "Ungrouped" (null).
	 */
	@Nullable
	String next(@Nullable String current)
	{
		if (groups.isEmpty())
		{
			return null;
		}
		int index = current == null ? -1 : groups.indexOf(find(current));
		return index + 1 >= groups.size() ? null : groups.get(index + 1).name;
	}

	/**
	 * "Vorkath" or "Ungrouped".
	 */
	static String display(@Nullable String name)
	{
		return name == null ? UNGROUPED : name;
	}

	/**
	 * The group a tile is shown under: its own if it still exists, otherwise ungrouped.
	 */
	@Nullable
	String groupOf(TilePoint tile)
	{
		Group g = find(tile.getGroup());
		return g == null ? null : g.name;
	}

	static String key(@Nullable String name)
	{
		return name == null ? "" : name.toLowerCase(Locale.ROOT);
	}
}
