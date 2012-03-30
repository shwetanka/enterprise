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

package org.neo4j.kernel.ha2.protocol.paxos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TODO
 */
public class PaxosContext
{
    List<String> acceptors = new ArrayList<String>();
    List<String> learners = new ArrayList<String>();
    List<String> proposers = new ArrayList<String>();
    
    String coordinator;
    String electedLearner;
    
    Map<String,Object> learnedValues = new HashMap<String, Object>(  );

    public List<String> getAcceptors()
    {
        return acceptors;
    }

    public List<String> getLearners()
    {
        return learners;
    }

    public List<String> getProposers()
    {
        return proposers;
    }
    
    public Object getLearnedValue(String key)
    {
        return learnedValues.get( key );
    }

    public boolean knowsValue( String key )
    {
        return learnedValues.containsKey( key );
    }
}
