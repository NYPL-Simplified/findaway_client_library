package org.nypl.findawayclientlibrary;


import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.media.session.MediaControllerCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.File;

import io.audioengine.mobile.AudioEngineEvent;
import io.audioengine.mobile.PlaybackEvent;
import io.audioengine.mobile.config.LogLevel;
import io.audioengine.mobile.AudioEngine;
import io.audioengine.mobile.AudioEngineException;

import io.audioengine.mobile.DownloadEvent;
import io.audioengine.mobile.DownloadStatus;

import io.audioengine.mobile.persistence.DeleteRequest;
import io.audioengine.mobile.persistence.DownloadEngine;
import io.audioengine.mobile.persistence.DownloadRequest;
import io.audioengine.mobile.persistence.DownloadType;

import io.audioengine.mobile.play.PlaybackEngine;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;




/**
 * Loads and exchanges fragments that are responsible for audio player UI
 * and audiobook-associated sub-screens.
 *
 * Communicates with the download portion of the sdk.
 *
 * Communicates with the audio playing portion of the sdk.  Acts as an intermediary between
 * the background player service and the front-end ui fragment.
 *
 * On Session Keys:
 * - Bibliotheca endpoint returns a new session key, every time it's hit.  This is a bug, but not currently worth fixing.
 * - When Bibliotheca obtains a new session key from Findaway, the previous session keys are not triggered to expire.
 * - Findaway keeps a list of “valid” session keys for each book.  All of them will work for “a while”, whatever that “while” may be.
 *   I can use either key in the app to resume download with.
 * - Sometimes, the keys will expire.  This will happen on Findaway’s schedule (days, weeks?).  If the keys have expired, I will need to get a fresh key.
 * - The only way to know that a key has expired, is to get a download error.  Right now, the information is in the download error message,
 *   not the error code, which is generic.  This will change in a future sdk.
 * - Playback always works, with any session key, as long as I’m using the Findaway sdk to play the Findaway audio content.
 *
 * from phone call with Findaway:
 * session are cached on Findaway side (reddis cache).  older keys will drop out.  This takes hours - weeks, but more likely weeks.
 * license allows to play, session allows to download
 * once downloaded, do not make calls to verify session, so yes, playing does work.
 * streaming he's not sure of, but he thinks it would fail with bad sessions, bc streaming
 * is like downloading to them.
 * can have multiple sessions per checked out book to patron?  didn't get direct confirm, but I think so.
 *
 *
 * On Sleep Timer:
 * - Sleep timer functionality will not be coming to Findaway sdk.  Must roll our own.
 *
 * On testing newer Findaway sdk:
 * Kevin Kovach [10:21 AM]
 * Also, I don't know what your release schedule/plan is but if you have some time before you're planning to release
 * I might suggest you could look at a snapshot/development version of the next SDK update.
 * That would give you a head start on all of the upcoming fixes and changes.
 * If you add the following to your repositories in gradle you can access SNAPSHOTS...
 * maven {
 * url "http://maven.findawayworld.com/artifactory/libs-snapshot/"
 * }
 * The current one is, "implementation 'io.audioengine.mobile:all:8.0.0-SNAPSHOT'"
 * I've changed some of the packages around while I've been working on protecting the internal classes that were never really meant to be public.
 * I did this in response to working on the Javadocs as well.
 *
 *
 * NOTE:  Saw this error, have not been able to duplicate it since.  Might have been an emulator glitch:
 * E/AudioFlinger: not enough memory for AudioTrack size=131296
 *
 * 10-24 18:31:34.945 11861-11861/org.nypl.findawaysdkdemo E/AndroidRuntime: FATAL EXCEPTION: main
 * Process: org.nypl.findawaysdkdemo, PID: 11861
 * java.lang.RuntimeException: Unable to start activity ComponentInfo{org.nypl.findawaysdkdemo/org.nypl.findawayclientlibrary.PlayBookActivity}: java.lang.NullPointerException: Attempt to invoke virtual method 'void android.view.View.setOnClickListener(android.view.View$OnClickListener)' on a null object reference
 * at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:2665)
 * at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:2726)
 * at android.app.ActivityThread.-wrap12(ActivityThread.java)
 * at android.app.ActivityThread$H.handleMessage(ActivityThread.java:1477)
 * at android.os.Handler.dispatchMessage(Handler.java:102)
 * at android.os.Looper.loop(Looper.java:154)
 * at android.app.ActivityThread.main(ActivityThread.java:6119)
 * at java.lang.reflect.Method.invoke(Native Method)
 * at com.android.internal.os.ZygoteInit$MethodAndArgsCaller.run(ZygoteInit.java:886)
 * at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:776)
 * Caused by: java.lang.NullPointerException: Attempt to invoke virtual method 'void android.view.View.setOnClickListener(android.view.View$OnClickListener)' on a null object reference
 * at org.nypl.findawayclientlibrary.PlayBookActivity.onCreate(PlayBookActivity.java:155)
 * at android.app.Activity.performCreate(Activity.java:6679)
 * at android.app.Instrumentation.callActivityOnCreate(Instrumentation.java:1118)
 * at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:2618)
 * at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:2726) 
 * at android.app.ActivityThread.-wrap12(ActivityThread.java) 
 * at android.app.ActivityThread$H.handleMessage(ActivityThread.java:1477) 
 * at android.os.Handler.dispatchMessage(Handler.java:102) 
 * at android.os.Looper.loop(Looper.java:154) 
 * at android.app.ActivityThread.main(ActivityThread.java:6119) 
 * at java.lang.reflect.Method.invoke(Native Method) 
 * at com.android.internal.os.ZygoteInit$MethodAndArgsCaller.run(ZygoteInit.java:886) 
 * at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:776) 
 * 10-24 18:31:34.948 1735-2207/system_process W/ActivityManager:   Force finishing activity org.nypl.findawaysdkdemo/org.nypl.findawayclientlibrary.PlayBookActivity
 *
 * Created by daryachernikhova on 9/14/17.
 *
 * TODO:  11-06 14:38:53.216 1335-2043/? E/BufferQueueProducer: [Toast] cancelBuffer: BufferQueue has been abandoned
 *
 * TODO: if can't download because will run out of drive space, and did not check for that condition before started downloading,
 * then the download error has the explanation in the text of the error message, and needs to be caught and parsed.
 *
 * TODO:
 * The session key should very rarely expire (fall out of Findaway's cache.  If it does, there will be a Download Error, but it does not
 * currently have an easy-to-check code.  The session expiring could probably be found in the error message.  Since it's a rare event, it'd
 * be OK to resolve all major errors by obtaining a new session key and re-trying the download.
 */
public class PlayBookActivity extends BaseActivity implements View.OnClickListener, View.OnLongClickListener,
                                                              Observer<AudioEngineEvent>, SeekBar.OnSeekBarChangeListener {
  // so can filter all log msgs belonging to my app
  private static final String APP_TAG = "FDLIB.";
  // so can do a search in log msgs for just this class's output
  private static final String TAG = APP_TAG + "PlayBookActivity";

  // corresponds to book resources path on the file system
  //String bookId = null;

  PlayBookFragment playBookFragment = null;

  // the one engine to control them all
  private static AudioEngine audioEngine = null;

  // fulfills books
  DownloadEngine downloadEngine = null;
  private DownloadRequest downloadRequest;

  // plays drm-ed audio
  private PlaybackEngine playbackEngine;

  // follows all download engine events
  private Subscription eventsSubscription;


  // Kevin's
  String sessionIdKevin = "964b5796-c67f-492d-8024-a69f3ba9be53";
  // mine:
  String sessionIdReal1 = "be101b8f-5013-4328-9898-5fe24b302641";
  String sessionIdReal2 = "1a03523c-8557-49cd-a046-2560d5f21056";
  String sessionIdReal3 = "a54406b6-76cf-4241-bbf6-7f1e8b76d7f4";
  String sessionIdReal4 = "720ba35d-8920-4621-9749-17f7ebe56316";
  String sessionIdReal5 = "3a2c87d3-29d3-4d57-8a24-ba06fd8dcf62";

  // fake -- real one with a letter changed
  String sessionIdFake1 = "3a2c87d3-29d3-4d57-8a24-ba06fd8dcf61";

  // Kevin's test value
  // String contentId = "83380";
  // Darya's value keyed to the fulfillmentId of the book NYPL bought through Bibliotheca
  String contentId = "102244";

  // Kevin's test value
  //String license = "5744a7b7b692b13bf8c06865";
  // Darya's value keyed to licenseId of the book NYPL bought through Bibliotheca
  //String license = "57db1411afde9f3e7a3c041b";
  String license = "57ff8f27afde9f3cea3c041b";


  /*
  {"format":"MP3","items":[{"title":"Track 1","sequence":1,"part":0,"duration":16201},{"title":"Track 2","sequence":2,"part":0,"duration":336070},{"title":"Track 3","sequence":3,"part":0,"duration":247803},{"title":"Track 4","sequence":4,"part":0,"duration":348714},{"title":"Track 5","sequence":5,"part":0,"duration":508844},{"title":"Track 6","sequence":6,"part":0,"duration":323767},{"title":"Track 7","sequence":7,"part":0,"duration":386408},{"title":"Track 8","sequence":8,"part":0,"duration":415953},{"title":"Track 9","sequence":9,"part":0,"duration":275832},{"title":"Track 10","sequence":10,"part":0,"duration":372851},{"title":"Track 11","sequence":11,"part":0,"duration":515793},{"title":"Track 12","sequence":12,"part":0,"duration":289886},{"title":"Track 13","sequence":13,"part":0,"duration":345239},{"title":"Track 14","sequence":14,"part":0,"duration":334111},{"title":"Track 15","sequence":15,"part":0,"duration":356080},{"title":"Track 16","sequence":16,"part":0,"duration":459055},{"title":"Track 17","sequence":17,"part":0,"duration":310993},{"title":"Track 18","sequence":18,"part":0,"duration":294771},{"title":"Track 19","sequence":19,"part":0,"duration":443277},{"title":"Track 20","sequence":20,"part":0,"duration":264286},{"title":"Track 21","sequence":21,"part":0,"duration":348322},{"title":"Track 22","sequence":22,"part":0,"duration":355009},{"title":"Track 23","sequence":23,"part":0,"duration":346441},{"title":"Track 24","sequence":24,"part":0,"duration":220191},{"title":"Track 25","sequence":25,"part":0,"duration":373164},{"title":"Track 26","sequence":26,"part":0,"duration":196394},{"title":"Track 27","sequence":27,"part":0,"duration":399313},{"title":"Track 28","sequence":28,"part":0,"duration":337089},{"title":"Track 29","sequence":29,"part":0,"duration":297069},{"title":"Track 30","sequence":30,"part":0,"duration":240645},{"title":"Track 31","sequence":31,"part":0,"duration":387323},{"title":"Track 32","sequence":32,"part":0,"duration":298036},{"title":"Track 33","sequence":33,"part":0,"duration":367600},{"title":"Track 34","sequence":34,"part":0,"duration":269197},{"title":"Track 35","sequence":35,"part":0,"duration":494189},{"title":"Track 36","sequence":36,"part":0,"duration":393932},{"title":"Track 37","sequence":37,"part":0,"duration":309321},{"title":"Track 38","sequence":38,"part":0,"duration":341321},{"title":"Track 39","sequence":39,"part":0,"duration":415091},{"title":"Track 40","sequence":40,"part":0,"duration":304044},{"title":"Track 41","sequence":41,"part":0,"duration":417076},{"title":"Track 42","sequence":42,"part":0,"duration":368932},{"title":"Track 43","sequence":43,"part":0,"duration":411198},{"title":"Track 44","sequence":44,"part":0,"duration":298271},{"title":"Track 45","sequence":45,"part":0,"duration":300230},{"title":"Track 46","sequence":46,"part":0,"duration":342758},{"title":"Track 47","sequence":47,"part":0,"duration":447848},{"title":"Track 48","sequence":48,"part":0,"duration":527914},{"title":"Track 49","sequence":49,"part":0,"duration":406523},{"title":"Track 50","sequence":50,"part":0,"duration":334398},{"title":"Track 51","sequence":51,"part":0,"duration":307518},{"title":"Track 52","sequence":52,"part":0,"duration":407071},{"title":"Track 53","sequence":53,"part":0,"duration":488390},{"title":"Track 54","sequence":54,"part":0,"duration":432854},{"title":"Track 55","sequence":55,"part":0,"duration":364074},{"title":"Track 56","sequence":56,"part":0,"duration":367130},{"title":"Track 57","sequence":57,"part":0,"duration":329566},{"title":"Track 58","sequence":58,"part":0,"duration":305925},{"title":"Track 59","sequence":59,"part":0,"duration":528070},{"title":"Track 60","sequence":60,"part":0,"duration":416031},{"title":"Track 61","sequence":61,"part":0,"duration":524283},{"title":"Track 62","sequence":62,"part":0,"duration":306683},{"title":"Track 63","sequence":63,"part":0,"duration":297932},{"title":"Track 64","sequence":64,"part":0,"duration":384972},{"title":"Track 65","sequence":65,"part":0,"duration":383639},{"title":"Track 66","sequence":66,"part":0,"duration":441683},{"title":"Track 67","sequence":67,"part":0,"duration":263528},{"title":"Track 68","sequence":68,"part":0,"duration":412478},{"title":"Track 69","sequence":69,"part":0,"duration":252792},{"title":"Track 70","sequence":70,"part":0,"duration":324080},{"title":"Track 71","sequence":71,"part":0,"duration":361487},{"title":"Track 72","sequence":72,"part":0,"duration":403910},{"title":"Track 73","sequence":73,"part":0,"duration":510594},{"title":"Track 74","sequence":74,"part":0,"duration":535594},{"title":"Track 75","sequence":75,"part":0,"duration":472769},{"title":"Track 76","sequence":76,"part":0,"duration":284008},{"title":"Track 77","sequence":77,"part":0,"duration":419114},{"title":"Track 78","sequence":78,"part":0,"duration":417990},{"title":"Track 79","sequence":79,"part":0,"duration":32318}],
    "sessionKey":"be101b8f-5013-4328-9898-5fe24b302641",
          "accountId":"3M","checkoutId":"59fca29f5cba2a0c0b900b7b","fulfillmentId":"102244",
          "licenseId":"57ff8f27afde9f3cea3c041b"}
  */

  // the part we're downloading right now
  int part = 0;

  // the chapter we're downloading right now
  int chapter = 1;

  int seekTo;

  long lastPlaybackPosition;


  /* ---------------------------------- LIFECYCLE METHODS ----------------------------------- */

  /**
   * Loads player UI fragment.
   * Triggers the creation of media session-holding objects.
   *
   * We are going to play the music in the AudioService class, but control it from the Activity class,
   * where the application's user interface operates.  To accomplish this, we need bind to the Service class.
   * This used to be done manually, but is now handled automagically by the MediaSession system.
   *
   * @param savedInstanceState
   */
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.findaway_playback_activity_main);

    // change the title displayed in the top bar from the app name (default) to something indicative
    //getSupportActionBar().setTitle("Audio Player UI Here");

    // Check that the activity is using the layout version with the fragment_container FrameLayout
    if (findViewById(R.id.fragment_container) != null) {

      // If we're being restored from a previous state, we don't need to do anything,
      // return or else we could end up with overlapping fragments.
      if (savedInstanceState != null) {
        return;
      }

      // Create a new Fragment that offers the user a player interface with media control buttons.
      playBookFragment = new PlayBookFragment();

      // In case this activity was started with special instructions from an
      // Intent, pass the Intent's extras to the fragment as arguments
      playBookFragment.setArguments(getIntent().getExtras());

      // Add the first fragment to the 'fragment_container' view component.
      // NOTE:  Because the fragment has been added to the FrameLayout container at runtime
      // instead of defining it in the activity's layout with a <fragment> element
      // the activity can remove the fragment and replace it with a different one later on in the code.
      this.loadFragment(playBookFragment, R.id.fragment_container, false);

    } else {
      // layout doesn't allow fragment loading
      throw new IllegalStateException("Missing layout container with id 'fragment_container'. Cannot continue.");
    }


    // Only update from the intent if we are not recreating from a config change:
    if (savedInstanceState == null) {
      // read the passed-in book id later on
      Intent intent = getIntent();
      if (intent != null) {
        Bundle extras = intent.getExtras();
        if (extras != null) {
            //bookId = extras.getString("audiobook_id", null);
        }
      }
    }


    // one-time call to start up the AudioEngine service
    this.initAudioEngine();

    // ask the AudioEngine to start a DownloadEngine
    this.initDownloadEngine();

    // ask the AudioEngine to start a PlaybackEngine
    this.initPlaybackEngine();
  }// onCreate



  /**
   * Stop the media playback when this activity is destroyed, and clean up resources.
   *
   * TODO:  what sdk calls should be made here?
   */
  @Override
  protected void onDestroy() {
    super.onDestroy();
  }


  /**
   * If this activity is not currently hopping, it shouldn't be listening to events.
   */
  @Override
  protected void onPause() {
    super.onPause();

    if (!eventsSubscription.isUnsubscribed()) {
      eventsSubscription.unsubscribe();
    }
  }


  /**
   * Called either after onCreate()-onStart(), or after the activity is restored after
   * having been paused.  Resume listening to download events while activity is in foreground.
   */
  @Override
  protected void onResume() {
    super.onResume();

    // a stream of _all_ download events for the supplied content id
    // the onCompleted(), onError() and onNext() methods are the ones implemented in the activity itself.
    eventsSubscription = downloadEngine.events(contentId).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(this);

    // a stream of just download status changes for the supplied content id
    downloadEngine.getStatus(contentId).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).take(1).subscribe(new Observer<DownloadStatus>() {

      @Override
      public void onCompleted() {

      }

      @Override
      public void onError(Throwable e) {

      }

      @Override
      public void onNext(DownloadStatus downloadStatus) {
        if (downloadStatus == DownloadStatus.DOWNLOADED) {
          if (playBookFragment != null) {
            playBookFragment.redrawDownloadButton(getResources().getString(R.string.delete));
          } else {
            Toast.makeText(getApplicationContext(), R.string.delete, Toast.LENGTH_LONG).show();
          }
        } else {
          if (downloadStatus == DownloadStatus.QUEUED || downloadStatus == DownloadStatus.DOWNLOADING) {
            if (playBookFragment != null) {
              playBookFragment.redrawDownloadButton(getResources().getString(R.string.pause));
            } else {
              Toast.makeText(getApplicationContext(), R.string.pause, Toast.LENGTH_LONG).show();
            }
          } else {
            if (downloadStatus == DownloadStatus.PAUSED || downloadStatus == DownloadStatus.NOT_DOWNLOADED) {
              if (playBookFragment != null) {
                playBookFragment.redrawDownloadButton(getResources().getString(R.string.download));
              } else {
                Toast.makeText(getApplicationContext(), R.string.download, Toast.LENGTH_LONG).show();
              }
            }
          }
        }
      }
    }); //downloadEngine.status.subscribe


    downloadEngine.getProgress(contentId).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).take(1).subscribe(new Observer<Integer>() {

      @Override
      public void onCompleted() {

        Log.d(TAG, "Initial progress complete.");
      }

      @Override
      public void onError(Throwable e) {

        Log.d(TAG, "Initial progress error: " + e.getMessage());
      }

      @Override
      public void onNext(Integer progress) {

        Log.d(TAG, "Got initial progress " + progress);

        if (playBookFragment != null) {
          playBookFragment.redrawDownloadProgress(progress, 0);
        }
      }
    }); //downloadEngine.progress.subscribe


    eventsSubscription = playbackEngine.events().subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(this);
  }


  /**
   * TODO:  What sdk calls need to be made when the player is being restored?
   *
   * Called after onStart() and before onPostCreate(), and only if the activity is being
   * re-initialized from a previously saved state.
   * Don't need to check if Bundle is null, unlike you do in onCreate().
   * NOTE:  Call savedInstanceState.get[...]() methods after calling super().
   *
   * @param savedInstanceState
   */
  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    // always call first, so can restore view hierarchy
    super.onRestoreInstanceState(savedInstanceState);
  }


  /**
   * TODO:  what sdk methods clean up the scene, but let the player to keep playing?
   *
   * Called on activity stop, attempts to save activity state into memory,
   * so it can be reconstituted if the activity is restored.
   * NOTE:  Always call the superclass implementation, so it can save the state of the view hierarchy.
   * NOTE:  In order for the Android system to restore the state of the views in your activity,
   * each view must have a unique ID, supplied by the android:id attribute.
   * NOTE:  Call savedInstanceState.put[...]() methods before calling super().
   *
   * @param savedInstanceState
   */
  @Override
  protected void onSaveInstanceState(Bundle savedInstanceState) {
    super.onSaveInstanceState(savedInstanceState);
  }


  /**
   * Start the AudioService instance when the Activity instance starts.
   * Pass the song list we've assembled to the AudioService.
   *
   *
   */
  @Override
  protected void onStart() {
    super.onStart();
    Log.d(TAG, "Activity.onStart");

  }


  /**
   * TODO:  what sdk methods clean up the scene, but let the player to keep playing?
   *
   */
  @Override
  protected void onStop() {
    super.onStop();
  }


  /* ---------------------------------- /LIFECYCLE METHODS ----------------------------------- */



  /* ------------------------------------ PLAYBACK EVENT HANDLERS ------------------------------------- */


  /**
   * AudioEngine needs the sessionKey that comes from the Bibliotheca
   * https://partner.yourcloudlibrary.com/cirrus/library/[libraryId]/GetItemAudioFulfillment endpoint.
   */
  private void initAudioEngine() {
    try {
      AudioEngine.init(this, sessionIdReal1, LogLevel.VERBOSE);
      audioEngine = AudioEngine.getInstance();
    } catch (AudioEngineException e) {
      Log.e(TAG, "Error getting audio engine: " + e.getMessage());
      e.printStackTrace();
    } catch (Exception e) {
      Log.e(TAG, "Error getting audio engine: " + e.getMessage());
      e.printStackTrace();
    }
  }


  /**
   * TODO: DownloadEngine needs the following setup: [...].
   */
  private void initDownloadEngine() {
    try {
      if (audioEngine == null) {
        throw new Exception("Must initialize AudioEngine before trying to get a DownloadEngine.");
      }

      downloadEngine = audioEngine.getDownloadEngine();
    } catch (AudioEngineException e) {
      // Call to getDownloadEngine will throw an exception if you have not previously
      // called init() on AudioEngine with a valid Context and Session.
      Log.e(TAG, "Error getting download engine: " + e.getMessage());
      e.printStackTrace();
    } catch (Exception e) {
      Log.e(TAG, "Error getting download engine: " + e.getMessage());
      e.printStackTrace();
    }
  }


  /**
   * TODO: .
   */
  private void initPlaybackEngine() {
    try {
      if (audioEngine == null) {
        throw new Exception("Must initialize AudioEngine before trying to get a PlaybackEngine.");
      }

      playbackEngine = audioEngine.getPlaybackEngine();
    } catch (AudioEngineException e) {
      Log.e(TAG, "Error getting playback engine: " + e.getMessage());
      e.printStackTrace();
    } catch (Exception e) {
      Log.e(TAG, "Error getting playback engine: " + e.getMessage());
      e.printStackTrace();
    }

    seekTo = 0;
    lastPlaybackPosition = 0;
  }


  /**
   * Make a download request, and ask the download engine to fulfill it.
   */
  private void downloadAudio() {

    // To start downloading the audio files for the chapter supplied: A valid chapter is a combination of content (book) id, part number, and chapter number.
    // NOTE: Get the list of chapters with their durations (in milliseconds) when call the get_audiobook
    // in the REST api.  The response to get_audiobook returns three sections, "active_products", "inactive_products",
    // and "audiobook".  You then use the license id from "active_products" to call checkout, and use the "chapters"
    // list from "audiobook" to make the download requests in the android sdk.  Here's some api info:
    // http://developer.audioengine.io/api/v4/class-docs/#/definitions/Audiobook .

    // NOTE:  DownloadType.TO_END gets book from specified chapter to the end of the book.
    // DownloadType.TO_END_WRAP gets from specified chapter to the end of the book, then wraps around and
    // gets all the beginning chapters, too.
    // DownloadType.SINGLE gets just that chapter.
    // The system does skip any chapters that are already downloaded.  So, if we need to re-download a chapter,
    // we'd have to delete it first then call download.

    Log.e(TAG, "before making downloadRequest, part=" + part + ", chapter=" + chapter);
    downloadRequest = DownloadRequest.builder().contentId(contentId).part(part).chapter(chapter).licenseId(license).type(DownloadType.TO_END_WRAP).build();
    Log.e(TAG, "after making downloadRequest, part=" + part + ", chapter=" + chapter);

    try {
      // Audio files are downloaded and stored under the application's standard internal files directory. This directory is deleted when the application is removed.
      Log.e(TAG, "before downloadEngine.download \n\n\n");
      downloadEngine.download(downloadRequest);
      Log.e(TAG, "after downloadEngine.download \n\n\n");
    } catch (Exception e) {
      Log.e(TAG, "Error getting download engine: " + e.getMessage());
      e.printStackTrace();
    }


    /* Extra Things I Could Do according to docs:

    // To get a DownloadRequest for a specific download request id:
    // TODO:  What does this allow me to do?
    // TODO:  Where would I get the id?  Where is the method defined?
    String requestId = "123";
    DownloadRequest downloadRequest(requestId);

    // To get all DownloadRequests:
    List<DownloadRequest> downloadRequests();

    // To pause the download for the supplied content keeping existing progress.  pauseAll() to pause all requests
    // TODO: where is the method defined?
    void pause(DownloadRequest request);

    // To cancel the download for the supplied content, removing any existing progress.  similarly, cancelAll().
    void cancel(DownloadRequest request);

    // To delete the audio files for a piece of content from the device.
    void delete(DeleteRequest request)

    // Get the download status for a book overall
    Observable<DownloadStatus> getStatus(String contentId) throws ContentNotFoundException

    // Get the download status for a specific chapter of a book
    Observable<DownloadStatus> getStatus(String contentId, Integer part, Integer chapter) throws ChapterNotFoundException

    // Get the download progress of a book as a percentage from 0 to 100
    Observable<Integer> getProgress(String contentId) throws ContentNotFoundException

    // Get the download progress of a chapter as a percentage from 0 to 100
    Observable<Integer> getProgress(String contentId, Integer part, Integer chapter) throws ChapterNotFoundException

    // Currently, you are not able to request a download for chapters from 2 different books so these are essentialy the same.
    // TODO: So, to download more than one book, I'll need to have a download queue?
    // Subscribe to all events for a given content id or request id
    Observable<DownloadEvent> events(String contentId / request id)

    */

  }


  @Override
  public void onClick(View view) {
    if (view.getId() == R.id.download_button) {

      Button downloadButton = (Button) view;

      if (downloadButton.getText().equals(getString(R.string.download))) {
        downloadAudio();

      } else if (downloadButton.getText().equals(getString(R.string.pause))) {

        downloadEngine.pause(downloadRequest);

      } else if (downloadButton.getText().equals(getString(R.string.resume))) {

        downloadEngine.download(downloadRequest);

      } else if (downloadButton.getText().equals(getString(R.string.delete))) {

        downloadEngine.delete(DeleteRequest.builder().contentId(contentId).build());
      }
    }


    if (view.getId() == R.id.play && view.getTag().equals(getResources().getString(R.string.play))) {
      Log.d(TAG, "playback PLAY button clicked");

      // NOTE: Can specify the chapter to start playing at here.
      playbackEngine.play(license, contentId, part, chapter, (int) lastPlaybackPosition);
      if (playBookFragment != null) {
        playBookFragment.redrawPlayButton(getResources().getString(R.string.pause), R.drawable.ic_pause_circle_outline_black_24dp);
      }

    } else {
        if (view.getId() == R.id.play && view.getTag().equals(getResources().getString(R.string.pause))) {
        Log.d(TAG, "playback PAUSE button clicked");
        playbackEngine.pause();

        if (playBookFragment != null) {
          playBookFragment.redrawPlayButton(getResources().getString(R.string.resume), R.drawable.ic_play_circle_outline_black_24dp);
        }

      } else {
        if (view.getId() == R.id.play && view.getTag().equals(getResources().getString(R.string.resume))) {
          Log.d(TAG, "playback RESUME button clicked");
          playbackEngine.resume();

          if (playBookFragment != null) {
            playBookFragment.redrawPlayButton(getResources().getString(R.string.pause), R.drawable.ic_pause_circle_outline_black_24dp);
          }
        } else {
          if (view.getId() == R.id.back10) {
            // TODO: what code would make sure not crossing boundary condition on file length?
            Log.d(TAG, "playback BACK10 button clicked");
            playbackEngine.seekTo(playbackEngine.getPosition() - 10000);
          } else {
            if (view.getId() == R.id.forward10) {
              Log.d(TAG, "playback FWD10 button clicked");
              playbackEngine.seekTo(playbackEngine.getPosition() + 10000);
            } else {
              if (view.getId() == R.id.previous) {
                playbackEngine.previousChapter();
              } else {
                if (view.getId() == R.id.next) {
                  playbackEngine.nextChapter();
                } else {
                  if (view.getId() == R.id.playback_speed_button) {
                    // change playback speed
                    if (((ToggleButton) view).isSelected() == true) {
                      playbackEngine.setSpeed(1.0f);
                      // setChecked sets the intrinsic boolean dataMember associated with your view object
                      ((ToggleButton) view).setChecked(false);
                      // setSelected sets the UI associated with your view object
                      ((ToggleButton) view).setSelected(false);
                      Log.d(TAG, "toggle button clicked: " + ((ToggleButton) view).isChecked() + ", speed=" + playbackEngine.getSpeed());
                    } else {
                      playbackEngine.setSpeed(2.0f);
                      ((ToggleButton) view).setChecked(true);
                      ((ToggleButton) view).setSelected(true);
                    }
                  } else {
                    Log.e(TAG, "Cannot recognize clicked button.");
                  }
                }
              }
            }
          }
        }
      }
    }

  }// onClick


  @Override
  public void onCompleted() {
    // ignore
  }


  @Override
  public void onError(Throwable e) {
    Log.e(TAG, "There was an error in the download or playback process: " + e.getMessage());
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


  @Override
  public boolean onLongClick(View view) {

    if (view.getId() == R.id.download_button) {

      Button downloadButton = (Button) view;

      if (downloadButton.getText().equals(getString(R.string.pause))) {

        downloadEngine.cancel(downloadRequest);

        return true;
      }
    }

    return false;
  }


  /**
   * Catches AudioEngineEvent events and redirects processing to either the download or playback event-handling methods.
   *
   * Download events are described here:  http://developer.audioengine.io/sdk/android/v7/download-engine .
   * @param engineEvent
   */
  @Override
  public void onNext(AudioEngineEvent engineEvent) {
    if (engineEvent instanceof DownloadEvent) {
      this.onNext((DownloadEvent) engineEvent);
    } else {
      if (engineEvent instanceof PlaybackEvent) {
        this.onNext((PlaybackEvent) engineEvent);
      }
    }
  }


  /**
   * Catches DownloadEngine events.
   *
   * Download events are described here:  http://developer.audioengine.io/sdk/android/v7/download-engine .
   * @param downloadEvent
   */
  public void onNext(DownloadEvent downloadEvent) {
    File filesDir = getFilesDir();
    if (filesDir.exists()) {
      Log.d(TAG, "filesDir.getAbsolutePath=" + filesDir.getAbsolutePath());
      String[] filesList = filesDir.list();
      Log.d(TAG, "filesDir.filesList=" + filesList.length);
    }

    String sharedPrefsPath = "shared_prefs/";
    File sharedPrefsDir = new File(getFilesDir(), "../" + sharedPrefsPath);
    if (sharedPrefsDir.exists()) {
      Log.d(TAG, "sharedPrefsDir.getAbsolutePath=" + sharedPrefsDir.getAbsolutePath());
      String[] filesList = sharedPrefsDir.list();
      Log.d(TAG, "sharedPrefsDir.filesList=" + filesList.length);
    }

    if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
      File externalFilesDir = getExternalFilesDir(null);
      if (externalFilesDir.exists()) {
        Log.d(TAG, "externalFilesDir.getAbsolutePath=" + externalFilesDir.getAbsolutePath());
        String[] externalFilesList = externalFilesDir.list();
        Log.d(TAG, "externalFilesDir.externalFilesList=" + externalFilesList.length);
      }
    }


    Log.d(TAG, "downloadEvent.chapter=" + downloadEvent.chapter);
    Log.d(TAG, "downloadEvent.chapter_download_percentage=" + downloadEvent.chapterPercentage);
    Log.d(TAG, "downloadEvent.content=" + downloadEvent.content);
    Log.d(TAG, "downloadEvent.content_download_percentage=" + downloadEvent.contentPercentage);
    Log.d(TAG, "downloadEvent.toString=" + downloadEvent.toString());

    if (downloadEvent.isError()) {

      // TODO:  getting E/SQLiteLog: (1) no such table: listenedEvents
      // don't think it's related to the download error.

      // NOTE:  if I use the wrong license to init AudioEngine with, I get download error, with message:
      // Download Event e6c50396-904a-4511-a5c0-acfbf9573401: 31051
      // and code 31051, which corresponds to HTTP_ERROR (see this api page for all error codes:
      // http://developer.audioengine.io/sdk/android/v7/download-engine ).
      // and also the chapter object is all nulled when onNext isError.
      // The downloadEvent stack trace is not helpful, but you can see helpful info in the stack trace
      // that's thrown from the findaway internal sdk code:
      // 10-30 19:45:33.548 8316-8316/org.nypl.findawaysdkdemo E/FDLIB.PlayBookActivity: before making downloadRequest, part=0, chapter=1
      // 10-30 19:45:33.548 8316-8316/org.nypl.findawaysdkdemo I/System.out: Sending AutoValue_DownloadRequest to onNext. Observers? true
      // 10-30 19:45:33.549 8316-8378/org.nypl.findawaysdkdemo D/OkHttp: --> POST https://api.findawayworld.com/v4/audiobooks/83380/playlists http/1.1
      // 10-30 19:45:33.549 8316-8378/org.nypl.findawaysdkdemo D/OkHttp: Content-Type: application/json; charset=UTF-8
      // 10-30 19:45:33.549 8316-8378/org.nypl.findawaysdkdemo D/OkHttp: Content-Length: 71
      // 10-30 19:45:33.549 8316-8378/org.nypl.findawaysdkdemo D/OkHttp: --> END POST
      // 10-30 19:45:33.550 1455-1482/? W/audio_hw_generic: Not supplying enough data to HAL, expected position 3501424 , only wrote 3501360
      // 10-30 19:45:33.595 8316-8378/org.nypl.findawaysdkdemo D/OkHttp: <-- 400 Bad Request https://api.findawayworld.com/v4/audiobooks/83380/playlists (45ms)
      // and some nicer stack trace, coming from the findaway sdk:
      // 10-30 19:54:28.605 13497-15009/org.nypl.findawaysdkdemo W/System.err:     at io.audioengine.mobile.persistence.Download.getPlaylist(Download.java:649)

      Toast.makeText(this, "Download error occurred: " + downloadEvent.getMessage(), Toast.LENGTH_LONG).show();

      Log.e(TAG, "downloadEvent.getMessage=" + downloadEvent.getMessage());
      Log.e(TAG, "downloadEvent.getCause=", downloadEvent.getCause());
      Log.e(TAG, "downloadEvent.code=" + downloadEvent.code);


      Log.e(TAG, "downloadEvent.getStackTrace:");
      StackTraceElement[] elements = Thread.currentThread().getStackTrace();
      for (int i = 0; i < elements.length; i++) {
        Log.e("Test", String.format("stack element[%d]: %s", i, elements[i]));
      }

      // NOTE:  The error sending is being re-worked by Findaway, and might change.  Here's the
      // description of how it currently works:
      // "Currently, if the license you supply is not found you'll get a AUDIO_NOT_FOUND.
      // If the license is found but not valid for the requested content you'll get an HTTP_ERROR.
      // If the license does not match the checkout you'll get a FORBIDDEN.
      // If the license is valid but not actually checked out you'll get a HTTP_ERROR
      // Lastly, HTTP_ERROR is currently the catch all. So, 500's on our end will result in that error code as well."
      // All codes listed here:  http://developer.audioengine.io/sdk/android/v7/download-engine .
      if (DownloadEvent.HTTP_ERROR.equals(downloadEvent.code)) {
        // decide if want to re-try downloading or throw a "sorry" message to user.
        Log.e(TAG, "DownloadEvent.HTTP_ERROR");
      } else if (DownloadEvent.FORBIDDEN.equals(downloadEvent.code)) {
        Log.e(TAG, "DownloadEvent.FORBIDDEN");
      } else if (DownloadEvent.ERROR_DOWNLOADING_FILE.equals(downloadEvent.code)) {
        Log.e(TAG, "DownloadEvent.ERROR_DOWNLOADING_FILE");
        // TODO: one possibility is downloadEvent.getMessage() == "write failed: ENOSPC (No space left on device)",
        // which would be hard to catch based on a string message, but common enough to need handling.

      }

    } else {
      if (downloadEvent.code.equals(DownloadEvent.DOWNLOAD_STARTED)) {

        Toast.makeText(this, getString(R.string.downloadStarted), Toast.LENGTH_SHORT).show();

        if (playBookFragment != null) {
          // NOTE: changes the downloadButton.getText() to return "Pause"
          playBookFragment.redrawDownloadButton(getResources().getString(R.string.pause));
        }
      } else if (downloadEvent.code.equals(DownloadEvent.DOWNLOAD_PAUSED)) {

        Toast.makeText(this, getString(R.string.downloadPaused), Toast.LENGTH_SHORT).show();
        if (playBookFragment != null) {
          playBookFragment.redrawDownloadButton(getResources().getString(R.string.resume));
        }

      } else if (downloadEvent.code.equals(DownloadEvent.DOWNLOAD_CANCELLED)) {

        Toast.makeText(this, getString(R.string.downloadCancelled), Toast.LENGTH_SHORT).show();
        resetProgress();
        if (playBookFragment != null) {
          playBookFragment.redrawDownloadButton(getResources().getString(R.string.download));
        }

      } else if (downloadEvent.code.equals(DownloadEvent.CHAPTER_DOWNLOAD_COMPLETED)) {

        Toast.makeText(this, getString(R.string.chapterDownloaded, downloadEvent.chapter.friendlyName()), Toast.LENGTH_SHORT).show();

      } else if (downloadEvent.code.equals(DownloadEvent.CONTENT_DOWNLOAD_COMPLETED)) {

        Toast.makeText(this, getString(R.string.downloadComplete), Toast.LENGTH_SHORT).show();
        if (playBookFragment != null) {
          playBookFragment.redrawDownloadButton(getResources().getString(R.string.delete));
        }

      } else if (downloadEvent.code.equals(DownloadEvent.DELETE_COMPLETE)) {

        Toast.makeText(this, getString(R.string.deleteComplete), Toast.LENGTH_SHORT).show();
        resetProgress();
        if (playBookFragment != null) {
          playBookFragment.redrawDownloadButton(getResources().getString(R.string.download));
        }

      } else if (downloadEvent.code.equals(DownloadEvent.DOWNLOAD_PROGRESS_UPDATE)) {

        setProgress(downloadEvent);

      } else {

        Log.w(TAG, "Unknown download event: " + downloadEvent.code);
      }
    }
  }// onNext(DownloadEvent)


  /**
   *
   * @param playbackEvent
   */
  public void onNext(PlaybackEvent playbackEvent) {

    if (playbackEvent.code.equals(PlaybackEvent.PLAYBACK_PROGRESS_UPDATE)) {
      if (playBookFragment != null) {
        playBookFragment.redrawPlaybackPosition(playbackEvent);
      }

      lastPlaybackPosition = playbackEvent.position;
    } else if (playbackEvent.code.equals(PlaybackEvent.PLAYBACK_STARTED)) {

      Toast.makeText(this, "Playback started.", Toast.LENGTH_SHORT).show();
    } else if (playbackEvent.code.equals(PlaybackEvent.PLAYBACK_PAUSED)) {

      Toast.makeText(this, "Playback paused.", Toast.LENGTH_SHORT).show();
    } else if (playbackEvent.code.equals(PlaybackEvent.PLAYBACK_STOPPED)) {

      Toast.makeText(this, "Playback stopped.", Toast.LENGTH_SHORT).show();
    } else if (playbackEvent.code.equals(PlaybackEvent.CHAPTER_PLAYBACK_COMPLETED)) {
      // NOTE:  "Do X to end of chapter" functionality can go here.
      Toast.makeText(this, "Chapter completed.", Toast.LENGTH_SHORT).show();
    }
  }


  public void onPlaybackComplete() {
    // TODO
  }


  /**
   * Respond to user manually selecting playback position within the chapter.
   *
   * @param seekBar
   * @param progress
   * @param fromUser
   */
  @Override
  public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

    if (fromUser) {
      if (playBookFragment != null) {
        playBookFragment.redrawPlaybackPosition(seekBar, progress, fromUser);
      }

      seekTo = progress;
    }
  }


  @Override
  public void onStartTrackingTouch(SeekBar seekBar) {
    //if (seekBar.getId() == this.seekBar.getId()) {

      if (!eventsSubscription.isUnsubscribed()) {

        eventsSubscription.unsubscribe();
      }
    //}
  }


  @Override
  public void onStopTrackingTouch(SeekBar seekBar) {
    //if (seekBar.getId() == this.seekBar.getId()) {

      playbackEngine.seekTo(seekTo);
      eventsSubscription = playbackEngine.events()
              .subscribeOn(Schedulers.io())
              .observeOn(AndroidSchedulers.mainThread())
              .subscribe(this);
    //}
  }


  /**
   * TODO:  Hook up playing audio functionality here.
   */
  private void playAudio() {
  }


  /**
   * TODO:  What connections do we have to the Findaway player, to be able to implement media controls
   * (play/pause/rewind X seconds, ff X seconds, seek bar, playback speed, etc.)?
   *
   */
  private void scheduleSeekbarUpdate() {
  }

  /* ------------------------------------ /PLAYBACK EVENT HANDLERS ------------------------------------- */


  /* ------------------------------------ UI METHODS ------------------------------------- */

    private void setProgress(DownloadEvent downloadEvent) {
      if (playBookFragment != null) {
        playBookFragment.redrawDownloadProgress(downloadEvent);
      }
    }

    private void resetProgress() {
      if (playBookFragment != null) {
        playBookFragment.resetProgress();
      }
    }


  /* ------------------------------------ /UI METHODS ------------------------------------- */


  /* ------------------------------------ UTILITY METHODS ------------------------------------- */

  /**
   * TODO: Do I need to manage app permissions manually, or will the Findaway sdk do that for me?
   * If it does it for me, where, what permissions, what reactions do I need to handle in the calling code?
   */
  private boolean checkAppPermissions() {
    return true;
  }


  /**
   * TODO:  How to hook up my UI buttons to Findaway MediaController?
   *
   * Wrapper around getSupportMediaController(), so we can call it from a child fragment.
   * @return  MediaControllerCompat that provides media control onscreen buttons.
   */
  public MediaControllerCompat getController() {
    return getSupportMediaController();
  }


  /* ------------------------------------ /UTILITY METHODS ------------------------------------- */


}
