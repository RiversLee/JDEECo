package cz.cuni.mff.d3s.jdeeco.ros;

import geometry_msgs.Point;
import geometry_msgs.Pose;
import geometry_msgs.PoseStamped;
import geometry_msgs.PoseWithCovariance;
import geometry_msgs.PoseWithCovarianceStamped;
import nav_msgs.Odometry;

import org.ros.concurrent.CancellableLoop;
import org.ros.message.MessageListener;
import org.ros.message.Time;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.Subscriber;

import sensor_msgs.NavSatFix;
import sensor_msgs.TimeReference;
import tf2_msgs.TFMessage;

/**
 * Provides methods for obtaining sensed values passed through ROS. Subscription
 * to the appropriate ROS topics is handled in the
 * {@link #subscribe(ConnectedNode)} method.
 * 
 * @author Dominik Skoda <skoda@d3s.mff.cuni.cz>
 */
public class Position extends TopicSubscriber {
	// TODO: document rosjava types that are seen by the user of this interface

	/**
	 * A switch that guards the subscription to "tf" (transformations) topic. If
	 * true then the {@link Position} object is subscribed to the "tf" topic.
	 */
	private static final boolean ENABLE_TF_LISTENER = false;

	/**
	 * The name of the topic for odometry resets.
	 */
	private static final String RESET_ODOMETRY_TOPIC = "/mobile_base/commands/reset_odometry";
	/**
	 * The name of the odometry topic.
	 */
	private static final String ODOMETRY_TOPIC = "/odom";
	/**
	 * The name of the GPS position topic.
	 */
	private static final String GPS_POSITION_TOPIC = "/gps/position";
	/**
	 * The name of the GPS time topic.
	 */
	private static final String GPS_TIME_TOPIC = "/gps/time";
	/**
	 * The name of the <a href="http://wiki.ros.org/amcl">AMCL</a> positioning
	 * topic.
	 */
	private static final String AMCL_POSITION_TOPIC = "/amcl_pose";
	/**
	 * The name of the topic for simple goals for base movement.
	 */
	private static final String SIMPLE_GOAL_TOPIC = "/move_base_simple/goal";
	/**
	 * The frame in which the goal coordinates are given.
	 */
	private static final String MAP_FRAME = "map";
	/**
	 * The name of the transformation topic.
	 */
	private static final String TRANSFORMATION_TOPIC = "/tf";

	/**
	 * The last position received in the odometry topic.
	 */
	private Point odometry;
	/**
	 * The lock to wait and notify on when a odometry reset is requested.
	 */
	private final Object resetOdometryLock;

	/**
	 * The last received position measured by GPS.
	 */
	private NavSatFix gpsPosition;
	/**
	 * The last time measured by the GPS sensor.
	 */
	private Time gpsTime;
	/**
	 * The most probable position of the robot based on the
	 * <a href="http://wiki.ros.org/amcl">AMCL</a> positioning algorithm.
	 */
	private PoseWithCovariance amclPosition;
	/**
	 * The target position for the robot.
	 */
	private Pose simpleGoal;
	/**
	 * The lock to wait and notify on when a simple goal is set.
	 */
	private final Object simpleGoalLock;

	/**
	 * Internal constructor enables the {@link RosServices} to be in the control
	 * of instantiating {@link Position}.
	 */
	Position() {
		resetOdometryLock = new Object();
		simpleGoalLock = new Object();
	}

	/**
	 * Register and subscribe to required ROS topics of sensor readings.
	 * 
	 * @param connectedNode
	 *            The ROS node on which the DEECo node runs.
	 */
	@Override
	protected void subscribeDescendant(ConnectedNode connectedNode) {
		subscribeOdometry(connectedNode);
		subscribeGPS(connectedNode);
		subscribeAMCL(connectedNode);
		subscribeSimpleGoal(connectedNode);

		if (ENABLE_TF_LISTENER) {
			subscribeTF(connectedNode);
		}
	}

	/**
	 * Subscribe to the ROS odometry topic and for the odometry reset messages.
	 * 
	 * @param connectedNode
	 *            The ROS node on which the DEECo node runs.
	 */
	private void subscribeOdometry(ConnectedNode connectedNode) {
		// Subscribe to listen on odometry messages
		Subscriber<Odometry> odometryTopic = connectedNode.newSubscriber(
				ODOMETRY_TOPIC, Odometry._TYPE);
		odometryTopic.addMessageListener(new MessageListener<Odometry>() {
			@Override
			public void onNewMessage(Odometry message) {
				odometry = message.getPose().getPose().getPosition();
				// TODO: logging
			}
		});

		// Subscribe to publish odometry reset messages
		Publisher<std_msgs.Empty> resetOdometryTopic = connectedNode
				.newPublisher(RESET_ODOMETRY_TOPIC, std_msgs.Empty._TYPE);
		connectedNode.executeCancellableLoop(new CancellableLoop() {
			@Override
			protected void setup() {
			}

			@Override
			protected void loop() throws InterruptedException {
				synchronized (resetOdometryLock) {
					resetOdometryLock.wait();
				}

				std_msgs.Empty emptyMsg = resetOdometryTopic.newMessage();
				resetOdometryTopic.publish(emptyMsg);
				// TODO: log
			}
		});
	}

	/**
	 * Subscribe for the GPS messages.
	 * 
	 * @param connectedNode
	 *            The ROS node on which the DEECo node runs.
	 */
	private void subscribeGPS(ConnectedNode connectedNode) {
		// Subscribe to the GPS position topic
		Subscriber<NavSatFix> navSatTopic = connectedNode.newSubscriber(
				GPS_POSITION_TOPIC, NavSatFix._TYPE);
		navSatTopic.addMessageListener(new MessageListener<NavSatFix>() {
			@Override
			public void onNewMessage(NavSatFix message) {
				gpsPosition = message;
			}
		});

		// Subscribe to the GPS time topic
		Subscriber<TimeReference> timeRefTopic = connectedNode.newSubscriber(
				GPS_TIME_TOPIC, TimeReference._TYPE);
		timeRefTopic.addMessageListener(new MessageListener<TimeReference>() {
			@Override
			public void onNewMessage(TimeReference message) {
				gpsTime = message.getTimeRef();

			}
		});
	}

	/**
	 * Subscribe for the AMCL positioning messages.
	 * 
	 * @param connectedNode
	 *            The ROS node on which the DEECo node runs.
	 */
	private void subscribeAMCL(ConnectedNode connectedNode) {
		Subscriber<PoseWithCovarianceStamped> amclTopic = connectedNode
				.newSubscriber(AMCL_POSITION_TOPIC,
						PoseWithCovarianceStamped._TYPE);
		amclTopic.addMessageListener(new MessageListener<PoseWithCovarianceStamped>() {
			@Override
			public void onNewMessage(PoseWithCovarianceStamped message) {
				amclPosition = message.getPose();
			}
		});
	}

	/**
	 * Subscribe to the topic for simple goals.
	 * 
	 * @param connectedNode
	 *            The ROS node on which the DEECo node runs.
	 */
	private void subscribeSimpleGoal(ConnectedNode connectedNode) {
		Publisher<PoseStamped> goalTopic = connectedNode.newPublisher(
				SIMPLE_GOAL_TOPIC, PoseStamped._TYPE);
		connectedNode.executeCancellableLoop(new CancellableLoop() {
			@Override
			protected void setup() {
				simpleGoal = goalTopic.newMessage().getPose();
			}

			@Override
			protected void loop() throws InterruptedException {
				synchronized (simpleGoalLock) {
					simpleGoalLock.wait();
				}

				PoseStamped goalMsg = goalTopic.newMessage();
				goalMsg.setPose(simpleGoal);
				goalMsg.getHeader().setFrameId(MAP_FRAME);
				goalMsg.getHeader().setStamp(connectedNode.getCurrentTime());
				goalTopic.publish(goalMsg);
				// TODO: log
			}
		});
	}

	/**
	 * Subscribe for the transformation messages.
	 * 
	 * @param connectedNode
	 *            The ROS node on which the DEECo node runs.
	 */
	private void subscribeTF(ConnectedNode connectedNode) {
		Subscriber<TFMessage> transformTopic = connectedNode.newSubscriber(
				TRANSFORMATION_TOPIC, TFMessage._TYPE);
		transformTopic.addMessageListener(new MessageListener<TFMessage>() {
			@Override
			public void onNewMessage(TFMessage message) {
				// TODO: Build TF tree
			}
		});
	}

	/**
	 * Reset the odometry counter.
	 */
	public void resetOdometry() {
		synchronized (resetOdometryLock) {
			resetOdometryLock.notify();
		}
	}

	/**
	 * The position published in the odometry topic.
	 * 
	 * @return last value of the position published in the odometry topic.
	 */
	public Point getOdometry() {
		return odometry;
	}

	/**
	 * Read the GPS position.
	 * 
	 * @return The position measured by GPS.
	 */
	public NavSatFix getGpsPosition() {
		return gpsPosition;
	}

	/**
	 * Get the time read by the GPS sensor.
	 * 
	 * @return The time measured by GPS.
	 */
	public long getGpsTime() {
		return gpsTime.totalNsecs() * 1000;
	}

	/**
	 * Probabilistic position of the robot computed by the <a
	 * href="http://wiki.ros.org/amcl">AMCL</a> algorithm.
	 * 
	 * @return The probabilistic position of the robot.
	 */
	public PoseWithCovariance getPosition() {
		return amclPosition;
	}

	/**
	 * Set a goal for the "move base" service. After the goal is set the robot
	 * starts to move to reach it. The goal consist of a position and an
	 * orientation. The position is expressed by three cartesian coordinates (X,
	 * Y, Z). The orientation is expressed by quaternion (X, Y, Z, W).
	 * 
	 * @param posX
	 *            The X coordinate for position.
	 * @param posY
	 *            The Y coordinate for position.
	 * @param posZ
	 *            The Z coordinate for position.
	 * @param oriX
	 *            The X coordinate for orientation.
	 * @param oriY
	 *            The Y coordinate for orientation.
	 * @param oriZ
	 *            The Z coordinate for orientation.
	 * @param oriW
	 *            The W coordinate for orientation.
	 */
	public void setSimpleGoal(double posX, double posY, double posZ,
			double oriX, double oriY, double oriZ, double oriW) {
		simpleGoal.getPosition().setX(posX);
		simpleGoal.getPosition().setY(posY);
		simpleGoal.getPosition().setZ(posZ);
		simpleGoal.getOrientation().setX(oriX);
		simpleGoal.getOrientation().setY(oriY);
		simpleGoal.getOrientation().setZ(oriZ);
		simpleGoal.getOrientation().setW(oriW);

		synchronized (simpleGoalLock) {
			simpleGoalLock.notify();
		}
	}

}
