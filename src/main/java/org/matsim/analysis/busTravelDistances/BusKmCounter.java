package org.matsim.analysis.busTravelDistances;

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
 * This class uses berlin-v5.5.3-1pct.output_events.xml.gz
 * to count the number of kms travelled by buses inside Berlin(-Brandenburg).
 *
 * @author rgraebe
 */
public class BusKmCounter implements VehicleLeavesTrafficEventHandler, LinkLeaveEventHandler {
    private static final String EVENTS_FILE_1pct = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-1pct/output-berlin-v5.5-1pct/berlin-v5.5.3-1pct.output_events.xml.gz";
    private static final String CONFIG_FILE_1pct = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-1pct/output-berlin-v5.5-1pct/berlin-v5.5.3-1pct.output_config.xml";
    private static final String VEHICLES_FILE_1pct = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-1pct/output-berlin-v5.5-1pct/berlin-v5.5.3-1pct.output_allVehicles.xml.gz";
    private static final String SCENARIO_PCT = "1pct";
    private Double busKmsBrandenburg = 0.;
    private Double busKmsBerlin = 0.;
    private Double busKmsTotal = 0.;
	private static final ShpOptions shpZones = new ShpOptions(Path.of("src/main/java/org/matsim/analysis/busTravelDistances/berlinBezirke/bezirksgrenzen.shp"), TransformationFactory.WGS84, StandardCharsets.UTF_8);
    // you can see the CRS of your shapefile in the .prj file that should be in the root where your .shp file is stored
    private static final Index indexZones = shpZones.createIndex("EPSG:31468", "Gemeinde_n");
    private static final Config config = ConfigUtils.loadConfig( CONFIG_FILE_1pct );
    private static final Scenario scenario = ScenarioUtils.loadScenario( config ) ;
    private BusKmCounter() { config.vehicles().setVehiclesFile( VEHICLES_FILE_1pct ); }

    public static void main( String[] args ) throws FactoryException, TransformException {

        var handler = new BusKmCounter();
        var manager = EventsUtils.createEventsManager();
        manager.addHandler(handler);

        manager.initProcessing();
        MatsimEventsReader reader = new MatsimEventsReader(manager);
        reader.readFile( EVENTS_FILE_1pct );
        manager.finishProcessing();

        System.out.println("Bus kms Brandenburg (" + SCENARIO_PCT + "): " + handler.busKmsBrandenburg );
        System.out.println("Bus kms Berlin (" + SCENARIO_PCT + "): " + handler.busKmsBerlin );
        System.out.println("Bus kms Total (" + SCENARIO_PCT + "): " + handler.busKmsTotal );
    }

    @Override
	public void handleEvent(LinkLeaveEvent linkLeaveEvent) {

		Vehicle vehicle = scenario.getVehicles().getVehicles().get(linkLeaveEvent.getVehicleId());
		Link link = scenario.getNetwork().getLinks().get(linkLeaveEvent.getLinkId());
		if (vehicle.getType().getId().toString().equals("Bus_veh_type")) {
//                System.out.println("found a bus");
			busKmsTotal = busKmsTotal + link.getLength() / 1000.;
			if (indexZones.contains(link.getCoord())) {
				busKmsBerlin = busKmsBerlin + link.getLength() / 1000.;
			} else
				busKmsBrandenburg = busKmsBrandenburg + link.getLength() / 1000.;
		}
	}

    @Override
    public void handleEvent( VehicleLeavesTrafficEvent vehicleLeavesTrafficEvent ) {

        Vehicle vehicle = scenario.getVehicles().getVehicles().get( vehicleLeavesTrafficEvent.getVehicleId() );
        Link link = scenario.getNetwork().getLinks().get( vehicleLeavesTrafficEvent.getLinkId() );
        if ( vehicle.getType().getId().toString().equals( "Bus_veh_type" ) ){
//                System.out.println("found a bus leaving traffic");
			busKmsTotal = busKmsTotal + link.getLength() / 1000.;
			if (indexZones.contains(link.getCoord())) {
				busKmsBerlin = busKmsBerlin + link.getLength() / 1000.;
			} else
				busKmsBrandenburg = busKmsBrandenburg + link.getLength() / 1000.;
            }
//        } catch ( Exception ignored ) {
//             not a transit vehicle OR vehicle = null :(
//        }
    }

}
