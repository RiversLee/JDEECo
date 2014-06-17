package cz.cuni.mff.d3s.deeco.simulation.scheduler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import cz.cuni.mff.d3s.deeco.executor.Executor;
import cz.cuni.mff.d3s.deeco.logging.Log;
import cz.cuni.mff.d3s.deeco.model.runtime.api.Trigger;
import cz.cuni.mff.d3s.deeco.scheduler.Scheduler;
import cz.cuni.mff.d3s.deeco.scheduler.SchedulerEvent;
import cz.cuni.mff.d3s.deeco.simulation.CallbackProvider;
import cz.cuni.mff.d3s.deeco.simulation.SimulationHost;
import cz.cuni.mff.d3s.deeco.simulation.SimulationTimeEventListener;
import cz.cuni.mff.d3s.deeco.task.Task;
import cz.cuni.mff.d3s.deeco.task.TaskTriggerListener;

/**
 * The {@link Scheduler} implementation designed for single threaded simulation.
 * This scheduler is suppose to be driven by the simulation through
 * {@link SimulationTimeEventListener} methods.
 * 
 * @author Michal Kit <kit@d3s.mff.cuni.cz>
 * 
 */
public class SimulationScheduler implements Scheduler,
		SimulationTimeEventListener {

	private final SimulationHost host;
	private final CallbackProvider callbackProvider;
	
	private final SortedSet<SchedulerEvent> queue;
	private final Set<Task> allTasks;
	private final Map<Task, SchedulerEvent> periodicEvents;
	
	private final Set<Trigger> onTriggerSchedules;

	private Executor executor;
	
	private Random rnd;

	public SimulationScheduler(SimulationHost host, CallbackProvider callbackProvider) {
		this.host = host;
		this.callbackProvider = callbackProvider;
		queue = new TreeSet<SchedulerEvent>();
		allTasks = new HashSet<>();
		periodicEvents = new HashMap<>();	
		onTriggerSchedules = new HashSet<>();
		
		long seed = 0;
		for (char c: host.getHostId().toCharArray())
			seed = seed*32+(c-'a');
		rnd = new Random(seed);
		
		host.setSimulationTimeEventListener(this);
	}

	@Override
	public void start() {
		registerNextExecution();
	}

	@Override
	public void stop() {
		Log.d("The simulation scheduler is stopped together with the simulation.");
	}

	@Override
	public void addTask(Task task) {
		if (task == null)
			throw new IllegalArgumentException("The task cannot be null");
		if (allTasks.contains(task))
			return;
		if (task.getTimeTrigger() != null) {
			SchedulerEvent event = new SchedulerEvent(task,
					task.getTimeTrigger());
			
			// for experiments, publisher task has a random start offset up to its period
			if (event.periodic) {
				int offset = rnd.nextInt((int) (task.getTimeTrigger().getPeriod() + 1));
				task.getTimeTrigger().setOffset(offset);
				Log.d(String.format("Scheduler init: Periodic task %s offset %d",task.getClass().toString(), offset));
			}
				
			scheduleAfter(event, task.getTimeTrigger().getOffset());

			periodicEvents.put(task, event);
		}
		task.setTriggerListener(new TaskTriggerListener() {
			@Override
			public void triggered(Task task, Trigger trigger) {				
				if (allTasks.contains(task)) {
					// if the trigger has been already scheduled (i.e., there have
					// been many consecutive invocations of that trigger in a row),
					// then skip this event
					if (onTriggerSchedules.contains(trigger))
						return;
					onTriggerSchedules.add(trigger);
					// schedule immediately, regardless the actual runtime of
					// the process that triggered this trigger					
					scheduleAfter(new SchedulerEvent(task, trigger), 0);
				}
			}
		});
		
		allTasks.add(task);

	}

	@Override
	public void removeTask(Task task) {
		if (!allTasks.contains(task))
			return;
		task.unsetTriggerListener();
		List<SchedulerEvent> remove = new LinkedList<>();
		for (SchedulerEvent se : queue) {
			if (se.executable.equals(task)) {
				remove.add(se);
			}
		}
		queue.removeAll(remove);
		periodicEvents.remove(task);
		allTasks.remove(task);
	}

	@Override
	public void executionFailed(Task task, Trigger trigger, Exception e) {
		Log.e(e.getMessage());
		executionCompleted(task, trigger);
	}

	@Override
	public void executionCompleted(Task task, Trigger trigger) {
		onTriggerSchedules.remove(trigger);
		registerNextExecution();
	}

	@Override
	public void setExecutor(Executor executor) {
		this.executor = executor;
		if (this.executor != null)
			this.executor.setExecutionListener(this);
	}

	@Override
	public void invokeAndWait(Runnable doRun) throws InterruptedException {
		Log.e("Simulation scheduler is being used! Invoking an external task is not allowed.");
	}

	@Override
	public void at(long time) {
		//System.out.println("Scheduler " +host.getId()+" at: "+time+" called with queue: " + Arrays.toString(queue.toArray()));
		SchedulerEvent event;
		while ((event = queue.first()) != null)  {
			// if notified too early (or already processed all events scheduled
			// for the current time)
			if (event.nextExecutionTime > time)
				break;
			
			// The time is right to execute the next task			
			pop();
			if (event.periodic) {
				// schedule for the next period 
				// add a random offset within the period (up to 75% of the period)
				event.nextPeriodStart += event.executable.getTimeTrigger().getPeriod();
				
				int offset = rnd.nextInt((int) (event.executable.getTimeTrigger().getPeriod()*0.75));
				event.nextExecutionTime = event.nextPeriodStart + offset;
				push(event);
			}
			if (executor != null) {
				executor.execute(event.executable, event.trigger);
			} else {
				Log.e("The simulation scheduler is associated with no excecutor!");
			}			
		}
	}
	
	/* (non-Javadoc)
	 * @see cz.cuni.mff.d3s.deeco.scheduler.CurrentTimeProvider#getCurrentTime()
	 */
	@Override
	public long getCurrentMilliseconds() {
		return host.getCurrentMilliseconds();
	}

	// ------Private methods--------

//	private void scheduleNow(SchedulerEvent event, long period) {
//		event.period = period;
//		event.nextExecutionTime = host.getCurrentTime() + lastProcessExecutionTime;
//		push(event);
//	}
	
	/**
	 * Note that this method has to be explicitly protected by queue's monitor!
	 */
	void scheduleAfter(SchedulerEvent event, long delay) {			
		event.nextExecutionTime = host.getCurrentMilliseconds() + delay;
		event.nextPeriodStart = host.getCurrentMilliseconds() + delay;
		push(event);
					
	}

	private void registerNextExecution() {
		if (!queue.isEmpty()) {
			long nextExecutionTime = queue.first().nextExecutionTime;
			if (nextExecutionTime <= host.getCurrentMilliseconds()) {
				return; //nextExecutionTime = host.getSimulationTime() + 1;
			}
			callbackProvider.callAt(nextExecutionTime, host.getHostId());
			//System.out.println("Scheduler " + host.getId() + " registering callback at " + nextExecutionTime);
		}
	}

	private SchedulerEvent pop() {
		if (queue.isEmpty())
			return null;
		else {
			SchedulerEvent result = queue.first();
			queue.remove(result);
			return result;
		}
	}

	private void push(SchedulerEvent event) {
		//Log.d("Adding: " + event);

		queue.add(event);
		//Log.d("Queue: " + Arrays.toString(queue.toArray()));

		// TODO take into account different scheduling policies and WCET of
		// tasks. According to those the tasks need to be rescheduled.
	}
}
