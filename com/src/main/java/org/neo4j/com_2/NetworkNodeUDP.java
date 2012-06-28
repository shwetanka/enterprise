/**
 * Copyright (c) 2002-2012 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.neo4j.com_2;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.FixedReceiveBufferSizePredictorFactory;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.jboss.netty.handler.codec.serialization.ObjectDecoder;
import org.jboss.netty.handler.codec.serialization.ObjectEncoder;
import org.jboss.netty.handler.logging.LoggingHandler;
import org.neo4j.com_2.message.Message;
import org.neo4j.com_2.message.MessageProcessor;
import org.neo4j.com_2.message.MessageSource;
import org.neo4j.com_2.message.MessageType;
import org.neo4j.graphdb.factory.GraphDatabaseSetting;
import org.neo4j.helpers.Listeners;
import org.neo4j.kernel.impl.util.StringLogger;
import org.neo4j.kernel.lifecycle.Lifecycle;

/**
 * TODO
 */
public class NetworkNodeUDP
    implements MessageProcessor, MessageSource, Lifecycle
{
    public static final GraphDatabaseSetting.PortSetting cluster_port = new GraphDatabaseSetting.PortSetting( "ha.cluster_port" );
    public static final GraphDatabaseSetting.StringSetting cluster_address = new GraphDatabaseSetting.StringSetting( "ha.cluster_address", GraphDatabaseSetting.ANY, "Must be a valid hostname" );
    protected ConnectionlessBootstrap bootstrap;

    public interface Configuration
    {
        String address(String def);
        int[] port(int[] defaultPortRange, int min, int max);
    }

    public interface ChannelFactory
    {
        Channel openChannel(URI uri);
    }
    
    public interface NetworkChannelsListener
    {
        void listeningAt(URI me);
        void channelOpened(URI to);
        void channelClosed(URI to);
    }
    
    // Receiving
    private ExecutorService executor;
    private Channel channel;
    private Iterable<MessageProcessor> processors = Listeners.newListeners();

    private Map<String,String> config;
    private StringLogger msgLog;
    private URI me;

    private Map<URI, Channel> connections = new ConcurrentHashMap<URI, Channel>();
    private Iterable<NetworkChannelsListener> listeners = Listeners.newListeners();

    ChannelGroup channelGroup = new DefaultChannelGroup();

    public NetworkNodeUDP( Map<String, String> config, StringLogger msgLog )
    {
        this.config = config;
        this.msgLog = msgLog;
    }

    @Override
    public void init()
        throws Throwable
    {
        executor = Executors.newCachedThreadPool();
        DatagramChannelFactory f = new NioDatagramChannelFactory( executor );

        bootstrap = new ConnectionlessBootstrap(f);

        // Configure the pipeline factory.
        bootstrap.setPipelineFactory( new NetworkNodePipelineFactory() );

        // Enable broadcast
        bootstrap.setOption( "broadcast", "true" );

        bootstrap.setOption( "receiveBufferSizePredictorFactory", new FixedReceiveBufferSizePredictorFactory( 1024 ) );

        int[] ports = cluster_port.getPorts( config );
        
        int minPort = ports[0];
        int maxPort = ports.length == 2 ? ports[1] : minPort;
        
        // Try all ports in the given range
        listen( minPort, maxPort );
    }

    private void listen( int minPort, int maxPort )
        throws URISyntaxException, ChannelException
    {
        ChannelException ex = null;
        for(int checkPort = minPort; checkPort <= maxPort; checkPort++)
        {
            try
            {
                channel = bootstrap.bind(new InetSocketAddress("localhost", checkPort));

                listeningAt( ( getURI( (InetSocketAddress) channel.getLocalAddress() ) ) );

                channelGroup = new DefaultChannelGroup();
                channelGroup.add(channel);
                return;
            }
            catch ( ChannelException e )
            {
                ex = e;
            }
        }

        executor.shutdown();
        throw ex;
    }

    @Override
    public void start()
        throws Throwable
    {
    }

    @Override
    public void stop()
        throws Throwable
    {
    }

    @Override
    public void shutdown()
        throws Throwable
    {
        channel.close();

        channelGroup.close();
    }

    // MessageSource implementation
    public void addMessageProcessor( MessageProcessor processor )
    {
        processors = Listeners.addListener( processor, processors);
    }

    public void receive( Message message )
    {
        for( MessageProcessor listener : processors )
        {
            try
            {
                listener.process( message );
            }
            catch( Exception e )
            {
                // Ignore
                e.printStackTrace(  );
            }
        }
    }

    // MessageProcessor implementation
    @Override
    public void process( Message<? extends MessageType> message )
    {
        if (message.hasHeader(Message.TO))
        {
            String to = message.getHeader( Message.TO );

            if (to.equals( Message.BROADCAST ))
            {
                broadcast( message );
            } else if (to.equals( me.toString() ))
            {
                receive( message );
            }else
            {
                send( message );
            }
        } else
        {
            // Internal message
            receive(message);
        }
    }
    
    
    private URI getURI(InetSocketAddress address) throws URISyntaxException
    {
        return new URI("neo4j:/" + address);
    }
    
    public void listeningAt( final URI me)
    {
        this.me = me;

        Listeners.notifyListeners( listeners, new Listeners.Notification<NetworkChannelsListener>()
        {
            @Override
            public void notify( NetworkChannelsListener listener )
            {
                listener.listeningAt( me );
            }
        } );
    }

    private void broadcast(Message message)
    {
        for (int i = 1234; i < 1234+2; i++)
        {
            String to = "neo4j://127.0.0.1:"+i;

            if (!to.equals(me.toString()))
            {
                message.setHeader( Message.TO, to );
                send( message );
            }
        }
    }

    private void send(Message message)
    {
        URI to = null;
        try
        {
            to = new URI( message.getHeader( Message.TO ) );
        }
        catch( URISyntaxException e )
        {
            msgLog.logMessage("Not valid URI:" + message.getHeader( Message.TO ), true);
        }

        try
        {
            msgLog.logMessage("Sending to "+to+": "+message, true);
            channel.write( message, new InetSocketAddress( to.getHost(), to.getPort() ) );
        } catch (Exception e)
        {
            e.printStackTrace();
            channel.close();
            closedChannel(to);
        }
    }

    protected void openedChannel( final URI uri, Channel ctxChannel)
    {
        connections.put(uri, ctxChannel);

        Listeners.notifyListeners( listeners, new Listeners.Notification<NetworkChannelsListener>()
        {
            @Override
            public void notify( NetworkChannelsListener listener )
            {
                listener.channelOpened( uri );
            }
        } );
    }

    protected void closedChannel( final URI uri)
    {
        Channel channel = connections.remove(uri);
        if (channel != null)
            channel.close();

        Listeners.notifyListeners( listeners, new Listeners.Notification<NetworkChannelsListener>()
        {
            @Override
            public void notify( NetworkChannelsListener listener )
            {
                listener.channelClosed( uri );
            }
        } );
    }

    public URI getMe()
    {
        return me;
    }

    public Channel getChannel(URI uri)
    {
        return connections.get(uri);
    }
    
    public void addNetworkChannelsListener(NetworkChannelsListener listener)
    {
        listeners = Listeners.addListener( listener, listeners );
    }

    public void removeNetworkChannelsListener(NetworkChannelsListener listener)
    {
        listeners = Listeners.removeListener( listener, listeners );
    }
    private class NetworkNodePipelineFactory
        implements ChannelPipelineFactory
    {
        @Override
        public ChannelPipeline getPipeline() throws Exception
        {
            ChannelPipeline pipeline = Channels.pipeline();
            pipeline.addFirst("log", new LoggingHandler());
            addSerialization(pipeline, 1024 * 1000);
            pipeline.addLast( "serverHandler", new MessageReceiver() );
            return pipeline;
        }

        private void addSerialization(ChannelPipeline pipeline, int frameLength)
        {
            pipeline.addLast( "frameDecoder",
                    new ObjectDecoder(frameLength, NetworkNodePipelineFactory.this.getClass().getClassLoader() ) );
            pipeline.addLast( "frameEncoder", new ObjectEncoder(1024 * 1000));
        }
    }
    
    private class MessageReceiver
            extends SimpleChannelHandler
    {
        @Override
        public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception
        {
            Channel ctxChannel = ctx.getChannel();
            openedChannel( getURI( (InetSocketAddress) ctxChannel.getRemoteAddress() ), ctxChannel );
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent event) throws Exception
        {
            final Message message = (Message) event.getMessage();
            msgLog.logMessage("Received:" + message, true);
            executor.submit( new Runnable()
            {
                @Override
                public void run()
                {
                    receive( message );
                }
            } );
        }

        @Override
        public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception
        {
            closedChannel( getURI( (InetSocketAddress) ctx.getChannel().getRemoteAddress() ) );
        }

        @Override
        public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception
        {
            closedChannel( getURI( (InetSocketAddress) ctx.getChannel().getRemoteAddress() ) );
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception
        {
            msgLog.logMessage("Receive exception:", e.getCause());
        }
    }
}
