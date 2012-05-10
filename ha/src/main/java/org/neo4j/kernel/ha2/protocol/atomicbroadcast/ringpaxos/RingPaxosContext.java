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

package org.neo4j.kernel.ha2.protocol.atomicbroadcast.ringpaxos;

import static org.neo4j.helpers.collection.Iterables.iterable;
import static org.neo4j.helpers.collection.Iterables.toList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.neo4j.helpers.Listeners;
import org.neo4j.helpers.Specifications;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.kernel.ha2.protocol.atomicbroadcast.AtomicBroadcastListener;
import org.neo4j.kernel.ha2.timeout.Timeouts;

/**
 * Context shared by all Ring Paxos state machines.
 */
public class RingPaxosContext
{
    public Timeouts timeouts;

    private String me;
    private List<String> possibleServers = new ArrayList<String>(  );
    private Set<String> failedServers = new HashSet<String>(  );

    Iterable<AtomicBroadcastListener> listeners = Listeners.newListeners();

    List<String> acceptors = new ArrayList<String>();
    List<String> learners = new ArrayList<String>();

    int f = 1; // Number of allowed failures

    String coordinator;

    // Coordinator state
    long c_rnd = 0;
    Object c_val = null;
    Object v = null;
    int c_vid;

    List<CoordinatorMessage.PromiseState> promiseStates = new ArrayList<CoordinatorMessage.PromiseState>( );
    List<CoordinatorMessage.AcceptedState> acceptedStates = new ArrayList<CoordinatorMessage.AcceptedState>(  );

    // Acceptor state
    long rnd = 0;
    long v_rnd = 0;
    Object v_val = null;
    List<String> ring;
    int v_vid;

    public void addPaxosListener(AtomicBroadcastListener listener)
    {
        listeners = Listeners.addListener( listener, listeners );
    }

    public void removePaxosListener(AtomicBroadcastListener listener)
    {
        listeners = Listeners.removeListener( listener, listeners );
    }

    public void setAllowedFailures(int f)
    {
        this.f = f;
    }

    public void setMe( String me )
    {
        this.me = me;
    }

    public String getMe()
    {
        return me;
    }

    public void setPossibleServers( String... serverIds )
    {
        possibleServers.clear();
        possibleServers.addAll( toList( iterable( serverIds ) ) );
    }

    public Iterable<String> getPossibleServers()
    {
        return possibleServers;
    }

    public Iterable<String> getLiveServers()
    {
        return Iterables.filter( Specifications.in( failedServers ), possibleServers );
    }

    public void fail(String serverId)
    {
        failedServers.add( serverId );
    }

    public void recover(String serverId)
    {
        failedServers.remove( serverId );
    }

    public List<String> getAcceptors()
    {
        return acceptors;
    }

    public List<String> getLearners()
    {
        return learners;
    }

    public String getCoordinator()
    {
        return coordinator;
    }

    public int getMinimumQuorumSize()
    {
        return acceptors.size()/2+1;
    }
    
    public void learnValue(  )
    {
        Listeners.notifyListeners( listeners, new Listeners.Notification<AtomicBroadcastListener>()
                {
                    @Override
                    public void notify( AtomicBroadcastListener listener )
                    {
                        listener.receive( v_val );
                    }
                } );
    }

    public boolean isFirst()
    {
        return ring.get( 0 ).equals( me );
    }

    public String getSuccessor()
    {
        return ring.get( ring.indexOf( me )+1 );
    }

    public boolean isNotLast()
    {
        return ring.indexOf( me ) < ring.size()-1;
    }

    public boolean isLastAcceptor()
    {
        return ring.indexOf( me ) == ring.size()-2;
    }
}
