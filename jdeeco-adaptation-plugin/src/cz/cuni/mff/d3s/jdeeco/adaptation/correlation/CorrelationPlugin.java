package cz.cuni.mff.d3s.jdeeco.adaptation.correlation;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cz.cuni.mff.d3s.deeco.annotations.processor.AnnotationProcessorException;
import cz.cuni.mff.d3s.deeco.annotations.processor.AnnotationProcessorExtensionPoint;
import cz.cuni.mff.d3s.deeco.annotations.processor.CorrelationAwareAnnotationProcessorExtension;
import cz.cuni.mff.d3s.deeco.logging.Log;
import cz.cuni.mff.d3s.deeco.model.runtime.api.ComponentInstance;
import cz.cuni.mff.d3s.deeco.runtime.DEECoContainer;
import cz.cuni.mff.d3s.deeco.runtime.DEECoNode;
import cz.cuni.mff.d3s.deeco.runtime.DEECoPlugin;
import cz.cuni.mff.d3s.deeco.runtime.DuplicateEnsembleDefinitionException;
import cz.cuni.mff.d3s.deeco.runtime.PluginStartupFailedException;
import cz.cuni.mff.d3s.deeco.runtime.DEECoContainer.StartupListener;
import cz.cuni.mff.d3s.jdeeco.adaptation.AdaptationPlugin;
import cz.cuni.mff.d3s.metaadaptation.correlation.Component;
import cz.cuni.mff.d3s.metaadaptation.correlation.CorrelationManager;

/**
 * Correlation plugin deploys a component that monitors and correlates data
 * of other components in the system and deploys new ensembles based on the
 * results of the correlation.
 * 
 * <p>It is desirable to have only one instance of the correlation component
 * since its processes are resource demanding and there is no benefit of having
 * more than one instance.</p>
 * 
 * @author Dominik Skoda <skoda@d3s.mff.cuni.cz>
 *
 */
public class CorrelationPlugin implements DEECoPlugin, StartupListener {
// TODO: get rid of CorrelationMetadataWraper and use only faultyKnowledge field
	private boolean verbose;
	private boolean dumpValues;
	
	private final Set<DEECoNode> nodes;
	
	AdaptationPlugin adaptationPlugin;
	
	
	/** Plugin dependencies. */
	@SuppressWarnings("unchecked")
	static private final List<Class<? extends DEECoPlugin>> DEPENDENCIES =
			Arrays.asList(new Class[]{AdaptationPlugin.class});

	@Override
	public List<Class<? extends DEECoPlugin>> getDependencies() {
		return DEPENDENCIES;
	}


	public CorrelationPlugin(Set<DEECoNode> nodes){
		if(nodes == null){
			throw new IllegalArgumentException(String.format("The %s argument is null.", "nodes"));
		}
		
		verbose = false;
		dumpValues = false;		
		this.nodes = nodes;
	}
		
	/**
	 * Specify the verbosity of the correlation process.
	 * @param verbose True to be verbose, false to be still.
	 * @return The self instance of {@link CorrelationPlugin} 
	 */
	public CorrelationPlugin withVerbosity(boolean verbose){
		this.verbose = verbose;
		return this;
	}

	/**
	 * Specify whether the correlation process should dump values while computing.
	 * @param dumpValues True to dump values, false to hold it.
	 * @return The self instance of {@link CorrelationPlugin} 
	 */
	public CorrelationPlugin withDumping(boolean dumpValues){
		this.dumpValues = dumpValues;
		return this;
	}

	@Override
	public void init(DEECoContainer container) {
		AnnotationProcessorExtensionPoint correlationAwareAnnotationProcessorExtension = new CorrelationAwareAnnotationProcessorExtension();
		container.getProcessor().addExtension(correlationAwareAnnotationProcessorExtension);
		container.addStartupListener(this);
		
		try {			
			container.deployComponent(new CorrelationKnowledgeData());
			container.deployEnsemble(CorrelationDataAggregation.class);
			
			adaptationPlugin = container.getPluginInstance(AdaptationPlugin.class);
			
		} catch (AnnotationProcessorException | DuplicateEnsembleDefinitionException e) {
			Log.e("Error while trying to deploy AdaptationManager", e);
		}
	}

	@Override
	public void onStartup() throws PluginStartupFailedException {		
		Set<Component> components = new HashSet<>();
		for(DEECoNode node : nodes){
			for(ComponentInstance ci : node.getRuntimeMetadata().getComponentInstances()){
				components.add(new ComponentImpl(ci));
			}
		}
		CorrelationEnsembleFactory ceFactory = new CorrelationEnsembleFactory();
		ceFactory.withVerbosity(verbose);
		
		ComponentManagerImpl componentManager = new ComponentManagerImpl(components);
		ConnectorManagerImpl connectorManager = new ConnectorManagerImpl(
				ceFactory, nodes);

		CorrelationManager manager = new CorrelationManager(componentManager, connectorManager);
		manager.setVerbosity(verbose);
		manager.setDumpValues(dumpValues);
		
		adaptationPlugin.registerAdaptation(manager);
	}
}
