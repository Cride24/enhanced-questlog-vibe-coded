package org.infernalstudios.questlog.core;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Headless checks for the server's group transition rules and persisted roster. */
public final class AdventureGroupsVerification {
    private static void check(boolean trueCondition, String message) {
        if (!trueCondition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Path directory = Files.createTempDirectory("questlog-groups-check-");
        Path file = directory.resolve("groups.dat");
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID(), chloe = UUID.randomUUID();
        AdventureGroups registry = new AdventureGroups(file);
        AdventureGroups.Group first = new AdventureGroups.Group(UUID.randomUUID(), "Explorers", alice);
        first.members.put(alice, "Alice");
        registry.register(first);
        AdventureGroups.Group second = new AdventureGroups.Group(UUID.randomUUID(), "Builders", chloe);
        second.members.put(chloe, "Chloe");
        registry.register(second);

        check(!AdventureGroups.canApply(first, second, alice), "A member cannot apply to another group");
        check(AdventureGroups.canApply(null, first, bob), "An ungrouped player can apply to an open group");
        first.applicants.put(bob, "Bob");
        check(!AdventureGroups.canApply(null, first, bob), "Duplicate application is rejected");
        check(!AdventureGroups.canAcceptApplicant(first, chloe, bob, null), "A visitor cannot accept an applicant");
        check(AdventureGroups.canAcceptApplicant(first, alice, bob, null), "The leader can accept an applicant");
        check(!AdventureGroups.canAcceptApplicant(first, alice, bob, second), "A grouped applicant cannot be accepted");
        check(!AdventureGroups.canViewApplicants(null, first) && AdventureGroups.canViewApplicants(first, first), "Applicants are hidden from visitors");

        first.open = false;
        check(!AdventureGroups.canApply(null, first, chloe), "Closed recruitment blocks new applications");
        check(first.applicants.containsKey(bob), "Closing recruitment keeps applications");
        check(AdventureGroups.canInvite(first, alice, bob, null, true), "Leader can invite while recruitment is closed");
        check(!AdventureGroups.canInvite(first, chloe, bob, null, true), "Visitor cannot invite");
        check(!AdventureGroups.canInvite(first, alice, bob, null, false), "Offline invite target is rejected");
        second.invitations.add(bob);
        registry.join(first, bob, "Bob");
        check(first.members.containsKey(bob) && !first.applicants.containsKey(bob) && !second.invitations.contains(bob), "Joining clears every other offer");
        check(!AdventureGroups.canLeave(first, alice), "Leader cannot leave with another member");
        check(!AdventureGroups.canManageMember(first, bob, alice) && AdventureGroups.canManageMember(first, alice, bob), "Only leader can manage another member");

        first.leader = bob;
        check(AdventureGroups.canLeave(first, alice), "Former leader can leave after transfer");
        registry.leave(first, alice);
        check(registry.group(first.id).members.containsKey(bob), "The remaining member stays");
        check(AdventureGroups.canLeave(first, bob), "The last member can leave");
        registry.leave(first, bob);
        check(registry.group(first.id) == null, "The last departure dissolves the group");

        second.invitations.add(alice);
        second.applicants.put(bob, "Bob");
        second.members.put(bob, "Bob"); // offline member remains in the saved roster
        check(registry.save(), "Group data saves");
        AdventureGroups loaded = new AdventureGroups(file);
        check(loaded.group(second.id) != null && loaded.group(second.id).members.containsKey(bob), "Offline member survives restart");
        check(loaded.group(second.id).invitations.contains(alice) && loaded.group(second.id).applicants.containsKey(bob), "Offers survive restart");
        loaded.group(second.id).open = false;
        check(loaded.save(), "A second save creates a recoverable backup");
        Files.writeString(file, "damaged");
        AdventureGroups recovered = new AdventureGroups(file);
        check(recovered.group(second.id) != null && recovered.group(second.id).members.containsKey(bob), "A damaged primary reads its backup");
        Path unrecoverable = directory.resolve("unrecoverable.dat");
        Files.writeString(unrecoverable, "damaged");
        check(!new AdventureGroups(unrecoverable).save(), "An unreadable save cannot be overwritten");
        Files.delete(unrecoverable);
        Files.deleteIfExists(file);
        Files.deleteIfExists(directory.resolve("groups.dat_old"));
        Files.delete(directory);
        System.out.println("Adventure groups: headless checks passed; no in-game test was run.");
    }
}
