package cz.cuni.mff.d3s.deeco.scheduling;

import java.util.List;

import cz.cuni.mff.d3s.deeco.runtime.model.Ensemble;
import cz.cuni.mff.d3s.deeco.runtime.model.Parameter;
import cz.cuni.mff.d3s.deeco.runtime.model.Schedule;

public class EnsembleTask extends Task {

	private final Ensemble ensemble;
	private final String coordinator;
	private final String member;

	public EnsembleTask(Ensemble ensemble, String coordinator, String member) {
		this.ensemble = ensemble;
		this.coordinator = coordinator;
		this.member = member;
	}

	@Override
	public Schedule getSchedule() {
		return ensemble.getSchedule();
	}

	public Ensemble getEnsemble() {
		return ensemble;
	}

	public String getCoordinator() {
		return coordinator;
	}

	public String getMember() {
		return member;
	}

	public List<Parameter> getMembershipParameters() {
		return ensemble.getMembership().getParameters();
	}

	public List<Parameter> getKnowledgeExchangeParameters() {
		return ensemble.getKnowledgeExchange().getParameters();
	}

	public boolean executeMembership(Object[] parameters) throws Exception {
		return (boolean) super.execute(ensemble.getMembership(), parameters);
	}

	public void executeKnowledgeExchange(Object[] parameters) throws Exception {
		super.execute(ensemble.getKnowledgeExchange(), parameters);
	}
}
