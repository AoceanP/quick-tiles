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
import java.util.Objects;
import javax.annotation.Nullable;
import net.runelite.api.coords.WorldPoint;

/**
 * One saved tile. The field names match RuneLite's Ground Markers plugin, so exports from either
 * plugin (and shared tile packs) can be pasted into the other.
 * <p>
 * Two points are equal when they are the same tile; colour and label are ignored.
 */
final class TilePoint
{
	private int regionId;
	private int regionX;
	private int regionY;
	private int z;
	@Nullable
	private Color color;
	@Nullable
	private String label;

	// For Gson.
	private TilePoint()
	{
	}

	TilePoint(int regionId, int regionX, int regionY, int z, @Nullable Color color, @Nullable String label)
	{
		this.regionId = regionId;
		this.regionX = regionX;
		this.regionY = regionY;
		this.z = z;
		this.color = color;
		this.label = label;
	}

	static TilePoint of(WorldPoint point, @Nullable Color color)
	{
		return new TilePoint(point.getRegionID(), point.getRegionX(), point.getRegionY(), point.getPlane(), color, null);
	}

	WorldPoint toWorldPoint()
	{
		return WorldPoint.fromRegion(regionId, regionX, regionY, z);
	}

	int getRegionId()
	{
		return regionId;
	}

	@Nullable
	Color getColor()
	{
		return color;
	}

	@Nullable
	String getLabel()
	{
		return label;
	}

	TilePoint withColor(@Nullable Color newColor)
	{
		return new TilePoint(regionId, regionX, regionY, z, newColor, label);
	}

	TilePoint withLabel(@Nullable String newLabel)
	{
		return new TilePoint(regionId, regionX, regionY, z, color, newLabel);
	}

	/**
	 * Rejects entries that can't be a real tile, e.g. from a malformed paste.
	 */
	boolean isValid()
	{
		return regionId >= 0 && regionX >= 0 && regionX < 64 && regionY >= 0 && regionY < 64 && z >= 0 && z < 4;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof TilePoint))
		{
			return false;
		}
		TilePoint other = (TilePoint) o;
		return regionId == other.regionId && regionX == other.regionX && regionY == other.regionY && z == other.z;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(regionId, regionX, regionY, z);
	}

	@Override
	public String toString()
	{
		return "TilePoint{" + regionId + " " + regionX + "," + regionY + "," + z + "}";
	}
}
