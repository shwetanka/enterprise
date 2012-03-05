/**
 * Copyright (c) 2002-2012 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.neo4j.kernel.ha2.protocol.tokenring;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.Test;
import org.neo4j.com2.NetworkChannels;
import org.neo4j.com2.NetworkMessageListener;
import org.neo4j.com2.NetworkMessageReceiver;
import org.neo4j.com2.NetworkMessageSender;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.kernel.ConfigProxy;
import org.neo4j.kernel.LifeSupport;
import org.neo4j.kernel.Lifecycle;
import org.neo4j.kernel.LifecycleAdapter;
import org.neo4j.kernel.ha2.protocol.RingParticipant;
import org.neo4j.kernel.ha2.statemachine.StateMachine;
import org.neo4j.kernel.ha2.statemachine.StateMachineConversations;
import org.neo4j.kernel.ha2.statemachine.StateMachineProxyFactory;
import org.neo4j.kernel.ha2.statemachine.StateMessage;
import org.neo4j.kernel.ha2.statemachine.StateTransition;
import org.neo4j.kernel.ha2.statemachine.StateTransitionListener;
import org.neo4j.kernel.ha2.statemachine.StateTransitionLogger;
import org.neo4j.kernel.ha2.statemachine.message.BroadcastMessage;
import org.neo4j.kernel.ha2.statemachine.message.ExpectationMessage;
import org.neo4j.kernel.ha2.statemachine.message.Message;
import org.neo4j.kernel.ha2.statemachine.message.MessageType;
import org.neo4j.kernel.ha2.statemachine.message.TargetedMessage;
import org.neo4j.kernel.impl.util.StringLogger;

/**
 * TODO
 */
public class TokenRingNetworkTest
{
    @Test
    public void testSendReceive()
    {
        LifeSupport life = new LifeSupport();
        life.add(new Server());
        life.add(new Server());
        life.start();

        try
        {
            Thread.sleep(10000);
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        life.shutdown();
    }

    private class Server
            implements Lifecycle
    {

        private final LifeSupport life = new LifeSupport();

        @Override
        public void init() throws Throwable
        {
        }

        @Override
        public void start() throws Throwable
        {
            Map<String, String> config = MapUtil.stringMap("port", "1234");

            final NetworkChannels channels = new NetworkChannels();

            final NetworkMessageReceiver receiver = new NetworkMessageReceiver(ConfigProxy.config(config, NetworkMessageReceiver.Configuration.class), StringLogger.SYSTEM, channels);
            final NetworkMessageSender sender = new NetworkMessageSender( StringLogger.SYSTEM, channels );

            life.add(receiver);
            life.add(sender);
            life.add(new LifecycleAdapter()
            {
                @Override
                public void start() throws Throwable
                {
                }
            });
            life.add(new LifecycleAdapter()
            {
                private Map<String, ExpectationFailure> expectations = new ConcurrentHashMap<String, ExpectationFailure>();

                private ScheduledExecutorService expectationScheduler;
                private StateMachine stateMachine;
                private StateMachineConversations conversations;
                private RingParticipant me;

                @Override
                public void init() throws Throwable
                {
                    expectationScheduler = Executors.newScheduledThreadPool( 3 );

                    me = new RingParticipant(channels.getMe().toString());
                    final TokenRingContext context = new TokenRingContext(me);
                    stateMachine = new StateMachine(context, TokenRingMessage.class, TokenRingState.start);
                    conversations = new StateMachineConversations( me.toString(), TokenRingMessage.class );

                    receiver.addMessageListener(new NetworkMessageListener()
                    {
                        @Override
                        public void received(Object message)
                        {
                            StateMessage stateEvent = (StateMessage) message;

                            ExpectationFailure expectationFailure = expectations.remove(stateEvent.getConversationId());
                            if (expectationFailure != null)
                                expectationFailure.cancel();

                            stateMachine.receive(stateEvent);
                        }
                    });

                    stateMachine.addStateTransitionListener(new StateTransitionLogger(me, Logger.getAnonymousLogger()) );
                    stateMachine.addStateTransitionListener(new StateTransitionListener()
                    {
                        public void stateTransition(StateTransition transition)
                        {
                            try
                            {

                                while (!context.getSendQueue().isEmpty())
                                    process(transition.getMessage().getConversationId(), context.getSendQueue().poll());
                            } catch (Throwable throwable)
                            {
                                throwable.printStackTrace();
                            }
                        }

                        private void process(String conversationId, Message message)
                        {
                            MessageType messageType = message.getMessageType();
                            if (messageType.failureMessage() != null)
                            {
                                ExpectationFailure expectationFailure = new ExpectationFailure(conversationId, messageType.failureMessage());
                                expectationScheduler.schedule(expectationFailure, 7, TimeUnit.SECONDS);
                                expectations.put(conversationId, expectationFailure);
                            }

                            if (message instanceof BroadcastMessage)
                            {
                                sender.broadcast( message );
                                return;
                            }

                            if (message instanceof TargetedMessage)
                            {
                                TargetedMessage targetedEvent = (TargetedMessage) message;
                                sender.send( targetedEvent.getTo().getServerId(), message );
                                return;
                            }

                            System.out.println("Unknown payload type:" + message.getClass().getName());
                        }
                    } );
                }

                @Override
                public void start() throws Throwable
                {
                    System.out.println("==== " + me + " starts");
                    new StateMachineProxyFactory( stateMachine, conversations ).newProxy( TokenRing.class ).start();
                }

                @Override
                public void stop() throws Throwable
                {
                }

                @Override
                public void shutdown() throws Throwable
                {
                    expectationScheduler.shutdown();
                }

                class ExpectationFailure
                        implements Runnable
                {
                    private String conversationId;
                    private MessageType messageType;
                    private boolean cancelled = false;

                    public ExpectationFailure(String conversationId, MessageType messageType )
                    {
                        this.conversationId = conversationId;
                        this.messageType = messageType;
                    }

                    public synchronized void cancel()
                    {
                        cancelled = true;
                    }

                    @Override
                    public synchronized void run()
                    {
                        if (!cancelled)
                            stateMachine.receive(new StateMessage(conversationId, new ExpectationMessage( messageType, "Timed out" )));
                    }
                }
            });

            life.start();
        }

        @Override
        public void stop() throws Throwable
        {
            System.out.println("Stop server");
            life.stop();
        }

        @Override
        public void shutdown() throws Throwable
        {
        }
    }
}