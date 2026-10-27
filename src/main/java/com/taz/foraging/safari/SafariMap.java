package com.taz.foraging.safari;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.Taz;
import com.taz.foraging.safari.Critters.Biome;
import net.minecraft.world.level.Level;
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
	private static Biome[] zones;

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

	/** The biome a map point is in, or null for the paths between them (spawn). */
	public static Biome zoneOf(Node n) {
		return zones()[n.index()];
	}

	/** The biome a position is in, by the map point nearest it. */
	public static Biome zoneAt(Vec3 pos) {
		Node n = nearest(pos);
		return n == null ? null : zoneOf(n);
	}

	private static synchronized Biome[] zones() {
		if (zones == null) zones = assignZones(nodes());
		return zones;
	}

	/**
	 * Which biome each point is in, as SkyHanni works it out: the area marker ("Forest Biome", "no_area") nearest it
	 * along the paths. The biomes fold round each other, so the nearest marker as the crow flies is wrong in places.
	 */
	private static Biome[] assignZones(List<Node> all) {
		Biome[] out = new Biome[all.size()];
		double[] dist = new double[all.size()];
		Arrays.fill(dist, Double.MAX_VALUE);
		PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
		for (Node n : all) {
			if (!n.tagged("area")) continue;
			dist[n.index()] = 0;
			out[n.index()] = n.name() != null && n.name().endsWith(" Biome") ? Biome.named(n.name().substring(0, n.name().length() - 6)) : null;
			queue.add(new double[]{0, n.index()});
		}
		while (!queue.isEmpty()) {
			double[] top = queue.poll();
			int i = (int) top[1];
			if (top[0] > dist[i]) continue;
			Node n = all.get(i);
			for (int k = 0; k < n.next().length; k++) {
				int j = n.next()[k];
				double d = dist[i] + n.cost()[k];
				if (d < dist[j]) {
					dist[j] = d;
					out[j] = out[i];
					queue.add(new double[]{d, j});
				}
			}
		}
		return out;
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

	/** Targets this near get a path over the blocks all the way ({@link BlockPath}). */
	private static final double BLOCK_RANGE = 32;

	/**
	 * The way to the nearest target, walked all the way: over the blocks for targets close by; along the map's
	 * walkways for the rest, with the last stretch from the walkway to the target over the blocks too. A straight line
	 * only for what nobody can walk to (a critter in the air), or blocks that aren't loaded.
	 */
	public static Walk path(Level level, Vec3 from, List<Vec3> targets) {
		List<Integer> near = new ArrayList<>();
		for (int t = 0; t < targets.size(); t++) {
			if (targets.get(t).distanceTo(from) <= BLOCK_RANGE) near.add(t);
		}
		if (level != null && !near.isEmpty()) {
			BlockPath.Result r = BlockPath.find(level, from, near.stream().map(targets::get).toList(), BLOCK_RANGE + 16);
			if (r != null) return new Walk(r.points(), near.get(r.target()), r.length());
		}
		Walk walk = path(from, targets);
		if (walk == null || level == null || walk.points().size() < 2) return walk;
		List<Vec3> points = walk.points();
		Vec3 join = points.get(points.size() - 2), target = points.get(points.size() - 1);
		if (join.distanceTo(target) <= BlockPath.REACH) return walk;
		BlockPath.Result end = BlockPath.find(level, join, List.of(target), BLOCK_RANGE + 16);
		if (end == null) return walk;
		List<Vec3> joined = new ArrayList<>(points.subList(0, points.size() - 1));
		joined.addAll(end.points().subList(1, end.points().size()));
		return new Walk(joined, walk.target(), walk.length() - join.distanceTo(target) + end.length());
	}

	/**
	 * The shortest walk from {@code from} along the map to whichever target is nearest that way, by the map alone. Each
	 * target is a position; the walk goes to the map point nearest it, then straight to it. Null when none can be reached.
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
