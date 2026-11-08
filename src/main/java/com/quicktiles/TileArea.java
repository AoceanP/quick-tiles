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
import java.util.List;
import net.runelite.api.coords.WorldPoint;

/**
 * The rectangle between two corner tiles, for area select.
 */
final class TileArea
{
	/**
	 * The biggest side an area can have, so a stray corner can't mark thousands of tiles.
	 */
	static final int MAX_SIDE = 40;

	private TileArea()
	{
	}

	/**
	 * @return whether the two corners make an area that can be filled: same floor, not too big
	 */
	static boolean isValid(WorldPoint a, WorldPoint b)
	{
		return a.getPlane() == b.getPlane() && width(a, b) <= MAX_SIDE && height(a, b) <= MAX_SIDE;
	}

	static int width(WorldPoint a, WorldPoint b)
	{
		return Math.abs(a.getX() - b.getX()) + 1;
	}

	static int height(WorldPoint a, WorldPoint b)
	{
		return Math.abs(a.getY() - b.getY()) + 1;
	}

	static int size(WorldPoint a, WorldPoint b)
	{
		return width(a, b) * height(a, b);
	}

	/**
	 * Every tile in the rectangle, corners included.
	 */
	static List<WorldPoint> tiles(WorldPoint a, WorldPoint b)
	{
		List<WorldPoint> out = new ArrayList<>(size(a, b));
		for (int x = Math.min(a.getX(), b.getX()); x <= Math.max(a.getX(), b.getX()); x++)
		{
			for (int y = Math.min(a.getY(), b.getY()); y <= Math.max(a.getY(), b.getY()); y++)
			{
				out.add(new WorldPoint(x, y, a.getPlane()));
			}
		}
		return out;
	}

	/**
	 * Just the tiles along the edge, for drawing the outline while choosing the second corner.
	 */
	static List<WorldPoint> edge(WorldPoint a, WorldPoint b)
	{
		List<WorldPoint> out = new ArrayList<>();
		int x0 = Math.min(a.getX(), b.getX());
		int x1 = Math.max(a.getX(), b.getX());
		int y0 = Math.min(a.getY(), b.getY());
		int y1 = Math.max(a.getY(), b.getY());
		for (int x = x0; x <= x1; x++)
		{
			for (int y = y0; y <= y1; y++)
			{
				if (x == x0 || x == x1 || y == y0 || y == y1)
				{
					out.add(new WorldPoint(x, y, a.getPlane()));
				}
			}
		}
		return out;
	}
}
