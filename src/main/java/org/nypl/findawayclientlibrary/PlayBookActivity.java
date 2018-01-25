package org.nypl.findawayclientlibrary;


import android.content.Intent;
import android.os.Bundle;

import android.support.annotation.NonNull;
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



/**
 * Loads and exchanges fragments that are responsible for audio player UI
 * and audiobook-associated sub-screens.
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

    // ask the AudioEngine to start a PlaybackEngine
    playbackService.initPlaybackEngine();
  }// onCreate



  /**
   * Do nothing.
   */
  @Override
  protected void onDestroy() {
    super.onDestroy();
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
    Integer downloadStatus = downloadService.getDownloadStatus(contentId);
    // If not, ask the DownloadService to either start or resume the download.
    if (downloadStatus.equals(DownloadService.DOWNLOAD_ERROR) || downloadStatus.equals(DownloadService.DOWNLOAD_NEEDED)) {
      // ask to start the download
      downloadService.downloadAudio(contentId, license, currentlyDownloadingChapter, null);
    }

    if (downloadStatus.equals(DownloadService.DOWNLOAD_STOPPED) || downloadStatus.equals(DownloadService.DOWNLOAD_PAUSED)) {
      // TODO:  do not see a "resume" method in the sdk.  for now, will form a new download request.
      // Test that, if I send in multiple requests with same info, it's equivalent to asking to resume the download
      // Test that resuming after a stop works, and what happens if app shuts down and is restored.
      downloadService.resumeDownload(contentId);
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
    for (int menuItemId = 10; menuItemId <= 13; menuItemId++) {
      int order = menuItemId;
      MenuItem menuItem = menu.add(groupId, menuItemId, order, "Chapter " + menuItemId);
      menuItem.setIcon(R.drawable.ic_radio_button_unchecked_black_24dp);
      menuItem.setCheckable(true);
    }

    // code from SO to update the nav menu through its adapter.  I don't think it should be necessary, but testing here:
    for (int i = 0, count = tocNavigationView.getChildCount(); i < count; i++) {
      final View child = tocNavigationView.getChildAt(i);
      if (child != null && child instanceof ListView) {
        final ListView menuView = (ListView) child;
        //final HeaderViewListAdapter adapter = (HeaderViewListAdapter) menuView.getAdapter();
        //final BaseAdapter wrapped = (BaseAdapter) adapter.getWrappedAdapter();
        //wrapped.notifyDataSetChanged();
      }
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
              if (view.getId() == R.id.previous_track_button) {
                playbackService.getPlaybackEngine().previousChapter();
              } else {
                if (view.getId() == R.id.next_track_button) {
                  playbackService.getPlaybackEngine().nextChapter();
                } else {
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
                }
              }
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
   * TODO:  What connections do we have to the Findaway player, to be able to implement media controls
   * (play/pause/rewind X seconds, ff X seconds, seek bar, playback speed, etc.)?
   *
   */
  private void scheduleSeekbarUpdate() {
  }

  /* ------------------------------------ /PLAYBACK EVENT HANDLERS ------------------------------------- */


  /* ------------------------------------ UI METHODS ------------------------------------- */

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


  /**
   * Update the download progress bar, and any other download-associated UI elements
   * to reflect current download status (passed in).
   * TODO: make sure passing non-null enum for status from DownloadService line 187.
   *
   * @param primaryProgress
   * @param secondaryProgress
   */
  public void setDownloadProgress(Integer primaryProgress, Integer secondaryProgress, Integer status) {
    if (playBookFragment != null) {
      playBookFragment.redrawDownloadProgress(primaryProgress, secondaryProgress);

      // change the download button's icon and text
      playBookFragment.redrawDownloadButton(status);
    }
  }


  /**
   *
   */
  public void resetDownloadProgress() {
    if (playBookFragment != null) {
      playBookFragment.resetDownloadProgress();
    }
  }


  /**
   * Ask the fragment to update the seek bar.
   */
  public void setPlayProgress(Long duration, Long position) {
    if (playBookFragment != null) {
      playBookFragment.redrawPlaybackPosition(duration, position);
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

  /* ------------------------------------ /UTILITY METHODS ------------------------------------- */


}


/*
* TODO:  when click play, get this error.  make sure that catching it safely.  the error itself might be for another reason:
*
* 01-03 00:11:23.608 8172-9037/org.nypl.audiobooklibrarydemoapp D/OkHttp: <-- HTTP FAILED: java.net.UnknownHostException: Unable to resolve host "api.findawayworld.com": No address associated with hostname
01-03 00:11:23.609 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err: java.lang.RuntimeException: java.net.UnknownHostException: Unable to resolve host "api.findawayworld.com": No address associated with hostname
01-03 00:11:23.610 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.exceptions.Exceptions.propagate(Exceptions.java:58)
01-03 00:11:23.610 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.observables.BlockingObservable.blockForSingle(BlockingObservable.java:464)
01-03 00:11:23.610 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.observables.BlockingObservable.first(BlockingObservable.java:167)
01-03 00:11:23.610 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at io.audioengine.mobile.play.RequestManager.streamingChapter(RequestManager.java:325)
01-03 00:11:23.610 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at io.audioengine.mobile.play.RequestManager$8.call(RequestManager.java:276)
01-03 00:11:23.610 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at io.audioengine.mobile.play.RequestManager$8.call(RequestManager.java:265)
01-03 00:11:23.610 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.operators.OnSubscribeMap$MapSubscriber.onNext(OnSubscribeMap.java:69)
01-03 00:11:23.611 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.operators.OperatorTake$1.onNext(OperatorTake.java:76)
01-03 00:11:23.611 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at com.squareup.sqlbrite.QueryToOneOperator$1.onNext(QueryToOneOperator.java:45)
01-03 00:11:23.611 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at com.squareup.sqlbrite.QueryToOneOperator$1.onNext(QueryToOneOperator.java:22)
01-03 00:11:23.611 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.observers.Subscribers$5.onNext(Subscribers.java:235)
01-03 00:11:23.611 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.operators.OperatorOnBackpressureLatest$LatestEmitter.emit(OperatorOnBackpressureLatest.java:165)
01-03 00:11:23.611 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.operators.OperatorOnBackpressureLatest$LatestEmitter.onNext(OperatorOnBackpressureLatest.java:131)
01-03 00:11:23.611 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.operators.OperatorOnBackpressureLatest$LatestSubscriber.onNext(OperatorOnBackpressureLatest.java:211)
01-03 00:11:23.611 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.operators.OperatorObserveOn$ObserveOnSubscriber.call(OperatorObserveOn.java:224)
01-03 00:11:23.611 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.schedulers.CachedThreadScheduler$EventLoopWorker$1.call(CachedThreadScheduler.java:230)
01-03 00:11:23.611 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.schedulers.ScheduledAction.run(ScheduledAction.java:55)
01-03 00:11:23.611 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:428)
01-03 00:11:23.611 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at java.util.concurrent.FutureTask.run(FutureTask.java:237)
01-03 00:11:23.611 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:272)
01-03 00:11:23.611 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1133)
01-03 00:11:23.611 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:607)
01-03 00:11:23.612 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at java.lang.Thread.run(Thread.java:761)
01-03 00:11:23.612 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err: Caused by: java.net.UnknownHostException: Unable to resolve host "api.findawayworld.com": No address associated with hostname
01-03 00:11:23.612 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at java.net.Inet6AddressImpl.lookupHostByName(Inet6AddressImpl.java:125)
01-03 00:11:23.612 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at java.net.Inet6AddressImpl.lookupAllHostAddr(Inet6AddressImpl.java:74)
01-03 00:11:23.612 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at java.net.InetAddress.getAllByName(InetAddress.java:752)
01-03 00:11:23.612 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.Dns$1.lookup(Dns.java:39)
01-03 00:11:23.612 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.internal.connection.RouteSelector.resetNextInetSocketAddress(RouteSelector.java:170)
01-03 00:11:23.612 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.internal.connection.RouteSelector.nextProxy(RouteSelector.java:136)
01-03 00:11:23.612 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.internal.connection.RouteSelector.next(RouteSelector.java:81)
01-03 00:11:23.612 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.internal.connection.StreamAllocation.findConnection(StreamAllocation.java:171)
01-03 00:11:23.613 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.internal.connection.StreamAllocation.findHealthyConnection(StreamAllocation.java:121)
01-03 00:11:23.613 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.internal.connection.StreamAllocation.newStream(StreamAllocation.java:100)
01-03 00:11:23.613 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.internal.connection.ConnectInterceptor.intercept(ConnectInterceptor.java:42)
01-03 00:11:23.613 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.java:92)
01-03 00:11:23.613 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.java:67)
01-03 00:11:23.613 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.internal.cache.CacheInterceptor.intercept(CacheInterceptor.java:93)
01-03 00:11:23.613 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.java:92)
01-03 00:11:23.613 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.java:67)
01-03 00:11:23.613 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.internal.http.BridgeInterceptor.intercept(BridgeInterceptor.java:93)
01-03 00:11:23.613 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.java:92)
01-03 00:11:23.613 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.internal.http.RetryAndFollowUpInterceptor.intercept(RetryAndFollowUpInterceptor.java:120)
01-03 00:11:23.613 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.java:92)
01-03 00:11:23.613 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.java:67)
01-03 00:11:23.613 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.logging.HttpLoggingInterceptor.intercept(HttpLoggingInterceptor.java:212)
01-03 00:11:23.613 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.java:92)
01-03 00:11:23.613 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.java:67)
01-03 00:11:23.613 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.RealCall.getResponseWithInterceptorChain(RealCall.java:179)
01-03 00:11:23.613 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at okhttp3.RealCall.execute(RealCall.java:63)
01-03 00:11:23.613 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at retrofit2.OkHttpCall.execute(OkHttpCall.java:174)
01-03 00:11:23.614 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at retrofit2.adapter.rxjava.RxJavaCallAdapterFactory$RequestArbiter.request(RxJavaCallAdapterFactory.java:171)
01-03 00:11:23.614 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.operators.OperatorSubscribeOn$1$1$1.request(OperatorSubscribeOn.java:80)
01-03 00:11:23.614 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.operators.OperatorTake$1$1.request(OperatorTake.java:109)
01-03 00:11:23.614 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.Subscriber.setProducer(Subscriber.java:211)
01-03 00:11:23.614 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.operators.OperatorTake$1.setProducer(OperatorTake.java:93)
01-03 00:11:23.614 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.operators.OnSubscribeMap$MapSubscriber.setProducer(OnSubscribeMap.java:102)
01-03 00:11:23.614 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.operators.OnSubscribeMap$MapSubscriber.setProducer(OnSubscribeMap.java:102)
01-03 00:11:23.614 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.operators.OnSubscribeFilter$FilterSubscriber.setProducer(OnSubscribeFilter.java:104)
01-03 00:11:23.614 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.operators.OnSubscribeMap$MapSubscriber.setProducer(OnSubscribeMap.java:102)
01-03 00:11:23.614 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.operators.OperatorSubscribeOn$1$1.setProducer(OperatorSubscribeOn.java:76)
01-03 00:11:23.614 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at retrofit2.adapter.rxjava.RxJavaCallAdapterFactory$CallOnSubscribe.call(RxJavaCallAdapterFactory.java:152)
01-03 00:11:23.614 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at retrofit2.adapter.rxjava.RxJavaCallAdapterFactory$CallOnSubscribe.call(RxJavaCallAdapterFactory.java:138)
01-03 00:11:23.614 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.Observable.unsafeSubscribe(Observable.java:10142)
01-03 00:11:23.614 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.operators.OperatorSubscribeOn$1.call(OperatorSubscribeOn.java:94)
01-03 00:11:23.614 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err: 	... 8 more
01-03 00:11:23.615 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err: Caused by: android.system.GaiException: android_getaddrinfo failed: EAI_NODATA (No address associated with hostname)
01-03 00:11:23.615 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at libcore.io.Posix.android_getaddrinfo(Native Method)
01-03 00:11:23.615 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at libcore.io.ForwardingOs.android_getaddrinfo(ForwardingOs.java:55)
01-03 00:11:23.615 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at java.net.Inet6AddressImpl.lookupHostByName(Inet6AddressImpl.java:106)
01-03 00:11:23.615 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err: 	... 48 more
01-03 00:11:23.615 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err: Caused by: rx.exceptions.OnErrorThrowable$OnNextValue: OnError while emitting onNext value: null
01-03 00:11:23.615 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.operators.OnSubscribeMap$MapSubscriber.onNext(OnSubscribeMap.java:73)
01-03 00:11:23.615 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.operators.OperatorTake$1.onNext(OperatorTake.java:76)
01-03 00:11:23.615 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at com.squareup.sqlbrite.QueryToOneOperator$1.onNext(QueryToOneOperator.java:45)
01-03 00:11:23.615 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at com.squareup.sqlbrite.QueryToOneOperator$1.onNext(QueryToOneOperator.java:22)
01-03 00:11:23.615 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.observers.Subscribers$5.onNext(Subscribers.java:235)
01-03 00:11:23.615 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.operators.OperatorOnBackpressureLatest$LatestEmitter.emit(OperatorOnBackpressureLatest.java:165)
01-03 00:11:23.615 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.operators.OperatorOnBackpressureLatest$LatestEmitter.onNext(OperatorOnBackpressureLatest.java:131)
01-03 00:11:23.616 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.operators.OperatorOnBackpressureLatest$LatestSubscriber.onNext(OperatorOnBackpressureLatest.java:211)
01-03 00:11:23.616 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err:     at rx.internal.operators.OperatorObserveOn$ObserveOnSubscriber.call(OperatorObserveOn.java:224)
01-03 00:11:23.616 8172-9035/org.nypl.audiobooklibrarydemoapp W/System.err: 	... 8 more
*
*
* */
