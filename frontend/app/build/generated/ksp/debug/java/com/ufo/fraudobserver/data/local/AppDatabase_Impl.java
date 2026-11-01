package com.ufo.fraudobserver.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile FlaggedTransactionDao _flaggedTransactionDao;

  private volatile WhitelistedVpaDao _whitelistedVpaDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `flagged_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `vpa` TEXT NOT NULL, `payeeName` TEXT, `amount` REAL, `riskScore` INTEGER NOT NULL, `triggerReason` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `sourceApp` TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `whitelisted_vpas` (`vpa` TEXT NOT NULL, `addedTimestamp` INTEGER NOT NULL, PRIMARY KEY(`vpa`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '71d68c4f16c047dd66b30eb4e507a386')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `flagged_transactions`");
        db.execSQL("DROP TABLE IF EXISTS `whitelisted_vpas`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsFlaggedTransactions = new HashMap<String, TableInfo.Column>(8);
        _columnsFlaggedTransactions.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFlaggedTransactions.put("vpa", new TableInfo.Column("vpa", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFlaggedTransactions.put("payeeName", new TableInfo.Column("payeeName", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFlaggedTransactions.put("amount", new TableInfo.Column("amount", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFlaggedTransactions.put("riskScore", new TableInfo.Column("riskScore", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFlaggedTransactions.put("triggerReason", new TableInfo.Column("triggerReason", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFlaggedTransactions.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFlaggedTransactions.put("sourceApp", new TableInfo.Column("sourceApp", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysFlaggedTransactions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesFlaggedTransactions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoFlaggedTransactions = new TableInfo("flagged_transactions", _columnsFlaggedTransactions, _foreignKeysFlaggedTransactions, _indicesFlaggedTransactions);
        final TableInfo _existingFlaggedTransactions = TableInfo.read(db, "flagged_transactions");
        if (!_infoFlaggedTransactions.equals(_existingFlaggedTransactions)) {
          return new RoomOpenHelper.ValidationResult(false, "flagged_transactions(com.ufo.fraudobserver.data.local.FlaggedTransactionEntity).\n"
                  + " Expected:\n" + _infoFlaggedTransactions + "\n"
                  + " Found:\n" + _existingFlaggedTransactions);
        }
        final HashMap<String, TableInfo.Column> _columnsWhitelistedVpas = new HashMap<String, TableInfo.Column>(2);
        _columnsWhitelistedVpas.put("vpa", new TableInfo.Column("vpa", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWhitelistedVpas.put("addedTimestamp", new TableInfo.Column("addedTimestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysWhitelistedVpas = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesWhitelistedVpas = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoWhitelistedVpas = new TableInfo("whitelisted_vpas", _columnsWhitelistedVpas, _foreignKeysWhitelistedVpas, _indicesWhitelistedVpas);
        final TableInfo _existingWhitelistedVpas = TableInfo.read(db, "whitelisted_vpas");
        if (!_infoWhitelistedVpas.equals(_existingWhitelistedVpas)) {
          return new RoomOpenHelper.ValidationResult(false, "whitelisted_vpas(com.ufo.fraudobserver.data.local.WhitelistedVpaEntity).\n"
                  + " Expected:\n" + _infoWhitelistedVpas + "\n"
                  + " Found:\n" + _existingWhitelistedVpas);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "71d68c4f16c047dd66b30eb4e507a386", "2162f38cff6c4edb1102cddc21ef0c89");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "flagged_transactions","whitelisted_vpas");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `flagged_transactions`");
      _db.execSQL("DELETE FROM `whitelisted_vpas`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(FlaggedTransactionDao.class, FlaggedTransactionDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(WhitelistedVpaDao.class, WhitelistedVpaDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public FlaggedTransactionDao flaggedTransactionDao() {
    if (_flaggedTransactionDao != null) {
      return _flaggedTransactionDao;
    } else {
      synchronized(this) {
        if(_flaggedTransactionDao == null) {
          _flaggedTransactionDao = new FlaggedTransactionDao_Impl(this);
        }
        return _flaggedTransactionDao;
      }
    }
  }

  @Override
  public WhitelistedVpaDao whitelistedVpaDao() {
    if (_whitelistedVpaDao != null) {
      return _whitelistedVpaDao;
    } else {
      synchronized(this) {
        if(_whitelistedVpaDao == null) {
          _whitelistedVpaDao = new WhitelistedVpaDao_Impl(this);
        }
        return _whitelistedVpaDao;
      }
    }
  }
}
