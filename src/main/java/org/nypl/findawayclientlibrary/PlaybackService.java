package org.nypl.findawayclientlibrary;

import io.audioengine.mobile.AudioEngine;
import io.audioengine.mobile.AudioEngineException;
import io.audioengine.mobile.config.LogLevel;

import io.audioengine.mobile.persistence.DownloadEngine;
import io.audioengine.mobile.persistence.DownloadRequest;

import io.audioengine.mobile.play.PlaybackEngine;

import org.nypl.findawayclientlibrary.util.LogHelper;



/**
 * Translates calls from our app's UI such as "play", "pause",
 * "rewind" into calls to the Findaway SDK.
 *
 * Is not currently a service in the sense that does not stand on its own thread.
 *
 * Created by daryachernikhova on 1/3/18.
 */
public class PlaybackService {
  // so can do a search in log msgs for just this class's output
  private static String TAG;
  private String sessionId;

  // the one engine to control them all
  private static AudioEngine audioEngine = null;

  // fulfills books
  DownloadEngine downloadEngine = null;
  //private DownloadRequest downloadRequest;

  // plays drm-ed audio
  private PlaybackEngine playbackEngine = null;

  // tracks where the audio playback should be, as far as the seek bar knows
  int seekTo;
  // tracks where in the file the audio playback has last played
  long lastPlaybackPosition;


  public PlaybackService(String APP_TAG, String sessionId) {
    TAG = APP_TAG + "PlaybackService";
    this.sessionId = sessionId;
  }


  /**
   * Get the AudioEngine instance.  Initialization is not guaranteed.
   * @return
   */
  public static AudioEngine getAudioEngine() {
    return audioEngine;
  }


  /**
   * Get the DownloadEngine instance.  Initialization is not guaranteed.
   * @return
   */
  public DownloadEngine getDownloadEngine() {
    return downloadEngine;
  }


  /**
   * Get the PlaybackEngine instance.  Initialization is not guaranteed.
   * @return
   */
  public PlaybackEngine getPlaybackEngine() {
    return playbackEngine;
  }


  /**
   * Gets knowledge of current position on the seek bar.
   * @return
   */
  public int getSeekTo() {
    return seekTo;
  }


  /**
   * Sets knowledge of current position on the seek bar.
   * @param seekTo
   */
  public void setSeekTo(int seekTo) {
    this.seekTo = seekTo;
  }


  /**
   * Gets knowledge of current position in the audio file.
   * @return
   */
  public long getLastPlaybackPosition() {
    return lastPlaybackPosition;
  }


  /**
   * Sets knowledge of current position in the audio file.
   * @param lastPlaybackPosition
   */
  public void setLastPlaybackPosition(long lastPlaybackPosition) {
    this.lastPlaybackPosition = lastPlaybackPosition;
  }


  /**
   * AudioEngine needs the sessionKey that comes from the Bibliotheca
   * https://partner.yourcloudlibrary.com/cirrus/library/[libraryId]/GetItemAudioFulfillment endpoint.
   */
  public void initAudioEngine(android.content.Context context) {
    try {
      AudioEngine.init(context, sessionId, LogLevel.VERBOSE);
      audioEngine = AudioEngine.getInstance();
    } catch (AudioEngineException e) {
      LogHelper.e(TAG, "Error getting audio engine: " + e.getMessage());
      e.printStackTrace();
    } catch (Exception e) {
      LogHelper.e(TAG, "Error getting audio engine: " + e.getMessage());
      e.printStackTrace();
    }
  }


  /**
   * Get an initialized findaway sdk DownloadEngine class, with your sessionId filled in.
   */
  public void initDownloadEngine() {
    try {
      if (this.getAudioEngine() == null) {
        throw new Exception("Must initialize AudioEngine before trying to get a DownloadEngine.");
      }

      downloadEngine = this.getAudioEngine().getDownloadEngine();
    } catch (AudioEngineException e) {
      // Call to getDownloadEngine will throw an exception if you have not previously
      // called init() on AudioEngine with a valid Context and Session.
      LogHelper.e(TAG, "Error getting download engine: " + e.getMessage());
      e.printStackTrace();
    } catch (Exception e) {
      LogHelper.e(TAG, "Error getting download engine: " + e.getMessage());
      e.printStackTrace();
    }
  }


  /**
   * Get an initialized findaway sdk PlaybackEngine class, with your sessionId filled in.
   */
  public void initPlaybackEngine() {
    try {
      if (this.getAudioEngine() == null) {
        throw new Exception("Must initialize AudioEngine before trying to get a PlaybackEngine.");
      }

      playbackEngine = this.getAudioEngine().getPlaybackEngine();
    } catch (AudioEngineException e) {
      LogHelper.e(TAG, "Error getting playback engine: " + e.getMessage());
      e.printStackTrace();
    } catch (Exception e) {
      LogHelper.e(TAG, "Error getting playback engine: " + e.getMessage());
      e.printStackTrace();
    }

    seekTo = 0;
    lastPlaybackPosition = 0;
  }


}
