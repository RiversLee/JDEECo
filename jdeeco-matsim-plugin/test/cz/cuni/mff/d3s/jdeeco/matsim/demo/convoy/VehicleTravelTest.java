package cz.cuni.mff.d3s.jdeeco.matsim.demo.convoy;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.StandardOutputStreamLog;
import org.matsim.core.utils.geometry.CoordImpl;

import cz.cuni.mff.d3s.deeco.annotations.processor.AnnotationProcessorException;
import cz.cuni.mff.d3s.deeco.runners.DEECoSimulation;
import cz.cuni.mff.d3s.deeco.runtime.DEECoException;
import cz.cuni.mff.d3s.deeco.runtime.DEECoNode;
import cz.cuni.mff.d3s.jdeeco.matsim.plugin.MATSimSimulation;
import cz.cuni.mff.d3s.jdeeco.matsim.plugin.MATSimVehicle;
import cz.cuni.mff.d3s.jdeeco.network.Network;
import cz.cuni.mff.d3s.jdeeco.network.device.SimpleBroadcastDevice;
import cz.cuni.mff.d3s.jdeeco.network.l2.strategy.KnowledgeInsertingStrategy;
import cz.cuni.mff.d3s.jdeeco.position.PositionPlugin;
import cz.cuni.mff.d3s.jdeeco.publishing.DefaultKnowledgePublisher;

/**
 * Example of vehicles traveling across the map
 * 
 * @author Vladimir Matena <matena@d3s.mff.cuni.cz>
 *
 */
public class VehicleTravelTest {
	@Rule
	public final StandardOutputStreamLog log = new StandardOutputStreamLog();

	public static void main(String[] args) throws AnnotationProcessorException, InterruptedException, DEECoException,
			InstantiationException, IllegalAccessException, IOException {
		new VehicleTravelTest().testTravel();
	}

	@Test
	public void testTravel() throws AnnotationProcessorException, InterruptedException, DEECoException,
			InstantiationException, IllegalAccessException, IOException {
		MATSimSimulation matSim = new MATSimSimulation("input/config.xml");

		// Create main application container
		DEECoSimulation realm = new DEECoSimulation(matSim.getTimer());
		
		// Add MATSim plug-in for all nodes
		realm.addPlugin(matSim);
		
		// Configure loop-back networking for all nodes
		realm.addPlugin(new SimpleBroadcastDevice(0, 0, 100000, SimpleBroadcastDevice.DEFAULT_MTU));
		realm.addPlugin(Network.class);
		realm.addPlugin(DefaultKnowledgePublisher.class);
		realm.addPlugin(KnowledgeInsertingStrategy.class);

		// Node hosting vehicle A
		MATSimVehicle agentA = new MATSimVehicle(); // MATSim agent with start position
		DEECoNode nodeA = realm.createNode(42, agentA, new PositionPlugin(0, 0)); // DEECO node with Id and agent as plug-in
		Vehicle vehicleA = new Vehicle("Vehicle A", new CoordImpl(100000, 100000), agentA); // DEECO component controlling the vehicle
		nodeA.deployComponent(vehicleA);
		nodeA.deployEnsemble(OtherVehicleEnsemble.class);

		// Node hosting vehicle B
		MATSimVehicle agentB = new MATSimVehicle(); // MATSim agent with start position
		DEECoNode nodeB = realm.createNode(45, agentB, new PositionPlugin(0, 100000)); // DEECO node with Id and agent as plug-in
		Vehicle vehicleB = new Vehicle("Vehicle B", new CoordImpl(100000, 100000), agentB); // DEECO component controlling the vehicle
		nodeB.deployComponent(vehicleB);
		nodeB.deployEnsemble(OtherVehicleEnsemble.class);

		// Simulate for specified time
		realm.start(7 * 60000);

		// Check both cars reached the destination and know about each other
		assertThat(log.getLog(), containsString("Vehicle A, pos: 4410 (1995, 2000), dst: 4410, speed: , otherPos: 4410 (1995, 2000)"));
		assertThat(log.getLog(), containsString("Vehicle B, pos: 4410 (1995, 2000), dst: 4410, speed: , otherPos: 4410 (1995, 2000)"));
	}
}
