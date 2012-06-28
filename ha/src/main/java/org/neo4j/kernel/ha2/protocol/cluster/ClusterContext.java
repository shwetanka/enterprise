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

package org.neo4j.kernel.ha2.protocol.cluster;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.neo4j.helpers.Listeners;
import org.neo4j.kernel.ha2.protocol.atomicbroadcast.multipaxos.LearnerContext;
import org.neo4j.kernel.ha2.protocol.atomicbroadcast.multipaxos.ProposerContext;
import org.neo4j.kernel.ha2.timeout.Timeouts;

/**
 *
 * Context for cluster API state machine
 */
public class ClusterContext
{
    URI me;
    Iterable<ClusterListener> listeners = Listeners.newListeners();
    ProposerContext proposerContext;
    LearnerContext learnerContext;
    public ClusterConfiguration configuration;
    public final Timeouts timeouts;

    public ClusterContext( ProposerContext proposerContext,
                           LearnerContext learnerContext,
                           ClusterConfiguration configuration,
                           Timeouts timeouts
    )
    {
        this.proposerContext = proposerContext;
        this.learnerContext = learnerContext;
        this.configuration = configuration;
        this.timeouts = timeouts;
    }

    // Cluster API
    public void addClusterListener(ClusterListener listener)
    {
        listeners = Listeners.addListener( listener, listeners );
    }

    public void removeClusterListener(ClusterListener listener)
    {
        listeners = Listeners.removeListener(listener, listeners);
    }

    // Implementation
    public void created( String name )
    {
        configuration = new ClusterConfiguration( name, Collections.singleton(me) );
        Listeners.notifyListeners( listeners, new Listeners.Notification<org.neo4j.kernel.ha2.protocol.cluster.ClusterListener>()
        {
            @Override
            public void notify( ClusterListener listener )
            {
                listener.enteredCluster( configuration );
            }
        } );
    }

    public void acquiredConfiguration( final List<URI> nodeList, final Map<String,URI> roles )
    {
        configuration.setNodes( nodeList );
        configuration.setRoles( roles );
    }

    public void joined()
    {
        configuration.joined( me );
        Listeners.notifyListeners( listeners, new Listeners.Notification<org.neo4j.kernel.ha2.protocol.cluster.ClusterListener>()
        {
            @Override
            public void notify( ClusterListener listener )
            {
                listener.enteredCluster( configuration );
            }
        } );
    }

    public void left()
    {
        configuration.left();
        Listeners.notifyListeners( listeners, new Listeners.Notification<org.neo4j.kernel.ha2.protocol.cluster.ClusterListener>()
        {
            @Override
            public void notify( ClusterListener listener )
            {
                listener.leftCluster();
            }
        } );
    }

    public void joined( final URI node)
    {
        configuration.joined( node );
        Listeners.notifyListeners( listeners, new Listeners.Notification<org.neo4j.kernel.ha2.protocol.cluster.ClusterListener>()
        {
            @Override
            public void notify( ClusterListener listener )
            {
                listener.joinedCluster( node );
            }
        } );
    }

    public void left( final URI node)
    {
        configuration.left(node);
        Listeners.notifyListeners( listeners, new Listeners.Notification<org.neo4j.kernel.ha2.protocol.cluster.ClusterListener>()
        {
            @Override
            public void notify( ClusterListener listener )
            {
                listener.leftCluster( node );
            }
        } );
    }

    public void elected( final String name, final URI node )
    {
        if (!node.equals(configuration.getElected( name )))
        {
            configuration.elected( name, node );
            Listeners.notifyListeners( listeners, new Listeners.Notification<org.neo4j.kernel.ha2.protocol.cluster.ClusterListener>()
            {
                @Override
                public void notify( ClusterListener listener )
                {
                    listener.elected( name, node );
                }
            } );
        }
    }

    public void setMe(URI me)
    {
        this.me = me;
    }

    public URI getMe()
    {
        return me;
    }

    public ClusterConfiguration getConfiguration()
    {
        return configuration;
    }

    public boolean isMe( URI server )
    {
        return me.equals( server );
    }

    public boolean isElectedAs( String roleName )
    {
        return me.equals( configuration.getElected( roleName ));
    }

    public boolean isInCluster()
    {
        return !configuration.getNodes().isEmpty();
    }
}
