package org.matsim.analysis.busTravelDistances;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.application.options.ShpOptions;
import org.matsim.application.options.ShpOptions.Index;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.vehicles.Vehicle;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * This class uses berlin-v5.5.3-1pct.output_events.xml.gz (or 10pct scenario)
 * to count the number of kms travelled by buses inside Berlin(-Brandenburg).
 *
 * @author rgraebe
 */
public class BusKmCounter implements VehicleLeavesTrafficEventHandler, LinkLeaveEventHandler {

    static Scenario scenario;

    String EVENTS_FILE_1pct = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-1pct/output-berlin-v5.5-1pct/berlin-v5.5.3-1pct.output_events.xml.gz";
//    String EVENTS_FILE_10pct = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-10pct/output-berlinv5.5/berlin-v5.5.3-10pct.output_events.xml.gz";
    static String CONFIG_FILE_1pct = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-1pct/output-berlin-v5.5-1pct/berlin-v5.5.3-1pct.output_config.xml";
//    static String CONFIG_FILE_10pct = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-10pct/output-berlinv5.5/berlin-v5.5.3-10pct.output_config.xml";
    static String VEHICLES_FILE_1pct = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-1pct/output-berlin-v5.5-1pct/berlin-v5.5.3-1pct.output_allVehicles.xml.gz";
//    static String VEHICLES_FILE_10pct = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-10pct/output-berlinv5.5/berlin-v5.5.3-10pct.output_allVehicles.xml.gz";
//    static String PLANS_FILE_10pct = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-10pct/output-berlinv5.5/berlin-v5.5.3-10pct.output_plans.xml.gz";
    static String PLANS_FILE_1pct = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-1pct/output-berlin-v5.5-1pct/berlin-v5.5.3-1pct.output_plans.xml.gz";
    static String NETWORK_FILE = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-10pct/output-berlinv5.5/berlin-v5.5.3-10pct.output_network.xml.gz";


    static String SCENARIO_PCT = "1pct";
    Double BUS_KMS_BRANDENBURG = 0.;
    Double BUS_KMS_BERLIN = 0.;
    Double BUS_KMS_Total = 0.;
	ShpOptions shpZones = new ShpOptions(Path.of("src/main/java/org/matsim/analysis/busTravelDistances/berlinBezirke/bezirksgrenzen.shp"), TransformationFactory.WGS84, StandardCharsets.UTF_8);
	Index indexZones = shpZones.createIndex("EPSG:31468", "Gemeinde_n");

//    private static final CoordinateTransformation transformation_fromEPSG31468 = TransformationFactory.getCoordinateTransformation("EPSG:31468", "EPSG:3857");
    
//    private static final Collection<SimpleFeature> features = ShapeFileReader.getAllFeatures("src/main/java/org/matsim/analysis/busTravelDistances/berlinBezirke/bezirksgrenzen.shp");
    // you can see the CRS of your shapefile in the .prj file that should be in the root where your .shp file is stored
//    private static Geometry berlinGeometry = null;
//    static GeometryFactory geofac = new GeometryFactory();

    public static void main( String[] args ) throws FactoryException, TransformException {

//    	CreateBerlinGeometry(); // to read the shape file and covert the CRS's to be compatible with MATSim's links
//        CountBerlinTrips(); // for testing only

        Config config = ConfigUtils.loadConfig( CONFIG_FILE_1pct );
        config.vehicles().setVehiclesFile( VEHICLES_FILE_1pct );
        scenario = ScenarioUtils.loadScenario( config ) ;

        var handler = new BusKmCounter();
        var manager = EventsUtils.createEventsManager();
        manager.addHandler(handler);

        manager.initProcessing();
        MatsimEventsReader reader = new MatsimEventsReader(manager);
        reader.readFile( handler.EVENTS_FILE_1pct );
        manager.finishProcessing();

        System.out.println("Bus kms Brandenburg (" + SCENARIO_PCT + "): " + handler.BUS_KMS_BRANDENBURG );
        System.out.println("Bus kms Berlin (" + SCENARIO_PCT + "): " + handler.BUS_KMS_BERLIN );
        System.out.println("Bus kms Total (" + SCENARIO_PCT + "): " + handler.BUS_KMS_Total );

    }


    @Override
	public void handleEvent(LinkLeaveEvent linkLeaveEvent) {

		Vehicle vehicle = scenario.getVehicles().getVehicles().get(linkLeaveEvent.getVehicleId());
		Link link = scenario.getNetwork().getLinks().get(linkLeaveEvent.getLinkId());
		if (vehicle.getType().getId().toString().equals("Bus_veh_type")) {
//                System.out.println("found a bus");
			BUS_KMS_Total = BUS_KMS_Total + link.getLength() / 1000.;
			if (indexZones.contains(link.getCoord())) {
				BUS_KMS_BERLIN = BUS_KMS_BERLIN + link.getLength() / 1000.;
			} else
				BUS_KMS_BRANDENBURG = BUS_KMS_BRANDENBURG + link.getLength() / 1000.;
		}
	}

    @Override
    public void handleEvent( VehicleLeavesTrafficEvent vehicleLeavesTrafficEvent ) {

        Vehicle vehicle = scenario.getVehicles().getVehicles().get( vehicleLeavesTrafficEvent.getVehicleId() );
        Link link = scenario.getNetwork().getLinks().get( vehicleLeavesTrafficEvent.getLinkId() );
        if ( vehicle.getType().getId().toString().equals( "Bus_veh_type" ) ){
//                System.out.println("found a bus leaving traffic");
			BUS_KMS_Total = BUS_KMS_Total + link.getLength() / 1000.;
			if (indexZones.contains(link.getCoord())) {
				BUS_KMS_BERLIN = BUS_KMS_BERLIN + link.getLength() / 1000.;
			} else
				BUS_KMS_BRANDENBURG = BUS_KMS_BRANDENBURG + link.getLength() / 1000.;
            }
//        } catch ( Exception ignored ) {
//             not a transit vehicle OR vehicle = null :(
//        }
    }

//    private static void CountBerlinTrips() {
//
//        var population = PopulationUtils.readPopulation(PLANS_FILE_1pct);
//        var network = NetworkUtils.readNetwork(NETWORK_FILE);
////        var network = scenario.getNetwork();
//
//        int counter = 0;
//
//        for (var person : population.getPersons().values()) {
//
//            var plan = person.getSelectedPlan();
//            var activities = TripStructureUtils.getActivities(plan, TripStructureUtils.StageActivityHandling.ExcludeStageActivities);
//
//            for (var i = 0 ; i < activities.size() - 1; i++) {
//
//                var fromCoord = getCoord(activities.get(i), network);
//                var toCoord = getCoord(activities.get(i + 1), network);
//
//                if (isInGeometry(fromCoord, berlinGeometry) && isInGeometry(toCoord, berlinGeometry)) {
//                    counter++;
//                }
//            }
//        }
////        System.out.println("========== " + counter + " trips within Berlin ==========");
//
//    }

//    private static void CreateBerlinGeometry() throws FactoryException, TransformException {
//        // one big geometry that contains all Berlin's districts
//        berlinGeometry = features.stream()
//                .map(feature -> (Geometry) feature.getDefaultGeometry())
//                .findAny().orElseThrow();
//
//        // to match the shapefile's CRS with Berlin's CRS
//        CoordinateReferenceSystem sourceCRS = MGC.getCRS(TransformationFactory.WGS84);
//        CoordinateReferenceSystem targetCRS = MGC.getCRS("EPSG:3857");
//        MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS);
//        berlinGeometry = JTS.transform(berlinGeometry, transform);
//    }

//    private static Coord getCoord( Activity activity, Network network) {
//        if (activity.getCoord() != null) {
//            return activity.getCoord();
//        }
//        return network.getLinks().get(activity.getLinkId()).getCoord();
//    }

//    private static boolean isInGeometry(Coord coord, Geometry geometry) {
//        var transformed = transformation_fromEPSG31468.transform(coord);
//        return geometry.covers( MGC.coord2Point(transformed));
////        return geometry.covers( MGC.coord2Point(coord));
//    }

}
