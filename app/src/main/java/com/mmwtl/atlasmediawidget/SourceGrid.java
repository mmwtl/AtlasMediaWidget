package com.mmwtl.atlasmediawidget;

/**
 * Rows of the source chooser for a given tile count and chooser area. The grid that gives the
 * largest square tile wins, so a flat wide card lays the tiles out in one row, a square card in
 * two and a tall narrow one in a column of pairs. Rows that cannot take a full set are the upper
 * ones: five tiles make two over three, as the chooser always did.
 */
final class SourceGrid {
    /** Grids whose square tile is at most this much smaller count as equal; then fewer rows win. */
    private static final float TIE = 0.06f;

    /** Tiles in each row, top to bottom. */
    final int[] rowSizes;
    final int columns;

    private SourceGrid(int[] rowSizes) {
        this.rowSizes = rowSizes;
        int widest = 0;
        for (int size : rowSizes) widest = Math.max(widest, size);
        this.columns = widest;
    }

    int rows() { return rowSizes.length; }

    /** Row of the tile at {@code index}, or -1 outside the grid. */
    int rowOf(int index) {
        int first = 0;
        for (int row = 0; row < rowSizes.length; row++) {
            if (index >= first && index < first + rowSizes[row]) return row;
            first += rowSizes[row];
        }
        return -1;
    }

    /** Position of the tile at {@code index} within its row, or -1 outside the grid. */
    int columnOf(int index) {
        int first = 0;
        for (int size : rowSizes) {
            if (index >= first && index < first + size) return index - first;
            first += size;
        }
        return -1;
    }

    /**
     * @param width  chooser width available to the tiles, margins included
     * @param height chooser height available to the tiles, margins included
     * @param minTileWidth  narrowest tile that still shows its label
     * @param minTileHeight lowest tile that still shows an icon over its label
     */
    static SourceGrid plan(int count, int width, int height, int minTileWidth, int minTileHeight) {
        if (count <= 0) return new SourceGrid(new int[0]);
        int bestRows = 1;
        float bestScore = -1f;
        boolean bestFits = false;
        for (int rows = 1; rows <= count; rows++) {
            int columns = (count + rows - 1) / rows;
            // A row count that leaves a whole row empty repeats a smaller grid.
            if ((columns * (rows - 1)) >= count) continue;
            float tileWidth = width / (float) columns;
            float tileHeight = height / (float) rows;
            boolean fits = tileWidth >= minTileWidth && tileHeight >= minTileHeight;
            float score = Math.min(tileWidth, tileHeight);
            boolean better;
            if (fits != bestFits) better = fits;
            else better = score > bestScore * (1f + TIE);
            if (bestScore < 0f || better) {
                bestRows = rows;
                bestScore = score;
                bestFits = fits;
            }
        }
        int[] sizes = new int[bestRows];
        int base = count / bestRows;
        int extra = count % bestRows;
        for (int row = 0; row < bestRows; row++) {
            sizes[row] = base + (row >= bestRows - extra ? 1 : 0);
        }
        return new SourceGrid(sizes);
    }
}
