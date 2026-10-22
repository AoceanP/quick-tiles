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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Undo and redo for tile edits. Each edit (a click, or a whole paint stroke) remembers
 * the affected regions' tiles before and after, so it can be put back exactly. Only this session's
 * edits are kept; nothing is saved.
 */
final class TileHistory
{
	/**
	 * Edits kept for undo. Older ones are forgotten.
	 */
	static final int LIMIT = 50;

	/**
	 * Region id to its tiles.
	 */
	interface Regions
	{
		List<TilePoint> get(int regionId);

		void save(int regionId, List<TilePoint> tiles);
	}

	private static final class Edit
	{
		final Map<Integer, List<TilePoint>> before = new HashMap<>();
		final Map<Integer, List<TilePoint>> after = new HashMap<>();
	}

	private final Deque<Edit> undo = new ArrayDeque<>();
	private final Deque<Edit> redo = new ArrayDeque<>();
	@Nullable
	private Edit open;

	/**
	 * Starts an edit. Regions changed until {@link #end} are undone together.
	 */
	void begin()
	{
		open = new Edit();
	}

	/**
	 * Call before changing a region, with its tiles as they are now. Only the first call per
	 * region in an edit counts, so a stroke over many tiles keeps the state from before it began.
	 */
	void touch(int regionId, List<TilePoint> current)
	{
		if (open != null && !open.before.containsKey(regionId))
		{
			open.before.put(regionId, new ArrayList<>(current));
		}
	}

	/**
	 * Finishes the edit, recording the regions' tiles after it. An edit that changed nothing is
	 * dropped.
	 */
	void end(Regions regions)
	{
		Edit edit = open;
		open = null;
		if (edit == null || edit.before.isEmpty())
		{
			return;
		}
		boolean changed = false;
		for (Map.Entry<Integer, List<TilePoint>> e : edit.before.entrySet())
		{
			List<TilePoint> now = new ArrayList<>(regions.get(e.getKey()));
			edit.after.put(e.getKey(), now);
			changed |= !sameTiles(e.getValue(), now);
		}
		if (!changed)
		{
			return;
		}
		undo.push(edit);
		while (undo.size() > LIMIT)
		{
			undo.removeLast();
		}
		redo.clear();
	}

	/**
	 * @return whether there was an edit to undo
	 */
	boolean undo(Regions regions)
	{
		Edit edit = undo.poll();
		if (edit == null)
		{
			return false;
		}
		edit.before.forEach(regions::save);
		redo.push(edit);
		return true;
	}

	/**
	 * @return whether there was an edit to redo
	 */
	boolean redo(Regions regions)
	{
		Edit edit = redo.poll();
		if (edit == null)
		{
			return false;
		}
		edit.after.forEach(regions::save);
		undo.push(edit);
		return true;
	}

	boolean canUndo()
	{
		return !undo.isEmpty();
	}

	boolean canRedo()
	{
		return !redo.isEmpty();
	}

	void clear()
	{
		undo.clear();
		redo.clear();
		open = null;
	}

	/**
	 * Tiles compare as equal by position only, so colours, labels and notes are checked too.
	 */
	private static boolean sameTiles(List<TilePoint> a, List<TilePoint> b)
	{
		if (a.size() != b.size())
		{
			return false;
		}
		for (int i = 0; i < a.size(); i++)
		{
			TilePoint x = a.get(i);
			TilePoint y = b.get(i);
			if (!x.equals(y) || !java.util.Objects.equals(x.getColor(), y.getColor())
				|| !java.util.Objects.equals(x.getLabel(), y.getLabel())
				|| !java.util.Objects.equals(x.getNote(), y.getNote()))
			{
				return false;
			}
		}
		return true;
	}
}
