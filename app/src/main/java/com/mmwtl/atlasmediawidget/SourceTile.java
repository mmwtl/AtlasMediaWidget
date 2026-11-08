package com.mmwtl.atlasmediawidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One tile of the card's source chooser: a head-unit source, or an Android player app that the
 * Bridge switches ONLINE to. The generic ONLINE tile keeps the configured default player.
 */
final class SourceTile {
    static final int MAX_TILES = 6;
    static final String PLAYER_PREFIX = "player:";
    private static final int MAX_PACKAGE_LENGTH = 128;
    private static final Pattern PACKAGE_NAME =
            Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");

    /** Head-unit sources a tile can stand for, in the order the editor offers them. */
    static final List<MediaSource.Id> NATIVE_SOURCES = List.of(
            MediaSource.Id.RADIO, MediaSource.Id.BT, MediaSource.Id.USB,
            MediaSource.Id.ONLINE, MediaSource.Id.CPAA);

    /** The chooser of earlier versions; also the set an empty or unreadable setting falls back to. */
    static final List<SourceTile> DEFAULT = List.of(
            source(MediaSource.Id.RADIO), source(MediaSource.Id.BT),
            source(MediaSource.Id.USB), source(MediaSource.Id.ONLINE),
            source(MediaSource.Id.CPAA));

    final MediaSource.Id source;
    /** Player app of a player tile; empty for a head-unit source. */
    final String playerPackage;

    private SourceTile(MediaSource.Id source, String playerPackage) {
        this.source = source;
        this.playerPackage = playerPackage;
    }

    static SourceTile source(MediaSource.Id id) {
        if (!NATIVE_SOURCES.contains(id)) throw new IllegalArgumentException("Not a tile source: " + id);
        return new SourceTile(id, "");
    }

    static SourceTile player(String packageName) {
        if (!isPackageName(packageName)) {
            throw new IllegalArgumentException("Not a package name: " + packageName);
        }
        return new SourceTile(MediaSource.Id.ONLINE, packageName);
    }

    boolean isPlayer() { return !playerPackage.isEmpty(); }

    String wire() { return isPlayer() ? PLAYER_PREFIX + playerPackage : source.name(); }

    /** Null for a value this version cannot show. */
    static SourceTile fromWire(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.startsWith(PLAYER_PREFIX)) {
            String packageName = trimmed.substring(PLAYER_PREFIX.length());
            return isPackageName(packageName) ? player(packageName) : null;
        }
        MediaSource.Id id = MediaSource.Id.fromWire(trimmed);
        return NATIVE_SOURCES.contains(id) ? source(id) : null;
    }

    static boolean isPackageName(String value) {
        return value != null && value.length() <= MAX_PACKAGE_LENGTH
                && PACKAGE_NAME.matcher(value).matches();
    }

    /** Unknown and repeated entries are dropped, the rest capped; nothing left means the default. */
    static List<SourceTile> decode(String value) {
        if (value == null || value.isBlank()) return DEFAULT;
        List<SourceTile> tiles = new ArrayList<>();
        for (String part : value.split(",")) {
            SourceTile tile = fromWire(part);
            if (tile != null && !tiles.contains(tile) && tiles.size() < MAX_TILES) tiles.add(tile);
        }
        return tiles.isEmpty() ? DEFAULT : Collections.unmodifiableList(tiles);
    }

    static String encode(List<SourceTile> tiles) {
        StringBuilder value = new StringBuilder();
        for (SourceTile tile : tiles) {
            if (value.length() > 0) value.append(',');
            value.append(tile.wire());
        }
        return value.toString();
    }

    /**
     * The tile of the playing source: a native source by id; ONLINE by its player's tile, else
     * the generic ONLINE tile. -1 when the set has no tile for it.
     */
    static int selectedIndex(List<SourceTile> tiles, MediaSource.Id active, String ownerPackage) {
        MediaSource.Id display = active == null ? MediaSource.Id.UNKNOWN : active.displayId();
        if (display == MediaSource.Id.ONLINE && ownerPackage != null && !ownerPackage.isEmpty()) {
            for (int index = 0; index < tiles.size(); index++) {
                if (ownerPackage.equals(tiles.get(index).playerPackage)) return index;
            }
        }
        for (int index = 0; index < tiles.size(); index++) {
            SourceTile tile = tiles.get(index);
            if (!tile.isPlayer() && tile.source == display) return index;
        }
        return -1;
    }

    /** The snapshot's entry for the tile's head-unit source, or null when it reports none. */
    static MediaSource sourceFor(SourceTile tile, List<MediaSource> sources) {
        MediaSource match = null;
        for (MediaSource source : sources) {
            if (source.id.displayId() == tile.source
                    && (match == null || source.id == tile.source)) match = source;
        }
        return match;
    }

    /**
     * A tile is tappable when it is not the playing one and its source accepts a switch. A player
     * tile needs its app installed but not OneOS reporting ONLINE available: the app itself is the
     * source, and the Bridge starts it without MediaCenter too.
     */
    static boolean selectable(List<SourceTile> tiles, int index, MediaSnapshot snapshot,
            boolean playerInstalled) {
        if (snapshot == null || index < 0 || index >= tiles.size()) return false;
        if (index == selectedIndex(tiles, MediaSource.selectedId(snapshot.audioSource,
                snapshot.sources), snapshot.ownerPackage)) return false;
        SourceTile tile = tiles.get(index);
        if (tile.isPlayer() && !playerInstalled) return false;
        MediaSource source = sourceFor(tile, snapshot.sources);
        return source != null && (tile.isPlayer() || source.available)
                && (source.capabilities & MediaBridgeContract.CAP_SET_SOURCE) != 0L;
    }

    @Override public boolean equals(Object other) {
        return other instanceof SourceTile tile && tile.source == source
                && tile.playerPackage.equals(playerPackage);
    }

    @Override public int hashCode() { return Objects.hash(source, playerPackage); }

    @Override public String toString() { return wire(); }
}
