package org.nypl.findawayclientlibrary;

import rx.android.schedulers.AndroidSchedulers;
import rx.Observer;
import rx.schedulers.Schedulers;
import rx.Subscription;

import io.audioengine.mobile.AudioEngineException;
import io.audioengine.mobile.PlaybackEngine;
import io.audioengine.mobile.PlaybackEvent;
import io.audioengine.mobile.PlayRequest;

import org.nypl.audiobookincludes.util.LogHelper;



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

  public static final Integer CHAPTER_PART_DEFAULT = new Integer(0);

  public static final Integer PLAY_ERROR = new Integer(-1);
  public static final Integer PLAY_SUCCESS = new Integer(0);

  private AudioService audioService;

  // Provides context for methods s.a. getFilesDir(), and allows events caught
  // by this class to be reflected in the app's UI.
  private org.nypl.audiobookincludes.PlayBookActivity callbackActivity = null;

  // plays drm-ed audio
  private PlaybackEngine playbackEngine = null;
  private PlayRequest playRequest;

  Subscription eventsSubscriptionAll = null;

  // tracks where the audio playback should be, as far as the seek bar knows
  int seekTo;
  // tracks where in the file the audio playback has last played
  long lastPlaybackPosition;


  public PlaybackService(String APP_TAG, AudioService audioService, org.nypl.audiobookincludes.PlayBookActivity callbackActivity) {
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
      // TODO
      e.printStackTrace();
    } catch (Exception e) {
      LogHelper.e(TAG, "Error getting playback engine: " + e.getMessage());
      e.printStackTrace();
    }

    // This is set to "true" by default in the Findaway SDK, but let's make it explicit.
    // We want the Findaway SDK to hear the audio focus change events broadcast by the OS,
    // and to respond to them by pausing, resuming, or ducking.
    playbackEngine.manageAudioFocus(true);

    seekTo = 0;
    lastPlaybackPosition = 0;
  }


  /**
   * Subscribe to a stream of _all_ playback events without discriminating on content id.
   * Keep the record of the subscription, in case will be asked to unsubscribe.
   *
   * @return
   */
  public Subscription subscribePlayEventsAll(Observer<PlaybackEvent> observer) {
    // reset
    if (eventsSubscriptionAll != null && !eventsSubscriptionAll.isUnsubscribed()) {
      eventsSubscriptionAll.unsubscribe();
    }

    eventsSubscriptionAll = this.getPlaybackEngine().events().subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(observer);

    return eventsSubscriptionAll;
  }


  /**
   * If there is a subscription to "all-type" play events, then unsubscribe it.
   *
   * @return
   */
  public Subscription unsubscribePlayEventsAll() {
    if (eventsSubscriptionAll != null && !eventsSubscriptionAll.isUnsubscribed()) {
      eventsSubscriptionAll.unsubscribe();
    }
    return eventsSubscriptionAll;
  }


  /* ------------------------------------ PLAYBACK EVENT HANDLERS ------------------------------------- */

  public Integer playAudio(String contentId, String licenseId, Integer chapter, Integer part) {

    if (part == null) {
      // most of the time, we don't need to get granular.  start download at part==0, and auto-proceed from there.
      // TODO:  untested assumption
      part = CHAPTER_PART_DEFAULT;
    }

    if ((contentId == null) || (licenseId == null) || (chapter == null)) {
      LogHelper.e(TAG, "PlaybackService cannot build play request for (contentId, licenseId, chapter)", contentId, licenseId, chapter);
      return PLAY_ERROR;
    }

    playRequest = PlayRequest.builder().contentId(contentId).part(part).chapter(chapter).license(licenseId).
            position((int) this.getLastPlaybackPosition()).build();

    try {
      this.getPlaybackEngine().play(playRequest);
      LogHelper.d(TAG, "after downloadEngine.download \n\n\n");
    } catch (Exception e) {
      LogHelper.e(TAG, e, "Error getting download engine: " + e.getMessage());
      return PLAY_ERROR;
    }

    return PLAY_SUCCESS;
  }


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
