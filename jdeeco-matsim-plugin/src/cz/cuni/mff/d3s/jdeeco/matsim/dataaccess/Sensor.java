package cz.cuni.mff.d3s.jdeeco.matsim.dataaccess;

public interface Sensor<T> {
	
	SensorType getSensorType();
	T read();
}
