package org.nypl.findawayclientlibrary;

//import io.audioengine.mobile.AudioEngine;
import android.widget.Toast;

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

  // Provides context for methods s.a. getFilesDir(), and allows events caught
  // by this class to be reflected in the app's UI.
  private PlayBookActivity callbackActivity = null;

  // plays drm-ed audio
  private PlaybackEngine playbackEngine = null;

  // tracks where the audio playback should be, as far as the seek bar knows
  int seekTo;
  // tracks where in the file the audio playback has last played
  long lastPlaybackPosition;


  public PlaybackService(String APP_TAG, AudioService audioService, PlayBookActivity callbackActivity) {
    TAG = APP_TAG + "PlaybackService";
    //this.sessionId = sessionId;
    this.audioService = audioService;
    this.callbackActivity = callbackActivity;
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
   * To satisfy rx.Observer implementation.
   */
  @Override
  public void onCompleted() {
    // ignore
  }


  /**
   * Handle playback events that are errors.
   */
  @Override
  public void onError(Throwable e) {
    LogHelper.e(TAG, "There was an error in the playback process: " + e.getMessage());
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
   * Handle a playback event, s.a. a chapter completing, playback position progressing, etc..
   * @param playbackEvent
   */
  @Override
  public void onNext(PlaybackEvent playbackEvent) {
    if (playbackEvent == null) {
      LogHelper.d(TAG, "onNext(PlaybackEvent playbackEvent) called with null playbackEvent");
      return;
    }

    if (playbackEvent.code().equals(PlaybackEvent.PLAYBACK_PROGRESS_UPDATE)) {
      Long duration = playbackEvent.duration();
      Long position = playbackEvent.position();

      // ask the activity to redraw
      callbackActivity.setPlayProgress(duration, position);

      // remember where we are in the audio file now
      this.setLastPlaybackPosition(playbackEvent.position());
    } else if (playbackEvent.code().equals(PlaybackEvent.PLAYBACK_STARTED)) {
      callbackActivity.notifyPlayEvent("Playback started.");
    } else if (playbackEvent.code().equals(PlaybackEvent.PLAYBACK_PAUSED)) {
      callbackActivity.notifyPlayEvent("Playback paused.");
    } else if (playbackEvent.code().equals(PlaybackEvent.PLAYBACK_STOPPED)) {
      callbackActivity.notifyPlayEvent("Playback stopped.");
    } else if (playbackEvent.code().equals(PlaybackEvent.CHAPTER_PLAYBACK_COMPLETED)) {
      // NOTE:  Sleep timer's "Do X to end of chapter" functionality can go here.
      callbackActivity.notifyPlayEvent("Chapter completed.");
    }

  }

  /* ------------------------------------ /PLAYBACK EVENT HANDLERS ------------------------------------- */

}
