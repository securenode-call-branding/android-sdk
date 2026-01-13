package io.securenode.branding.data.db.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import io.securenode.branding.data.db.entity.EventEntity;
import java.lang.Class;
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

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class EventDao_Impl implements EventDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<EventEntity> __insertionAdapterOfEventEntity;

  private final EntityDeletionOrUpdateAdapter<EventEntity> __updateAdapterOfEventEntity;

  private final SharedSQLiteStatement __preparedStmtOfMarkUploaded;

  private final SharedSQLiteStatement __preparedStmtOfDeleteUploadedOlderThan;

  public EventDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfEventEntity = new EntityInsertionAdapter<EventEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `events` (`id`,`phoneE164`,`outcome`,`surface`,`displayedAtEpochMs`,`idempotencyKey`,`metaJson`,`createdAtEpochMs`,`uploaded`,`attempts`,`lastError`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final EventEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getPhoneE164());
        statement.bindString(3, entity.getOutcome());
        if (entity.getSurface() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getSurface());
        }
        if (entity.getDisplayedAtEpochMs() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getDisplayedAtEpochMs());
        }
        if (entity.getIdempotencyKey() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getIdempotencyKey());
        }
        if (entity.getMetaJson() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getMetaJson());
        }
        statement.bindLong(8, entity.getCreatedAtEpochMs());
        final int _tmp = entity.getUploaded() ? 1 : 0;
        statement.bindLong(9, _tmp);
        statement.bindLong(10, entity.getAttempts());
        if (entity.getLastError() == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, entity.getLastError());
        }
      }
    };
    this.__updateAdapterOfEventEntity = new EntityDeletionOrUpdateAdapter<EventEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `events` SET `id` = ?,`phoneE164` = ?,`outcome` = ?,`surface` = ?,`displayedAtEpochMs` = ?,`idempotencyKey` = ?,`metaJson` = ?,`createdAtEpochMs` = ?,`uploaded` = ?,`attempts` = ?,`lastError` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final EventEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getPhoneE164());
        statement.bindString(3, entity.getOutcome());
        if (entity.getSurface() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getSurface());
        }
        if (entity.getDisplayedAtEpochMs() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getDisplayedAtEpochMs());
        }
        if (entity.getIdempotencyKey() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getIdempotencyKey());
        }
        if (entity.getMetaJson() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getMetaJson());
        }
        statement.bindLong(8, entity.getCreatedAtEpochMs());
        final int _tmp = entity.getUploaded() ? 1 : 0;
        statement.bindLong(9, _tmp);
        statement.bindLong(10, entity.getAttempts());
        if (entity.getLastError() == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, entity.getLastError());
        }
        statement.bindLong(12, entity.getId());
      }
    };
    this.__preparedStmtOfMarkUploaded = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE events SET uploaded = 1 WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteUploadedOlderThan = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM events WHERE uploaded = 1 AND createdAtEpochMs < ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final EventEntity entity, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfEventEntity.insertAndReturnId(entity);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final EventEntity entity, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfEventEntity.handle(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object markUploaded(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfMarkUploaded.acquire();
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
          __preparedStmtOfMarkUploaded.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteUploadedOlderThan(final long cutoffEpochMs,
      final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteUploadedOlderThan.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, cutoffEpochMs);
        try {
          __db.beginTransaction();
          try {
            final Integer _result = _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteUploadedOlderThan.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object countPending(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM events WHERE uploaded = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
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
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object pending(final int limit,
      final Continuation<? super List<EventEntity>> $completion) {
    final String _sql = "SELECT * FROM events WHERE uploaded = 0 ORDER BY id ASC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<EventEntity>>() {
      @Override
      @NonNull
      public List<EventEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPhoneE164 = CursorUtil.getColumnIndexOrThrow(_cursor, "phoneE164");
          final int _cursorIndexOfOutcome = CursorUtil.getColumnIndexOrThrow(_cursor, "outcome");
          final int _cursorIndexOfSurface = CursorUtil.getColumnIndexOrThrow(_cursor, "surface");
          final int _cursorIndexOfDisplayedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "displayedAtEpochMs");
          final int _cursorIndexOfIdempotencyKey = CursorUtil.getColumnIndexOrThrow(_cursor, "idempotencyKey");
          final int _cursorIndexOfMetaJson = CursorUtil.getColumnIndexOrThrow(_cursor, "metaJson");
          final int _cursorIndexOfCreatedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtEpochMs");
          final int _cursorIndexOfUploaded = CursorUtil.getColumnIndexOrThrow(_cursor, "uploaded");
          final int _cursorIndexOfAttempts = CursorUtil.getColumnIndexOrThrow(_cursor, "attempts");
          final int _cursorIndexOfLastError = CursorUtil.getColumnIndexOrThrow(_cursor, "lastError");
          final List<EventEntity> _result = new ArrayList<EventEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final EventEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpPhoneE164;
            _tmpPhoneE164 = _cursor.getString(_cursorIndexOfPhoneE164);
            final String _tmpOutcome;
            _tmpOutcome = _cursor.getString(_cursorIndexOfOutcome);
            final String _tmpSurface;
            if (_cursor.isNull(_cursorIndexOfSurface)) {
              _tmpSurface = null;
            } else {
              _tmpSurface = _cursor.getString(_cursorIndexOfSurface);
            }
            final Long _tmpDisplayedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfDisplayedAtEpochMs)) {
              _tmpDisplayedAtEpochMs = null;
            } else {
              _tmpDisplayedAtEpochMs = _cursor.getLong(_cursorIndexOfDisplayedAtEpochMs);
            }
            final String _tmpIdempotencyKey;
            if (_cursor.isNull(_cursorIndexOfIdempotencyKey)) {
              _tmpIdempotencyKey = null;
            } else {
              _tmpIdempotencyKey = _cursor.getString(_cursorIndexOfIdempotencyKey);
            }
            final String _tmpMetaJson;
            if (_cursor.isNull(_cursorIndexOfMetaJson)) {
              _tmpMetaJson = null;
            } else {
              _tmpMetaJson = _cursor.getString(_cursorIndexOfMetaJson);
            }
            final long _tmpCreatedAtEpochMs;
            _tmpCreatedAtEpochMs = _cursor.getLong(_cursorIndexOfCreatedAtEpochMs);
            final boolean _tmpUploaded;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfUploaded);
            _tmpUploaded = _tmp != 0;
            final int _tmpAttempts;
            _tmpAttempts = _cursor.getInt(_cursorIndexOfAttempts);
            final String _tmpLastError;
            if (_cursor.isNull(_cursorIndexOfLastError)) {
              _tmpLastError = null;
            } else {
              _tmpLastError = _cursor.getString(_cursorIndexOfLastError);
            }
            _item = new EventEntity(_tmpId,_tmpPhoneE164,_tmpOutcome,_tmpSurface,_tmpDisplayedAtEpochMs,_tmpIdempotencyKey,_tmpMetaJson,_tmpCreatedAtEpochMs,_tmpUploaded,_tmpAttempts,_tmpLastError);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
