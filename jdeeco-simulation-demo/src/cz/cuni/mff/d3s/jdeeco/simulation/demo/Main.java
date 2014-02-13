package cz.cuni.mff.d3s.jdeeco.simulation.demo;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import cz.cuni.mff.d3s.deeco.DeecoProperties;
import cz.cuni.mff.d3s.deeco.annotations.processor.AnnotationProcessor;
import cz.cuni.mff.d3s.deeco.annotations.processor.AnnotationProcessorException;
import cz.cuni.mff.d3s.deeco.knowledge.ReadOnlyKnowledgeManager;
import cz.cuni.mff.d3s.deeco.logging.Log;
import cz.cuni.mff.d3s.deeco.model.runtime.api.KnowledgePath;
import cz.cuni.mff.d3s.deeco.model.runtime.api.RuntimeMetadata;
import cz.cuni.mff.d3s.deeco.model.runtime.custom.RuntimeMetadataFactoryExt;
import cz.cuni.mff.d3s.deeco.network.DirectGossipStrategy;
import cz.cuni.mff.d3s.deeco.network.DirectRecipientSelector;
import cz.cuni.mff.d3s.deeco.network.KnowledgeData;
import cz.cuni.mff.d3s.deeco.network.KnowledgeDataManager;
import cz.cuni.mff.d3s.deeco.network.PacketReceiver;
import cz.cuni.mff.d3s.deeco.network.PacketSender;
import cz.cuni.mff.d3s.deeco.network.PublisherTask;
import cz.cuni.mff.d3s.deeco.runtime.RuntimeFramework;
import cz.cuni.mff.d3s.deeco.simulation.Host;
import cz.cuni.mff.d3s.deeco.simulation.Simulation;
import cz.cuni.mff.d3s.deeco.simulation.SimulationRuntimeBuilder;

/**
 * Main class for launching the CBSE evaluation demo.
 * 
 * @author Jaroslav Keznikl <keznikl@d3s.mff.cuni.cz>
 * 
 */
public class Main {

	static String OMNET_CONFIG_TEMPLATE = "omnetpp.ini.templ";
	static String OMNET_CONFIG_PATH = "omnetpp.ini";
	static String OMNET_NETWORK_CONF_PATH = "network-config/network-demo.xml";
	
	static String DEFAULT_COMPONENT_CFG = "configurations/component.cfg";
	static String DEFAULT_SITE_CFG = "configurations/site.cfg";
	
	static int SIMULATION_DURATION = 10000;

	
	public static void main(String[] args) throws AnnotationProcessorException, IOException {
		String componentCfg = DEFAULT_COMPONENT_CFG;
		String siteCfg = DEFAULT_SITE_CFG;
		
		if (args.length == 2) {
			componentCfg = args[0];
			siteCfg = args[1];
		}
		
		Simulation sim = new Simulation(true);
		sim.initialize(); //loads Library
		
		AnnotationProcessor processor = new AnnotationProcessor(RuntimeMetadataFactoryExt.eINSTANCE);
		SimulationRuntimeBuilder builder = new SimulationRuntimeBuilder();
		
		SiteConfigParser siteParser = new SiteConfigParser(siteCfg);
		Position topRight = siteParser.parseTopRightCorner();
		
		Area area = null;
		Set<Area> areas = new HashSet<>();
		while ((area = siteParser.parseArea()) != null) {
			areas.add(area);
		}
		
		final AreaNetworkRegistry networkRegistry = AreaNetworkRegistry.getInstance();
		networkRegistry.initialize(areas);

		TeamLocationService.INSTANCE.init(areas);
		
		ComponentConfigParser parser = new ComponentConfigParser(componentCfg);
		
		PositionAwareComponent component = null;
		List<RuntimeFramework> runtimes = new ArrayList<>();
		List<Host> hosts = new ArrayList<>();
		
		StringBuilder omnetConfig = new StringBuilder();
		int i = 0;		
		
		DirectRecipientSelector directRecipientSelector;
		DirectGossipStrategy directGossipStrategy = new DirectGossipStrategy() {
			
			@Override
			public boolean gossipTo(String recipient) {
				return new Random().nextInt(100) < 5;
			}
		};
		
		// for each component config crate a separate model including only the component and all ensemble definitions,
		// a separate host, and a separate runtime framework
		List<PositionAwareComponent> components = new LinkedList<>();
		while ((component = parser.parseComponent()) != null) {
			components.add(component);
			RuntimeMetadata model = RuntimeMetadataFactoryExt.eINSTANCE.createRuntimeMetadata();
			processor.process(model, component, MemberDataAggregation.class); 
						
			omnetConfig.append(String.format(
					"**.node[%s].mobility.initialX = %dm\n", 
					i, (int) (component.position.x)));
			omnetConfig.append(String.format(
					"**.node[%s].mobility.initialY = %dm\n", 
					i, (int) (component.position.y)));
			omnetConfig.append(String.format(
					"**.node[%s].mobility.initialZ = 0m\n", i));
			omnetConfig.append(String.format(
					"**.node[%s].appl.id = \"%s\"\n\n", i, component.id));
			Host host = sim.getHost(component.id, "node["+i+"]");			
			hosts.add(host);
			
			networkRegistry.addComponent(component);
			
			// there is only one component instance
			model.getComponentInstances().get(0).getInternalData().put(PositionAwareComponent.HOST_REFERENCE, host);
			
			directRecipientSelector = new DirectRecipientSelector() {
				
				@Override
				public Collection<String> getRecipients(KnowledgeData data,
						ReadOnlyKnowledgeManager sender) {
					List<String> result = new LinkedList<>();
					KnowledgePath kpTeam = KnowledgePathBuilder.buildSimplePath("teamId");
					String ownerTeam = (String) data.getKnowledge().getValue(kpTeam);
					if (ownerTeam != null) {
						//Find all areas of my team
						List<Area> areas = networkRegistry.getTeamSites(ownerTeam);
						//Pick one randomly
						Area area = areas.get(new Random().nextInt(areas.size()));
						//Get all the members in that area
						List<PositionAwareComponent> recipients = networkRegistry.getMembersBelongingToTeam(ownerTeam, area);
						//Randomly choose a subset of them and return those as possible message recipients
						for (PositionAwareComponent c : recipients) {
							if (!c.id.equals(sender.getId()) &&new Random().nextInt(2) == 0) {
								result.add(c.id);
							}
						}
					}
					//return result;
					return new LinkedList<>();
				}
			};
			
			RuntimeFramework runtime = builder.build(host, model, Arrays.asList(directRecipientSelector), directGossipStrategy); 
			runtimes.add(runtime);
			runtime.start();
			i++;
		}	
		
		Files.copy(Paths.get(OMNET_CONFIG_TEMPLATE), Paths.get(OMNET_CONFIG_PATH), StandardCopyOption.REPLACE_EXISTING);
		
		PrintWriter out = new PrintWriter(Files.newOutputStream(Paths.get(OMNET_CONFIG_PATH), StandardOpenOption.APPEND));
		out.println();
		out.println(String.format("**.playgroundSizeX = %dm", (int) topRight.x));
		out.println(String.format("**.playgroundSizeY = %dm", (int) topRight.y));
		out.println();
		out.println(String.format("**.numNodes = %d", hosts.size()));
		out.println();
		out.println(omnetConfig.toString());
		
//		StringBuilder routerConfig = generateNetworkConfig(areas, components, OMNET_NETWORK_CONF_PATH);
		
//		out.println(routerConfig);
		
		out.close();

		logSimulationParameters(i);
		
		sim.run("Cmdenv", OMNET_CONFIG_PATH);
		
		sim.finalize();
		
		//System.gc();
		System.out.println("Simulation finished.");
	}


	private static void logSimulationParameters(int componentCnt) {
		Log.d(String.format("Simulation parameters: %d components, packet size %d, publish period %d,"
				+ " %s publishing, boundary %s, cache deadline %d, cache wipe period %d, maxRebroadcastDelay %d",
				componentCnt, 
				Integer.getInteger(DeecoProperties.PACKET_SIZE, PacketSender.DEFAULT_PACKET_SIZE), 
				Integer.getInteger(DeecoProperties.PUBLISHING_PERIOD, PublisherTask.DEFAULT_PUBLISHING_PERIOD), 
				Boolean.getBoolean(DeecoProperties.USE_INDIVIDUAL_KNOWLEDGE_PUBLISHING) ?  "individual" : "list",
				Boolean.getBoolean(DeecoProperties.DISABLE_BOUNDARY_CONDITIONS) ?  "disabled" : "enabled",
				Integer.getInteger(DeecoProperties.MESSAGE_CACHE_DEADLINE, PacketReceiver.DEFAULT_MAX_MESSAGE_TIME),
				Integer.getInteger(DeecoProperties.MESSAGE_CACHE_WIPE_PERIOD, PacketReceiver.DEFAULT_MESSAGE_WIPE_PERIOD),
				Integer.getInteger(DeecoProperties.MAXIMUM_REBROADCAST_DELAY, KnowledgeDataManager.DEFAULT_MAX_REBROADCAST_DELAY)));
	}
	
//	private static StringBuilder generateNetworkConfig(Set<Area> areas, List<PositionAwareComponent> components, String networkConfig) throws IOException {
//		assert areas.size() < 250;
//		StringBuilder oConfig = new StringBuilder();
//		PrintWriter out = new PrintWriter(Files.newOutputStream(Paths.get(networkConfig)));
//		oConfig.append("\n");
//		oConfig.append("**.numRouters = " + areas.size() + "\n\n");
//		oConfig.append("\n");
//		
//		out.println("<config>");
//		//Generate interfaces for each of the areas
//		List<PositionAwareComponent> copyOfComponents = new LinkedList<>(components);
//		Set<PositionAwareComponent> toDelete;
//		int i = 0;
//		//Configure interfaces and default routes
//		for (Area area : areas) {
//			//Assign addresses to the ethernet interfaces
//			out.println("<interface hosts='router["+ i +"]' names='wlan0' address='192." + i + ".x.x' netmask='255.255.x.x'/>");
//			out.println("<interface hosts='router[" + i + "]' names='eth0' address='193.1.x.x' netmask='255.255.x.x'/>");
//			//Assign the default route to Wireless
//			out.println("<route hosts='router["+ i +"]' destination='192." + i + ".0.0' netmask='255.255.0.0' interface='wlan0'/>");
//			//Assign the default route to switch
//			for (int j = 0; j < areas.size(); j++)
//				if (j != i)
//					out.println("<route hosts='router["+ i +"]' destination='192."+j+".0.0' netmask='255.255.0.0' gateway='router["+j+"]' interface='eth0'/>");
//			toDelete = new HashSet<>();
//			for (PositionAwareComponent c : copyOfComponents) {
//				if (area.isInArea(c.position)) {
//					System.out.println();
//					toDelete.add(c);
//					//Interface description
//					out.println("<interface hosts='node["+ components.indexOf(c) +"]' names='nic80211' address='192." + i + ".x.x' netmask='255.255.x.x'/>");
//					//Default route
//					out.println("<route hosts='node[" + components.indexOf(c) + "]' destination='*' netmask='*' gateway='router[" + i + "]' interface='nic80211'/>");
//				}
//			}
//			copyOfComponents.removeAll(toDelete);			
//			oConfig.append("**.router["+i+"].mobility.initialX = " + area.getCenterX() + "m\n");
//			oConfig.append("**.router["+i+"].mobility.initialY = " + area.getCenterY() + "m\n");
//			oConfig.append("**.router["+i+"].mobility.initialZ = 0m\n");
//			
//			i++;
//		}
//		//Assign default network
//		for (PositionAwareComponent c : copyOfComponents) {
//			out.println("<interface hosts='node["+ components.indexOf(c) +"]' names='nic80211' address='192.0.x.x' netmask='255.255.x.x'/>");
//		}
//		out.println("</config>");
//		out.close();
//		return oConfig;
//	}
}
