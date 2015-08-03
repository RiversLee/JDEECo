package cz.cuni.mff.d3s.deeco.ensembles.intelligent;

import java.util.Collection;
import java.util.Map;

import cz.cuni.mff.d3s.deeco.ensembles.EnsembleFactory;
import cz.cuni.mff.d3s.deeco.ensembles.EnsembleFormationException;
import cz.cuni.mff.d3s.deeco.ensembles.EnsembleInstance;
import cz.cuni.mff.d3s.deeco.knowledge.container.KnowledgeContainer;

/**
 * 
 * @author Zbyněk Jiráček
 *
 */
public abstract class MiniZincIntelligentEnsembleFactory implements EnsembleFactory {
	
	private MznScriptRunner scriptRunner;
	
	public MiniZincIntelligentEnsembleFactory(MznScriptRunner scriptRunner) {
		this.scriptRunner = scriptRunner;
	}
	
	public MiniZincIntelligentEnsembleFactory(String scriptPath) {
		this(new MznScriptRunner(scriptPath));
	}
			
	protected abstract ScriptInputVariableRegistry parseInput(KnowledgeContainer knowledgeContainer) throws EnsembleFormationException;
	
	protected abstract Collection<EnsembleInstance> createInstancesFromOutput(ScriptOutputVariableRegistry scriptOutput) throws EnsembleFormationException;


	@Override
	public Collection<EnsembleInstance> createInstances(KnowledgeContainer container) throws EnsembleFormationException {
		
		ScriptInputVariableRegistry inputVars = parseInput(container);
		
		try {
			Map<String, String> output = scriptRunner.runScript(inputVars);
			ScriptOutputVariableRegistry outputVars = new ScriptOutputVariableRegistry(output);
			return createInstancesFromOutput(outputVars);
			
		} catch (ScriptExecutionException e) {
			throw new EnsembleFormationException("Failed to create ensemble instance (" + this.getClass().getName() + ")", e);
		}
	}
}
