package cz.cuni.mff.d3s.deeco.provider;

import java.util.LinkedList;
import java.util.List;

import cz.cuni.mff.d3s.deeco.invokable.SchedulableProcess;
import cz.cuni.mff.d3s.deeco.knowledge.ComponentKnowledge;

public abstract class AbstractDEECoObjectProvider {

	protected List<SchedulableProcess> processes;
	protected List<ComponentKnowledge> knowledges;
	
	public List<SchedulableProcess> getProcesses() {
		if (processes == null) {
			processProcesses();
		}
		return processes;
	}
	
	public List<ComponentKnowledge> getKnowledges() {
		if (knowledges == null) {
			processKnowledges();
		}
		return knowledges;
	}
	
	abstract protected void processKnowledges();
	
	abstract protected void processProcesses();

}
