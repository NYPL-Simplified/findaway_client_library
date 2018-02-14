package org.nypl.findawayclientlibrary;

import io.audioengine.mobile.AudioEngine;
import io.audioengine.mobile.LogLevel;

import org.nypl.audiobookincludes.util.LogHelper;
import org.nypl.audiobookincludes.ManifestModel;


/**
 * Keeps track of the audio engine, which we need to get playback and download engines.
 *
 * Is not currently a service in the sense that does not stand on its own thread.
 *
 * NOTE: I'm not currently sure if there will be a cause to want to have more than one AudioEngine.
 * Would a patron be subscribed to more than one library with Findaway books in its collection?  It's not impossible.
 * Would the Findaway SDK work well with more than one concurent AudioEngine, or would we want to make sure to only have one in memory at any given time?
 * TODO: In a future branch, decide what code should enforce having only one AudioEngine per PlaybackEngine and DownloadEngine set.
 *
 * Created by daryachernikhova on 1/5/18.
 */
public class AudioService implements org.nypl.audiobookincludes.AudioService {
  private static String APP_TAG;
  // so can do a search in log msgs for just this class's output
  private static String TAG;

  // the one engine to control them all
  private AudioEngine audioEngine = null;

  private String sessionId;



  // TODO: all the sessionId, contentId and licenseId hardcoded vars will go away.
  // Kevin's
  String sessionIdKevin = "964b5796-c67f-492d-8024-a69f3ba9be53";
  // mine:
  String sessionIdReal1 = "be101b8f-5013-4328-9898-5fe24b302641";
  String sessionIdReal2 = "1a03523c-8557-49cd-a046-2560d5f21056";
  String sessionIdReal3 = "a54406b6-76cf-4241-bbf6-7f1e8b76d7f4";
  String sessionIdReal4 = "720ba35d-8920-4621-9749-17f7ebe56316";
  String sessionIdReal5 = "3a2c87d3-29d3-4d57-8a24-ba06fd8dcf62";
  String sessionIdReal6 = "35b481ab-c47f-41ff-95b1-ad2ab2551181";
  String sessionIdReal7 = "8718066c-3aa9-4353-803c-f7dd3c829e40";
  String sessionIdFake1 = "00000000-0000-0000-0000-000000000000";


  // Kevin's test value
  // String contentId = "83380";
  // Darya's value keyed to the fulfillmentId of the book NYPL bought through Bibliotheca
  String contentId = "102244";

  // Kevin's test value
  //String license = "5744a7b7b692b13bf8c06865";
  // Darya's value keyed to licenseId of the book NYPL bought through Bibliotheca
  //String license = "57ff8f27afde9f3cea3c041b";
  //String license = "57db1411afde9f3e7a3c041b";
  String licenseId = "580e7da175435e471d2e042a";


  // plays drm-ed audio
  private PlaybackService playbackService = null;
  // fulfills books
  private DownloadService downloadService = null;


  /**
   * Constructor.
   * TODO: Will accept an audiobook manifest model object.
   *
   * @param APP_TAG  What application is this library package running in.
   * @param sessionId  Library customer's session with Findaway.
   */
  public AudioService(String APP_TAG, String sessionId) {
    APP_TAG = APP_TAG;
    TAG = APP_TAG + "AudioService";
    this.sessionId = sessionId;
  }


  /**
   * Get the AudioEngine instance.  Initialization is not guaranteed.
   * @return
   */
  public AudioEngine getAudioEngine() {
    return audioEngine;
  }


  /**
   * TODO: lazy init -- if I started AudioEngine to delete a file, and now I want to play a file,
   * then find the previously made clases, and just attach the callbackactivity to them.
   *
   * Init the Download and Playback functionality-handling classes with the activity that will
   * be accepting status update notifications.
   *
   * @param context
   * @param callbackActivity
   */
  public void init(android.content.Context context, org.nypl.audiobookincludes.PlayBookActivity callbackActivity) {
    this.initAudioEngine(context);

    // plays drm-ed audio
    playbackService = new PlaybackService(APP_TAG, this, callbackActivity);

    // ask the AudioEngine to start a PlaybackEngine
    playbackService.initPlaybackEngine();

    // fulfills books
    downloadService = new DownloadService(APP_TAG, this, callbackActivity);

    // ask the AudioEngine to start a DownloadEngine
    downloadService.initDownloadEngine();
  }


  /**
   * TODO doc
   * TODO lazy init if already had callbackactivity before
   *
   * @param context
   * @param callbackFragment
   */
  public void init(android.content.Context context, org.nypl.audiobookincludes.DeleteBookFragment callbackFragment) {
    this.initAudioEngine(context);

    // fulfills books
    downloadService = new DownloadService(APP_TAG, this, callbackFragment);

    // ask the AudioEngine to start a DownloadEngine
    downloadService.initDownloadEngine();
  }


  /**
   * AudioEngine needs the sessionKey that comes from the Bibliotheca
   * https://partner.yourcloudlibrary.com/cirrus/library/[libraryId]/GetItemAudioFulfillment endpoint.
   */
  public void initAudioEngine(android.content.Context context) {
    try {
      AudioEngine.init(context, sessionId, LogLevel.VERBOSE);
      audioEngine = AudioEngine.getInstance();
    } catch (Exception e) {
      LogHelper.e(TAG, e, "Error getting audio engine: ", e.getMessage());
      //TODO: should never happen.  but if it does, kick user back to book detail screen and apologize
    }
  }


  public void subscribeEvents(ManifestModel audiobookManifest, boolean unsubscribeDownload, boolean unsubscribePlay) {
    // a stream of _all_ download events for the supplied content id
    // the onCompleted(), onError() and onNext() methods are the ones implemented in the activity itself.
    downloadService.subscribeDownloadEventsAll(downloadService, contentId);
    playbackService.subscribePlayEventsAll(playbackService);
  }


  public void unsubscribeEvents(boolean unsubscribeDownload, boolean unsubscribePlay) {
    if (unsubscribeDownload) {
      downloadService.unsubscribeDownloadEventsAll();
    }

    if (unsubscribePlay) {
      playbackService.unsubscribePlayEventsAll();
    }
  }


  @Override
  public DOWNLOAD_STATUS getDownloadStatus(ManifestModel audiobookManifest) {
    return downloadService.getDownloadStatus(contentId);
  }


  @Override
  public DOWNLOAD_STATUS downloadAudio(ManifestModel audiobookManifest, Integer chapter, Integer part) {
    // ask to start the download
    return downloadService.downloadAudio(contentId, licenseId, chapter, part);
  }


  @Override
  public DOWNLOAD_STATUS deleteDownload(String contentId) {
    return downloadService.deleteDownload(contentId);
  }


  @Override
  public DOWNLOAD_STATUS cancelDownload(String contentId) {
    return null;
  }


  @Override
  public DOWNLOAD_STATUS pauseDownload(String contentId) {
    return null;
  }


  @Override
  public DOWNLOAD_STATUS resumeDownload(ManifestModel audiobookManifest) {
    return downloadService.resumeDownload(contentId);
  }


  @Override
  public Integer playAudio(ManifestModel audiobookManifest, Integer chapter, Integer part) {
    return playbackService.playAudio(contentId, licenseId, chapter, null);
  }


  @Override
  public void pausePlayback() {
    playbackService.pausePlayback();
  }


  @Override
  public void resumePlayback() {
    playbackService.resumePlayback();
  }


  @Override
  public long getSeekTo() {
    return playbackService.getSeekTo();
  }


  @Override
  public void seekTo(long milliseconds) {
    playbackService.seekTo(milliseconds);
  }


  @Override
  public void seekAhead(long millisecondsToJump) {
    playbackService.seekAhead(millisecondsToJump);
  }


  @Override
  public void seekBehind(long millisecondsToJump) {
    playbackService.seekBehind(millisecondsToJump);
  }


}
