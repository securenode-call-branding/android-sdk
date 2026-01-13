package io.securenode.branding.data.db.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import io.securenode.branding.data.db.entity.BrandingEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class BrandingDao_Impl implements BrandingDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<BrandingEntity> __insertionAdapterOfBrandingEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteOlderThan;

  public BrandingDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfBrandingEntity = new EntityInsertionAdapter<BrandingEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `branding` (`phoneE164`,`brandName`,`logoUrl`,`callReason`,`updatedAtEpochMs`) VALUES (?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BrandingEntity entity) {
        statement.bindString(1, entity.getPhoneE164());
        if (entity.getBrandName() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getBrandName());
        }
        if (entity.getLogoUrl() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getLogoUrl());
        }
        if (entity.getCallReason() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getCallReason());
        }
        statement.bindLong(5, entity.getUpdatedAtEpochMs());
      }
    };
    this.__preparedStmtOfDeleteOlderThan = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM branding WHERE updatedAtEpochMs < ?";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final BrandingEntity entity, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfBrandingEntity.insert(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertAll(final List<BrandingEntity> entities,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfBrandingEntity.insert(entities);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteOlderThan(final long cutoffEpochMs,
      final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteOlderThan.acquire();
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
          __preparedStmtOfDeleteOlderThan.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object count(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM branding";
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
  public Object get(final String e164, final Continuation<? super BrandingEntity> $completion) {
    final String _sql = "SELECT * FROM branding WHERE phoneE164 = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, e164);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<BrandingEntity>() {
      @Override
      @Nullable
      public BrandingEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfPhoneE164 = CursorUtil.getColumnIndexOrThrow(_cursor, "phoneE164");
          final int _cursorIndexOfBrandName = CursorUtil.getColumnIndexOrThrow(_cursor, "brandName");
          final int _cursorIndexOfLogoUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "logoUrl");
          final int _cursorIndexOfCallReason = CursorUtil.getColumnIndexOrThrow(_cursor, "callReason");
          final int _cursorIndexOfUpdatedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAtEpochMs");
          final BrandingEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpPhoneE164;
            _tmpPhoneE164 = _cursor.getString(_cursorIndexOfPhoneE164);
            final String _tmpBrandName;
            if (_cursor.isNull(_cursorIndexOfBrandName)) {
              _tmpBrandName = null;
            } else {
              _tmpBrandName = _cursor.getString(_cursorIndexOfBrandName);
            }
            final String _tmpLogoUrl;
            if (_cursor.isNull(_cursorIndexOfLogoUrl)) {
              _tmpLogoUrl = null;
            } else {
              _tmpLogoUrl = _cursor.getString(_cursorIndexOfLogoUrl);
            }
            final String _tmpCallReason;
            if (_cursor.isNull(_cursorIndexOfCallReason)) {
              _tmpCallReason = null;
            } else {
              _tmpCallReason = _cursor.getString(_cursorIndexOfCallReason);
            }
            final long _tmpUpdatedAtEpochMs;
            _tmpUpdatedAtEpochMs = _cursor.getLong(_cursorIndexOfUpdatedAtEpochMs);
            _result = new BrandingEntity(_tmpPhoneE164,_tmpBrandName,_tmpLogoUrl,_tmpCallReason,_tmpUpdatedAtEpochMs);
          } else {
            _result = null;
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
