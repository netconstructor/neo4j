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
package org.neo4j.consistency.checking;

import org.neo4j.consistency.report.ConsistencyReport;
import org.neo4j.consistency.store.DiffRecordAccess;
import org.neo4j.consistency.store.RecordAccess;
import org.neo4j.consistency.store.RecordReference;
import org.neo4j.kernel.impl.nioneo.store.NeoStoreRecord;
import org.neo4j.kernel.impl.nioneo.store.NodeRecord;
import org.neo4j.kernel.impl.nioneo.store.PrimitiveRecord;
import org.neo4j.kernel.impl.nioneo.store.PropertyRecord;
import org.neo4j.kernel.impl.nioneo.store.Record;
import org.neo4j.kernel.impl.nioneo.store.RelationshipRecord;

// TODO: it would be great if this also checked for cyclic chains. (we would also need cycle checking for full check, and for relationships)
enum OwnerChain
        implements ComparativeRecordChecker<PropertyRecord, PropertyRecord, ConsistencyReport.PropertyConsistencyReport>
{
    OLD
    {
        @Override
        RecordReference<PropertyRecord> property( DiffRecordAccess records, long id )
        {
            return records.previousProperty( id );
        }

        @Override
        RecordReference<NodeRecord> node( DiffRecordAccess records, long id )
        {
            return records.previousNode( id );
        }

        @Override
        RecordReference<RelationshipRecord> relationship( DiffRecordAccess records, long id )
        {
            return records.previousRelationship( id );
        }

        @Override
        RecordReference<NeoStoreRecord> graph( DiffRecordAccess records )
        {
            return records.previousGraph();
        }

        @Override
        void wrongOwner( ConsistencyReport.PropertyConsistencyReport report )
        {
            report.changedForWrongOwner();
        }
    },

    NEW
    {
        @Override
        RecordReference<PropertyRecord> property( DiffRecordAccess records, long id )
        {
            return records.property( id );
        }

        @Override
        RecordReference<NodeRecord> node( DiffRecordAccess records, long id )
        {
            return records.node( id );
        }

        @Override
        RecordReference<RelationshipRecord> relationship( DiffRecordAccess records, long id )
        {
            return records.relationship( id );
        }

        @Override
        RecordReference<NeoStoreRecord> graph( DiffRecordAccess records )
        {
            return records.graph();
        }

        @Override
        void wrongOwner( ConsistencyReport.PropertyConsistencyReport report )
        {
            report.ownerDoesNotReferenceBack();
        }
    };

    private final ComparativeRecordChecker<PropertyRecord, PrimitiveRecord, ConsistencyReport.PropertyConsistencyReport>
            OWNER_CHECK =
            new ComparativeRecordChecker<PropertyRecord, PrimitiveRecord, ConsistencyReport.PropertyConsistencyReport>()
            {
                @Override
                public void checkReference( PropertyRecord record, PrimitiveRecord owner,
                                            ConsistencyReport.PropertyConsistencyReport report, RecordAccess records )
                {
                    if ( !owner.inUse() || Record.NO_NEXT_PROPERTY.is( owner.getNextProp() ) )
                    {
                        wrongOwner( report );
                    }
                    else if ( owner.getNextProp() != record.getId() )
                    {
                        report.forReference( property( (DiffRecordAccess) records, owner.getNextProp() ),
                                             OwnerChain.this );
                    }
                }
            };

    @Override
    public void checkReference( PropertyRecord record, PropertyRecord property,
                                ConsistencyReport.PropertyConsistencyReport report, RecordAccess records )
    {
        if ( record.getId() != property.getId() )
        {
            if ( !property.inUse() || Record.NO_NEXT_PROPERTY.is( property.getNextProp() ) )
            {
                wrongOwner( report );
            }
            else if ( property.getNextProp() != record.getId() )
            {
                report.forReference( property( (DiffRecordAccess) records, property.getNextProp() ), this );
            }
        }
    }

    void check( PropertyRecord record, ConsistencyReport.PropertyConsistencyReport report,
                DiffRecordAccess records )
    {
        report.forReference( ownerOf( record, records ), OWNER_CHECK );
    }

    private RecordReference<? extends PrimitiveRecord> ownerOf( PropertyRecord record, DiffRecordAccess records )
    {
        if ( record.getNodeId() != -1 )
        {
            return node( records, record.getNodeId() );
        }
        else if ( record.getRelId() != -1 )
        {
            return relationship( records, record.getRelId() );
        }
        else
        {
            return graph( records );
        }
    }

    abstract RecordReference<PropertyRecord> property( DiffRecordAccess records, long id );

    abstract RecordReference<NodeRecord> node( DiffRecordAccess records, long id );

    abstract RecordReference<RelationshipRecord> relationship( DiffRecordAccess records, long id );

    abstract RecordReference<NeoStoreRecord> graph( DiffRecordAccess records );

    abstract void wrongOwner( ConsistencyReport.PropertyConsistencyReport report );
}
