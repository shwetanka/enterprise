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
package org.neo4j.kernel.ha;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jboss.netty.channel.Channel;
import org.neo4j.com.Protocol;
import org.neo4j.com.RequestContext;
import org.neo4j.com.RequestType;
import org.neo4j.com.Server;
import org.neo4j.com.TxChecksumVerifier;
import org.neo4j.kernel.impl.util.StringLogger;

/**
 * Sits on the master side, receiving serialized requests from slaves (via
 * {@link MasterClient}). Delegates actual work to {@link MasterImpl}.
 */
public class MasterServer extends Server<Master, Void>
{
    public static final int FRAME_LENGTH = Protocol.DEFAULT_FRAME_LENGTH;

    public MasterServer( Master requestTarget, final int port, StringLogger logger, int maxConcurrentTransactions,
            int oldChannelThreshold, TxChecksumVerifier txVerifier )
    {
        super( requestTarget, port, logger, FRAME_LENGTH, MasterClient18.PROTOCOL_VERSION, maxConcurrentTransactions,
                oldChannelThreshold, txVerifier );
    }

    @Override
    protected RequestType<Master> getRequestContext( byte id )
    {
        return HaRequestType18.values()[id];
    }

    @Override
    protected void finishOffChannel( Channel channel, RequestContext context )
    {
        getRequestTarget().finishTransaction( context, false );
    }

    @Override
    public void shutdown()
    {
        getRequestTarget().shutdown();
        super.shutdown();
    }

    @Override
    protected boolean shouldLogFailureToFinishOffChannel( Throwable failure )
    {
        return !( failure instanceof UnableToResumeTransactionException );
    }

    public Map<Integer, Collection<RequestContext>> getSlaveInformation()
    {
        // Which slaves are connected a.t.m?
        Set<Integer> machineIds = new HashSet<Integer>();
        Map<Channel, RequestContext> channels = getConnectedSlaveChannels();
        synchronized ( channels )
        {
            for ( RequestContext context : channels.values() )
            {
                machineIds.add( context.machineId() );
            }
        }

        // Insert missing slaves into the map so that all connected slave
        // are in the returned map
        Map<Integer, Collection<RequestContext>> ongoingTransactions =
                ((MasterImpl) getRequestTarget()).getOngoingTransactions();
        for ( Integer machineId : machineIds )
        {
            if ( !ongoingTransactions.containsKey( machineId ) )
            {
                ongoingTransactions.put( machineId, Collections.<RequestContext>emptyList() );
            }
        }
        return new TreeMap<Integer, Collection<RequestContext>>( ongoingTransactions );
    }
}
