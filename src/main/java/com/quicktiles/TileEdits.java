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
import java.util.List;
import javax.annotation.Nullable;

/**
 * The editing rules, kept free of client code so they can be unit tested.
 */
final class TileEdits
{
	enum Mode
	{
		/**
		 * Mark tiles in the brush colour, recolouring tiles that already have another colour.
		 */
		PAINT,
		/**
		 * Remove marks.
		 */
		ERASE,
	}

	private TileEdits()
	{
	}

	/**
	 * A stroke erases when it starts on a tile that already has the brush colour, and paints otherwise.
	 * So pressing the key on your own tile removes it, and pressing it anywhere else marks.
	 */
	static Mode modeFor(@Nullable TilePoint startTile, Color brush)
	{
		return startTile != null && brush.equals(startTile.getColor()) ? Mode.ERASE : Mode.PAINT;
	}

	/**
	 * Applies one edit to a region's tiles.
	 *
	 * @return true if the list changed
	 */
	static boolean apply(List<TilePoint> tiles, TilePoint target, Mode mode, Color brush)
	{
		int index = tiles.indexOf(target);
		if (mode == Mode.ERASE)
		{
			if (index < 0)
			{
				return false;
			}
			tiles.remove(index);
			return true;
		}

		if (index < 0)
		{
			tiles.add(target.withColor(brush).withLabel(null));
			return true;
		}
		TilePoint existing = tiles.get(index);
		if (brush.equals(existing.getColor()))
		{
			return false;
		}
		tiles.set(index, existing.withColor(brush));
		return true;
	}

	/**
	 * Adds imported tiles to a region, keeping existing tiles where both have one.
	 *
	 * @return how many tiles were added
	 */
	static int merge(List<TilePoint> tiles, List<TilePoint> incoming)
	{
		int added = 0;
		for (TilePoint point : incoming)
		{
			if (point != null && point.isValid() && !tiles.contains(point))
			{
				tiles.add(point);
				added++;
			}
		}
		return added;
	}
}
