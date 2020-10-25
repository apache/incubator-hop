/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2018 by Hitachi Vantara : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.apache.hop.pipeline.transforms.databaselookup;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.RowSet;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.database.MySQLDatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObjectInterface;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaBinary;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.junit.rules.RestoreHopEngineEnvironment;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.ITransformData;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.databaselookup.readallcache.ReadAllCache;
import org.apache.hop.pipeline.transforms.mock.TransformMockHelper;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Andrey Khayrutdinov
 */
public class DatabaseLookupUTest {
  @ClassRule public static RestoreHopEngineEnvironment env = new RestoreHopEngineEnvironment();

  private static final String BINARY_FIELD = "aBinaryFieldInDb";
  private static final String ID_FIELD = "id";
  private TransformMockHelper<DatabaseLookupMeta, DatabaseLookupData> mockHelper;

  @BeforeClass
  public static void setUpClass() throws Exception {
    HopEnvironment.init();
  }

  @AfterClass
  public static void tearDown() {
    HopEnvironment.reset();
  }

  @Before
  public void setUp() {
    mockHelper = createMockHelper();
  }

  @After
  public void cleanUp() {
    mockHelper.cleanUp();
  }

  @Test
  public void mySqlVariantDbIsLazyConverted() throws Exception {
    DatabaseLookupMeta meta = createDatabaseMeta();
    DatabaseLookupData data = createDatabaseData();
    Database db = createVirtualDb( meta.getDatabaseMeta() );

    DatabaseLookup lookup = spyLookup( mockHelper, db, meta.getDatabaseMeta() );

    lookup.init();
    lookup.init();

    verify( db ).getLookup( any( PreparedStatement.class ), anyBoolean(), eq( false ) );
  }


  private TransformMockHelper<DatabaseLookupMeta, DatabaseLookupData> createMockHelper() {
    TransformMockHelper<DatabaseLookupMeta, DatabaseLookupData> mockHelper =
      new TransformMockHelper<DatabaseLookupMeta, DatabaseLookupData>( "test DatabaseLookup", DatabaseLookupMeta.class,
        DatabaseLookupData.class );
    when( mockHelper.logChannelFactory.create( any(), any( LoggingObjectInterface.class ) ) )
      .thenReturn( mockHelper.logChannelInterface );
    when( mockHelper.pipeline.isRunning() ).thenReturn( true );

    RowMeta inputRowMeta = new RowMeta();
    RowSet rowSet = mock( RowSet.class );
    when( rowSet.getRowWait( anyLong(), any( TimeUnit.class ) ) ).thenReturn( new Object[ 0 ] ).thenReturn( null );
    when( rowSet.getRowMeta() ).thenReturn( inputRowMeta );

    when( mockHelper.pipeline.findRowSet( anyString(), anyInt(), anyString(), anyInt() ) ).thenReturn( rowSet );

    when( mockHelper.pipelineMeta.findNextTransforms( Matchers.any( TransformMeta.class ) ) )
      .thenReturn( Collections.singletonList( mock( TransformMeta.class ) ) );
    when( mockHelper.pipelineMeta.findPreviousTransforms( any( TransformMeta.class ), anyBoolean() ) )
      .thenReturn( Collections.singletonList( mock( TransformMeta.class ) ) );

    return mockHelper;
  }


  private DatabaseLookupMeta createDatabaseMeta() throws HopException {
    MySQLDatabaseMeta mysql = new MySQLDatabaseMeta();
    DatabaseMeta dbMeta = new DatabaseMeta();
    dbMeta.setIDatabase( mysql );

    DatabaseLookupMeta meta = new DatabaseLookupMeta();
    meta.setDatabaseMeta( dbMeta );
    meta.setTablename( "VirtualTable" );

    meta.setTableKeyField( new String[] { ID_FIELD } );
    meta.setKeyCondition( new String[] { "=" } );

    meta.setReturnValueNewName( new String[] { "returned value" } );
    meta.setReturnValueField( new String[] { BINARY_FIELD } );
    meta.setReturnValueDefaultType( new int[] { IValueMeta.TYPE_BINARY } );

    meta.setStreamKeyField1( new String[ 0 ] );
    meta.setStreamKeyField2( new String[ 0 ] );

    meta.setReturnValueDefault( new String[] { "" } );

    meta = spy( meta );
    doAnswer( new Answer() {
      @Override public Object answer( InvocationOnMock invocation ) throws Throwable {
        IRowMeta row = (IRowMeta) invocation.getArguments()[ 0 ];
        IValueMeta v = new ValueMetaBinary( BINARY_FIELD );
        row.addValueMeta( v );
        return null;
      }
    } ).when( meta ).getFields(
      any( IRowMeta.class ),
      anyString(),
      any( IRowMeta[].class ),
      any( TransformMeta.class ),
      any( IVariables.class ),
      any( IHopMetadataProvider.class ) );
    return meta;
  }


  private DatabaseLookupData createDatabaseData() {
    return new DatabaseLookupData();
  }


  private Database createVirtualDb( DatabaseMeta meta ) throws Exception {
    ResultSet rs = mock( ResultSet.class );
    when( rs.getMetaData() ).thenReturn( mock( ResultSetMetaData.class ) );

    PreparedStatement ps = mock( PreparedStatement.class );
    when( ps.executeQuery() ).thenReturn( rs );

    Connection connection = mock( Connection.class );
    when( connection.prepareStatement( anyString() ) ).thenReturn( ps );

    Database db = new Database( mock( LoggingObjectInterface.class ), meta );
    db.setConnection( connection );

    db = spy( db );
    doNothing().when( db ).normalConnect( anyString() );

    IValueMeta binary = new ValueMetaString( BINARY_FIELD );
    binary.setStorageType( IValueMeta.STORAGE_TYPE_BINARY_STRING );

    IValueMeta id = new ValueMetaInteger( ID_FIELD );

    IRowMeta metaByQuerying = new RowMeta();
    metaByQuerying.addValueMeta( binary );
    metaByQuerying.addValueMeta( id );

    doReturn( metaByQuerying ).when( db ).getTableFields( anyString() );
    doReturn( metaByQuerying ).when( db ).getTableFieldsMeta( anyString(), anyString() );

    return db;
  }


  private DatabaseLookup spyLookup( TransformMockHelper<DatabaseLookupMeta, DatabaseLookupData> mocks, Database db,
                                    DatabaseMeta dbMeta ) {
    DatabaseLookup lookup =
      new DatabaseLookup( mocks.transformMeta, mocks.iTransformData, 1, mocks.pipelineMeta, mocks.pipeline );
    lookup = Mockito.spy( lookup );

    doReturn( db ).when( lookup ).getDatabase( eq( dbMeta ) );
    for ( RowSet rowSet : lookup.getOutputRowSets() ) {
      if ( mockingDetails( rowSet ).isMock() ) {
        when( rowSet.putRow( any( IRowMeta.class ), any( Object[].class ) ) ).thenReturn( true );
      }
    }

    return lookup;
  }

  @Test
  public void testEqualsAndIsNullAreCached() throws Exception {
    when( mockHelper.logChannelFactory.create( any(), any( LoggingObjectInterface.class ) ) )
      .thenReturn( mockHelper.logChannelInterface );

    DatabaseLookup look =
      new MockDatabaseLookup( mockHelper.transformMeta, mockHelper.iTransformData, 0, mockHelper.pipelineMeta,
        mockHelper.pipeline );
    DatabaseLookupData lookData = new DatabaseLookupData();
    lookData.cache = DefaultCache.newCache( lookData, 0 );
    lookData.lookupMeta = new RowMeta();

    MySQLDatabaseMeta mysql = new MySQLDatabaseMeta();
    DatabaseMeta dbMeta = new DatabaseMeta();
    dbMeta.setIDatabase( mysql );

    DatabaseLookupMeta meta = new DatabaseLookupMeta();
    meta.setDatabaseMeta( dbMeta );
    meta.setTablename( "VirtualTable" );

    meta.setTableKeyField( new String[] { "ID1", "ID2" } );
    meta.setKeyCondition( new String[] { "=", "IS NULL" } );

    meta.setReturnValueNewName( new String[] { "val1", "val2" } );
    meta.setReturnValueField( new String[] { BINARY_FIELD, BINARY_FIELD } );
    meta.setReturnValueDefaultType( new int[] { IValueMeta.TYPE_BINARY, IValueMeta.TYPE_BINARY } );

    meta.setStreamKeyField1( new String[ 0 ] );
    meta.setStreamKeyField2( new String[ 0 ] );

    meta.setReturnValueDefault( new String[] { "", "" } );

    meta = spy( meta );
    doAnswer( new Answer() {
      @Override public Object answer( InvocationOnMock invocation ) throws Throwable {
        IRowMeta row = (IRowMeta) invocation.getArguments()[ 0 ];
        IValueMeta v = new ValueMetaBinary( BINARY_FIELD );
        row.addValueMeta( v );
        return null;
      }
    } ).when( meta ).getFields(
      any( IRowMeta.class ),
      anyString(),
      any( IRowMeta[].class ),
      any( TransformMeta.class ),
      any( IVariables.class ),
      any( IHopMetadataProvider.class ) );


    look.init();
    assertTrue( lookData.allEquals ); // Test for fix on PDI-15202

  }

  @Test
  public void getRowInCacheTest() throws HopException {
    when( mockHelper.logChannelFactory.create( any(), any( LoggingObjectInterface.class ) ) )
      .thenReturn( mockHelper.logChannelInterface );

    DatabaseLookup look =
      new DatabaseLookup( mockHelper.transformMeta, mockHelper.iTransformData, 0, mockHelper.pipelineMeta,
        mockHelper.pipeline );
    DatabaseLookupData lookData = new DatabaseLookupData();
    lookData.cache = DefaultCache.newCache( lookData, 0 );
    lookData.lookupMeta = new RowMeta();

    look.init( new DatabaseLookupMeta(), lookData );

    IValueMeta valueMeta = new ValueMetaInteger( "fieldTest" );
    RowMeta lookupMeta = new RowMeta();
    lookupMeta.setValueMetaList( Collections.singletonList( valueMeta ) );
    Object[] kgsRow1 = new Object[ 1 ];
    kgsRow1[ 0 ] = 1L;
    Object[] kgsRow2 = new Object[ 1 ];
    kgsRow2[ 0 ] = 2L;
    Object[] add1 = new Object[ 1 ];
    add1[ 0 ] = 10L;
    Object[] add2 = new Object[ 1 ];
    add2[ 0 ] = 20L;
    lookData.cache.storeRowInCache( mockHelper.iTransformMeta, lookupMeta, kgsRow1, add1 );
    lookData.cache.storeRowInCache( mockHelper.iTransformMeta, lookupMeta, kgsRow2, add2 );

    Object[] rowToCache = new Object[ 1 ];
    rowToCache[ 0 ] = 0L;
    lookData.conditions = new int[ 1 ];
    lookData.conditions[ 0 ] = DatabaseLookupMeta.CONDITION_GE;
    Object[] dataFromCache = lookData.cache.getRowFromCache( lookupMeta, rowToCache );

    assertArrayEquals( dataFromCache, add1 );
  }


  @Test
  public void createsReadOnlyCache_WhenReadAll_AndNotAllEquals() throws Exception {
    DatabaseLookupData data = getCreatedData( false );
    assertThat( data.cache, is( instanceOf( ReadAllCache.class ) ) );
  }

  @Test
  public void createsReadDefaultCache_WhenReadAll_AndAllEquals() throws Exception {
    DatabaseLookupData data = getCreatedData( true );
    assertThat( data.cache, is( instanceOf( DefaultCache.class ) ) );
  }

  private DatabaseLookupData getCreatedData( boolean allEquals ) throws Exception {
    Database db = mock( Database.class );
    when( db.getRows( anyString(), anyInt() ) )
      .thenReturn( Collections.singletonList( new Object[] { 1L } ) );

    RowMeta returnRowMeta = new RowMeta();
    returnRowMeta.addValueMeta( new ValueMetaInteger() );
    when( db.getReturnRowMeta() ).thenReturn( returnRowMeta );

    DatabaseLookupMeta meta = createTestMeta();
    DatabaseLookupData data = new DatabaseLookupData();

    DatabaseLookup transform = createSpiedTransform( db, mockHelper, meta );
    transform.init();


    data.db = db;
    data.keytypes = new int[] { IValueMeta.TYPE_INTEGER };
    if ( allEquals ) {
      data.allEquals = true;
      data.conditions = new int[] { DatabaseLookupMeta.CONDITION_EQ };
    } else {
      data.allEquals = false;
      data.conditions = new int[] { DatabaseLookupMeta.CONDITION_LT };
    }
    transform.init();

    return data;
  }

  private DatabaseLookupMeta createTestMeta() {
    DatabaseLookupMeta meta = new DatabaseLookupMeta();
    meta.setCached( true );
    meta.setLoadingAllDataInCache( true );
    meta.setDatabaseMeta( mock( DatabaseMeta.class ) );
    // it's ok here, we won't do actual work
    meta.allocate( 1, 0 );
    meta.setStreamKeyField1( new String[] { "Test" } );
    return meta;
  }

  private DatabaseLookup createSpiedTransform( Database db,
                                          TransformMockHelper<DatabaseLookupMeta, DatabaseLookupData> mockHelper,
                                          DatabaseLookupMeta meta ) throws HopException {
    DatabaseLookup transform = spyLookup( mockHelper, db, meta.getDatabaseMeta() );
    doNothing().when( transform ).determineFieldsTypesQueryingDb();
    doReturn( null ).when( transform ).lookupValues( any( IRowMeta.class ), any( Object[].class ) );

    RowMeta input = new RowMeta();
    input.addValueMeta( new ValueMetaInteger( "Test" ) );
    transform.setInputRowMeta( input );
    return transform;
  }


  @Test
  public void createsReadDefaultCache_AndUsesOnlyNeededFieldsFromMeta() throws Exception {
    Database db = mock( Database.class );
    when( db.getRows( anyString(), anyInt() ) )
      .thenReturn( Arrays.asList( new Object[] { 1L }, new Object[] { 2L } ) );

    RowMeta returnRowMeta = new RowMeta();
    returnRowMeta.addValueMeta( new ValueMetaInteger() );
    returnRowMeta.addValueMeta( new ValueMetaInteger() );
    when( db.getReturnRowMeta() ).thenReturn( returnRowMeta );

    DatabaseLookupMeta meta = createTestMeta();
    DatabaseLookupData data = new DatabaseLookupData();

    DatabaseLookup transform = createSpiedTransform( db, mockHelper, meta );
    transform.init();

    data.db = db;
    data.keytypes = new int[] { IValueMeta.TYPE_INTEGER };
    data.allEquals = true;
    data.conditions = new int[] { DatabaseLookupMeta.CONDITION_EQ };

    transform.init();

    data.lookupMeta = new RowMeta();
    data.lookupMeta.addValueMeta( new ValueMetaInteger() );

    assertNotNull( data.cache.getRowFromCache( data.lookupMeta, new Object[] { 1L } ) );
    assertNotNull( data.cache.getRowFromCache( data.lookupMeta, new Object[] { 2L } ) );
  }

  public class MockDatabaseLookup extends DatabaseLookup {
    public MockDatabaseLookup( TransformMeta transformMeta, ITransformData data, int copyNr, PipelineMeta pipelineMeta, Pipeline pipeline ) {
      super( transformMeta, meta, data, copyNr, pipelineMeta, pipeline );
    }

    @Override
    Database getDatabase( DatabaseMeta meta ) {
      try {
        return createVirtualDb( meta );
      } catch ( Exception ex ) {
        throw new RuntimeException( ex );
      }
    }
  }

}
