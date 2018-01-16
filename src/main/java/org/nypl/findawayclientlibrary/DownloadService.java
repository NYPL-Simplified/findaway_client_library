package org.nypl.findawayclientlibrary;

import java.io.File;

import android.content.Context;
import android.os.Environment;
import android.widget.Toast;

import io.audioengine.mobile.DownloadStatus;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import io.audioengine.mobile.AudioEngineException;
import io.audioengine.mobile.DownloadEngine;
import io.audioengine.mobile.DownloadEvent;
import io.audioengine.mobile.DownloadRequest;
import io.audioengine.mobile.DownloadType;

import org.nypl.findawayclientlibrary.util.LogHelper;



/**
 * Translates calls from our app's UI such as "download chapter", "pause/retry download",
 * into calls to the Findaway SDK.
 *
 * Is not currently a service in the sense that does not stand on its own thread.
 *
 * Created by daryachernikhova on 1/5/18.
 */
public class DownloadService implements Observer<DownloadEvent> {
  // so can do a search in log msgs for just this class's output
  private static String TAG;

  public static final Integer CHAPTER_PART_DEFAULT = new Integer(0);

  public static final Integer DOWNLOAD_ERROR = new Integer(-1);
  public static final Integer DOWNLOAD_SUCCESS = new Integer(0);
  public static final Integer DOWNLOAD_NEEDED = new Integer(1);
  public static final Integer DOWNLOAD_RUNNING = new Integer(2);
  public static final Integer DOWNLOAD_PAUSED = new Integer(3);
  public static final Integer DOWNLOAD_STOPPED = new Integer(4);

  private AudioService audioService;

  // Provides context for methods s.a. getFilesDir(), and allows events caught
  // by this class to be reflected in the app's UI.
  private PlayBookActivity callbackActivity = null;

  // fulfills books
  DownloadEngine downloadEngine = null;
  private DownloadRequest downloadRequest;


  public DownloadService(String APP_TAG, AudioService audioService, PlayBookActivity callbackActivity) {
    TAG = APP_TAG + "DownloadService";
    //this.sessionId = sessionId;
    this.audioService = audioService;
    this.callbackActivity = callbackActivity;
  }


  /**
   * Get the DownloadEngine instance.  Initialization is not guaranteed.
   * @return
   */
  public DownloadEngine getDownloadEngine() {
    return downloadEngine;
  }


  /**
   * Get an initialized findaway sdk DownloadEngine class, with your sessionId filled in.
   */
  public void initDownloadEngine() {
    try {
      if (audioService.getAudioEngine() == null) {
        throw new Exception("Must initialize AudioEngine before trying to get a DownloadEngine.");
      }

      downloadEngine = audioService.getAudioEngine().getDownloadEngine();
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
   * Subscribe to a stream of _all_ download events for the supplied content id.
   * @return
   */
  public Subscription subscribeDownloadEventsAll(Observer<DownloadEvent> observer, String contentId) {
    Subscription eventsSubscription = this.getDownloadEngine().events(contentId).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(observer);
    return eventsSubscription;
  }


  /**
   * Subscribe to a stream of download events that reflect the progress of an ongoing download request.
   * @return
   */
  public Subscription subscribeDownloadEventsProgress(Observer<Integer> observer, String contentId) {
    Subscription eventsSubscription = null;

    // if we were given an outside observer to let listen to download progress
    if (observer != null) {
      eventsSubscription = this.getDownloadEngine().getProgress(contentId).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(observer);
      return eventsSubscription;
    }

    // make our own observer
    this.getDownloadEngine().getProgress(contentId).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).take(1).subscribe(new Observer<Integer>() {
      @Override
      public void onCompleted() {
        LogHelper.d(TAG, "Initial download progress complete.");
      }

      @Override
      public void onError(Throwable e) {
        LogHelper.d(TAG, "Initial download progress error: ", e.getMessage());
      }

      @Override
      public void onNext(Integer progress) {
        LogHelper.d(TAG, "Got initial download progress ", progress);

        callbackActivity.setDownloadProgress(progress, 0, null);
      }
    }); //downloadEngine.progress.subscribe

    return eventsSubscription;
  }


  /**
   * Subscribe to a stream of just download status changes for the supplied content id.
   * @return
   */
  public Subscription subscribeDownloadEventsStatusChanges(Observer<DownloadStatus> observer, String contentId) {
    Subscription eventsSubscription = this.getDownloadEngine().getStatus(contentId).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(observer);
    return eventsSubscription;
  }



  /* ------------------------------------ DOWNLOAD EVENT HANDLERS ------------------------------------- */

  /**
   * Translate DownloadEngine-specific status flags into ones our app uses.
   *
   * @param contentId
   * @return
   */
  public Integer getDownloadStatus(String contentId) {
    // GIGO
    if (contentId == null) {
      return DOWNLOAD_ERROR;
    }

    try {
      // TODO:  used to throw ContentNotFoundException.  test that feeding junk contentId doesn't break the new DownloadEngine.
      if (downloadEngine.getStatus(contentId).equals(DownloadStatus.NOT_DOWNLOADED)) {
        return DOWNLOAD_NEEDED;
      }

      if (downloadEngine.getStatus(contentId).equals(DownloadStatus.QUEUED) || downloadEngine.getStatus(contentId).equals(DownloadStatus.DOWNLOADED)) {
        return DOWNLOAD_RUNNING;
      }

      if (downloadEngine.getStatus(contentId).equals(DownloadStatus.PAUSED)) {
        return DOWNLOAD_PAUSED;
      }

      if (downloadEngine.getStatus(contentId).equals(DownloadStatus.DOWNLOADED)) {
        return DOWNLOAD_SUCCESS;
      }
    } catch (Exception e) {
      // was our contentId not recognized?  record the error, and return.
      LogHelper.e(TAG, e, "DownloadEngine cannot give status for contentId=", contentId);
    }

    // unrecognized status, something's wrong
    return DOWNLOAD_ERROR;
  }


  /**
   * Make a download request, and ask the Findaway download engine to fulfill it.
   * Will download the entire book, starting with the chapter specified, skipping any
   * chapters that are already downloaded.  Will wrap to also download the beginning of the book,
   * if needed.  So, if pass in chapter 5, then will download chapters 5 - end, and then
   * download chapters 1-4.
   *
   * NOTE: Audio files are downloaded and stored under the application's standard internal files directory. This directory is deleted when the application is removed.
   *
   * NOTE:  As long as we use the DownloadEngine to download, queue, delete, etc., the Engine will have stored knowledge
   * of the current state in the findaway internal database.  So, if I use the engine to delete, it will change the status to “NOT_DOWNLOADED”
   * which I can check for and re-download later.
   *
   * TODO:  Test how to request downloads for 2different books (may need to make my own queue).
   *
   * @param contentId  the catalog book id
   * @param contentId  id of the license to play book (what was checked out to patron)
   * @param chapter  the chapter to start download at
   * @param part  the more specific chapter part to start download at
   */
  public Integer downloadAudio(String contentId, String licenseId, Integer chapter, Integer part) {
    if (part == null) {
      // most of the time, we don't need to get granular.  start download at part==0, and auto-proceed from there.
      part = CHAPTER_PART_DEFAULT;
    }

    if ((contentId == null) || (licenseId == null) || (chapter == null)) {
      LogHelper.e(TAG, "DownloadService cannot build download request for (contentId, licenseId, chapter)", contentId, licenseId, chapter);
      return DOWNLOAD_ERROR;
    }

    // NOTE:  DownloadType.TO_END gets book from specified chapter to the end of the book.
    // DownloadType.TO_END_WRAP gets from specified chapter to the end of the book, then wraps around and
    // gets all the beginning chapters, too.
    // DownloadType.SINGLE gets just that chapter.
    // The system does skip any chapters that are already downloaded.  So, if we need to re-download a chapter,
    // we'd have to delete it first then call download.

    LogHelper.d(TAG, "before building downloadRequest, part=" + part + ", chapter=" + chapter);
    // NOTE:  If you start with the first chapter in your request the DownloadEngine will skip any chapters that are already downloaded.
    downloadRequest = DownloadRequest.builder().contentId(contentId).part(part).chapter(chapter).licenseId(licenseId).type(DownloadType.TO_END_WRAP).build();

    try {
      this.getDownloadEngine().download(downloadRequest);
      LogHelper.d(TAG, "after downloadEngine.download \n\n\n");
    } catch (Exception e) {
      LogHelper.e(TAG, e, "Error getting download engine: " + e.getMessage());
      return DOWNLOAD_ERROR;
    }

    return DOWNLOAD_RUNNING;
  }


  deleteDownload() {
    // To cancel the download for the supplied content, removing any existing progress.  similarly, cancelAll().
    void delete(DownloadRequest request);
  }

  cancelDownload() {
  // To cancel the download for the supplied content, removing any existing progress.  similarly, cancelAll().
  void cancel(DownloadRequest request);
  }


  pauseDownload() {

    // To pause the download for the supplied content keeping existing progress.  pauseAll() to pause all requests
    void pause(DownloadRequest request);
  }

  getDownloadProgress() {
    // Get the download progress of a book as a percentage from 0 to 100
    Observable<Integer> getProgress(String contentId) throws ContentNotFoundException

  }

  resumeDownload(String contentId) {

    // To get all DownloadRequests:
  List<DownloadRequest> downloadRequests()
  }


  @Override
  public void onCompleted() {
    // ignore
  }


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
   * Catches DownloadEngine events.
   *
   * Download events are described here:  http://developer.audioengine.io/sdk/android/v7/download-engine .
   * @param downloadEvent
   */
  @Override
  public void onNext(DownloadEvent downloadEvent) {
    // TODO: this directory-checking code is for debugging, and should not go live
    if (callbackActivity != null) {
      File filesDir = callbackActivity.getFilesDir();
      if (filesDir.exists()) {
        LogHelper.d(TAG, "filesDir.getAbsolutePath=" + filesDir.getAbsolutePath());
        String[] filesList = filesDir.list();
        LogHelper.d(TAG, "filesDir.filesList=" + filesList.length);
      }

      String sharedPrefsPath = "shared_prefs/";
      File sharedPrefsDir = new File(callbackActivity.getFilesDir(), "../" + sharedPrefsPath);
      if (sharedPrefsDir.exists()) {
        LogHelper.d(TAG, "sharedPrefsDir.getAbsolutePath=" + sharedPrefsDir.getAbsolutePath());
        String[] filesList = sharedPrefsDir.list();
        LogHelper.d(TAG, "sharedPrefsDir.filesList=" + filesList.length);
      }

      if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
        File externalFilesDir = callbackActivity.getExternalFilesDir(null);
        if (externalFilesDir.exists()) {
          LogHelper.d(TAG, "externalFilesDir.getAbsolutePath=" + externalFilesDir.getAbsolutePath());
          String[] externalFilesList = externalFilesDir.list();
          LogHelper.d(TAG, "externalFilesDir.externalFilesList=" + externalFilesList.length);
        }
      }
    }

    LogHelper.d(TAG, "downloadEvent.chapter=" + downloadEvent.chapter());
    LogHelper.d(TAG, "downloadEvent.chapter_download_percentage=" + downloadEvent.chapterPercentage());
    LogHelper.d(TAG, "downloadEvent.content=" + downloadEvent.content());
    LogHelper.d(TAG, "downloadEvent.content_download_percentage=" + downloadEvent.contentPercentage());
    LogHelper.d(TAG, "downloadEvent.toString=" + downloadEvent.toString());


    if (downloadEvent.isError()) {
      // TODO: in future branch, handle errors, don't just output to log and screen and forget
      callbackActivity.notifyDownloadEvent("Download error occurred: " + downloadEvent.message());

      LogHelper.e(TAG, "downloadEvent.getMessage=" + downloadEvent.message());
      //LogHelper.e(TAG, "downloadEvent.getCause=", downloadEvent.getCause());
      LogHelper.e(TAG, "downloadEvent.code=" + downloadEvent.code());


      LogHelper.e(TAG, "downloadEvent.getStackTrace:");
      StackTraceElement[] elements = Thread.currentThread().getStackTrace();
      for (int i = 0; i < elements.length; i++) {
        LogHelper.e("Test", String.format("stack element[%d]: %s", i, elements[i]));
      }

      // NOTE:  The error sending is being re-worked by Findaway, and might change.  Here's the
      // description of how it currently works:
      // "Currently, if the license you supply is not found you'll get a AUDIO_NOT_FOUND.
      // If the license is found but not valid for the requested content you'll get an HTTP_ERROR.
      // If the license does not match the checkout you'll get a FORBIDDEN.
      // If the license is valid but not actually checked out you'll get a HTTP_ERROR
      // Lastly, HTTP_ERROR is currently the catch all. So, 500's on our end will result in that error code as well."
      // All codes listed here:  http://developer.audioengine.io/sdk/android/v7/download-engine .
      if (DownloadEvent.UNKNOWN_DOWNLOAD_ERROR.equals(downloadEvent.code())) {
        // decide if want to re-try downloading or throw a "sorry" message to user.
        LogHelper.e(TAG, "DownloadEvent.UNKNOWN_DOWNLOAD_ERROR");
      } else if (DownloadEvent.NOT_ENOUGH_SPACE_ERROR.equals(downloadEvent.code())) {
        LogHelper.e(TAG, "DownloadEvent.NOT_ENOUGH_SPACE_ERROR");
      } else if (DownloadEvent.ERROR_DOWNLOADING_FILE.equals(downloadEvent.code())) {
        LogHelper.e(TAG, "DownloadEvent.ERROR_DOWNLOADING_FILE");
        // TODO: one possibility is downloadEvent.getMessage() == "write failed: ENOSPC (No space left on device)",
        // which would be hard to catch based on a string message, but common enough to need handling.

      } else if (DownloadEvent.CHAPTER_ALREADY_DOWNLOADED.equals(downloadEvent.code())) {
        LogHelper.e(TAG, "DownloadEvent.NOT_ENOUGH_SPACE_ERROR");
      } else if (DownloadEvent.CHAPTER_ALREADY_DOWNLOADING.equals(downloadEvent.code())) {
        LogHelper.e(TAG, "DownloadEvent.NOT_ENOUGH_SPACE_ERROR");
      }

    } else {
      // download event is not an error, whee
      if (downloadEvent.code().equals(DownloadEvent.DOWNLOAD_STARTED)) {
        // TODO: make sure all string messages are coming in from R.string
        callbackActivity.notifyDownloadEvent(callbackActivity.getString(R.string.downloadStarted));
        callbackActivity.setDownloadProgress(0, 0, DOWNLOAD_RUNNING);

      } else if (downloadEvent.code().equals(DownloadEvent.DOWNLOAD_PAUSED)) {
        callbackActivity.notifyDownloadEvent(callbackActivity.getString(R.string.downloadPaused));
        callbackActivity.setDownloadProgress(0, 0, DOWNLOAD_PAUSED);

      } else if (downloadEvent.code().equals(DownloadEvent.DOWNLOAD_CANCELLED)) {
        callbackActivity.notifyDownloadEvent(callbackActivity.getString(R.string.downloadCancelled));

        callbackActivity.resetDownloadProgress();
        callbackActivity.setDownloadProgress(0, 0, DOWNLOAD_STOPPED);

      } else if (downloadEvent.code().equals(DownloadEvent.CHAPTER_DOWNLOAD_COMPLETED)) {
        callbackActivity.notifyDownloadEvent(callbackActivity.getString(R.string.chapterDownloaded, downloadEvent.chapter().friendlyName()));

      } else if (downloadEvent.code().equals(DownloadEvent.CONTENT_DOWNLOAD_COMPLETED)) {
        callbackActivity.notifyDownloadEvent(callbackActivity.getString(R.string.downloadComplete));
        callbackActivity.setDownloadProgress(0, 0, DOWNLOAD_SUCCESS);

      } else if (downloadEvent.code().equals(DownloadEvent.DELETE_COMPLETE)) {
        callbackActivity.notifyDownloadEvent(callbackActivity.getString(R.string.deleteComplete));
        callbackActivity.resetDownloadProgress();
        callbackActivity.setDownloadProgress(0, 0, DOWNLOAD_STOPPED);

      } else if (downloadEvent.code().equals(DownloadEvent.DELETE_ALL_CONTENT_COMPLETE)) {
        callbackActivity.notifyDownloadEvent(callbackActivity.getString(R.string.deleteAllContentComplete));
        callbackActivity.resetDownloadProgress();
        callbackActivity.setDownloadProgress(0, 0, DOWNLOAD_STOPPED);

      } else if (downloadEvent.code().equals(DownloadEvent.DOWNLOAD_PROGRESS_UPDATE)) {
        callbackActivity.setDownloadProgress(downloadEvent.contentPercentage(), downloadEvent.chapterPercentage(), null);

      } else {
        LogHelper.w(TAG, "Unknown download event: " + downloadEvent.code());
      }

    }
  }// onNext(DownloadEvent)

  /* ------------------------------------ /DOWNLOAD EVENT HANDLERS ------------------------------------- */



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

      /*
      01-05 17:50:16.844 9665-9711/org.nypl.audiobooklibrarydemoapp I/System.out: Sending PlayNextRequest to onNext. Observers? true
      01-05 17:50:16.847 9665-9717/org.nypl.audiobooklibrarydemoapp D/OkHttp: --> POST https://api.findawayworld.com/v4/audiobooks/102244/playlists http/1.1
      01-05 17:50:16.847 9665-9717/org.nypl.audiobooklibrarydemoapp D/OkHttp: Content-Type: application/json; charset=UTF-8
      01-05 17:50:16.847 9665-9717/org.nypl.audiobooklibrarydemoapp D/OkHttp: Content-Length: 41
      01-05 17:50:16.847 9665-9717/org.nypl.audiobooklibrarydemoapp D/OkHttp: --> END POST
      01-05 17:50:16.884 9665-9683/org.nypl.audiobooklibrarydemoapp D/EGL_emulation: eglMakeCurrent: 0xa8885300: ver 2 0 (tinfo 0xa8883320)
      01-05 17:50:16.896 9665-9683/org.nypl.audiobooklibrarydemoapp D/EGL_emulation: eglMakeCurrent: 0xa8885300: ver 2 0 (tinfo 0xa8883320)
      01-05 17:50:16.971 9665-9717/org.nypl.audiobooklibrarydemoapp D/OkHttp: <-- 200 OK https://api.findawayworld.com/v4/audiobooks/102244/playlists (123ms)
       */


  }
