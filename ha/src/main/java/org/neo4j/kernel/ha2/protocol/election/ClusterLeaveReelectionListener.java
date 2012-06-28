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

package org.neo4j.kernel.ha2.protocol.election;

import java.net.URI;

import org.neo4j.kernel.ha2.protocol.cluster.ClusterConfiguration;
import org.neo4j.kernel.ha2.protocol.cluster.ClusterListener;

/**
 * TODO
 */
public class ClusterLeaveReelectionListener
    implements ClusterListener
{
    private final Election election;

    public ClusterLeaveReelectionListener( Election election )
    {
        this.election = election;
    }

    @Override
    public void enteredCluster( ClusterConfiguration clusterConfiguration )
    {
    }

    @Override
    public void joinedCluster( URI node )
    {
    }

    @Override
    public void leftCluster( URI node )
    {
        // Suggest reelection for all roles of this node
        election.demote( node );
    }

    @Override
    public void leftCluster()
    {
    }

    @Override
    public void elected( String role, URI electedNode )
    {
    }
}
