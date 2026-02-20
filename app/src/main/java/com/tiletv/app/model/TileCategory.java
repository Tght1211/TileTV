package com.tiletv.app.model;

import java.util.List;

public class TileCategory {
    private String name;
    private List<TileItem> tiles;

    // getter/setter
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<TileItem> getTiles() { return tiles; }
    public void setTiles(List<TileItem> tiles) { this.tiles = tiles; }
}
