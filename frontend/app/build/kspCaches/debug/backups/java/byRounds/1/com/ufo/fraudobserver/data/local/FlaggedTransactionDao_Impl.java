package com.ufo.fraudobserver.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class FlaggedTransactionDao_Impl implements FlaggedTransactionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<FlaggedTransactionEntity> __insertionAdapterOfFlaggedTransactionEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfClearAll;

  public FlaggedTransactionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfFlaggedTransactionEntity = new EntityInsertionAdapter<FlaggedTransactionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `flagged_transactions` (`id`,`vpa`,`payeeName`,`amount`,`riskScore`,`triggerReason`,`timestamp`,`sourceApp`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final FlaggedTransactionEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getVpa());
        if (entity.getPayeeName() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getPayeeName());
        }
        if (entity.getAmount() == null) {
          statement.bindNull(4);
        } else {
          statement.bindDouble(4, entity.getAmount());
        }
        statement.bindLong(5, entity.getRiskScore());
        statement.bindString(6, entity.getTriggerReason());
        statement.bindLong(7, entity.getTimestamp());
        if (entity.getSourceApp() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getSourceApp());
        }
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM flagged_transactions WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM flagged_transactions";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final FlaggedTransactionEntity transaction,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfFlaggedTransactionEntity.insertAndReturnId(transaction);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<FlaggedTransactionEntity>> getAllFlow() {
    final String _sql = "SELECT * FROM flagged_transactions ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"flagged_transactions"}, new Callable<List<FlaggedTransactionEntity>>() {
      @Override
      @NonNull
      public List<FlaggedTransactionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfVpa = CursorUtil.getColumnIndexOrThrow(_cursor, "vpa");
          final int _cursorIndexOfPayeeName = CursorUtil.getColumnIndexOrThrow(_cursor, "payeeName");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfRiskScore = CursorUtil.getColumnIndexOrThrow(_cursor, "riskScore");
          final int _cursorIndexOfTriggerReason = CursorUtil.getColumnIndexOrThrow(_cursor, "triggerReason");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSourceApp = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceApp");
          final List<FlaggedTransactionEntity> _result = new ArrayList<FlaggedTransactionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FlaggedTransactionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpVpa;
            _tmpVpa = _cursor.getString(_cursorIndexOfVpa);
            final String _tmpPayeeName;
            if (_cursor.isNull(_cursorIndexOfPayeeName)) {
              _tmpPayeeName = null;
            } else {
              _tmpPayeeName = _cursor.getString(_cursorIndexOfPayeeName);
            }
            final Double _tmpAmount;
            if (_cursor.isNull(_cursorIndexOfAmount)) {
              _tmpAmount = null;
            } else {
              _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            }
            final int _tmpRiskScore;
            _tmpRiskScore = _cursor.getInt(_cursorIndexOfRiskScore);
            final String _tmpTriggerReason;
            _tmpTriggerReason = _cursor.getString(_cursorIndexOfTriggerReason);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpSourceApp;
            if (_cursor.isNull(_cursorIndexOfSourceApp)) {
              _tmpSourceApp = null;
            } else {
              _tmpSourceApp = _cursor.getString(_cursorIndexOfSourceApp);
            }
            _item = new FlaggedTransactionEntity(_tmpId,_tmpVpa,_tmpPayeeName,_tmpAmount,_tmpRiskScore,_tmpTriggerReason,_tmpTimestamp,_tmpSourceApp);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getRecent(final int limit,
      final Continuation<? super List<FlaggedTransactionEntity>> $completion) {
    final String _sql = "SELECT * FROM flagged_transactions ORDER BY timestamp DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<FlaggedTransactionEntity>>() {
      @Override
      @NonNull
      public List<FlaggedTransactionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfVpa = CursorUtil.getColumnIndexOrThrow(_cursor, "vpa");
          final int _cursorIndexOfPayeeName = CursorUtil.getColumnIndexOrThrow(_cursor, "payeeName");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfRiskScore = CursorUtil.getColumnIndexOrThrow(_cursor, "riskScore");
          final int _cursorIndexOfTriggerReason = CursorUtil.getColumnIndexOrThrow(_cursor, "triggerReason");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSourceApp = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceApp");
          final List<FlaggedTransactionEntity> _result = new ArrayList<FlaggedTransactionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FlaggedTransactionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpVpa;
            _tmpVpa = _cursor.getString(_cursorIndexOfVpa);
            final String _tmpPayeeName;
            if (_cursor.isNull(_cursorIndexOfPayeeName)) {
              _tmpPayeeName = null;
            } else {
              _tmpPayeeName = _cursor.getString(_cursorIndexOfPayeeName);
            }
            final Double _tmpAmount;
            if (_cursor.isNull(_cursorIndexOfAmount)) {
              _tmpAmount = null;
            } else {
              _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            }
            final int _tmpRiskScore;
            _tmpRiskScore = _cursor.getInt(_cursorIndexOfRiskScore);
            final String _tmpTriggerReason;
            _tmpTriggerReason = _cursor.getString(_cursorIndexOfTriggerReason);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpSourceApp;
            if (_cursor.isNull(_cursorIndexOfSourceApp)) {
              _tmpSourceApp = null;
            } else {
              _tmpSourceApp = _cursor.getString(_cursorIndexOfSourceApp);
            }
            _item = new FlaggedTransactionEntity(_tmpId,_tmpVpa,_tmpPayeeName,_tmpAmount,_tmpRiskScore,_tmpTriggerReason,_tmpTimestamp,_tmpSourceApp);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<Integer> getCountFlow() {
    final String _sql = "SELECT COUNT(*) FROM flagged_transactions";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"flagged_transactions"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
