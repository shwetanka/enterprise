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
package org.neo4j.server.enterprise;

import java.util.Map;

import org.apache.commons.configuration.Configuration;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.kernel.HighlyAvailableGraphDatabase;
import org.neo4j.server.configuration.Configurator;
import org.neo4j.server.database.CommunityDatabase;
import org.neo4j.server.database.GraphDatabaseFactory;

public class EnterpriseDatabase extends CommunityDatabase {

	enum DatabaseMode implements GraphDatabaseFactory
    {
        SINGLE
        {
            @Override
            public GraphDatabaseAPI createDatabase( String databaseStoreDirectory,
                    Map<String, String> databaseProperties )
            {
                return new EmbeddedGraphDatabase( databaseStoreDirectory, databaseProperties );
            }
        },
        HA
        {
            @Override
            public GraphDatabaseAPI createDatabase( String databaseStoreDirectory,
                    Map<String, String> databaseProperties )
            {
                return new HighlyAvailableGraphDatabase( databaseStoreDirectory, databaseProperties );
            }
        };

        @Override
        public abstract GraphDatabaseAPI createDatabase( String databaseStoreDirectory,
                Map<String, String> databaseProperties );
    }
	
	public EnterpriseDatabase(Configuration serverConfig) {
		super(serverConfig);
	}
	
	@Override
	@SuppressWarnings("deprecation")
	public void start() throws Throwable
	{
		try
        {
			GraphDatabaseFactory factory = DatabaseMode.valueOf( serverConfig.getString( Configurator.DB_MODE_KEY, DatabaseMode.SINGLE.name() ).toUpperCase() );
			
			this.graph = factory.createDatabase(
					serverConfig.getString(Configurator.DATABASE_LOCATION_PROPERTY_KEY, Configurator.DEFAULT_DATABASE_LOCATION_PROPERTY_KEY), 
					loadNeo4jProperties());
			
            log.info( "Successfully started database" );
        } catch(Exception e)
        {
            log.error( "Failed to start database.", e);
            throw e;
        }
	}

}
