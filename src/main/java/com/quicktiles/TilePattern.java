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
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import net.runelite.api.coords.WorldPoint;

/**
 * WHAT: A shape of tiles relative to where you stood when you copied it, e.g. a boss's safe
 * spots, that can be pasted anywhere else.
 * WHY: Exports are tied to one place in the world; a pattern lets the same layout be reused at
 * another spot, another instance, or by a friend standing somewhere else.
 * PRE: Not applicable.
 * POST: Not applicable.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
final class TilePattern
{
	/** How far from you (in tiles) a pattern reaches when it's copied. */
	static final int RADIUS = 10;
	/** Marks the clipboard text as a pattern rather than a tile export. */
	static final int FORMAT = 1;

	/**
	 * One tile of the pattern: how far it is from you, and how it looks.
	 */
	static final class Spot
	{
		int dx;
		int dy;
		@Nullable
		Color color;
		@Nullable
		String label;
		@Nullable
		String group;
		@Nullable
		String note;
	}

	int quickTilesPattern = FORMAT;
	List<Spot> tiles = new ArrayList<>();

	/**
	 * WHAT: The pattern of saved tiles around a point.
	 * WHY: "Copy a pattern" takes the tiles near you, on your floor.
	 * PRE: anchor is where you stand, in saved (non-instance) coordinates; saved may hold any tiles.
	 * POST: Nothing changes.
	 * RETURN: the pattern, possibly with no tiles
	 * THROWS: Nothing.
	 */
	static TilePattern around(WorldPoint anchor, Collection<TilePoint> saved, int radius)
	{
		TilePattern pattern = new TilePattern();
		for (TilePoint t : saved)
		{
			WorldPoint p = t.toWorldPoint();
			int dx = p.getX() - anchor.getX();
			int dy = p.getY() - anchor.getY();
			if (p.getPlane() != anchor.getPlane() || Math.abs(dx) > radius || Math.abs(dy) > radius)
			{
				continue;
			}
			Spot s = new Spot();
			s.dx = dx;
			s.dy = dy;
			s.color = t.getColor();
			s.label = t.getLabel();
			s.group = t.getGroup();
			s.note = t.getNote();
			pattern.tiles.add(s);
		}
		return pattern;
	}

	/**
	 * WHAT: The pattern's tiles placed around a new point.
	 * WHY: "Paste" puts the same shape at your feet, wherever you are now.
	 * PRE: anchor is where you stand, in saved (non-instance) coordinates.
	 * POST: Nothing changes.
	 * RETURN: the tiles to add
	 * THROWS: Nothing.
	 */
	List<TilePoint> placeAt(WorldPoint anchor)
	{
		List<TilePoint> out = new ArrayList<>();
		for (Spot s : tiles)
		{
			if (s == null)
			{
				continue;
			}
			TilePoint t = TilePoint.of(anchor.dx(s.dx).dy(s.dy), s.color).withLabel(s.label).withGroup(s.group).withNote(s.note);
			if (t.isValid())
			{
				out.add(t);
			}
		}
		return out;
	}

	/**
	 * WHAT: Whether this was read from real pattern text.
	 * WHY: A Ground Markers export also parses as JSON; only a pattern carries the format marker.
	 * PRE: None.
	 * POST: Nothing changes.
	 * RETURN: true for a pattern with at least one tile
	 * THROWS: Nothing.
	 */
	boolean isValid()
	{
		return quickTilesPattern == FORMAT && tiles != null && !tiles.isEmpty();
	}
}
