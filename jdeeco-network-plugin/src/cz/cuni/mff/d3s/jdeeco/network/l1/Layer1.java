package cz.cuni.mff.d3s.jdeeco.network.l1;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cz.cuni.mff.d3s.jdeeco.network.Address;
import cz.cuni.mff.d3s.jdeeco.network.L1DataProcessor;
import cz.cuni.mff.d3s.jdeeco.network.L1StrategyManager;
import cz.cuni.mff.d3s.jdeeco.network.L2PacketSender;
import cz.cuni.mff.d3s.jdeeco.network.l0.Device;
import cz.cuni.mff.d3s.jdeeco.network.l0.Layer0;
import cz.cuni.mff.d3s.jdeeco.network.l2.L2Packet;
import cz.cuni.mff.d3s.jdeeco.network.l2.L2ReceivedInfo;

/**
 * Defines L1 methods that are called from the upper layer (L2) or L1 strategies.
 * 
 * 
 * @author Michal Kit <kit@d3s.mff.cuni.cz>
 *
 */
public class Layer1 implements L2PacketSender, L1StrategyManager {

	private final Set<L1Strategy> strategies; // registered strategies
	private final int nodeId; // node ID
	private final DataIDSource dataIdSource; // data ID source
	private final Map<Device, Layer0> layers0; // layers 0 for each device
	private final Map<CollectorKey, Collector> collectors; // collectors that store incoming L1 packets. Grouped by data
															// ID and Node ID
	private final L1DataProcessor l1DataProcessor; // reference to the upper layer

	public Layer1(L1DataProcessor l1DataProcessor, int nodeId, DataIDSource dataIdSource) {
		this.layers0 = new HashMap<Device, Layer0>();
		this.strategies = new HashSet<L1Strategy>();
		this.collectors = new HashMap<CollectorKey, Collector>();
		this.nodeId = nodeId;
		this.dataIdSource = dataIdSource;
		this.l1DataProcessor = l1DataProcessor;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * cz.cuni.mff.d3s.jdeeco.network.L1StrategyManager#registerL1Strategy(cz.cuni.mff.d3s.jdeeco.network.l1.L1Strategy)
	 */
	@Override
	public void registerL1Strategy(L1Strategy strategy) {
		strategies.add(strategy);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see cz.cuni.mff.d3s.jdeeco.network.L1StrategyManager#getRegisteredL1Strategies()
	 */
	@Override
	public Collection<L1Strategy> getRegisteredL1Strategies() {
		Set<L1Strategy> result = new HashSet<L1Strategy>();
		result.addAll(strategies);
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * cz.cuni.mff.d3s.jdeeco.network.L1StrategyManager#unregisterL1Strategy(cz.cuni.mff.d3s.jdeeco.network.l1.L1Strategy
	 * )
	 */
	@Override
	public boolean unregisterL1Strategy(L1Strategy strategy) {
		return strategies.remove(strategy);
	}

	/**
	 * Registers new network device.
	 * 
	 * @param device
	 *            device to be registered
	 */
	public void registerDevice(Device device) {
		if (!layers0.containsKey(device)) {
			layers0.put(device, new Layer0(device));
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see cz.cuni.mff.d3s.jdeeco.network.L2PacketSender#sendL2Packet(cz.cuni.mff.d3s.jdeeco.network.l2.L2Packet,
	 * cz.cuni.mff.d3s.jdeeco.network.Address)
	 */
	public boolean sendL2Packet(L2Packet l2Packet, Address address) {
		if (l2Packet != null) {
			for (Device device : layers0.keySet()) {
				/**
				 * Go through every device and check whether it is capable to send to the desired address.
				 */
				if (device.canSend(address)) {
					int chunkSize = device.getMTU() - 4;
					/**
					 * Disassemble the L2 packet into the L1 packets.
					 */
					List<L1Packet> l1Packets = disassembleL2ToL1(l2Packet, chunkSize);
					if (l1Packets.size() > 0) {
						bufferAndSend(l1Packets, device, address);
						return true;
					} else {
						return false;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Looks for a device being able to send data for a given address and when found it sends packet over that device.
	 * Used by L1 strategies.
	 * 
	 * @param l1Packet
	 *            packet to be sent
	 * @param address
	 *            network address to which packet is destined
	 * @return true whenever packet was sent. False otherwise.
	 */
	public boolean sendL1Packet(L1Packet l1Packet, Address address) {
		if (l1Packet != null) {
			for (Device device : layers0.keySet()) {
				if (device.canSend(address)) {
					send(l1Packet, device, address);
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Processes L0 packet coming from a device.
	 * 
	 * @param l0Packet
	 *            L0 packet to be processed
	 * @param device
	 *            device that received this L0 packet
	 * @param receivedInfo
	 *            additional information on packet receival
	 */
	public void processL0Packet(byte[] l0Packet, Device device, ReceivedInfo receivedInfo) {
		ByteBuffer byteBuffer = ByteBuffer.wrap(l0Packet);
		int l1PacketCount = byteBuffer.getInt();
		int l0PacketChunkSize;
		byte[] l0PacketChunk;
		L1Packet l1Packet;
		Collector collector = null;
		CollectorKey key = null;
		for (int i = 0; i < l1PacketCount; i++) {
			l0PacketChunkSize = byteBuffer.getInt();
			l0PacketChunk = new byte[l0PacketChunkSize];
			byteBuffer.get(l0PacketChunk, byteBuffer.position(), l0PacketChunkSize);
			l1Packet = L1Packet.fromBytes(l0PacketChunk);
			l1Packet.receivedInfo = receivedInfo;
			if (key == null || key.equals(new CollectorKey(l1Packet.dataId, l1Packet.srcNode))) {
				key = new CollectorKey(l1Packet.dataId, l1Packet.srcNode);
				collector = collectors.get(key);
				if (collector == null) {
					collector = new Collector(l1Packet.totalSize);
					collectors.put(key, collector);
				}
			}
			collector.addL1Packet(l1Packet);
			if (collector.isComplete()) {
				// FIX header is unknown at this level
				l1DataProcessor.processL1Data(collector.getMarshalledData(), collector.getL2ReceivedInfo());
				collectors.remove(key);
			}
		}
	}

	/**
	 * Sends L1 packet. Can be used by L1 strategies.
	 * 
	 * @param l1Packet
	 *            L1 packet to be sent
	 * @param device
	 *            device to be used for transmission
	 * @param address
	 *            destination address
	 */
	protected void send(L1Packet l1Packet, Device device, Address address) {
		Layer0 layer0 = layers0.get(device);
		layer0.bufferPackets(l1Packet, address);
		// FIX - should not send all the packets. It should send only this packet.
		layer0.sendAll();
	}

	/**
	 * It buffers L1 packet and then tries to send buffered packets in MTUs of the device.
	 * 
	 * 
	 * @param l1Packet
	 *            L1 packet to be sent
	 * @param device
	 *            device to be used for transmission
	 * @param address
	 *            destination address
	 */
	protected void bufferAndSend(Collection<L1Packet> l1Packets, Device device, Address address) {
		Layer0 layer0 = layers0.get(device);
		layer0.bufferPackets(l1Packets, address);
		layer0.sendMTUs();
	}

	/**
	 * Disassembles L2 packet into L1 packets according to the given MTU.
	 * 
	 * @param l2Packet
	 *            L2 packet to be disassembled
	 * @param mtu
	 *            maximum L1 packet size
	 * @return L1 packets being the disassembling the L2 packet
	 */
	protected List<L1Packet> disassembleL2ToL1(L2Packet l2Packet, int mtu) {
		LinkedList<L1Packet> result = new LinkedList<L1Packet>();
		if (l2Packet.getData() != null && l2Packet.getData().length > 0) {
			L2ReceivedInfo receivedInfo = l2Packet.receivedInfo;
			int totalSize = l2Packet.getData().length;
			int srcNode, dataId;
			int chunkSize = mtu - Layer0.L0_CHUNK_COUNT_BYTES - Layer0.L0_CHUNK_SIZE_BYTES; // MTU - L1PacketCount -
																							// SIZE OF L1Packet
			if (receivedInfo == null) {
				srcNode = nodeId;
				dataId = dataIdSource.createDataID();
			} else {
				srcNode = receivedInfo.srcNode;
				dataId = receivedInfo.dataId;
			}
			int current = 0;
			byte[] payload;
			while (current < l2Packet.getData().length) {
				payload = Arrays.copyOfRange(l2Packet.getData(), current,
						Math.min(current + chunkSize, l2Packet.getData().length - 1));
				result.add(new L1Packet(payload, srcNode, dataId, current, totalSize, null));
				current += chunkSize;
			}
		}
		return result;
	}

	/**
	 * Stores incoming L1 packets from the network. It provides facilities that help to assemble L2 packets from
	 * retrieved L1 packets.
	 * 
	 * 
	 * @author Michal Kit <kit@d3s.mff.cuni.cz>
	 *
	 */
	protected class Collector {
		private final LinkedList<L1Packet> l1Packets; // incoming L1 packets

		/**
		 * Facility map representing the complete payload. Initially all elements are false indicating no data. While L1
		 * packets arrive its false entries turn into true
		 */
		private final boolean[] map;
		/**
		 * Indicates whether it is possible to assemble L2 packet from the available L1 packets. This field is for
		 * optimization
		 */
		private boolean isComplete = false;
		/** Indicates whether the value in the isContinue field is valid */
		private boolean isCompleteValid = true;

		public Collector(int totalSize) {
			this.l1Packets = new LinkedList<L1Packet>();
			this.map = new boolean[totalSize];
		}

		/**
		 * Adds packet to the collector and updates the map of available data.
		 * 
		 * @param l1Packet
		 *            L1 packet to be added
		 */
		public void addL1Packet(L1Packet l1Packet) {
			isCompleteValid = false;
			this.l1Packets.addAll(l1Packets);
			for (int i = l1Packet.startPos; i < l1Packet.startPos + l1Packet.payloadSize; i++) {
				map[i] = true;
			}
		}

		/**
		 * States whether the L2 packet is complete. For external use.
		 * 
		 * @return true whenever L2 packet is complete. False otherwise.
		 */
		public boolean isComplete() {
			if (!isCompleteValid) {
				isCompleteValid = true;
				if (l1Packets.isEmpty()) {
					isComplete = false;
				} else {
					isComplete = true;
					for (boolean fill : map) {
						if (!fill) {
							isComplete = false;
							break;
						}
					}
				}
			}
			return isComplete;
		}

		/**
		 * Retrieves the marshalled data of the L2 packet.
		 * 
		 * @return marshalled data of the L2 packet
		 */
		public byte[] getMarshalledData() {
			if (isComplete()) {
				int totalSize = l1Packets.getFirst().totalSize;
				byte[] result = new byte[totalSize];
				int to;
				for (L1Packet l1Packet : l1Packets) {
					to = l1Packet.startPos + l1Packet.payloadSize;
					for (int i = l1Packet.startPos, j = 0; i < to && j < l1Packet.payloadSize; i++, j++) {
						result[i] = l1Packet.payload[j];
					}
				}
				return result;
			} else {
				return null;
			}
		}

		/**
		 * Creates (if possible) L2ReceivedInfo instance from the L1 packets.
		 * 
		 * @return L2ReceivedInfo instance
		 */
		public L2ReceivedInfo getL2ReceivedInfo() {
			if (isComplete()) {
				L1Packet first = l1Packets.getFirst();
				return new L2ReceivedInfo(new LinkedList<L1Packet>(l1Packets), first.srcNode, first.dataId);
			} else {
				return null;
			}
		}
	}

	/**
	 * Combines two values: data ID and source node to compose a key for each collector.
	 * 
	 * @author Michal Kit <kit@d3s.mff.cuni.cz>
	 *
	 */
	protected class CollectorKey {
		public final int dataId;
		public final int srcNode;

		public CollectorKey(int dataId, int srcNode) {
			this.dataId = dataId;
			this.srcNode = srcNode;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + dataId;
			result = prime * result + srcNode;
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
			CollectorKey other = (CollectorKey) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (dataId != other.dataId)
				return false;
			if (srcNode != other.srcNode)
				return false;
			return true;
		}

		private Layer1 getOuterType() {
			return Layer1.this;
		}
	}
}
