package io.confluent.kafka.connect.cdc;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.kafka.connect.errors.DataException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public abstract class CachingTableMetadataProvider implements TableMetadataProvider {
  final JdbcCDCSourceConnectorConfig config;
  final Cache<ChangeKey, TableMetadata> tableMetadataCache;

  public CachingTableMetadataProvider(JdbcCDCSourceConnectorConfig config) {
    this.config = config;
    this.tableMetadataCache = CacheBuilder
        .newBuilder()
        .expireAfterWrite(config.schemaCacheMs, TimeUnit.MILLISECONDS)
        .build();
  }

  protected Connection openConnection() throws SQLException {
    return JdbcUtils.openConnection(this.config);
  }

  protected abstract TableMetadata fetchTableMetadata(String schemaName, String tableName) throws SQLException;

  @Override
  public TableMetadata tableMetadata(String schemaName, String tableName) {
    final ChangeKey changeKey = new ChangeKey(schemaName, tableName);
    try {
      return this.tableMetadataCache.get(changeKey, new Callable<TableMetadata>() {
        @Override
        public TableMetadata call() throws Exception {
          return fetchTableMetadata(schemaName, tableName);
        }
      });

    } catch (ExecutionException ex) {
      throw new DataException("Exception thrown while getting metadata for " + changeKey, ex);
    }
  }
}