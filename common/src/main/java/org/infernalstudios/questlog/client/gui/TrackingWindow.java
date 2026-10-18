package org.infernalstudios.questlog.client.gui;

/** GUI-scaled geometry; no rendering or Minecraft singleton required. */
public final class TrackingWindow {
    public static final int BORDER = 8;
    public static final int HEADER = 32;
    public static final int ROW = 28;
    public static final int OBJECTIVE_ROW = 12;
    public static final int MIN_HEIGHT = HEADER + 28 + BORDER;
    public static int rowHeight(int index, int firstObjectiveCount) {
        return index == 0 ? (int) Math.min(Integer.MAX_VALUE, ROW + (long) Math.max(0, firstObjectiveCount) * OBJECTIVE_ROW) : ROW;
    }
    public static int rowTop(int index, int firstObjectiveCount) {
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.max(0, index) * ROW
                + (index > 0 ? (long) Math.max(0, firstObjectiveCount) * OBJECTIVE_ROW : 0));
    }
    public static int indexAt(int contentY, int count, int firstObjectiveCount) {
        if (count <= 0 || contentY < 0 || contentY >= rowTop(count, firstObjectiveCount)) return -1;
        if (contentY < rowHeight(0, firstObjectiveCount)) return 0;
        return 1 + (contentY - rowHeight(0, firstObjectiveCount)) / ROW;
    }
    public int x, y, width, height;
    private int edges, originX, originY, originWidth, originHeight;
    private double pressX, pressY;
    private boolean moving;
    public TrackingWindow(int x, int y, int width, int height) { this.x=x; this.y=y; this.width=width; this.height=height; }
    public boolean contains(double mx, double my) { return mx >= x && mx < x + width && my >= y && my < y + height; }
    public void clamp(int screenWidth, int screenHeight) {
        width = Math.min(Math.max(110, width), Math.max(1, screenWidth));
        height = Math.min(Math.max(MIN_HEIGHT, height), Math.max(1, screenHeight));
        x = Math.max(0, Math.min(x, screenWidth - width));
        y = Math.max(0, Math.min(y, screenHeight - height));
    }
    public boolean begin(double mx, double my) {
        if (!contains(mx, my)) return false;
        edges = (mx < x+BORDER ? 1 : mx >= x+width-BORDER ? 2 : 0)
                | (my < y+BORDER ? 4 : my >= y+height-BORDER ? 8 : 0);
        moving = edges == 0 && my < y+HEADER;
        if (!moving && edges == 0) return false;
        pressX=mx; pressY=my; originX=x; originY=y; originWidth=width; originHeight=height;
        return true;
    }
    public boolean dragging() { return moving || edges != 0; }
    public void drag(double mx, double my, int screenWidth, int screenHeight) {
        if (!dragging()) return;
        int dx=(int)(mx-pressX), dy=(int)(my-pressY);
        if (moving) { x=originX+dx; y=originY+dy; }
        else {
            int right=originX+originWidth, bottom=originY+originHeight;
            if ((edges&1)!=0) { x=Math.max(0, Math.min(originX+dx,right-110)); width=right-x; }
            if ((edges&2)!=0) width=Math.max(110, originWidth+dx);
            if ((edges&4)!=0) { y=Math.max(0,Math.min(originY+dy,bottom-MIN_HEIGHT)); height=bottom-y; }
            if ((edges&8)!=0) height=Math.max(MIN_HEIGHT,originHeight+dy);
        }
        clamp(screenWidth, screenHeight);
    }
    public void end() { moving=false; edges=0; }
}
