package org.infernalstudios.questlog.core.quests.display;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
//? if >=1.21
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import org.infernalstudios.questlog.Questlog;
import org.infernalstudios.questlog.core.QuestText;
import org.infernalstudios.questlog.client.gui.QuestlogGuiSet;
import org.infernalstudios.questlog.core.quests.Quest;
import org.infernalstudios.questlog.core.quests.objectives.Objective;
import org.infernalstudios.questlog.core.quests.rewards.ChoiceReward;
import org.infernalstudios.questlog.core.quests.rewards.Reward;
import org.infernalstudios.questlog.util.JsonUtils;
import org.infernalstudios.questlog.util.texture.AnimatedTexture;
import org.infernalstudios.questlog.util.texture.Blittable;
import org.infernalstudios.questlog.util.texture.Texture;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuestDisplayData {
    private final Component title;
    private final Component description;
    @Nullable
    private final Component details;
    @Nullable
    private final Component descriptionCompleted;
    @Nullable
    private final Component descriptionFailed;
    @Nullable
    private final String descriptionTranslationKey;
    @Nullable
    private final String detailsTranslationKey;
    @Nullable
    private final String completedTranslationKey;
    @Nullable
    private final String failedTranslationKey;
    @Nullable
    private final Blittable icon;
    @Nullable
    private final ResourceLocation completedSound;
    @Nullable
    private final ResourceLocation triggeredSound;

    private final boolean toastOnUnlock;
    private final boolean toastOnComplete;
    private final boolean showPopupOnUnlock;
    private final boolean hidden;
    private final boolean hideWhenCompleted;
    private final boolean includeInMain;

    private final boolean disableDetailsButton;
    private final boolean detailsOpenByDefault;
    @Nullable
    private final Boolean rewardPreviewOverride;

    private final ResourceLocation bgTexture;
    private final ResourceLocation rightPanelTexture;
    private final ResourceLocation peripheralTexture;
    @Nullable
    private final ResourceLocation overlayTexture;
    private final int overlayWidth;
    private final int overlayHeight;
    private final int overlayXOffset;
    private final int overlayYOffset;

    @Nullable
    private final Blittable badge;

    private final Component backButtonText;
    private final Component collectButtonText;
    private final Component collectedText;
    private final Component uncollectedText;
    private final Palette palette;

    private final String chapter;
    private final int sortOrder;

    private final int leftPanelWidth;
    private final int rightPanelWidth;
    private final int panelHeight;
    private final int leftPanelXOffset;
    private final int leftPanelYOffset;
    private final int rightPanelXOffset;
    private final int rightPanelYOffset;

    @Nullable
    private List<ObjectiveDisplayData> objectiveDisplay = null;
    @Nullable
    private List<RewardDisplayData> rewardDisplay = null;

    private final JsonObject textSource;
    private final String constructedLanguage;
    private String cachedLanguage;
    private QuestDisplayData cachedText;

    public QuestDisplayData(JsonObject data) { this(data, true); }
    private QuestDisplayData(JsonObject data, boolean dynamic) {
        this.textSource = dynamic ? data.deepCopy() : null;
        this.constructedLanguage = QuestText.language();
        data = QuestText.project(data);
        boolean translatable = JsonUtils.getOrDefault(data, "translatable", false);

        String title = JsonUtils.getOrDefault(data, "title", "");
        this.title = QuestText.translatable(data,"title",translatable) ? Component.translatable(title) : Component.literal(title);
        this.sortOrder = JsonUtils.getOrDefault(data, "sort_order", JsonUtils.getOrDefault(data, "order", 0));

        Component parsedDescription = parseDescription(data.get("description"), QuestText.translatable(data,"description",translatable));
        if (parsedDescription == null) {
            String rawStr = JsonUtils.getOrDefault(data, "description", "");
            parsedDescription = parseInlineRichText(QuestText.translatable(data,"description",translatable) ? Component.translatable(rawStr).getString() : rawStr);
        }
        this.description = parsedDescription;
        this.descriptionTranslationKey = translationKey(data.get("description"), QuestText.translatable(data,"description",translatable));
        JsonElement detailsElement = data.get("details");
        Component parsedDetails = detailsElement == null || detailsElement.isJsonNull()
                || detailsElement.isJsonArray() && detailsElement.getAsJsonArray().isEmpty()
                ? null : parseDescription(detailsElement, QuestText.translatable(data,"details",translatable));
        this.detailsTranslationKey = translationKey(detailsElement, QuestText.translatable(data,"details",translatable));
        this.details = parsedDetails != null
                && (this.detailsTranslationKey != null || !parsedDetails.getString().isBlank()) ? parsedDetails : null;
        this.descriptionCompleted = parseDescription(data.get("description_completed"), QuestText.translatable(data,"description_completed",translatable));
        this.completedTranslationKey = translationKey(data.get("description_completed"), QuestText.translatable(data,"description_completed",translatable));
        this.descriptionFailed = parseDescription(data.get("description_failed"), QuestText.translatable(data,"description_failed",translatable));
        this.failedTranslationKey = translationKey(data.get("description_failed"), QuestText.translatable(data,"description_failed",translatable));

        if (data.has("badge") && data.get("badge").isJsonObject()) {
            JsonObject badgeObj = data.getAsJsonObject("badge");
            String texture = JsonUtils.getString(badgeObj, "texture");
            int u = JsonUtils.getOrDefault(badgeObj, "u", 0);
            int v = JsonUtils.getOrDefault(badgeObj, "v", 0);
            int w = JsonUtils.getOrDefault(badgeObj, "width", 16);
            int h = JsonUtils.getOrDefault(badgeObj, "height", 16);
            int tw = JsonUtils.getOrDefault(badgeObj, "texture_width", 256);
            int th = JsonUtils.getOrDefault(badgeObj, "texture_height", 256);
            int frames = JsonUtils.getOrDefault(badgeObj, "frames", 1);
            int frameTime = JsonUtils.getOrDefault(badgeObj, "frame_time", 100);

            if (frames > 1) {
                this.badge = new AnimatedTexture(Questlog.parseId(texture), w, h, u, v, tw, th, frames, frameTime);
            } else {
                this.badge = new Texture(Questlog.parseId(texture), w, h, u, v, tw, th);
            }
        } else {
            this.badge = null;
        }

        this.icon = JsonUtils.getIcon(data, "icon");

        this.chapter = JsonUtils.getOrDefault(data, "chapter", "questlog:main");
        boolean isMainChapter = this.chapter.equals("questlog:main") || this.chapter.equals("main");
        this.includeInMain = JsonUtils.getOrDefault(data, "include_in_main", isMainChapter);

        this.leftPanelWidth = JsonUtils.getOrDefault(data, "left_panel_width", 275);
        this.rightPanelWidth = JsonUtils.getOrDefault(data, "right_panel_width", 170);
        this.panelHeight = JsonUtils.getOrDefault(data, "panel_height", 166);

        String overlayLoc = JsonUtils.getOrDefault(data, "overlay", (String) null);
        this.overlayTexture = overlayLoc == null ? null : Questlog.parseId(overlayLoc);
        this.overlayWidth = JsonUtils.getOrDefault(data, "overlay_width", this.leftPanelWidth);
        this.overlayHeight = JsonUtils.getOrDefault(data, "overlay_height", this.panelHeight);
        this.overlayXOffset = JsonUtils.getOrDefault(data, "overlay_x_offset", 0);
        this.overlayYOffset = JsonUtils.getOrDefault(data, "overlay_y_offset", 0);

        String completedSoundLoc = JsonUtils.getOrDefault(data, "completed_sound", (String) null);
        this.completedSound = completedSoundLoc == null ? null : Questlog.parseId(completedSoundLoc);
        String triggeredSoundLoc = JsonUtils.getOrDefault(data, "triggered_sound", (String) null);
        this.triggeredSound = triggeredSoundLoc == null ? null : Questlog.parseId(triggeredSoundLoc);

        String backgroundLoc = JsonUtils.getOrDefault(data, "background_texture", Questlog.MODID + ":textures/gui/quest_page.png");
        String rightPanelLoc = JsonUtils.getOrDefault(data, "right_panel_texture", backgroundLoc);
        String peripheralLoc = JsonUtils.getOrDefault(data, "peripheral_texture", Questlog.MODID + ":textures/gui/quest_peripherals.png");
        this.bgTexture = Questlog.parseId(backgroundLoc);
        this.rightPanelTexture = Questlog.parseId(rightPanelLoc);
        this.peripheralTexture = Questlog.parseId(peripheralLoc);

        this.palette = new Palette(
                parseColor(data, "text_color"),
                parseColor(data, "completed_text_color"),
                parseColor(data, "hovered_text_color"),
                parseColor(data, "title_color"),
                parseColor(data, "progress_text_color")
        );

        this.backButtonText = parseComponent(data, "back_button_text", "gui.back", QuestText.translatable(data,"back_button_text",translatable));
        this.collectButtonText = parseComponent(data, "collect_button_text", "questlog.reward.collect", QuestText.translatable(data,"collect_button_text",translatable));
        this.uncollectedText = parseComponent(data, "uncollected_text", "questlog.reward.uncollected", QuestText.translatable(data,"uncollected_text",translatable));
        this.collectedText = parseComponent(data, "collected_text", "questlog.reward.collected", QuestText.translatable(data,"collected_text",translatable));

        this.toastOnUnlock = JsonUtils.getOrDefault(data, "toast_on_unlock", true);
        this.toastOnComplete = JsonUtils.getOrDefault(data, "toast_on_complete", true);
        this.showPopupOnUnlock = JsonUtils.getOrDefault(data, "show_popup_on_unlock", false);
        this.hidden = JsonUtils.getOrDefault(data, "hidden", false);
        this.hideWhenCompleted = JsonUtils.getOrDefault(data, "hide_when_completed", false);

        this.disableDetailsButton = JsonUtils.getOrDefault(data, "disable_details_button", false);
        this.detailsOpenByDefault = JsonUtils.getOrDefault(data, "details_open_by_default", false);
        this.rewardPreviewOverride = data.has("show_rewards_before_completion")
                ? JsonUtils.getBoolean(data, "show_rewards_before_completion") : null;

        this.leftPanelXOffset = JsonUtils.getOrDefault(data, "left_panel_x_offset", 0);
        this.leftPanelYOffset = JsonUtils.getOrDefault(data, "left_panel_y_offset", 0);
        this.rightPanelXOffset = JsonUtils.getOrDefault(data, "right_panel_x_offset", 0);
        this.rightPanelYOffset = JsonUtils.getOrDefault(data, "right_panel_y_offset", 0);
    }

    @Nullable
    private static String translationKey(JsonElement element, boolean translatable) {
        if (!translatable || element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) return null;
        String value = element.getAsString();
        if ((value.startsWith("[") && value.endsWith("]") && !value.contains("]("))
                || (value.startsWith("{") && value.endsWith("}"))) return null;
        return value;
    }

    @Nullable
    private Component resolveLocalized(@Nullable Component parsed, @Nullable String key) {
        return key == null ? parsed : parseInlineRichText(Component.translatable(key).getString());
    }

    private Component parseDescription(JsonElement descriptionElement, boolean translatable) {
        if (descriptionElement == null) return null;
        Component parsedDescription = null;
        try {
            if (descriptionElement.isJsonArray() || descriptionElement.isJsonObject()) {
                //? if >=1.21 {
                parsedDescription = Component.Serializer.fromJson(descriptionElement, RegistryAccess.EMPTY);
                //?} else
                //parsedDescription = Component.Serializer.fromJson(descriptionElement);
            } else if (descriptionElement.isJsonPrimitive()) {
                String rawStr = descriptionElement.getAsString();
                if ((rawStr.startsWith("[") && rawStr.endsWith("]") && !rawStr.contains("](")) ||
                        (rawStr.startsWith("{") && rawStr.endsWith("}"))) {
                    try {
                        //? if >=1.21 {
                        parsedDescription = Component.Serializer.fromJson(rawStr, RegistryAccess.EMPTY);
                        //?} else
                        //parsedDescription = Component.Serializer.fromJson(rawStr);
                    } catch (Exception ignored) {
                        parsedDescription = parseInlineRichText(translatable ? Component.translatable(rawStr).getString() : rawStr);
                    }
                } else {
                    parsedDescription = parseInlineRichText(translatable ? Component.translatable(rawStr).getString() : rawStr);
                }
            }
        } catch (Exception e) {
            Questlog.LOGGER.error("Failed to parse description for quest", e);
        }
        return parsedDescription;
    }

    private Component parseInlineRichText(String text) {
        Pattern pattern = Pattern.compile("\\[([^]]+)]\\(([^)]+)\\)");
        Matcher matcher = pattern.matcher(text);
        MutableComponent component = Component.empty();
        int lastEnd = 0;
        while (matcher.find()) {
            component.append(Component.literal(text.substring(lastEnd, matcher.start())));
            String display = matcher.group(1);
            String action = matcher.group(2);
            MutableComponent part = Component.literal(display);
            Style style = Style.EMPTY.withUnderlined(true);

            if (action.startsWith("quest:")) {
                style = style.withClickEvent(new ClickEvent(ClickEvent.Action.CHANGE_PAGE, action.substring(6)));
            } else if (action.startsWith("image:")) {
                style = style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(action)));
            } else if (action.startsWith("item:")) {
                ResourceLocation itemId = ResourceLocation.tryParse(action.substring(5));
                ItemStack stack = itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)
                        ? BuiltInRegistries.ITEM.get(itemId).getDefaultInstance() : ItemStack.EMPTY;
                if (stack.isEmpty()) {
                    Questlog.LOGGER.warn("Unknown item link '{}' in quest description", action);
                    component.append(Component.literal(display));
                    lastEnd = matcher.end();
                    continue;
                }
                style = style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(stack)));
                style = style.withClickEvent(new ClickEvent(ClickEvent.Action.CHANGE_PAGE, action));
            }
            component.append(part.withStyle(style));
            lastEnd = matcher.end();
        }
        component.append(Component.literal(text.substring(lastEnd)));
        return component;
    }

    private Component parseComponent(JsonObject style, String key, String defaultKey, boolean translatable) {
        String raw = JsonUtils.getOrDefault(style, key, (String) null);
        if (raw == null) return Component.translatable(defaultKey);
        return translatable ? Component.translatable(raw) : Component.literal(raw);
    }

    public void setQuest(Quest quest) {
        this.objectiveDisplay = new java.util.ArrayList<>();
        for (Objective obj : quest.objectives) {
            addObjectiveDisplayData(obj, 0);
        }
        this.rewardDisplay = new java.util.ArrayList<>();
        for (Reward reward : quest.rewards) {
            addRewardDisplayData(reward, 0);
        }
    }

    private void addRewardDisplayData(Reward reward, int indentLevel) {
        RewardDisplayData displayData = reward.getDisplay();
        if (displayData != null) {
            displayData.setIndentLevel(indentLevel);
            this.rewardDisplay.add(displayData);
        }
        if (reward instanceof ChoiceReward choiceReward) {
            for (Reward choice : choiceReward.getChoices()) {
                addRewardDisplayData(choice, indentLevel + 1);
            }
        }
    }

    private void addObjectiveDisplayData(Objective obj, int indentLevel) {
        if (obj.isHidden()) return;
        ObjectiveDisplayData displayData = obj.getDisplay();
        if (displayData != null) {
            displayData.setIndentLevel(indentLevel);
            this.objectiveDisplay.add(displayData);
        }
        for (Objective child : obj.getChildren()) {
            addObjectiveDisplayData(child, indentLevel + 1);
        }
    }

    private QuestDisplayData texts() {
        if (textSource == null || constructedLanguage.equals(QuestText.language())) return this;
        if (!QuestText.language().equals(cachedLanguage)) {
            cachedText = new QuestDisplayData(textSource, false); cachedLanguage = QuestText.language();
        }
        return cachedText;
    }

    public boolean matchesSearch(String query) {
        if (query == null || query.isBlank()) return true;
        String lowerQuery = query.toLowerCase();

        if (this.getTitle().getString().toLowerCase().contains(lowerQuery)) return true;
        Component description = this.getDescription();
        if (description != null && description.getString().toLowerCase().contains(lowerQuery)) return true;
        if (this.hasDetails() && this.getDetails().getString().toLowerCase().contains(lowerQuery)) return true;
        Component completed = texts().resolveLocalized(texts().descriptionCompleted, texts().completedTranslationKey);
        if (completed != null && completed.getString().toLowerCase().contains(lowerQuery)) return true;
        Component failed = texts().resolveLocalized(texts().descriptionFailed, texts().failedTranslationKey);
        if (failed != null && failed.getString().toLowerCase().contains(lowerQuery)) return true;
        if (this.objectiveDisplay != null) {
            for (ObjectiveDisplayData obj : this.objectiveDisplay) {
                if (obj.getName().getString().toLowerCase().contains(lowerQuery)) return true;
            }
        }
        if (this.rewardDisplay != null) {
            for (RewardDisplayData rew : this.rewardDisplay) {
                if (rew.getName().getString().toLowerCase().contains(lowerQuery)) return true;
            }
        }
        return false;
    }

    public Component getDescription(Quest quest) {
        if (quest != null) {
            if (quest.isFailed() && texts().descriptionFailed != null) {
                return texts().resolveLocalized(texts().descriptionFailed, texts().failedTranslationKey);
            }
            if (quest.isCompleted() && texts().descriptionCompleted != null) {
                return texts().resolveLocalized(texts().descriptionCompleted, texts().completedTranslationKey);
            }
        }
        return this.getDescription();
    }

    public String getChapter() {
        return this.chapter;
    }

    public int getSortOrder() {
        return this.sortOrder;
    }

    public Component getTitle() {
        return texts().title;
    }

    public Component getDescription() {
        return texts().resolveLocalized(texts().description, texts().descriptionTranslationKey);
    }

    public boolean hasDetails() {
        return texts().details != null && !this.getDetails().getString().isBlank();
    }

    public Component getDetails() {
        return texts().details != null ? texts().resolveLocalized(texts().details, texts().detailsTranslationKey)
                : Component.translatable("questlog.info.no_details").withStyle(style -> style.withItalic(true));
    }

    public List<ObjectiveDisplayData> getObjectiveDisplayData() {
        if (this.objectiveDisplay == null)
            throw new IllegalStateException("QuestDisplayData has not been assigned a quest");
        return this.objectiveDisplay;
    }

    public List<RewardDisplayData> getRewardDisplayData() {
        if (this.rewardDisplay == null)
            throw new IllegalStateException("QuestDisplayData has not been assigned a quest");
        return this.rewardDisplay;
    }

    @Nullable
    public Blittable getBadge() {
        return this.badge;
    }

    @Nullable
    public Blittable getIcon() {
        return this.icon;
    }

    @Nullable
    public SoundEvent getCompletedSound() {
        return BuiltInRegistries.SOUND_EVENT.get(this.completedSound);
    }

    @Nullable
    public SoundEvent getTriggeredSound() {
        return BuiltInRegistries.SOUND_EVENT.get(this.triggeredSound);
    }

    public int getLeftPanelWidth() {
        return this.leftPanelWidth;
    }

    public int getRightPanelWidth() {
        return this.rightPanelWidth;
    }

    public int getPanelHeight() {
        return this.panelHeight;
    }

    public int getLeftPanelXOffset() {
        return this.leftPanelXOffset;
    }

    public int getLeftPanelYOffset() {
        return this.leftPanelYOffset;
    }

    public int getRightPanelXOffset() {
        return this.rightPanelXOffset;
    }

    public int getRightPanelYOffset() {
        return this.rightPanelYOffset;
    }

    public boolean isDetailsButtonDisabled() {
        return this.disableDetailsButton;
    }

    public boolean isDetailsOpenByDefault() {
        return this.detailsOpenByDefault;
    }

    public boolean shouldShowRewardPreviews(boolean clientDefault) {
        return this.rewardPreviewOverride != null ? this.rewardPreviewOverride : clientDefault;
    }

    public QuestlogGuiSet getGuiSet() {
        return (this.bgTexture.equals(QuestlogGuiSet.DEFAULT.backgroundLoc) &&
                this.rightPanelTexture.equals(QuestlogGuiSet.DEFAULT.rightPanelLoc) &&
                this.peripheralTexture.equals(QuestlogGuiSet.DEFAULT.peripheralLoc) &&
                this.leftPanelWidth == 275 &&
                this.rightPanelWidth == 170 &&
                this.panelHeight == 166)
                ? QuestlogGuiSet.DEFAULT
                : new QuestlogGuiSet(this.bgTexture, this.rightPanelTexture, this.peripheralTexture, this.leftPanelWidth, this.rightPanelWidth, this.panelHeight);
    }

    public boolean shouldToastOnUnlock() {
        return this.toastOnUnlock;
    }

    public boolean shouldToastOnComplete() {
        return this.toastOnComplete;
    }

    public boolean shouldShowPopupOnUnlock() {
        return this.showPopupOnUnlock;
    }

    public boolean shouldIncludeInMain() {
        return this.includeInMain;
    }

    public boolean isHidden() {
        return this.hidden;
    }

    public boolean shouldHideWhenCompleted() {
        return this.hideWhenCompleted;
    }


    public Palette getPalette() {
        return this.palette;
    }

    public Component getBackButtonText() {
        return texts().backButtonText;
    }

    public Component getCollectButtonText() {
        return texts().collectButtonText;
    }

    public Component getCollectedText() {
        return texts().collectedText;
    }

    public Component getUncollectedText() {
        return texts().uncollectedText;
    }

    @Nullable
    public ResourceLocation getOverlayTexture() {
        return this.overlayTexture;
    }

    public int getOverlayWidth() {
        return this.overlayWidth;
    }

    public int getOverlayHeight() {
        return this.overlayHeight;
    }

    public int getOverlayXOffset() {
        return this.overlayXOffset;
    }

    public int getOverlayYOffset() {
        return this.overlayYOffset;
    }

    @Nullable
    private Integer parseColor(JsonObject data, String key) {
        String colorStr = JsonUtils.getOrDefault(data, key, (String) null);
        if (colorStr == null) return null;
        return colorStr.startsWith("#") ? Integer.parseInt(colorStr.substring(1), 16) : Integer.parseInt(colorStr, 16);
    }
}
