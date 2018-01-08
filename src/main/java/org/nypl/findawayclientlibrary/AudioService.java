package org.nypl.findawayclientlibrary;

import io.audioengine.mobile.AudioEngine;
import io.audioengine.mobile.AudioEngineException;
import io.audioengine.mobile.LogLevel;

import org.nypl.findawayclientlibrary.util.LogHelper;


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
public class AudioService {
  // so can do a search in log msgs for just this class's output
  private static String TAG;

  // the one engine to control them all
  private AudioEngine audioEngine = null;

  private String sessionId;


  public AudioService(String APP_TAG, String sessionId) {
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
   * AudioEngine needs the sessionKey that comes from the Bibliotheca
   * https://partner.yourcloudlibrary.com/cirrus/library/[libraryId]/GetItemAudioFulfillment endpoint.
   */
  public void initAudioEngine(android.content.Context context) {
    //try {
      AudioEngine.init(context, sessionId, LogLevel.VERBOSE);
      audioEngine = AudioEngine.getInstance();
    //} catch (AudioEngineException e) {
      //LogHelper.e(TAG, "Error getting audio engine: " + e.getMessage());
      //e.printStackTrace();
    //} catch (Exception e) {
      //LogHelper.e(TAG, "Error getting audio engine: " + e.getMessage());
      //e.printStackTrace();
    //}
  }


}
