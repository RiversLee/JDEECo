package cz.cuni.mff.d3s.jdeeco.ensembles.intelligent.z3;

public class RandomNameGenerator {

	private static int index;
		
	public static String getNext() {
		return "_tmp" + (index++);
	}

}
