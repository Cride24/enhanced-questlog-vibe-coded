package org.infernalstudios.questlog;

import com.google.gson.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.infernalstudios.questlog.client.gui.TrackingWindow;
import org.infernalstudios.questlog.client.gui.TrackingReorder;
import org.infernalstudios.questlog.core.*;
import org.infernalstudios.questlog.core.quests.*;
import org.infernalstudios.questlog.core.quests.display.QuestDisplayData;
import org.infernalstudios.questlog.core.quests.objectives.misc.ReadObjective;
import org.infernalstudios.questlog.core.quests.rewards.Reward;
import org.infernalstudios.questlog.core.validation.*;
import java.util.*;

/** Deterministic state/serialization checks. Does not validate visual rendering or launch a game. */
public final class TrackingValidationVerification {
    private static int checks;
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        check(!QuestValidation.inspect(new JsonObject(),QuestValidation.UNAVAILABLE).functional(), "Title is mandatory");
        var missingDescription = json("{}"); missingDescription.remove("description");
        check(!QuestValidation.inspect(missingDescription,QuestValidation.UNAVAILABLE).functional(), "Missing description prevents activation");
        check(!QuestValidation.inspect(json("{\"description\":\"   \"}"),QuestValidation.UNAVAILABLE).functional(), "Blank description prevents activation");
        check(QuestValidation.inspect(json("{\"description\":{\"text\":\"Formatted description\"}}"),QuestValidation.UNAVAILABLE).functional(), "Component descriptions remain supported");
        check(!QuestValidation.inspect(json("{\"description\":{\"text\":\"\"}}"),QuestValidation.UNAVAILABLE).functional(), "Empty component description stays incomplete");
        verifyIdentityAndFeedback();
        verifyNewQuestOrder();
        verifyLiveReordering();
        var refs = (QuestValidation.References)(kind,id,tag) -> !id.getPath().equals("missing");
        check(QuestValidation.inspect(json("{}"),refs).functional(), "Existing defaults and rewardless quests remain valid");
        check(QuestValidation.inspect(json("{\"objectives\":[{\"type\":\"item_obtain\"}]}"),refs).functional(), "Item wildcard stays optional");
        check(QuestValidation.inspect(json("{\"objectives\":[{\"type\":\"entity_kill\"}]}"),refs).functional(), "Entity wildcard stays optional");
        check(QuestValidation.inspect(json("{\"objectives\":[{\"type\":\"enchant\"}]}"),refs).functional(), "Enchant wildcard stays optional");
        check(!QuestValidation.inspect(json("{\"objectives\":[{\"type\":\"block_place\"}]}"),refs).functional(), "Missing mandatory target");
        check(!QuestValidation.inspect(json("{\"rewards\":[{\"type\":\"item\"}]}"),refs).functional(), "Item reward must name an item");
        for (String invalid : List.of("{\"active\":\"false\"}","{\"objectives\":null}","{\"objectives\":[1]}",
                "{\"objectives\":[{\"type\":\"unknown\"}]}","{\"objectives\":[{}]}",
                "{\"objectives\":[{\"type\":\"read\",\"required_amount\":0}]}",
                "{\"objectives\":[{\"type\":\"read\",\"required_amount\":1.5}]}",
                "{\"rewards\":[{\"type\":\"experience\"}]}",
                "{\"rewards\":[{\"type\":\"choice\",\"choices\":[]}]}",
                "{\"rewards\":[{\"type\":\"random\",\"entries\":[{}]}]}",
                "{\"chapter\":\"questlog:missing\"}","{\"_validation_error\":\"syntax\"}"))
            check(!QuestValidation.inspect(json(invalid),refs).functional(), "Reject malformed definition: " + invalid);
        JsonObject nested=json("{\"objectives\":[{\"type\":\"and\",\"objectives\":[{\"type\":\"quest_complete\",\"quest\":\"questlog:missing\"}]}]}");
        check(QuestValidation.inspect(nested,refs).issues().getFirst().path().equals("objectives[0].objectives[0].quest"), "Precise nested path");
        JsonObject tagged=json("{\"objectives\":[{\"type\":\"item_obtain\",\"item\":\"#minecraft:logs\"}]}");
        String before=tagged.toString();
        check(QuestValidation.inspect(tagged,refs).functional(), "Objective tags supported");
        check(QuestValidation.inspect(json("{\"objectives\":[{\"type\":\"visit_biome\",\"biome\":\"#minecraft:is_overworld\"}]}"),refs).functional(),
                "Biome tags accepted by author validation");
        check(QuestValidation.inspect(json("{\"objectives\":[{\"type\":\"visit_structure\",\"structure\":\"#minecraft:village\"}]}"),refs).functional(),
                "Structure tags accepted by author validation");
        check(before.equals(tagged.toString()), "Validation never repairs/mutates input");
        var unverified=QuestValidation.inspect(tagged,QuestValidation.UNAVAILABLE);
        check(unverified.functional() && !unverified.issues().isEmpty() && !unverified.issues().getFirst().error(), "Unavailable registry is advisory");
        check(!QuestValidation.inspect(json("{\"rewards\":[{\"type\":\"item\",\"item\":\"#minecraft:logs\"}]}"),refs).functional(), "Item rewards cannot use predicate tags");

        QuestManager manager=new QuestManager(null);
        Quest first=quest("first",manager), pending=quest("pending",manager), suspended=quest("suspended",manager);
        ReadObjective objective=new ReadObjective(json("{\"required_amount\":2}"));
        objective.setParent(first); first.objectives.add(objective);
        first.setActive(false); objective.setUnits(1);
        check(objective.getUnits()==0, "Suspension rejects normal progression");
        first.setActive(true); objective.setUnits(1);
        check(objective.getUnits()==1, "Activation resumes progression");
        first.setActive(false); check(objective.getUnits()==1, "Suspension preserves earned units"); first.setActive(true);
        Reward reward=new Reward(new JsonObject()) {}; reward.setParent(pending); pending.rewards.add(reward);
        suspended.setActive(false);
        manager.addQuest(first); manager.addQuest(pending); manager.addQuest(suspended);
        QuestTracking tracking=new QuestTracking();
        tracking.replace(List.of(pending.getId(),first.getId(),first.getId(),suspended.getId()),id->true);
        check(tracking.ids().size()==3, "Deduplicate IDs");
        check(tracking.display(manager).equals(List.of(first,suspended,pending)), "Incomplete/suspended before completed with reward, stable personal order");
        CompoundTag saved=new CompoundTag(); tracking.save(saved);
        QuestTracking restored=new QuestTracking(); restored.load(saved);
        check(restored.ids().equals(tracking.ids()), "Server persistence preserves personal order");
        check(!tracking.prune(manager), "Keep pending rewards and suspended quests");
        check(tracking.display(manager,false).equals(List.of(first,pending)), "Player view hides suspended entries without removing personal IDs");
        check(tracking.contains(suspended.getId()) && tracking.display(manager,true).contains(suspended), "Author mode restores followed inactive entries");
        var oldOrder = tracking.ids();
        check(tracking.reorder(suspended.getId(),first.getId(),false) && tracking.ids().equals(List.of(pending.getId(),suspended.getId(),first.getId())), "Drag reorders IDs and keeps hidden/suspended entries");
        check(!tracking.reorder(first.getId(),first.getId(),true), "Dropping on the same icon preserves order");
        tracking.replace(oldOrder,id->true);
        Reward secondReward=new Reward(new JsonObject()) {}; secondReward.setParent(pending); pending.rewards.add(secondReward);
        reward.setRewarded(true); check(!tracking.prune(manager), "Partial collection keeps the followed quest");
        secondReward.setRewarded(true); check(tracking.prune(manager) && !tracking.contains(pending.getId()), "Only remove once every reward is claimed");
        first.objectives.clear(); check(tracking.prune(manager) && !tracking.contains(first.getId()), "Rewardless completion removes tracking");
        first.objectives.add(objective); check(!tracking.contains(first.getId()), "Reset/new cycle never repins automatically");
        check(tracking.contains(suspended.getId()), "Suspended entry stays followed");
        manager.removeQuest(suspended.getId()); check(tracking.prune(manager) && tracking.ids().isEmpty(), "Deleted quest removes tracking");
        check(!suspended.isActive(), "Removed instances no longer process events");
        JsonObject inactive=json("{\"active\":false}");
        check(!Quest.create(inactive,ResourceLocation.parse("questlog:inactive"),manager).isActive(), "Definition carries explicit deactivation");
        check(Quest.create(json("{}"),ResourceLocation.parse("questlog:legacy"),manager).isActive(), "Old packs stay active by default");

        DefinitionUtil.clearClientCaches();
        check(!QuestValidation.inspect(nested,new QuestReferences(null,null)).functional(), "Real cache lookup reports a missing quest without throwing");
        DefinitionUtil.putCachedQuest(ResourceLocation.parse("questlog:malformed_order"),json("{\"sort_order\":{},\"title\":[]}"));
        DefinitionUtil.putCachedQuest(ResourceLocation.parse("questlog:normal_order"),json("{}"));
        check(DefinitionUtil.getCachedQuestKeys().size()==2, "Malformed ordering/title leaves definitions available to validation");
        DefinitionUtil.clearClientCaches();
        JsonObject draftReward = new JsonObject(); draftReward.addProperty("type","questlog:experience");
        QuestDraftFields.putInteger(draftReward,"experience","");
        check(!QuestValidation.inspectEntry(draftReward,true,refs).functional(), "Saving an empty required number never substitutes a valid default");
        QuestDraftFields.putInteger(draftReward,"experience","bad");
        check(!QuestValidation.inspectEntry(draftReward,true,refs).functional() && QuestDraftFields.text(draftReward,"experience","").equals("bad"), "Malformed numbers stay available for correction");
        QuestDraftFields.putInteger(draftReward,"experience","12");
        check(QuestValidation.inspectEntry(draftReward,true,refs).functional(), "Corrected numeric draft becomes valid");
        verifyFixtures();
        Quest broken = Quest.create(json("{\"_functional\":false}"),ResourceLocation.parse("questlog:broken"),manager);
        check(!broken.isFunctional() && !broken.isActive(), "Server functional status survives definition sync and prevents activity");
        check(Quest.create(json("{}"),ResourceLocation.parse("questlog:valid"),manager).isFunctional(), "Legacy definitions default to functional");
        verifyProgressRecovery();
        verifyPacketCodecs();
        check(!QuestValidation.inspect(json("{\"objectives\":[{\"type\":\"visit_position\",\"bounds\":\"bad\"}]}"),refs).functional(), "Invalid bounds do not silently become origin");
        check(QuestValidation.inspect(json("{\"objectives\":[{\"type\":\"visit_position\",\"bounds\":[1,2,3]}]}"),refs).functional(), "Position coordinate array supported");
        check(QuestValidation.inspect(json("{\"objectives\":[{\"type\":\"visit_position\",\"bounds\":{\"x\":1,\"y\":2,\"z\":3}}]}"),refs).functional(), "Position coordinate object supported");

        TrackingWindow window=new TrackingWindow(10,10,200,140);
        check(TrackingWindow.rowHeight(0,2)==52 && TrackingWindow.rowTop(1,2)==52
                && TrackingWindow.rowTop(2,2)==80, "Only the true first tracked quest reserves objective lines");
        check(TrackingWindow.indexAt(40,3,2)==0 && TrackingWindow.indexAt(52,3,2)==1
                && TrackingWindow.indexAt(80,3,2)==2, "Clicks follow expanded and compact row bounds");
        check(TrackingWindow.indexAt(30+30,3,2)==1 && TrackingWindow.rowTop(1,0)==28,
                "Pixel scrolling and changing the first quest update row geometry");
        check(!window.begin(50,60), "Body is reserved for rows, not window movement");
        check(window.begin(50,20), "Header starts movement"); window.drag(500,500,320,200); window.end();
        check(window.x==120 && window.y==60, "Window remains on screen");
        check(window.begin(window.x+window.width-1,window.y+window.height-1), "Corner starts resize");
        window.drag(0,0,320,200); window.end(); check(window.width==110 && window.height==TrackingWindow.MIN_HEIGHT, "Minimum size leaves space for heading, separator and a whole row");
        window.clamp(90,40); check(window.x==0 && window.y==0 && window.width==90 && window.height==40, "Small GUI scales remain bounded");
        TrackingWindow continuous = new TrackingWindow(20,20,200,140);
        check(continuous.begin(60,30), "Mouse press captures header");
        continuous.drag(80,50,800,600); continuous.drag(110,70,800,600); continuous.drag(150,90,800,600);
        check(continuous.dragging() && continuous.x==110 && continuous.y==80, "Several frames update movement from the original press until release");
        continuous.end(); continuous.drag(300,300,800,600);
        check(continuous.x==110 && continuous.y==80, "Release stops movement");
        check(continuous.begin(309,219), "Mouse press captures the resize corner");
        continuous.drag(340,250,800,600); continuous.drag(400,310,800,600);
        check(continuous.dragging() && continuous.width==291 && continuous.height==231, "Resize continues across frames until release");
        continuous.end();
        verifyFrameResize();
        var config = new org.infernalstudios.questlog.config.QuestlogConfig.Tracking();
        config.backgroundOpacity=-20; check(config.backgroundAlpha()==0, "Negative opacity is bounded");
        config.backgroundOpacity=125; check(config.backgroundAlpha()==1, "Opacity above 100 is bounded");
        config.backgroundOpacity=40; check(Math.abs(config.backgroundAlpha()-.4f)<.001, "Configured opacity becomes shader alpha");
        System.out.println("Tracking/validation: " + checks + " checks passed (headless; in-game checks still required).");
    }
    private static void verifyNewQuestOrder() {
        DefinitionUtil.clearClientCaches();
        check(DefinitionUtil.nextQuestOrder("questlog:lessons")==0,"An empty chapter starts at order zero");
        for (int i=0;i<5;i++) {
            DefinitionUtil.putCachedQuest(ResourceLocation.parse("questlog:lesson"+i),json("{\"chapter\":\"lessons\",\"sort_order\":"+i+"}"));
        }
        DefinitionUtil.putCachedQuest(ResourceLocation.parse("questlog:other"),json("{\"chapter\":\"other\",\"sort_order\":1000,\"include_in_main\":true}"));
        check(DefinitionUtil.nextQuestOrder("questlog:lessons")==5,"Five quests ending at four propose five, ignoring other chapters");
        DefinitionUtil.putCachedQuest(ResourceLocation.parse("questlog:lesson4"),json("{\"chapter\":\"questlog:lessons\",\"sort_order\":40,\"active\":false}"));
        check(DefinitionUtil.nextQuestOrder("lessons")==41,"Append after order forty, including inactive drafts and short chapter IDs");
        DefinitionUtil.putCachedQuest(ResourceLocation.parse("questlog:legacy"),json("{\"chapter\":\"lessons\",\"order\":50}"));
        check(DefinitionUtil.nextQuestOrder("lessons")==51,"Legacy order values also count");
        DefinitionUtil.putCachedQuest(ResourceLocation.parse("questlog:modern"),json("{\"chapter\":\"lessons\",\"sort_order\":60,\"order\":{}}"));
        check(DefinitionUtil.nextQuestOrder("lessons")==61,"Current sort_order takes priority over a malformed legacy field");
        DefinitionUtil.putCachedQuest(ResourceLocation.parse("questlog:malformed"),json("{\"chapter\":{},\"sort_order\":10000}"));
        check(DefinitionUtil.nextQuestOrder("lessons")==61,"Unreadable chapter data cannot change the suggested order");
        check(DefinitionUtil.nextQuestOrder("main")==0,"Main-page inclusions do not count as main-chapter definitions");
        DefinitionUtil.putCachedQuest(ResourceLocation.parse("questlog:negative"),json("{\"chapter\":\"negative\",\"sort_order\":-4}"));
        check(DefinitionUtil.nextQuestOrder("negative")==-3,"Negative ordering advances by one as well");
        DefinitionUtil.putCachedQuest(ResourceLocation.parse("questlog:limit"),json("{\"chapter\":\"limit\",\"sort_order\":2147483647}"));
        check(DefinitionUtil.nextQuestOrder("limit")==Integer.MAX_VALUE,"Integer limit never wraps to a negative order");
        check(DefinitionUtil.nextQuestOrder("Invalid Chapter")==0,"An invalid chapter string remains editable without throwing");
        DefinitionUtil.clearClientCaches();
    }
    private static void verifyFrameResize() {
        for (int edge=0;edge<4;edge++) {
            TrackingWindow window=new TrackingWindow(20,20,200,140);
            int x=switch(edge) { case 0 -> 27; case 1 -> 212; default -> 80; };
            int y=switch(edge) { case 2 -> 27; case 3 -> 152; default -> 80; };
            check(window.begin(x,y),"The full brown frame captures edge "+edge);
            window.drag(x+10,y+10,800,600);
            check(window.dragging() && (window.width!=200 || window.height!=140),"Frame drag resizes instead of moving/opening a quest on edge "+edge);
            window.end();
        }
        TrackingWindow window=new TrackingWindow(20,20,200,140);
        check(!window.begin(28,80),"Content immediately inside the frame remains available to quest icons");
        check(window.begin(80,49),"The separator area belongs to the movable heading");
    }
    private static void verifyLiveReordering() {
        QuestManager manager=new QuestManager(null);
        Quest a=quest("drag_a",manager), b=quest("drag_b",manager), c=quest("drag_c",manager), pending=quest("drag_pending",manager), hidden=quest("drag_hidden",manager);
        for (Quest quest : List.of(a,b,c)) {
            ReadObjective objective=new ReadObjective(json("{\"required_amount\":2}"));
            objective.setParent(quest); quest.objectives.add(objective);
        }
        hidden.setActive(false);
        for (Quest quest : List.of(a,b,c,pending,hidden)) manager.addQuest(quest);
        List<ResourceLocation> original=List.of(a.getId(),hidden.getId(),b.getId(),c.getId(),pending.getId());
        QuestTracking authoritative=new QuestTracking(); authoritative.replace(original,id->true);
        TrackingReorder drag=new TrackingReorder();
        check(drag.begin(original,a.getId(),105,100),"Icon press captures its vertical offset");
        check(drag.top(125)==120,"Dragged row follows the pointer without jumping to its center");
        check(drag.move(drag.display(manager,false),0,100,28,134),"A held drag crosses the next row before release");
        check(drag.display(manager,false).equals(List.of(b,a,c,pending)),"Other quests fill the previous position immediately");
        check(authoritative.ids().equals(original) && drag.ids().contains(hidden.getId()),"Preview preserves hidden quests and leaves authoritative order untouched");
        check(!drag.move(drag.display(manager,false),0,100,28,134),"A stationary pointer does not oscillate the order between frames");
        check(drag.move(drag.display(manager,false),0,100,28,190),"One motion can cross several rows");
        check(drag.display(manager,false).equals(List.of(b,c,a,pending)),"Incomplete quests stay before pending rewards during dragging");
        check(!drag.move(drag.display(manager,false),0,100,28,500),"Dragging past another state group cannot cross that boundary");
        check(drag.move(drag.display(manager,false),0,100,28,105),"The pointer can bring the row back upward during the same gesture");
        check(drag.display(manager,false).equals(List.of(a,b,c,pending)),"Upward movement is previewed immediately too");
        drag.end();
        check(!drag.active() && authoritative.ids().equals(original),"Cancelling a preview never changes saved order");
        check(!drag.begin(original,ResourceLocation.parse("questlog:absent"),0,0),"Unknown icons cannot start reordering");
        drag.begin(original,c.getId(),105,100);
        check(drag.move(drag.display(manager,false),2,100,28,60),"Scrolled gestures use absolute row indices");
        check(drag.display(manager,false).equals(List.of(a,c,b,pending)),"Scrolled upward movement reaches the adjacent row correctly");
        List<ResourceLocation> committed=drag.ids(); drag.end(); authoritative.replace(committed,id->true);
        CompoundTag saved=new CompoundTag(); authoritative.save(saved); QuestTracking restored=new QuestTracking(); restored.load(saved);
        check(restored.ids().equals(committed),"The final preview order is saved on commit and survives reconnection");
    }
    private static void verifyIdentityAndFeedback() {
        ResourceLocation id = QuestIdGenerator.next("questlog:Apprentissage","L’équipement à vapeur !", ignored -> false);
        check(id.toString().equals("questlog:apprentissage_l-equipement-a-vapeur_1"), "Automatic ID normalizes chapter, accents and punctuation");
        check(QuestIdGenerator.next("main","Même titre",other->other.getPath().endsWith("_1")).getPath().endsWith("_2"), "Existing identity advances the numerical suffix");
        check(QuestIdGenerator.next("main","",ignored->false).getPath().equals("main_sans-titre_1"), "Empty draft has a valid automatic identity");
        check(QuestIdGenerator.next("a".repeat(300),"é".repeat(300),ignored->false).toString().length()<160, "Long input remains bounded for the read-only ID display");
        var report = new QuestValidation.Report(List.of(new QuestValidation.Issue("title","questlog.validation.required",true)));
        check(!report.showAfterSave(false) && report.showAfterSave(true), "Incomplete draft saves silently; activation shows a report");
        var advisory = new QuestValidation.Report(List.of(new QuestValidation.Issue("item","questlog.validation.unchecked",false)));
        check(!advisory.showAfterSave(true), "Advisory references do not interrupt a valid activation");
    }
    private static void verifyFixtures() {
        java.nio.file.Path directory = java.nio.file.Path.of(System.getProperty("questlog.examples"));
        QuestManager manager = new QuestManager(null) { @Override public boolean isClient() { return true; } };
        DefinitionUtil.clearClientCaches();
        try {
            for (String suite : List.of("functional","malformed")) {
                try (var files = java.nio.file.Files.walk(directory.resolve(suite).resolve("questlog/chapters"))) {
                    for (var path : files.filter(p -> p.toString().endsWith(".json")).toList())
                        DefinitionUtil.putCachedChapter(ResourceLocation.parse("questlog:"+path.getFileName().toString().replace(".json","")),JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject());
                }
            }
            try (var files = java.nio.file.Files.walk(directory.resolve("functional/questlog/quests"))) {
                for (var path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    JsonObject def = JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject();
                    check(QuestValidation.inspect(def,new QuestReferences(null,null)).functional(), "Functional example validates: "+path.getFileName());
                    check(Quest.create(def,ResourceLocation.parse("questlog:test/"+path.getFileName().toString().replace(".json","")),manager)!=null, "Functional example constructs: "+path.getFileName());
                }
            }
            try (var files = java.nio.file.Files.walk(directory.resolve("malformed/questlog/quests"))) {
                for (var path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    if (path.getFileName().toString().equals("04_syntax.json")) {
                        boolean rejected = false;
                        try { JsonParser.parseString(java.nio.file.Files.readString(path)); } catch (JsonParseException expected) { rejected=true; }
                        check(rejected,"Deliberate syntax fixture is malformed");
                    } else {
                        JsonObject def = JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject();
                        check(!QuestValidation.inspect(def,new QuestReferences(null,null)).functional(), "Malformed example is reported: "+path.getFileName());
                    }
                }
            }
        } catch (java.io.IOException error) { throw new AssertionError("Cannot read example fixtures",error); }
        finally { DefinitionUtil.clearClientCaches(); }
    }
    private static void verifyPacketCodecs() {
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
        try {
            var tracking=new org.infernalstudios.questlog.network.packet.QuestTrackingPacket(List.of(ResourceLocation.parse("questlog:b"),ResourceLocation.parse("questlog:a")));
            org.infernalstudios.questlog.network.packet.QuestTrackingPacket.STREAM_CODEC.encode(buffer,tracking);
            check(tracking.equals(org.infernalstudios.questlog.network.packet.QuestTrackingPacket.STREAM_CODEC.decode(buffer)), "Tracking packet preserves order on the wire");
            buffer.clear();
            JsonArray huge = new JsonArray();
            for (int i=0; i<4000; i++) {
                JsonObject row = new JsonObject(); row.addProperty("id","questlog:a"); row.addProperty("path","objectives["+i+"].item");
                row.addProperty("message","questlog.validation.reference"); row.addProperty("error",true); huge.add(row);
            }
            String bounded = org.infernalstudios.questlog.network.packet.QuestAuthorPacket.encodeReport(huge);
            JsonArray parsed = JsonParser.parseString(bounded).getAsJsonArray();
            check(parsed.size()<huge.size() && parsed.get(parsed.size()-1).getAsJsonObject().get("message").getAsString().equals("questlog.validation.truncated"), "Large report remains valid JSON with an explicit truncation notice");
            var result = new org.infernalstudios.questlog.network.packet.QuestAuthorResultPacket(ResourceLocation.parse("questlog:a"),2,true,bounded);
            org.infernalstudios.questlog.network.packet.QuestAuthorResultPacket.STREAM_CODEC.encode(buffer,result);
            check(result.equals(org.infernalstudios.questlog.network.packet.QuestAuthorResultPacket.STREAM_CODEC.decode(buffer)), "Large author report roundtrip exceeds the old 32K string limit safely");
            buffer.clear();
            var draft = new org.infernalstudios.questlog.network.packet.QuestEditSavePacket(ResourceLocation.parse("questlog:preview_1"),"{}",true);
            org.infernalstudios.questlog.network.packet.QuestEditSavePacket.STREAM_CODEC.encode(buffer,draft);
            check(draft.equals(org.infernalstudios.questlog.network.packet.QuestEditSavePacket.STREAM_CODEC.decode(buffer)), "Server creation intent survives the save packet");
            buffer.clear();
            var author=new org.infernalstudios.questlog.network.packet.QuestAuthorPacket(ResourceLocation.parse("questlog:a"),0);
            org.infernalstudios.questlog.network.packet.QuestAuthorPacket.STREAM_CODEC.encode(buffer,author);
            check(author.equals(org.infernalstudios.questlog.network.packet.QuestAuthorPacket.STREAM_CODEC.decode(buffer)), "Author action roundtrip");
        } finally { buffer.release(); }
    }
    private static void verifyProgressRecovery() {
        QuestManager manager=new QuestManager(null) { @Override public boolean isClient() { return true; } @Override public void sync(ResourceLocation id) {} };
        JsonObject original=json("{\"objectives\":[{\"type\":\"read\",\"required_amount\":2},{\"type\":\"read\",\"required_amount\":3}]}");
        ResourceLocation id=ResourceLocation.parse("questlog:recovery");
        Quest source=Quest.create(original,id,manager);
        source.objectives.get(0).setUnits(1); source.objectives.get(1).setUnits(2);
        CompoundTag progress=source.serialize();
        JsonObject draft=json("{\"active\":false}");
        Quest suspended=Quest.create(draft,id,manager); suspended.deserialize(progress);
        check(suspended.serialize().equals(progress), "An incomplete draft preserves the entire saved progression snapshot");
        JsonObject reordered=original.deepCopy();
        JsonArray reversed=new JsonArray(); reversed.add(original.getAsJsonArray("objectives").get(1)); reversed.add(original.getAsJsonArray("objectives").get(0));
        reordered.add("objectives",reversed);
        Quest resumed=Quest.create(reordered,id,manager); resumed.deserialize(suspended.serialize());
        check(resumed.objectives.get(0).getUnits()==2 && resumed.objectives.get(1).getUnits()==1, "Reordering restores counters to matching objective definitions");
        JsonObject edited=original.deepCopy(); edited.getAsJsonArray("objectives").get(0).getAsJsonObject().addProperty("required_amount",4);
        Quest changed=Quest.create(edited,id,manager); changed.deserialize(progress);
        check(changed.objectives.get(0).getUnits()==0 && changed.objectives.get(1).getUnits()==2, "Changed objective starts fresh without losing the unchanged one");
        source.setActive(false);
        Reward blocked=new Reward(new JsonObject()) {}; blocked.setParent(source); blocked.applyReward(null);
        check(!blocked.hasRewarded(), "Suspension blocks base reward application");
    }
    private static Quest quest(String id, QuestManager manager) {
        return new Quest(new QuestDisplayData(json("{}")),new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),
                ResourceLocation.parse("questlog:"+id),manager,false,false) { @Override public void markForUpdate() {} };
    }
    private static JsonObject json(String value) { JsonObject object=JsonParser.parseString(value).getAsJsonObject(); if (!object.has("title")) object.addProperty("title","Test"); if (!object.has("description")) object.addProperty("description","Test description"); return object; }
    private static void check(boolean value,String message) { checks++; if (!value) throw new AssertionError(message); }
}
