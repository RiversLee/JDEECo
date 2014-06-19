package cz.cuni.mff.d3s.deeco.simulation;

import java.util.Collection;
import java.util.Random;

import cz.cuni.mff.d3s.deeco.DeecoProperties;
import cz.cuni.mff.d3s.deeco.executor.Executor;
import cz.cuni.mff.d3s.deeco.executor.SameThreadExecutor;
import cz.cuni.mff.d3s.deeco.knowledge.KnowledgeManagerContainer;
import cz.cuni.mff.d3s.deeco.model.runtime.api.RuntimeMetadata;
import cz.cuni.mff.d3s.deeco.model.runtime.custom.TimeTriggerExt;
import cz.cuni.mff.d3s.deeco.network.DirectGossipStrategy;
import cz.cuni.mff.d3s.deeco.network.DirectRecipientSelector;
import cz.cuni.mff.d3s.deeco.network.KnowledgeDataManager;
import cz.cuni.mff.d3s.deeco.network.PublisherTask;
import cz.cuni.mff.d3s.deeco.runtime.RuntimeFramework;
import cz.cuni.mff.d3s.deeco.runtime.RuntimeFrameworkImpl;
import cz.cuni.mff.d3s.deeco.scheduler.Scheduler;
import cz.cuni.mff.d3s.deeco.simulation.scheduler.SimulationScheduler;

public class SimulationRuntimeBuilder {

	public RuntimeFramework build(SimulationHost host, CallbackProvider callbackProvider, RuntimeMetadata model, Collection<DirectRecipientSelector> recipientSelectors, DirectGossipStrategy directGossipStrategy) {
		if (model == null) {
			throw new IllegalArgumentException("Model must not be null");
		}

		// Set up the executor
		Executor executor = new SameThreadExecutor();

		// Set up the simulation scheduler
		Scheduler scheduler = new SimulationScheduler(host, callbackProvider);
		scheduler.setExecutor(executor);

		// Set up the host container
		KnowledgeManagerContainer container = new KnowledgeManagerContainer();

		KnowledgeDataManager kdManager = new KnowledgeDataManager(
				container, 
				host.getPacketSender(), 
				model.getEnsembleDefinitions(), 
				host.getHostId(), 
				scheduler, recipientSelectors, directGossipStrategy);
		
		// Bind KnowledgeDataReceiver with PacketDataReceiver
		host.getPacketReceiver().setKnowledgeDataReceiver(kdManager);
		
		// Set up the publisher task
		TimeTriggerExt publisherTrigger = new TimeTriggerExt();
		publisherTrigger.setPeriod(Integer.getInteger(DeecoProperties.PUBLISHING_PERIOD, PublisherTask.DEFAULT_PUBLISHING_PERIOD));
		long seed = 0;
		for (char c: host.getHostId().toCharArray())
			seed = seed*32+(c-'a');
		Random rnd = new Random(seed);
		publisherTrigger.setOffset(rnd.nextInt((int) publisherTrigger.getPeriod()) + 1);
		PublisherTask publisherTask = new PublisherTask(
				scheduler, 
				kdManager, publisherTrigger,
				host.getHostId());
		
		// Add publisher task to the scheduler
		scheduler.addTask(publisherTask);

		return new RuntimeFrameworkImpl(model, scheduler, executor,
				container);
	}

	

}