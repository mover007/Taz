package com.taz.dungeons.skillissue;

import com.taz.dungeons.skillissue.SkillIssue.Cause;
import com.taz.dungeons.skillissue.SkillIssue.Phase;
import com.taz.dungeons.skillissue.SkillIssue.Pool;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill Issue's toxic tone: "How did you die in terms?" roasts for teammates, and excuses that blame the team for your
 * own deaths.
 */
final class ToxicLines {
	static final Pool ROASTS;
	static final Pool EXCUSES;

	private ToxicLines() {
	}

	static {
		Map<Phase, List<String>> phase = new EnumMap<>(Phase.class);
		phase.put(Phase.CLEAR, List.of(
				"How did you die in clear, {name}? The mobs are basically level 1.",
				"{name} died in CLEAR. Uninstall.",
				"{name} really died before blood. Go back to F1.",
				"Imagine dying in clear. {name} didn't have to imagine."));
		phase.put(Phase.BLOOD, List.of(
				"How do you die at blood, {name}? You just stand there.",
				"{name} died to blood mobs. The Watcher is embarrassed for you.",
				"{name} got farmed at blood. Free XP for the Watcher."));
		phase.put(Phase.TO_BOSS, List.of(
				"{name} died walking to the boss. WALKING.",
				"How did you die on the way to boss, {name}? There's nothing there."));
		phase.put(Phase.MAXOR, List.of(
				"How did you die to Maxor, {name}? He's the warm-up.",
				"{name} died to MAXOR. The tutorial boss. Unreal.",
				"{name} got stage fright on P1. Embarrassing."));
		phase.put(Phase.STORM, List.of(
				"How did you die to Storm, {name}? Stand on the pad.",
				"{name} died in Storm. The pads are RIGHT there.",
				"{name} got fried by Storm. Weather report: skill issue."));
		phase.put(Phase.TERMINALS, List.of(
				"How did you die in terms, {name}? It's clicking colours.",
				"{name} died doing terminals. Outplayed by a GUI. Again.",
				"{name} died in P3. The terminal was smarter than you.",
				"Imagine dying in terms. {name} imagined it, then did it."));
		phase.put(Phase.CORE, List.of(
				"How do you die at core, {name}? Just run.",
				"{name} died at core. So close, still so bad."));
		phase.put(Phase.NECRON, List.of(
				"How did you die to Necron, {name}? The lasers are the size of a house.",
				"{name} died to Necron. Dodging is free, you know."));
		phase.put(Phase.DRAGONS, List.of(
				"How did you die in P5, {name}? We were THIS close.",
				"{name} threw P5. Thanks for nothing.",
				"{name} died to the dragons. Their aim is better than yours."));
		phase.put(Phase.SADAN, List.of(
				"How did you get stepped on, {name}? They're GIANTS.",
				"{name} died to Sadan. Look up, genius."));
		phase.put(Phase.LIVID, List.of("How do you hit the wrong Livid, {name}? It's one colour."));
		phase.put(Phase.THORN, List.of("{name} lost to a DEER."));
		phase.put(Phase.PROFESSOR, List.of("{name} got schooled by The Professor. Should've studied."));
		phase.put(Phase.SCARF, List.of("How do you die to Scarf, {name}? Seriously."));
		phase.put(Phase.BONZO, List.of("{name} died to Bonzo. A clown beat you, {name}."));
		phase.put(Phase.BOSS, List.of("How did you die to {boss}, {name}?"));

		Map<Cause, List<String>> cause = new EnumMap<>(Cause.class);
		cause.put(Cause.DISCONNECTED, List.of(
				"{name} disconnected. Convenient timing.",
				"{name} rage quit. Sorry, \"disconnected\".",
				"{name}'s wifi saw the run and left."));
		cause.put(Cause.TRAP, List.of(
				"How do you walk into a trap, {name}? It's right there.",
				"{name} found the trap with their face."));
		cause.put(Cause.CRUSHED, List.of(
				"How did you get crushed, {name}? Move.",
				"{name} got flattened. Look up sometime."));
		cause.put(Cause.MOB, List.of("{name} died to \"a mob\". Not even a named one. Embarrassing."));
		cause.put(Cause.DIED, List.of(
				"How did you just die, {name}? Nothing even hit you.",
				"{name} died to absolutely nothing. Talent."));

		Map<String, List<String>> classes = new HashMap<>();
		classes.put("Archer", List.of(
				"How did you die, {name}? You're ARCHER. Stay back.",
				"{name} the Archer died. Did you forget the bow?"));
		classes.put("Berserk", List.of(
				"{name} the Berserk died. All damage, no brain.",
				"Berserk {name} face-tanked again. You're not the tank."));
		classes.put("Healer", List.of(
				"How does the HEALER die, {name}?",
				"Our healer {name} died. Heal YOURSELF next time.",
				"{name} the healer died. We're all dead now, thanks."));
		classes.put("Mage", List.of(
				"{name} the Mage died. Big brain, small health bar.",
				"How did you die, {name}? Mages stand in the BACK."));
		classes.put("Tank", List.of(
				"How does the TANK die, {name}? That's your whole job.",
				"Tank {name} died. You had ONE job."));

		Map<String, List<String>> killer = new HashMap<>();
		killer.put("Shadow Assassin", List.of("How did a Shadow Assassin get you, {name}? Turn around."));
		killer.put("Adventurer", List.of("{name} died to a {killer}. A lost one. Like you."));
		killer.put("Fels", List.of("{name} died to a Fels. Just don't look at it."));
		killer.put("Withermancer", List.of("{name} got destroyed by a Withermancer. Dodge the skulls."));
		killer.put("Mimic", List.of("{name} died to a Mimic. You lost to a CHEST, {name}."));
		killer.put("Crypt", List.of("How do you die to a {killer}, {name}? It's a crypt mob."));
		killer.put("Sniper", List.of("{name} got sniped. Ever heard of cover?"));
		killer.put("Super Archer", List.of("A Super Archer outshot {name}. Even the mobs aim better."));
		killer.put("Zombie", List.of("{name} died to a {killer}. A ZOMBIE. Uninstall."));
		killer.put("Skelet", List.of("{name} got clapped by a {killer}. Bones beat you."));
		killer.put("Wither Miner", List.of("{name} got mined by a Wither Miner. Embarrassing."));
		killer.put("Terracotta", List.of("{name} died to a Terracotta. A pot. You lost to a pot."));
		killer.put("Lurker", List.of("{name} got jumped by a Crypt Lurker. Watch your corners."));

		ROASTS = new Pool(phase, cause, classes, killer, List.of(
				"How did you die to a {killer}, {name}?",
				"{name} got clapped by a {killer}. Embarrassing.",
				"{name} lost to a {killer}. Go back to F1."), List.of(
				"{name} died. Skill issue.",
				"How did you die, {name}?",
				"{name} died. Uninstall.",
				"{name} is sitting out the rest of the run. Again.",
				"Carried and still died, {name}."), "are you even trying?");

		Map<Phase, List<String>> myPhase = new EnumMap<>(Phase.class);
		myPhase.put(Phase.CLEAR, List.of("Died in clear. Where was my healer?", "Died in clear because nobody was killing anything."));
		myPhase.put(Phase.BLOOD, List.of("Died at blood. Nobody was killing the mobs, apparently."));
		myPhase.put(Phase.TO_BOSS, List.of("Died before boss. Lag, and also you guys."));
		myPhase.put(Phase.MAXOR, List.of("Died to Maxor because nobody picked up the crystal in time."));
		myPhase.put(Phase.STORM, List.of("Storm got me because nobody stunned him. Not my fault."));
		myPhase.put(Phase.TERMINALS, List.of(
				"Died in terms because SOMEONE didn't do their terminal.",
				"Died in terms. You all left me alone, thanks guys."));
		myPhase.put(Phase.CORE, List.of("Died at core. You all ran off and left me."));
		myPhase.put(Phase.NECRON, List.of("Died to Necron. Our healer was asleep."));
		myPhase.put(Phase.DRAGONS, List.of("Died in P5 because nobody killed the dragon on time."));
		myPhase.put(Phase.SADAN, List.of("A giant stepped on me because nobody was killing it."));
		myPhase.put(Phase.LIVID, List.of("Died to Livid. Someone hit the wrong one, wasn't me."));
		myPhase.put(Phase.THORN, List.of("Died to Thorn. Where was the damage?"));
		myPhase.put(Phase.PROFESSOR, List.of("Died to The Professor. Team diff."));
		myPhase.put(Phase.SCARF, List.of("Died to Scarf. Nobody was killing his minions."));
		myPhase.put(Phase.BONZO, List.of("Died to Bonzo. Team diff."));
		myPhase.put(Phase.BOSS, List.of("Died to {boss}. Team diff."));

		Map<Cause, List<String>> myCause = new EnumMap<>(Cause.class);
		myCause.put(Cause.DISCONNECTED, List.of("My wifi died, not me. Doesn't count."));
		myCause.put(Cause.TRAP, List.of("Someone should've marked that trap. Thanks team."));
		myCause.put(Cause.CRUSHED, List.of("Got crushed. Nobody called it out."));
		myCause.put(Cause.MOB, List.of("Died to a mob nobody else bothered to kill."));
		myCause.put(Cause.DIED, List.of("Died to nothing. Probably a teammate's fault."));

		Map<String, List<String>> myClass = new HashMap<>();
		myClass.put("Archer", List.of("Died because the tank wasn't tanking."));
		myClass.put("Berserk", List.of("Died because I was the only one actually hitting things."));
		myClass.put("Healer", List.of("Died healing you lot. Ungrateful."));
		myClass.put("Mage", List.of("Died carrying all of you with damage."));
		myClass.put("Tank", List.of("Died because you all hid behind me."));

		EXCUSES = new Pool(myPhase, myCause, myClass, Map.of(), List.of(
				"That {killer} should've been someone else's job."), List.of(
				"Team diff.",
				"Not my fault, check the healer.",
				"Lag. And you guys.",
				"I was carrying, someone had to die."), "still not my fault");
	}
}
