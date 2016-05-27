package cz.cuni.mff.d3s.jdeeco.ensembles.intelligent.z3;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.junit.Ignore;
import org.junit.Test;

import cz.cuni.mff.d3s.deeco.annotations.processor.AnnotationProcessorException;
import cz.cuni.mff.d3s.deeco.runners.DEECoSimulation;
import cz.cuni.mff.d3s.deeco.runtime.DEECoException;
import cz.cuni.mff.d3s.deeco.runtime.DEECoNode;
import cz.cuni.mff.d3s.deeco.timer.DiscreteEventTimer;
import cz.cuni.mff.d3s.deeco.timer.SimulationTimer;
import cz.cuni.mff.d3s.jdeeco.edl.EDLReader;
import cz.cuni.mff.d3s.jdeeco.edl.model.edl.EdlDocument;

public class IntelligentEnsemblesTest {

	public static void main(String[] args) throws InstantiationException, IllegalAccessException, DEECoException, AnnotationProcessorException, IOException {
		new IntelligentEnsemblesTest().testEnsembles(false);
	}
	
	@Test
	@Ignore
	public void testEnsembles() throws InstantiationException, IllegalAccessException, DEECoException, AnnotationProcessorException, IOException {
		testEnsembles(true);
	}
	
	private void testEnsembles(boolean silent) throws InstantiationException, IllegalAccessException, DEECoException, AnnotationProcessorException, IOException {		
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		if (silent) {
			RescuerComponent.outputStream = new PrintStream(baos);
		} else {
			RescuerComponent.outputStream = System.out;
		}
		
		/* create main application container */
		SimulationTimer simulationTimer = new DiscreteEventTimer();
		DEECoSimulation realm = new DEECoSimulation(simulationTimer);
		
		/* create one and only deeco node (centralized deployment) */
		DEECoNode deeco = realm.createNode(0);
		/* deploy components and ensemble factories */
		
		deeco.deployComponent(new RescuerComponent("1", 100));
		deeco.deployComponent(new RescuerComponent("2", 80));
		deeco.deployComponent(new RescuerComponent("3", 60));
		deeco.deployComponent(new RescuerComponent("4", 40));
		deeco.deployComponent(new RescuerComponent("5", 20));
		deeco.deployComponent(new RescuerComponent("6", 0));
		deeco.deployComponent(new RescuerComponent("7", 10));
		deeco.deployComponent(new RescuerComponent("8", 30));
		deeco.deployComponent(new RescuerComponent("9", 50));
		deeco.deployComponent(new RescuerComponent("10", 70));
		deeco.deployComponent(new RescuerComponent("11", 90));
		deeco.deployComponent(new RescuerComponent("12", 80));
		deeco.deployComponent(new RescuerComponent("13", 80));
		deeco.deployComponent(new RescuerComponent("14", 80));
		deeco.deployComponent(new RescuerComponent("15", 80));
		deeco.deployComponent(new FireFighterComponent("101", 10));
		deeco.deployComponent(new FireFighterComponent("102", 20));
		deeco.deployComponent(new FireFighterComponent("103", 30));

		EdlDocument model = (EdlDocument) new EDLReader().readDocument("test/cz/cuni/mff/d3s/jdeeco/ensembles/intelligent/z3/pendolino.edl");

		deeco.deployEnsembleFactory(new Z3IntelligentEnsembleFactory(model));
		
		/* WHEN simulation is performed */
		realm.start(999);
		
		if (silent) {
			assertThat(baos.toString(), containsString("Rescuer 1: train 2"));
			assertThat(baos.toString(), containsString("Rescuer 2: train 2"));
			assertThat(baos.toString(), containsString("Rescuer 3: train 1"));
			assertThat(baos.toString(), containsString("Rescuer 4: train 2"));
			assertThat(baos.toString(), containsString("Rescuer 5: train 1"));
			assertThat(baos.toString(), containsString("Rescuer 6: train 1"));
		}
	}
}