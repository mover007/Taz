package com.taz.xpassist.xp;

import java.util.ArrayList;
import java.util.List;

/** Shows runs of grouped tasks as one line: "Buy Trapped I → V (Forest)" instead of five rows. */
public final class TaskGroups {
	public record Line(List<XpTask> tasks, String label, double cost, double xp, boolean owned, boolean partial,
					   java.util.Map<String, Double> materials) {
		public XpTask first() {
			return tasks.getFirst();
		}

		public String fullLabel() {
			return first().category.label + " - " + label;
		}
	}

	private TaskGroups() {
	}

	/** Merges consecutive tasks that share a group key; everything else stays one task per line. */
	public static List<Line> merge(List<XpTask> tasks) {
		List<Line> lines = new ArrayList<>();
		List<XpTask> run = new ArrayList<>();
		for (XpTask task : tasks) {
			if (!run.isEmpty() && (task.groupKey == null || !task.groupKey.equals(run.getFirst().groupKey))) {
				lines.add(line(run));
				run = new ArrayList<>();
			}
			run.add(task);
		}
		if (!run.isEmpty()) {
			lines.add(line(run));
		}
		return lines;
	}

	public static Line line(List<XpTask> run) {
		XpTask first = run.getFirst();
		double cost = 0;
		double xp = 0;
		boolean owned = true;
		boolean partial = false;
		java.util.Map<String, Double> materials = new java.util.LinkedHashMap<>();
		for (XpTask task : run) {
			task.materials.forEach((name, amount) -> materials.merge(name, amount, Double::sum));
			cost += task.cost;
			xp += task.xp;
			owned &= task.owned;
			partial |= task.partialPrice;
		}
		String label = first.label;
		if (run.size() > 1 && first.groupLabel != null) {
			label = first.groupLabel.replace("{}", first.groupStep + " → " + run.getLast().groupStep);
		}
		return new Line(List.copyOf(run), label, cost, xp, owned, partial, materials);
	}
}
