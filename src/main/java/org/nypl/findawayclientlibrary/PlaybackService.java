package org.nypl.findawayclientlibrary;

//import io.audioengine.mobile.AudioEngine;
import io.audioengine.mobile.AudioEngineException;
//import io.audioengine.mobile.config.LogLevel;

//import io.audioengine.mobile.persistence.DownloadEngine;
//import io.audioengine.mobile.persistence.DownloadRequest;

import io.audioengine.mobile.PlaybackEngine;
import io.audioengine.mobile.PlaybackEvent;
import rx.Observer;

import org.nypl.findawayclientlibrary.util.LogHelper;



/**
 * Translates calls from our app's UI such as "play", "pause",
 * "rewind" into calls to the Findaway SDK.
 *
 * Is not currently a service in the sense that does not stand on its own thread.
 *
 * Created by daryachernikhova on 1/3/18.
 */
public class PlaybackService implements Observer<PlaybackEvent> {
  // so can do a search in log msgs for just this class's output
  private static String TAG;

  private AudioService audioService;

  // plays drm-ed audio
  private PlaybackEngine playbackEngine = null;

  // tracks where the audio playback should be, as far as the seek bar knows
  int seekTo;
  // tracks where in the file the audio playback has last played
  long lastPlaybackPosition;


  public PlaybackService(String APP_TAG, AudioService audioService) {
    TAG = APP_TAG + "PlaybackService";
    //this.sessionId = sessionId;
    this.audioService = audioService;
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
   * Get an initialized findaway sdk PlaybackEngine class, with your sessionId filled in.
   */
  public void initPlaybackEngine() {
    try {
      if (audioService.getAudioEngine() == null) {
        throw new Exception("Must initialize AudioEngine before trying to get a PlaybackEngine.");
      }

      playbackEngine = audioService.getAudioEngine().getPlaybackEngine();
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


  /* ------------------------------------ PLAYBACK EVENT HANDLERS ------------------------------------- */

  /**
   * TODO
   */
  @Override
  public void onCompleted() {
    // ignore
  }


  /**
   * TODO
   */
  @Override
  public void onError(Throwable e) {
    LogHelper.e(TAG, "There was an error in the download or playback process: " + e.getMessage());
    e.printStackTrace();
    // TODO: why am I seeing rx.exceptions.MissingBackpressureException on playback speed change?
    /*
    11.324 5192-5192/org.nypl.findawaysdkdemo W/System.err: rx.exceptions.MissingBackpressureException
    11-17 22:18:11.329 5192-5192/org.nypl.findawaysdkdemo W/System.err:     at rx.internal.operators.OperatorObserveOn$ObserveOnSubscriber.onNext(OperatorObserveOn.java:160)
    11-17 22:18:11.334 5192-5192/org.nypl.findawaysdkdemo W/System.err:     at rx.internal.operators.OperatorSubscribeOn$1$1.onNext(OperatorSubscribeOn.java:53)
    11-17 22:18:11.338 5192-5192/org.nypl.findawaysdkdemo W/System.err:     at com.jakewharton.rxrelay.RelaySubscriptionManager$RelayObserver.onNext(RelaySubscriptionManager.java:205)
    11-17 22:18:11.342 5192-5192/org.nypl.findawaysdkdemo W/System.err:     at com.jakewharton.rxrelay.PublishRelay.call(PublishRelay.java:47)
    11-17 22:18:11.346 5192-5192/org.nypl.findawaysdkdemo W/System.err:     at com.jakewharton.rxrelay.SerializedAction1.call(SerializedAction1.java:84)
    11-17 22:18:11.350 5192-5192/org.nypl.findawaysdkdemo W/System.err:     at com.jakewharton.rxrelay.SerializedRelay.call(SerializedRelay.java:20)
    11-17 22:18:11.354 5192-5192/org.nypl.findawaysdkdemo W/System.err:     at io.audioengine.mobile.play.PlayerEventBus.send(PlayerEventBus.java:33)
    11-17 22:18:11.358 5192-5192/org.nypl.findawaysdkdemo W/System.err:     at io.audioengine.mobile.play.FindawayMediaPlayer.onPlayerStateChanged(FindawayMediaPlayer.java:373)
    11-17 22:18:11.363 5192-5192/org.nypl.findawaysdkdemo W/System.err:     at com.google.android.exoplayer.ExoPlayerImpl.handleEvent(ExoPlayerImpl.java:206)
    11-17 22:18:11.368 5192-5192/org.nypl.findawaysdkdemo W/System.err:     at com.google.android.exoplayer.ExoPlayerImpl$1.handleMessage(ExoPlayerImpl.java:65)
    11-17 22:18:11.371 5192-5192/org.nypl.findawaysdkdemo W/System.err:     at android.os.Handler.dispatchMessage(Handler.java:102)
    11-17 22:18:11.375 5192-5192/org.nypl.findawaysdkdemo W/System.err:     at android.os.Looper.loop(Looper.java:154)
    11-17 22:18:11.379 5192-5192/org.nypl.findawaysdkdemo W/System.err:     at android.os.HandlerThread.run(HandlerThread.java:61)
    */
  }


  /**
   * TODO: doc
   * @param playbackEvent
   */
  @Override
  public void onNext(PlaybackEvent playbackEvent) {
    /* TODO: bring back:
    if (playbackEvent.code().equals(PlaybackEvent.PLAYBACK_PROGRESS_UPDATE)) {
      if (playBookFragment != null) {
        playBookFragment.redrawPlaybackPosition(playbackEvent);
      }

      playbackService.setLastPlaybackPosition(playbackEvent.position());
    } else if (playbackEvent.code().equals(PlaybackEvent.PLAYBACK_STARTED)) {

      Toast.makeText(this, "Playback started.", Toast.LENGTH_SHORT).show();
    } else if (playbackEvent.code().equals(PlaybackEvent.PLAYBACK_PAUSED)) {

      Toast.makeText(this, "Playback paused.", Toast.LENGTH_SHORT).show();
    } else if (playbackEvent.code().equals(PlaybackEvent.PLAYBACK_STOPPED)) {

      Toast.makeText(this, "Playback stopped.", Toast.LENGTH_SHORT).show();
    } else if (playbackEvent.code().equals(PlaybackEvent.CHAPTER_PLAYBACK_COMPLETED)) {
      // NOTE:  "Do X to end of chapter" functionality can go here.
      Toast.makeText(this, "Chapter completed.", Toast.LENGTH_SHORT).show();
    }
    */
  }

  /* ------------------------------------ /PLAYBACK EVENT HANDLERS ------------------------------------- */

}
