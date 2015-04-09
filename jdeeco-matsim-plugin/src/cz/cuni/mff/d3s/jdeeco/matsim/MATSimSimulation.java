package cz.cuni.mff.d3s.jdeeco.matsim;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.mobsim.framework.Mobsim;
import org.matsim.core.router.util.TravelTime;
import org.matsim.withinday.trafficmonitoring.TravelTimeCollector;
import org.matsim.withinday.trafficmonitoring.TravelTimeCollectorFactory;

import cz.cuni.mff.d3s.deeco.logging.Log;
import cz.cuni.mff.d3s.deeco.network.AbstractHost;
import cz.cuni.mff.d3s.deeco.runtime.DEECoContainer;
import cz.cuni.mff.d3s.deeco.runtime.DEECoPlugin;
import cz.cuni.mff.d3s.deeco.timer.CurrentTimeProvider;
import cz.cuni.mff.d3s.deeco.timer.SimulationTimer;
import cz.cuni.mff.d3s.deeco.timer.TimerEventListener;
import cz.cuni.mff.d3s.jdeeco.matsim.old.roadtrains.MATSimDataProviderReceiver;
import cz.cuni.mff.d3s.jdeeco.matsim.old.simulation.AdditionAwareAgentSource;
import cz.cuni.mff.d3s.jdeeco.matsim.old.simulation.DefaultMATSimExtractor;
import cz.cuni.mff.d3s.jdeeco.matsim.old.simulation.DefaultMATSimUpdater;
import cz.cuni.mff.d3s.jdeeco.matsim.old.simulation.JDEECoAgent;
import cz.cuni.mff.d3s.jdeeco.matsim.old.simulation.JDEECoAgentSource;
import cz.cuni.mff.d3s.jdeeco.matsim.old.simulation.JDEECoMobsimFactory;
import cz.cuni.mff.d3s.jdeeco.matsim.old.simulation.JDEECoWithinDayMobsimListener;
import cz.cuni.mff.d3s.jdeeco.matsim.old.simulation.MATSimDataProvider;
import cz.cuni.mff.d3s.jdeeco.matsim.old.simulation.MATSimDataReceiver;
import cz.cuni.mff.d3s.jdeeco.matsim.old.simulation.MATSimExtractor;
import cz.cuni.mff.d3s.jdeeco.matsim.old.simulation.MATSimPreloadingControler;
import cz.cuni.mff.d3s.jdeeco.matsim.old.simulation.MATSimRouter;
import cz.cuni.mff.d3s.jdeeco.matsim.old.simulation.MATSimSimulationStepListener;

/**
 * Plug-in providing MATSim simulation
 * 
 * Based on the code from the jDEECo 2 simulation project
 * 
 * @author Vladimir Matena <matena@d3s.mff.cuni.cz>
 *
 */
public class MATSimSimulation implements DEECoPlugin {
	private final TreeSet<Callback> callbacks;

	class TimerProvider implements SimulationTimer, CurrentTimeProvider /*
																		 * TODO: Current time provider or simulation
																		 * timer
																		 */, MATSimSimulationStepListener {
		@Override
		public void notifyAt(long time, TimerEventListener listener, DEECoContainer node) {
			// System.out.println("NOTIFY AT CALLED FOR: " + time + " NODE:" + node.getId());
			// MATSimSimulation.this.oldSimulation.callAt(time, String.valueOf(node.getId()));
			final String hostId = String.valueOf(node.getId());

			callAt(time, hostId);

			MATSimSimulation.this.getHost(String.valueOf(node.getId())).listener = listener;
		}

		public synchronized void callAt(long absoluteTime, String hostId) {
			Callback callback = hostIdToCallback.remove(hostId);
			if (callback != null) {
				callbacks.remove(callback);
			}
			callback = new Callback(hostId, absoluteTime);
			hostIdToCallback.put(hostId, callback);
			// System.out.println("For " + absoluteTime);
			callbacks.add(callback);
		}

		@Override
		public long getCurrentMilliseconds() {
			return MATSimSimulation.this.currentMilliseconds;
		}

		@Override
		public void start(long duration) {
			double startTime = MATSimSimulation.this.getController().getConfig().getQSimConfigGroup().getStartTime();
			double endTime = startTime + (((double) (duration)) / 1000);
			MATSimSimulation.this.getController().getConfig().getQSimConfigGroup().setEndTime(endTime);
			MATSimSimulation.this.getController().run();
		}

		@Override
		public void at(double seconds, Mobsim mobsim) {
			// Exchange data with MATSim
			long milliseconds = secondsToMilliseconds(seconds);
			matSimReceiver.setMATSimData(extractor.extractFromMATSim(listener.getAllJDEECoAgents(), mobsim));
			listener.updateJDEECoAgents(matSimProvider.getMATSimData());
			// Add callback for the MATSim step
			callAt(milliseconds + simulationStep, SIMULATION_CALLBACK);
			Host host;
			Callback callback;
			// Iterate through all the callbacks until the MATSim callback.
			while (!callbacks.isEmpty()) {
				callback = callbacks.pollFirst();
				if (callback.getHostId().equals(SIMULATION_CALLBACK)) {
					break;
				}
				currentMilliseconds = callback.getAbsoluteTime();
				// System.out.println("At: " + currentMilliseconds);
				host = hosts.get(callback.hostId);
				host.at(millisecondsToSeconds(currentMilliseconds));
			}
		}

		private double millisecondsToSeconds(long milliseconds) {
			return ((double) (milliseconds)) / 1000;
		}
	}

	public class Host extends AbstractHost {
		public TimerEventListener listener;

		public Host(String id, CurrentTimeProvider timeProvider) {
			super(id, timeProvider);
			// TODO Auto-generated constructor stub
		}

		public void at(double absoluteTime) {
			// System.out.println("CALLBACK CALLED AT: " + getCurrentMilliseconds() + " NODE: " + getHostId());
			listener.at(getCurrentMilliseconds());
		}

	}

	private class Callback implements Comparable<Callback> {

		private final long milliseconds;
		private final String hostId;

		public Callback(String hostId, long milliseconds) {
			this.hostId = hostId;
			this.milliseconds = milliseconds;
		}

		public long getAbsoluteTime() {
			return milliseconds;
		}

		public String getHostId() {
			return hostId;
		}

		@Override
		public int compareTo(Callback c) {
			if (c.getAbsoluteTime() < milliseconds) {
				return 1;
			} else if (c.getAbsoluteTime() > milliseconds) {
				return -1;
			} else if (this == c) {
				return 0;
			} else {
				return this.hashCode() < c.hashCode() ? 1 : -1;
			}
		}

		public String toString() {
			return hostId + " " + milliseconds;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + ((hostId == null) ? 0 : hostId.hashCode());
			result = prime * result + (int) (milliseconds ^ (milliseconds >>> 32));
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Callback other = (Callback) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (hostId == null) {
				if (other.hostId != null)
					return false;
			} else if (!hostId.equals(other.hostId))
				return false;
			if (milliseconds != other.milliseconds)
				return false;
			return true;
		}

		private MATSimSimulation getOuterType() {
			return MATSimSimulation.this;
		}
	}

	private final TimerProvider timer = new TimerProvider();
	private final JDEECoAgentSource agentSource = new JDEECoAgentSource();
	private final MATSimRouter router;
	private final MATSimDataProviderReceiver matSimProviderReceiver = new MATSimDataProviderReceiver(
			new LinkedList<String>());

	// private final cz.cuni.mff.d3s.deeco.simulation.matsim.MATSimSimulation oldSimulation;

	private static final String SIMULATION_CALLBACK = "SIMULATION_CALLBACK";
	private long currentMilliseconds;
	private final long simulationStep; // in milliseconds
	private final TravelTime travelTime;
	private final Map<String, Callback> hostIdToCallback;
	private final Controler controler;
	private final JDEECoWithinDayMobsimListener listener;
	private final MATSimDataProvider matSimProvider;
	private final MATSimDataReceiver matSimReceiver;
	private final Map<String, Host> hosts;
	private final MATSimExtractor extractor;

	// private final Exchanger<Object> exchanger;

	public MATSimSimulation(File config, AdditionAwareAgentSource... additionalAgentSources) throws IOException {
		List<AdditionAwareAgentSource> agentSources = new LinkedList<>();
		agentSources.add(agentSource);
		agentSources.addAll(Arrays.asList(additionalAgentSources));

		/*
		 * oldSimulation = new cz.cuni.mff.d3s.deeco.simulation.matsim.MATSimSimulation(matSimProviderReceiver,
		 * matSimProviderReceiver, new DefaultMATSimUpdater(), new DefaultMATSimExtractor(), agentSources,
		 * config.getAbsolutePath());
		 */
		// this.exchanger = new Exchanger<Object>();

		this.callbacks = new TreeSet<>();
		this.hostIdToCallback = new HashMap<>();
		this.hosts = new HashMap<>();

		this.controler = new MATSimPreloadingControler(config.getAbsolutePath());
		this.controler.setOverwriteFiles(true);
		this.controler.getConfig().getQSimConfigGroup().setSimStarttimeInterpretation("onlyUseStarttime");

		double end = this.controler.getConfig().getQSimConfigGroup().getEndTime();
		double start = this.controler.getConfig().getQSimConfigGroup().getStartTime();
		double step = this.controler.getConfig().getQSimConfigGroup().getTimeStepSize();
		Log.i("Starting simulation: matsimStartTime: " + start + " matsimEndTime: " + end);
		this.extractor = new DefaultMATSimExtractor();
		this.listener = new JDEECoWithinDayMobsimListener(timer, new DefaultMATSimUpdater(), extractor);
		this.matSimProvider = (MATSimDataProvider) matSimProviderReceiver;
		this.matSimReceiver = (MATSimDataReceiver) matSimProviderReceiver;

		Set<String> analyzedModes = new HashSet<String>();
		analyzedModes.add(TransportMode.car);
		travelTime = new TravelTimeCollectorFactory().createTravelTimeCollector(controler.getScenario(), analyzedModes);

		controler.addControlerListener(new StartupListener() {
			public void notifyStartup(StartupEvent event) {
				controler.getEvents().addHandler((TravelTimeCollector) travelTime);
				controler.getMobsimListeners().add((TravelTimeCollector) travelTime);
				controler.setMobsimFactory(new JDEECoMobsimFactory(listener, agentSources));
			}
		});
		/**
		 * Bind MATSim listener with the agent source. It is necessary to let the listener know about the jDEECo agents
		 * that it needs to update with data coming from a jDEECo runtime.
		 */
		for (AdditionAwareAgentSource source : agentSources) {
			if (source instanceof JDEECoAgentSource) {
				listener.registerAgentProvider((JDEECoAgentSource) source);
			}
		}

		this.simulationStep = secondsToMilliseconds(step);
		currentMilliseconds = secondsToMilliseconds(controler.getConfig().getQSimConfigGroup().getStartTime());

		router = new MATSimRouter(getController(), travelTime, 10 /* TODO: FAKE VALUE */);
	}

	private long secondsToMilliseconds(double seconds) {
		return (long) (seconds * 1000);
	}

	public void addHost(String id, cz.cuni.mff.d3s.jdeeco.matsim.MATSimSimulation.Host host) {
		hosts.put(id, host);
	}

	public Host getHost(String id) {
		return hosts.get(id);
	}

	public SimulationTimer getTimer() {
		return timer;
	}

	public MATSimRouter getRouter() {
		return router;
	}

	public Controler getController() {
		return controler;
	}

	public MATSimDataProviderReceiver getMATSimProviderReceiver() {
		return matSimProviderReceiver;
	}

	public void addVehicle(int vehicleId, Id startLink) {
		agentSource.addAgent(new JDEECoAgent(new IdImpl(vehicleId), startLink));
	}

	@Override
	public List<Class<? extends DEECoPlugin>> getDependencies() {
		// No dependencies
		return new LinkedList<Class<? extends DEECoPlugin>>();
	}

	@Override
	public void init(DEECoContainer container) {
		Host host = new Host(String.valueOf(container.getId()), getTimer());

		addHost(String.valueOf(container.getId()), host);
	}
}
