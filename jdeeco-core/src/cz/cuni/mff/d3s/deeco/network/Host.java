package cz.cuni.mff.d3s.deeco.network;

import cz.cuni.mff.d3s.deeco.scheduler.CurrentTimeProvider;

/**
 * 
 * Class representing a host in the simulation.
 * 
 * @author Michal Kit <kit@d3s.mff.cuni.cz>
 * 
 */
public class Host implements CurrentTimeProvider, NetworkInterface {

	private final PacketReceiver packetReceiver;
	private final PacketSender packetSender;
	
	private final String id;
	
	private final NetworkProvider networkProvider;
	private final PositionProvider positionProvider;
	private final CurrentTimeProvider timeProvider;
	
	// XXX: Why are the two last booleans here if they are not used?
	protected Host(NetworkProvider networkProvider, PositionProvider positionProvider, CurrentTimeProvider timeProvider, String jDEECoAppModuleId, boolean hasMANETNic, boolean hasIPNic) {
		this.networkProvider = networkProvider;
		this.positionProvider = positionProvider;
		this.timeProvider = timeProvider;
		this.id = jDEECoAppModuleId;
		this.packetReceiver = new PacketReceiver(id);
		this.packetSender = new PacketSender(this);
		this.packetReceiver.setCurrentTimeProvider(this);
	}
	
	public Host(NetworkProvider networkProvider, PositionProvider positionProvider, CurrentTimeProvider timeProvider, String jDEECoAppModuleId) {
		this(networkProvider, positionProvider, timeProvider, jDEECoAppModuleId, true, true);
	}

	public PacketReceiver getPacketReceiver() {
		return packetReceiver;
	}
	
	public PacketSender getPacketSender() {
		return packetSender;
	}

	public String getHostId() {
		return id;
	}

	// Method used by the simulation

	public void packetReceived(byte[] packet, double rssi) {
		packetReceiver.packetReceived(packet, rssi);
	}

	// The method used by publisher
	public void sendPacket(byte[] packet, String recipient) {
		networkProvider.sendPacket(id, packet, recipient);
	}

	public double getPositionX() {
		return positionProvider.getPositionX(this);
	}

	public double getPositionY() {
		return positionProvider.getPositionY(this);
	}

	@Override
	public long getCurrentMilliseconds() {
		return timeProvider.getCurrentMilliseconds();
	}
	
	public void finalize() {
		packetReceiver.clearCachedMessages();
	}
}