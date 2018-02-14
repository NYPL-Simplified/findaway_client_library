package org.nypl.findawayclientlibrary;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.os.Environment;

import io.audioengine.mobile.Chapter;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import io.audioengine.mobile.AudioEngineException;
import io.audioengine.mobile.DeleteRequest;
import io.audioengine.mobile.DownloadStatus;
import io.audioengine.mobile.DownloadEngine;
import io.audioengine.mobile.DownloadEvent;
import io.audioengine.mobile.DownloadRequest;
import io.audioengine.mobile.DownloadType;

import org.nypl.audiobookincludes.util.LogHelper;
import org.nypl.audiobookincludes.AudioService.DOWNLOAD_STATUS;


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

  /*
  public static enum DOWNLOAD_STATUS {
    DOWNLOAD_ERROR, DOWNLOAD_SUCCESS,
    DOWNLOAD_NEEDED, DOWNLOAD_RUNNING, DOWNLOAD_PAUSED, DOWNLOAD_STOPPED,
    DOWNLOAD_CANCELED, DELETE_REQUESTED
  }
  */
  
  private AudioService audioService;

  // Provides context for methods s.a. getFilesDir(), and allows events caught
  // by this class to be reflected in the app's UI.
  private org.nypl.audiobookincludes.PlayBookActivity callbackActivity = null;

  // receives deletion-related notifications, for when we're operating the DownloadService from the
  // book's detail page in the host app, having only an included fragment to show deletion progress,
  // rather than a whole activity screen
  private org.nypl.audiobookincludes.DeleteBookFragment callbackFragment = null;

  // fulfills books
  DownloadEngine downloadEngine = null;

  // local var to store subscription to "all events" from the DownloadEngine
  Subscription eventsSubscriptionAll = null;

  // local var to store subscription to events that track progress from the DownloadEngine
  Subscription eventsSubscriptionProgress = null;

  // local var to store subscription to events that track status changes from the DownloadEngine
  Subscription eventsSubscriptionStatus = null;



  public DownloadService(String APP_TAG, AudioService audioService, org.nypl.audiobookincludes.PlayBookActivity callbackActivity) {
    TAG = APP_TAG + "DownloadService";
    //this.sessionId = sessionId;
    this.audioService = audioService;
    this.callbackActivity = callbackActivity;
  }


  public DownloadService(String APP_TAG, AudioService audioService, org.nypl.audiobookincludes.DeleteBookFragment callbackFragment) {
    TAG = APP_TAG + "DownloadService";
    //this.sessionId = sessionId;
    this.audioService = audioService;
    this.callbackFragment = callbackFragment;
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
      LogHelper.e(TAG, e, "Error getting download engine: ", e.getMessage());
      e.printStackTrace();
    } catch (Exception e) {
      LogHelper.e(TAG, e, "Error getting download engine: ", e.getMessage());
      e.printStackTrace();
    }
  }


  /**
   * Subscribe to a stream of _all_ download events for the supplied content id.
   * Keep the record of the subscription, in case will be asked to unsubscribe.
   *
   * NOTE: Download events are described here:  http://developer.audioengine.io/sdk/android/v7/download-engine .
   * @return
   */
  public Subscription subscribeDownloadEventsAll(Observer<DownloadEvent> observer, String contentId) {
    // reset
    if (eventsSubscriptionAll != null && !eventsSubscriptionAll.isUnsubscribed()) {
      eventsSubscriptionAll.unsubscribe();
    }

    try {
      eventsSubscriptionAll = this.getDownloadEngine().events(contentId).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(observer);
    } catch (Exception e) {
      // null pointer and other exceptions are not expected, but possible
      // do nothing, we're already unsubscribed.
      LogHelper.e(TAG, e, "Error subscribing to download events: ", e.getMessage());
    }

    return eventsSubscriptionAll;
  }


  /**
   * If there is a subscription to "all-type" download events, then unsubscribe it.
   *
   * @return
   */
  public Subscription unsubscribeDownloadEventsAll() {
    if (eventsSubscriptionAll != null && !eventsSubscriptionAll.isUnsubscribed()) {
      eventsSubscriptionAll.unsubscribe();
    }
    return eventsSubscriptionAll;
  }


  /**
   * Subscribe to a stream of download events that reflect the progress of an ongoing download request.
   * @return
   */
  public Subscription subscribeDownloadEventsProgress(Observer<Integer> observer, String contentId) {
    // reset
    if (eventsSubscriptionProgress != null && !eventsSubscriptionProgress.isUnsubscribed()) {
      eventsSubscriptionProgress.unsubscribe();
    }

    // if we were given an outside observer to let listen to download progress
    if (observer != null) {
      try {
        eventsSubscriptionProgress = this.getDownloadEngine().getProgress(contentId).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(observer);
      } catch (Exception e) {
        // null pointer and other exceptions are not expected, but possible
        // do nothing, we're already unsubscribed.
        LogHelper.e(TAG, e, "Error subscribing to download progress events with given observer: ", e.getMessage());
      }

      return eventsSubscriptionProgress;
    }

    // make our own observer
    try {
      eventsSubscriptionProgress = this.getDownloadEngine().getProgress(contentId).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).take(1).subscribe(new Observer<Integer>() {
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

          callbackActivity.drawDownloadProgressButton(progress, 0, null);
        }
      }); //downloadEngine.progress.subscribe
    } catch (Exception e) {
      // null pointer and other exceptions are not expected, but possible
      // do nothing, we're already unsubscribed.
      LogHelper.e(TAG, e, "Error subscribing to download progress events: ", e.getMessage());
    }

    return eventsSubscriptionProgress;
  }


  /**
   * Subscribe to a stream of just download status changes for the supplied content id.
   * @return
   */
  public Subscription subscribeDownloadEventsStatusChanges(Observer<DownloadStatus> observer, String contentId) {
    // reset
    if (eventsSubscriptionStatus != null && !eventsSubscriptionStatus.isUnsubscribed()) {
      eventsSubscriptionStatus.unsubscribe();
    }

    try {
      eventsSubscriptionStatus = this.getDownloadEngine().getStatus(contentId).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(observer);
    } catch (Exception e) {
      // null pointer and other exceptions are not expected, but possible
      // do nothing, we're already unsubscribed.
      LogHelper.e(TAG, e, "Error subscribing to download status events: ", e.getMessage());
    }

    return eventsSubscriptionStatus;
  }



  /* ------------------------------------ DOWNLOAD EVENT HANDLERS ------------------------------------- */

  /**
   * Translate DownloadEngine-specific status flags into ones our app uses.
   *
   * @param contentId
   * @return
   */
  public DOWNLOAD_STATUS getDownloadStatus(String contentId) {
    // GIGO
    if (contentId == null) {
      return DOWNLOAD_STATUS.DOWNLOAD_ERROR;
    }

    try {
      // TODO:  used to throw ContentNotFoundException.  test that feeding junk contentId doesn't break the new DownloadEngine.
      if (downloadEngine.getStatus(contentId).equals(DownloadStatus.NOT_DOWNLOADED)) {
        return DOWNLOAD_STATUS.DOWNLOAD_NEEDED;
      }

      if (downloadEngine.getStatus(contentId).equals(DownloadStatus.QUEUED) || downloadEngine.getStatus(contentId).equals(DownloadStatus.DOWNLOADED)) {
        return DOWNLOAD_STATUS.DOWNLOAD_RUNNING;
      }

      if (downloadEngine.getStatus(contentId).equals(DownloadStatus.PAUSED)) {
        return DOWNLOAD_STATUS.DOWNLOAD_PAUSED;
      }

      if (downloadEngine.getStatus(contentId).equals(DownloadStatus.DOWNLOADED)) {
        return DOWNLOAD_STATUS.DOWNLOAD_SUCCESS;
      }
    } catch (Exception e) {
      // was our contentId not recognized?  record the error, and return.
      LogHelper.e(TAG, e, "DownloadEngine cannot give status for contentId=", contentId);
    }

    // unrecognized status, something's wrong
    return DOWNLOAD_STATUS.DOWNLOAD_ERROR;
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
   * NOTE:  Call getDownloadEngine().getProgress(contentId) to get the download progress
   * of a book as a percentage from 0 to 100.  Or use the download event system.
   *
   * TODO:  Test how to request downloads for 2different books (may need to make my own queue).
   *
   * @param contentId  the catalog book id
   * @param contentId  id of the license to play book (what was checked out to patron)
   * @param chapter  the chapter to start download at
   * @param part  the more specific chapter part to start download at
   */
  public DOWNLOAD_STATUS downloadAudio(String contentId, String licenseId, Integer chapter, Integer part) {
    if (part == null) {
      // most of the time, we don't need to get granular.  start download at part==0, and auto-proceed from there.
      part = CHAPTER_PART_DEFAULT;
    }

    if ((contentId == null) || (licenseId == null) || (chapter == null)) {
      LogHelper.e(TAG, "DownloadService cannot build download request for (contentId, licenseId, chapter)", contentId, licenseId, chapter);
      return DOWNLOAD_STATUS.DOWNLOAD_ERROR;
    }

    // NOTE:  DownloadType.TO_END gets book from specified chapter to the end of the book.
    // DownloadType.TO_END_WRAP gets from specified chapter to the end of the book, then wraps around and
    // gets all the beginning chapters, too.
    // DownloadType.SINGLE gets just that chapter.
    // The system does skip any chapters that are already downloaded.  So, if we need to re-download a chapter,
    // we'd have to delete it first then call download.

    LogHelper.d(TAG, "before building downloadRequest, part=" + part + ", chapter=" + chapter);
    // NOTE:  If you start with the first chapter in your request the DownloadEngine will skip any chapters that are already downloaded.
    DownloadRequest downloadRequest = DownloadRequest.builder().contentId(contentId).part(part).chapter(chapter).licenseId(licenseId).type(DownloadType.TO_END_WRAP).build();

    try {
      this.getDownloadEngine().download(downloadRequest);
      LogHelper.d(TAG, "after downloadEngine.download \n\n\n");
    } catch (Exception e) {
      LogHelper.e(TAG, e, "Error getting download engine: " + e.getMessage());
      return DOWNLOAD_STATUS.DOWNLOAD_ERROR;
    }

    return DOWNLOAD_STATUS.DOWNLOAD_RUNNING;
  }


  /**
   * Delete all content previously downloaded for this book.
   * TODO: untested
   *
   * @param contentId  book to delete
   * @return
   */
  public DOWNLOAD_STATUS deleteDownload(String contentId) {
    DeleteRequest deleteRequest = DeleteRequest.builder().contentId(contentId).build();

    try {
      this.getDownloadEngine().delete(deleteRequest);
    } catch (Exception e) {
      LogHelper.e(TAG, e, "Error deleting a download: " + e.getMessage());
      return DOWNLOAD_STATUS.DOWNLOAD_ERROR;
    }

    return DOWNLOAD_STATUS.DELETE_REQUESTED;
  }


  /**
   * Go through all download requests currently on the engine, and
   * return those whose contentId matches the one passed in.
   *
   * @param contentId
   * @return
   */
  public List<DownloadRequest> findDownloadRequests(String contentId) {
    List<DownloadRequest> foundRequests = new ArrayList<DownloadRequest>();
    if (contentId == null) {
      return foundRequests;
    }

    // To get all DownloadRequests:
    List<DownloadRequest> requests = this.getDownloadEngine().downloadRequests();
    for (DownloadRequest request : requests) {
      if (((DownloadRequest) request).contentId().equals(contentId)) {
        foundRequests.add(request);
      }
    }

    return foundRequests;
  }


  /**
   * Cancel the download for the supplied content, removing any existing progress.
   * TODO: untested
   * @param contentId  book to cancel
   * @return
   */
  public DOWNLOAD_STATUS cancelDownload(String contentId) {
    List<DownloadRequest> foundRequests = this.findDownloadRequests(contentId);
    if ((foundRequests == null) || (foundRequests.size() == 0)) {
      // nothing to do here
      return DOWNLOAD_STATUS.DOWNLOAD_CANCELED;
    }

    for (DownloadRequest request : foundRequests) {
      try {
        this.getDownloadEngine().cancel(request);
      } catch (Exception e) {
        LogHelper.e(TAG, e, "Error canceling a download request: " + e.getMessage());
        return DOWNLOAD_STATUS.DOWNLOAD_ERROR;
      }
    }

    return DOWNLOAD_STATUS.DOWNLOAD_CANCELED;
  }


  /**
   * Pause a download request, keeping existing progress (perhaps until we get on WiFi).
   *
   * TODO: untested
   * @param contentId  book to pause download of
   * @return
   */
  public DOWNLOAD_STATUS pauseDownload(String contentId) {
    List<DownloadRequest> foundRequests = this.findDownloadRequests(contentId);
    if ((foundRequests == null) || (foundRequests.size() == 0)) {
      // nothing to do here
      return DOWNLOAD_STATUS.DOWNLOAD_PAUSED;
    }

    for (DownloadRequest request : foundRequests) {
      try {
        this.getDownloadEngine().pause(request);
      } catch (Exception e) {
        LogHelper.e(TAG, e, "Error pausing a download request: ", e.getMessage());
        return DOWNLOAD_STATUS.DOWNLOAD_ERROR;
      }
    }

    return DOWNLOAD_STATUS.DOWNLOAD_PAUSED;
  }


  /**
   * Resume a paused download request, keeping existing progress.
   *
   * TODO: untested
   * @param contentId  book to download
   * @return
   */
  public DOWNLOAD_STATUS resumeDownload(String contentId) {
    List<DownloadRequest> foundRequests = this.findDownloadRequests(contentId);
    if ((foundRequests == null) || (foundRequests.size() == 0)) {
      // nothing to do here
      return DOWNLOAD_STATUS.DOWNLOAD_ERROR;
    }

    for (DownloadRequest request : foundRequests) {
      try {
        this.getDownloadEngine().download(request);
      } catch (Exception e) {
        LogHelper.e(TAG, e, "Error resuming a download request: ", e.getMessage());
        return DOWNLOAD_STATUS.DOWNLOAD_ERROR;
      }
    }

    return DOWNLOAD_STATUS.DOWNLOAD_RUNNING;
  }


  /**
   * To satisfy rx.Observer implementation.
   */
  @Override
  public void onCompleted() {
    // ignore
  }


  /**
   * Handle download events that are errors.
   */
  @Override
  public void onError(Throwable e) {
    LogHelper.e(TAG, "There was an error in the download or playback process: " + e.getMessage());
    e.printStackTrace();
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
    /*
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
    */

    LogHelper.d(TAG, "downloadEvent.chapter=" + downloadEvent.chapter());
    LogHelper.d(TAG, "downloadEvent.chapter_download_percentage=" + downloadEvent.chapterPercentage());
    LogHelper.d(TAG, "downloadEvent.content=" + downloadEvent.content());
    LogHelper.d(TAG, "downloadEvent.content_download_percentage=" + downloadEvent.contentPercentage());
    LogHelper.d(TAG, "downloadEvent.toString=" + downloadEvent.toString());


    Chapter chapter = downloadEvent.chapter();

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
        LogHelper.d(TAG, "DownloadEvent.CHAPTER_ALREADY_DOWNLOADED");
        // TODO: ungray the chapter in the TOC

      } else if (DownloadEvent.CHAPTER_ALREADY_DOWNLOADING.equals(downloadEvent.code())) {
        LogHelper.d(TAG, "DownloadEvent.CHAPTER_ALREADY_DOWNLOADING");
      }

    } else {
      // download event is not an error, whee
      if (downloadEvent.code().equals(DownloadEvent.DOWNLOAD_STARTED)) {
        // DOWNLOAD_STARTED gets called for every chapter that is to be downloaded, not just for the whole book.

        // if download is resuming from not the first chapter, then it's ok not to show the toast
        // if there is an introduction, it will be labeled as chapter 0, and it's ok not to show the toast until
        // the first chapter is being downloaded.
        if (chapter != null && new Integer(1).equals(chapter.chapter())) {
          callbackActivity.notifyDownloadEvent(callbackActivity.getString(R.string.downloadStarted));
        }
        //callbackActivity.drawDownloadProgressButton(downloadEvent.contentPercentage(), downloadEvent.chapterPercentage(), DOWNLOAD_RUNNING);

        // tell the activity to start drawing the download progress bar
        //callbackActivity.downloadStarting();

      } else if (downloadEvent.code().equals(DownloadEvent.DOWNLOAD_PAUSED)) {
        callbackActivity.notifyDownloadEvent(callbackActivity.getString(R.string.downloadPaused));
        //callbackActivity.drawDownloadProgressButton(downloadEvent.contentPercentage(), downloadEvent.chapterPercentage(), DOWNLOAD_PAUSED);

      } else if (downloadEvent.code().equals(DownloadEvent.DOWNLOAD_CANCELLED)) {
        //callbackActivity.notifyDownloadEvent(callbackActivity.getString(R.string.downloadCancelled));

        // tell the activity to stop drawing the download progress bar
        callbackActivity.downloadStopping();

        callbackActivity.resetDownloadProgress();
        callbackActivity.drawDownloadProgressButton(0, 0, DOWNLOAD_STATUS.DOWNLOAD_STOPPED);

      } else if (downloadEvent.code().equals(DownloadEvent.CHAPTER_DOWNLOAD_COMPLETED)) {
        // TODO: ungray the chapter in the table of contents
        //callbackActivity.notifyDownloadEvent(callbackActivity.getString(R.string.chapterDownloaded, downloadEvent.chapter().friendlyName()));
        if (chapter != null) {
          // TODO: Danger, we're assuming that there is no introduction (chapter 0), and that the book is chapterized, and that
          // there's only one part, instead of uniquely identifying a chapter.  This is very raw proof of concept for now,
          // just to practice drawing the TOC.
          callbackActivity.drawDownloadProgressTableOfContents(chapter.chapter());
        }

      } else if (downloadEvent.code().equals(DownloadEvent.CONTENT_DOWNLOAD_COMPLETED)) {
        callbackActivity.notifyDownloadEvent(callbackActivity.getString(R.string.downloadComplete));

        // tell the activity to stop drawing the download progress bar
        callbackActivity.downloadStopping();

        callbackActivity.drawDownloadProgressButton(100, 100, DOWNLOAD_STATUS.DOWNLOAD_SUCCESS);

      } else if (downloadEvent.code().equals(DownloadEvent.DELETE_COMPLETE)) {
        //callbackActivity.notifyDownloadEvent(callbackActivity.getString(R.string.deleteComplete));
        callbackActivity.resetDownloadProgress();

        callbackActivity.drawDownloadProgressButton(0, 0, DOWNLOAD_STATUS.DOWNLOAD_STOPPED);

      } else if (downloadEvent.code().equals(DownloadEvent.DELETE_ALL_CONTENT_COMPLETE)) {
        //callbackActivity.notifyDownloadEvent(callbackActivity.getString(R.string.deleteAllContentComplete));
        callbackActivity.resetDownloadProgress();

        callbackActivity.drawDownloadProgressButton(0, 0, DOWNLOAD_STATUS.DOWNLOAD_STOPPED);

      } else if (downloadEvent.code().equals(DownloadEvent.DOWNLOAD_PROGRESS_UPDATE)) {
        // callbackActivity.drawDownloadProgressButton(downloadEvent.contentPercentage(), downloadEvent.chapterPercentage(), null);
        //LogHelper.e(TAG, "downloadEvent.contentPercentage()=", downloadEvent.contentPercentage());
        callbackActivity.setDownloadProgress(downloadEvent.contentPercentage(), downloadEvent.chapterPercentage(), null);

      } else {
        LogHelper.w(TAG, "Unknown download event: " + downloadEvent.code());
      }

    }
  }// onNext(DownloadEvent)

  /* ------------------------------------ /DOWNLOAD EVENT HANDLERS ------------------------------------- */


}
