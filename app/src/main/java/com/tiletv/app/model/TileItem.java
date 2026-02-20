package com.tiletv.app.model;

public class TileItem {
    private String name;
    private String url;
    private String icon;
    private int level; // 1=TV版网页(原生支持遥控器), 2=普通网页+空间焦点导航, 3=虚拟光标模式

    public TileItem() {}

    public TileItem(String name, String url, String icon, int level) {
        this.name = name;
        this.url = url;
        this.icon = icon;
        this.level = level;
    }

    // 所有 getter 和 setter
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
}
