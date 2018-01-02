package org.nypl.findawayclientlibrary;


import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v7.app.AppCompatActivity;

import org.nypl.findawayclientlibrary.BaseFragment;


/**
 * Sets up the listeners that child fragments ask for.
 *
 * Created by daryachernikhova on 7/24/17.
 */
public class BaseActivity extends AppCompatActivity implements BaseFragment.ReplaceFragmentListener {
  // so can filter all log msgs belonging to my app
  public static final String APP_TAG = "AUDLIB.";
  // so can do a search in log msgs for just this class's output
  private static final String TAG = APP_TAG + "BaseActivity";



  /**
   * Do nothing, this is implemented in the calling project's main activity.
   * @param demoBookId
   */
  public void onBookSelected(String demoBookId) {}


  /**
   * Implemented in PlayBookActivity.
   * @return  MediaControllerCompat that provides media control onscreen buttons.
   */
  public MediaControllerCompat getController() {
    return null;
  }


  /**
   * NOTE: keeping for later use -- demonstrates calling for a fragment to be replaced.
   * @param demoBookId
   * /
  public void onBookSelected(String demoBookId) {
    PlayBookFragment playBookFragment = new PlayBookFragment();

    Bundle args = new Bundle();
    args.putString("audiobook_id", demoBookId);
    playBookFragment.setArguments(args);

    // Load the audiobook player fragment into the view, and pass it information on what book to play.
    this.loadFragment(playBookFragment, R.id.fragment_container, true);
  }
  */


  /**
   * Loads a fragment into the parent space allocated.
   * Gets the parent view frame passed in.  If it's not passed, then loads the new fragment into
   * own parent container.
   *
   * @param newFragment Fragment class/view to put on the screen.
   * @param parentFrameId Id of view container to show fragment in.
   * @param replace If false, adds a new fragment.  If true, replaces existing fragment with a new one.
   */
  public void loadFragment(Fragment newFragment, int parentFrameId, boolean replace) {

    FragmentManager fragmentManager = getSupportFragmentManager();
    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

    if (replace) {
      // Replace whatever is in the fragment_container view with this fragment,
      // and add the transaction to the back stack so the user can navigate back
      fragmentTransaction.replace(parentFrameId, newFragment);
    } else {
      // this is our first time loading a fragment into that frame
      //fragmentTransaction.add(R.id.fragment_container, newFragment);
      fragmentTransaction.add(parentFrameId, newFragment);
    }

    // NOTE: When you remove or replace a fragment and add the transaction to the back stack,
    // the fragment that is removed is stopped (not destroyed). If the user navigates back to
    // restore the fragment, it restarts. If you do not add the transaction to the back stack,
    // then the fragment is destroyed when removed or replaced.
    // TODO: when use the hardware "back" button, get an empty screen in-between the play and the select activity screens
    fragmentTransaction.addToBackStack(null);

    // Commit the transaction
    fragmentTransaction.commit();
  }


}
