package com.taz.foraging.safari;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.Taz;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Predicate;

/**
 * The Critter Safari's map: SkyHanni's island graph for it (SkyHanni-REPO, MIT, bundled as
 * assets/taz/foraging/safari/safari-graph.json with its licence). About 1,400 points along the walkable ways, each joined
 * to its neighbours, some of them named: "Ritual", "Gem Podiums", "Birdfeeder", "Hideonwall", "Floor drop",
 * "Icy Biome". Taz walks it like SkyHanni's navigation does: the shortest way along the joins to the nearest target.
 */
public final class SafariMap {
	public record Node(int index, Vec3 pos, String name, List<String> tags, int[] next, double[] cost) {
		public boolean is(String name) {
			return name.equals(this.name);
		}

		public boolean tagged(String tag) {
			return tags.contains(tag);
		}
	}

	/** Where the walk can start: the points this close to you (or else the nearest). */
	private static final double START_RANGE = 16;

	private static List<Node> nodes;

	private SafariMap() {
	}

	public static synchronized List<Node> nodes() {
		if (nodes == null) nodes = load();
		return nodes;
	}

	private static List<Node> load() {
		try (InputStream in = SafariMap.class.getResourceAsStream("/assets/taz/foraging/safari/safari-graph.json")) {
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			Map<String, Integer> index = new HashMap<>();
			for (String id : json.keySet()) index.put(id, index.size());
			List<Node> out = new ArrayList<>(index.size());
			for (Map.Entry<String, JsonElement> e : json.entrySet()) {
				JsonObject n = e.getValue().getAsJsonObject();
				String[] xyz = n.get("Position").getAsString().split(":");
				Vec3 pos = new Vec3(Double.parseDouble(xyz[0]) + 0.5, Double.parseDouble(xyz[1]), Double.parseDouble(xyz[2]) + 0.5);
				String name = n.has("Name") ? n.get("Name").getAsString().trim() : null;
				List<String> tags = new ArrayList<>();
				if (n.has("Tags")) n.getAsJsonArray("Tags").forEach(t -> tags.add(t.getAsString()));
				JsonObject neighbours = n.getAsJsonObject("Neighbours");
				List<Integer> links = new ArrayList<>();
				List<Double> costs = new ArrayList<>();
				for (Map.Entry<String, JsonElement> link : neighbours.entrySet()) {
					Integer to = index.get(link.getKey());
					if (to == null) continue;
					links.add(to);
					costs.add(link.getValue().getAsDouble());
				}
				out.add(new Node(index.get(e.getKey()), pos, name, List.copyOf(tags),
						links.stream().mapToInt(Integer::intValue).toArray(), costs.stream().mapToDouble(Double::doubleValue).toArray()));
			}
			return List.copyOf(out);
		} catch (Exception e) {
			Taz.LOGGER.error("Could not read the Critter Safari map", e);
			return List.of();
		}
	}

	public static List<Node> matching(Predicate<Node> which) {
		return nodes().stream().filter(which).toList();
	}

	public static Node nearest(Vec3 pos) {
		Node best = null;
		double bestDist = Double.MAX_VALUE;
		for (Node n : nodes()) {
			double d = n.pos().distanceToSqr(pos);
			if (d < bestDist) {
				best = n;
				bestDist = d;
			}
		}
		return best;
	}

	/** A walk found by {@link #path}: the points to follow, the target reached and how far it is. */
	public record Walk(List<Vec3> points, int target, double length) {
	}

	/**
	 * The shortest walk from {@code from} along the map to whichever target is nearest that way. Each target is a
	 * position; the walk goes to the map point nearest it, then straight to it. Null when none can be reached.
	 */
	public static Walk path(Vec3 from, List<Vec3> targets) {
		List<Node> all = nodes();
		if (all.isEmpty() || targets.isEmpty()) return null;
		int size = all.size();
		// Where each target joins the map, and the last stretch from there.
		int[] join = new int[targets.size()];
		Map<Integer, List<Integer>> targetsAt = new HashMap<>();
		for (int t = 0; t < targets.size(); t++) {
			join[t] = nearest(targets.get(t)).index();
			targetsAt.computeIfAbsent(join[t], k -> new ArrayList<>()).add(t);
		}
		double[] dist = new double[size];
		int[] prev = new int[size];
		Arrays.fill(dist, Double.MAX_VALUE);
		Arrays.fill(prev, -1);
		PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
		boolean started = false;
		for (Node n : all) {
			double d = n.pos().distanceTo(from);
			if (d <= START_RANGE) {
				dist[n.index()] = d;
				queue.add(new double[]{d, n.index()});
				started = true;
			}
		}
		if (!started) {
			Node n = nearest(from);
			dist[n.index()] = n.pos().distanceTo(from);
			queue.add(new double[]{dist[n.index()], n.index()});
		}
		// A target this close is walked to straight; the map can only beat that with a shorter way round.
		int bestTarget = -1;
		double bestTotal = Double.MAX_VALUE;
		for (int t = 0; t < targets.size(); t++) {
			double d = targets.get(t).distanceTo(from);
			if (d <= START_RANGE && d < bestTotal) {
				bestTotal = d;
				bestTarget = t;
			}
		}
		int direct = bestTarget;
		// Dijkstra, stopping at the first target whose whole way (walk plus last stretch) can't be beaten.
		while (!queue.isEmpty()) {
			double[] top = queue.poll();
			int i = (int) top[1];
			if (top[0] > dist[i]) continue;
			if (top[0] >= bestTotal) break;
			List<Integer> here = targetsAt.get(i);
			if (here != null) {
				for (int t : here) {
					double total = dist[i] + all.get(i).pos().distanceTo(targets.get(t));
					if (total < bestTotal) {
						bestTotal = total;
						bestTarget = t;
						direct = -1;
					}
				}
			}
			Node n = all.get(i);
			for (int k = 0; k < n.next().length; k++) {
				int j = n.next()[k];
				double d = dist[i] + n.cost()[k];
				if (d < dist[j]) {
					dist[j] = d;
					prev[j] = i;
					queue.add(new double[]{d, j});
				}
			}
		}
		if (bestTarget < 0) return null;
		if (bestTarget == direct) return new Walk(List.of(targets.get(direct)), direct, bestTotal);
		List<Vec3> points = new ArrayList<>();
		for (int i = join[bestTarget]; i >= 0; i = prev[i]) points.add(0, all.get(i).pos());
		points.add(targets.get(bestTarget));
		return new Walk(points, bestTarget, bestTotal);
	}
}
