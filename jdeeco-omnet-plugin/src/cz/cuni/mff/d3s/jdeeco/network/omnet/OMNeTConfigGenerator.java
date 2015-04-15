package cz.cuni.mff.d3s.jdeeco.network.omnet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import cz.cuni.mff.d3s.deeco.simulation.omnet.OMNeTNative;
import cz.cuni.mff.d3s.jdeeco.position.Position;
import cz.cuni.mff.d3s.jdeeco.position.PositionPlugin;

public class OMNeTConfigGenerator {
	class Node {
		public final int id;
		public int ordinal;
		public final String ipAddress;
		public Position position;

		Node(int id, String ipAddress, Position position) {
			this.id = id;
			this.ipAddress = ipAddress;
			this.position = position;
		}
	}

	static final String DEFAULT_CONTENT = String.format("%s%somnetpp.ini", OMNeTNative.LIB_PATH, File.separator);

	private final long limit;

	private Set<Node> nodes = new HashSet<>();

	public OMNeTConfigGenerator(long limit) {
		this.limit = limit;
	}

	public void addNode(Node node) {
		nodes.add(node);
	}

	public void addNode(int id, String ipAddress, Position position) {
		addNode(new Node(id, ipAddress, position));
	}

	public void addNode(OMNeTSimulation.OMNeTHost host) {
		// Determine node IP address
		String ip = null;
		if (host.infrastructureDevice != null) {
			ip = host.infrastructureDevice.address.ipAddress;
		}

		// Determine node position
		Position position = new Position(0, 0, 0);
		if (host.broadcastDevice != null) {
			position = host.container.getPluginInstance(PositionPlugin.class).getInitialPosition();
		}
		addNode(new Node(host.getId(), ip, position));
	}

	public String getContent() throws IOException {
		StringBuilder content = new StringBuilder();

		// Load default configuration content
		try {
			content.append(new String(Files.readAllBytes(Paths.get(DEFAULT_CONTENT))));
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Base OMNeT configuration file not found", e);
		}
		content.append(String.format("%n%n%n# CONTENT GENERATED BY %s %n%n%n", getClass().getName()));

		// Add num nodes
		content.append(String.format("**.numNodes = %d%n", nodes.size()));

		// Add time limit
		content.append(String.format("sim-time-limit = %fs%n", (double) limit / 1000));

		// Add nodes
		int counter = 0;
		for (Node node : nodes) {
			node.ordinal = counter++;
			content.append(String.format("%n%n# Node %d definition%n", node.id));
			content.append(String.format("**.node[%d].mobility.initialX = %dm%n", node.ordinal, (int)node.position.x));
			content.append(String.format("**.node[%d].mobility.initialY = %dm%n", node.ordinal, (int)node.position.y));
			content.append(String.format("**.node[%d].mobility.initialZ = %dm%n", node.ordinal, (int)node.position.z));
			content.append(String.format("**.node[%d].appl.id = %d", node.ordinal, node.id));
		}

		// Add IP configuration
		content.append(String.format("%n%n%n# IP Static configuration%n"));
		content.append("*.configurator.config = xml(\"");
		content.append(String.format("<config>\\%n"));
		for (Node node : nodes) {
			if (node.ipAddress != null) {
				content.append(String.format("\t<interface hosts='**.node[%d]' address='%s' netmask='255.x.x.x'/>\\%n",
						node.ordinal, node.ipAddress));
			}
		}
		content.append("</config>\")");

		return content.toString();
	}

	public File writeToTemp() throws IOException {
		// Note: OMNeT finds its parts relative to configuration file
		File temp = new File(String.format("%s%somnentpp-%d.ini", OMNeTNative.LIB_PATH, File.separator,
				System.currentTimeMillis()));
		temp.deleteOnExit();

		FileWriter writer = new FileWriter(temp);
		writer.write(getContent());
		writer.close();

		return temp;
	}
}
