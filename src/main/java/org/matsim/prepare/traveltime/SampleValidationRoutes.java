package org.matsim.prepare.traveltime;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.FastDijkstraFactory;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.scenario.ProjectionUtils;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.transformations.GeotoolsTransformation;
import org.matsim.prepare.RunOpenBerlinCalibration;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

@CommandLine.Command(
	name = "sample-validation-routes",
	description = "Sample routes for travel time validation."
)
@CommandSpec(
	requireNetwork = true,
	produces = {"routes-validation.csv"}
)
public class SampleValidationRoutes implements MATSimAppCommand {

	private static final Logger log = LogManager.getLogger(SampleValidationRoutes.class);

	@CommandLine.Mixin
	private InputOptions input = InputOptions.ofCommand(SampleValidationRoutes.class);
	@CommandLine.Mixin
	private OutputOptions output = OutputOptions.ofCommand(SampleValidationRoutes.class);

	@CommandLine.Mixin
	private ShpOptions shp;

	@CommandLine.Option(names = "--api", description = "API service that should be used", defaultValue = "google")
	private Api api;

	@CommandLine.Option(names = "--api-key", description = "API key.")
	private String apiKey;

	@CommandLine.Option(names = "--num-routes", description = "Number of routes (per time bin)", defaultValue = "1000")
	private int numRoutes;

	@CommandLine.Option(names = "--hours", description = "Hours to validate", defaultValue = "3,7,8,9,12,13,16,17,18,21", split = ",")
	private List<Integer> hours;

	@CommandLine.Option(names = "--dist-range", description = "Range for the sampled distances.", split = ",", defaultValue = "3000,10000")
	private List<Double> distRange;

	@CommandLine.Option(names = "--mode", description = "Mode to validate", defaultValue = TransportMode.car)
	private String mode;

	/**
	 * Random coord in the same direction as a link.
	 */
	static Coord rndCoord(SplittableRandom rnd, double dist, Link link) {

		Coord v = link.getFromNode().getCoord();
		Coord u = link.getToNode().getCoord();

		var angle = Math.atan2(u.getY() - v.getY(), u.getX() - v.getX());

		var sample = angle + rnd.nextDouble(-0.2, 0.2) * Math.PI * 2;

		var x = Math.cos(sample) * dist;
		var y = Math.sin(sample) * dist;

		return new Coord(RunOpenBerlinCalibration.roundNumber(v.getX() + x), RunOpenBerlinCalibration.roundNumber(v.getY() + y));
	}

	public static void main(String[] args) {
		new SampleValidationRoutes().execute(args);
	}

	@Override
	public Integer call() throws Exception {

		Network network = input.getNetwork();

		SplittableRandom rnd = new SplittableRandom(0);

		FreeSpeedTravelTime tt = new FreeSpeedTravelTime();
		OnlyTimeDependentTravelDisutility util = new OnlyTimeDependentTravelDisutility(tt);
		LeastCostPathCalculator router = new FastDijkstraFactory(false).createPathCalculator(network, util, tt);

		List<Route> routes = sampleRoutes(network, router, rnd);

		log.info("Sampled {} routes in range {}", routes.size(), distRange);

		try (CSVPrinter csv = new CSVPrinter(Files.newBufferedWriter(output.getPath()), CSVFormat.DEFAULT)) {
			csv.printRecord("from_node", "to_node", "beeline_dist", "dist", "travel_time", "geometry");
			for (Route route : routes) {
				csv.printRecord(route.fromNode, route.toNode,
					CoordUtils.calcEuclideanDistance(network.getNodes().get(route.fromNode).getCoord(), network.getNodes().get(route.toNode).getCoord()),
					route.dist, route.travelTime,
					String.format(Locale.US, "MULTIPOINT(%.5f %.5f, %.5f %.5f)", route.from.getX(), route.from.getY(), route.to.getX(), route.to.getY()));
			}
		}

		Path out = Path.of(output.getPath().toString().replace(".csv", "-api-" + api + ".csv"));

		if (api == Api.none) {
			log.info("Not querying API.");
			return 0;
		}

		// Collect existing entries to support resuming
		Set<Entry> entries = new HashSet<>();
		OpenOption open;
		if (Files.exists(out)) {
			open = StandardOpenOption.APPEND;
			try (CSVParser csv = new CSVParser(Files.newBufferedReader(out), CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())) {
				for (CSVRecord r : csv) {
					entries.add(new Entry(
						Id.createNodeId(r.get("from_node")),
						Id.createNodeId(r.get("to_node")),
						r.get("api"),
						Integer.parseInt(r.get("hour")))
					);
				}
			}

		} else
			open = StandardOpenOption.CREATE_NEW;


		try (RouteValidator val = switch (api) {
			case google -> new GoogleRouteValidator(apiKey);
			case woosmap -> new WoosMapRouteValidator(apiKey);
			case mapbox -> new MapboxRouteValidator(apiKey);
			case here -> new HereRouteValidator(apiKey);
			case tomtom -> new TomTomRouteValidator(apiKey);
			case none -> null;
		}) {

			try (CSVPrinter csv = new CSVPrinter(Files.newBufferedWriter(out, open), CSVFormat.DEFAULT)) {

				if (open == StandardOpenOption.CREATE_NEW) {
					csv.printRecord("from_node", "to_node", "api", "hour", "dist", "travel_time");
					csv.flush();
				}

				int i = 0;
				for (Route route : routes) {
					for (int h : hours) {

						// Skip entries already present
						Entry e = new Entry(route.fromNode, route.toNode, val.name(), h);
						if (entries.contains(e))
							continue;

						try {
							RouteValidator.Result res = val.calculate(route.from, route.to, h);
							csv.printRecord(route.fromNode, route.toNode, val.name(), h, res.dist(), res.travelTime());
						} catch (Exception ex) {
							log.warn("Could not retrieve result for route {} (retrying)", route, ex);

							try {
								Thread.sleep(30_000);
								RouteValidator.Result res = val.calculate(route.from, route.to, h);
								csv.printRecord(route.fromNode, route.toNode, val.name(), h, res.dist(), res.travelTime());
							} catch (Exception ex2) {
								log.error("Failed to retrieve result for {}", route, ex);
							}
						}
					}

					csv.flush();

					if (i++ % 50 == 0) {
						log.info("Processed {} routes", i - 1);
					}
				}
			}
		}

		return 0;
	}

	/**
	 * Samples routes from the network.
	 */
	private List<Route> sampleRoutes(Network network, LeastCostPathCalculator router, SplittableRandom rnd) {

		List<Route> result = new ArrayList<>();
		List<? extends Link> links = new ArrayList<>(network.getLinks().values());
		String crs = ProjectionUtils.getCRS(network);

		GeotoolsTransformation ct = new GeotoolsTransformation(crs, "EPSG:4326");

		ShpOptions.Index index = shp.isDefined() ? shp.createIndex(crs, "_") : null;


		for (int i = 0; i < numRoutes; i++) {
			Link link = links.remove(rnd.nextInt(0, links.size()));

			if (index != null && !index.contains(link.getCoord())) {
				i--;
				continue;
			}

			if (!link.getAllowedModes().contains(mode)) {
				i--;
				continue;
			}

			Coord dest = rndCoord(rnd, rnd.nextDouble(distRange.get(0), distRange.get(1)), link);
			Link to = NetworkUtils.getNearestLink(network, dest);

			if (!to.getAllowedModes().contains(mode)) {
				i--;
				continue;
			}

			LeastCostPathCalculator.Path path = router.calcLeastCostPath(link.getFromNode(), to.getToNode(), 0, null, null);

			if (path.nodes.size() < 2) {
				i--;
				continue;
			}

			result.add(new Route(
				link.getFromNode().getId(),
				to.getToNode().getId(),
				ct.transform(link.getFromNode().getCoord()),
				ct.transform(to.getToNode().getCoord()),
				path.travelTime,
				path.links.stream().mapToDouble(Link::getLength).sum()
			));
		}
		return result;
	}

	public enum Api {
		none,
		google,
		woosmap,
		mapbox,
		here,
		tomtom
	}

	private record Route(Id<Node> fromNode, Id<Node> toNode, Coord from, Coord to, double travelTime, double dist) {
	}

	private record Entry(Id<Node> fromNode, Id<Node> toNode, String api, int hour) {
	}

}