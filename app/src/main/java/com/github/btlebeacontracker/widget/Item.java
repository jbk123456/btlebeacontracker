package com.github.btlebeacontracker.widget;

import java.io.Serializable;

public class Item implements Serializable, Comparable<Item> {

    private String id;
    private String caption;
    private boolean isActive;

    private Item(String id, String caption) {
        super();
        this.id = id;
        this.caption = caption;
        isActive = true;
    }

    public Item(String id, String caption, boolean b) {
        this(id, caption);
        isActive = b;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    boolean isActive() {
        return isActive;
    }

    public void setActive(boolean b) {
        this.isActive = b;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public int compareTo(Item other) {
        return id.compareTo(other.id);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Item)) return false;
        return ((Item) other).id.equals(id);
    }
}
