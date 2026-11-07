package org.infernalstudios.questlog.client.gui;

import net.minecraft.resources.ResourceLocation;
import org.infernalstudios.questlog.core.QuestManager;
import org.infernalstudios.questlog.core.QuestTracking;
import org.infernalstudios.questlog.core.quests.Quest;

import java.util.List;

/** Local drag preview; the authoritative order changes only when the gesture is released. */
public final class TrackingReorder {
    private final QuestTracking preview = new QuestTracking();
    private ResourceLocation source;
    private double grabOffset;

    public boolean begin(List<ResourceLocation> ids, ResourceLocation source, double pointerY, int rowTop) {
        if (!ids.contains(source)) return false;
        preview.replace(ids, ignored -> true);
        this.source = source;
        this.grabOffset = pointerY - rowTop;
        return true;
    }

    public boolean active() { return source != null; }
    public ResourceLocation source() { return source; }
    public double top(double pointerY) { return pointerY - grabOffset; }
    public List<ResourceLocation> ids() { return preview.ids(); }
    public List<Quest> display(QuestManager manager, boolean authorMode) { return preview.display(manager, authorMode); }
    public void end() { source = null; }

    public boolean move(List<Quest> rows, int firstVisible, int contentTop, int rowHeight, double pointerY) {
        if (!active() || rows.isEmpty()) return false;
        int sourceIndex = -1;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).getId().equals(source)) { sourceIndex = i; break; }
        if (sourceIndex < 0) return false;
        Quest quest = rows.get(sourceIndex);
        int first = sourceIndex, last = sourceIndex;
        while (first > 0 && sameGroup(quest, rows.get(first - 1))) first--;
        while (last + 1 < rows.size() && sameGroup(quest, rows.get(last + 1))) last++;
        double center = top(pointerY) + rowHeight / 2.0;
        int targetIndex = firstVisible + (int) Math.floor((center - contentTop) / rowHeight);
        targetIndex = Math.max(first, Math.min(last, targetIndex));
        double targetCenter = contentTop + (targetIndex - firstVisible) * rowHeight + rowHeight / 2.0;
        boolean after = center > targetCenter || center == targetCenter && targetIndex > sourceIndex;
        return moveToIndex(rows, targetIndex, after);
    }

    public boolean moveToIndex(List<Quest> rows, int targetIndex, boolean after) {
        if (!active() || rows.isEmpty()) return false;
        int sourceIndex = -1;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).getId().equals(source)) { sourceIndex = i; break; }
        if (sourceIndex < 0) return false;
        Quest quest = rows.get(sourceIndex);
        int first = sourceIndex, last = sourceIndex;
        while (first > 0 && sameGroup(quest, rows.get(first - 1))) first--;
        while (last + 1 < rows.size() && sameGroup(quest, rows.get(last + 1))) last++;
        targetIndex = Math.max(first, Math.min(last, targetIndex));
        return preview.reorder(source, rows.get(targetIndex).getId(), after);
    }

    private static boolean sameGroup(Quest a, Quest b) {
        return (a.isActive() && a.isCompleted()) == (b.isActive() && b.isCompleted());
    }
}
