package org.nypl.findawayclientlibrary;


import android.content.Intent;
import android.os.Bundle;

import android.support.annotation.NonNull;
import android.support.design.internal.NavigationMenuView;
import android.support.design.widget.NavigationView;

import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Toast;

import org.nypl.findawayclientlibrary.util.LogHelper;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import android.os.Handler;



/**
 * Loads and exchanges fragments that are responsible for audio player UI
 * and audiobook-associated sub-screens.  Is responsible for responding to events
 * and stimuli, while delegating the UI drawing to the child fragments.
 *
 * Starts and communicates with the download portion of the sdk (through the DownloadService).
 * Starts and communicates with the audio playing portion of the sdk (through the PlaybackService).
 * Is called upon by the DownloadService and PlaybackService to update the UI in response to events.
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
 * Created by daryachernikhova on 9/14/17.
 *
 * TODO:  make sure that, if a book is already downloaded, another download doesn't start the next time
 * the activity resumes.
 *
 * TODO:
 * The session key should very rarely expire (fall out of Findaway's cache.  If it does, there will be a Download Error, but it does not
 * currently have an easy-to-check code.  The session expiring could probably be found in the error message.  Since it's a rare event, it'd
 * be OK to resolve all major errors by obtaining a new session key and re-trying the download.
 */
public class PlayBookActivity extends BaseActivity implements NavigationView.OnNavigationItemSelectedListener,
          View.OnClickListener, View.OnLongClickListener, SeekBar.OnSeekBarChangeListener {
  // so can do a search in log msgs for just this class's output
  private static final String TAG = APP_TAG + "PlayBookActivity";

  PlayBookFragment playBookFragment = null;

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
  String license = "580e7da175435e471d2e042a";


  // TODO: instead of radial gradient, do a gentle square bottom -> top gradient, and make the colors gray-blue.

  // the chapter we're downloading right now
  int currentlyDownloadingChapter = 1;

  // the chapter we're playing right now
  int currentlyPlayingChapter = 1;

  DrawerLayout drawerLayout;
  ActionBarDrawerToggle actionBarDrawerToggle;
  NavigationView tocNavigationView;

  // holds the one engine to control them all
  private AudioService audioService = new AudioService(APP_TAG, sessionIdReal7);
  // plays drm-ed audio
  private PlaybackService playbackService = new PlaybackService(APP_TAG, audioService, this);
  // fulfills books
  private DownloadService downloadService = new DownloadService(APP_TAG, audioService, this);

  // will be updated by the download event handler, so can update progress on the UI from a timed thread
  // percent of entire book that's been downloaded, last we were informed
  Integer lastDownloadProgressTotal = 0;
  // percent of currently downloading chapter that's been downloaded, last we were informed
  Integer lastDowloadProgressChapter = 0;
  // is the current download running?
  DownloadService.DOWNLOAD_STATUS lastDownloadProgressStatus = DownloadService.DOWNLOAD_STATUS.DOWNLOAD_STOPPED;

  private final Runnable taskUpdateDownloadProgress = new Runnable() {
    @Override
    public void run() {
      LogHelper.e(TAG, "taskUpdateDownloadProgress.run()");
      drawDownloadProgressButton();
    }
  };

  // taskUpdateDownloadProgress | scheduleDownloadProgressUpdate |
  // stopDownloadProgressUpdate
  // todo: turn off progress bar completely, and see if the ui locks up during download,
  // just because the DownloadService is running on the UI thread right now.

  private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
  private ScheduledFuture<?> scheduledFuture;
  private final Handler threadHandler = new Handler();
  // in milliseconds
  private static final long PROGRESS_UPDATE_TIME_INTERVAL = 5000;
  // in milliseconds
  private static final long PROGRESS_UPDATE_INITIAL_DELAY = 1000;



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

    // set the top menu toolbar
    Toolbar toolbar = (Toolbar) findViewById(R.id.top_nav_toolbar);
    // changes the left icon, which by default is the material design back arrow
    //toolbar.setNavigationIcon(R.drawable.menu_icon_books);
    toolbar.setOverflowIcon(getResources().getDrawable(R.drawable.toc_dark));

    setSupportActionBar(toolbar);

    // NOTE: setDisplayHomeAsUpEnabled makes the icon and title in the action bar clickable and adds a "<-", so that "up" (ancestral) navigation can be provided.
    // setHomeButtonEnabled is like setDisplayHomeAsUpEnabled, except "<-" doesn’t show up unless the android:parentActivityName is specified.
    // setDisplayShowHomeEnabled controls whether to show the Activity icon/logo or not.

    // NOTE: the back/up button knows where to go by the parentActivity declared in the manifest.
    // getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    // remove the title displayed in the top bar, we'll need the space for menu buttons
    getSupportActionBar().setTitle(null);

    // drawer_layout is defined in playback_activity_main.xml, and contains both the toolbar and the TOC ListView
    drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

    actionBarDrawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
    // do not display the burger "open drawer" icon or the <- "close drawer" icon
    actionBarDrawerToggle.setDrawerIndicatorEnabled(false);

    drawerLayout.addDrawerListener(actionBarDrawerToggle);
    actionBarDrawerToggle.syncState();
    tocNavigationView = (NavigationView) findViewById(R.id.toc_drawer_view);
    tocNavigationView.setNavigationItemSelectedListener(this);

    // make sure we're allowed to proceed to starting the media session
    boolean allowedToProceed = checkAppPermissions();
    if (!allowedToProceed) {
      // TODO: future branch: go to My Books, and throw a notification explaining why.
      LogHelper.e(TAG, "PlayBookActivity.onCreate: Did not get needed permissions from user.");
    }

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

      //if (bookId == null) {
        // TODO: when code for error message toasts, display an error message here and don't load player UI.
      //}

      //LogHelper.d(TAG, "bookId=" + bookId);
    }


    // one-time call to start up the AudioEngine service
    audioService.initAudioEngine(this);

    // ask the AudioEngine to start a DownloadEngine
    downloadService.initDownloadEngine();
    /*
          Intent i = new Intent(context, AudioService.class);
          i.setAction(AudioService.ACTION_CMD);
          i.putExtra(AudioService.CMD_NAME, AudioService.CMD_PAUSE);
          startService(i);
--
    startService(new Intent(getApplicationContext(), AudioService.class));
---
  // we will play audio in the AudioService
  private AudioService audioService = null;
  private Intent playIntent = null;
  private boolean isMusicServiceBound = false;

then

    if (playIntent == null) {
      playIntent = new Intent(this, AudioService.class);

      // BIND_AUTO_CREATE recreates the Service if it is destroyed when there’s a bounding client
      boolean bound = bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
      Log.d(TAG, SUB_TAG + "bound=" + bound);

      // TODO: ?? Every call to this method will result in a corresponding call to the target service's
      // android.app.Service.onStartCommand method, with the intent given here.
      // This provides a convenient way to submit jobs to a service without having to bind and call on to its interface.
      // TODO: redundant after binding the service?  remove and hope.
      //startService(playIntent);

      // NOTE:  don't have to stop the IntentService as it calls stopSelf() internally when it needs.
    }

    then also:
      @Override
  protected void onDestroy() {
    stopService(playIntent);
    audioService = null;
    super.onDestroy();
  }


and then this:
  / * *
   * We are going to play the music in the Service class, but control it from the Activity class, where the application's user interface operates.
   * To accomplish this, we will have to bind to the Service class, which we do here.
   * /
    private ServiceConnection musicConnection = new ServiceConnection() {

      @Override
      public void onServiceConnected(ComponentName name, IBinder service) {
        Log.d(TAG, SUB_TAG + "musicConnection.onServiceConnected");

        AudioService.MusicBinder binder = (AudioService.MusicBinder)service;
        // get service
        audioService = binder.getService();

        // pass chapter list
        // NOTE: If the service is Local (not IntentService), setChapters execution happens on the thread of the calling client/activity (this UI thread).
        // If wish to call a long-running operation, then spin a new background thread in the called service method.
        audioService.setChapters(chapters);
        isMusicServiceBound = true;
      }

      @Override
      public void onServiceDisconnected(ComponentName name) {
        audioService = null;
        isMusicServiceBound = false;
      }
    };

--

Regardless of whether your application is started, bound, or both, any application component can use the service (even from a separate application) in the same way that any component can use an activity—by starting it with an Intent. However, you can declare the service as private in the manifest file and block access from other applications. This is discussed more in the section about Declaring the service in the manifest.

Caution: A service runs in the main thread of its hosting process; the service does not create its own thread and does not run in a separate process unless you specify otherwise. If your service is going to perform any CPU-intensive work or blocking operations, such as MP3 playback or networking, you should create a new thread within the service to complete that work. By using a separate thread, you can reduce the risk of Application Not Responding (ANR) errors, and the application's main thread can remain dedicated to user interaction with your activities.




     */

    // ask the AudioEngine to start a PlaybackEngine
    playbackService.initPlaybackEngine();
  }// onCreate



  /**
   * Clean up for garbage collection.
   */
  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (scheduledExecutorService != null) {
      scheduledExecutorService.shutdown();
    }
  }


  /**
   * If this activity is not currently hopping, it shouldn't be listening to events.
   * TODO: or should it?  test
   */
  @Override
  protected void onPause() {
    super.onPause();

    downloadService.unsubscribeDownloadEventsAll();
    playbackService.unsubscribePlayEventsAll();
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
    downloadService.subscribeDownloadEventsAll(downloadService, contentId);

    // TODO: deleting for debugging the progress bar, remove when done design
    downloadService.deleteDownload(contentId);

    // We know what book this activity is to be playing.  Does this book need any downloading?
    // Check to see all the book files have successfully downloaded.
    DownloadService.DOWNLOAD_STATUS downloadStatus = downloadService.getDownloadStatus(contentId);
    // If not, ask the DownloadService to either start or resume the download.
    if (downloadStatus.equals(DownloadService.DOWNLOAD_STATUS.DOWNLOAD_ERROR) || downloadStatus.equals(DownloadService.DOWNLOAD_STATUS.DOWNLOAD_NEEDED)) {
      // ask to start the download
      downloadService.downloadAudio(contentId, license, currentlyDownloadingChapter, null);

      // prepare to periodically update UI as download proceeds
      this.downloadStarting();
    }

    if (downloadStatus.equals(DownloadService.DOWNLOAD_STATUS.DOWNLOAD_STOPPED) || downloadStatus.equals(DownloadService.DOWNLOAD_STATUS.DOWNLOAD_PAUSED)) {
      // TODO:  do not see a "resume" method in the sdk.  for now, will form a new download request.
      // Test that, if I send in multiple requests with same info, it's equivalent to asking to resume the download
      // Test that resuming after a stop works, and what happens if app shuts down and is restored.
      downloadService.resumeDownload(contentId);

      // prepare to periodically update UI as download proceeds
      this.downloadStarting();
    }

    playbackService.subscribePlayEventsAll(playbackService);
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
   * TODO:  what sdk methods clean up the scene, but let the player to keep playing?
   *
   */
  @Override
  protected void onStop() {
    super.onStop();
  }


  /* ---------------------------------- /LIFECYCLE METHODS ----------------------------------- */


  /* ------------------------------------ NAVIGATION EVENT HANDLERS ------------------------------------- */

  /**
   * Highlight the chapter being played in the TOC, and scroll the TOC to show that chapter
   * towards the center of the screen.
   */
  public void drawChosenTableOfContentsMenuItem() {
    // see https://stackoverflow.com/questions/31233279/navigation-drawer-how-do-i-set-the-selected-item-at-startup?rq=1

  }


  /**
   * For now:  Hardcoded method for demo purposes that fills the right nav toc menu with
   * chapter information.
   * Later:  Will accept a list of chapters and could be called when the book's metadata changes.
   */
  public void loadTableOfContentsMenu() {

    final Menu menu = tocNavigationView.getMenu();

    int groupId = R.id.group_chapter_menu;
    for (int menuItemId = 1; menuItemId <= 13; menuItemId++) {
      int order = menuItemId;
      MenuItem menuItem = menu.add(groupId, menuItemId, order, "Chapter " + menuItemId);
      menuItem.setIcon(R.drawable.ic_radio_button_unchecked_black_24dp);
      menuItem.setCheckable(true);
      menuItem.setEnabled(false);
    }
  }


  /**
   * Find out if the table of contents is open.  If it is, then close it.
   * Else, the user must really mean to go back to a previous fragment,
   * or maybe to leave the activity altogether for a previous screen.
   * Do that for them.
   */
  @Override
  public void onBackPressed() {
    if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
      drawerLayout.closeDrawer(GravityCompat.END);
    } else {
      super.onBackPressed();
    }

  }


  /**
   * Fill up and inflate the top navigation bar.
   * If we have a book to load, then also load its table of contents menu.
   *
   * NOTE:  It is not permitted to add menu items in the Activity an then add more items in the child Fragment.
   * If creating menu from inside multiple fragments' onCreateOptionsMenu(), then make sure to call menu.clear()
   * at the beginning of each fragment's onCreateOptionsMenu().
   * If changing menu items, based on context, use onPrepareOptionsMenu(), which is called every time user clicks on the menu bar.
   *
   * @param menu
   * @return
   */
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.action_toolbar, menu);

    // dynamically populate the TOC with either the hard-coded test data or the book metadata.
    // can also be called from other parts of the code, s.a. when the user selects a new book.
    loadTableOfContentsMenu();

    return super.onCreateOptionsMenu(menu);
  }


  /**
   * A chapter got clicked in the table of contents menu.  Play that chapter,
   * and visibly mark it as selected in the menu.
   *
   * @param item
   * @return
   */
  @Override
  public boolean onNavigationItemSelected(@NonNull MenuItem item) {
    // TODO: gray highlight isn't appearing, previously selected chapter's icon isn't resetting

    int checkedItemId = item.getItemId();

    final Menu navMenu = tocNavigationView.getMenu();
    int menuSize = navMenu.size();
    for (int i=0; i<menuSize; i++) {
      MenuItem menuItem = navMenu.getItem(i);
      // unselect the menu item if it's not our selected one
      // NOTE: if doesn't work, try invisible menu item, like in https://stackoverflow.com/questions/36051045/how-to-uncheck-checked-items-in-navigation-view/39017882
      if (menuItem.getItemId() != checkedItemId) {
        menuItem.setChecked(false);

        if (menuItem.getGroupId() == R.id.group_chapter_menu) {
          // if menu item has a chapter icon, draw the "unchecked" version
          menuItem.setIcon(R.drawable.ic_radio_button_unchecked_black_24dp);
          LogHelper.d(TAG, "I definitely set it");
        }
      }
    }

    // set the selected chapter's icon to "checked"
    item.setChecked(true);
    tocNavigationView.setCheckedItem(checkedItemId);
    item.setIcon(R.drawable.ic_radio_button_checked_black_24dp);

    // TODO: start playing the selected chapter
    //audioService.setChapterPosition(0);
    //audioService.playTrack();

    drawerLayout.closeDrawer(GravityCompat.END);
    return true;
  }


  /**
   * Called by OS when the user selects one of the top nav app bar items.
   *
   * @param item  Indicates which item was clicked.  item.getItemId() corresponds to <item> element's android:id attribute.
   * @return
   */
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();

    // User chose the "Settings" item, show the app settings UI...
    if (id == R.id.action_sleep_timer) {
      return true;
    }

    // If we were running everything on one codebase, then the standard way to handle the "up" action
    // would be to not call for it here, but to let the super.onOptionsItemSelected() handle it.  Because we're running
    // from a library project, we would not be able to specify a calling project's activity as PlayBookActivity's
    // parent activity in the manifest.  So we must handle the call to return to parent in the java code ourselves.
    if (id == android.R.id.home) {
      LogHelper.d(TAG, "action bar clicked");
      // TODO: finish(); return true; would stop the activity and go up a level to the calling activity.
    }


    if (id == R.id.action_table_of_contents) {
      if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
        drawerLayout.closeDrawer(GravityCompat.END);
      } else {
        drawerLayout.openDrawer(GravityCompat.END);
      }
      return true;
    }

    // If we got here, the user's action was not recognized.  Invoke the superclass to handle it.
    return super.onOptionsItemSelected(item);
  }


  /* ------------------------------------ /NAVIGATION EVENT HANDLERS ------------------------------------- */


  /* ------------------------------------ PLAYBACK EVENT HANDLERS ------------------------------------- */

  /**
   * Display or log notifications related to playback events.
   * Limit to only displaying when situation warrants, s.a. during development.
   * @param message
   */
  public void notifyPlayEvent(String message) {
    // have more toast notifications when developing than on prod
    if (LogHelper.getVerbosity() == LogHelper.DEV) {
      Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
  }


  @Override
  public void onClick(View view) {

    if (view.getId() == R.id.play_pause_button && view.getTag().equals(getResources().getString(R.string.play))) {
      LogHelper.d(TAG, "playback PLAY button clicked");

      try {
        // NOTE: Can specify the chapter to start playing at here.
        // ask to start playing audio
        // TODO: wait for the first chapter to be there, first
        playbackService.playAudio(contentId, license, currentlyPlayingChapter, null);
      } catch (Exception e) {
        e.printStackTrace();
        LogHelper.e(TAG, e, "play call generated a problem");
      }
      if (playBookFragment != null) {
        playBookFragment.redrawPlayButton(getResources().getString(R.string.pause), R.drawable.ic_pause_circle_outline_black_24dp);
      }

    } else {
        if (view.getId() == R.id.play_pause_button && view.getTag().equals(getResources().getString(R.string.pause))) {
          LogHelper.d(TAG, "playback PAUSE button clicked");
          playbackService.getPlaybackEngine().pause();

        if (playBookFragment != null) {
          playBookFragment.redrawPlayButton(getResources().getString(R.string.resume), R.drawable.ic_play_circle_outline_black_24dp);
        }

      } else {
        if (view.getId() == R.id.play_pause_button && view.getTag().equals(getResources().getString(R.string.resume))) {
          LogHelper.d(TAG, "playback RESUME button clicked");
          playbackService.getPlaybackEngine().resume();

          if (playBookFragment != null) {
            playBookFragment.redrawPlayButton(getResources().getString(R.string.pause), R.drawable.ic_pause_circle_outline_black_24dp);
          }
        } else {
          if (view.getId() == R.id.rewind_button) {
            // TODO: what code would make sure not crossing boundary condition on file length?
            LogHelper.d(TAG, "playback BACK10 button clicked");
            playbackService.getPlaybackEngine().seekTo(playbackService.getPlaybackEngine().getPosition() - 10000);
          } else {
            if (view.getId() == R.id.forward_button) {
              LogHelper.d(TAG, "playback FWD10 button clicked");
              playbackService.getPlaybackEngine().seekTo(playbackService.getPlaybackEngine().getPosition() + 10000);
            } else {
              //if (view.getId() == R.id.previous_track_button) {
              //  playbackService.getPlaybackEngine().previousChapter();
              //} else {
                //if (view.getId() == R.id.next_track_button) {
                  //playbackService.getPlaybackEngine().nextChapter();
                //} else {

                  /*
                 // TODO: call from drop-down menu
                  if (view.getId() == R.id.playback_speed_button) {
                    // change playback speed
                    if (((ToggleButton) view).isSelected() == true) {
                      playbackService.getPlaybackEngine().setSpeed(1.0f);
                      // setChecked sets the intrinsic boolean dataMember associated with your view object
                      ((ToggleButton) view).setChecked(false);
                      // setSelected sets the UI associated with your view object
                      ((ToggleButton) view).setSelected(false);
                      Log.d(TAG, "toggle button clicked: " + ((ToggleButton) view).isChecked() + ", speed=" + playbackService.getPlaybackEngine().getSpeed());
                    } else {
                      playbackService.getPlaybackEngine().setSpeed(2.0f);
                      ((ToggleButton) view).setChecked(true);
                      ((ToggleButton) view).setSelected(true);
                    }
                  } else {
                    */
                  LogHelper.e(TAG, "Cannot recognize clicked button.");
                  //}
                //}
              //}
            }
          }
        }
      }
    }

  }// onClick


  @Override
  public boolean onLongClick(View view) {
    return false;
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

      playbackService.setSeekTo(progress);
    }
  }


  @Override
  public void onStartTrackingTouch(SeekBar seekBar) {
    //if (seekBar.getId() == this.seekBar.getId()) {

    // TODO:  hmm, why was the demo code unsubscribing here, and then subscribing back in on StopTrackingTouch?
    playbackService.unsubscribePlayEventsAll();

    //}
  }


  @Override
  public void onStopTrackingTouch(SeekBar seekBar) {
    //if (seekBar.getId() == this.seekBar.getId()) {

    playbackService.getPlaybackEngine().seekTo(playbackService.getSeekTo());

    playbackService.subscribePlayEventsAll(playbackService);

    //}
  }


  /**
   * Ask the fragment to update the seek bar.
   */
  public void setPlayProgress(Long duration, Long position) {
    if (playBookFragment != null) {
      playBookFragment.redrawPlaybackPosition(duration, position);
    }
  }

  /* ------------------------------------ /PLAYBACK EVENT HANDLERS ------------------------------------- */


  /* ------------------------------------ DOWNLOAD METHODS ------------------------------------- */

  /**
   * Lets this activity know that it should start drawing/updating the download progress view.
   */
  public void downloadStarting() {
    LogHelper.e(TAG, "downloadStarting");
    scheduleDownloadProgressUpdate();
  }


  /**
   * Lets this activity know that it should stop drawing/updating the download progress view.
   */
  public void downloadStopping() {
    LogHelper.e(TAG, "downloadStopping");
    stopDownloadProgressUpdate();
  }


  /**
   * Start the regularly scheduled UI updates of the download progress bar.
   */
  private void scheduleDownloadProgressUpdate() {
    stopDownloadProgressUpdate();
    LogHelper.e(TAG, "scheduleDownloadProgressUpdate");
    if (!scheduledExecutorService.isShutdown()) {
      scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(
        new Runnable() {
          @Override
          public void run() {
            threadHandler.post(taskUpdateDownloadProgress);
          }
        }, PROGRESS_UPDATE_INITIAL_DELAY,
        PROGRESS_UPDATE_TIME_INTERVAL, TimeUnit.MILLISECONDS);
    }
  }


  /**
   * Stop the regularly scheduled UI updates of the download progress bar.
   */
  private void stopDownloadProgressUpdate() {
    LogHelper.e(TAG, "stopDownloadProgressUpdate");
    if (scheduledFuture != null) {
      scheduledFuture.cancel(false);
    }
  }


  /**
   * Display or log notifications related to download events.
   * Limit to only displaying when situation warrants, s.a. during development.
   * @param message
   */
  public void notifyDownloadEvent(String message) {
    // have more toast notifications when developing than on prod
    if (LogHelper.getVerbosity() == LogHelper.DEV) {
      Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
  }


  /**
   * Call an update on the download progress UI elements with whatever we have stored
   * as our last known progress counts.
   */
  public void drawDownloadProgressButton() {
    LogHelper.e(TAG, "drawDownloadProgressButton");
    drawDownloadProgressButton(lastDownloadProgressTotal, lastDowloadProgressChapter, lastDownloadProgressStatus);
  }


  /**
   * Update the download progress bar, and any other download-associated UI elements
   * to reflect current download status (passed in).
   *
   * @param primaryProgress  ex: percent of total content downloaded
   * @param secondaryProgress  ex: percent of chapter downloaded
   * @param status  ex: "running"/"paused"/etc.
   */
  public void drawDownloadProgressButton(Integer primaryProgress, Integer secondaryProgress, DownloadService.DOWNLOAD_STATUS status) {
    if (playBookFragment != null) {
      playBookFragment.redrawDownloadProgress(primaryProgress, secondaryProgress);

      // if null, use a default value
      if (status == null) {  status = DownloadService.DOWNLOAD_STATUS.DOWNLOAD_STOPPED;  }

      // change the download button's icon and text
      playBookFragment.redrawDownloadButton(status);
    }
  }


  /**
   * Accept a chapter id that's been downloaded, and ungray the chapter in the TOC.
   *
   * @param downloadedChapter The chapter that's just finished downloading.
   */
  public void drawDownloadProgressTableOfContents(Integer downloadedChapter) {
    if (tocNavigationView == null) {  return;  }
    LogHelper.d(TAG, "drawDownloadProgressTableOfContents: tocNavigationView.getChildCount()=", tocNavigationView.getChildCount());

    // TODO: Danger, we're assuming that there is no introduction (chapter 0), and that the book is chapterized, and that
    // there's only one part, instead of uniquely identifying a chapter.  This is very raw proof of concept for now,
    // just to practice drawing the TOC.
    final View child = tocNavigationView.getChildAt(downloadedChapter-1);
    //tocNavigationView.getMenu().getItem(downloadedChapter-1).setEnabled(true);

    if (child != null) {
      child.setEnabled(true);
    }

    // code from SO to update the nav menu through its adapter.  I don't think it should be necessary, but testing here:
    //for (int i = 0, count = tocNavigationView.getChildCount(); i < count; i++) {
      //final View child = tocNavigationView.getChildAt(i);
      //if (child != null && child instanceof ListView) {
        //final ListView menuView = (ListView) child;
        //final HeaderViewListAdapter adapter = (HeaderViewListAdapter) menuView.getAdapter();
        //final BaseAdapter wrapped = (BaseAdapter) adapter.getWrappedAdapter();
        //wrapped.notifyDataSetChanged();
      //}
    //}
  }


  /**
   * Reset the UI to show no downloads currently in progress.
   */
  public void resetDownloadProgress() {
    lastDownloadProgressTotal = 0;
    lastDowloadProgressChapter = 0;
    lastDownloadProgressStatus = DownloadService.DOWNLOAD_STATUS.DOWNLOAD_STOPPED;

    if (playBookFragment != null) {
      playBookFragment.resetDownloadProgress();
    }
  }


  /**
   * Update download progress tracking internal variables.
   *
   * @param primaryProgress
   * @param secondaryProgress
   * @param status
   */
  public void setDownloadProgress(Integer primaryProgress, Integer secondaryProgress, DownloadService.DOWNLOAD_STATUS status) {
    lastDownloadProgressTotal = primaryProgress;
    lastDowloadProgressChapter = secondaryProgress;
    lastDownloadProgressStatus = status;
  }


  /* ------------------------------------ /DOWNLOAD METHODS ------------------------------------- */


  /* ------------------------------------ UTILITY METHODS ------------------------------------- */

  /**
   * TODO: Do I need to manage app permissions manually, or will the Findaway sdk do that for me?
   * If it does it for me, where, what permissions, what reactions do I need to handle in the calling code?
   */
  private boolean checkAppPermissions() {
    return true;
  }

  /* ------------------------------------ /UTILITY METHODS ------------------------------------- */


}


