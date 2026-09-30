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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Saves tiles in the RuneLite profile, one config key per map region, like Ground Markers does.
 */
@Singleton
class TileStore
{
	static final String REGION_PREFIX = "region_";
	static final String GROUND_MARKERS_GROUP = "groundMarker";

	private static final Logger log = LoggerFactory.getLogger(TileStore.class);

	private final ConfigManager configManager;
	private final Gson gson;
	/**
	 * Parsed tiles by region. The right-click check runs every frame, so this avoids re-reading the JSON.
	 */
	private final Map<Integer, List<TilePoint>> cache = new HashMap<>();

	@Inject
	TileStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/**
	 * A modifiable copy of the region's tiles.
	 */
	List<TilePoint> get(int regionId)
	{
		return new ArrayList<>(cache.computeIfAbsent(regionId, r -> read(QuickTilesConfig.GROUP, r)));
	}

	/**
	 * Forgets parsed tiles, e.g. after switching RuneLite profile.
	 */
	void clearCache()
	{
		cache.clear();
	}

	void save(int regionId, Collection<TilePoint> tiles)
	{
		cache.put(regionId, new ArrayList<>(tiles));
		if (tiles.isEmpty())
		{
			configManager.unsetConfiguration(QuickTilesConfig.GROUP, REGION_PREFIX + regionId);
			return;
		}
		configManager.setConfiguration(QuickTilesConfig.GROUP, REGION_PREFIX + regionId, gson.toJson(tiles));
	}

	/**
	 * Every region that has Ground Markers tiles saved, for the "copy from Ground Markers" option.
	 */
	List<Integer> groundMarkerRegions()
	{
		String prefix = GROUND_MARKERS_GROUP + "." + REGION_PREFIX;
		List<Integer> regions = new ArrayList<>();
		for (String key : configManager.getConfigurationKeys(prefix))
		{
			try
			{
				regions.add(Integer.parseInt(key.substring(prefix.length())));
			}
			catch (NumberFormatException e)
			{
				log.debug("Skipping odd Ground Markers key {}", key);
			}
		}
		return regions;
	}

	List<TilePoint> groundMarkers(int regionId)
	{
		return read(GROUND_MARKERS_GROUP, regionId);
	}

	/**
	 * Reads a pasted list of tiles. Returns an empty list if the text isn't one.
	 */
	List<TilePoint> parse(@Nullable String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return Collections.emptyList();
		}
		try
		{
			TilePoint[] parsed = gson.fromJson(json.trim(), TilePoint[].class);
			if (parsed == null)
			{
				return Collections.emptyList();
			}
			return Arrays.stream(parsed)
				.filter(Objects::nonNull)
				.filter(TilePoint::isValid)
				.collect(Collectors.toCollection(ArrayList::new));
		}
		catch (JsonParseException | IllegalStateException | ClassCastException e)
		{
			log.debug("Not a tile list", e);
			return Collections.emptyList();
		}
	}

	String toJson(Collection<TilePoint> tiles)
	{
		return gson.toJson(tiles);
	}

	private List<TilePoint> read(String group, int regionId)
	{
		String json = configManager.getConfiguration(group, REGION_PREFIX + regionId);
		List<TilePoint> tiles = parse(json);
		return tiles.isEmpty() ? new ArrayList<>() : tiles;
	}
}
