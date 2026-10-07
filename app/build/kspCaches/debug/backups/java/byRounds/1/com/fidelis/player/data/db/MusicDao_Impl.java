package com.fidelis.player.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.EntityUpsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
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
public final class MusicDao_Impl implements MusicDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<AudioFeaturesEntity> __insertionAdapterOfAudioFeaturesEntity;

  private final EntityInsertionAdapter<ScrobbleEntity> __insertionAdapterOfScrobbleEntity;

  private final SharedSQLiteStatement __preparedStmtOfSetLosslessAvailable;

  private final EntityUpsertionAdapter<TrackEntity> __upsertionAdapterOfTrackEntity;

  private final EntityUpsertionAdapter<SpotifyMatchEntity> __upsertionAdapterOfSpotifyMatchEntity;

  public MusicDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfAudioFeaturesEntity = new EntityInsertionAdapter<AudioFeaturesEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `audio_features` (`trackId`,`bpm`,`camelotKey`,`energy`) VALUES (?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AudioFeaturesEntity entity) {
        statement.bindLong(1, entity.getTrackId());
        statement.bindDouble(2, entity.getBpm());
        statement.bindString(3, entity.getCamelotKey());
        statement.bindDouble(4, entity.getEnergy());
      }
    };
    this.__insertionAdapterOfScrobbleEntity = new EntityInsertionAdapter<ScrobbleEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `scrobble_queue` (`id`,`artist`,`track`,`album`,`startedAtEpochSec`,`sent`) VALUES (nullif(?, 0),?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ScrobbleEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getArtist());
        statement.bindString(3, entity.getTrack());
        if (entity.getAlbum() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getAlbum());
        }
        statement.bindLong(5, entity.getStartedAtEpochSec());
        final int _tmp = entity.getSent() ? 1 : 0;
        statement.bindLong(6, _tmp);
      }
    };
    this.__preparedStmtOfSetLosslessAvailable = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE tracks SET losslessAvailable = ? WHERE id = ?";
        return _query;
      }
    };
    this.__upsertionAdapterOfTrackEntity = new EntityUpsertionAdapter<TrackEntity>(new EntityInsertionAdapter<TrackEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `tracks` (`id`,`title`,`artist`,`album`,`durationMs`,`uri`,`isrc`,`codec`,`bitDepth`,`sampleRateHz`,`bitrateKbps`,`isLossless`,`losslessAvailable`,`normalizedKey`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TrackEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindString(3, entity.getArtist());
        statement.bindString(4, entity.getAlbum());
        statement.bindLong(5, entity.getDurationMs());
        statement.bindString(6, entity.getUri());
        if (entity.getIsrc() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getIsrc());
        }
        statement.bindString(8, entity.getCodec());
        if (entity.getBitDepth() == null) {
          statement.bindNull(9);
        } else {
          statement.bindLong(9, entity.getBitDepth());
        }
        statement.bindLong(10, entity.getSampleRateHz());
        if (entity.getBitrateKbps() == null) {
          statement.bindNull(11);
        } else {
          statement.bindLong(11, entity.getBitrateKbps());
        }
        final int _tmp = entity.isLossless() ? 1 : 0;
        statement.bindLong(12, _tmp);
        final int _tmp_1 = entity.getLosslessAvailable() ? 1 : 0;
        statement.bindLong(13, _tmp_1);
        statement.bindString(14, entity.getNormalizedKey());
      }
    }, new EntityDeletionOrUpdateAdapter<TrackEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `tracks` SET `id` = ?,`title` = ?,`artist` = ?,`album` = ?,`durationMs` = ?,`uri` = ?,`isrc` = ?,`codec` = ?,`bitDepth` = ?,`sampleRateHz` = ?,`bitrateKbps` = ?,`isLossless` = ?,`losslessAvailable` = ?,`normalizedKey` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TrackEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindString(3, entity.getArtist());
        statement.bindString(4, entity.getAlbum());
        statement.bindLong(5, entity.getDurationMs());
        statement.bindString(6, entity.getUri());
        if (entity.getIsrc() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getIsrc());
        }
        statement.bindString(8, entity.getCodec());
        if (entity.getBitDepth() == null) {
          statement.bindNull(9);
        } else {
          statement.bindLong(9, entity.getBitDepth());
        }
        statement.bindLong(10, entity.getSampleRateHz());
        if (entity.getBitrateKbps() == null) {
          statement.bindNull(11);
        } else {
          statement.bindLong(11, entity.getBitrateKbps());
        }
        final int _tmp = entity.isLossless() ? 1 : 0;
        statement.bindLong(12, _tmp);
        final int _tmp_1 = entity.getLosslessAvailable() ? 1 : 0;
        statement.bindLong(13, _tmp_1);
        statement.bindString(14, entity.getNormalizedKey());
        statement.bindLong(15, entity.getId());
      }
    });
    this.__upsertionAdapterOfSpotifyMatchEntity = new EntityUpsertionAdapter<SpotifyMatchEntity>(new EntityInsertionAdapter<SpotifyMatchEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `spotify_matches` (`rowId`,`spotifyId`,`spotifyTitle`,`spotifyArtist`,`localTrackId`,`confidence`,`method`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SpotifyMatchEntity entity) {
        statement.bindLong(1, entity.getRowId());
        statement.bindString(2, entity.getSpotifyId());
        statement.bindString(3, entity.getSpotifyTitle());
        statement.bindString(4, entity.getSpotifyArtist());
        if (entity.getLocalTrackId() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getLocalTrackId());
        }
        statement.bindLong(6, entity.getConfidence());
        statement.bindString(7, entity.getMethod());
      }
    }, new EntityDeletionOrUpdateAdapter<SpotifyMatchEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `spotify_matches` SET `rowId` = ?,`spotifyId` = ?,`spotifyTitle` = ?,`spotifyArtist` = ?,`localTrackId` = ?,`confidence` = ?,`method` = ? WHERE `rowId` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SpotifyMatchEntity entity) {
        statement.bindLong(1, entity.getRowId());
        statement.bindString(2, entity.getSpotifyId());
        statement.bindString(3, entity.getSpotifyTitle());
        statement.bindString(4, entity.getSpotifyArtist());
        if (entity.getLocalTrackId() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getLocalTrackId());
        }
        statement.bindLong(6, entity.getConfidence());
        statement.bindString(7, entity.getMethod());
        statement.bindLong(8, entity.getRowId());
      }
    });
  }

  @Override
  public Object putFeatures(final AudioFeaturesEntity features,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfAudioFeaturesEntity.insert(features);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object enqueueScrobble(final ScrobbleEntity scrobble,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfScrobbleEntity.insertAndReturnId(scrobble);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object setLosslessAvailable(final long id, final boolean available,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetLosslessAvailable.acquire();
        int _argIndex = 1;
        final int _tmp = available ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
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
          __preparedStmtOfSetLosslessAvailable.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertTracks(final List<TrackEntity> tracks,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfTrackEntity.upsert(tracks);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertMatch(final SpotifyMatchEntity match,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfSpotifyMatchEntity.upsert(match);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<TrackEntity>> observeTracks() {
    final String _sql = "SELECT * FROM tracks ORDER BY artist, album, title";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"tracks"}, new Callable<List<TrackEntity>>() {
      @Override
      @NonNull
      public List<TrackEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfArtist = CursorUtil.getColumnIndexOrThrow(_cursor, "artist");
          final int _cursorIndexOfAlbum = CursorUtil.getColumnIndexOrThrow(_cursor, "album");
          final int _cursorIndexOfDurationMs = CursorUtil.getColumnIndexOrThrow(_cursor, "durationMs");
          final int _cursorIndexOfUri = CursorUtil.getColumnIndexOrThrow(_cursor, "uri");
          final int _cursorIndexOfIsrc = CursorUtil.getColumnIndexOrThrow(_cursor, "isrc");
          final int _cursorIndexOfCodec = CursorUtil.getColumnIndexOrThrow(_cursor, "codec");
          final int _cursorIndexOfBitDepth = CursorUtil.getColumnIndexOrThrow(_cursor, "bitDepth");
          final int _cursorIndexOfSampleRateHz = CursorUtil.getColumnIndexOrThrow(_cursor, "sampleRateHz");
          final int _cursorIndexOfBitrateKbps = CursorUtil.getColumnIndexOrThrow(_cursor, "bitrateKbps");
          final int _cursorIndexOfIsLossless = CursorUtil.getColumnIndexOrThrow(_cursor, "isLossless");
          final int _cursorIndexOfLosslessAvailable = CursorUtil.getColumnIndexOrThrow(_cursor, "losslessAvailable");
          final int _cursorIndexOfNormalizedKey = CursorUtil.getColumnIndexOrThrow(_cursor, "normalizedKey");
          final List<TrackEntity> _result = new ArrayList<TrackEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TrackEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpArtist;
            _tmpArtist = _cursor.getString(_cursorIndexOfArtist);
            final String _tmpAlbum;
            _tmpAlbum = _cursor.getString(_cursorIndexOfAlbum);
            final long _tmpDurationMs;
            _tmpDurationMs = _cursor.getLong(_cursorIndexOfDurationMs);
            final String _tmpUri;
            _tmpUri = _cursor.getString(_cursorIndexOfUri);
            final String _tmpIsrc;
            if (_cursor.isNull(_cursorIndexOfIsrc)) {
              _tmpIsrc = null;
            } else {
              _tmpIsrc = _cursor.getString(_cursorIndexOfIsrc);
            }
            final String _tmpCodec;
            _tmpCodec = _cursor.getString(_cursorIndexOfCodec);
            final Integer _tmpBitDepth;
            if (_cursor.isNull(_cursorIndexOfBitDepth)) {
              _tmpBitDepth = null;
            } else {
              _tmpBitDepth = _cursor.getInt(_cursorIndexOfBitDepth);
            }
            final int _tmpSampleRateHz;
            _tmpSampleRateHz = _cursor.getInt(_cursorIndexOfSampleRateHz);
            final Integer _tmpBitrateKbps;
            if (_cursor.isNull(_cursorIndexOfBitrateKbps)) {
              _tmpBitrateKbps = null;
            } else {
              _tmpBitrateKbps = _cursor.getInt(_cursorIndexOfBitrateKbps);
            }
            final boolean _tmpIsLossless;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsLossless);
            _tmpIsLossless = _tmp != 0;
            final boolean _tmpLosslessAvailable;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfLosslessAvailable);
            _tmpLosslessAvailable = _tmp_1 != 0;
            final String _tmpNormalizedKey;
            _tmpNormalizedKey = _cursor.getString(_cursorIndexOfNormalizedKey);
            _item = new TrackEntity(_tmpId,_tmpTitle,_tmpArtist,_tmpAlbum,_tmpDurationMs,_tmpUri,_tmpIsrc,_tmpCodec,_tmpBitDepth,_tmpSampleRateHz,_tmpBitrateKbps,_tmpIsLossless,_tmpLosslessAvailable,_tmpNormalizedKey);
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
  public Object tracksByNormalizedKey(final String key,
      final Continuation<? super List<TrackEntity>> $completion) {
    final String _sql = "SELECT * FROM tracks WHERE normalizedKey = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, key);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TrackEntity>>() {
      @Override
      @NonNull
      public List<TrackEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfArtist = CursorUtil.getColumnIndexOrThrow(_cursor, "artist");
          final int _cursorIndexOfAlbum = CursorUtil.getColumnIndexOrThrow(_cursor, "album");
          final int _cursorIndexOfDurationMs = CursorUtil.getColumnIndexOrThrow(_cursor, "durationMs");
          final int _cursorIndexOfUri = CursorUtil.getColumnIndexOrThrow(_cursor, "uri");
          final int _cursorIndexOfIsrc = CursorUtil.getColumnIndexOrThrow(_cursor, "isrc");
          final int _cursorIndexOfCodec = CursorUtil.getColumnIndexOrThrow(_cursor, "codec");
          final int _cursorIndexOfBitDepth = CursorUtil.getColumnIndexOrThrow(_cursor, "bitDepth");
          final int _cursorIndexOfSampleRateHz = CursorUtil.getColumnIndexOrThrow(_cursor, "sampleRateHz");
          final int _cursorIndexOfBitrateKbps = CursorUtil.getColumnIndexOrThrow(_cursor, "bitrateKbps");
          final int _cursorIndexOfIsLossless = CursorUtil.getColumnIndexOrThrow(_cursor, "isLossless");
          final int _cursorIndexOfLosslessAvailable = CursorUtil.getColumnIndexOrThrow(_cursor, "losslessAvailable");
          final int _cursorIndexOfNormalizedKey = CursorUtil.getColumnIndexOrThrow(_cursor, "normalizedKey");
          final List<TrackEntity> _result = new ArrayList<TrackEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TrackEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpArtist;
            _tmpArtist = _cursor.getString(_cursorIndexOfArtist);
            final String _tmpAlbum;
            _tmpAlbum = _cursor.getString(_cursorIndexOfAlbum);
            final long _tmpDurationMs;
            _tmpDurationMs = _cursor.getLong(_cursorIndexOfDurationMs);
            final String _tmpUri;
            _tmpUri = _cursor.getString(_cursorIndexOfUri);
            final String _tmpIsrc;
            if (_cursor.isNull(_cursorIndexOfIsrc)) {
              _tmpIsrc = null;
            } else {
              _tmpIsrc = _cursor.getString(_cursorIndexOfIsrc);
            }
            final String _tmpCodec;
            _tmpCodec = _cursor.getString(_cursorIndexOfCodec);
            final Integer _tmpBitDepth;
            if (_cursor.isNull(_cursorIndexOfBitDepth)) {
              _tmpBitDepth = null;
            } else {
              _tmpBitDepth = _cursor.getInt(_cursorIndexOfBitDepth);
            }
            final int _tmpSampleRateHz;
            _tmpSampleRateHz = _cursor.getInt(_cursorIndexOfSampleRateHz);
            final Integer _tmpBitrateKbps;
            if (_cursor.isNull(_cursorIndexOfBitrateKbps)) {
              _tmpBitrateKbps = null;
            } else {
              _tmpBitrateKbps = _cursor.getInt(_cursorIndexOfBitrateKbps);
            }
            final boolean _tmpIsLossless;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsLossless);
            _tmpIsLossless = _tmp != 0;
            final boolean _tmpLosslessAvailable;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfLosslessAvailable);
            _tmpLosslessAvailable = _tmp_1 != 0;
            final String _tmpNormalizedKey;
            _tmpNormalizedKey = _cursor.getString(_cursorIndexOfNormalizedKey);
            _item = new TrackEntity(_tmpId,_tmpTitle,_tmpArtist,_tmpAlbum,_tmpDurationMs,_tmpUri,_tmpIsrc,_tmpCodec,_tmpBitDepth,_tmpSampleRateHz,_tmpBitrateKbps,_tmpIsLossless,_tmpLosslessAvailable,_tmpNormalizedKey);
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
  public Object trackByIsrc(final String isrc,
      final Continuation<? super TrackEntity> $completion) {
    final String _sql = "SELECT * FROM tracks WHERE isrc = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, isrc);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<TrackEntity>() {
      @Override
      @Nullable
      public TrackEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfArtist = CursorUtil.getColumnIndexOrThrow(_cursor, "artist");
          final int _cursorIndexOfAlbum = CursorUtil.getColumnIndexOrThrow(_cursor, "album");
          final int _cursorIndexOfDurationMs = CursorUtil.getColumnIndexOrThrow(_cursor, "durationMs");
          final int _cursorIndexOfUri = CursorUtil.getColumnIndexOrThrow(_cursor, "uri");
          final int _cursorIndexOfIsrc = CursorUtil.getColumnIndexOrThrow(_cursor, "isrc");
          final int _cursorIndexOfCodec = CursorUtil.getColumnIndexOrThrow(_cursor, "codec");
          final int _cursorIndexOfBitDepth = CursorUtil.getColumnIndexOrThrow(_cursor, "bitDepth");
          final int _cursorIndexOfSampleRateHz = CursorUtil.getColumnIndexOrThrow(_cursor, "sampleRateHz");
          final int _cursorIndexOfBitrateKbps = CursorUtil.getColumnIndexOrThrow(_cursor, "bitrateKbps");
          final int _cursorIndexOfIsLossless = CursorUtil.getColumnIndexOrThrow(_cursor, "isLossless");
          final int _cursorIndexOfLosslessAvailable = CursorUtil.getColumnIndexOrThrow(_cursor, "losslessAvailable");
          final int _cursorIndexOfNormalizedKey = CursorUtil.getColumnIndexOrThrow(_cursor, "normalizedKey");
          final TrackEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpArtist;
            _tmpArtist = _cursor.getString(_cursorIndexOfArtist);
            final String _tmpAlbum;
            _tmpAlbum = _cursor.getString(_cursorIndexOfAlbum);
            final long _tmpDurationMs;
            _tmpDurationMs = _cursor.getLong(_cursorIndexOfDurationMs);
            final String _tmpUri;
            _tmpUri = _cursor.getString(_cursorIndexOfUri);
            final String _tmpIsrc;
            if (_cursor.isNull(_cursorIndexOfIsrc)) {
              _tmpIsrc = null;
            } else {
              _tmpIsrc = _cursor.getString(_cursorIndexOfIsrc);
            }
            final String _tmpCodec;
            _tmpCodec = _cursor.getString(_cursorIndexOfCodec);
            final Integer _tmpBitDepth;
            if (_cursor.isNull(_cursorIndexOfBitDepth)) {
              _tmpBitDepth = null;
            } else {
              _tmpBitDepth = _cursor.getInt(_cursorIndexOfBitDepth);
            }
            final int _tmpSampleRateHz;
            _tmpSampleRateHz = _cursor.getInt(_cursorIndexOfSampleRateHz);
            final Integer _tmpBitrateKbps;
            if (_cursor.isNull(_cursorIndexOfBitrateKbps)) {
              _tmpBitrateKbps = null;
            } else {
              _tmpBitrateKbps = _cursor.getInt(_cursorIndexOfBitrateKbps);
            }
            final boolean _tmpIsLossless;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsLossless);
            _tmpIsLossless = _tmp != 0;
            final boolean _tmpLosslessAvailable;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfLosslessAvailable);
            _tmpLosslessAvailable = _tmp_1 != 0;
            final String _tmpNormalizedKey;
            _tmpNormalizedKey = _cursor.getString(_cursorIndexOfNormalizedKey);
            _result = new TrackEntity(_tmpId,_tmpTitle,_tmpArtist,_tmpAlbum,_tmpDurationMs,_tmpUri,_tmpIsrc,_tmpCodec,_tmpBitDepth,_tmpSampleRateHz,_tmpBitrateKbps,_tmpIsLossless,_tmpLosslessAvailable,_tmpNormalizedKey);
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

  @Override
  public Object track(final long id, final Continuation<? super TrackEntity> $completion) {
    final String _sql = "SELECT * FROM tracks WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<TrackEntity>() {
      @Override
      @Nullable
      public TrackEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfArtist = CursorUtil.getColumnIndexOrThrow(_cursor, "artist");
          final int _cursorIndexOfAlbum = CursorUtil.getColumnIndexOrThrow(_cursor, "album");
          final int _cursorIndexOfDurationMs = CursorUtil.getColumnIndexOrThrow(_cursor, "durationMs");
          final int _cursorIndexOfUri = CursorUtil.getColumnIndexOrThrow(_cursor, "uri");
          final int _cursorIndexOfIsrc = CursorUtil.getColumnIndexOrThrow(_cursor, "isrc");
          final int _cursorIndexOfCodec = CursorUtil.getColumnIndexOrThrow(_cursor, "codec");
          final int _cursorIndexOfBitDepth = CursorUtil.getColumnIndexOrThrow(_cursor, "bitDepth");
          final int _cursorIndexOfSampleRateHz = CursorUtil.getColumnIndexOrThrow(_cursor, "sampleRateHz");
          final int _cursorIndexOfBitrateKbps = CursorUtil.getColumnIndexOrThrow(_cursor, "bitrateKbps");
          final int _cursorIndexOfIsLossless = CursorUtil.getColumnIndexOrThrow(_cursor, "isLossless");
          final int _cursorIndexOfLosslessAvailable = CursorUtil.getColumnIndexOrThrow(_cursor, "losslessAvailable");
          final int _cursorIndexOfNormalizedKey = CursorUtil.getColumnIndexOrThrow(_cursor, "normalizedKey");
          final TrackEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpArtist;
            _tmpArtist = _cursor.getString(_cursorIndexOfArtist);
            final String _tmpAlbum;
            _tmpAlbum = _cursor.getString(_cursorIndexOfAlbum);
            final long _tmpDurationMs;
            _tmpDurationMs = _cursor.getLong(_cursorIndexOfDurationMs);
            final String _tmpUri;
            _tmpUri = _cursor.getString(_cursorIndexOfUri);
            final String _tmpIsrc;
            if (_cursor.isNull(_cursorIndexOfIsrc)) {
              _tmpIsrc = null;
            } else {
              _tmpIsrc = _cursor.getString(_cursorIndexOfIsrc);
            }
            final String _tmpCodec;
            _tmpCodec = _cursor.getString(_cursorIndexOfCodec);
            final Integer _tmpBitDepth;
            if (_cursor.isNull(_cursorIndexOfBitDepth)) {
              _tmpBitDepth = null;
            } else {
              _tmpBitDepth = _cursor.getInt(_cursorIndexOfBitDepth);
            }
            final int _tmpSampleRateHz;
            _tmpSampleRateHz = _cursor.getInt(_cursorIndexOfSampleRateHz);
            final Integer _tmpBitrateKbps;
            if (_cursor.isNull(_cursorIndexOfBitrateKbps)) {
              _tmpBitrateKbps = null;
            } else {
              _tmpBitrateKbps = _cursor.getInt(_cursorIndexOfBitrateKbps);
            }
            final boolean _tmpIsLossless;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsLossless);
            _tmpIsLossless = _tmp != 0;
            final boolean _tmpLosslessAvailable;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfLosslessAvailable);
            _tmpLosslessAvailable = _tmp_1 != 0;
            final String _tmpNormalizedKey;
            _tmpNormalizedKey = _cursor.getString(_cursorIndexOfNormalizedKey);
            _result = new TrackEntity(_tmpId,_tmpTitle,_tmpArtist,_tmpAlbum,_tmpDurationMs,_tmpUri,_tmpIsrc,_tmpCodec,_tmpBitDepth,_tmpSampleRateHz,_tmpBitrateKbps,_tmpIsLossless,_tmpLosslessAvailable,_tmpNormalizedKey);
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

  @Override
  public Flow<List<SpotifyMatchEntity>> observeGaps() {
    final String _sql = "SELECT * FROM spotify_matches WHERE localTrackId IS NULL";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"spotify_matches"}, new Callable<List<SpotifyMatchEntity>>() {
      @Override
      @NonNull
      public List<SpotifyMatchEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfRowId = CursorUtil.getColumnIndexOrThrow(_cursor, "rowId");
          final int _cursorIndexOfSpotifyId = CursorUtil.getColumnIndexOrThrow(_cursor, "spotifyId");
          final int _cursorIndexOfSpotifyTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "spotifyTitle");
          final int _cursorIndexOfSpotifyArtist = CursorUtil.getColumnIndexOrThrow(_cursor, "spotifyArtist");
          final int _cursorIndexOfLocalTrackId = CursorUtil.getColumnIndexOrThrow(_cursor, "localTrackId");
          final int _cursorIndexOfConfidence = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence");
          final int _cursorIndexOfMethod = CursorUtil.getColumnIndexOrThrow(_cursor, "method");
          final List<SpotifyMatchEntity> _result = new ArrayList<SpotifyMatchEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SpotifyMatchEntity _item;
            final long _tmpRowId;
            _tmpRowId = _cursor.getLong(_cursorIndexOfRowId);
            final String _tmpSpotifyId;
            _tmpSpotifyId = _cursor.getString(_cursorIndexOfSpotifyId);
            final String _tmpSpotifyTitle;
            _tmpSpotifyTitle = _cursor.getString(_cursorIndexOfSpotifyTitle);
            final String _tmpSpotifyArtist;
            _tmpSpotifyArtist = _cursor.getString(_cursorIndexOfSpotifyArtist);
            final Long _tmpLocalTrackId;
            if (_cursor.isNull(_cursorIndexOfLocalTrackId)) {
              _tmpLocalTrackId = null;
            } else {
              _tmpLocalTrackId = _cursor.getLong(_cursorIndexOfLocalTrackId);
            }
            final int _tmpConfidence;
            _tmpConfidence = _cursor.getInt(_cursorIndexOfConfidence);
            final String _tmpMethod;
            _tmpMethod = _cursor.getString(_cursorIndexOfMethod);
            _item = new SpotifyMatchEntity(_tmpRowId,_tmpSpotifyId,_tmpSpotifyTitle,_tmpSpotifyArtist,_tmpLocalTrackId,_tmpConfidence,_tmpMethod);
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
  public Object features(final long id,
      final Continuation<? super AudioFeaturesEntity> $completion) {
    final String _sql = "SELECT * FROM audio_features WHERE trackId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<AudioFeaturesEntity>() {
      @Override
      @Nullable
      public AudioFeaturesEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTrackId = CursorUtil.getColumnIndexOrThrow(_cursor, "trackId");
          final int _cursorIndexOfBpm = CursorUtil.getColumnIndexOrThrow(_cursor, "bpm");
          final int _cursorIndexOfCamelotKey = CursorUtil.getColumnIndexOrThrow(_cursor, "camelotKey");
          final int _cursorIndexOfEnergy = CursorUtil.getColumnIndexOrThrow(_cursor, "energy");
          final AudioFeaturesEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpTrackId;
            _tmpTrackId = _cursor.getLong(_cursorIndexOfTrackId);
            final float _tmpBpm;
            _tmpBpm = _cursor.getFloat(_cursorIndexOfBpm);
            final String _tmpCamelotKey;
            _tmpCamelotKey = _cursor.getString(_cursorIndexOfCamelotKey);
            final float _tmpEnergy;
            _tmpEnergy = _cursor.getFloat(_cursorIndexOfEnergy);
            _result = new AudioFeaturesEntity(_tmpTrackId,_tmpBpm,_tmpCamelotKey,_tmpEnergy);
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

  @Override
  public Object pendingScrobbles(final Continuation<? super List<ScrobbleEntity>> $completion) {
    final String _sql = "SELECT * FROM scrobble_queue WHERE sent = 0 ORDER BY startedAtEpochSec LIMIT 50";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ScrobbleEntity>>() {
      @Override
      @NonNull
      public List<ScrobbleEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfArtist = CursorUtil.getColumnIndexOrThrow(_cursor, "artist");
          final int _cursorIndexOfTrack = CursorUtil.getColumnIndexOrThrow(_cursor, "track");
          final int _cursorIndexOfAlbum = CursorUtil.getColumnIndexOrThrow(_cursor, "album");
          final int _cursorIndexOfStartedAtEpochSec = CursorUtil.getColumnIndexOrThrow(_cursor, "startedAtEpochSec");
          final int _cursorIndexOfSent = CursorUtil.getColumnIndexOrThrow(_cursor, "sent");
          final List<ScrobbleEntity> _result = new ArrayList<ScrobbleEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ScrobbleEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpArtist;
            _tmpArtist = _cursor.getString(_cursorIndexOfArtist);
            final String _tmpTrack;
            _tmpTrack = _cursor.getString(_cursorIndexOfTrack);
            final String _tmpAlbum;
            if (_cursor.isNull(_cursorIndexOfAlbum)) {
              _tmpAlbum = null;
            } else {
              _tmpAlbum = _cursor.getString(_cursorIndexOfAlbum);
            }
            final long _tmpStartedAtEpochSec;
            _tmpStartedAtEpochSec = _cursor.getLong(_cursorIndexOfStartedAtEpochSec);
            final boolean _tmpSent;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSent);
            _tmpSent = _tmp != 0;
            _item = new ScrobbleEntity(_tmpId,_tmpArtist,_tmpTrack,_tmpAlbum,_tmpStartedAtEpochSec,_tmpSent);
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
  public Object markScrobbled(final List<Long> ids, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("UPDATE scrobble_queue SET sent = 1 WHERE id IN (");
        final int _inputSize = ids.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(")");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        for (long _item : ids) {
          _stmt.bindLong(_argIndex, _item);
          _argIndex++;
        }
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
