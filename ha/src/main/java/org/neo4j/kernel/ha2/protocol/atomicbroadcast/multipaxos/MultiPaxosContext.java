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

package org.neo4j.kernel.ha2.protocol.atomicbroadcast.multipaxos;

import java.net.URI;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.neo4j.kernel.ha2.protocol.cluster.ClusterContext;
import org.neo4j.kernel.ha2.timeout.Timeouts;

/**
 * Context shared by all Paxos state machines.
 */
public class MultiPaxosContext
{
    ClusterContext clusterContext;
    ProposerContext proposerContext;
    LearnerContext learnerContext;
    Timeouts timeouts;

    PaxosInstanceStore paxosInstances = new PaxosInstanceStore();

    public MultiPaxosContext(ClusterContext clusterContext, ProposerContext proposerContext, LearnerContext learnerContext, Timeouts timeouts)
    {
        this.clusterContext = clusterContext;
        this.proposerContext = proposerContext;
        this.learnerContext = learnerContext;
        this.timeouts = timeouts;
    }

    public int getServerId()
    {
        int i = clusterContext.getConfiguration().getNodes().indexOf( clusterContext.getMe() );
        if (i == -1)
            i = 99;

        return i;
    }

    public List<URI> getAcceptors()
    {
        return clusterContext.getConfiguration().getNodes();
    }

    public Iterable<URI> getLearners()
    {
        return clusterContext.getConfiguration().getNodes();
    }

    public URI getCoordinator()
    {
        return clusterContext.getConfiguration().getCoordinator();
    }

    public PaxosInstanceStore getPaxosInstances()
    {
        return paxosInstances;
    }

    public int getMinimumQuorumSize( List<URI> acceptors )
    {
        // n >= 2f+1
        if (acceptors.size() >= 2*clusterContext.getConfiguration().getAllowedFailures() + 1)
            return acceptors.size()-clusterContext.getConfiguration().getAllowedFailures();
        else
            return acceptors.size();
    }
}
