package com.fidelis.player.data.db;

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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class MusicDatabase_Impl extends MusicDatabase {
  private volatile MusicDao _musicDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `tracks` (`id` INTEGER NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, `uri` TEXT NOT NULL, `isrc` TEXT, `codec` TEXT NOT NULL, `bitDepth` INTEGER, `sampleRateHz` INTEGER NOT NULL, `bitrateKbps` INTEGER, `isLossless` INTEGER NOT NULL, `losslessAvailable` INTEGER NOT NULL, `normalizedKey` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_isrc` ON `tracks` (`isrc`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_normalizedKey` ON `tracks` (`normalizedKey`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `spotify_matches` (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `spotifyId` TEXT NOT NULL, `spotifyTitle` TEXT NOT NULL, `spotifyArtist` TEXT NOT NULL, `localTrackId` INTEGER, `confidence` INTEGER NOT NULL, `method` TEXT NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_spotify_matches_spotifyId` ON `spotify_matches` (`spotifyId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `audio_features` (`trackId` INTEGER NOT NULL, `bpm` REAL NOT NULL, `camelotKey` TEXT NOT NULL, `energy` REAL NOT NULL, PRIMARY KEY(`trackId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `scrobble_queue` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `artist` TEXT NOT NULL, `track` TEXT NOT NULL, `album` TEXT, `startedAtEpochSec` INTEGER NOT NULL, `sent` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '88e30fe4e8390d3ee170f498840f8a61')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `tracks`");
        db.execSQL("DROP TABLE IF EXISTS `spotify_matches`");
        db.execSQL("DROP TABLE IF EXISTS `audio_features`");
        db.execSQL("DROP TABLE IF EXISTS `scrobble_queue`");
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
        final HashMap<String, TableInfo.Column> _columnsTracks = new HashMap<String, TableInfo.Column>(14);
        _columnsTracks.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTracks.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTracks.put("artist", new TableInfo.Column("artist", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTracks.put("album", new TableInfo.Column("album", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTracks.put("durationMs", new TableInfo.Column("durationMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTracks.put("uri", new TableInfo.Column("uri", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTracks.put("isrc", new TableInfo.Column("isrc", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTracks.put("codec", new TableInfo.Column("codec", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTracks.put("bitDepth", new TableInfo.Column("bitDepth", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTracks.put("sampleRateHz", new TableInfo.Column("sampleRateHz", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTracks.put("bitrateKbps", new TableInfo.Column("bitrateKbps", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTracks.put("isLossless", new TableInfo.Column("isLossless", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTracks.put("losslessAvailable", new TableInfo.Column("losslessAvailable", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTracks.put("normalizedKey", new TableInfo.Column("normalizedKey", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTracks = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTracks = new HashSet<TableInfo.Index>(2);
        _indicesTracks.add(new TableInfo.Index("index_tracks_isrc", false, Arrays.asList("isrc"), Arrays.asList("ASC")));
        _indicesTracks.add(new TableInfo.Index("index_tracks_normalizedKey", false, Arrays.asList("normalizedKey"), Arrays.asList("ASC")));
        final TableInfo _infoTracks = new TableInfo("tracks", _columnsTracks, _foreignKeysTracks, _indicesTracks);
        final TableInfo _existingTracks = TableInfo.read(db, "tracks");
        if (!_infoTracks.equals(_existingTracks)) {
          return new RoomOpenHelper.ValidationResult(false, "tracks(com.fidelis.player.data.db.TrackEntity).\n"
                  + " Expected:\n" + _infoTracks + "\n"
                  + " Found:\n" + _existingTracks);
        }
        final HashMap<String, TableInfo.Column> _columnsSpotifyMatches = new HashMap<String, TableInfo.Column>(7);
        _columnsSpotifyMatches.put("rowId", new TableInfo.Column("rowId", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSpotifyMatches.put("spotifyId", new TableInfo.Column("spotifyId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSpotifyMatches.put("spotifyTitle", new TableInfo.Column("spotifyTitle", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSpotifyMatches.put("spotifyArtist", new TableInfo.Column("spotifyArtist", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSpotifyMatches.put("localTrackId", new TableInfo.Column("localTrackId", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSpotifyMatches.put("confidence", new TableInfo.Column("confidence", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSpotifyMatches.put("method", new TableInfo.Column("method", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSpotifyMatches = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSpotifyMatches = new HashSet<TableInfo.Index>(1);
        _indicesSpotifyMatches.add(new TableInfo.Index("index_spotify_matches_spotifyId", true, Arrays.asList("spotifyId"), Arrays.asList("ASC")));
        final TableInfo _infoSpotifyMatches = new TableInfo("spotify_matches", _columnsSpotifyMatches, _foreignKeysSpotifyMatches, _indicesSpotifyMatches);
        final TableInfo _existingSpotifyMatches = TableInfo.read(db, "spotify_matches");
        if (!_infoSpotifyMatches.equals(_existingSpotifyMatches)) {
          return new RoomOpenHelper.ValidationResult(false, "spotify_matches(com.fidelis.player.data.db.SpotifyMatchEntity).\n"
                  + " Expected:\n" + _infoSpotifyMatches + "\n"
                  + " Found:\n" + _existingSpotifyMatches);
        }
        final HashMap<String, TableInfo.Column> _columnsAudioFeatures = new HashMap<String, TableInfo.Column>(4);
        _columnsAudioFeatures.put("trackId", new TableInfo.Column("trackId", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAudioFeatures.put("bpm", new TableInfo.Column("bpm", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAudioFeatures.put("camelotKey", new TableInfo.Column("camelotKey", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAudioFeatures.put("energy", new TableInfo.Column("energy", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysAudioFeatures = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesAudioFeatures = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoAudioFeatures = new TableInfo("audio_features", _columnsAudioFeatures, _foreignKeysAudioFeatures, _indicesAudioFeatures);
        final TableInfo _existingAudioFeatures = TableInfo.read(db, "audio_features");
        if (!_infoAudioFeatures.equals(_existingAudioFeatures)) {
          return new RoomOpenHelper.ValidationResult(false, "audio_features(com.fidelis.player.data.db.AudioFeaturesEntity).\n"
                  + " Expected:\n" + _infoAudioFeatures + "\n"
                  + " Found:\n" + _existingAudioFeatures);
        }
        final HashMap<String, TableInfo.Column> _columnsScrobbleQueue = new HashMap<String, TableInfo.Column>(6);
        _columnsScrobbleQueue.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScrobbleQueue.put("artist", new TableInfo.Column("artist", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScrobbleQueue.put("track", new TableInfo.Column("track", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScrobbleQueue.put("album", new TableInfo.Column("album", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScrobbleQueue.put("startedAtEpochSec", new TableInfo.Column("startedAtEpochSec", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScrobbleQueue.put("sent", new TableInfo.Column("sent", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysScrobbleQueue = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesScrobbleQueue = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoScrobbleQueue = new TableInfo("scrobble_queue", _columnsScrobbleQueue, _foreignKeysScrobbleQueue, _indicesScrobbleQueue);
        final TableInfo _existingScrobbleQueue = TableInfo.read(db, "scrobble_queue");
        if (!_infoScrobbleQueue.equals(_existingScrobbleQueue)) {
          return new RoomOpenHelper.ValidationResult(false, "scrobble_queue(com.fidelis.player.data.db.ScrobbleEntity).\n"
                  + " Expected:\n" + _infoScrobbleQueue + "\n"
                  + " Found:\n" + _existingScrobbleQueue);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "88e30fe4e8390d3ee170f498840f8a61", "26bfd373fee1b8dd4eedc9859e7603d6");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "tracks","spotify_matches","audio_features","scrobble_queue");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `tracks`");
      _db.execSQL("DELETE FROM `spotify_matches`");
      _db.execSQL("DELETE FROM `audio_features`");
      _db.execSQL("DELETE FROM `scrobble_queue`");
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
    _typeConvertersMap.put(MusicDao.class, MusicDao_Impl.getRequiredConverters());
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
  public MusicDao musicDao() {
    if (_musicDao != null) {
      return _musicDao;
    } else {
      synchronized(this) {
        if(_musicDao == null) {
          _musicDao = new MusicDao_Impl(this);
        }
        return _musicDao;
      }
    }
  }
}
